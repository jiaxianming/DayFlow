package com.dayflow.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayflow.agent.model.ActivityItem;
import com.dayflow.agent.model.NoteItem;
import com.dayflow.agent.model.TaskItem;
import com.dayflow.agent.orchestration.AgentContext;
import com.dayflow.mapper.ActivityMapper;
import com.dayflow.mapper.NoteMapper;
import com.dayflow.mapper.TaskMapper;
import com.dayflow.pojo.entity.ActivityEntity;
import com.dayflow.pojo.entity.NoteEntity;
import com.dayflow.pojo.entity.TaskEntity;
import com.dayflow.pojo.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 报告数据采集工具，注册给 Collector Agent。
 * <p>userId 一律从 {@link AgentContext} 读取（后端掌控），LLM 全程不接触 userId，
 * 杜绝 LLM 幻觉导致越权拉取他人数据。userId 缺失时安全降级返回空列表（不抛异常）。</p>
 * <p>三个 {@code @Tool} 方法分别覆盖 Planner 规划的三类数据源（ACTIVITY/TASK/NOTE），
 * 由 Spring AI 在 {@code collectorChatClient} 的 {@code defaultTools} 中注册，
 * LLM 依板块 dataSource 自主调用取数。</p>
 *
 * @author jiaxianming
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportDataTools {

    /**
     * 日期入参格式（yyyy-MM-dd）
     */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 工作活动 Mapper
     */
    private final ActivityMapper activityMapper;

    /**
     * 待办任务 Mapper
     */
    private final TaskMapper taskMapper;

    /**
     * 学习笔记 Mapper
     */
    private final NoteMapper noteMapper;

    /**
     * 查询指定日期范围内用户的工作活动记录。
     * <p>userId 取自 {@link AgentContext#getUserId()}；缺失则返回空列表。</p>
     *
     * @param startDate 起始日期 yyyy-MM-dd
     * @param endDate   结束日期 yyyy-MM-dd
     * @return 活动记录轻量视图列表（按发生时间倒序）
     */
    @Tool(description = "查询指定日期范围内用户的工作活动记录（含分类与发生时间）")
    public List<ActivityItem> listActivities(String startDate, String endDate) {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            log.warn("listActivities: AgentContext.userId 缺失，安全降级返回空列表");
            return List.of();
        }
        List<ActivityEntity> entities = activityMapper.selectList(
                new LambdaQueryWrapper<ActivityEntity>()
                        .eq(ActivityEntity::getUserId, userId)
                        .ge(ActivityEntity::getOccurredAt, parseStart(startDate))
                        .le(ActivityEntity::getOccurredAt, parseEnd(endDate))
                        .orderByDesc(ActivityEntity::getOccurredAt));
        return entities.stream()
                .map(e -> new ActivityItem(e.getContent(),
                        e.getCategory() == null ? null : e.getCategory().name(),
                        formatTime(e.getOccurredAt())))
                .toList();
    }

    /**
     * 查询指定日期范围内已完成的任务。
     * <p>userId 取自 {@link AgentContext#getUserId()}；缺失则返回空列表。
     * 仅返回 status=DONE 且完成时间落在区间内的任务。</p>
     *
     * @param startDate 起始日期 yyyy-MM-dd
     * @param endDate   结束日期 yyyy-MM-dd
     * @return 已完成任务轻量视图列表（按完成时间倒序）
     */
    @Tool(description = "查询指定日期范围内已完成的任务")
    public List<TaskItem> listCompletedTasks(String startDate, String endDate) {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            log.warn("listCompletedTasks: AgentContext.userId 缺失，安全降级返回空列表");
            return List.of();
        }
        List<TaskEntity> entities = taskMapper.selectList(
                new LambdaQueryWrapper<TaskEntity>()
                        .eq(TaskEntity::getUserId, userId)
                        .eq(TaskEntity::getStatus, TaskStatus.DONE)
                        .ge(TaskEntity::getCompletedAt, parseStart(startDate))
                        .le(TaskEntity::getCompletedAt, parseEnd(endDate))
                        .orderByDesc(TaskEntity::getCompletedAt));
        return entities.stream()
                .map(t -> new TaskItem(t.getTitle(),
                        t.getStatus() == null ? null : t.getStatus().name(),
                        formatTime(t.getCompletedAt())))
                .toList();
    }

    /**
     * 按关键词检索学习笔记（M3 无 RAG，走 LIKE 标题/内容/标签匹配）。
     * <p>userId 取自 {@link AgentContext#getUserId()}；缺失则返回空列表。
     * 关键词为空时退化为按日期区间全量检索。</p>
     *
     * @param keywords  关键词（匹配标题/内容/标签，任一命中即可）
     * @param startDate 起始日期 yyyy-MM-dd
     * @param endDate   结束日期 yyyy-MM-dd
     * @return 笔记轻量视图列表（按创建时间倒序）
     */
    @Tool(description = "按关键词检索学习笔记（标题/内容/标签匹配）")
    public List<NoteItem> searchNotes(String keywords, String startDate, String endDate) {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            log.warn("searchNotes: AgentContext.userId 缺失，安全降级返回空列表");
            return List.of();
        }
        String kw = keywords == null || keywords.isBlank() ? null : keywords;
        LambdaQueryWrapper<NoteEntity> wrapper = new LambdaQueryWrapper<NoteEntity>()
                .eq(NoteEntity::getUserId, userId)
                .ge(NoteEntity::getCreatedAt, parseStart(startDate))
                .le(NoteEntity::getCreatedAt, parseEnd(endDate));
        if (kw != null) {
            wrapper.and(w -> w.like(NoteEntity::getTitle, kw)
                    .or().like(NoteEntity::getContent, kw)
                    .or().like(NoteEntity::getTags, kw));
        }
        wrapper.orderByDesc(NoteEntity::getCreatedAt);
        List<NoteEntity> entities = noteMapper.selectList(wrapper);
        return entities.stream()
                .map(n -> new NoteItem(n.getTitle(), n.getTags(), n.getContent()))
                .toList();
    }

    /**
     * 把 yyyy-MM-dd 解析为当天 00:00:00
     *
     * @param date 日期字符串
     * @return 当天起始时间
     */
    private LocalDateTime parseStart(String date) {
        return LocalDate.parse(date, DATE_FMT).atStartOfDay();
    }

    /**
     * 把 yyyy-MM-dd 解析为当天 23:59:59
     *
     * @param date 日期字符串
     * @return 当天结束时间
     */
    private LocalDateTime parseEnd(String date) {
        return LocalDate.parse(date, DATE_FMT).atTime(23, 59, 59);
    }

    /**
     * 时间格式化为字符串，null 返回 null
     *
     * @param time 待格式化时间
     * @return ISO 字符串或 null
     */
    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.toString();
    }
}
