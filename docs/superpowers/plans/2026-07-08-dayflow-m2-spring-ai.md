# DayFlow M2 Spring AI 接入 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Boot 4.1 项目中接入 Spring AI 2.0，打通 DeepSeek（默认）/ Ollama 可插拔对话能力，交付 JWT 鉴权的 `POST /api/ai/chat` 端点。

**Architecture:** Provider 选择与 `ChatModel` 创建交给 Spring AI 2.0 原生 auto-config（由 `spring.ai.model.chat` 选择，双 starter 同 classpath 无歧义）。`AiConfig` 只自建 `ChatClient` bean + 启动期 fail-fast。`AiServiceImpl` 注入 `ChatClient`，调 `.prompt().user().call().content()`，LLM 异常统一映射 `BusinessException(SYSTEM_ERROR=500)`。

**Tech Stack:** Java 21、Maven、Spring Boot 4.1.0、Spring AI 2.0.0（`spring-ai-bom`）、`spring-ai-starter-model-deepseek` + `spring-ai-starter-model-ollama`、复用 M1（`Result<T>`/`ResultCode`/`GlobalExceptionHandler`/`JwtInterceptor`/`UserContext`）。

## Global Constraints

- 分支：`feature/m2-spring-ai`（已建，HEAD 含 M2 spec）。
- 版本：Spring Boot 4.1.0、Spring AI **2.0.0**（GA 2026-06-12，BOM 管版本）、Java 21、Maven。
- `spring.ai.model.chat` 是 Spring AI 2.0 的 chat provider 选择器（`spring.ai.deepseek.chat.enabled` 已移除，**勿用**）。
- 所有 Java 类 JavaDoc 用**多行格式**（`/**` 与 `*/` 各占一行），`@author jiaxianming`。
- Controller 薄层（校验 + 调 Service + `Result` 包装）；Service 接口与实现分离（接口 `service/`，实现 `service/impl/`）。
- 实体/DTO/VO/Query 后缀规范（本里程碑仅 `ChatRequestDTO`、`ChatVO`）。
- 密钥只走环境变量（`DEEPSEEK_API_KEY` 等），配置用 Spring AI 原生 `spring.ai.*` 属性。
- 切片测试用新包 `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` + `@MockitoBean`（**非**已移除的 `@MockBean`）；排除 `WebConfig` + `@MockitoBean JwtUtil`（M1 范本）。
- `pom.xml` 已含 byte-buddy-agent javaagent（M1 修复），Mockito 在 JDK21 沙箱正常。
- **`@SpringBootTest` 类测试需本机 MySQL**（localhost:3306，root/root，`createDatabaseIfNotExist=true`）—— M1 既有前提。
- 提交：feature 分支 task 级提交为**审查检查点**，遵循全局「不自动提交」——commit 步骤是建议检查点，实际提交在人工审查后进行。
- 不提交 `PageUtils.java.bak`、`AI 日报/`（全程排除）。

---

## File Structure

| 文件 | 职责 | 任务 |
|------|------|------|
| `dayflow-server/pom.xml`（改） | 加 `spring-ai-bom` + 两 starter + `spring-ai.version` | T1 |
| `dayflow-server/src/main/resources/application.yml`（改） | 现有 `spring:` 块下加 `ai:` | T1 |
| `.../config/AiConfig.java`（新） | 自建 `ChatClient` bean + `@PostConstruct` fail-fast | T1 |
| `.../pojo/dto/ChatRequestDTO.java`（新） | `{ message: @NotBlank }` | T2 |
| `.../pojo/vo/ChatVO.java`（新） | `{ reply, provider, model }` | T2 |
| `.../service/AiService.java`（新） | 接口：`ChatVO chat(ChatRequestDTO)` | T2 |
| `.../service/impl/AiServiceImpl.java`（新） | 调 ChatClient + 元信息 + 异常映射 | T2 |
| `.../controller/AiController.java`（新） | `POST /api/ai/chat` 薄层 | T3 |
| `.../config/AiConfigTest.java`（新） | bean 装配 + fail-fast | T1 |
| `.../service/impl/AiServiceImplTest.java`（新） | 正常 + 异常→500 | T2 |
| `.../controller/AiControllerTest.java`（新） | 200 + 400 切片 | T3 |
| `.../AiLiveSmokeTest.java`（新，可选） | `@EnabledIfEnvironmentVariable` 门控 live 冒烟 | T4 |

