# DayFlow M2 Spring AI 接入设计

> 日期：2026-07-08（spike 结论回写：2026-07-08）
> 里程碑：M2（Spring AI 接入）
> 上游 spec：`docs/superpowers/specs/2026-07-07-dayflow-ai-report-design.md`（整体架构与多智能体设计）
> 上游 spec：`docs/superpowers/specs/2026-07-08-dayflow-m1-data-layer-design.md`（数据层基线、ResultCode、JWT）
> 下游：`writing-plans` 据此细化为 TDD task

---

## 1. 目标与范围

M2 在 Boot 4.1 + Spring AI 2.0 上**把对话能力打通**，为 M3 多智能体提供可用的 `ChatClient` 地基。M2 是"能跟模型对话"这一层，不含任何 Agent 编排。

**本里程碑交付**：
- Spring AI 2.0 接入（pom 依赖 + 配置）
- DeepSeek（默认）/ Ollama chat **可插拔**，经 Spring AI 2.0 原生选择器 `spring.ai.model.chat` 切换，统一暴露 `ChatClient` bean
- `POST /api/ai/chat`：JWT 鉴权的最小可用对话端点（既是 ChatClient 接通证明，也是最小功能）
- 密钥经环境变量管理 + 启动期 fail-fast 校验
- 测试：单测 mock ChatClient（CI 不连真模型、不花钱）+ 可选 live 冒烟（需 key）

**明确不做（留后续里程碑）**：
- Redis 向量库、embedding 模型、笔记切块、RAG 语义检索 → M3（Collector Agent 的 `searchNotes` 工具真正需要时）
- 结构化输出（`.entity()` / `BeanOutputConverter`）→ M3（Planner / Reviewer 的结构化产出）
- Tool Calling（`@Tool` / `ToolCallingAdvisor`）→ M3（Collector 拉业务数据）
- 多 Agent 编排、反馈循环、`agent_trace` 写入、报告生成 → M3
- ChatClient 流式/异步响应 → M3+
- 多 Provider 运行时动态切换 UI → 后续版本

> ⚠️ 前置依赖：默认 provider 为 DeepSeek 云端，**开发者需自备 `DEEPSEEK_API_KEY`** 才能跑 live；CI 走 mock 不受影响。

---

## 2. 技术基线

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 4.1.0（M1 已就位） | Java 21 |
| Spring AI | **2.0.0** | GA 2026-06-12，已上 Maven Central；要求 Boot 4.x；`spring-ai-bom` 统一管版本 |
| DeepSeek starter | `spring-ai-starter-model-deepseek` | 默认 chat provider（DeepSeek 自有 starter，非 OpenAI 兼容改 base-url） |
| Ollama starter | `spring-ai-starter-model-ollama` | 本地 chat provider，可切换 |
| ChatClient | Spring AI 2.0 主抽象 | `ChatClient.create(chatModel)`；M3 各 Agent 共用基础 |
| 鉴权 | 复用 M1 JWT（jjwt + JwtInterceptor + UserContext） | `/api/ai/**` 自动落入 `/api/**` 拦截 |
| 统一响应 | 复用 M1 `Result<T>` / `ResultCode` / `GlobalExceptionHandler` | 不新增 AI 专用码（失败用 `SYSTEM_ERROR=500`） |

**版本锁定**：`spring-ai.version = 2.0.0`（spike 已确认 GA）。来源：[Spring AI 2.0.0 GA 公告](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now)。

---

## 3. Provider 可插拔架构（核心）

### 3.1 设计决策：Spring AI 2.0 原生选择器（spike 后简化）

> **Spike 结论（2026-07-08 回写）**：原 brainstorming 定的方案 A 是"手搓 `DeepSeekChatModel`/`OllamaChatModel` bean + 关闭两 starter auto-config 杜绝歧义 + `dayflow.ai.*` 自有命名空间"。查 [Spring AI 2.0 官方 DeepSeek 文档](https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html) 后发现：
> 1. `spring.ai.deepseek.chat.enabled` **已被移除，不再有效**；
> 2. 2.0 改用顶层选择器 **`spring.ai.model.chat=deepseek|ollama|none`**（官方原文："This change is done to allow configuration of multiple models"）——框架原生支持多 provider 二选一；
> 3. 双 starter 同 classpath 时，按 `spring.ai.model.chat` 自动激活对应 auto-config（如 `=deepseek` → `DeepSeekChatAutoConfiguration` 建 `DeepSeekChatModel`），**非选中方的 auto-config 自然 back off，无歧义**。
>
> 手搓 bean 因此变为反模式（对抗框架、手写构造 API 更易碎）。经用户确认，**简化**为：provider 选择 + ChatModel 创建交给 auto-config；`AiConfig` 只保留两件善后事。

