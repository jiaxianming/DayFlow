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
     * 调用 ChatClient 并返回结构化结果 + 元信息
     * <p>Spring AI 2.0 的 {@code .call()} 返回 {@link ChatClient.CallResponseSpec}，
     * 一次调用即可同时拿到 {@code chatResponse()}（含 token 用量元信息）与 {@code entity(Class)}（结构化对象）。</p>
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
        T payload = callResponse.entity(type);
        long latency = System.currentTimeMillis() - start;
        log.debug("Agent 调用 type={} tokens={} latencyMs={}", type.getSimpleName(), tokens, latency);
        return new AgentResult<>(payload, tokens, latency);
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
}
