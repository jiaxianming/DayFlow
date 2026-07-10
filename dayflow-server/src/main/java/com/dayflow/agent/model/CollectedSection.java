package com.dayflow.agent.model;

import lombok.Data;

import java.util.List;

/**
 * 素材板块
 * <p>对应 {@link PlanSection} 的同名板块，承载该板块采集到的素材条目清单。</p>
 *
 * @author jiaxianming
 */
@Data
public class CollectedSection {

    /**
     * 板块名称（与 PlanSection.name 对应）
     */
    private String sectionName;

    /**
     * 该板块的素材条目
     */
    private List<CollectedItem> items;
}
