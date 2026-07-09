package com.dayflow.agent.model;

import com.dayflow.pojo.enums.DataSource;
import lombok.Data;

/**
 * 计划板块
 * <p>{@code dataSource} 指示 Collector 该板块从哪类数据源采集，{@code focus} 给出采集聚焦点。</p>
 *
 * @author jiaxianming
 */
@Data
public class PlanSection {

    /**
     * 板块名称
     */
    private String name;

    /**
     * 数据源类型（ACTIVITY / TASK / NOTE）
     */
    private DataSource dataSource;

    /**
     * 采集聚焦点（给 Collector 的提示）
     */
    private String focus;
}