---

### Task 1: Spring AI 接入（pom + AiConfig + application.yml）

**Files:**
- Modify: `dayflow-server/pom.xml`
- Modify: `dayflow-server/src/main/resources/application.yml`
- Create: `dayflow-server/src/main/java/com/dayflow/config/AiConfig.java`
- Test: `dayflow-server/src/test/java/com/dayflow/config/AiConfigTest.java`

**Interfaces:**
- Consumes: Spring AI auto-config 提供的 `ChatModel`（按 `spring.ai.model.chat` 选定）
- Produces: `ChatClient` bean（业务层注入点）；启动期 DeepSeek 缺 key → `IllegalStateException`

- [ ] **Step 1: pom 加 Spring AI 依赖与版本**

在 `dayflow-server/pom.xml` 的 `<properties>` 内加 `spring-ai.version`（与 `mybatis-plus.version` 同级）：

```xml
        <mybatis-plus.version>3.5.15</mybatis-plus.version>
        <jjwt.version>0.12.6</jjwt.version>
        <spring-ai.version>2.0.0</spring-ai.version>
```

在 `<dependencies>` 末尾（`spring-boot-webmvc-test` 之后、`</dependencies>` 之前）加两 starter：

```xml
        <!-- Spring AI：DeepSeek（默认）+ Ollama（可切换）；
             provider 由 spring.ai.model.chat 选择，ChatModel 由 auto-config 创建 -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-deepseek</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-ollama</artifactId>
        </dependency>
```

在 `<build>` **之前**新增 `<dependencyManagement>`（引 BOM 统一管 Spring AI 版本）：

```xml
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
```

- [ ] **Step 2: 验证依赖可解析**

Run: `mvn -f dayflow-server/pom.xml -q dependency:resolve`
Expected: BUILD SUCCESS（Spring AI 2.0.0 制品从 Maven Central 拉取成功）

- [ ] **Step 3: 写失败测试 `AiConfigTest`（RED）**

```java
package com.dayflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiConfig 测试
 * <p>① ChatClient bean 装配成功（@SpringBootTest，需本机 MySQL）；
 * ② provider=deepseek 且 key 空白时启动 fail-fast（ApplicationContextRunner，不连 DB）。</p>
 *
 * @author jiaxianming
 */
class AiConfigTest {

    /**
     * 完整上下文下 ChatClient bean 存在（provider 默认 deepseek，注入测试 key 绕过 fail-fast）
     */
    @SpringBootTest(properties = "spring.ai.deepseek.api-key=test-key")
    static class ContextLoads {

        @Autowired(required = false)
        private ChatClient chatClient;

        @Test
        void chatClientBeanExists() {
            assertThat(chatClient).isNotNull();
        }
    }

    /**
     * provider=deepseek 且 api-key 空白 → 启动抛 IllegalStateException
     */
    @Test
    void failFastWhenDeepSeekKeyMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(AiConfig.class)
                .withPropertyValues(
                        "spring.ai.model.chat=deepseek",
                        "spring.ai.deepseek.api-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable root = context.getStartupFailure();
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    assertThat(root).isInstanceOf(IllegalStateException.class);
                    assertThat(root.getMessage()).contains("DEEPSEEK_API_KEY");
                });
    }
}
```

- [ ] **Step 4: 运行测试，确认失败**

Run: `mvn -f dayflow-server/pom.xml -q -Dtest=AiConfigTest test`
Expected: 编译失败（`AiConfig` 不存在）

- [ ] **Step 5: application.yml 加 `spring.ai` 配置块**

在 `application.yml` 现有 `spring:` 块内（`sql:` 同级、`spring:` 之下）新增 `ai:`：

```yaml
spring:
  application:
    name: dayflow-server
  mvc:
    # 找不到处理器时抛 NoHandlerFoundException，配合 GlobalExceptionHandler 统一回 404
    throw-exception-if-no-handler-found: true
  web:
    resources:
      # 关闭默认静态资源映射，使未匹配路径走 NoHandlerFoundException → 404
      add-mappings: false
  datasource:
    url: jdbc:mysql://localhost:3306/dayflow?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true
    username: ${DAYFLOW_DB_USER:root}
    password: ${DAYFLOW_DB_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
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
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: null
dayflow:
  jwt:
    # JWT 签名密钥（dev 默认值；生产用环境变量 DAYFLOW_JWT_SECRET 覆盖）
    secret: ${DAYFLOW_JWT_SECRET:zXj9Lp2Qr7TuVwXyZ0a1Bc3De5Fg7Hi9Jk1Lm3Np5Qr7St9Uv1Wx3Yz}
    # JWT 有效期（秒）—— 604800 = 7 天
    expiration: 604800
logging:
  level:
    com.dayflow: debug
```

