package com.dayflow.agent.config;

import com.dayflow.agent.prompt.PromptLoader;
import com.dayflow.agent.tools.ReportDataTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 4 个 Agent 专属 ChatClient 配置：各自 defaultSystem 注入角色 prompt。
 * <p>提示词已外置至 {@code resources/prompts/*.txt}，由 {@link PromptLoader} 启动时加载；
 * 本类仅负责装配——调 prompt 改文件即可，无需改 Java。</p>
 * <p>Planner/Writer/Reviewer 三个 ChatClient 仅注入角色 prompt（不带 tools）；
 * Collector 额外通过 {@code defaultTools} 注册 {@link ReportDataTools}，
 * 使 LLM 可自主调用 @Tool 方法采集真实数据。</p>
 *
 * @author jiaxianming
 */
@Configuration
public class AgentChatClientConfig {

    /**
     * Collector 数据采集工具（含 @Tool 三方法），作 collectorChatClient 的 defaultTools
     */
    private final ReportDataTools reportDataTools;

    /**
     * 提示词加载器：提供 {@code prompts/*.txt} 中的角色提示词
     */
    private final PromptLoader promptLoader;

    /**
     * 构造器注入 ReportDataTools 与 PromptLoader。
     *
     * @param reportDataTools 报告数据采集工具
     * @param promptLoader    提示词加载器
     */
    public AgentChatClientConfig(ReportDataTools reportDataTools, PromptLoader promptLoader) {
        this.reportDataTools = reportDataTools;
        this.promptLoader = promptLoader;
    }

    /**
     * Planner 专属 ChatClient，注入主编角色 prompt（来自 prompts/planner.txt）。
     *
     * @param chatModel M2 auto-config 创建的 ChatModel
     * @return Planner 专属 ChatClient
     */
    @Bean(name = "plannerChatClient")
    public ChatClient plannerChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).defaultSystem(promptLoader.get("planner")).build();
    }

    /**
     * Writer 专属 ChatClient，注入撰稿角色 prompt（来自 prompts/writer.txt）。
     *
     * @param chatModel M2 auto-config 创建的 ChatModel
     * @return Writer 专属 ChatClient
     */
    @Bean(name = "writerChatClient")
    public ChatClient writerChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).defaultSystem(promptLoader.get("writer")).build();
    }

    /**
     * Reviewer 专属 ChatClient，注入审校角色 prompt（来自 prompts/reviewer.txt）。
     *
     * @param chatModel M2 auto-config 创建的 ChatModel
     * @return Reviewer 专属 ChatClient
     */
    @Bean(name = "reviewerChatClient")
    public ChatClient reviewerChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).defaultSystem(promptLoader.get("reviewer")).build();
    }

    /**
     * Collector 专属 ChatClient，注入记者角色 prompt（来自 prompts/collector.txt）并注册
     * {@link ReportDataTools} 为 defaultTools，使 LLM 可自主调用 @Tool 方法采集真实数据。
     * <p>安全约束：userId 全程由 {@code AgentContext}（后端掌控）传入，
     * LLM 不接触 userId；工具内 userId 缺失时安全降级返回空列表。</p>
     *
     * @param chatModel M2 auto-config 创建的 ChatModel
     * @return Collector 专属 ChatClient（defaultSystem + defaultTools）
     */
    @Bean(name = "collectorChatClient")
    public ChatClient collectorChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(promptLoader.get("collector"))
                .defaultTools(reportDataTools)
                .build();
    }

    /**
     * Collector 第二段结构化专用 ChatClient（<strong>无 tool</strong>）。
     * <p>规避 DeepSeek tool calling 后间歇空 content 导致 {@code .entity()} 崩溃：
     * 第一段带 tool 仅取文本（{@code callForContent}），第二段用本 client 把文本结构化为
     * {@code CollectedMaterial}——无 tool 调用下模型稳定产 content，不再受 tool calling
     * 空 content 影响。提示词来自 prompts/collector-struct.txt。</p>
     *
     * @param chatModel M2 auto-config 创建的 ChatModel
     * @return Collector 结构化专用 ChatClient（仅 defaultSystem，无 tools）
     */
    @Bean(name = "collectorStructChatClient")
    public ChatClient collectorStructChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).defaultSystem(promptLoader.get("collector-struct")).build();
    }
}
