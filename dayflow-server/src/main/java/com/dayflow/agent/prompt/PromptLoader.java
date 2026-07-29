package com.dayflow.agent.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 提示词加载器：启动时从 classpath 下的 {@code prompts/*.txt} 一次性加载所有角色提示词并缓存。
 * <p>提示词外置为 resources 文件，便于独立维护与调优（改 prompt 无需改 Java 业务代码）。
 * 加载策略：构造期按「必需清单」逐个读取，<strong>任一文件缺失或读取失败即 fail-fast</strong>
 * （抛 {@link IllegalStateException} 使应用启动失败）——提示词缺失时 Agent 无法工作，应尽早暴露。
 * 运行期只读缓存，无 IO。</p>
 *
 * @author jiaxianming
 */
@Component
public class PromptLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptLoader.class);

    /** 提示词文件所在 classpath 目录 */
    private static final String DIR = "prompts/";

    /** 提示词文件后缀 */
    private static final String SUFFIX = ".txt";

    /**
     * 必需加载的提示词清单（与 {@code resources/prompts/} 下文件名一一对应，不含后缀）。
     * 清单内任一文件缺失即视为配置错误，启动失败。
     */
    private static final List<String> REQUIRED = List.of(
            "planner", "writer", "reviewer", "collector", "collector-struct");

    /** 提示词缓存：启动时一次性填充，运行期只读 */
    private final Map<String, String> cache = new HashMap<>();

    /**
     * 构造期加载全部必需提示词。
     *
     * @throws IllegalStateException 任一必需文件缺失或读取失败
     */
    public PromptLoader() {
        for (String name : REQUIRED) {
            cache.put(name, read(name));
        }
        log.info("Agent 提示词加载完成: {}", cache.keySet());
    }

    /**
     * 按名称取提示词。
     *
     * @param name 提示词名（planner / writer / reviewer / collector / collector-struct）
     * @return 提示词全文
     * @throws IllegalStateException 未知名称（防拼写错误静默返回 null）
     */
    public String get(String name) {
        String text = cache.get(name);
        if (text == null) {
            throw new IllegalStateException("未知的 Agent 提示词: " + name);
        }
        return text;
    }

    /**
     * 从 classpath 读取单个提示词文件（UTF-8）。
     *
     * @param name 提示词名（不含后缀）
     * @return 文件全文
     * @throws IllegalStateException 文件缺失或读取失败
     */
    private String read(String name) {
        String path = DIR + name + SUFFIX;
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("Agent 提示词文件缺失: classpath:" + path);
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 Agent 提示词失败: " + path, e);
        }
    }
}
