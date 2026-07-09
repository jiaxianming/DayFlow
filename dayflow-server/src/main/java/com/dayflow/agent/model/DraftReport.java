package com.dayflow.agent.model;

import lombok.Data;

import java.util.List;

/**
 * 撰写产出的草稿
 * <p>Writer 的结构化产出，板块内 {@code content} 为中文 markdown；Reviewer 据此审校。</p>
 *
 * @author jiaxianming
 */
@Data
public class DraftReport {

    /**
     * 草稿标题
     */
    private String title;

    /**
     * 草稿板块清单
     */
    private List<DraftSection> sections;
}
