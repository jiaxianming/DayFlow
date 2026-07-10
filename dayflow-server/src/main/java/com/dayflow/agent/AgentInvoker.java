package com.dayflow.agent;

import com.dayflow.agent.model.AgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * Agent 调用聚合器：封装 4 个 Agent 共用的「调用 ChatClient → 测 latency → 提取 token → 解析 entity」。
 * <p>不吞异常：调用失败时原样抛出，由编排层 {@code run} 的 try-catch 统一转 report.status=FAILED。</p>
 *
 * @author jiaxianming
 */
@Component
public class AgentInvoker {

    private static final Logger log = LoggerFactory.getLogger(AgentInvoker.class);

    /**
     * 调用 ChatClient 并返回结构化结果 + 元信息。
     * <p>Spring AI 2.0 的 {@code .call()} 返回 {@link ChatClient.CallResponseSpec}，
     * 一次调用即可同时拿到 {@code chatResponse()}（含 token 用量元信息）与 {@code entity(Class)}（结构化对象）。</p>
     * <p><strong>结构化解析失败自动重试 1 次</strong>：DeepSeek 偶发输出非法 JSON（字段间漏逗号、
     * markdown 包裹等），{@code .entity()} 经 BeanOutputConverter 严格解析会抛
     * {@code StreamReadException}。捕获后重试一次（模型重新生成，大概率合法），token 累加
     * （首次失败也计费）；重试仍失败则原样抛出，由编排层 {@code run} 的 catch 统一转 FAILED。</p>
     *
     * @param client     已配置 defaultSystem（及 defaultTools）的专属 ChatClient
     * @param userPrompt 用户提示文本
     * @param type       结构化产出类型
     * @param <T>        结构化类型
     * @return AgentResult（payload + tokens + latencyMs）
     */
    public <T> AgentResult<T> invoke(ChatClient client, String userPrompt, Class<T> type) {
        long start = System.currentTimeMillis();
        // .call() 返回 CallResponseSpec：同一对象既提供 chatResponse()（元信息）又提供 entity(Class)（结构化对象）
        ChatClient.CallResponseSpec callResponse =
                client.prompt().user(userPrompt).call();
        ChatResponse chatResponse = callResponse.chatResponse();
        int tokens = extractTokens(chatResponse);
        try {
            T payload = callResponse.entity(type);
            long latency = System.currentTimeMillis() - start;
            log.debug("Agent 调用 type={} tokens={} latencyMs={}", type.getSimpleName(), tokens, latency);
            return new AgentResult<>(payload, tokens, latency);
        } catch (RuntimeException parseErr) {
            // 结构化解析失败（DeepSeek 偶发非法 JSON）—— 重试 1 次，提升结构化输出可靠性
            log.warn("Agent 结构化解析失败，重试 1 次 type={} tokens={} err={}",
                    type.getSimpleName(), tokens, parseErr.getMessage());
            return retryInvoke(client, userPrompt, type, start, tokens);
        }
    }

    /**
     * 结构化解析失败后的重试：重新调用一次，token 累加（首次失败也计费），latency 含两次端到端耗时。
     * <p>重试仍失败则原样抛出，由编排层 {@code run} 的 try-catch 统一转 {@code report.status=FAILED}。</p>
     *
     * @param client      已配置 defaultSystem 的专属 ChatClient
     * @param userPrompt  用户提示文本
     * @param type        结构化产出类型
     * @param start       首次调用起始时间（latency 统一口径）
     * @param firstTokens 首次失败调用消耗的 token（计费累加）
     * @param <T>         结构化类型
     * @return AgentResult（payload=重试产出；tokens=两次累加；latency=含两次）
     */
    private <T> AgentResult<T> retryInvoke(ChatClient client, String userPrompt, Class<T> type,
                                           long start, int firstTokens) {
        ChatClient.CallResponseSpec callResponse =
                client.prompt().user(userPrompt).call();
        ChatResponse chatResponse = callResponse.chatResponse();
        int retryTokens = extractTokens(chatResponse);
        T payload = callResponse.entity(type);
        long latency = System.currentTimeMillis() - start;
        int total = firstTokens + retryTokens;
        log.debug("Agent 重试成功 type={} tokens={} latencyMs={}", type.getSimpleName(), total, latency);
        return new AgentResult<>(payload, total, latency);
    }

    /**
     * 从 ChatResponse 提取 token 用量（usage 为空或字段缺失时返回 0，不影响主流程）
     *
     * @param chatResponse ChatResponse
     * @return total tokens，取不到为 0
     */
    static int extractTokens(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return 0;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null || usage.getTotalTokens() == null) {
            return 0;
        }
        return usage.getTotalTokens();
    }

    /**
     * 调用 ChatClient 仅取文本内容（不解析 entity），用于带 tool 的采集调用。
     * <p>DeepSeek 在 tool calling 后最终 content 可能间歇为空，若继续走 {@code .entity()}
     * 会因空串反序列化抛 {@code MismatchedInputException}（BeanOutputConverter 无内容可映射），
     * 进而整条编排链崩溃。本方法只取文本，空内容安全降级为空字符串（绝不抛），
     * 由 Collector 第二段无 tool 结构化兜底。</p>
     *
     * @param client     已配置 defaultSystem（及 defaultTools）的专属 ChatClient
     * @param userPrompt 用户提示文本
     * @return AgentResult（payload=内容文本，空内容返回空串；tokens；latencyMs）
     */
    public AgentResult<String> callForContent(ChatClient client, String userPrompt) {
        long start = System.currentTimeMillis();
        ChatClient.CallResponseSpec callResponse =
                client.prompt().user(userPrompt).call();
        ChatResponse chatResponse = callResponse.chatResponse();
        int tokens = extractTokens(chatResponse);
        String content = extractContent(chatResponse);
        long latency = System.currentTimeMillis() - start;
        log.debug("Agent 内容调用 type=content tokens={} latencyMs={}", tokens, latency);
        return new AgentResult<>(content, tokens, latency);
    }

    /**
     * 从 ChatResponse 提取 assistant 文本内容（两段式采集第一段专用）。
     * <p>任一环节为 null（含 DeepSeek tool calling 后间歇空 content）均安全返回空字符串，
     * 绝不抛异常——这是 Collector 防 {@code .entity()} 崩溃的关键。</p>
     *
     * @param chatResponse ChatResponse
     * @return 文本内容，空或缺失返回 ""
     */
    static String extractContent(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null) {
            return "";
        }
        String text = chatResponse.getResult().getOutput().getText();
        return text == null ? "" : text;
    }
}
