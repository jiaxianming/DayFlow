package com.dayflow.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dayflow.pojo.enums.AgentName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 执行轨迹实体（M1 只读，写入在 M3）
 *
 * @author jiaxianming
 */
@Data
@TableName("agent_trace")
public class AgentTraceEntity implements Serializable {

    /**
     * 主键 ID（雪花 ID）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联报告 ID
     */
    @TableField("report_id")
    private Long reportId;

    /**
     * Agent 名称
     */
    @TableField("agent_name")
    private AgentName agentName;

    /**
     * 执行步骤序号
     */
    @TableField("step")
    private Integer step;

    /**
     * 输入摘要
     */
    @TableField("input_summary")
    private String inputSummary;

    /**
     * 输出摘要
     */
    @TableField("output_summary")
    private String outputSummary;

    /**
     * Token 消耗量
     */
    @TableField("tokens")
    private Integer tokens;

    /**
     * 执行耗时（毫秒）
     */
    @TableField("latency_ms")
    private Integer latencyMs;

    /**
     * 重试次数
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 创建时间（新增时自动填充）
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间（新增/更新时自动填充）
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
