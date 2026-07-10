package com.dayflow.agent.tools;

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
import com.dayflow.pojo.enums.ActivityCategory;
import com.dayflow.pojo.enums.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ReportDataTools 测试：按 AgentContext.userId 查询 + 安全降级。
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class ReportDataToolsTest {

    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private NoteMapper noteMapper;

    @InjectMocks
    private ReportDataTools tools;

    @AfterEach
    void clear() {
        AgentContext.clear();
    }

    @Test
    void listActivitiesQueriesByAgentContextUserId() {
        AgentContext.setUserId(7L);
        ActivityEntity e = new ActivityEntity();
        e.setContent("完成需求评审");
        e.setCategory(ActivityCategory.MEETING);
        e.setOccurredAt(LocalDateTime.of(2026, 7, 9, 10, 0));
        when(activityMapper.selectList(any())).thenReturn(List.of(e));

        List<ActivityItem> items = tools.listActivities("2026-07-09", "2026-07-09");

        assertEquals(1, items.size());
        assertEquals("完成需求评审", items.get(0).content());
        assertEquals("MEETING", items.get(0).category());
    }

    @Test
    void listActivitiesReturnsEmptyWhenNoUserId() {
        // userId 缺失（AgentContext 未设）→ 安全降级返回空，不抛异常
        AgentContext.clear();
        List<ActivityItem> items = tools.listActivities("2026-07-09", "2026-07-09");
        assertTrue(items.isEmpty());
    }

    @Test
    void listCompletedTasksQueriesDoneTasks() {
        AgentContext.setUserId(7L);
        TaskEntity t = new TaskEntity();
        t.setTitle("写技术方案");
        t.setStatus(TaskStatus.DONE);
        t.setCompletedAt(LocalDateTime.of(2026, 7, 9, 18, 0));
        when(taskMapper.selectList(any())).thenReturn(List.of(t));

        List<TaskItem> items = tools.listCompletedTasks("2026-07-09", "2026-07-09");

        assertEquals(1, items.size());
        assertEquals("写技术方案", items.get(0).title());
        assertEquals("DONE", items.get(0).status());
    }

    @Test
    void searchNotesMatchesByKeyword() {
        AgentContext.setUserId(7L);
        NoteEntity n = new NoteEntity();
        n.setTitle("Spring AI 学习");
        n.setTags("ai,spring");
        n.setContent("ChatClient 用法");
        when(noteMapper.selectList(any())).thenReturn(List.of(n));

        List<NoteItem> items = tools.searchNotes("ai", "2026-07-09", "2026-07-09");

        assertEquals(1, items.size());
        assertEquals("Spring AI 学习", items.get(0).title());
    }
}
