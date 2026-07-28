package com.dayflow.service;

import com.dayflow.common.BusinessException;
import com.dayflow.common.UserContext;
import com.dayflow.mapper.AgentTraceMapper;
import com.dayflow.mapper.ReportMapper;
import com.dayflow.pojo.dto.ReportCreateDTO;
import com.dayflow.pojo.entity.AgentTraceEntity;
import com.dayflow.pojo.entity.ReportEntity;
import com.dayflow.pojo.enums.AgentName;
import com.dayflow.pojo.enums.ReportStatus;
import com.dayflow.pojo.enums.ReportType;
import com.dayflow.pojo.vo.AgentTraceVO;
import com.dayflow.pojo.vo.ReportVO;
import com.dayflow.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ReportService 测试
 * <p>沿用 ActivityServiceImplTest / NoteServiceImplTest 范式：@AfterEach clear UserContext、
 * captor 验 status 注入、getByIdNotFound/ReturnsVO；并覆盖 T9 特有的 listTraces 走 traceMapper。</p>
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private AgentTraceMapper traceMapper;

    @InjectMocks
    private ReportServiceImpl reportService;

    /**
     * UserContext 基于 ThreadLocal，每个测试结束后必须清理，防止线程复用导致的内存泄漏与跨用例污染
     */
    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void createSetsStatusGenerating() {
        // 模拟 JwtInterceptor 写入当前用户
        UserContext.setUserId(7L);
        ReportCreateDTO dto = new ReportCreateDTO();
        dto.setType(ReportType.DAILY);
        dto.setPeriodStart(LocalDate.now());
        dto.setPeriodEnd(LocalDate.now());
        dto.setTitle("日报");
        when(reportMapper.insert(any(ReportEntity.class))).thenAnswer(inv -> {
            ((ReportEntity) inv.getArgument(0)).setId(7L);
            return 1;
        });
        Long id = reportService.create(dto);
        assertEquals(7L, id);
        // 捕获传给 mapper 的实体，验证 status=GENERATING 且 userId 注入确实发生
        // 删掉 create 里 setStatus(GENERATING) 或 setUserId 行，下方断言会失败
        ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportMapper).insert(captor.capture());
        ReportEntity saved = captor.getValue();
        assertEquals(ReportStatus.GENERATING, saved.getStatus());
        assertEquals(7L, saved.getUserId());
        assertEquals(ReportType.DAILY, saved.getType());
        // M1 仅写元信息，不生成 content（content 留空，M3 多智能体才填）
        assertNull(saved.getContent());
    }

    @Test
    void markGeneratedSetsTitleAndContent() {
        // 编排层 finalize：markGenerated 写入 title/content/token/status，并清空 errorMsg
        ReportEntity e = new ReportEntity();
        e.setId(1L);
        e.setStatus(ReportStatus.GENERATING);
        e.setErrorMsg("旧的失败信息");
        when(reportMapper.selectById(1L)).thenReturn(e);

        reportService.markGenerated(1L, "我的日报", "# 正文", 120);

        ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportMapper).updateById(captor.capture());
        ReportEntity saved = captor.getValue();
        assertEquals(ReportStatus.GENERATED, saved.getStatus());
        assertEquals("我的日报", saved.getTitle());
        assertEquals("# 正文", saved.getContent());
        assertEquals(120, saved.getTokenUsage());
        assertNull(saved.getErrorMsg());
    }

    @Test
    void getByIdNotFoundThrows() {
        when(reportMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> reportService.getById(999L));
    }

    @Test
    void getByIdReturnsVO() {
        // 归属当前用户：校验通过，返回 VO
        UserContext.setUserId(1L);
        ReportEntity e = new ReportEntity();
        e.setId(1L);
        e.setUserId(1L);
        e.setType(ReportType.WEEKLY);
        e.setPeriodStart(LocalDate.of(2026, 7, 1));
        e.setPeriodEnd(LocalDate.of(2026, 7, 7));
        e.setTitle("周报");
        e.setStatus(ReportStatus.GENERATED);
        when(reportMapper.selectById(1L)).thenReturn(e);
        ReportVO vo = reportService.getById(1L);
        assertEquals(1L, vo.getId());
        assertEquals(ReportType.WEEKLY, vo.getType());
        assertEquals("周报", vo.getTitle());
        assertEquals(ReportStatus.GENERATED, vo.getStatus());
    }

    @Test
    void getByIdForbiddenWhenNotOwner() {
        // 当前用户 1L，报告归属 2L（他人）-> 越权分支 FORBIDDEN(403)
        UserContext.setUserId(1L);
        ReportEntity e = new ReportEntity();
        e.setId(9L);
        e.setUserId(2L);              // 他人报告
        when(reportMapper.selectById(9L)).thenReturn(e);
        BusinessException ex = assertThrows(BusinessException.class, () -> reportService.getById(9L));
        assertEquals(403, ex.getCode());
    }

    @Test
    void deleteForbiddenWhenNotOwner() {
        // 越权删除：不应触达 deleteById
        UserContext.setUserId(1L);
        ReportEntity e = new ReportEntity();
        e.setId(9L);
        e.setUserId(2L);
        when(reportMapper.selectById(9L)).thenReturn(e);
        BusinessException ex = assertThrows(BusinessException.class, () -> reportService.delete(9L));
        assertEquals(403, ex.getCode());
        verify(reportMapper, never()).deleteById(any());
    }

    @Test
    void listTracesForbiddenWhenNotOwner() {
        // 越权查 trace：先校验报告归属，不应触达 traceMapper.selectList
        UserContext.setUserId(1L);
        ReportEntity e = new ReportEntity();
        e.setId(9L);
        e.setUserId(2L);
        when(reportMapper.selectById(9L)).thenReturn(e);
        BusinessException ex = assertThrows(BusinessException.class, () -> reportService.listTraces(9L));
        assertEquals(403, ex.getCode());
        verify(traceMapper, never()).selectList(any());
    }

    @Test
    void listTracesReturnsByReportId() {
        // 验证 listTraces 走 traceMapper.selectList 且按 reportId 查、转 VO
        // 归属校验通过：报告归属当前用户
        UserContext.setUserId(1L);
        ReportEntity owner = new ReportEntity();
        owner.setId(100L);
        owner.setUserId(1L);
        when(reportMapper.selectById(100L)).thenReturn(owner);
        AgentTraceEntity t = new AgentTraceEntity();
        t.setId(1L);
        t.setReportId(100L);
        t.setAgentName(AgentName.PLANNER);
        t.setStep(1);
        t.setInputSummary("规划输入");
        t.setOutputSummary("规划输出");
        t.setTokens(50);
        t.setLatencyMs(200);
        t.setRetryCount(0);
        when(traceMapper.selectList(any())).thenReturn(List.of(t));
        List<AgentTraceVO> list = reportService.listTraces(100L);
        assertEquals(1, list.size());
        assertEquals(100L, list.get(0).getReportId());
        assertEquals(AgentName.PLANNER, list.get(0).getAgentName());
        assertEquals(1, list.get(0).getStep());
        // 确实调用的是 traceMapper 而非 reportMapper
        verify(traceMapper).selectList(any());
        verify(reportMapper, never()).selectList(any());
    }
}
