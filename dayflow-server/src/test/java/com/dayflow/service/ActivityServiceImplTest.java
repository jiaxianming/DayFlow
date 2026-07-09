package com.dayflow.service;

import com.dayflow.common.BusinessException;
import com.dayflow.common.UserContext;
import com.dayflow.mapper.ActivityMapper;
import com.dayflow.pojo.dto.ActivityCreateDTO;
import com.dayflow.pojo.dto.ActivityUpdateDTO;
import com.dayflow.pojo.entity.ActivityEntity;
import com.dayflow.pojo.enums.ActivityCategory;
import com.dayflow.pojo.vo.ActivityVO;
import com.dayflow.service.impl.ActivityServiceImpl;
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
 * ActivityService 测试
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceImplTest {

    @Mock
    private ActivityMapper activityMapper;

    @InjectMocks
    private ActivityServiceImpl activityService;

    /**
     * UserContext 基于 ThreadLocal，每个测试结束后必须清理，防止线程复用导致的内存泄漏与跨用例污染
     */
    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void createReturnsId() {
        // 模拟 JwtInterceptor 写入当前用户；若 create 删掉 setUserId 行，下方 captor 断言会失败
        UserContext.setUserId(7L);
        ActivityCreateDTO dto = new ActivityCreateDTO();
        dto.setContent("写代码");
        dto.setCategory(ActivityCategory.WORK);
        when(activityMapper.insert(any(ActivityEntity.class))).thenAnswer(inv -> {
            ((ActivityEntity) inv.getArgument(0)).setId(100L);
            return 1;
        });
        Long id = activityService.create(dto);
        assertEquals(100L, id);
        // 捕获传给 mapper 的实体，验证 userId 注入确实发生（可证伪 create 的 setUserId 行）
        ArgumentCaptor<ActivityEntity> captor = ArgumentCaptor.forClass(ActivityEntity.class);
        verify(activityMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
    }

    @Test
    void updateForbiddenWhenNotOwner() {
        // 当前用户 1L，活动归属 999L（他人）-> 越权分支 FORBIDDEN(403)
        UserContext.setUserId(1L);
        ActivityEntity existed = new ActivityEntity();
        existed.setId(5L);
        existed.setUserId(999L);
        when(activityMapper.selectById(5L)).thenReturn(existed);
        ActivityUpdateDTO dto = new ActivityUpdateDTO();
        dto.setContent("改内容");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.update(5L, dto));
        assertEquals(403, ex.getCode());
        // 越权分支不应触发 updateById
        verify(activityMapper, never()).updateById(any(ActivityEntity.class));
    }

    @Test
    void getByIdForbiddenWhenNotOwner() {
        // 当前用户 1L，活动归属 2L（他人）-> 越权分支 FORBIDDEN(403)
        UserContext.setUserId(1L);
        ActivityEntity e = new ActivityEntity();
        e.setId(9L);
        e.setUserId(2L);
        when(activityMapper.selectById(9L)).thenReturn(e);
        BusinessException ex = assertThrows(BusinessException.class, () -> activityService.getById(9L));
        assertEquals(403, ex.getCode());
    }

    @Test
    void deleteForbiddenWhenNotOwner() {
        // 越权删除：不应触达 deleteById
        UserContext.setUserId(1L);
        ActivityEntity e = new ActivityEntity();
        e.setId(9L);
        e.setUserId(2L);
        when(activityMapper.selectById(9L)).thenReturn(e);
        BusinessException ex = assertThrows(BusinessException.class, () -> activityService.delete(9L));
        assertEquals(403, ex.getCode());
        verify(activityMapper, never()).deleteById(any());
    }

    @Test
    void getByIdNotFoundThrows() {
        when(activityMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> activityService.getById(999L));
    }

    @Test
    void getByIdReturnsVO() {
        // 归属当前用户：校验通过，返回 VO
        UserContext.setUserId(1L);
        ActivityEntity e = new ActivityEntity();
        e.setId(1L);
        e.setUserId(1L);
        e.setContent("测试");
        e.setCategory(ActivityCategory.WORK);
        when(activityMapper.selectById(1L)).thenReturn(e);
        ActivityVO vo = activityService.getById(1L);
        assertEquals(1L, vo.getId());
        assertEquals("测试", vo.getContent());
    }
}
