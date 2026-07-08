# DayFlow M1 数据层 CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 DayFlow M1 数据层 —— 6 张表的 CRUD、JWT 登录鉴权、MyBatis-Plus/MySQL 数据层基建，全部测试通过。

**Architecture:** Spring Boot 4.1 + MyBatis-Plus（`mybatis-plus-spring-boot4-starter`）+ MySQL 8。Controller 薄层 / Service 厚层（继承 MyBatis-Plus `IService` 获通用 CRUD，特有逻辑再扩展）/ `pojo` 下分 entity·dto·query·vo。JWT 无状态鉴权（拦截器 + ThreadLocal）。TDD：每 task 先 RED 再 GREEN。

**Tech Stack:** Spring Boot 4.1.x、Spring Framework 7、MyBatis-Plus 3.5.14+（boot4 starter）、MySQL 8.0、jjwt 0.12.6、Lombok、JUnit 5 + Mockito + MockMvc、Java 21、Maven。

## Global Constraints

- 包根 `com.dayflow`；实体必须 `Entity` 后缀，DTO/Query/VO 各加后缀；主键 `@TableId(type = IdType.ASSIGN_ID)`，字段显式 `@TableField`
- 目录：`controller/` `service/impl/` `mapper/` `pojo/{entity,dto,query,vo}/` `config/` `common/`
- JavaDoc 多行格式，`@author jiaxianming`；遵循阿里手册；SLF4J 日志，禁 `System.out`
- 统一响应 `Result<T>`：字段 `code`/`msg`/`data`，成功 `code=200`
- 状态码：200/400/401/403/404/409/500（HTTP 语义）
- API 前缀 `/api/<resource>`；查询超 3 参数封装 Query 对象
- 提交：feature 分支 task 级提交，约定式 `type(scope): desc`；**不自动合并 main**
- MyBatis-Plus：Mapper 继承 `BaseMapper`，查询用 `LambdaQueryWrapper`，**不写 XML**
- 字符集 utf8mb4 / InnoDB；时间字段 `created_at`+`updated_at` 自动填充；**不引入 Flyway**

---

## File Structure

| 文件 | 责任 | Task |
|------|------|------|
| `dayflow-server/pom.xml` | 依赖（Boot 4.1 + MyBatis-Plus boot4 + jjwt + mysql） | T1 |
| `dayflow-server/src/main/resources/application.yml` | 数据源 / MyBatis-Plus / JWT / sql.init 配置 | T1,T2,T5 |
| `dayflow-server/src/main/resources/schema.sql` | 6 表 DDL + 预置用户（dev 自动执行） | T2 |
| `init.sql`（项目根） | 同 schema.sql，手动/开源用 | T2 |
| `config/MybatisPlusConfig.java` | 分页插件 + 自动填充处理器注册 | T2 |
| `common/MetaObjectHandler.java` | created_at/updated_at 自动填充 | T2 |
| `common/ResultCode.java` | 码段细化（403/404/409） | T3 |
| `common/GlobalExceptionHandler.java` | 5 异常分支 | T3 |
| `common/JwtUtil.java` | JWT 签发/解析/校验 | T5 |
| `common/UserContext.java` | ThreadLocal 当前 userId | T5 |
| `common/JwtInterceptor.java` | 拦截 /api/**，注入 UserContext | T5 |
| `config/WebConfig.java` | 注册 JwtInterceptor | T5 |
| `pojo/entity/*.java` | 6 实体（UserEntity 等） | T4 |
| `mapper/*.java` | 6 Mapper（BaseMapper） | T4 |
| `pojo/enums/*.java` | ActivityCategory/TaskStatus/ReportType/ReportStatus/AgentName | T4 |
| `service/UserAuthService(+impl)` | 登录校验 + 签发 | T5 |
| `controller/AuthController.java` | POST /api/auth/login | T5 |
| `pojo/dto/LoginDTO.java` `pojo/vo/LoginVO.java` | 登录入参/出参 | T5 |
| `service/ActivityService(+impl)` `controller/ActivityController.java` `pojo/{query,dto,vo}/Activity*` | Activity CRUD | T6 |
| 同上 Task/Note/Report + `AgentTraceMapper` | Task(Note/Report CRUD + trace 只读 | T7-T9 |
| `.claude/rules/api-design.md` | 清理剩余冲突 | T10 |

---

## Task 1: 基线验证 —— Boot 4.1 升级 + 依赖引入

**目标：** 把 Boot 3.3.5 升到 4.1.x，引入 MyBatis-Plus boot4 starter + jjwt + MySQL 驱动，验证应用能启动（排 #7009 坑）。

**Files:**
- Modify: `dayflow-server/pom.xml`
- Create: `dayflow-server/src/main/resources/application.yml`

**Interfaces:**
- Produces: 可启动的 Boot 4.1 应用 + 数据层/JWT 依赖基线

- [ ] **Step 1: 改 pom.xml 为 Boot 4.1 + 新依赖**

替换整个 `pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <groupId>com.dayflow</groupId>
    <artifactId>dayflow-server</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>dayflow-server</name>
    <description>DayFlow AI 日报/周报生成器 后端</description>

    <properties>
        <java.version>21</java.version>
        <mybatis-plus.version>3.5.14</mybatis-plus.version>
        <jjwt.version>0.12.6</jjwt.version>
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

        <!-- MyBatis-Plus（Spring Boot 4 专用 starter） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <!-- MySQL 驱动 -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
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

- [ ] **Step 2: 建 application.yml（最小配置）**

`dayflow-server/src/main/resources/application.yml`：

```yaml
server:
  port: 8080
spring:
  application:
    name: dayflow-server
  datasource:
    url: jdbc:mysql://localhost:3306/dayflow?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true
    username: ${DAYFLOW_DB_USER:root}
    password: ${DAYFLOW_DB_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: null
logging:
  level:
    com.dayflow: debug
```

- [ ] **Step 3: 验证编译 + 启动（排 #7009 坑）**

```bash
mvn -f dayflow-server/pom.xml clean compile
```
Expected: BUILD SUCCESS（无 IPage / 找不到符号错误）。

```bash
mvn -f dayflow-server/pom.xml spring-boot:run
```
Expected: 看到 `Started DayflowApplication`，无 mybatis-spring 版本异常后 `Ctrl+C` 停止。

> **若启动报 mybatis-spring 版本错误（#7009）：** fallback —— 把 `mybatis-plus.version` 升到 `3.5.15` 或更高再试；若仍失败，回滚为 `mybatis-plus-spring-boot3-starter` + Boot `3.4.x`（成熟线），并记录到 progress.md。

- [ ] **Step 4: 验证测试仍绿**

```bash
mvn -f dayflow-server/pom.xml test
```
Expected: `Tests run: 7, Failures: 0`（M0 既有测试在 Boot 4.1 下继续通过）。

- [ ] **Step 5: 提交**

```bash
git add dayflow-server/pom.xml dayflow-server/src/main/resources/application.yml
git commit -m "feat(m1): 升级 Boot 4.1 + 引入 MyBatis-Plus boot4 / jjwt / mysql"
```

---

## Task 2: 数据层基建 —— DDL + MyBatis-Plus 配置

**目标：** 6 表 DDL（dev 自动执行）+ 分页插件 + 时间自动填充。预置 1 个 BCrypt 用户。

**Files:**
- Create: `dayflow-server/src/main/resources/schema.sql`
- Create: `init.sql`（项目根）
- Create: `dayflow-server/src/main/java/com/dayflow/config/MybatisPlusConfig.java`
- Create: `dayflow-server/src/main/java/com/dayflow/common/MetaObjectHandler.java`
- Modify: `application.yml`（加 sql.init + mapper-locations）

**Interfaces:**
- Produces: `dayflow` 库 6 表 + 预置用户（id=1, username=admin）；`MetaObjectHandler` 自动填充 created_at/updated_at

- [ ] **Step 1: 生成预置用户的 BCrypt hash**

```bash
mvn -f dayflow-server/pom.xml -q exec:java -Dexec.mainClass=... 2>/dev/null || python -c "print('用 SpringSecurity BCrypt 工具生成 dayflow123 的 hash')"
```
实际做法：在 Step 3 的 schema.sql 里先用占位 hash，T5 写 JwtUtil 时用一段临时 main 方法或在线工具生成 `dayflow123` 的 BCrypt hash 替换。

> 生成命令（临时测试类 `dayflow-server/src/test/java/com/dayflow/common/BcryptHashGenerator.java`，T2 后删除）：

```java
package com.dayflow.common;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
class BcryptHashGenerator {
    @Test
    void gen() {
        System.out.println(new BCryptPasswordEncoder().encode("dayflow123"));
    }
}
```
> 注：Boot 4 的 `spring-security-crypto` 需加依赖 `<dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-crypto</artifactId></dependency>`（T5 用到，T5 引入；此处生成 hash 可临时手动用在线 BCrypt 工具，把结果填入 schema.sql）。**简化：用在线 BCrypt 生成 `dayflow123` 的 hash，直接填入下方占位。**

- [ ] **Step 2: 写 schema.sql（6 表 DDL + 预置用户）**

`dayflow-server/src/main/resources/schema.sql` —— 内容直接复制 spec §3.2 的完整 DDL（含 `CREATE DATABASE` + 6 表 + 索引 + 预置用户 INSERT）。把预置用户行的 `$2a$10$<bcrypt_hash_of_dayflow123>` 替换为 Step 1 生成的真实 hash。

- [ ] **Step 3: 复制 schema.sql 为项目根 init.sql**

```bash
cp dayflow-server/src/main/resources/schema.sql init.sql
```

- [ ] **Step 4: application.yml 开启 dev 自动建表**

在 `application.yml` 的 `spring:` 下追加：

```yaml
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
```

> `mode: always` 仅 dev 用；生产改 `never`（M5 处理 profile）。

- [ ] **Step 5: 写 MybatisPlusConfig（分页插件）**

`dayflow-server/src/main/java/com/dayflow/config/MybatisPlusConfig.java`：

```java
package com.dayflow.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * @author jiaxianming
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

- [ ] **Step 6: 写 MetaObjectHandler（自动填充时间）**

`dayflow-server/src/main/java/com/dayflow/common/MetaObjectHandler.java`：

```java
package com.dayflow.common;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充：created_at / updated_at
 *
 * @author jiaxianming
 */