- [ ] **Step 6: 创建 `AiConfig`**

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

    /**
     * @param environment Spring 环境，用于读取 spring.ai.* 属性做 fail-fast 校验
     */
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

- [ ] **Step 7: 运行测试，确认通过**

Run: `mvn -f dayflow-server/pom.xml -q -Dtest=AiConfigTest test`
Expected: 2 个测试 PASS（`ContextLoads.chatClientBeanExists` 需本机 MySQL；`failFastWhenDeepSeekKeyMissing` 不连 DB）

- [ ] **Step 8: 提交检查点（待人工审查）**

```bash
git add dayflow-server/pom.xml \
        dayflow-server/src/main/resources/application.yml \
        dayflow-server/src/main/java/com/dayflow/config/AiConfig.java \
        dayflow-server/src/test/java/com/dayflow/config/AiConfigTest.java
git commit -m "feat(m2): Spring AI 2.0 接入 + AiConfig（ChatClient bean + fail-fast）"
```

---

### Task 2: AiService + DTO/VO（TDD）

**Files:**
- Create: `dayflow-server/src/main/java/com/dayflow/pojo/dto/ChatRequestDTO.java`
- Create: `dayflow-server/src/main/java/com/dayflow/pojo/vo/ChatVO.java`
- Create: `dayflow-server/src/main/java/com/dayflow/service/AiService.java`
- Create: `dayflow-server/src/main/java/com/dayflow/service/impl/AiServiceImpl.java`
- Test: `dayflow-server/src/test/java/com/dayflow/service/impl/AiServiceImplTest.java`

**Interfaces:**
- Consumes: Task 1 的 `ChatClient` bean；`com.dayflow.common.{BusinessException, ResultCode}`
- Produces: `AiService.chat(ChatRequestDTO) -> ChatVO`（Task 3 Controller 消费）

- [ ] **Step 1: 写失败测试 `AiServiceImplTest`（RED）**

```java
package com.dayflow.service.impl;

import com.dayflow.common.BusinessException;
import com.dayflow.pojo.dto.ChatRequestDTO;
import com.dayflow.pojo.vo.ChatVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AiServiceImpl 单元测试
 * <p>用 RETURNS_DEEP_STUBS 桩 ChatClient 流式链（.prompt().user().call().content()），
 * 验证回复映射 + provider/model 元信息 + LLM 异常映射 500。</p>
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiServiceImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private Environment environment;

    @InjectMocks
    private AiServiceImpl aiService;

    /**
     * 正常调用：返回回复并附 provider/model
     */
    @Test
    void chatReturnsReplyWithMeta() {
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("你好");
        when(environment.getProperty("spring.ai.model.chat", "deepseek")).thenReturn("deepseek");
        when(environment.getProperty("spring.ai.deepseek.chat.model", "deepseek-chat"))
                .thenReturn("deepseek-chat");

        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setMessage("在吗");

        ChatVO vo = aiService.chat(dto);

        assertEquals("你好", vo.getReply());
        assertEquals("deepseek", vo.getProvider());
        assertEquals("deepseek-chat", vo.getModel());
    }

    /**
     * LLM 调用异常 → BusinessException(code=500)
     */
    @Test
    void chatThrowsBusinessExceptionOnLlmFailure() {
        when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new RuntimeException("boom"));

        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setMessage("在吗");

        BusinessException ex = assertThrows(BusinessException.class, () -> aiService.chat(dto));
        assertEquals(500, ex.getCode());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `mvn -f dayflow-server/pom.xml -q -Dtest=AiServiceImplTest test`
Expected: 编译失败（`ChatRequestDTO` / `ChatVO` / `AiService` / `AiServiceImpl` 不存在）

- [ ] **Step 3: 创建 `ChatRequestDTO`**

```java
package com.dayflow.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 对话请求入参
 *
 * @author jiaxianming
 */
