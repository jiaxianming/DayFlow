package com.dayflow.pojo.query;

import com.dayflow.pojo.enums.ReportType;
import lombok.Data;

/**
 * 报告查询条件
 *
 * @author jiaxianming
 */
@Data
public class ReportQuery {

    /**
     * 报告类型（可空）
     */
    private ReportType type;

    /**
     * 当前页码（从 1 开始）
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer size = 20;
}