### 3.2 `AiConfig`（瘦身）

```
dayflow-server/src/main/java/com/dayflow/config/AiConfig.java
```

```java
package com.dayflow.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Spring AI 接入配置
 * <p>Provider 选择与 ChatModel 创建交给 Spring AI 2.0 原生 auto-config
 * （由 spring.ai.model.chat 决定激活 DeepSeek 或 Ollama）。本类只负责：
 * 1) 自建 ChatClient bean（ChatClient.create(chatModel)），供业务层注入与单测 mock；
 * 2) 启动期 fail-fast：provider=deepseek 但 DEEPSEEK_API_KEY 空白时直接报错。</p>
 *
 * @author jiaxianming
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    private final Environment environment;

    public AiConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * 自建 ChatClient，业务层统一注入此 bean
     *
     * @param chatModel auto-config 按 spring.ai.model.chat 选定的 ChatModel
     * @return ChatClient
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    /**
     * 启动期 fail-fast：DeepSeek 缺 key 不让带病启动
     */
    @PostConstruct
    public void validate() {
        String provider = environment.getProperty("spring.ai.model.chat", "deepseek");
        if ("deepseek".equals(provider)) {
            String key = environment.getProperty("spring.ai.deepseek.api-key", "");
            if (key.isBlank()) {
                throw new IllegalStateException(
                        "DayFlow 启动失败：spring.ai.model.chat=deepseek 但未配置 DEEPSEEK_API_KEY。"
                                + "请设置环境变量 DEEPSEEK_API_KEY，或改用 DAYFLOW_AI_PROVIDER=ollama。");
            }
        }
        log.info("DayFlow AI provider = {}", provider);
    }
}
```

### 3.3 关键点

- **无手动 bean 构造、无 auto-config exclude**：`spring.ai.model.chat` 让非选中 provider 的 auto-config 自然 back off。
- **自建 `ChatClient` bean**：`ChatClient.create(chatModel)` 为 2.0 文档原句；提供一个干净的注入点便于 M2 单测直接 mock `ChatClient`（而非 ChatModel）。即便 Spring AI 也 auto-config 了 `ChatClient`，Spring 默认用户 bean 优先（`@ConditionalOnMissingBean`）。
- **fail-fast**：`@PostConstruct` 在 context 刷新期执行，抛 `IllegalStateException` 即中止启动并给出修复指引。
- **`ChatClient` 调用链（2.0 确认）**：`chatClient.prompt().user(msg).call().content()`。

---

## 4. 配置与密钥

`application.yml` 在现有 `spring:` 块下新增 `ai:`（provider/密钥全用 Spring AI 2.0 原生属性，由 auto-config 消费；环境变量占位符沿用 M1 `${ENV:default}` 模式）：

```yaml
spring:
  # ... 现有 application / mvc / web / datasource / sql 保持不变 ...
  ai:
    # chat provider 选择器：deepseek（默认，云端）/ ollama（本地）/ none
    model:
      chat: ${DAYFLOW_AI_PROVIDER:deepseek}
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:}
      chat:
        model: ${DAYFLOW_DEEPSEEK_MODEL:deepseek-chat}
    ollama:
      base-url: ${DAYFLOW_OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        model: ${DAYFLOW_OLLAMA_MODEL:qwen2.5}
```

- 密钥**只走环境变量，不入库不入仓**（`.env` 已在 `.gitignore`）。
- 切换：`DAYFLOW_AI_PROVIDER=ollama`（其余有合理默认，配合本地 Ollama 即可）。
- **采用 Spring AI 原生 `spring.ai.*` 属性**（非 `dayflow.ai.*`）：auto-config 直接消费；业务层 `AiServiceImpl` 经 `Environment` 读 `spring.ai.model.chat` 与当前 provider 的 `*.chat.model`，填进 `ChatVO` 元信息，无需自定义 properties 类。
- DeepSeek 有效模型含 `deepseek-chat`（spec 默认值；2.0 默认 `deepseek-v4-flash`，亦可）。
- `.env.example` 补 `DEEPSEEK_API_KEY=`（M5 工程化统一补，M2 先 spec 注明）。

---

## 5. 验证端点 —— `POST /api/ai/chat`

沿用 M1 范式（Controller 薄层 / Service 接口 + 实现分离 / `Result<T>` / JWT 鉴权 / `@author jiaxianming`）。