@Data
public class ChatRequestDTO {

    /**
     * 用户消息
     */
    @NotBlank(message = "消息不能为空")
    private String message;
}
```

- [ ] **Step 4: 创建 `ChatVO`**

```java
package com.dayflow.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 对话返回视图
 *
 * @author jiaxianming
 */
@Data
@AllArgsConstructor
public class ChatVO {

    /**
     * 模型回复内容
     */
    private String reply;

    /**
     * 实际使用的 provider（deepseek / ollama）
     */
    private String provider;

    /**
     * 实际使用的模型名
     */
    private String model;
}
```

- [ ] **Step 5: 创建 `AiService` 接口**

```java
package com.dayflow.service;

import com.dayflow.pojo.dto.ChatRequestDTO;
import com.dayflow.pojo.vo.ChatVO;

/**
 * AI 对话服务
 *
 * @author jiaxianming
 */
public interface AiService {

    /**
     * 与模型对话
     *
     * @param dto 对话请求
     * @return 含回复与 provider/model 元信息
     */
    ChatVO chat(ChatRequestDTO dto);
}
```

- [ ] **Step 6: 创建 `AiServiceImpl`**

```java
package com.dayflow.service.impl;

import com.dayflow.common.BusinessException;
import com.dayflow.common.ResultCode;
import com.dayflow.pojo.dto.ChatRequestDTO;
import com.dayflow.pojo.vo.ChatVO;
import com.dayflow.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * AI 对话服务实现
 *
 * @author jiaxianming
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    private final ChatClient chatClient;
    private final Environment environment;

    /**
     * @param chatClient  AiConfig 自建的 ChatClient
     * @param environment 读取 spring.ai.* 填充 provider/model 元信息
     */
    public AiServiceImpl(ChatClient chatClient, Environment environment) {
        this.chatClient = chatClient;
        this.environment = environment;
    }

    @Override
    public ChatVO chat(ChatRequestDTO dto) {
        String reply;
        try {
            reply = chatClient.prompt()
                    .user(dto.getMessage())
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI 调用失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "AI 服务调用失败，请稍后重试");
        }
        return new ChatVO(reply == null ? "" : reply, currentProvider(), currentModel());
    }

    /**
     * @return 当前激活 provider（默认 deepseek）
     */
    private String currentProvider() {
        return environment.getProperty("spring.ai.model.chat", "deepseek");
    }

    /**
     * @return 当前 provider 对应的模型名
     */
    private String currentModel() {
        if ("ollama".equals(currentProvider())) {
            return environment.getProperty("spring.ai.ollama.chat.model", "qwen2.5");
        }
        return environment.getProperty("spring.ai.deepseek.chat.model", "deepseek-chat");
    }
}
```

- [ ] **Step 7: 运行测试，确认通过**

Run: `mvn -f dayflow-server/pom.xml -q -Dtest=AiServiceImplTest test`
Expected: 2 个测试 PASS

- [ ] **Step 8: 提交检查点（待人工审查）**

```bash
git add dayflow-server/src/main/java/com/dayflow/pojo/dto/ChatRequestDTO.java \
        dayflow-server/src/main/java/com/dayflow/pojo/vo/ChatVO.java \
        dayflow-server/src/main/java/com/dayflow/service/AiService.java \
        dayflow-server/src/main/java/com/dayflow/service/impl/AiServiceImpl.java \
        dayflow-server/src/test/java/com/dayflow/service/impl/AiServiceImplTest.java
git commit -m "feat(m2): AiService 对话服务 + DTO/VO（含异常映射 500）"
```

---

### Task 3: AiController + JWT 切片测试（TDD）

**Files:**
- Create: `dayflow-server/src/main/java/com/dayflow/controller/AiController.java`
- Test: `dayflow-server/src/test/java/com/dayflow/controller/AiControllerTest.java`

**Interfaces:**
- Consumes: Task 2 的 `AiService.chat(ChatRequestDTO) -> ChatVO`；M1 `Result`、`GlobalExceptionHandler`、`JwtUtil`
- Produces: `POST /api/ai/chat`（JWT 鉴权由全局 `JwtInterceptor` 拦截 `/api/**` 提供）

- [ ] **Step 1: 写失败测试 `AiControllerTest`（RED）**

```java
package com.dayflow.controller;

import com.dayflow.common.GlobalExceptionHandler;
import com.dayflow.common.JwtUtil;
import com.dayflow.pojo.vo.ChatVO;
import com.dayflow.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AiController 测试（@WebMvcTest 切片）
 * <p>排除 WebConfig 以避免 JwtInterceptor 注册到 /api/ai/** 拦截无 token 请求；
 * 用 @MockitoBean 提供 JwtUtil 满足被切片扫描到的 JwtInterceptor 构造依赖。</p>
 *
 * @author jiaxianming
 */
