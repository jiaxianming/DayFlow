package com.dayflow.pojo.enums;

/**
 * 审校问题类型
 * <p>Reviewer 检出的每一类问题的分类，用于 Writer 在反馈循环中定位修改方向。</p>
 *
 * @author jiaxianming
 */
public enum ReviewIssueType {
    /**
     * 夸大 / 无依据（产出与素材不符）
     */
    OVERCLAIM,
    /**
     * 板块间重复（多个 section 表述同一事项）
     */
    REDUNDANT,
    /**
     * 板块未覆盖（plan 中规划但 draft 缺失）
     */
    MISSING,
    /**
     * 语气不当（非专业 / 非客观汇报口吻）
     */
    TONE
}
