package com.dayflow.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 4 个 Agent 专属 ChatClient 配置：各自 defaultSystem 注入角色 prompt。
 * <p>本 task（T4）只建 3 个不带 tools 的 ChatClient（Planner/Writer/Reviewer）。
 * collectorChatClient 需注入 {@code ReportDataTools} 作 defaultTools，依赖 Task 6 完成，
 * 届时在 ReportDataTools 就绪后于此补建（带 COLLECTOR_PROMPT + defaultTools）。
 * COLLECTOR_PROMPT 常量在此一并定义，供 T6 直接引用。</p>
 *
 * @author jiaxianming
 */
@Configuration
public class AgentChatClientConfig {

    /** 主编：规划日报板块 */
    public static final String PLANNER_PROMPT = """
            你是日报主编（Planner）。根据用户提供的日期与数据提示，规划一份「工作与学习日报」的板块结构。
            规则：
            1. 板块数量 2-4 个；每个板块指定 dataSource（ACTIVITY/TASK/NOTE 之一）与 focus（该板块重点）。
            2. 标题格式固定为「<日期> 工作与学习日报」。
            3. 若数据提示表明当日无任何记录，则产出单个板块（name=今日暂无记录，dataSource=ACTIVITY，focus=说明当日无记录）。
            4. 严格输出结构化 JSON，字段：title、sections[{name,dataSource,focus}]。
            """;

    /** 撰稿：把素材写成中文段落 */
    public static final String WRITER_PROMPT = """
            你是日报撰稿人（Writer）。根据报告计划与采集到的素材，撰写通顺的中文 markdown 段落。
            规则：
            1. 严格按计划板块结构组织；每个板块 content 为 2-5 句中文段落。
            2. 每段必须有素材依据，不得臆造、不得夸大；某板块无素材时写「本板块今日无记录」。
            3. 客观专业、不啰嗦；若收到修改建议（suggestions），严格据此修改。
            4. 严格输出结构化 JSON，字段：title、sections[{name,content}]。
            """;

    /** 审校：质检草稿 */
    public static final String REVIEWER_PROMPT = """
            你是日报审校（Reviewer）。对草稿做四维质检：①素材依据（是否夸大/无依据 OVERCLAIM）
            ②去重（板块间是否重复 REDUNDANT）③板块完整（是否漏板块 MISSING）④语气（是否不当 TONE）。
            规则：
            1. 全部通过则 passed=true、issues 为空、suggestions 为空。
            2. 否则 passed=false，issues 列出具体问题，suggestions 给出给撰稿人的明确修改建议。
            3. 严格输出结构化 JSON，字段：passed(boolean)、issues[{section,type,description}]、suggestions。
            """;

    /** 记者：采集（在 Task 6 补建 collectorChatClient 时使用） */
    public static final String COLLECTOR_PROMPT = """
            你是日报记者（Collector）。根据报告计划，调用提供的工具采集真实数据，按板块归类并归纳摘要。
            规则：
            1. 必须调用工具拉取真实数据，禁止编造；按计划板块的 dataSource 调对应工具。
            2. 每条素材出 summary（简短摘要）与 ref（如时间或标题）。
            3. 某数据源为空则该板块 items 为空、保留板块名。
            4. 严格输出结构化 JSON，字段：sections[{sectionName,items[{source,summary,ref}]}]。
            """;

    /**
     * Planner 专属 ChatClient，注入主编角色 prompt
     *
     * @param chatModel M2 auto-config 创建的 ChatModel
     * @return Planner 专属 ChatClient
     */
    @Bean(name = "plannerChatClient")
    public ChatClient plannerChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).defaultSystem(PLANNER_PROMPT).build();
    }

    /**
     * Writer 专属 ChatClient，注入撰稿角色 prompt
     *
     * @param chatModel M2 auto-config 创建的 ChatModel
     * @return Writer 专属 ChatClient
     */
    @Bean(name = "writerChatClient")
    public ChatClient writerChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).defaultSystem(WRITER_PROMPT).build();
    }

    /**
     * Reviewer 专属 ChatClient，注入审校角色 prompt
     *
     * @param chatModel M2 auto-config 创建的 ChatModel
     * @return Reviewer 专属 ChatClient
     */
    @Bean(name = "reviewerChatClient")
    public ChatClient reviewerChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).defaultSystem(REVIEWER_PROMPT).build();
    }

    // collectorChatClient 在 Task 6 补建（需注入 ReportDataTools 作 defaultTools）
}