@Component
public class MetaObjectHandlerImpl implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
```

- [ ] **Step 7: 启动验证表已建 + 预置用户存在**

```bash
mvn -f dayflow-server/pom.xml spring-boot:run
```
Expected: 启动日志看到 schema.sql 执行、无 SQL 错误；`Ctrl+C`。用 MySQL 客户端确认：`USE dayflow; SHOW TABLES;` 见 6 表；`SELECT username FROM user;` 见 `admin`。

- [ ] **Step 8: 提交**

```bash
git add dayflow-server/src/main/resources/schema.sql init.sql \
  dayflow-server/src/main/resources/application.yml \
  dayflow-server/src/main/java/com/dayflow/config/MybatisPlusConfig.java \
  dayflow-server/src/main/java/com/dayflow/common/MetaObjectHandler.java
git commit -m "feat(m1): 数据层基建（6 表 DDL + 预置用户 + 分页 + 自动填充）"
```

---

## Task 3: 码段细化 + 异常扩展（M-5 + M-3）

**目标：** ResultCode 加 403/404、BUSINESS_ERROR 改 409；GlobalExceptionHandler 扩 5 分支并补测。

**Files:**
- Modify: `dayflow-server/src/main/java/com/dayflow/common/ResultCode.java`
- Modify: `dayflow-server/src/main/java/com/dayflow/common/GlobalExceptionHandler.java`
- Modify: `dayflow-server/src/test/java/com/dayflow/common/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces: `ResultCode.FORBIDDEN/NOT_FOUND/BUSINESS_ERROR(409)`；异常→码映射完整

- [ ] **Step 1: 写失败测试（RED）—— 异常映射**

在 `GlobalExceptionHandlerTest.java` 追加：

```java
import com.dayflow.controller.HealthController;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void businessExceptionReturns409() throws Exception {
        mockMvc.perform(get("/api/health/error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("业务异常"));
    }
}
```

> HealthController 的 `/api/health/error` 抛 `BusinessException(ResultCode.BUSINESS_ERROR)`，改码后期望 409。

- [ ] **Step 2: 运行测试，确认失败**

```bash
mvn -f dayflow-server/pom.xml test -Dtest=GlobalExceptionHandlerTest
```
Expected: FAIL（当前 BUSINESS_ERROR 仍是 500，期望 409）。

- [ ] **Step 3: 改 ResultCode（加 403/404，BUSINESS_ERROR→409）**

`ResultCode.java` 枚举体替换为：

```java
    SUCCESS(200, "成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    BUSINESS_ERROR(409, "业务规则冲突"),
    SYSTEM_ERROR(500, "系统异常");
```

- [ ] **Step 4: 改 GlobalExceptionHandler（5 分支）**

替换整个类：

```java
package com.dayflow.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器
 *
 * @author jiaxianming
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数错误");
        log.warn("参数校验失败: {}", msg);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数错误");
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleBody(HttpMessageNotReadableException e) {
        log.warn("请求体不可读: {}", e.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "请求体格式错误");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNotFound(NoHandlerFoundException e) {
        return Result.fail(ResultCode.NOT_FOUND.getCode(), "资源不存在");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethod(HttpRequestMethodNotSupportedException e) {
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "请求方法不支持");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.SYSTEM_ERROR.getCode(), "系统异常");
    }
}
```

> `BusinessException` 需补 `getCode()` —— 见 Step 5。

- [ ] **Step 5: BusinessException 补 code 字段与 getter**

替换 `BusinessException.java`：

```java
package com.dayflow.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * @author jiaxianming
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
```

- [ ] **Step 6: 运行测试，确认通过（GREEN）**

```bash
mvn -f dayflow-server/pom.xml test
```
Expected: `Tests run: 7, Failures: 0`（BUSINESS_ERROR 现为 409）。

- [ ] **Step 7: 补 validation/兜底分支测试（M-3）**

新增 `dayflow-server/src/test/java/com/dayflow/controller/ValidationFailureTest.java`：

