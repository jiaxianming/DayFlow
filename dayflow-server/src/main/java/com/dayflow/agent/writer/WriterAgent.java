package com.dayflow.agent.writer;

import com.dayflow.agent.AgentInvoker;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.CollectedMaterial;
import com.dayflow.agent.model.DraftReport;
import com.dayflow.agent.model.ReportPlan;
import com.dayflow.pojo.enums.ReportType;
import tools.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 撰写 Agent：把素材写成通顺中文 markdown 段落（结构化 DraftReport）。
 * <p>收到 Reviewer 的 suggestions 时据此返工。</p>
 *
 * @author jiaxianming
 */
@Component
public class WriterAgent {

    /**
     * JSON 序列化器（Spring Boot 4.1 内置 Jackson 3.x，包名为 {@code tools.jackson.databind}）
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Agent 调用聚合器：封装「调用 ChatClient → 测 latency → 提取 token → 解析 entity」
     */
    private final AgentInvoker invoker;

    /**
     * Writer 专属 ChatClient（已注 WRITER_PROMPT 为 defaultSystem）
     */
    private final ChatClient writerChatClient;

    /**
     * 构造 WriterAgent。
     *
     * @param invoker          Agent 调用聚合器
     * @param writerChatClient Writer 专属 ChatClient（已注 defaultSystem）
     */
    public WriterAgent(AgentInvoker invoker,
                       @Qualifier("writerChatClient") ChatClient writerChatClient) {
        this.invoker = invoker;
        this.writerChatClient = writerChatClient;
    }

    /**
     * 撰写草稿。
     * <p>构造 prompt（plan + material 序列化为 JSON，附带 suggestions），
     * 调 {@link AgentInvoker#invoke} 得到结构化 {@link DraftReport}。</p>
     *
     * @param plan        报告计划
     * @param material    采集素材
     * @param suggestions 修改建议（首次为 null）
     * @return AgentResult（payload=DraftReport）
     */
    public AgentResult<DraftReport> write(ReportPlan plan, CollectedMaterial material, String suggestions, ReportType type) {
        String prompt = buildPrompt(plan, material, suggestions, type);
        return invoker.invoke(writerChatClient, prompt, DraftReport.class);
    }

    /**
     * 构造用户提示文本。
     * <p>suggestions 为 null 时显示「无修改建议（首次撰写）」，非 null 时原样透传 Reviewer 的建议。</p>
     *
     * @param plan        报告计划
     * @param material    采集素材
     * @param suggestions 修改建议（首次为 null）
     * @return 用户提示文本
     */
    @SneakyThrows
    private String buildPrompt(ReportPlan plan, CollectedMaterial material, String suggestions, ReportType type) {
        String typeLabel = (type == ReportType.WEEKLY) ? "周报" : "日报";
        return "报告计划：" + JSON.writeValueAsString(plan)
                + "\n采集素材：" + JSON.writeValueAsString(material)
                + "\n修改建议：" + (suggestions == null ? "无修改建议（首次撰写）" : suggestions)
                + "\n请据此撰写" + typeLabel + "草稿。";
    }
}
