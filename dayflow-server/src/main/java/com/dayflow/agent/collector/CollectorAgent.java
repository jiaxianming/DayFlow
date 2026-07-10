package com.dayflow.agent.collector;

import com.dayflow.agent.AgentInvoker;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.CollectedMaterial;
import com.dayflow.agent.model.PlanSection;
import com.dayflow.agent.model.ReportPlan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 采集 Agent：按报告计划调工具拉真实数据，归纳成结构化素材包。
 * <p>collectorChatClient 已预配 {@code defaultTools(reportDataTools)}，LLM 自主调工具取数。</p>
 * <p>安全约束：userId 绝不进 prompt（本 Agent 不读 userId），
 * 仅经 {@code AgentContext} 供 Tool 使用，杜绝 LLM 幻觉导致越权拉取他人数据。</p>
 *
 * @author jiaxianming
 */
@Component
public class CollectorAgent {

    /**
     * Agent 调用聚合器：封装「调用 ChatClient → 测 latency → 提取 token → 解析 entity」
     */
    private final AgentInvoker invoker;

    /**
     * Collector 专属 ChatClient（已注 COLLECTOR_PROMPT 为 defaultSystem + defaultTools）
     */
    private final ChatClient collectorChatClient;

    /**
     * 构造 CollectorAgent。
     *
     * @param invoker            Agent 调用聚合器
     * @param collectorChatClient Collector 专属 ChatClient（已注 defaultSystem + defaultTools）
     */
    public CollectorAgent(AgentInvoker invoker,
                          @Qualifier("collectorChatClient") ChatClient collectorChatClient) {
        this.invoker = invoker;
        this.collectorChatClient = collectorChatClient;
    }

    /**
     * 采集素材。
     * <p>据报告计划与日期构造 prompt（<strong>不含 userId</strong>），调
     * {@link AgentInvoker#invoke} 得到结构化 {@link CollectedMaterial}。
     * 采集范围固定为 [date, date]（单日）。</p>
     *
     * @param plan 报告计划（标题 + 板块清单）
     * @param date 采集日期（范围 = [date, date]）
     * @return AgentResult（payload=CollectedMaterial）
     */
    public AgentResult<CollectedMaterial> collect(ReportPlan plan, LocalDate date) {
        String prompt = buildPrompt(plan, date);
        return invoker.invoke(collectorChatClient, prompt, CollectedMaterial.class);
    }

    /**
     * 构造用户提示文本。
     * <p>列出采集日期范围、报告标题与各板块（数据源 + 重点），
     * 指示 LLM 对每个板块调对应工具拉真实数据并按板块归纳。
     * 板块清单为空时仅给出采集框架（由 LLM 自行决策）。</p>
     *
     * @param plan 报告计划
     * @param date 采集日期
     * @return 用户提示文本
     */
    private String buildPrompt(ReportPlan plan, LocalDate date) {
        String dateStr = date.toString();
        StringBuilder sb = new StringBuilder();
        sb.append("采集日期：").append(dateStr).append("。").append("开始：").append(dateStr)
          .append("，结束：").append(dateStr).append("。");
        sb.append("报告标题：").append(plan.getTitle()).append("。");
        sb.append("请按以下板块结构采集数据：\n");
        if (plan.getSections() != null) {
            for (PlanSection s : plan.getSections()) {
                sb.append("- 板块「").append(s.getName()).append("」，数据源：")
                  .append(s.getDataSource()).append("，重点：").append(s.getFocus()).append("\n");
            }
        }
        sb.append("对每个板块调用对应工具拉取真实数据，按板块归类并归纳摘要。");
        return sb.toString();
    }
}
