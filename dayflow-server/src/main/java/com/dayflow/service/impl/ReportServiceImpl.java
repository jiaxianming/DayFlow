package com.dayflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayflow.common.BusinessException;
import com.dayflow.common.ResultCode;
import com.dayflow.common.UserContext;
import com.dayflow.mapper.AgentTraceMapper;
import com.dayflow.mapper.ReportMapper;
import com.dayflow.pojo.dto.ReportCreateDTO;
import com.dayflow.pojo.entity.AgentTraceEntity;
import com.dayflow.pojo.entity.ReportEntity;
import com.dayflow.pojo.enums.ReportStatus;
import com.dayflow.pojo.query.ReportQuery;
import com.dayflow.pojo.vo.AgentTraceVO;
import com.dayflow.pojo.vo.ReportVO;
import com.dayflow.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 报告服务实现
 * <p>沿用 T6 Activity CRUD 范式，但 T9 有特有结构：注入两个 Mapper（ReportMapper + AgentTraceMapper）、
 * create 仅写元信息（status=GENERATING，不生成 content）、无 update 方法、listTraces 走 traceMapper 只读 agent_trace。</p>
 *
 * @author jiaxianming
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;

    private final AgentTraceMapper traceMapper;

    @Override
    public Long create(ReportCreateDTO dto) {
        ReportEntity e = new ReportEntity();
        e.setUserId(UserContext.getUserId());
        e.setType(dto.getType());
        e.setPeriodStart(dto.getPeriodStart());
        e.setPeriodEnd(dto.getPeriodEnd());
        e.setTitle(dto.getTitle());
        // M1 仅写元信息：status=GENERATING，content 留空由 M3 多智能体填充
        e.setStatus(ReportStatus.GENERATING);
        reportMapper.insert(e);
        return e.getId();
    }

    @Override
    public ReportVO getById(Long id) {
        ReportEntity e = reportMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在");
        }
        if (!Objects.equals(e.getUserId(), UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作他人报告");
        }
        return toVO(e);
    }

    @Override
    public void delete(Long id) {
        ReportEntity e = reportMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在");
        }
        if (!Objects.equals(e.getUserId(), UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作他人报告");
        }
        reportMapper.deleteById(id);
    }

    @Override
    public IPage<ReportVO> page(ReportQuery q) {
        LambdaQueryWrapper<ReportEntity> w = new LambdaQueryWrapper<ReportEntity>()
                .eq(ReportEntity::getUserId, UserContext.getUserId())
                .eq(q.getType() != null, ReportEntity::getType, q.getType())
                .orderByDesc(ReportEntity::getCreatedAt);
        Page<ReportEntity> p = new Page<>(q.getPage(), q.getSize());
        return reportMapper.selectPage(p, w).convert(this::toVO);
    }

    @Override
    public List<AgentTraceVO> listTraces(Long reportId) {
        // 先校验报告归属：报告不存在 -> NOT_FOUND；非本人报告 -> FORBIDDEN
        ReportEntity report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在");
        }
        if (!Objects.equals(report.getUserId(), UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作他人报告");
        }
        List<AgentTraceEntity> traces = traceMapper.selectList(
                new LambdaQueryWrapper<AgentTraceEntity>()
                        .eq(AgentTraceEntity::getReportId, reportId)
                        .orderByAsc(AgentTraceEntity::getStep));
        return traces.stream().map(this::toTraceVO).toList();
    }

    @Override
    public void markGenerated(Long id, String content, Integer tokenUsage) {
        // 编排层内部 finalize：异步线程无 UserContext，不加 userId 校验（reportId 来自 generate 创建，受信）
        ReportEntity e = reportMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在");
        }
        e.setStatus(ReportStatus.GENERATED);
        e.setContent(content);
        e.setTokenUsage(tokenUsage);
        e.setErrorMsg(null);
        reportMapper.updateById(e);
    }

    @Override
    public void markFailed(Long id, String errorMsg) {
        // 编排层内部 finalize：异步线程无 UserContext，不加 userId 校验（reportId 来自 generate 创建，受信）
        ReportEntity e = reportMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在");
        }
        e.setStatus(ReportStatus.FAILED);
        e.setErrorMsg(errorMsg);
        reportMapper.updateById(e);
    }

    /**
     * 报告实体转视图对象
     *
     * @param e 报告实体
     * @return 报告视图
     */
    private ReportVO toVO(ReportEntity e) {
        ReportVO vo = new ReportVO();
        vo.setId(e.getId());
        vo.setUserId(e.getUserId());
        vo.setType(e.getType());
        vo.setPeriodStart(e.getPeriodStart());
        vo.setPeriodEnd(e.getPeriodEnd());
        vo.setTitle(e.getTitle());
        vo.setContent(e.getContent());
        vo.setStatus(e.getStatus());
        vo.setErrorMsg(e.getErrorMsg());
        vo.setTokenUsage(e.getTokenUsage());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    /**
     * 轨迹实体转视图对象
     *
     * @param e 轨迹实体
     * @return 轨迹视图
     */
    private AgentTraceVO toTraceVO(AgentTraceEntity e) {
        AgentTraceVO vo = new AgentTraceVO();
        vo.setId(e.getId());
        vo.setReportId(e.getReportId());
        vo.setAgentName(e.getAgentName());
        vo.setStep(e.getStep());
        vo.setInputSummary(e.getInputSummary());
        vo.setOutputSummary(e.getOutputSummary());
        vo.setTokens(e.getTokens());
        vo.setLatencyMs(e.getLatencyMs());
        vo.setRetryCount(e.getRetryCount());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }
}
