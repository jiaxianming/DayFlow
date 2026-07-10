package com.dayflow.controller;

import com.dayflow.agent.orchestration.ReportOrchestrationService;
import com.dayflow.common.Result;
import com.dayflow.pojo.dto.ReportCreateDTO;
import com.dayflow.pojo.dto.ReportGenerateDTO;
import com.dayflow.pojo.query.ReportQuery;
import com.dayflow.pojo.vo.AgentTraceVO;
import com.dayflow.pojo.vo.ReportVO;
import com.dayflow.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 报告接口（M1 仅元信息 CRUD + agent_trace 只读）
 * <p>薄层 Controller：参数校验 + 调用 Service + Result 包装。</p>
 *
 * @author jiaxianming
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 报告编排服务（M3：触发 4 Agent 异步生成流水线）
     */
    private final ReportOrchestrationService orchestrationService;

    /**
     * 创建报告（仅写元信息，status=GENERATING）
     *
     * @param dto 报告创建入参
     * @return 新建报告 ID
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ReportCreateDTO dto) {
        return Result.success(reportService.create(dto));
    }

    /**
     * 触发报告生成（异步）：立即返回 reportId，前端轮询状态与轨迹。
     * <p>薄层：{@code @Valid} 校验 + 一次 {@code orchestrationService.generate} 调用；
     * 编排服务在请求线程内建报告(GENERATING) + 提交 4 Agent 异步流水线。</p>
     *
     * @param dto 生成入参（type / date）
     * @return 新建报告 id
     */
    @PostMapping("/generate")
    public Result<Long> generate(@Valid @RequestBody ReportGenerateDTO dto) {
        return Result.success(orchestrationService.generate(dto));
    }

    /**
     * 查询单个报告
     *
     * @param id 报告 ID
     * @return 报告视图
     */
    @GetMapping("/{id}")
    public Result<ReportVO> get(@PathVariable Long id) {
        return Result.success(reportService.getById(id));
    }

    /**
     * 删除报告
     *
     * @param id 报告 ID
     * @return 空载荷成功结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reportService.delete(id);
        return Result.success();
    }

    /**
     * 分页查询报告（按类型过滤）
     *
     * @param query 查询条件
     * @return 分页数据
     */
    @GetMapping
    public Result<?> page(ReportQuery query) {
        return Result.success(reportService.page(query));
    }

    /**
     * 查询某报告的 Agent 执行轨迹（M1 只读，写入在 M3）
     *
     * @param id 报告 ID
     * @return 轨迹列表
     */
    @GetMapping("/{id}/traces")
    public Result<List<AgentTraceVO>> traces(@PathVariable Long id) {
        return Result.success(reportService.listTraces(id));
    }
}
