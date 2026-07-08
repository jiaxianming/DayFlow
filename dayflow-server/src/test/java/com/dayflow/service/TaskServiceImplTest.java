package com.dayflow.service;

import com.dayflow.common.BusinessException;
import com.dayflow.common.UserContext;
import com.dayflow.mapper.TaskMapper;
import com.dayflow.pojo.dto.TaskCreateDTO;
import com.dayflow.pojo.dto.TaskUpdateDTO;
import com.dayflow.pojo.entity.TaskEntity;
import com.dayflow.pojo.enums.TaskStatus;
import com.dayflow.pojo.vo.TaskVO;
import com.dayflow.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TaskService 测试
 * <p>沿用 T6 Activity 范式的测试深度：create 验 userId 注入（captor 可证伪）、
 * update 越权 FORBIDDEN、getById NOT_FOUND / 返回 VO；额外覆盖 complete 状态流转。</p>
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskMapper taskMapper;

    @InjectMocks
    private TaskServiceImpl taskService;

    /**
     * UserContext 基于 ThreadLocal，每个测试结束后必须清理，防止线程复用导致的跨用例污染
     */
    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void createReturnsId() {
        // 模拟 JwtInterceptor 写入当前用户；若 create 删掉 setUserId 行，下方 captor 断言会失败
        UserContext.setUserId(7L);
        TaskCreateDTO dto = new TaskCreateDTO();
        dto.setTitle("写周报");
        dto.setStatus(TaskStatus.TODO);
        when(taskMapper.insert(any(TaskEntity.class))).thenAnswer(inv -> {
            ((TaskEntity) inv.getArgument(0)).setId(100L);
            return 1;
        });
        Long id = taskService.create(dto);
        assertEquals(100L, id);
        // 捕获传给 mapper 的实体，验证 userId 注入确实发生（可证伪 create 的 setUserId 行）
        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("写周报", captor.getValue().getTitle());
        assertEquals(TaskStatus.TODO, captor.getValue().getStatus());
    }

    @Test
    void updateForbiddenWhenNotOwner() {
        // 当前用户 1L，任务归属 999L（他人）-> 越权分支 FORBIDDEN(403)
        UserContext.setUserId(1L);
        TaskEntity existed = new TaskEntity();
        existed.setId(5L);
        existed.setUserId(999L);
        when(taskMapper.selectById(5L)).thenReturn(existed);
        TaskUpdateDTO dto = new TaskUpdateDTO();
        dto.setTitle("改标题");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.update(5L, dto));
        assertEquals(403, ex.getCode());
        // 越权分支不应触发 updateById
        verify(taskMapper, never()).updateById(any(TaskEntity.class));
    }

    @Test
    void getByIdNotFoundThrows() {
        when(taskMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> taskService.getById(999L));
    }

    @Test
    void getByIdReturnsVO() {
        TaskEntity e = new TaskEntity();
        e.setId(1L);
        e.setUserId(1L);
        e.setTitle("测试任务");
        e.setStatus(TaskStatus.DOING);
        when(taskMapper.selectById(1L)).thenReturn(e);
        TaskVO vo = taskService.getById(1L);
        assertEquals(1L, vo.getId());
        assertEquals("测试任务", vo.getTitle());
        assertEquals(TaskStatus.DOING, vo.getStatus());
    }

    @Test
    void completeSetsStatusDoneAndCompletedAt() {
        // complete 核心契约：status -> DONE，completedAt -> 非空
        TaskEntity e = new TaskEntity();
        e.setId(1L);
        e.setStatus(TaskStatus.TODO);
        when(taskMapper.selectById(1L)).thenReturn(e);
        taskService.complete(1L);
        // 显式声明 argThat lambda 参数类型为 TaskEntity，规避 BaseMapper.updateById 重载歧义
        verify(taskMapper).updateById(argThat((TaskEntity t) ->
                t.getStatus() == TaskStatus.DONE && t.getCompletedAt() != null));
    }

    @Test
    void completeNotFoundThrows() {
        when(taskMapper.selectById(404L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> taskService.complete(404L));
        verify(taskMapper, never()).updateById(any(TaskEntity.class));
    }
}
