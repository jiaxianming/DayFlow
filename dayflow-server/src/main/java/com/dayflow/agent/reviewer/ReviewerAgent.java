package com.dayflow.agent.reviewer;

import com.dayflow.agent.AgentInvoker;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.CollectedMaterial;
import com.dayflow.agent.model.DraftReport;
import com.dayflow.agent.model.ReviewResult;
import tools.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 审校 Agent：对草稿做四维质检（素材依据/去重/板块完整/语气）。
 * <p>passed=false 时给 suggestions 供 Writer 返工。</p>
 *
 * @author jiaxianming
 */
@Component
public class ReviewerAgent {

    /**
     * JSON 序列化器（Spring Boot 4.1 内置 Jackson 3.x，包名为 {@code tools.jackson.databind}）
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Agent 调用聚合器：封装「调用 ChatClient → 测 latency → 提取 token → 解析 entity」
     */
    private final AgentInvoker invoker;

    /**
     * Reviewer 专属 ChatClient（已注 REVIEWER_PROMPT 为 defaultSystem）
     */
    private final ChatClient reviewerChatClient;

    /**
     * 构造 ReviewerAgent。
     *
     * @param invoker            Agent 调用聚合器
     * @param reviewerChatClient Reviewer 专属 ChatClient（已注 defaultSystem）
     */
    public ReviewerAgent(AgentInvoker invoker,
                         @Qualifier("reviewerChatClient") ChatClient reviewerChatClient) {
        this.invoker = invoker;
        this.reviewerChatClient = reviewerChatClient;
    }

    /**
     * 审校草稿。
     * <p>构造 prompt（草稿 + 原始素材序列化为 JSON，供 Reviewer 核对素材依据），
     * 调 {@link AgentInvoker#invoke} 得到结构化 {@link ReviewResult}。</p>
     *
     * @param draft    草稿
     * @param material 采集素材（供核对素材依据）
     * @return AgentResult（payload=ReviewResult）
     */
    public AgentResult<ReviewResult> review(DraftReport draft, CollectedMaterial material) {
        String prompt = buildPrompt(draft, material);
        return invoker.invoke(reviewerChatClient, prompt, ReviewResult.class);
    }

    /**
     * 构造用户提示文本。
     * <p>将草稿与原始素材一并给出，便于 Reviewer 核对每条结论是否有素材依据。</p>
     *
     * @param draft    草稿
     * @param material 采集素材
     * @return 用户提示文本
     */
    @SneakyThrows
    private String buildPrompt(DraftReport draft, CollectedMaterial material) {
        return "草稿：" + JSON.writeValueAsString(draft)
                + "\n原始素材（供核对依据）：" + JSON.writeValueAsString(material)
                + "\n请做四维质检并输出结构化结果。";
    }
}
