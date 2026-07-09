package com.dayflow.agent.model;

import com.dayflow.pojo.enums.ReviewIssueType;
import lombok.Data;

/**
 * 审校问题
 * <p>Reviewer 检出的单条问题，{@code section} 定位出问题板块，{@code type} 标分类，{@code description} 给细节。</p>
 *
 * @author jiaxianming
 */
@Data
public class ReviewIssue {

    /**
     * 出问题的板块名
     */
    private String section;

    /**
     * 问题分类
     */
    private ReviewIssueType type;

    /**
     * 问题描述
     */
    private String description;
}
