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
 * <p><strong>两段式</strong>（规避 DeepSeek tool calling 后间歇空 content 导致 {@code .entity()} 崩溃）：
 * <ol>
 *   <li>第一段：{@code collectorChatClient}（带 {@code defaultTools}）经 {@code callForContent}
 *       仅取采集文本——LLM 自主调工具取数，空 content 安全降级为 ""。</li>
 *   <li>第二段：{@code structChatClient}（无 tool）把采集文本结构化为 {@link CollectedMaterial}
 *       ——无 tool 调用下模型稳定产 content。</li>
 * </ol></p>
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
     * Collector 第二段结构化专用 ChatClient（无 tool），规避 tool calling 后空 content 崩溃
     */
    private final ChatClient structChatClient;

    /**
     * 构造 CollectorAgent。
     *
     * @param invoker            Agent 调用聚合器
     * @param collectorChatClient Collector 专属 ChatClient（已注 defaultSystem + defaultTools）
     * @param structChatClient   Collector 第二段结构化专用 ChatClient（无 tool）
     */
    public CollectorAgent(AgentInvoker invoker,
                          @Qualifier("collectorChatClient") ChatClient collectorChatClient,
                          @Qualifier("collectorStructChatClient") ChatClient structChatClient) {
        this.invoker = invoker;
        this.collectorChatClient = collectorChatClient;
        this.structChatClient = structChatClient;
    }

    /**
     * 采集素材（两段式）。
     * <p>据报告计划与周期构造 prompt（<strong>不含 userId</strong>）：
     * <ol>
     *   <li>第一段：带 tool 的 {@code collectorChatClient} 经 {@code callForContent} 取采集文本
     *       ——tool calling 后空 content 降级为 ""，不崩。</li>
     *   <li>第二段：无 tool 的 {@code structChatClient} 把文本结构化为 {@link CollectedMaterial}。</li>
     * </ol>
     * 两段 token / latency 累加。采集范围为 [startDate, endDate]。</p>
     *
     * @param plan      报告计划（标题 + 板块清单）
     * @param startDate 采集起始日（含）
     * @param endDate   采集结束日（含）
     * @return AgentResult（payload=CollectedMaterial；tokens/latency 为两段累加）
     */
    public AgentResult<CollectedMaterial> collect(ReportPlan plan, LocalDate startDate, LocalDate endDate) {
        String prompt = buildPrompt(plan, startDate, endDate);
        // 第一段：带 tool 采集——仅取文本（callForContent），规避 tool calling 后空 content 崩溃
        AgentResult<String> collected = invoker.callForContent(collectorChatClient, prompt);
        // 第二段：无 tool 结构化——把采集文本喂给 structChatClient，DeepSeek 无 tool 调用稳定产 content
        AgentResult<CollectedMaterial> structured =
                invoker.invoke(structChatClient, collected.payload(), CollectedMaterial.class);
        // 两段 token / latency 累加
        return new AgentResult<>(structured.payload(),
                collected.tokens() + structured.tokens(),
                collected.latencyMs() + structured.latencyMs());
    }

    /**
     * 构造用户提示文本。
     * <p>列出采集周期范围、报告标题与各板块（数据源 + 重点），
     * 指示 LLM 对每个板块调对应工具拉真实数据并按板块归纳。
     * 板块清单为空时仅给出采集框架（由 LLM 自行决策）。</p>
     *
     * @param plan      报告计划
     * @param startDate 采集起始日
     * @param endDate   采集结束日
     * @return 用户提示文本
     */
    private String buildPrompt(ReportPlan plan, LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("采集周期：").append(startDate).append(" ~ ").append(endDate).append("。")
          .append("开始：").append(startDate).append("，结束：").append(endDate).append("。");
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
