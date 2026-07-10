package com.dayflow.agent.model;

import lombok.Data;

import java.util.List;

/**
 * 审校结果
 * <p>Reviewer 的结构化产出。{@code passed=true} 时编排层结束流程；
 * {@code passed=false} 时编排层将 {@code issues} + {@code suggestions} 回喂 Writer 重写，
 * 最多重试 2 次。</p>
 *
 * @author jiaxianming
 */
@Data
public class ReviewResult {

    /**
     * 是否通过
     */
    private boolean passed;

    /**
     * 问题清单（passed=false 时非空）
     */
    private List<ReviewIssue> issues;

    /**
     * 给 Writer 的修改建议（passed=false 时非空）
     */
    private String suggestions;
}
