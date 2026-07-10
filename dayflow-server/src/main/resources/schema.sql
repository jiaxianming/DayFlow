-- DayFlow 数据库初始化脚本（dev profile 通过 spring.sql.init.mode=always 每次启动执行）
-- 幂等设计：CREATE DATABASE/TABLE IF NOT EXISTS + 预置用户 ON DUPLICATE KEY UPDATE，
-- 重复执行不会报 "Table already exists" 或违反 uk_username 唯一约束。
-- 项目根 init.sql 内容与本文件保持一致，供手动初始化 / 开源展示使用。

CREATE DATABASE IF NOT EXISTS dayflow DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE dayflow;

-- 用户
CREATE TABLE IF NOT EXISTS `user` (
  `id`            BIGINT       NOT NULL COMMENT '雪花ID',
  `username`      VARCHAR(64)  NOT NULL COMMENT '登录名',
  `nickname`      VARCHAR(64)  NULL     COMMENT '昵称',
  `password_hash` VARCHAR(128) NOT NULL COMMENT 'BCrypt',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB COMMENT='用户';

-- 工作活动
CREATE TABLE IF NOT EXISTS `activity` (
  `id`          BIGINT      NOT NULL COMMENT '雪花ID',
  `user_id`     BIGINT      NOT NULL COMMENT '所属用户ID',
  `content`     TEXT        NOT NULL COMMENT '活动描述',
  `category`    VARCHAR(16) NOT NULL DEFAULT 'OTHER' COMMENT '活动分类：WORK-工作、STUDY-学习、MEETING-会议、OTHER-其他（默认）',
  `occurred_at` DATETIME    NULL     COMMENT '发生时间',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB COMMENT='工作活动';

-- 内置轻量待办
CREATE TABLE IF NOT EXISTS `task` (
  `id`           BIGINT       NOT NULL COMMENT '雪花ID',
  `user_id`      BIGINT       NOT NULL COMMENT '所属用户ID',
  `title`        VARCHAR(200) NOT NULL COMMENT '任务标题',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'TODO' COMMENT '任务状态：TODO-待办、DOING-进行中、DONE-已完成（默认TODO）',
  `completed_at` DATETIME     NULL     COMMENT '完成时间（供周报统计）',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB COMMENT='内置轻量待办';

-- 学习笔记（M1 只存原文，切块 embedding 留 M2）
CREATE TABLE IF NOT EXISTS `note` (
  `id`         BIGINT        NOT NULL COMMENT '雪花ID',
  `user_id`    BIGINT        NOT NULL COMMENT '所属用户ID',
  `title`      VARCHAR(200)  NOT NULL COMMENT '笔记标题',
  `content`    MEDIUMTEXT    NOT NULL COMMENT '原文',
  `tags`       VARCHAR(200)  NULL     COMMENT '逗号分隔',
  `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB COMMENT='学习笔记';

-- 报告（M1 只存元信息与最终稿字段，AI 生成留 M3）
CREATE TABLE IF NOT EXISTS `report` (
  `id`           BIGINT        NOT NULL COMMENT '雪花ID',
  `user_id`      BIGINT        NOT NULL COMMENT '所属用户ID',
  `type`         VARCHAR(8)    NOT NULL COMMENT '报告类型：DAILY-日报、WEEKLY-周报',
  `period_start` DATE          NOT NULL COMMENT '报告周期起始日',
  `period_end`   DATE          NOT NULL COMMENT '报告周期结束日',
  `title`        VARCHAR(200)  NULL     COMMENT '报告标题',
  `content`      MEDIUMTEXT    NULL     COMMENT '最终 Markdown',
  `status`       VARCHAR(16)   NOT NULL DEFAULT 'GENERATING' COMMENT '报告状态：GENERATING-生成中、GENERATED-已生成、FAILED-生成失败（默认GENERATING）',
  `error_msg`    VARCHAR(500)  NULL     COMMENT '失败时的错误信息',
  `token_usage`  INT           NULL     DEFAULT 0 COMMENT 'Token消耗量',
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_type_period` (`user_id`, `type`, `period_start`)
) ENGINE=InnoDB COMMENT='报告';

-- Agent 执行轨迹（M1 只建表 + 只读；写入留 M3）
CREATE TABLE IF NOT EXISTS `agent_trace` (
  `id`             BIGINT   NOT NULL COMMENT '雪花ID',
  `report_id`      BIGINT   NOT NULL COMMENT '关联报告ID',
  `agent_name`     VARCHAR(16) NOT NULL COMMENT 'Agent名称：PLANNER-规划者、COLLECTOR-收集者、WRITER-撰写者、REVIEWER-审核者',
  `step`           INT      NOT NULL COMMENT '执行步骤序号',
  `input_summary`  TEXT     NULL     COMMENT '输入摘要',
  `output_summary` TEXT     NULL     COMMENT '输出摘要',
  `tokens`         INT      NULL     DEFAULT 0 COMMENT 'Token消耗量',
  `latency_ms`     INT      NULL     DEFAULT 0 COMMENT '执行耗时（毫秒）',
  `retry_count`    INT      NOT NULL DEFAULT 0 COMMENT '重试次数',
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_report_id` (`report_id`)
) ENGINE=InnoDB COMMENT='Agent 执行轨迹';

-- 预置单用户（明文 dayflow123，BCrypt cost 10 hash）
-- 幂等：重复执行走 ON DUPLICATE KEY UPDATE，不违反 uk_username
INSERT INTO `user` (`id`, `username`, `nickname`, `password_hash`)
VALUES (1, 'admin', '管理员', '$2a$10$Bf3BB47YX3/N0aMg4ujK8uGd8mzzgSApNIHBjNwOq0lT6s5YO9qSW')
ON DUPLICATE KEY UPDATE
  `nickname`      = VALUES(`nickname`),
  `password_hash` = VALUES(`password_hash`);
