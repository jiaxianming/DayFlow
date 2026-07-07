# DayFlow 实现计划 · 总体路线图 + M0 详细计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 分阶段落地 DayFlow（基于 Spring AI 多智能体的个人日报/周报生成器），本文件给出全项目里程碑路线图，并详细展开第一个可独立交付的里程碑 M0。

**Architecture:** 前后端分离。后端 Spring Boot 3.3 + Spring AI 1.0 + MyBatis-Plus + MySQL 8；编辑部模式 4 Agent（Planner/Collector/Writer/Reviewer）+ 反馈循环；前端 Vue3 + TS。详见 `docs/superpowers/specs/2026-07-07-dayflow-ai-report-design.md`。

**Tech Stack:** Java 21、Maven 3.9、Spring Boot 3.3.x、Spring AI 1.0.x、MyBatis-Plus 3.5.x、MySQL 8、Redis Stack（向量库）、Vue3 + TS + Vite + Pinia + Element Plus。

## Global Constraints

- JDK 21，构建用 Maven（本机无 Gradle）
- Spring Boot 3.3.x + Spring AI 1.0.x
- 实体类**不加** `Entity` 后缀；主键 `@TableId(type = IdType.ASSIGN_ID)`，字段显式 `@TableField`
- 命名后缀：视图对象 `VO`、传输对象 `DTO`、请求 `Request`、响应 `Response`
- Controller 薄层（校验 + 调 Service + `Result` 包装），业务逻辑下沉 Service
- 所有类/方法用**多行 JavaDoc**（`/**` 与 `*/` 各占一行），中文注释
- 统一返回 `Result<T>`；全局异常 `@RestControllerAdvice`
- **提交规范（遵循用户全局指令）**：业务代码修改需经人工审查后才提交；plan 中每个 task 末尾的 commit 步骤是「建议检查点」，实际提交在 checkpoint 审查后进行，不自动 `git commit`
- 错误处理三态覆盖：正常 / 空数据 / 异常

---

## 一、总体里程碑路线图

每个里程碑 = 一份独立 plan，各自产出**可运行、可测试**的软件。

| 里程碑 | 主题 | 核心产出（可验证） | 计划状态 |
|--------|------|--------------------|----------|
| **M0** | 项目骨架与通用基建 | Spring Boot 启动、`Result<T>`、全局异常、health 接口、单测可跑 | ✅ 本文件详细展开 |
| **M1** | 数据层与基础 CRUD | 6 张表 entity/mapper、简单登录、Activity/Note/Task 的 Service+Controller，Postman 跑通 | ⏳ 待细化 |
| **M2** | Spring AI 接入 | 接入 Spring AI、DeepSeek/Ollama 可插拔配置、ChatClient 调通、Redis 向量库 + 笔记 RAG | ⏳ 待细化 |
| **M3** | 多智能体核心（卖点） | Planner/Collector(+Tool Calling)/Writer/Reviewer、`ReportOrchestrationService` 反馈循环、`agent_trace`、报告生成 API | ⏳ 待细化 |
| **M4** | 前端 Web | Vue3 录入页 + 报告页 + Agent 协作时间线可视化 | ⏳ 待细化 |
| **M5** | 开源工程化 | docker-compose 一键起、README、CI、`init.sql` 示例数据、文档 | ⏳ 待细化 |

> M1–M5 的 task 级 TDD 细节将在各自里程碑启动前，由 writing-plans 单独生成，避免预先写大量易过时的代码（YAGNI）。

---

## 二、M0 详细计划：项目骨架与通用基建

**M0 验收标准**：`mvn test` 全绿；`mvn spring-boot:run` 启动成功；`GET http://localhost:8080/api/health` 返回 `{"code":200,"message":"成功","data":"ok"}`；抛业务异常时被全局处理器捕获并返回统一结构。

**M0 文件清单：**

| 文件 | 职责 |
|------|------|
| `dayflow-server/pom.xml` | Maven 依赖与构建（M0 仅 web+validation+lombok+test，无 DB） |
| `.../dayflow/DayflowApplication.java` | Spring Boot 启动类 |
| `.../dayflow/common/ResultCode.java` | 状态码枚举 |
| `.../dayflow/common/Result.java` | 统一返回包装 |
| `.../dayflow/common/BusinessException.java` | 业务异常 |
| `.../dayflow/common/GlobalExceptionHandler.java` | 全局异常处理 |
| `.../dayflow/controller/HealthController.java` | 健康检查接口 |
| `.../dayflow/common/ResultTest.java` | Result 单元测试 |
| `.../dayflow/controller/HealthControllerTest.java` | health 集成测试 |
| `src/main/resources/application.yml` | 应用配置 |

