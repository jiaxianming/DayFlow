package com.dayflow.service;

import com.dayflow.common.BusinessException;
import com.dayflow.common.UserContext;
import com.dayflow.mapper.NoteMapper;
import com.dayflow.pojo.dto.NoteCreateDTO;
import com.dayflow.pojo.dto.NoteUpdateDTO;
import com.dayflow.pojo.entity.NoteEntity;
import com.dayflow.pojo.vo.NoteVO;
import com.dayflow.service.impl.NoteServiceImpl;
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
 * NoteService 测试
 * <p>沿用 ActivityServiceImplTest 范式：@AfterEach clear UserContext、captor 验 userId 注入、
 * updateForbiddenWhenNotOwner、getByIdNotFound/ReturnsVO。</p>
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class NoteServiceImplTest {

    @Mock
    private NoteMapper noteMapper;

    @InjectMocks
    private NoteServiceImpl noteService;

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
        NoteCreateDTO dto = new NoteCreateDTO();
        dto.setTitle("Spring AI 学习笔记");
        dto.setContent("多智能体协作...");
        dto.setTags("AI,笔记");
        when(noteMapper.insert(any(NoteEntity.class))).thenAnswer(inv -> {
            ((NoteEntity) inv.getArgument(0)).setId(100L);
            return 1;
        });
        Long id = noteService.create(dto);
        assertEquals(100L, id);
        // 捕获传给 mapper 的实体，验证 userId 注入确实发生（可证伪 create 的 setUserId 行）
        ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("Spring AI 学习笔记", captor.getValue().getTitle());
        assertEquals("AI,笔记", captor.getValue().getTags());
    }

    @Test
    void updateForbiddenWhenNotOwner() {
        // 当前用户 1L，笔记归属 999L（他人）-> 越权分支 FORBIDDEN(403)
        UserContext.setUserId(1L);
        NoteEntity existed = new NoteEntity();
        existed.setId(5L);
        existed.setUserId(999L);
        when(noteMapper.selectById(5L)).thenReturn(existed);
        NoteUpdateDTO dto = new NoteUpdateDTO();
        dto.setTitle("改标题");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> noteService.update(5L, dto));
        assertEquals(403, ex.getCode());
        // 越权分支不应触发 updateById
        verify(noteMapper, never()).updateById(any(NoteEntity.class));
    }

    @Test
    void getByIdForbiddenWhenNotOwner() {
        // 当前用户 1L，笔记归属 2L（他人）-> 越权分支 FORBIDDEN(403)
        UserContext.setUserId(1L);
        NoteEntity e = new NoteEntity();
        e.setId(9L);
        e.setUserId(2L);
        when(noteMapper.selectById(9L)).thenReturn(e);
        BusinessException ex = assertThrows(BusinessException.class, () -> noteService.getById(9L));
        assertEquals(403, ex.getCode());
    }

    @Test
    void deleteForbiddenWhenNotOwner() {
        // 越权删除：不应触达 deleteById
        UserContext.setUserId(1L);
        NoteEntity e = new NoteEntity();
        e.setId(9L);
        e.setUserId(2L);
        when(noteMapper.selectById(9L)).thenReturn(e);
        BusinessException ex = assertThrows(BusinessException.class, () -> noteService.delete(9L));
        assertEquals(403, ex.getCode());
        verify(noteMapper, never()).deleteById(any());
    }

    @Test
    void getByIdNotFoundThrows() {
        when(noteMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> noteService.getById(999L));
    }

    @Test
    void getByIdReturnsVO() {
        // 归属当前用户：校验通过，返回 VO
        UserContext.setUserId(1L);
        NoteEntity e = new NoteEntity();
        e.setId(1L);
        e.setUserId(1L);
        e.setTitle("测试笔记");
        e.setContent("正文内容");
        e.setTags("test");
        when(noteMapper.selectById(1L)).thenReturn(e);
        NoteVO vo = noteService.getById(1L);
        assertEquals(1L, vo.getId());
        assertEquals("测试笔记", vo.getTitle());
        assertEquals("正文内容", vo.getContent());
        assertEquals("test", vo.getTags());
    }
}