| 层 | 文件 | 职责 |
|---|---|---|
| DTO | `pojo/dto/ChatRequestDTO` | `{ message: @NotBlank(message="消息不能为空") }` |
| VO | `pojo/vo/ChatVO` | `{ reply: String, provider: String, model: String }` |
| Service 接口 | `service/AiService` | `ChatVO chat(ChatRequestDTO dto)` |
| Service 实现 | `service/impl/AiServiceImpl` | 注入 `ChatClient` + `Environment`；调 `chatClient.prompt().user(msg).call().content()`；附 provider/model；LLM 异常 → `BusinessException(SYSTEM_ERROR)` |
| Controller | `controller/AiController` | `POST /api/ai/chat`，`@Valid` + 一行 Service 调用 + `Result` 包装 |

**调用范式**（Service 内，2.0 确认）：

```java
String reply;
try {
    reply = chatClient.prompt().user(dto.getMessage()).call().content();
} catch (Exception e) {
    log.error("AI 调用失败", e);
    throw new BusinessException(ResultCode.SYSTEM_ERROR, "AI 服务调用失败，请稍后重试");
}
return new ChatVO(reply == null ? "" : reply, currentProvider(), currentModel());
```

- **provider/model 元信息**：从 `Environment` 读 `spring.ai.model.chat`（provider）与对应 provider 的 `*.chat.model`（model），便于联调时一眼确认走 DeepSeek 还是 Ollama。
- **鉴权**：`/api/ai/**` 自动落入现有 `JwtInterceptor` 的 `/api/**` 拦截（与 `/api/activities` 同），无需改拦截器；`UserContext` 可用（M2 暂不消费 userId，M3 报告生成才用）。
- **同步调用**：M2 最简同步；流式/异步留 M3+。

---

## 6. 错误处理（三态覆盖）

| 场景 | 处理 | code |
|---|---|---|
| 正常 | 返回 reply + provider/model | 200 |
| 空消息 | `@Valid @NotBlank` → `GlobalExceptionHandler` 校验分支 | 400 |
| 未带 / 无效 JWT | `JwtInterceptor` | 401 |
| LLM 调用失败（网络 / 超时 / 鉴权 / 限流） | `AiServiceImpl` catch Spring AI 异常 → `throw new BusinessException(ResultCode.SYSTEM_ERROR, "AI 服务调用失败，请稍后重试")` | 500 |
| DeepSeek 缺 key 启动 | `AiConfig.validate()` `@PostConstruct` 抛 `IllegalStateException` | 启动中止（非 HTTP） |

- **不新增 AI 专用 `ResultCode`**：M2 用 `SYSTEM_ERROR`（500）兜底；M3 报告失败语义（`report.status=FAILED` + `error_msg`）再细化。
- **重试/超时**：沿用 Spring AI 内置 `spring.ai.retry`（默认 max-attempts=10）；M2 不改默认，M3 按需调优。
- **不向客户端泄露供应商原始报错**：统一友好提示 + 服务端 ERROR 日志含堆栈（遵循全局日志规范）。

---

## 7. 测试策略（对齐 M1 手法）

| 测试 | 手段 | 覆盖 |
|---|---|---|
| `AiServiceImplTest` | `@ExtendWith(MockitoExtension.class)`，`@Mock(answer=RETURNS_DEEP_STUBS) ChatClient`（桩 `.prompt().user(any).call().content()` 链）+ `@Mock Environment` | 正常回复映射（provider/model 附加）；LLM 异常→`BusinessException` code=500 |
| `AiControllerTest` | `@WebMvcTest` 切片 + `@MockitoBean AiService` + `@MockitoBean JwtUtil` + 排除 `WebConfig` + `@Import(GlobalExceptionHandler.class)`（M1 范本 `ActivityControllerTest`） | 200 正常、空 message→400 |
| `AiConfigTest` | `@SpringBootTest`（或 `ApplicationContextRunner`）：注入 `ChatClient` bean 成功；fail-fast：deepseek + 空 key → 启动抛 `IllegalStateException` | bean 装配 + fail-fast |
| **Live 冒烟（可选）** | `@EnabledIfEnvironmentVariable(named="DEEPSEEK_API_KEY", matches=".+")` | 真调 DeepSeek "说一句话"，断言非空；CI 无 key 自动跳过，合并前手动跑 |

