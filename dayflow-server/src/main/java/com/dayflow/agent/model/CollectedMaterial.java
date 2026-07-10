package com.dayflow.agent.model;

import lombok.Data;

import java.util.List;

/**
 * 采集产出的素材包
 * <p>Collector 的结构化产出，按板块组织原始素材摘要；作为 Writer 的输入上下文。</p>
 *
 * @author jiaxianming
 */
@Data
public class CollectedMaterial {

    /**
     * 各板块的素材集合
     */
    private List<CollectedSection> sections;
}
