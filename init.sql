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
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB COMMENT='用户';

-- 工作活动
CREATE TABLE IF NOT EXISTS `activity` (
  `id`          BIGINT      NOT NULL,
  `user_id`     BIGINT      NOT NULL,
  `content`     TEXT        NOT NULL COMMENT '活动描述',
  `category`    VARCHAR(16) NOT NULL DEFAULT 'OTHER' COMMENT 'WORK/STUDY/MEETING/OTHER',
  `occurred_at` DATETIME    NULL     COMMENT '发生时间',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB COMMENT='工作活动';

-- 内置轻量待办
CREATE TABLE IF NOT EXISTS `task` (
  `id`           BIGINT      NOT NULL,
  `user_id`      BIGINT      NOT NULL,
  `title`        VARCHAR(200) NOT NULL,
  `status`       VARCHAR(16) NOT NULL DEFAULT 'TODO' COMMENT 'TODO/DOING/DONE',
  `completed_at` DATETIME    NULL     COMMENT '完成时间（供周报统计）',
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB COMMENT='内置轻量待办';

-- 学习笔记（M1 只存原文，切块 embedding 留 M2）
CREATE TABLE IF NOT EXISTS `note` (
  `id`         BIGINT        NOT NULL,
  `user_id`    BIGINT        NOT NULL,
  `title`      VARCHAR(200)  NOT NULL,
  `content`    MEDIUMTEXT    NOT NULL COMMENT '原文',
  `tags`       VARCHAR(200)  NULL     COMMENT '逗号分隔',
  `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB COMMENT='学习笔记';

-- 报告（M1 只存元信息与最终稿字段，AI 生成留 M3）
CREATE TABLE IF NOT EXISTS `report` (
  `id`           BIGINT        NOT NULL,
  `user_id`      BIGINT        NOT NULL,
  `type`         VARCHAR(8)    NOT NULL COMMENT 'DAILY/WEEKLY',
  `period_start` DATE          NOT NULL,
  `period_end`   DATE          NOT NULL,
  `title`        VARCHAR(200)  NULL,
  `content`      MEDIUMTEXT    NULL     COMMENT '最终 Markdown',
  `status`       VARCHAR(16)   NOT NULL DEFAULT 'GENERATING' COMMENT 'GENERATING/GENERATED/FAILED',
  `error_msg`    VARCHAR(500)  NULL,
  `token_usage`  INT           NULL     DEFAULT 0,
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_type_period` (`user_id`, `type`, `period_start`)
) ENGINE=InnoDB COMMENT='报告';

-- Agent 执行轨迹（M1 只建表 + 只读；写入留 M3）
CREATE TABLE IF NOT EXISTS `agent_trace` (
  `id`             BIGINT   NOT NULL,
  `report_id`      BIGINT   NOT NULL,
  `agent_name`     VARCHAR(16) NOT NULL COMMENT 'PLANNER/COLLECTOR/WRITER/REVIEWER',
  `step`           INT      NOT NULL,
  `input_summary`  TEXT     NULL,
  `output_summary` TEXT     NULL,
  `tokens`         INT      NULL DEFAULT 0,
  `latency_ms`     INT      NULL DEFAULT 0,
  `retry_count`    INT      NOT NULL DEFAULT 0,
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
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