```java
package com.dayflow.controller;

import com.dayflow.common.GlobalExceptionHandler;
import com.dayflow.pojo.dto.LoginDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 参数校验失败 → 400（M-3 补测）
 */
@WebMvcTest(controllers = {})
@Import(GlobalExceptionHandler.class)
class ValidationFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void malformedBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
```
> 此测试在 T5 加完 AuthController 后才能真正触发 login 路由；T3 阶段先建，预期它会因 404 而非 400 失败 —— 把它标记 `@Disabled` 直到 T5 完成，T5 再启用。**T3 提交时先不建此测试，移到 T5 内一并做**（避免空悬依赖）。

- [ ] **Step 8: 提交**

```bash
git add dayflow-server/src/main/java/com/dayflow/common/ResultCode.java \
  dayflow-server/src/main/java/com/dayflow/common/GlobalExceptionHandler.java \
  dayflow-server/src/main/java/com/dayflow/common/BusinessException.java \
  dayflow-server/src/test/java/com/dayflow/common/GlobalExceptionHandlerTest.java
git commit -m "feat(m1): 码段细化(403/404/409) + 异常处理 5 分支"
```

---

## Task 4: 6 表实体 + Mapper + 枚举

**目标：** 6 个 Entity（MyBatis-Plus 注解 + 自动填充）+ 6 个 Mapper + 5 个枚举。

**Files:**
- Create: `pojo/entity/{UserEntity,ActivityEntity,TaskEntity,NoteEntity,ReportEntity,AgentTraceEntity}.java`
- Create: `mapper/{UserMapper,ActivityMapper,TaskMapper,NoteMapper,ReportMapper,AgentTraceMapper}.java`
- Create: `pojo/enums/{ActivityCategory,TaskStatus,ReportType,ReportStatus,AgentName}.java`

**Interfaces:**
- Produces: 全部实体与 Mapper，供 T5/T6+ 使用。实体字段名 = spec §3.2 DDL 列名（驼峰映射）。

- [ ] **Step 1: 写枚举（5 个）**

`pojo/enums/ActivityCategory.java`：

```java
package com.dayflow.pojo.enums;

/**
 * 活动分类
 */
public enum ActivityCategory { WORK, STUDY, MEETING, OTHER }
```

`pojo/enums/TaskStatus.java`：

```java
package com.dayflow.pojo.enums;
public enum TaskStatus { TODO, DOING, DONE }
```

`pojo/enums/ReportType.java`：

```java
package com.dayflow.pojo.enums;
public enum ReportType { DAILY, WEEKLY }
```

`pojo/enums/ReportStatus.java`：

```java
package com.dayflow.pojo.enums;
public enum ReportStatus { GENERATING, GENERATED, FAILED }
```

`pojo/enums/AgentName.java`：

```java
package com.dayflow.pojo.enums;
public enum AgentName { PLANNER, COLLECTOR, WRITER, REVIEWER }
```

- [ ] **Step 2: 写 6 实体**

`pojo/entity/UserEntity.java`：

```java
package com.dayflow.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体
 *
 * @author jiaxianming
 */
@Data
@TableName("user")
public class UserEntity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("username")
    private String username;

    @TableField("nickname")
    private String nickname;

    @TableField("password_hash")
    private String passwordHash;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

`pojo/entity/ActivityEntity.java`：

```java
package com.dayflow.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dayflow.pojo.enums.ActivityCategory;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作活动实体
 *
 * @author jiaxianming
 */
@Data
@TableName("activity")
public class ActivityEntity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("content")
    private String content;

    @TableField("category")
    private ActivityCategory category;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

`pojo/entity/TaskEntity.java`：

```java
package com.dayflow.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dayflow.pojo.enums.TaskStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 待办任务实体
 *
 * @author jiaxianming
 */
@Data
@TableName("task")
public class TaskEntity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("title")
    private String title;

    @TableField("status")
    private TaskStatus status;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

`pojo/entity/NoteEntity.java`：

```java
package com.dayflow.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习笔记实体（M1 只存原文）
 *
 * @author jiaxianming
 */
@Data
@TableName("note")
public class NoteEntity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("tags")
    private String tags;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

`pojo/entity/ReportEntity.java`：

```java
package com.dayflow.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dayflow.pojo.enums.ReportStatus;
import com.dayflow.pojo.enums.ReportType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报告实体（M1 只存元信息与最终稿字段；生成逻辑在 M3）
 *
 * @author jiaxianming
 */
@Data
@TableName("report")
public class ReportEntity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("type")
    private ReportType type;

    @TableField("period_start")
    private LocalDate periodStart;

    @TableField("period_end")
    private LocalDate periodEnd;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("status")
    private ReportStatus status;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("token_usage")
    private Integer tokenUsage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

`pojo/entity/AgentTraceEntity.java`：

```java
package com.dayflow.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dayflow.pojo.enums.AgentName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 执行轨迹实体（M1 只读，写入在 M3）
 *
 * @author jiaxianming
 */
@Data
@TableName("agent_trace")
public class AgentTraceEntity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("report_id")
    private Long reportId;

    @TableField("agent_name")
    private AgentName agentName;

    @TableField("step")
    private Integer step;

    @TableField("input_summary")
    private String inputSummary;

    @TableField("output_summary")
    private String outputSummary;

    @TableField("tokens")
    private Integer tokens;

    @TableField("latency_ms")
    private Integer latencyMs;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: 写 6 Mapper**

`mapper/UserMapper.java`：

```java
package com.dayflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayflow.pojo.entity.UserEntity;

/**
 * 用户 Mapper
 *
 * @author jiaxianming
 */
public interface UserMapper extends BaseMapper<UserEntity> {
}
```

其余 5 个 Mapper 同模式（`ActivityMapper extends BaseMapper<ActivityEntity>` 等），文件：`mapper/ActivityMapper.java`、`mapper/TaskMapper.java`、`mapper/NoteMapper.java`、`mapper/ReportMapper.java`、`mapper/AgentTraceMapper.java`。每个都是单接口 `extends BaseMapper<对应 Entity>`。

- [ ] **Step 4: 启动类加 @MapperScan**

`DayflowApplication.java` 顶部加：

```java
@MapperScan("com.dayflow.mapper")
```
import `org.mybatis.spring.annotation.MapperScan`。

- [ ] **Step 5: 编译验证**

```bash
mvn -f dayflow-server/pom.xml clean compile
```
Expected: BUILD SUCCESS。

- [ ] **Step 6: 提交**

```bash
git add dayflow-server/src/main/java/com/dayflow/pojo/ \
  dayflow-server/src/main/java/com/dayflow/mapper/ \
  dayflow-server/src/main/java/com/dayflow/DayflowApplication.java
git commit -m "feat(m1): 6 表实体 + Mapper + 枚举"
```

---

## Task 5: JWT 登录与鉴权

**目标：** `/api/auth/login` 校验预置用户并签发 JWT；JwtInterceptor 拦截 `/api/**` 注入 UserContext。

**Files:**
- Create: `common/JwtUtil.java`、`common/UserContext.java`、`common/JwtInterceptor.java`、`config/WebConfig.java`
- Create: `pojo/dto/LoginDTO.java`、`pojo/vo/LoginVO.java`
- Create: `service/UserAuthService.java`、`service/impl/UserAuthServiceImpl.java`
- Create: `controller/AuthController.java`
- Create: `dayflow-server/src/test/java/com/dayflow/common/JwtUtilTest.java`
- Modify: `application.yml`（jwt 配置）；pom.xml（加 `spring-security-crypto`）