- 单测**全部 mock**：CI 不连真模型、不花钱、不依赖网络。
- byte-buddy-agent javaagent 已在 `pom.xml`（M1 修复），Mockito 在 JDK21 沙箱下正常；`RETURNS_DEEP_STUBS` 桩 ChatClient 流式链是 Mockito 标准手法。
- 与 M1 的 45 测试同构，M2 预计新增约 8–12 个测试。

---

## 8. pom 依赖

```xml
<properties>
    <spring-ai.version>2.0.0</spring-ai.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Spring AI：DeepSeek（默认）+ Ollama（可切换）两 starter；
         provider 由 spring.ai.model.chat 选择，ChatModel 由 auto-config 创建；
         ChatClient 由 AiConfig 自建 -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-deepseek</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-ollama</artifactId>
    </dependency>
</dependencies>
```

- 引 BOM 统一管 Spring AI 全家版本；deepseek + ollama 两 starter 都引。
- Spring AI 2.0 要求 Boot 4.x —— 已满足（Boot 4.1）。

---

## 9. 验收标准

1. `mvn clean test` 全绿（mock 测试，不连真模型）。
2. 配 `DEEPSEEK_API_KEY` 启动成功 → `POST /api/ai/chat`（带 JWT + `{message}`）返回模型真实回复，VO `provider=deepseek`。
3. `DAYFLOW_AI_PROVIDER=ollama`（本地起 Ollama + 拉模型）→ 同一端点走 Ollama 回复，VO `provider=ollama`。
4. 无 key 启动 → fail-fast 清晰报错（提示设置 `DEEPSEEK_API_KEY`）。
5. 四态端到端冒烟：正常 / 空 message→400 / 无 token→401 / 调用异常→500。

---

## 10. 任务预览

`writing-plans` 据此细化为 TDD task（每 task 红→绿 + feature 分支 task 级提交）：

- **T1** Spring AI 接入：`pom`（BOM 2.0.0 + 两 starter）+ `AiConfig`（ChatClient bean + fail-fast）+ `application.yml` 增 `spring.ai.*` + `AiConfigTest`（装配 + fail-fast）
- **T2** `AiService` / `AiServiceImpl` + `ChatRequestDTO` + `ChatVO` + `AiServiceImplTest`（正常 + 异常→500）
- **T3** `AiController` + `AiControllerTest`（200 + 400 切片）
- **T4** 收尾：`mvn clean test` 全绿 + live 冒烟（可选）+ 端到端四态验证 + tag `m2-complete`

---

## 11. 风险与 fallback

| 风险 | 应对 |
|------|------|
| ~~关闭 starter auto-config 的 `*.chat.enabled` 是否存在~~ | **已解决（spike）**：`*.chat.enabled` 已移除，2.0 用 `spring.ai.model.chat` 选择器；无需 exclude |
| ~~双 starter 同 classpath 的 auto-config 冲突~~ | **已解决（spike）**：`spring.ai.model.chat` 让非选中 auto-config 自然 back off |
| ~~手搓 DeepSeek/Ollama model 构造 API 易碎~~ | **已规避**：简化方案不再手搓，ChatModel 交 auto-config；`ChatClient.create(chatModel)` 稳定 |
| 自建 `ChatClient` bean 与 Spring AI auto-config 的 `ChatClient` 冲突 | Spring 默认用户 bean 优先（`@ConditionalOnMissingBean`）；若仍冲突，退回注入 auto-config 的 `ChatClient`（plan T1 验证） |
| Spring AI 2.0 GA 较新（约 1 个月），细节偶有粗糙 | T1 优先验证启动 + bean 装配 + 一次真实调用；问题回写 spec |
| DeepSeek 无 embedding 接口 | M2 不做 embedding/RAG（已推 M3）；M3 做 RAG 时用 Ollama `nomic-embed-text`（768 维）+ Redis 向量库 |
| live 测试需要真实 key / 本地 Ollama | CI 走 mock；live 冒烟用 `@EnabledIfEnvironmentVariable` 门控，合并前手动跑 |

---

## 12. 与上游 spec 的差异说明

`docs/superpowers/specs/2026-07-07-dayflow-ai-report-design.md` 基于 Spring Boot 3.3 + Spring AI 1.0 编写，技术栈已在 M1 决策 M-9 升级为 Boot 4.1 + Spring AI 2.0。本 spec 的 Spring AI 代码示例以 2.0 为准；上游 spec 的多智能体设计（4 Agent + 反馈循环 + Tool Calling）仍是 M3 的权威设计，不受本里程碑影响，M3 启动时按 Spring AI 2.0 重写 Agent 代码示例。