@WebMvcTest(controllers = AiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.dayflow.config.WebConfig.class))
@Import(GlobalExceptionHandler.class)
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiService aiService;

    /**
     * JwtInterceptor 被 @WebMvcTest 自动扫描，构造需要 JwtUtil；
     * 这里 mock 它只为满足上下文依赖，WebConfig 已排除故拦截器不会进入请求链。
     */
    @MockitoBean
    private JwtUtil jwtUtil;

    private final ObjectMapper json = new ObjectMapper();

    /**
     * 正常对话返回 200 + ChatVO
     */
    @Test
    void chatReturns200() throws Exception {
        when(aiService.chat(any())).thenReturn(new ChatVO("你好", "deepseek", "deepseek-chat"));
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.provider").value("deepseek"));
    }

    /**
     * 空 message → @Valid 失败 → 400
     */
    @Test
    void chatWithEmptyMessageReturns400() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `mvn -f dayflow-server/pom.xml -q -Dtest=AiControllerTest test`
Expected: 编译失败（`AiController` 不存在）

- [ ] **Step 3: 创建 `AiController`**

```java
package com.dayflow.controller;

import com.dayflow.common.Result;
import com.dayflow.pojo.dto.ChatRequestDTO;
import com.dayflow.pojo.vo.ChatVO;
import com.dayflow.service.AiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 对话控制器
 * <p>/api/ai/** 由全局 JwtInterceptor 拦截，需携带有效 JWT。</p>
 *
 * @author jiaxianming
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    /**
     * @param aiService AI 对话服务
     */
    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * 与模型对话
     *
     * @param dto 对话请求（message 不能为空）
     * @return 对话结果
     */
    @PostMapping("/chat")
    public Result<ChatVO> chat(@Valid @RequestBody ChatRequestDTO dto) {
        return Result.success(aiService.chat(dto));
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `mvn -f dayflow-server/pom.xml -q -Dtest=AiControllerTest test`
Expected: 2 个测试 PASS

- [ ] **Step 5: 提交检查点（待人工审查）**

```bash
git add dayflow-server/src/main/java/com/dayflow/controller/AiController.java \
        dayflow-server/src/test/java/com/dayflow/controller/AiControllerTest.java
git commit -m "feat(m2): AiController 对话端点 POST /api/ai/chat（JWT 鉴权）"
```

---

### Task 4: 收尾（全量验证 + live 冒烟 + tag）

**Files:**
- Create（可选）: `dayflow-server/src/test/java/com/dayflow/AiLiveSmokeTest.java`

- [ ] **Step 1: 写可选 live 冒烟测试（`@EnabledIfEnvironmentVariable` 门控）**

```java
package com.dayflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DeepSeek live 冒烟测试（可选）
 * <p>仅当环境变量 DEEPSEEK_API_KEY 非空时运行；CI 无 key 自动跳过。
 * 需本机 MySQL（@SpringBootTest）。合并前手动跑一次确认真实链路。</p>
 *
 * @author jiaxianming
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=${DEEPSEEK_API_KEY}")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class AiLiveSmokeTest {

    @Autowired
    private ChatClient chatClient;

    /**
     * 真调 DeepSeek，断言非空回复
     */
    @Test
    void deepSeekReplies() {
        String reply = chatClient.prompt().user("用一个字回答：你好").call().content();
        assertTrue(reply != null && !reply.isBlank(), "DeepSeek 回复不应为空，实际: " + reply);
    }
}
```

- [ ] **Step 2: 全量测试（mock，不连真模型）**

Run: `mvn -f dayflow-server/pom.xml clean test`
Expected: BUILD SUCCESS，M1 的 45 + M2 新增测试全部 PASS（live 冒烟因无 key 自动 disabled）

- [ ] **Step 3: 启动 + live 四态冒烟（需 DEEPSEEK_API_KEY + MySQL）**

设置环境变量后启动：

```bash
export DEEPSEEK_API_KEY=<你的 key>
mvn -f dayflow-server/pom.xml spring-boot:run
```

另开终端，登录拿 token（M1 预置 admin/dayflow123）：

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"dayflow123"}' | sed -E 's/.*"data":"([^"]+)".*/\1/')
echo $TOKEN
```

