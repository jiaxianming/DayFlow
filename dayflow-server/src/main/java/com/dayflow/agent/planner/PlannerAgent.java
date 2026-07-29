package com.dayflow.agent.planner;

import com.dayflow.agent.AgentInvoker;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.PlanInput;
import com.dayflow.agent.model.ReportPlan;
import com.dayflow.pojo.enums.ReportType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 规划 Agent：据日期 + 数据提示产出报告板块计划（结构化 ReportPlan）。
 * <p>空数据由 LLM 据 dataHint="当日无任何记录" 产出单板块计划，非硬编码。</p>
 * <p>安全约束：userId 绝不进 prompt（{@link PlanInput} 不含 userId 字段），
 * 仅经 {@code AgentContext} 供 Tool 使用，杜绝 LLM 幻觉导致越权拉取他人数据。</p>
 *
 * @author jiaxianming
 */
@Component
public class PlannerAgent {

    /**
     * Agent 调用聚合器：封装「调用 ChatClient → 测 latency → 提取 token → 解析 entity」
     */
    private final AgentInvoker invoker;

    /**
     * Planner 专属 ChatClient（已注 PLANNER_PROMPT 为 defaultSystem）
     */
    private final ChatClient plannerChatClient;

    /**
     * 构造 PlannerAgent。
     *
     * @param invoker           Agent 调用聚合器
     * @param plannerChatClient Planner 专属 ChatClient（已注 defaultSystem）
     */
    public PlannerAgent(AgentInvoker invoker,
                        @Qualifier("plannerChatClient") ChatClient plannerChatClient) {
        this.invoker = invoker;
        this.plannerChatClient = plannerChatClient;
    }

    /**
     * 规划报告板块。
     * <p>构造 prompt（含 date/reportType/dataHint，<strong>不含 userId</strong>），
     * 调 {@link AgentInvoker#invoke} 得到结构化 {@link ReportPlan}。</p>
     *
     * @param input 规划输入（date/reportType/dataHint）
     * @return AgentResult（payload=ReportPlan）
     */
    public AgentResult<ReportPlan> plan(PlanInput input) {
        String prompt = buildPrompt(input);
        return invoker.invoke(plannerChatClient, prompt, ReportPlan.class);
    }

    /**
     * 构造用户提示文本。
     * <p>dataHint 为 null 时显示「无」；空数据（"当日无任何记录"）由编排层传入并由 LLM 据系统 prompt 产单板块。</p>
     *
     * @param input 规划输入
     * @return 用户提示文本
     */
    private String buildPrompt(PlanInput input) {
        ReportType type = input.getReportType();
        String typeLabel = (type == ReportType.WEEKLY) ? "周报" : "日报";
        String period = input.getStartDate().equals(input.getEndDate())
                ? "日期：" + input.getStartDate()
                : "周期：" + input.getStartDate() + " ~ " + input.getEndDate();
        String sectionHint = (type == ReportType.WEEKLY)
                ? "（建议板块：本周工作总结 / 学习收获 / 问题与改进 / 下周计划）"
                : "（建议板块：今日工作 / 学习记录）";
        return period + "；报告类型：" + typeLabel + "；数据提示："
                + (input.getDataHint() == null ? "无" : input.getDataHint())
                + "。请据此规划" + typeLabel + "板块结构" + sectionHint + "。";
    }
}