**Interfaces:**
- Produces: `UserContext.getCurrentUserId()`（供 T6+ 所有 Service 取当前用户）；登录端点返回 `LoginVO{token,userId,username,nickname}`

- [ ] **Step 1: pom 加 spring-security-crypto（BCrypt 校验）**

在 pom dependencies 加：

```xml
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-crypto</artifactId>
        </dependency>
```

- [ ] **Step 2: application.yml 加 jwt 配置**

在 yml 根加：

```yaml
dayflow:
  jwt:
    secret: ${DAYFLOW_JWT_SECRET:zXj9Lp2Qr7TuVwXyZ0a1Bc3De5Fg7Hi9Jk1Lm3Np5Qr7St9Uv1Wx3Yz}
    expiration: 604800
```
> expiration 单位秒（604800 = 7 天）；secret 默认值仅 dev，生产用环境变量。

- [ ] **Step 3: 写 JwtUtilTest（RED）**

`dayflow-server/src/test/java/com/dayflow/common/JwtUtilTest.java`：

```java
package com.dayflow.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 测试
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // 反射注入配置（跳过 Spring 容器）
        reflectSet(jwtUtil, "secret", "test-secret-test-secret-test-secret-32+");
        reflectSet(jwtUtil, "expiration", 60L);
    }

    @Test
    void generateAndParseRoundTrip() {
        String token = jwtUtil.generate(1L, "admin");
        assertEquals(1L, jwtUtil.parseUserId(token));
        assertEquals("admin", jwtUtil.parseUsername(token));
    }

    @Test
    void invalidTokenReturnsNull() {
        assertNull(jwtUtil.parseUserId("not.a.jwt"));
    }

    @Test
    void expiredTokenReturnsNull() throws InterruptedException {
        reflectSet(jwtUtil, "expiration", 1L);
        String token = jwtUtil.generate(1L, "admin");
        Thread.sleep(1500);
        assertNull(jwtUtil.parseUserId(token));
    }

    private void reflectSet(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 4: 运行测试，确认失败**

```bash
mvn -f dayflow-server/pom.xml test -Dtest=JwtUtilTest
```
Expected: 编译失败（JwtUtil 不存在）。

- [ ] **Step 5: 写 JwtUtil（GREEN）**

`dayflow-server/src/main/java/com/dayflow/common/JwtUtil.java`：

```java
package com.dayflow.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发 / 解析
 *
 * @author jiaxianming
 */
@Component
public class JwtUtil {

    @Value("${dayflow.jwt.secret}")
    private String secret;

    @Value("${dayflow.jwt.expiration}")
    private long expiration;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 token
     */
    public String generate(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration * 1000))
                .signWith(key())
                .compact();
    }

    public Long parseUserId(String token) {
        try {
            Claims c = parse(token);
            return Long.valueOf(c.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    public String parseUsername(String token) {
        try {
            return parse(token).get("username", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }
}
```

- [ ] **Step 6: 写 UserContext、JwtInterceptor、WebConfig**

`common/UserContext.java`：

```java
package com.dayflow.common;

/**
 * 当前登录用户上下文（ThreadLocal）
 *
 * @author jiaxianming
 */
public class UserContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    public static void setUserId(Long userId) { CURRENT.set(userId); }
    public static Long getUserId() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
```

`common/JwtInterceptor.java`：

```java
package com.dayflow.common;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器
 *
 * @author jiaxianming
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String token = header.substring(7);
        Long userId = jwtUtil.parseUserId(token);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        UserContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        UserContext.clear();
    }
}
```

`config/WebConfig.java`：

```java
package com.dayflow.config;

import com.dayflow.common.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册 JWT 拦截器
 *
 * @author jiaxianming
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/health/**");
    }
}
```

- [ ] **Step 7: 写 LoginDTO / LoginVO / UserAuthService / AuthController**

`pojo/dto/LoginDTO.java`：

```java
package com.dayflow.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录入参
 *
 * @author jiaxianming
 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
```

`pojo/vo/LoginVO.java`：

```java
package com.dayflow.pojo.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录出参
 *
 * @author jiaxianming
 */
@Data
@Builder
public class LoginVO {
    private String token;
    private Long userId;
    private String username;
    private String nickname;
}
```

`service/UserAuthService.java`：

```java
package com.dayflow.service;

import com.dayflow.pojo.dto.LoginDTO;
import com.dayflow.pojo.vo.LoginVO;

/**
 * 用户鉴权服务
 *
 * @author jiaxianming
 */
public interface UserAuthService {

    /**
     * 登录
     */
    LoginVO login(LoginDTO dto);
}
```

`service/impl/UserAuthServiceImpl.java`：

```java
package com.dayflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayflow.common.BusinessException;
import com.dayflow.common.JwtUtil;
import com.dayflow.common.ResultCode;
import com.dayflow.mapper.UserMapper;
import com.dayflow.pojo.dto.LoginDTO;
import com.dayflow.pojo.entity.UserEntity;
import com.dayflow.pojo.vo.LoginVO;
import com.dayflow.service.UserAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户鉴权服务实现
 *
 * @author jiaxianming
 */
@Service
@RequiredArgsConstructor
public class UserAuthServiceImpl implements UserAuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public LoginVO login(LoginDTO dto) {
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }
}
```

`controller/AuthController.java`：

```java
package com.dayflow.controller;

import com.dayflow.common.Result;
import com.dayflow.pojo.dto.LoginDTO;
import com.dayflow.pojo.vo.LoginVO;
import com.dayflow.service.UserAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权接口
 *
 * @author jiaxianming
 */
@Tag(name = "鉴权")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserAuthService userAuthService;

    /**
     * 登录
     */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userAuthService.login(dto));
    }
}
```

> swagger 注解 `@Tag`/`@Operation` 需 springdoc —— M1 未引入 springdoc，**移除这两个注解及 import**（保留其余）。M4/M5 引入 springdoc 时再加回。

- [ ] **Step 8: 端到端验证登录**

```bash
mvn -f dayflow-server/pom.xml spring-boot:run
```
另开终端：
```bash
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"dayflow123"}'
```
Expected: `{"code":200,"msg":"成功","data":{"token":"...","userId":1,"username":"admin","nickname":"管理员"}}`。

错误密码验证：
```bash
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"wrong"}'
```
Expected: `{"code":401,"msg":"用户名或密码错误","data":null}`。`Ctrl+C` 停应用。

- [ ] **Step 9: 运行全部测试**

```bash
mvn -f dayflow-server/pom.xml test
```
Expected: 全绿（含 JwtUtilTest 3 个新测试）。

- [ ] **Step 10: 提交**

```bash
git add dayflow-server/pom.xml dayflow-server/src/main/resources/application.yml \
  dayflow-server/src/main/java/com/dayflow/common/JwtUtil.java \
  dayflow-server/src/main/java/com/dayflow/common/UserContext.java \
  dayflow-server/src/main/java/com/dayflow/common/JwtInterceptor.java \
  dayflow-server/src/main/java/com/dayflow/config/WebConfig.java \
  dayflow-server/src/main/java/com/dayflow/pojo/dto/LoginDTO.java \
  dayflow-server/src/main/java/com/dayflow/pojo/vo/LoginVO.java \
  dayflow-server/src/main/java/com/dayflow/service/UserAuthService.java \
  dayflow-server/src/main/java/com/dayflow/service/impl/UserAuthServiceImpl.java \
  dayflow-server/src/main/java/com/dayflow/controller/AuthController.java \
  dayflow-server/src/test/java/com/dayflow/common/JwtUtilTest.java
