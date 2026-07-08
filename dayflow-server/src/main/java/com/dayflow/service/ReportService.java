package com.dayflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dayflow.pojo.dto.ReportCreateDTO;
import com.dayflow.pojo.query.ReportQuery;
import com.dayflow.pojo.vo.AgentTraceVO;
import com.dayflow.pojo.vo.ReportVO;

import java.util.List;

/**
 * 报告服务接口
 * <p>M1 数据层：create（仅元信息）/ getById / delete / page；listTraces 提供 agent_trace 只读访问。</p>
 *
 * @author jiaxianming
 */
public interface ReportService {

    /**
     * 创建报告（仅写元信息，status=GENERATING，不生成 content）
     *
     * @param dto 报告创建入参
     * @return 新建报告 ID
     */
    Long create(ReportCreateDTO dto);

    /**
     * 按 ID 查询报告
     *
     * @param id 报告 ID
     * @return 报告视图
     */
    ReportVO getById(Long id);

    /**
     * 删除报告
     *
     * @param id 报告 ID
     */
    void delete(Long id);

    /**
     * 分页查询当前用户的报告（按类型过滤）
     *
     * @param query 查询条件
     * @return 报告分页
     */
    IPage<ReportVO> page(ReportQuery query);

    /**
     * 查询某报告的 Agent 执行轨迹（按 step 升序）
     *
     * @param reportId 报告 ID
     * @return 轨迹视图列表
     */
    List<AgentTraceVO> listTraces(Long reportId);
}