包根：`com.dayflow`。

---

### Task 1: Maven 项目骨架 + 启动类

**Files:**
- Create: `dayflow-server/pom.xml`
- Create: `dayflow-server/src/main/java/com/dayflow/DayflowApplication.java`
- Create: `dayflow-server/src/main/resources/application.yml`
- Test: `dayflow-server/src/test/java/com/dayflow/DayflowApplicationTests.java`

**Interfaces:**
- Produces: 可编译、可启动的 Spring Boot 应用；包根 `com.dayflow`

- [ ] **Step 1: 创建 `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.dayflow</groupId>
    <artifactId>dayflow-server</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>dayflow-server</name>
    <description>DayFlow AI 日报/周报生成器 后端</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建启动类**

```java
package com.dayflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DayFlow 后端启动类
 *
 * @author dayflow
 */
@SpringBootApplication
public class DayflowApplication {

    /**
     * 应用入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DayflowApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 `application.yml`**

```yaml
server:
  port: 8080
spring:
  application:
    name: dayflow-server
```

- [ ] **Step 4: 创建上下文加载测试**

```java
package com.dayflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring 上下文加载测试
 */
@SpringBootTest
class DayflowApplicationTests {

    /**
     * 上下文可正常加载
     */
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 5: 编译并运行测试，验证通过**

Run: `mvn -f dayflow-server/pom.xml -q test`
Expected: BUILD SUCCESS，`DayflowApplicationTests` 通过

- [ ] **Step 6: 启动验证**

Run: `mvn -f dayflow-server/pom.xml spring-boot:run`（看到 `Started DayflowApplication` 后 Ctrl+C 退出）
Expected: 启动成功，无异常

- [ ] **Step 7: 提交检查点（待人工审查）**

```bash
git add dayflow-server/
git commit -m "feat(m0): scaffold spring boot project"
```

---

### Task 2: 统一返回 `Result<T>`（TDD）

**Files:**
- Create: `dayflow-server/src/main/java/com/dayflow/common/ResultCode.java`
- Create: `dayflow-server/src/main/java/com/dayflow/common/Result.java`
- Test: `dayflow-server/src/test/java/com/dayflow/common/ResultTest.java`

**Interfaces:**
- Produces: `Result.success()`、`Result.success(T data)`、`Result.fail(ResultCode)`、`Result.fail(Integer code, String message)`；字段 `code/message/data`

- [ ] **Step 1: 写失败测试**

```java
package com.dayflow.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 统一返回测试
 */
class ResultTest {

    /**
     * success() 无数据
     */
    @Test
    void successWithoutData() {
        Result<Void> result = Result.success();
        assertEquals(200, result.getCode());
        assertEquals("成功", result.getMessage());
        assertNull(result.getData());
    }

    /**
     * success(data) 带数据
     */
    @Test
    void successWithData() {
        Result<String> result = Result.success("ok");
        assertEquals(200, result.getCode());
        assertEquals("ok", result.getData());
    }

    /**
     * fail(ResultCode)
     */
    @Test
    void failWithResultCode() {
        Result<Void> result = Result.fail(ResultCode.BUSINESS_ERROR);
        assertEquals(500, result.getCode());
        assertEquals("业务异常", result.getMessage());
    }

    /**
     * fail(code, message)
     */
    @Test
    void failWithCodeAndMessage() {
        Result<Void> result = Result.fail(400, "参数错误");
        assertEquals(400, result.getCode());
        assertEquals("参数错误", result.getMessage());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `mvn -f dayflow-server/pom.xml -q -Dtest=ResultTest test`
Expected: 编译失败（`Result` / `ResultCode` 不存在）

- [ ] **Step 3: 实现 `ResultCode`**

```java
package com.dayflow.common;

import lombok.Getter;

/**
 * 统一状态码
 *
 * @author dayflow
 */
@Getter
public enum ResultCode {

    /** 成功 */
    SUCCESS(200, "成功"),
    /** 参数错误 */
    PARAM_ERROR(400, "参数错误"),
    /** 未认证 */
    UNAUTHORIZED(401, "未认证"),
    /** 业务异常 */
    BUSINESS_ERROR(500, "业务异常"),
    /** 系统异常 */
    SYSTEM_ERROR(500, "系统异常");

    /**
     * 状态码
     */
    private final Integer code;