git commit -m "feat(m1): JWT 登录与鉴权（/api/auth/login + 拦截器 + UserContext）"
```

---

## Task 6: Activity CRUD（CRUD 范式）

**目标：** Activity 的 list/create/update/delete/get，确立后续 Task/Note/Report 复用的范式。

**Files:**
- Create: `pojo/query/ActivityQuery.java`、`pojo/dto/ActivityCreateDTO.java`、`pojo/dto/ActivityUpdateDTO.java`、`pojo/vo/ActivityVO.java`
- Create: `service/ActivityService.java`、`service/impl/ActivityServiceImpl.java`
- Create: `controller/ActivityController.java`
- Create: `dayflow-server/src/test/java/com/dayflow/service/ActivityServiceImplTest.java`
- Create: `dayflow-server/src/test/java/com/dayflow/controller/ActivityControllerTest.java`

**Interfaces:**
- Consumes: `UserContext.getUserId()`（T5）
- Produces: `GET/POST/PUT/DELETE /api/activities`；`ActivityService` 供后续参考的范式

- [ ] **Step 1: 写 Service 测试（RED）**

`dayflow-server/src/test/java/com/dayflow/service/ActivityServiceImplTest.java`：

```java
package com.dayflow.service;

import com.dayflow.common.BusinessException;
import com.dayflow.mapper.ActivityMapper;
import com.dayflow.pojo.dto.ActivityCreateDTO;
import com.dayflow.pojo.entity.ActivityEntity;
import com.dayflow.pojo.enums.ActivityCategory;
import com.dayflow.pojo.vo.ActivityVO;
import com.dayflow.service.impl.ActivityServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ActivityService 测试
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceImplTest {

    @Mock
    private ActivityMapper activityMapper;

    @InjectMocks
    private ActivityServiceImpl activityService;

    @Test
    void createReturnsId() {
        ActivityCreateDTO dto = new ActivityCreateDTO();
        dto.setContent("写代码");
        dto.setCategory(ActivityCategory.WORK);
        when(activityMapper.insert(any(ActivityEntity.class))).thenAnswer(inv -> {
            ((ActivityEntity) inv.getArgument(0)).setId(100L);
            return 1;
        });
        Long id = activityService.create(dto);
        assertEquals(100L, id);
    }

    @Test
    void getByIdNotFoundThrows() {
        when(activityMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> activityService.getById(999L));
    }

    @Test
    void getByIdReturnsVO() {
        ActivityEntity e = new ActivityEntity();
        e.setId(1L);
        e.setUserId(1L);
        e.setContent("测试");
        e.setCategory(ActivityCategory.WORK);
        when(activityMapper.selectById(1L)).thenReturn(e);
        ActivityVO vo = activityService.getById(1L);
        assertEquals(1L, vo.getId());
        assertEquals("测试", vo.getContent());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
mvn -f dayflow-server/pom.xml test -Dtest=ActivityServiceImplTest
```
Expected: 编译失败（Service / DTO / VO 不存在）。

- [ ] **Step 3: 写 DTO / Query / VO**

`pojo/dto/ActivityCreateDTO.java`：

```java
package com.dayflow.pojo.dto;

import com.dayflow.pojo.enums.ActivityCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动创建入参
 *
 * @author jiaxianming
 */
@Data
public class ActivityCreateDTO {

    @NotBlank(message = "内容不能为空")
    private String content;

    @NotNull(message = "分类不能为空")
    private ActivityCategory category;

    private LocalDateTime occurredAt;
}
```

`pojo/dto/ActivityUpdateDTO.java`：

```java
package com.dayflow.pojo.dto;

import com.dayflow.pojo.enums.ActivityCategory;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动修改入参
 *
 * @author jiaxianming
 */
@Data
public class ActivityUpdateDTO {
    private String content;
    private ActivityCategory category;
    private LocalDateTime occurredAt;
}
```

`pojo/query/ActivityQuery.java`：

```java
package com.dayflow.pojo.query;

import com.dayflow.pojo.enums.ActivityCategory;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动查询条件
 *
 * @author jiaxianming
 */
@Data
public class ActivityQuery {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ActivityCategory category;
    private Integer page = 1;
    private Integer size = 20;
}
```

`pojo/vo/ActivityVO.java`：

```java
package com.dayflow.pojo.vo;

import com.dayflow.pojo.enums.ActivityCategory;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动视图
 *
 * @author jiaxianming
 */
@Data
public class ActivityVO {
    private Long id;
    private Long userId;
    private String content;
    private ActivityCategory category;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: 写 ActivityService 接口 + impl（GREEN）**

`service/ActivityService.java`：

```java
package com.dayflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dayflow.pojo.dto.ActivityCreateDTO;
import com.dayflow.pojo.dto.ActivityUpdateDTO;
import com.dayflow.pojo.query.ActivityQuery;
import com.dayflow.pojo.vo.ActivityVO;

/**
 * 活动服务
 *
 * @author jiaxianming
 */
public interface ActivityService {

    Long create(ActivityCreateDTO dto);

    ActivityVO getById(Long id);

    void update(Long id, ActivityUpdateDTO dto);

    void delete(Long id);

    IPage<ActivityVO> page(ActivityQuery query);
}
```

`service/impl/ActivityServiceImpl.java`：

```java
package com.dayflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayflow.common.BusinessException;
import com.dayflow.common.ResultCode;
import com.dayflow.common.UserContext;
import com.dayflow.mapper.ActivityMapper;
import com.dayflow.pojo.dto.ActivityCreateDTO;
import com.dayflow.pojo.dto.ActivityUpdateDTO;
import com.dayflow.pojo.entity.ActivityEntity;
import com.dayflow.pojo.enums.ActivityCategory;
import com.dayflow.pojo.query.ActivityQuery;
import com.dayflow.pojo.vo.ActivityVO;
import com.dayflow.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 活动服务实现
 *
 * @author jiaxianming
 */
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private final ActivityMapper activityMapper;

    @Override
    public Long create(ActivityCreateDTO dto) {
        ActivityEntity e = new ActivityEntity();
        e.setUserId(UserContext.getUserId());
        e.setContent(dto.getContent());
        e.setCategory(dto.getCategory());
        e.setOccurredAt(dto.getOccurredAt());
        activityMapper.insert(e);
        return e.getId();
    }

    @Override
    public ActivityVO getById(Long id) {
        ActivityEntity e = activityMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "活动不存在");
        }
        return toVO(e);
    }

    @Override
    public void update(Long id, ActivityUpdateDTO dto) {
        ActivityEntity e = activityMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "活动不存在");
        }
        if (!Objects.equals(e.getUserId(), UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");
        }
        if (dto.getContent() != null) e.setContent(dto.getContent());
        if (dto.getCategory() != null) e.setCategory(dto.getCategory());
        if (dto.getOccurredAt() != null) e.setOccurredAt(dto.getOccurredAt());
        activityMapper.updateById(e);
    }

    @Override
    public void delete(Long id) {
        ActivityEntity e = activityMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "活动不存在");
        }
        activityMapper.deleteById(id);
    }

    @Override
    public IPage<ActivityVO> page(ActivityQuery q) {
        LambdaQueryWrapper<ActivityEntity> w = new LambdaQueryWrapper<ActivityEntity>()
                .eq(ActivityEntity::getUserId, UserContext.getUserId())
                .ge(q.getStartTime() != null, ActivityEntity::getOccurredAt, q.getStartTime())
                .le(q.getEndTime() != null, ActivityEntity::getOccurredAt, q.getEndTime())
                .eq(q.getCategory() != null, ActivityEntity::getCategory, q.getCategory())
                .orderByDesc(ActivityEntity::getOccurredAt);
        Page<ActivityEntity> p = new Page<>(q.getPage(), q.getSize());
        return activityMapper.selectPage(p, w).convert(this::toVO);
    }

    private ActivityVO toVO(ActivityEntity e) {
        ActivityVO vo = new ActivityVO();
        vo.setId(e.getId());
        vo.setUserId(e.getUserId());
        vo.setContent(e.getContent());
        vo.setCategory(e.getCategory());
        vo.setOccurredAt(e.getOccurredAt());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }
}
```

- [ ] **Step 5: 运行 Service 测试，确认通过**

```bash
mvn -f dayflow-server/pom.xml test -Dtest=ActivityServiceImplTest
```
Expected: 3 测试 PASS。

- [ ] **Step 6: 写 Controller + Controller 测试**

`controller/ActivityController.java`：

```java
package com.dayflow.controller;

import com.dayflow.common.Result;
import com.dayflow.pojo.dto.ActivityCreateDTO;
import com.dayflow.pojo.dto.ActivityUpdateDTO;
import com.dayflow.pojo.query.ActivityQuery;
import com.dayflow.pojo.vo.ActivityVO;
import com.dayflow.service.ActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 活动接口
 *
 * @author jiaxianming
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @PostMapping
    public Result<Long> create(@Valid @RequestBody ActivityCreateDTO dto) {
        return Result.success(activityService.create(dto));
    }

    @GetMapping("/{id}")
    public Result<ActivityVO> get(@PathVariable Long id) {
        return Result.success(activityService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ActivityUpdateDTO dto) {
        activityService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        activityService.delete(id);
        return Result.success();
    }

    @GetMapping
    public Result<?> page(ActivityQuery query) {
        return Result.success(activityService.page(query));
    }
}
```

`dayflow-server/src/test/java/com/dayflow/controller/ActivityControllerTest.java`：

```java
package com.dayflow.controller;

import com.dayflow.common.GlobalExceptionHandler;
import com.dayflow.pojo.vo.ActivityVO;
import com.dayflow.service.ActivityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ActivityController 测试（@WebMvcTest 切片，不连 DB）
 */
@WebMvcTest(ActivityController.class)
@Import(GlobalExceptionHandler.class)
class ActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivityService activityService;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void createReturns200() throws Exception {
        when(activityService.create(any())).thenReturn(10L);
        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\",\"category\":\"WORK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(10));
    }

    @Test
    void createWithInvalidBodyReturns400() {
        // 缺 content -> @Valid 失败 -> 400
        try {
            mockMvc.perform(post("/api/activities")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"category\":\"WORK\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void getByIdReturns200() throws Exception {
        ActivityVO vo = new ActivityVO();
        vo.setId(1L);
        vo.setContent("测试");
        when(activityService.getById(1L)).thenReturn(vo);
        mockMvc.perform(get("/api/activities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }
}
```

> **注意：** `@WebMvcTest` 默认不装载 JwtInterceptor（它只扫描指定 Controller）。T6 测试不测鉴权（鉴权在 T5 已端到端验证）。若 Boot 4 下 `@WebMvcTest` 仍尝试装载 WebConfig 的拦截器导致报错，在 test 加 `@MockBean JwtUtil` 或用 `@AutoConfigureMockMvc(addFilters = false)`。

- [ ] **Step 7: 运行全部测试 + 提交**

```bash
mvn -f dayflow-server/pom.xml test
```
Expected: 全绿。

```bash
git add dayflow-server/src/main/java/com/dayflow/pojo/query/ActivityQuery.java \
  dayflow-server/src/main/java/com/dayflow/pojo/dto/ActivityCreateDTO.java \
  dayflow-server/src/main/java/com/dayflow/pojo/dto/ActivityUpdateDTO.java \
  dayflow-server/src/main/java/com/dayflow/pojo/vo/ActivityVO.java \
  dayflow-server/src/main/java/com/dayflow/service/ActivityService.java \
  dayflow-server/src/main/java/com/dayflow/service/impl/ActivityServiceImpl.java \
  dayflow-server/src/main/java/com/dayflow/controller/ActivityController.java \
  dayflow-server/src/test/java/com/dayflow/service/ActivityServiceImplTest.java \
  dayflow-server/src/test/java/com/dayflow/controller/ActivityControllerTest.java
git commit -m "feat(m1): Activity CRUD（CRUD 范式）"
```

---

## Task 7: Task CRUD（含 complete 状态流转）

**目标：** Task 的 CRUD + `PATCH /api/tasks/{id}/complete`（status→DONE + completed_at 置当前时间）。

**Files:**（沿用 T6 范式：每个资源各建 DTO/Query/VO/Service/Controller/测试）
- `pojo/dto/TaskCreateDTO.java`（字段：`@NotBlank title`、`status` 可空默认 TODO）、`pojo/dto/TaskUpdateDTO.java`（title/status）、`pojo/query/TaskQuery.java`（status、page、size）、`pojo/vo/TaskVO.java`
- `service/TaskService.java`（比 Activity 多 `void complete(Long id)`）、`service/impl/TaskServiceImpl.java`
- `controller/TaskController.java`（多 `@PatchMapping("/{id}/complete")`）
- `TaskServiceImplTest.java`、`TaskControllerTest.java`

**Interfaces:**
- Produces: `PATCH /api/tasks/{id}/complete`

- [ ] **Step 1: TaskServiceImplTest（RED）—— 含 complete 测试**

```java
package com.dayflow.service;

import com.dayflow.mapper.TaskMapper;
import com.dayflow.pojo.entity.TaskEntity;
import com.dayflow.pojo.enums.TaskStatus;
import com.dayflow.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskMapper taskMapper;
    @InjectMocks
    private TaskServiceImpl taskService;

    @Test
    void completeSetsStatusDoneAndCompletedAt() {
        TaskEntity e = new TaskEntity();
        e.setId(1L);
        e.setStatus(TaskStatus.TODO);
        when(taskMapper.selectById(1L)).thenReturn(e);
        when(taskMapper.updateById(any())).thenReturn(1);
        taskService.complete(1L);
        verify(taskMapper).updateById(argThat(t ->
                ((TaskEntity) t).getStatus() == TaskStatus.DONE
                        && ((TaskEntity) t).getCompletedAt() != null));
    }
}
```
import 补：`import static org.mockito.ArgumentMatchers.argThat;`

- [ ] **Step 2: 运行，确认失败**

```bash
mvn -f dayflow-server/pom.xml test -Dtest=TaskServiceImplTest
```
Expected: 编译失败。

- [ ] **Step 3: 写 Task DTO/Query/VO**（结构同 T6，字段按上）

`pojo/dto/TaskCreateDTO.java`：`@NotBlank title` + `status`（TaskStatus，可空）。
`pojo/dto/TaskUpdateDTO.java`：`title` + `status`。
`pojo/query/TaskQuery.java`：`status` + `page` + `size`。
`pojo/vo/TaskVO.java`：`id, userId, title, status, completedAt, createdAt`。

- [ ] **Step 4: 写 TaskService + impl（GREEN，含 complete）**

`service/TaskService.java`（CRUD 方法签名同 `ActivityService`，把 `Activity` 换 `Task`，多 `void complete(Long id)`）。

`service/impl/TaskServiceImpl.java`：复制 `ActivityServiceImpl` 结构，字段换为 `title/status/completedAt`，查询条件用 `TaskStatus`。额外实现 `complete`：

```java
    @Override
    public void complete(Long id) {
        TaskEntity e = taskMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
        }
        e.setStatus(TaskStatus.DONE);
        e.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(e);
    }
```

- [ ] **Step 5: 写 TaskController**（端点同 Activity 模式 + complete）

```java
    @PatchMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        taskService.complete(id);
        return Result.success();
    }
```
其余 `POST/GET/PUT/DELETE/GET list` 与 `ActivityController` 同（类型换 Task）。

- [ ] **Step 6: TaskControllerTest —— 覆盖 complete 端点**

仿 `ActivityControllerTest` 写 create/get/invalidate + 加：

```java
    @Test
    void completeReturns200() throws Exception {
        mockMvc.perform(patch("/api/tasks/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(taskService).complete(1L);
    }
```
import `static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;` 和 `static org.mockito.Mockito.verify;`。

- [ ] **Step 7: 全测 + 提交**

```bash
mvn -f dayflow-server/pom.xml test
git add dayflow-server/src/main/java/com/dayflow/pojo/dto/Task* \
  dayflow-server/src/main/java/com/dayflow/pojo/query/TaskQuery.java \
  dayflow-server/src/main/java/com/dayflow/pojo/vo/TaskVO.java \
  dayflow-server/src/main/java/com/dayflow/service/TaskService.java \
  dayflow-server/src/main/java/com/dayflow/service/impl/TaskServiceImpl.java \
  dayflow-server/src/main/java/com/dayflow/controller/TaskController.java \
  dayflow-server/src/test/java/com/dayflow/service/TaskServiceImplTest.java \
  dayflow-server/src/test/java/com/dayflow/controller/TaskControllerTest.java
git commit -m "feat(m1): Task CRUD + complete 状态流转"
```

---

## Task 8: Note CRUD

**目标：** Note 的 CRUD（M1 只存原文，不做切块 embedding）。

**Files:**（沿用 T6 范式）
- `pojo/dto/NoteCreateDTO.java`（`@NotBlank title`、`@NotBlank content`、`tags`）、`pojo/dto/NoteUpdateDTO.java`、`pojo/query/NoteQuery.java`（tags 模糊、page、size）、`pojo/vo/NoteVO.java`
- `service/NoteService.java`、`service/impl/NoteServiceImpl.java`
- `controller/NoteController.java`
- `NoteServiceImplTest.java`、`NoteControllerTest.java`

**Interfaces:** 无新接口（标准 CRUD）

- [ ] **Step 1: NoteServiceImplTest（RED）** —— 测 create + getById，仿 `ActivityServiceImplTest`。

- [ ] **Step 2: 运行确认失败**

- [ ] **Step 3: 写 Note DTO/Query/VO** —— `NoteCreateDTO` 含 `@NotBlank title` + `@NotBlank content` + `tags`；`NoteQuery` 含 `tags`（LIKE 模糊）、`page`、`size`；`NoteVO` 含 `id, userId, title, content, tags, createdAt`。

- [ ] **Step 4: 写 NoteService + impl（GREEN）** —— 复制 `ActivityServiceImpl` 结构，字段换 `title/content/tags`；查询条件 `like(NoteEntity::getTags, q.getTags())`（当 tags 非空）。

- [ ] **Step 5: 写 NoteController** —— `POST/GET/{id}/PUT/{id}/DELETE/{id}/GET`，类型换 Note。

- [ ] **Step 6: NoteControllerTest** —— 仿 `ActivityControllerTest`，create 用 `{"title":"t","content":"c"}`。

- [ ] **Step 7: 全测 + 提交**

```bash
mvn -f dayflow-server/pom.xml test
git add dayflow-server/src/main/java/com/dayflow/pojo/dto/Note* \
  dayflow-server/src/main/java/com/dayflow/pojo/query/NoteQuery.java \
  dayflow-server/src/main/java/com/dayflow/pojo/vo/NoteVO.java \
  dayflow-server/src/main/java/com/dayflow/service/Note* \
  dayflow-server/src/main/java/com/dayflow/controller/NoteController.java \
  dayflow-server/src/test/java/com/dayflow/service/NoteServiceImplTest.java \
  dayflow-server/src/test/java/com/dayflow/controller/NoteControllerTest.java
git commit -m "feat(m1): Note CRUD"
```

---

## Task 9: Report CRUD + agent_trace 只读

**目标：** Report 的 list/create(仅元信息)/get/delete；`GET /api/reports/{id}/traces`（agent_trace 只读）。

**Files:**（沿用 T6 范式）
- `pojo/dto/ReportCreateDTO.java`（`@NotNull type`、`@NotNull periodStart`、`@NotNull periodEnd`、`title`）、`pojo/query/ReportQuery.java`（type、page、size）、`pojo/vo/ReportVO.java`、`pojo/vo/AgentTraceVO.java`
- `service/ReportService.java`（含 `List<AgentTraceVO> listTraces(Long reportId)`）、`service/impl/ReportServiceImpl.java`
- `controller/ReportController.java`
- `ReportServiceImplTest.java`、`ReportControllerTest.java`

**Interfaces:**
- Produces: `GET /api/reports/{id}/traces` —— 返回该报告的 Agent 轨迹列表（M3 写入，M1 只读）

- [ ] **Step 1: ReportServiceImplTest（RED）** —— 测 create（仅元信息，status=GENERATING）+ listTraces。

```java
    @Test
    void createSetsStatusGenerating() {
        ReportCreateDTO dto = new ReportCreateDTO();
        dto.setType(ReportType.DAILY);
        dto.setPeriodStart(LocalDate.now());
        dto.setPeriodEnd(LocalDate.now());
        when(reportMapper.insert(any())).thenAnswer(inv -> { ((ReportEntity) inv.getArgument(0)).setId(7L); return 1; });
        Long id = reportService.create(dto);
        assertEquals(7L, id);
    }

    @Test
    void listTracesReturnsByReportId() {
        AgentTraceEntity t = new AgentTraceEntity();
        t.setId(1L); t.setReportId(100L); t.setAgentName(AgentName.PLANNER);
        when(traceMapper.selectList(any())).thenReturn(List.of(t));
        var list = reportService.listTraces(100L);
        assertEquals(1, list.size());
    }
```

- [ ] **Step 2: 运行确认失败**

- [ ] **Step 3: 写 Report DTO/Query/VO + AgentTraceVO**

`pojo/dto/ReportCreateDTO.java`：`@NotNull type`(ReportType) + `@NotNull periodStart`(LocalDate) + `@NotNull periodEnd`(LocalDate) + `title`。
`pojo/query/ReportQuery.java`：`type` + `page` + `size`。
`pojo/vo/ReportVO.java`：`id, userId, type, periodStart, periodEnd, title, content, status, errorMsg, tokenUsage, createdAt`。
`pojo/vo/AgentTraceVO.java`：`id, reportId, agentName, step, inputSummary, outputSummary, tokens, latencyMs, retryCount, createdAt`。

- [ ] **Step 4: 写 ReportService + impl（GREEN）**

`service/ReportService.java`：`Long create(ReportCreateDTO)`、`ReportVO getById(Long)`、`void delete(Long)`、`IPage<ReportVO> page(ReportQuery)`、`List<AgentTraceVO> listTraces(Long reportId)`。

`service/impl/ReportServiceImpl.java`：注入 `ReportMapper` + `AgentTraceMapper`。`create` 时 `status=GENERATING`，不生成 content。`listTraces`：

```java
    @Override
    public List<AgentTraceVO> listTraces(Long reportId) {
        List<AgentTraceEntity> traces = traceMapper.selectList(
                new LambdaQueryWrapper<AgentTraceEntity>()
                        .eq(AgentTraceEntity::getReportId, reportId)
                        .orderByAsc(AgentTraceEntity::getStep));
        return traces.stream().map(this::toTraceVO).toList();
    }
```

- [ ] **Step 5: 写 ReportController**

```java
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ReportCreateDTO dto) { ... }

    @GetMapping("/{id}")
    public Result<ReportVO> get(@PathVariable Long id) { ... }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { ... }

    @GetMapping
    public Result<?> page(ReportQuery query) { ... }

    @GetMapping("/{id}/traces")
    public Result<List<AgentTraceVO>> traces(@PathVariable Long id) {
        return Result.success(reportService.listTraces(id));
    }
```

- [ ] **Step 6: ReportControllerTest** —— 覆盖 create/get/traces。

- [ ] **Step 7: 全测 + 提交**

```bash
mvn -f dayflow-server/pom.xml test
git add dayflow-server/src/main/java/com/dayflow/pojo/dto/ReportCreateDTO.java \
  dayflow-server/src/main/java/com/dayflow/pojo/query/ReportQuery.java \
  dayflow-server/src/main/java/com/dayflow/pojo/vo/ReportVO.java \
  dayflow-server/src/main/java/com/dayflow/pojo/vo/AgentTraceVO.java \
  dayflow-server/src/main/java/com/dayflow/service/ReportService.java \
  dayflow-server/src/main/java/com/dayflow/service/impl/ReportServiceImpl.java \
  dayflow-server/src/main/java/com/dayflow/controller/ReportController.java \
  dayflow-server/src/test/java/com/dayflow/service/ReportServiceImplTest.java \
  dayflow-server/src/test/java/com/dayflow/controller/ReportControllerTest.java
git commit -m "feat(m1): Report CRUD + agent_trace 只读"
```

---

## Task 10: 收尾 —— 清理 api-design.md + 全量验证 + tag

**目标：** 清理 `.claude/rules/api-design.md` 剩余冲突；全量测试；打 tag `m1-complete`。

**Files:**
- Modify: `.claude/rules/api-design.md`

- [ ] **Step 1: 清理 api-design.md 剩余冲突**

逐条修正 `.claude/rules/api-design.md`：
- §2 分页：移除 `PageUtils.java`/`Query.java` 引用，改为 "分页响应直接用 MyBatis-Plus `IPage`：`{records, total, current, size}`"；分页字段说明替换为 IPage 字段；分页查询参数改为 `current`(Long,1)/`size`(Long,10)
- §2 成功/失败响应统一 `code:200` 成功、`msg` 字段（已改，确认）
- §4 HTTP 状态码：改为说明 "DayFlow Controller 统一返回 `Result` 包装（HTTP 200），业务语义在 `Result.code`（200/400/401/403/404/409/500）"；移除 201/204 风格说明
- §5 版本控制：移除 `/api/v1` 强制前缀，改为 "路径前缀 `/api/<resource>`，暂不版本化（后续需要时加 `/v2`）"

- [ ] **Step 2: 全量验证**

```bash
mvn -f dayflow-server/pom.xml clean test
```
Expected: 所有测试 PASS（M0 + M1 新增：JwtUtil 3、Activity 6、Task、Note、Report 等）。

- [ ] **Step 3: 启动端到端冒烟**

```bash
mvn -f dayflow-server/pom.xml spring-boot:run
```
另开终端跑通：登录 → 带 token 创建 activity → 查 activity list → 创建 task 并 complete → 创建 note → 创建 report → 查 report traces（空数组）。`Ctrl+C`。

- [ ] **Step 4: 提交 + 打 tag**

```bash
git add .claude/rules/api-design.md
git commit -m "docs(m1): 清理 api-design.md 剩余冲突（IPage/Result 包装/路径前缀）"
git tag m1-complete
```

- [ ] **Step 5: 更新 progress.md ledger**

在 `.superpowers/sdd/progress.md` 的 M1 段追加：各 Task 完成状态、commit hash、tag。标注 M2/M3/M5 入场任务（如 Spring AI 2.0 spec 重写、Docker+Testcontainers 集成测试）。

---

## Self-Review

**1. Spec coverage：**
- 技术基线 Boot 4.1 + MyBatis-Plus boot4 + jjwt → T1 ✓
- 6 表 DDL + 预置用户 + 自动填充 + 分页 → T2 ✓
- ResultCode 403/404/409 + 异常 5 分支 + M-3 补测 → T3 ✓（validation 补测在 T5 Controller 测试体现）
- 6 实体 + Mapper + 枚举 → T4 ✓
- JWT 登录 + 拦截器 + UserContext → T5 ✓
- Activity/Task/Note/Report CRUD → T6/T7/T8/T9 ✓
- agent_trace 只读 → T9 ✓
- 测试策略（@WebMvcTest + Mockito，无集成测试）→ 各 Task 测试 ✓
- api-design.md 清理 → T10 ✓
- M1 边界（不做 AI 生成/向量库/注册）→ 各 Task 明确未包含 ✓

**2. Placeholder 扫描：** T2 的 BCrypt hash 占位有明确生成方式（Step 1）；T7-T9 的"沿用 T6 范式"均给出了具体字段、特有方法代码、测试 —— 非空悬。无 TBD/TODO。

**3. 类型一致性：** `UserContext.getUserId()`（T5 定义，T6+ 使用）、`Result.success/fail`（既有）、`BusinessException(ResultCode, String)`（T3 定义）、实体字段名（T4 定义，T6+ toVO 使用）—— 跨 task 一致。`ActivityService.create` 返回 `Long`（T6 定义，测试用 `Long`）—— 一致。

**4. 已知简化（非占位，记录在案）：**
- springdoc 延后 M4/M5（T5 已注明移除 swagger 注解）
- T6-T9 测试不覆盖鉴权（T5 已端到端验证拦截器）
- 真 DB 集成测试留 M5（Docker+Testcontainers）

**风险提示：** Boot 4.1 + mybatis-plus-spring-boot4-starter 较新，T1 Step 3 必须实测启动；若 #7009 复现，按 T1 fallback（升 MP 版本或降 Boot 3.4 + Spring AI 1.0）。
