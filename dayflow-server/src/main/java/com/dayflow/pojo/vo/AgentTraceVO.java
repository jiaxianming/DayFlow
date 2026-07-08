package com.dayflow.pojo.vo;

import com.dayflow.pojo.enums.AgentName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 执行轨迹视图对象（M1 只读，写入在 M3）
 *
 * @author jiaxianming
 */
@Data
public class AgentTraceVO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 关联报告 ID
     */
    private Long reportId;

    /**
     * Agent 名称（PLANNER / COLLECTOR / WRITER / REVIEWER）
     */
    private AgentName agentName;

    /**
     * 执行步骤序号
     */
    private Integer step;

    /**
     * 输入摘要
     */
    private String inputSummary;

    /**
     * 输出摘要
     */
    private String outputSummary;

    /**
     * Token 消耗量
     */
    private Integer tokens;

    /**
     * 执行耗时（毫秒）
     */
    private Integer latencyMs;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
