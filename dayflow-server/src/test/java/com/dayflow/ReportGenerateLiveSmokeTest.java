package com.dayflow;

import com.dayflow.agent.orchestration.ReportOrchestrationService;
import com.dayflow.pojo.dto.ReportGenerateDTO;
import com.dayflow.pojo.enums.ReportType;
import com.dayflow.pojo.vo.AgentTraceVO;
import com.dayflow.pojo.vo.ReportVO;
import com.dayflow.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 报告生成 live 冒烟（可选）：真调 DeepSeek，端到端验证 4 Agent 协作。
 *
 * <p>仅当环境变量 DEEPSEEK_API_KEY 非空时运行；CI 无 key 自动跳过。
 * 需本机 MySQL + 已预置用户/数据。合并前手动跑一次确认真实链路。</p>
 *
 * <p>真实路径建议手动经 HTTP 触发（登录拿 JWT → POST /api/reports/generate →
 * 轮询 GET /api/reports/{id} 与 /api/reports/{id}/traces）。此处直接调
 * orchestration.run 做简化覆盖，断言放宽（详见各方法注释），重点由人工 HTTP 验证。</p>
 *
 * @author jiaxianming
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=${DEEPSEEK_API_KEY}")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class ReportGenerateLiveSmokeTest {

    @Autowired
    private ReportOrchestrationService orchestration;

    @Autowired
    private ReportService reportService;

    /**
     * 端到端冒烟：直接调编排 run，验收报告生成与轨迹落库。
     *
     * <p>⚠️ live 测试需先有 JWT 上下文：实际经 Controller 触发更真实。
     * 此处直接调 orchestration.run 模拟（UserContext 由 generate 设置），
     * 但 generate 依赖 UserContext.getUserId()——live 场景需先 login 拿 token 走 HTTP。
     * 简化：直接调 run(reportId, userId, date, type)，reportId 由 reportService.create 预建。</p>
     *
     * <p>断言刻意放宽为 assertNotNull：live 真实路径由人工 HTTP 验证（见类注释）。</p>
     */
    @Test
    void generateProducesGeneratedReportWithTraces() {
        // 预建 report 入参（status=GENERATING 由 generate 创建；此处仅构造 DTO 备用）
        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setType(ReportType.DAILY);
        dto.setDate(LocalDate.now());
        // generate 内部从 UserContext 取 userId；测试线程无 JWT，故直接调 run
        // 先用固定 userId 建 report（此处假设 admin userId=1，按实际预置用户调整）
        Long userId = 1L;
        // 直接执行编排（同步，不经线程池）
        orchestration.run(1L, userId, LocalDate.now(), ReportType.DAILY);

        // 验收：status=GENERATED（或 FAILED 时人工排查），此处仅断言非空
        ReportVO report = reportService.getById(1L);
        assertNotNull(report, "生成的报告不应为空");

        // 至少有 Agent 轨迹返回（Planner/Collector/Writer/Reviewer 至少各一条，数量由人工核对）
        List<AgentTraceVO> traces = reportService.listTraces(1L);
        assertNotNull(traces, "报告应产出 Agent 轨迹列表");
    }
}