四态冒烟：

```bash
# ① 正常
curl -s -X POST http://localhost:8080/api/ai/chat \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"用一个字回答：你好"}'
# 期望: {"code":200,"msg":"success","data":{"reply":"...","provider":"deepseek","model":"deepseek-chat"}}

# ② 空 message → 400
curl -s -X POST http://localhost:8080/api/ai/chat \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":""}'
# 期望: {"code":400,"msg":"消息不能为空",...}

# ③ 无 token → 401
curl -s -X POST http://localhost:8080/api/ai/chat \
  -H 'Content-Type: application/json' -d '{"message":"hi"}'
# 期望: {"code":401,...}

# ④ 调用异常路径：构造非法 base-url 触发 500（可选，或信任单测覆盖）
```

- [ ] **Step 4: fail-fast 验证（不设 key 启动）**

```bash
unset DEEPSEEK_API_KEY
mvn -f dayflow-server/pom.xml spring-boot:run 2>&1 | grep -i "DEEPSEEK_API_KEY"
# 期望: 启动失败，日志含 "DayFlow 启动失败：... 未配置 DEEPSEEK_API_KEY ..."
```

- [ ] **Step 5: Ollama 切换验证（可选，需本地 Ollama）**

```bash
# 本机先 ollama pull qwen2.5 并 ollama serve
DAYFLOW_AI_PROVIDER=ollama mvn -f dayflow-server/pom.xml spring-boot:run
# 同 Step 3 的 ① 调用，期望 data.provider=ollama
```

- [ ] **Step 6: 提交检查点 + tag（待人工审查）**

```bash
git add dayflow-server/src/test/java/com/dayflow/AiLiveSmokeTest.java
git commit -m "test(m2): live 冒烟测试（@EnabledIfEnvironmentVariable 门控）"
git tag m2-complete
```

---

## Self-Review

**1. Spec 覆盖**：
- §1 交付物 → T1（接入+AiConfig）、T2（Service+DTO/VO）、T3（端点）、T4（测试+冒烟）✓
- §3 AiConfig（ChatClient bean + fail-fast）→ T1 Step 6 ✓
- §4 配置 `spring.ai.*` → T1 Step 5 ✓
- §5 `POST /api/ai/chat` + DTO/VO/Service/Controller → T2 + T3 ✓
- §6 错误三态（400 @Valid / 401 JwtInterceptor / 500 BusinessException）→ T2 异常映射、T3 校验、T4 四态冒烟 ✓
- §7 测试策略（AiServiceImplTest deep-stubs、AiControllerTest 切片、AiConfigTest 装配+fail-fast、live 门控）→ 各 task ✓
- §8 pom（BOM 2.0.0 + 两 starter）→ T1 Step 1 ✓
- §9 验收 5 条 → T4 Step 2-5 ✓

**2. 占位符扫描**：无 TBD/TODO；每个代码步骤含完整可编译代码；命令含期望输出。

**3. 类型一致性**：
- `AiService.chat(ChatRequestDTO) -> ChatVO`：T2 接口、T2 impl、T3 controller、T2/T3 测试全一致 ✓
- `ChatVO(reply, provider, model)`：T2 VO、T2 impl `new ChatVO(...)`、T3 测试 `new ChatVO("你好","deepseek","deepseek-chat")` 字段顺序一致 ✓
- `ChatRequestDTO.message`：T2 DTO、T2/T3 测试 `setMessage`/`{"message":...}` 一致 ✓
- `ResultCode.SYSTEM_ERROR`（500）：T2 impl + T2 测试断言 `assertEquals(500, ex.getCode())` 一致 ✓
- `BusinessException(ResultCode, String)` 构造签名：T2 impl 用法与 M1 `BusinessException.java` 一致 ✓
- `@MockitoBean JwtUtil`、`excludeFilters WebConfig`、`@Import(GlobalExceptionHandler.class)`：T3 与 M1 `ActivityControllerTest` 范本完全一致 ✓