    /**
     * 提示信息
     */
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
```

- [ ] **Step 4: 实现 `Result`**

```java
package com.dayflow.common;

import lombok.Getter;

import java.io.Serializable;

/**
 * 统一返回包装
 *
 * @param <T> 数据载荷类型
 * @author dayflow
 */
@Getter
public class Result<T> implements Serializable {

    /**
     * 状态码
     */
    private final Integer code;

    /**
     * 提示信息
     */
    private final String message;

    /**
     * 数据载荷
     */
    private final T data;

    /**
     * 全参构造
     *
     * @param code 状态码
     * @param message 提示信息
     * @param data 数据载荷
     */
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功（无数据）
     *
     * @param <T> 载荷类型
     * @return 成功结果
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功（带数据）
     *
     * @param data 数据
     * @param <T> 载荷类型
     * @return 成功结果
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 失败（按状态码）
     *
     * @param resultCode 状态码枚举
     * @param <T> 载荷类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 失败（自定义码与信息）
     *
     * @param code 状态码
     * @param message 提示信息
     * @param <T> 载荷类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
```

- [ ] **Step 5: 运行测试，确认通过**

Run: `mvn -f dayflow-server/pom.xml -q -Dtest=ResultTest test`
Expected: 4 个测试全部 PASS

- [ ] **Step 6: 提交检查点（待人工审查）**

```bash
git add dayflow-server/src/main/java/com/dayflow/common/Result.java \
        dayflow-server/src/main/java/com/dayflow/common/ResultCode.java \
        dayflow-server/src/test/java/com/dayflow/common/ResultTest.java
git commit -m "feat(common): add unified Result wrapper"
```

---

### Task 3: 业务异常 + 全局异常处理（TDD）

**Files:**
- Create: `dayflow-server/src/main/java/com/dayflow/common/BusinessException.java`
- Create: `dayflow-server/src/main/java/com/dayflow/common/GlobalExceptionHandler.java`
- Test: `dayflow-server/src/test/java/com/dayflow/common/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces: `BusinessException(ResultCode, String)`；`GlobalExceptionHandler` 把 `BusinessException` / `Exception` 转为 `Result.fail(...)`

- [ ] **Step 1: 写失败测试（MockMvc 验证异常被拦截）**

```java
package com.dayflow.common;

import com.dayflow.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理测试
 * <p>依赖 HealthController 的 /api/health/error 测试端点触发业务异常</p>
 */
@WebMvcTest(HealthController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 业务异常被全局处理器捕获，返回统一结构
     */
    @Test
    void businessExceptionReturnsUnifiedResult() throws Exception {
        mockMvc.perform(get("/api/health/error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("业务异常"));
    }
}
```

> 说明：本测试依赖 Task 4 的 `HealthController` 提供 `/api/health/error` 端点。执行顺序上先写此测试（红），Task 4 实现端点后转绿。

- [ ] **Step 2: 运行测试，确认失败**

Run: `mvn -f dayflow-server/pom.xml -q -Dtest=GlobalExceptionHandlerTest test`
Expected: 编译失败（`BusinessException` / `GlobalExceptionHandler` / `HealthController` 尚不存在）

- [ ] **Step 3: 实现 `BusinessException`**

```java
package com.dayflow.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * @author dayflow
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 状态码
     */
    private final ResultCode resultCode;

    /**
     * 按状态码构造业务异常
     *
     * @param resultCode 状态码
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    /**
     * 按状态码 + 自定义信息构造
     *
     * @param resultCode 状态码
     * @param message 自定义信息
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
```

- [ ] **Step 4: 实现 `GlobalExceptionHandler`**

```java
package com.dayflow.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * @author dayflow
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常
     *
     * @param e 业务异常
     * @return 统一失败结果
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getResultCode().getCode(), e.getMessage());
    }

    /**
     * 参数校验异常
     *
     * @param e 校验异常
     * @return 统一失败结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("参数错误");
        log.warn("参数校验失败: {}", message);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 兜底系统异常
     *
     * @param e 系统异常
     * @return 统一失败结果
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleSystem(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }
}
```

- [ ] **Step 5: 暂不运行（依赖 Task 4 端点）**

记录：本 task 的测试在 Task 4 完成后转绿。

- [ ] **Step 6: 提交检查点（待人工审查）**

```bash
git add dayflow-server/src/main/java/com/dayflow/common/BusinessException.java \
        dayflow-server/src/main/java/com/dayflow/common/GlobalExceptionHandler.java \
        dayflow-server/src/test/java/com/dayflow/common/GlobalExceptionHandlerTest.java
git commit -m "feat(common): add business exception and global handler"
```

---

### Task 4: 健康检查端点（含异常测试端点）

**Files:**
- Create: `dayflow-server/src/main/java/com/dayflow/controller/HealthController.java`
- Test: `dayflow-server/src/test/java/com/dayflow/controller/HealthControllerTest.java`

**Interfaces:**
- Produces: `GET /api/health` → `Result.success("ok")`；`GET /api/health/error` → 抛 `BusinessException`（供异常处理测试）

- [ ] **Step 1: 写失败测试**

```java
package com.dayflow.controller;

import com.dayflow.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 健康检查接口测试
 */
@WebMvcTest(HealthController.class)
@Import(GlobalExceptionHandler.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * health 返回成功
     */
    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("ok"));
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `mvn -f dayflow-server/pom.xml -q -Dtest=HealthControllerTest test`
Expected: 编译失败（`HealthController` 不存在）

- [ ] **Step 3: 实现 `HealthController`**

```java
package com.dayflow.controller;

import com.dayflow.common.BusinessException;
import com.dayflow.common.Result;
import com.dayflow.common.ResultCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查控制器
 *
 * @author dayflow
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * 健康检查
     *
     * @return ok
     */
    @GetMapping
    public Result<String> health() {
        return Result.success("ok");
    }

