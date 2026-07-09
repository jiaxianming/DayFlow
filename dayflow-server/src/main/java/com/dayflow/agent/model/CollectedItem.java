package com.dayflow.agent.model;

import lombok.Data;

/**
 * 单条素材
 * <p>Collector 对原始记录（activity/task/note）做的摘要条目；{@code ref} 为可追溯的引用标识，
 * 便于 Writer/Reviewer 校验产出是否有据可依。</p>
 *
 * @author jiaxianming
 */
@Data
public class CollectedItem {

    /**
     * 来源类型，如 ACTIVITY / TASK / NOTE
     */
    private String source;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 引用标识（如原记录的时间 / 标题）
     */
    private String ref;
}
