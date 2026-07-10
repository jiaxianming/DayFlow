package com.dayflow.service;

import com.dayflow.mapper.AgentTraceMapper;
import com.dayflow.pojo.entity.AgentTraceEntity;
import com.dayflow.pojo.enums.AgentName;
import com.dayflow.service.impl.AgentTraceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentTraceService 测试：轨迹字段落库正确
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class AgentTraceServiceImplTest {

    @Mock
    private AgentTraceMapper traceMapper;

    @InjectMocks
    private AgentTraceServiceImpl traceService;

    @Test
    void traceInsertsEntityWithAllFields() {
        when(traceMapper.insert(any(AgentTraceEntity.class))).thenReturn(1);
        traceService.trace(100L, AgentName.PLANNER, 1, "规划输入摘要", "规划输出摘要", 80, 320L, 0);

        ArgumentCaptor<AgentTraceEntity> captor = ArgumentCaptor.forClass(AgentTraceEntity.class);
        verify(traceMapper).insert(captor.capture());
        AgentTraceEntity saved = captor.getValue();
        assertEquals(100L, saved.getReportId());
        assertEquals(AgentName.PLANNER, saved.getAgentName());
        assertEquals(1, saved.getStep());
        assertEquals("规划输入摘要", saved.getInputSummary());
        assertEquals("规划输出摘要", saved.getOutputSummary());
        assertEquals(80, saved.getTokens());
        assertEquals(320, saved.getLatencyMs());
        assertEquals(0, saved.getRetryCount());
    }
}