    /**
     * 异常路径（仅用于联调全局异常处理）
     *
     * @return 不返回，恒抛业务异常
     */
    @GetMapping("/error")
    public Result<Void> error() {
        throw new BusinessException(ResultCode.BUSINESS_ERROR);
    }
}
```

- [ ] **Step 4: 运行两个测试类，确认通过**

Run: `mvn -f dayflow-server/pom.xml -q -Dtest=HealthControllerTest,GlobalExceptionHandlerTest test`
Expected: 全部 PASS（含 Task 3 的异常测试转绿）

- [ ] **Step 5: 提交检查点（待人工审查）**

```bash
git add dayflow-server/src/main/java/com/dayflow/controller/HealthController.java \
        dayflow-server/src/test/java/com/dayflow/controller/HealthControllerTest.java
git commit -m "feat(controller): add health endpoint"
```

---

### Task 5: M0 全量验证与收尾

**Files:**
- Create: `.gitignore`
- Modify: 无

- [ ] **Step 1: 创建 `.gitignore`**

```gitignore
# Maven
target/

# IDE
.idea/
*.iml
.vscode/
.settings/
.project
.classpath

# OS
.DS_Store
Thumbs.db

# Logs
*.log
logs/

# Env
.env
*.local
```

- [ ] **Step 2: 全量测试**

Run: `mvn -f dayflow-server/pom.xml -q clean test`
Expected: BUILD SUCCESS，所有测试通过

- [ ] **Step 3: 启动并端到端验证**

Run: `mvn -f dayflow-server/pom.xml spring-boot:run`，另开终端执行：

```bash
curl http://localhost:8080/api/health
# 期望: {"code":200,"message":"成功","data":"ok"}

curl http://localhost:8080/api/health/error
# 期望: {"code":500,"message":"业务异常","data":null}
```

Expected: 两个请求均返回统一 `Result` 结构

- [ ] **Step 4: 提交收尾（待人工审查）**

```bash
git add .gitignore
git commit -m "chore(m0): add gitignore, finalize m0"
git tag m0-complete
```

---

## 三、Self-Review

**1. Spec 覆盖**：M0 对应 spec「整体架构/通用基建层」（`Result`/异常/Controller 薄层规范），是 M1–M5 的地基。spec 的多智能体、数据模型、前端等内容分别由 M1–M5 覆盖，已在路线图标明「待细化」。M0 本身不引入 DB 与 Spring AI（YAGNI），避免无数据源启动失败。

**2. 占位符扫描**：无 TBD/TODO；每个代码步骤均给出完整可编译代码；命令含期望输出。Task 3 测试与 Task 4 端点存在跨 task 依赖，已在 Step 5 显式说明「Task 4 完成后转绿」，非占位符。

**3. 类型一致性**：`Result` 的方法签名（`success()/success(T)/fail(ResultCode)/fail(Integer,String)`）在 Task 2–4 引用一致；`ResultCode.BUSINESS_ERROR(500,"业务异常")` 在 Task 3 测试断言与枚举定义一致；`HealthController` 的 `/api/health` 与 `/api/health/error` 路径在 Task 3/4 测试中引用一致。

**4. 跨 task 依赖说明**：Task 3（异常处理器）的测试需要 Task 4（HealthController 的 error 端点）才能转绿。执行顺序按 Task 1→2→3→4→5；Task 3 的测试在 Task 4 完成后统一验证。这是有意设计，体现「异常处理器 + 触发点」成对验证。
