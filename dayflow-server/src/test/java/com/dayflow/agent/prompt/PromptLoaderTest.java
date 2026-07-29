package com.dayflow.agent.prompt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PromptLoader 单元测试：纯单元（{@code new PromptLoader()}，不依赖 Spring 上下文 / MySQL / API_KEY），
 * 验证提示词从 classpath 正确加载并缓存、未知名称 fail-fast。
 *
 * @author jiaxianming
 */
class PromptLoaderTest {

    private final PromptLoader loader = new PromptLoader();

    /**
     * 5 个必需提示词均加载成功、非空，且含各自角色关键词（防止文件被误清空 / 调包）。
     */
    @Test
    void loadsAllRequiredPrompts() {
        assertThat(loader.get("planner")).isNotBlank().contains("Planner");
        assertThat(loader.get("writer")).isNotBlank().contains("Writer");
        assertThat(loader.get("reviewer")).isNotBlank().contains("Reviewer");
        assertThat(loader.get("collector")).isNotBlank().contains("Collector");
        assertThat(loader.get("collector-struct")).isNotBlank().contains("整理员");
    }

    /**
     * 未知名称抛 IllegalStateException，避免静默返回 null 导致后续 NPE 难以定位。
     */
    @Test
    void unknownNameThrows() {
        assertThatThrownBy(() -> loader.get("unknown"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown");
    }
}
