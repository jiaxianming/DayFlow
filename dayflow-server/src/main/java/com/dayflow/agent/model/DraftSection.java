package com.dayflow.agent.model;

import lombok.Data;

/**
 * 草稿板块
 * <p>{@code content} 为中文 markdown 文本，是 Writer 基于 {@link CollectedSection} 撰写的成稿内容。</p>
 *
 * @author jiaxianming
 */
@Data
public class DraftSection {

    /**
     * 板块名称
     */
    private String name;

    /**
     * 板块内容（中文 markdown）
     */
    private String content;
}
