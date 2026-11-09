-- ============================================================
-- 猫狗宠物之家（pet-home）数据库脚本
-- 内容：31 张表结构 + 运行必需的基础数据
-- 环境：MySQL 8.0 / utf8mb4
-- 用法：mysql -uroot -p < pet_home_full_schema.sql
-- 说明：
--   1. 基础数据 = 账号、店铺、商品分类、商品、知识库语料、敏感词
--   2. 不含宠物档案、排班模板、预约、帖子、消息、通知等运行期数据，由系统运行产生
--   3. 脚本可重复执行（建表前 DROP TABLE IF EXISTS），执行前请确认目标库可清空
-- ============================================================

CREATE DATABASE IF NOT EXISTS `pet_home`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE `pet_home`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `ai_chat_messages`;
CREATE TABLE `ai_chat_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `role` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'user或assistant',
  `message_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'TEXT、HEALTH_REPORT、RECOMMENDATION',
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息正文',
  `citations_json` json DEFAULT NULL COMMENT '引用来源',
  `structured_data_json` json DEFAULT NULL COMMENT '推荐卡片等结构化数据',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMPLETED' COMMENT '消息状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_chat_messages_session` (`session_id`,`created_at`),
  KEY `idx_ai_chat_messages_session_id` (`session_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI顾问消息';
DROP TABLE IF EXISTS `ai_chat_sessions`;
CREATE TABLE `ai_chat_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int NOT NULL COMMENT '用户ID',
  `pet_id` int NOT NULL COMMENT '宠物ID',
  `mode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'QA、REPORT、RECOMMENDATION',
  `last_message_at` datetime DEFAULT NULL COMMENT '最后消息时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_chat_sessions_owner_mode` (`user_id`,`pet_id`,`mode`),
  KEY `idx_ai_chat_sessions_updated` (`user_id`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI顾问会话';
DROP TABLE IF EXISTS `ai_usage_logs`;
CREATE TABLE `ai_usage_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int DEFAULT NULL,
  `scene` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `model_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prompt_tokens` int NOT NULL DEFAULT '0',
  `completion_tokens` int NOT NULL DEFAULT '0',
  `total_tokens` int NOT NULL DEFAULT '0',
  `latency_ms` bigint NOT NULL DEFAULT '0',
  `success` tinyint(1) NOT NULL DEFAULT '1',
  `error_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_usage_user_time` (`user_id`,`created_at`),
  KEY `idx_ai_usage_scene_time` (`scene`,`created_at`),
  CONSTRAINT `fk_ai_usage_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `appointment_schedules`;
CREATE TABLE `appointment_schedules` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` int NOT NULL COMMENT '店铺ID',
  `service_id` int NOT NULL COMMENT '服务ID',
  `start_time` time NOT NULL COMMENT '每日开始时间',
  `end_time` time NOT NULL COMMENT '每日结束时间',
  `break_start` time DEFAULT NULL COMMENT '午休开始时间，空表示无休息区间',
  `break_end` time DEFAULT NULL COMMENT '午休结束时间，空表示无休息区间',
  `interval_minutes` int NOT NULL DEFAULT '0' COMMENT '服务间隔分钟数（0~120），两段服务之间的缓冲，影响时段切分步长',
  `capacity_per_slot` smallint NOT NULL DEFAULT '1' COMMENT '单个时段可预约宠物数',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_service` (`service_id`),
  KEY `fk_schedule_shop` (`shop_id`),
  CONSTRAINT `fk_schedule_service` FOREIGN KEY (`service_id`) REFERENCES `products` (`id`),
  CONSTRAINT `fk_schedule_shop` FOREIGN KEY (`shop_id`) REFERENCES `shops` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约排班模板（每日时间段）';
DROP TABLE IF EXISTS `appointment_slots`;
CREATE TABLE `appointment_slots` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` int NOT NULL COMMENT '店铺ID',
  `service_id` int NOT NULL COMMENT '服务ID',
  `slot_date` date NOT NULL COMMENT '预约日期',
  `day_status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT '天级状态：OPEN营业/CLOSED全天休业',
  `start_time` time NOT NULL COMMENT '开始时间',
  `end_time` time NOT NULL COMMENT '结束时间',
  `capacity` int NOT NULL DEFAULT '1' COMMENT '可预约宠物数',
  `booked_count` int NOT NULL DEFAULT '0' COMMENT '已预约数',
  `status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN/FULL/CLOSED/EXPIRED',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slot_service_date_start` (`service_id`,`slot_date`,`start_time`),
  KEY `idx_slot_shop_date` (`shop_id`,`slot_date`),
  KEY `idx_slot_public` (`service_id`,`slot_date`,`status`),
  CONSTRAINT `fk_slot_service` FOREIGN KEY (`service_id`) REFERENCES `products` (`id`),
  CONSTRAINT `fk_slot_shop` FOREIGN KEY (`shop_id`) REFERENCES `shops` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可预约时段';
DROP TABLE IF EXISTS `audit_logs`;
CREATE TABLE `audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operator_id` int NOT NULL,
  `action` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` bigint DEFAULT NULL,
  `request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ip_address` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `detail_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_operator` (`operator_id`,`created_at`),
  KEY `idx_audit_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `cart_items`;
CREATE TABLE `cart_items` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `product_id` int NOT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `checked` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`,`product_id`),
  KEY `idx_user` (`user_id`),
  KEY `fk_cart_product` (`product_id`),
  CONSTRAINT `fk_cart_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cart_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `content_moderation_logs`;
CREATE TABLE `content_moderation_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `target_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` bigint NOT NULL,
  `trigger_source` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AI' COMMENT '触发来源',
  `rule_hits` text COLLATE utf8mb4_unicode_ci COMMENT 'DFA命中结果',
  `target_excerpt` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核内容摘要',
  `latency_ms` bigint DEFAULT NULL COMMENT '审核耗时毫秒',
  `user_id` int NOT NULL,
  `ai_decision` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `risk_level` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `categories` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `image_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `evidence_json` json DEFAULT NULL,
  `confidence` decimal(4,3) DEFAULT NULL,
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `prompt_version` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reviewer_id` int DEFAULT NULL,
  `review_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending',
  `reviewed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_moderation_target` (`target_type`,`target_id`),
  KEY `idx_moderation_review` (`review_status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `conversation_participants`;
CREATE TABLE `conversation_participants` (
  `conversation_id` int NOT NULL,
  `user_id` int NOT NULL,
  `last_read_message_id` int DEFAULT NULL,
  `unread_count` int NOT NULL DEFAULT '0',
  `last_read_at` datetime DEFAULT NULL,
  `deleted_at` datetime DEFAULT NULL COMMENT '当前用户删除会话时间',
  `cleared_before_message_id` int DEFAULT NULL COMMENT '当前用户可见历史起点',
  `archived` tinyint(1) NOT NULL DEFAULT '0',
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`conversation_id`,`user_id`),
  KEY `idx_participant_inbox` (`user_id`,`archived`,`unread_count`),
  KEY `idx_participant_visible` (`user_id`,`deleted_at`,`conversation_id`),
  CONSTRAINT `fk_participant_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_participant_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_participant_unread` CHECK ((`unread_count` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `conversations`;
CREATE TABLE `conversations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `category` enum('community','mall','service') COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('private_letter','customer_service','consult') COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` int NOT NULL,
  `target_id` int NOT NULL,
  `last_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_message_id` int DEFAULT NULL,
  `last_message_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target_cat` (`user_id`,`target_id`,`category`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_target_id` (`target_id`),
  KEY `idx_category` (`category`),
  KEY `idx_conversation_activity` (`last_message_at`,`updated_at`),
  CONSTRAINT `chk_conversation_scene` CHECK ((((`category` = _utf8mb4'community') and (`type` = _utf8mb4'private_letter')) or ((`category` = _utf8mb4'mall') and (`type` = _utf8mb4'customer_service')) or ((`category` = _utf8mb4'service') and (`type` = _utf8mb4'consult'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `knowledge_articles`;
CREATE TABLE `knowledge_articles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `species` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'all',
  `topic` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `publisher` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `published_on` date DEFAULT NULL,
  `reviewed_on` date DEFAULT NULL,
  `review_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'draft',
  `reviewer_id` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_status_species` (`review_status`,`species`),
  KEY `fk_knowledge_reviewer` (`reviewer_id`),
  CONSTRAINT `fk_knowledge_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `knowledge_chunks`;
CREATE TABLE `knowledge_chunks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `article_id` bigint NOT NULL,
  `chunk_order` int NOT NULL DEFAULT '0',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `search_keywords` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_chunk_article` (`article_id`,`chunk_order`),
  FULLTEXT KEY `ft_knowledge_chunk_content` (`content`) /*!50100 WITH PARSER `ngram` */ ,
  CONSTRAINT `fk_knowledge_chunk_article` FOREIGN KEY (`article_id`) REFERENCES `knowledge_articles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `messages`;
CREATE TABLE `messages` (
  `id` int NOT NULL AUTO_INCREMENT,
  `sender_id` int DEFAULT NULL,
  `receiver_id` int NOT NULL,
  `category` enum('community','mall','service') COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('private_letter','customer_service','consult') COLLATE utf8mb4_unicode_ci NOT NULL,
  `conversation_id` int NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_type` enum('text','image','product_card','service_card','order_card','system') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'text',
  `related_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `related_id` bigint DEFAULT NULL,
  `client_message_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `read_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_client` (`conversation_id`,`sender_id`,`client_message_id`),
  KEY `idx_receiver` (`receiver_id`),
  KEY `idx_sender` (`sender_id`),
  KEY `idx_type` (`type`),
  KEY `idx_category` (`category`),
  KEY `idx_conversation` (`conversation_id`),
  KEY `idx_message_cursor` (`conversation_id`,`id`),
  KEY `idx_message_unread` (`conversation_id`,`receiver_id`,`is_read`),
  CONSTRAINT `fk_messages_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_msg_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_msg_sender` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_message_scene` CHECK ((((`category` = _utf8mb4'community') and (`type` = _utf8mb4'private_letter')) or ((`category` = _utf8mb4'mall') and (`type` = _utf8mb4'customer_service')) or ((`category` = _utf8mb4'service') and (`type` = _utf8mb4'consult'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `notifications`;
CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `sender_id` int DEFAULT NULL,
  `source_message_id` int DEFAULT NULL,
  `category` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `level` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'info',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `related_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `related_id` bigint DEFAULT NULL,
  `action_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dedupe_key` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `read_at` datetime DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `deleted_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_dedupe` (`user_id`,`dedupe_key`),
  UNIQUE KEY `uk_notification_source` (`user_id`,`source_message_id`),
  KEY `idx_notification_inbox` (`user_id`,`is_read`,`created_at`),
  KEY `idx_notification_category` (`user_id`,`category`,`created_at`),
  KEY `fk_notification_sender` (`sender_id`),
  CONSTRAINT `fk_notification_sender` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_notification_category` CHECK ((`category` in (_utf8mb4'system',_utf8mb4'community',_utf8mb4'mall',_utf8mb4'service',_utf8mb4'health')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `order_appointment_slots`;
CREATE TABLE `order_appointment_slots` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` int NOT NULL COMMENT '预约订单ID',
  `slot_id` int NOT NULL COMMENT '预约时段ID',
  `pet_id` int NOT NULL COMMENT '预约宠物ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_slot` (`order_id`,`slot_id`),
  KEY `idx_order_slot_order` (`order_id`),
  KEY `idx_order_slot_pet` (`pet_id`),
  KEY `fk_order_slot_slot` (`slot_id`),
  CONSTRAINT `fk_order_slot_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `fk_order_slot_pet` FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`),
  CONSTRAINT `fk_order_slot_slot` FOREIGN KEY (`slot_id`) REFERENCES `appointment_slots` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约订单与时段关联';
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `id` int NOT NULL AUTO_INCREMENT,
  `order_no` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` int NOT NULL,
  `shop_id` int NOT NULL,
  `order_type` enum('product','booking') COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `status` enum('pending_payment','paid','shipped','completed','cancelled','confirmed') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending_payment',
  `appointment_time` datetime DEFAULT NULL,
  `appointment_end_time` datetime DEFAULT NULL COMMENT '多时段预约的最后结束时间',
  `appointment_duration` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `appointment_pet_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预约时宠物名称快照',
  `shipping_address` json DEFAULT NULL,
  `items` json NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user` (`user_id`),
  KEY `idx_shop` (`shop_id`),
  KEY `idx_status` (`status`),
  KEY `idx_order_type` (`order_type`),
  KEY `idx_orders_appointment_reminder` (`order_type`,`status`,`appointment_time`),
  CONSTRAINT `fk_orders_shop` FOREIGN KEY (`shop_id`) REFERENCES `shops` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_orders_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `pet_allergies`;
CREATE TABLE `pet_allergies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pet_id` int NOT NULL,
  `allergen_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `severity` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'unknown',
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_allergen` (`pet_id`,`allergen_code`),
  KEY `idx_pet_allergies_pet` (`pet_id`),
  CONSTRAINT `fk_pet_allergy_pet` FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `pet_care_plans`;
CREATE TABLE `pet_care_plans` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pet_id` int NOT NULL,
  `care_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `planned_on` date NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending',
  `note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pet_care_plan_pet_status` (`pet_id`,`status`,`planned_on`),
  CONSTRAINT `fk_pet_care_plan_pet` FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `pet_deworming_records`;
CREATE TABLE `pet_deworming_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pet_id` int NOT NULL,
  `product_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `treated_on` date DEFAULT NULL,
  `next_due_on` date DEFAULT NULL,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_deworming_pet` (`pet_id`),
  KEY `idx_deworming_due` (`next_due_on`),
  CONSTRAINT `fk_deworming_pet` FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `pet_vaccinations`;
CREATE TABLE `pet_vaccinations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pet_id` int NOT NULL,
  `vaccine_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `vaccinated_on` date DEFAULT NULL,
  `next_due_on` date DEFAULT NULL,
  `batch_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `vet_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_vaccination_pet` (`pet_id`),
  KEY `idx_vaccination_due` (`next_due_on`),
  CONSTRAINT `fk_vaccination_pet` FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `pet_weight_records`;
CREATE TABLE `pet_weight_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pet_id` int NOT NULL,
  `weight` decimal(6,2) NOT NULL,
  `recorded_on` date NOT NULL,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_weight_pet_date` (`pet_id`,`recorded_on`),
  CONSTRAINT `fk_weight_pet` FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `pets`;
CREATE TABLE `pets` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `species` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `breed` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gender` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `color` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `birth_date` date DEFAULT NULL,
  `neutered` tinyint(1) NOT NULL DEFAULT '0',
  `weight` decimal(6,2) DEFAULT NULL,
  `photo_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `medical_notes` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'normal',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pets_user` (`user_id`,`status`),
  CONSTRAINT `fk_pets_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `post_comments`;
CREATE TABLE `post_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_message_id` int DEFAULT NULL,
  `post_id` int NOT NULL,
  `user_id` int NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `root_id` bigint DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'normal',
  `like_count` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_source` (`source_message_id`),
  KEY `idx_comments_post` (`post_id`,`status`,`created_at`),
  KEY `idx_comments_parent` (`parent_id`),
  KEY `idx_comments_user` (`user_id`,`created_at`),
  KEY `idx_comment_review` (`status`,`created_at`),
  KEY `idx_comments_status_created` (`status`,`created_at`),
  CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `post_comments` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_comment_post` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `posts`;
CREATE TABLE `posts` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `category` enum('晒宠','求助','领养','好物','日常') COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `image` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `likes` int NOT NULL DEFAULT '0',
  `comments` int NOT NULL DEFAULT '0',
  `status` enum('normal','pending','blocked') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'normal',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`),
  KEY `idx_posts_status_created` (`status`,`created_at`),
  KEY `idx_posts_deleted_created` (`deleted_at`,`created_at`),
  CONSTRAINT `fk_posts_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `product_categories`;
CREATE TABLE `product_categories` (
  `id` bigint NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `product_type` enum('goods','service') COLLATE utf8mb4_unicode_ci NOT NULL,
  `category_code` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category_level` tinyint NOT NULL DEFAULT '1',
  `sort_order` int NOT NULL DEFAULT '0',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `medical_flag` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_code` (`category_code`),
  KEY `idx_category_tree` (`product_type`,`parent_id`,`enabled`,`sort_order`),
  KEY `fk_category_parent` (`parent_id`),
  CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `product_categories` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `products`;
CREATE TABLE `products` (
  `id` int NOT NULL AUTO_INCREMENT,
  `shop_id` int NOT NULL,
  `product_type` enum('goods','service') COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category_id` bigint NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `target_pet` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ALL' COMMENT '适用宠物：CAT、DOG、ALL',
  `life_stage` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ALL' COMMENT '适用阶段：YOUNG、ADULT、SENIOR、ALL',
  `health_tags` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '商家自由填写的健康标签，逗号分隔',
  `price` decimal(10,2) NOT NULL,
  `stock` int DEFAULT '0',
  `duration` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duration_minutes` int DEFAULT NULL COMMENT '服务时长分钟数（15~480），排班时段切分依据',
  `image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('on','off') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'on',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_shop` (`shop_id`),
  KEY `idx_type_status` (`product_type`,`status`),
  KEY `idx_product_category` (`category_id`,`status`,`product_type`),
  KEY `idx_products_target_pet` (`target_pet`),
  KEY `idx_products_life_stage` (`life_stage`),
  CONSTRAINT `fk_products_category` FOREIGN KEY (`category_id`) REFERENCES `product_categories` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_products_shop` FOREIGN KEY (`shop_id`) REFERENCES `shops` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `sensitive_words`;
CREATE TABLE `sensitive_words` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `word` varchar(100) NOT NULL COMMENT '敏感词',
  `category` varchar(32) NOT NULL COMMENT '违规分类',
  `severity` varchar(16) NOT NULL COMMENT '级别：REVIEW/BLOCK',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sensitive_word` (`word`),
  KEY `idx_sensitive_enabled` (`enabled`,`severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='DFA敏感词规则';
DROP TABLE IF EXISTS `shops`;
CREATE TABLE `shops` (
  `id` int NOT NULL AUTO_INCREMENT,
  `owner_id` int NOT NULL,
  `flagship_id` int DEFAULT NULL COMMENT '所属旗舰店ID，旗舰店自身为空',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('mall','service') COLLATE utf8mb4_unicode_ci NOT NULL,
  `shop_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商家内部门店编号',
  `status` enum('open','closed','pending_audit','rejected') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending_audit',
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
  `logo` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `business_hours` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '门店联系电话',
  `city` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所在城市',
  `district` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所在区县',
  `total_sales` decimal(10,2) DEFAULT '0.00',
  `rating` decimal(2,1) NOT NULL DEFAULT '0.0' COMMENT '店铺综合评分(0.0-5.0)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shops_owner_code` (`owner_id`,`shop_code`),
  UNIQUE KEY `uk_shops_one_flagship` (((case when ((`type` = _utf8mb4'mall') and (`archived_at` is null)) then `owner_id` else NULL end))),
  KEY `idx_owner` (`owner_id`),
  KEY `idx_status` (`status`),
  KEY `fk_shop_flagship` (`flagship_id`),
  KEY `idx_shops_owner_type_status` (`owner_id`,`type`,`status`),
  CONSTRAINT `fk_shop_flagship` FOREIGN KEY (`flagship_id`) REFERENCES `shops` (`id`),
  CONSTRAINT `fk_shops_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `user_follows`;
CREATE TABLE `user_follows` (
  `follower_id` int NOT NULL COMMENT '关注者用户ID',
  `followee_id` int NOT NULL COMMENT '被关注者用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`follower_id`,`followee_id`),
  KEY `idx_user_follows_follower_time` (`follower_id`,`created_at`),
  KEY `idx_user_follows_followee_time` (`followee_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户关注关系';
DROP TABLE IF EXISTS `user_reminders`;
CREATE TABLE `user_reminders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `pet_id` int DEFAULT NULL,
  `title` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reminder_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'health',
  `remind_at` datetime DEFAULT NULL,
  `note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_reminder_due` (`status`,`remind_at`),
  KEY `idx_user_reminder_user` (`user_id`,`remind_at`),
  KEY `fk_user_reminder_pet` (`pet_id`),
  CONSTRAINT `fk_user_reminder_pet` FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_reminder_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` enum('user','business','admin') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user',
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `city` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('normal','disabled') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'normal',
  `pets` json DEFAULT NULL,
  `addresses` json DEFAULT NULL,
  `liked_posts` json DEFAULT NULL,
  `following` json DEFAULT NULL,
  `followers` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 基础数据（运行必需）：账号、店铺、商品分类、商品、知识库语料、敏感词
-- ============================================================

LOCK TABLES `users` WRITE;
INSERT INTO `users` (`id`, `username`, `password`, `role`, `nickname`, `phone`, `email`, `city`, `avatar`, `status`, `pets`, `addresses`, `liked_posts`, `following`, `followers`, `created_at`, `updated_at`) VALUES (1,'admin','$2a$12$uBRMFkwdYgVWkQcNvI.zB.U7jBwzNVieghOtTR0tVTcQsGMMJ6/O2','admin','系统管理员','10000000000','admin@pet-home.com','北京',NULL,'normal',NULL,NULL,NULL,NULL,NULL,'2026-06-22 00:55:46','2026-10-25 17:17:53'),(2,'user1','$2a$12$JqZqYC6fytaFVan9X2iVkO1ALftJHv.GdbKUmW/GpWNzTclX64XL6','user','小狗果果','13800000008','user1@example.com','北京',NULL,'normal','[{\"name\": \"果果\", \"breed\": \"金毛\", \"color\": \"金黄色\", \"photo\": \"\", \"gender\": \"公\", \"weight\": \"40\", \"species\": \"猫\", \"neutered\": true, \"vaccines\": [{\"date\": \"2024-03-14\", \"name\": \"犬八联疫苗\", \"nextDate\": \"2025-03-14\"}, {\"date\": \"2024-05-08\", \"name\": \"狂犬疫苗\", \"nextDate\": \"\"}, {\"date\": \"2025-03-16\", \"name\": \"犬八联疫苗\", \"nextDate\": \"\"}], \"birthDate\": \"2024-01-22\", \"weightRecords\": [{\"date\": \"2026-06-03\", \"note\": \"最近经常带果果出去玩\", \"weight\": 41}, {\"date\": \"2026-02-12\", \"note\": \"过年吃了好多肉食\", \"weight\": 42}], \"medicalHistory\": \"无既往重大病史；对海鲜类食物过敏，食用后会出现皮肤红疹、瘙痒症状\"}, {\"name\": \"鱼鱼\", \"breed\": \"橘猫\", \"color\": \"黄色\", \"photo\": \"\", \"gender\": \"公\", \"weight\": \"4.8\", \"species\": \"猫\", \"birthDate\": \"2023-03-25\"}]','[{\"city\": \"杭州市\", \"name\": \"张三\", \"phone\": \"13900001111\", \"detail\": \"文三路100号\", \"district\": \"西湖区\", \"province\": \"浙江省\"}]','[1, 6, 10, 3, 18, 7, 8, 16, 9, 5]','[4, 3]','[4, 3]','2026-06-22 00:55:46','2026-10-29 19:22:18'),(3,'user2','$2a$12$pd12V2Nv4o2r5elro1p.cOoj9.W/QRFl7UxUeP0iWbQBsgj/YfCLi','user','豆豆是洗袜袜','13800000002','user2@example.com','上海',NULL,'normal',NULL,NULL,NULL,'[2]','[2]','2026-06-22 00:55:46','2026-10-30 22:08:03'),(4,'user3','123456','user','方块人','13800000003','user3@example.com','广州',NULL,'normal',NULL,NULL,NULL,'[2]','[2]','2026-06-22 00:55:46','2026-06-23 04:35:57'),(5,'biz1','$2a$12$3NiobDwP0bby5tX2P0oq0eqCxFTHnXesJl3WL9qZ7SVO/HyhAk5ze','business','宠物乐园','13900000001','biz1@example.com','北京',NULL,'normal',NULL,NULL,NULL,NULL,NULL,'2026-06-22 00:55:46','2026-10-29 17:49:13'),(6,'biz2','$2a$12$sSxvIjoe/uHpOFq/lMeA6.Fk0IRTyoQfF6DoEko9iwyj0H8bVe0cO','business','萌宠生活','13900000002','biz2@example.com','上海',NULL,'normal',NULL,NULL,NULL,NULL,NULL,'2026-06-22 00:55:46','2026-10-29 17:49:13'),(7,'biz3','123456','business','爱宠星球','13900000003','biz3@example.com','深圳',NULL,'normal',NULL,NULL,NULL,NULL,NULL,'2026-06-22 00:55:46','2026-10-30 17:56:33');
UNLOCK TABLES;

LOCK TABLES `shops` WRITE;
INSERT INTO `shops` (`id`, `owner_id`, `flagship_id`, `name`, `type`, `shop_code`, `status`, `archived_at`, `logo`, `description`, `business_hours`, `address`, `contact_phone`, `city`, `district`, `total_sales`, `rating`, `created_at`) VALUES (1,5,NULL,'宠物乐园旗舰店','mall','FLAG-000001','open',NULL,NULL,'品牌宠物食品、玩具、服饰，一站式购物','09:00-21:00','北京市朝阳区宠物大道1号','13900000001',NULL,NULL,15000.00,4.5,'2026-06-22 00:55:46'),(2,5,1,'宠物乐园服务中心','service','OUT-000002','open',NULL,NULL,'专业宠物美容、寄养、健康护理','10:00-20:00','北京市朝阳区宠物大道2号','13900000001',NULL,NULL,8000.00,4.8,'2026-06-22 00:55:46'),(3,6,NULL,'萌宠生活优选','mall','FLAG-000003','open',NULL,NULL,'全球精选宠物用品，品质保障','08:00-22:00','上海市浦东新区萌宠路1号','13900000002',NULL,NULL,22000.00,4.2,'2026-06-22 00:55:46'),(4,6,3,'萌宠生活护理站','service','OUT-000004','open',NULL,NULL,'宠物洗澡、造型、训练一条龙','10:00-19:00','上海市浦东新区萌宠路2号','13900000002',NULL,NULL,12000.00,4.6,'2026-06-22 00:55:46'),(5,7,NULL,'爱宠星球商城','mall','FLAG-000005','open',NULL,NULL,'智能宠物设备、有机食品，科技养宠','09:30-21:30','深圳市南山区科技园路10号','13900000003',NULL,NULL,18000.00,4.3,'2026-06-22 00:55:46'),(6,7,5,'爱宠星球服务中心','service','OUT-000006','closed',NULL,NULL,'宠物医疗咨询、上门服务、紧急救助','24小时','深圳市南山区科技园路11号','13900000003',NULL,NULL,9500.00,4.7,'2026-06-22 00:55:46'),(16,5,1,'小猫小狗服装','service','S-58AB8860','rejected',NULL,'','各种各样的衣服裤子','8：00-22：00','湖南省岳阳楼区','18845612379',NULL,NULL,0.00,0.0,'2026-11-01 15:44:06');
UNLOCK TABLES;

LOCK TABLES `product_categories` WRITE;
INSERT INTO `product_categories` (`id`, `parent_id`, `product_type`, `category_code`, `category_name`, `category_level`, `sort_order`, `enabled`, `medical_flag`, `created_at`) VALUES (1000,NULL,'goods','MALL_FOOD','主粮',1,10,1,0,'2026-10-25 17:24:48'),(1001,1000,'goods','MALL_FOOD_CAT','猫主粮',2,11,1,0,'2026-10-25 17:24:48'),(1002,1000,'goods','MALL_FOOD_DOG','犬主粮',2,12,1,0,'2026-10-25 17:24:48'),(1003,1000,'goods','MALL_FOOD_YOUNG','幼年主粮',2,13,1,0,'2026-10-25 17:24:48'),(1100,NULL,'goods','MALL_SNACK','零食',1,20,1,0,'2026-10-25 17:24:48'),(1101,1100,'goods','MALL_SNACK_CHEW','磨牙棒',2,21,1,0,'2026-10-25 17:24:48'),(1102,1100,'goods','MALL_SNACK_FREEZE_DRIED','冻干',2,22,1,0,'2026-10-25 17:24:48'),(1200,NULL,'goods','MALL_NUTRITION','营养保健',1,30,1,0,'2026-10-25 17:24:48'),(1201,1200,'goods','MALL_NUTRITION_VITAMIN','维生素',2,31,1,0,'2026-10-25 17:24:48'),(1202,1200,'goods','MALL_NUTRITION_PROBIOTIC','益生菌',2,32,1,0,'2026-10-25 17:24:48'),(1203,1200,'goods','MALL_NUTRITION_HAIRBALL','化毛',2,33,1,0,'2026-10-25 17:24:48'),(1300,NULL,'goods','MALL_CLEANING','清洁护理',1,40,1,0,'2026-10-25 17:24:48'),(1301,1300,'goods','MALL_CLEANING_LITTER_BOX','猫砂盆',2,41,1,0,'2026-10-25 17:24:48'),(1400,NULL,'goods','MALL_HOME','居家用品',1,50,1,0,'2026-10-25 17:24:48'),(1401,1400,'goods','MALL_HOME_WATER','饮水机',2,51,1,0,'2026-10-25 17:24:48'),(1402,1400,'goods','MALL_HOME_BED','窝垫',2,52,1,0,'2026-10-25 17:24:48'),(1403,1400,'goods','MALL_HOME_CAT_TREE','猫爬架',2,53,1,0,'2026-10-25 17:24:48'),(1500,NULL,'goods','MALL_TOY','玩具训练',1,60,1,0,'2026-10-25 17:24:48'),(1501,1500,'goods','MALL_TOY_CAT_TEASER','逗猫棒',2,61,1,0,'2026-10-25 17:24:48'),(1502,1500,'goods','MALL_TOY_BALL','漏食球',2,62,1,0,'2026-10-25 17:24:48'),(1600,NULL,'goods','MALL_TRAVEL','出行穿戴',1,70,1,0,'2026-10-25 17:24:48'),(1601,1600,'goods','MALL_TRAVEL_COLLAR','项圈',2,71,1,0,'2026-10-25 17:24:48'),(1602,1600,'goods','MALL_TRAVEL_BACKPACK','背包',2,72,1,0,'2026-10-25 17:24:48'),(1603,1600,'goods','MALL_TRAVEL_RAINCOAT','雨衣',2,73,1,0,'2026-10-25 17:24:48'),(1604,1600,'goods','MALL_TRAVEL_CLOTHING','服饰',2,74,1,0,'2026-10-25 17:24:48'),(2000,NULL,'service','CARE_HEALTH','医疗健康',1,10,1,1,'2026-10-25 17:25:50'),(2001,2000,'service','CARE_HEALTH_CHECKUP','体检',2,11,1,1,'2026-10-25 17:25:50'),(2002,2000,'service','CARE_HEALTH_VACCINE','疫苗',2,12,1,1,'2026-10-25 17:25:50'),(2003,2000,'service','CARE_HEALTH_CONSULT','在线问诊',2,13,1,1,'2026-10-25 17:25:50'),(2100,NULL,'service','CARE_GROOMING','美容洗护',1,20,1,0,'2026-10-25 17:25:50'),(2101,2100,'service','CARE_GROOMING_BASIC','基础洗护',2,21,1,0,'2026-10-25 17:25:50'),(2102,2100,'service','CARE_GROOMING_STYLE','造型美容',2,22,1,0,'2026-10-25 17:25:50'),(2103,2100,'service','CARE_GROOMING_SPA','SPA',2,23,1,0,'2026-10-25 17:25:50'),(2104,2100,'service','CARE_GROOMING_CAT','猫咪专护',2,24,1,0,'2026-10-25 17:25:50'),(2200,NULL,'service','CARE_BOARDING','寄养托管',1,30,1,0,'2026-10-25 17:25:50'),(2201,2200,'service','CARE_BOARDING_STORE','门店寄养',2,31,1,0,'2026-10-25 17:25:50'),(2202,2200,'service','CARE_BOARDING_HOME','家庭寄养',2,32,1,0,'2026-10-25 17:25:50'),(2300,NULL,'service','CARE_TRAINING','训练行为',1,40,1,0,'2026-10-25 17:25:50'),(2301,2300,'service','CARE_TRAINING_PUPPY','幼犬训练',2,41,1,0,'2026-10-25 17:25:50'),(2302,2300,'service','CARE_TRAINING_BEHAVIOR','行为纠正',2,42,1,0,'2026-10-25 17:25:50'),(2303,2300,'service','CARE_TRAINING_SOCIAL','社交训练',2,43,1,0,'2026-10-25 17:25:50'),(2400,NULL,'service','CARE_HOME','上门照护',1,50,1,0,'2026-10-25 17:25:50'),(2401,2400,'service','CARE_HOME_FEEDING','上门喂养',2,51,1,0,'2026-10-25 17:25:50'),(2500,NULL,'service','CARE_LIFE','生活服务',1,60,1,0,'2026-10-25 17:25:50'),(2501,2500,'service','CARE_LIFE_PHOTO','宠物摄影',2,61,1,0,'2026-10-25 17:25:50');
UNLOCK TABLES;

LOCK TABLES `products` WRITE;
INSERT INTO `products` (`id`, `shop_id`, `product_type`, `category`, `category_id`, `name`, `description`, `target_pet`, `life_stage`, `health_tags`, `price`, `stock`, `duration`, `image_url`, `status`, `created_at`) VALUES (1,1,'goods','幼年主粮',1003,'天然幼猫粮1.5kg','优质鸡肉配方，促进消化吸收，适合6个月以下幼猫。','CAT','YOUNG','肠胃呵护,营养均衡',129.00,198,NULL,NULL,'on','2026-06-22 19:15:08'),(2,1,'goods','逗猫棒',1501,'电动逗猫棒','自动摇摆，带羽毛和铃铛，USB充电，逗猫利器。','CAT','ALL','互动训练,体重管理',49.90,150,NULL,NULL,'on','2026-06-22 19:15:08'),(3,1,'goods','猫砂盆',1301,'封闭式猫砂盆','防外溅设计，活性炭过滤除臭，大空间，适合多猫家庭。','CAT','ALL','清洁护理',89.00,80,NULL,NULL,'on','2026-06-22 19:15:08'),(4,1,'goods','雨衣',1603,'狗狗防水雨衣','轻便透气，可调节魔术贴，反光条设计，雨天出行必备。','DOG','ALL','出行防护',69.00,100,NULL,NULL,'on','2026-06-22 19:15:08'),(5,1,'goods','维生素',1201,'综合维生素咀嚼片','含12种必需维生素，改善皮毛，增强免疫力，犬猫通用。','ALL','ALL','营养补充',98.00,120,NULL,NULL,'on','2026-06-22 19:15:08'),(6,2,'service','造型美容',2102,'精致宠物美容套餐','包含洗澡、剪毛、指甲修剪、耳朵清洁，全程60分钟。','ALL','ADULT','美毛,皮肤护理',198.00,0,'60分钟',NULL,'on','2026-06-22 19:15:08'),(7,2,'service','门店寄养',2201,'标准宠物寄养','独立房间，每日遛弯2次，提供基础粮，实时监控可看。','ALL','ALL','寄养照护',88.00,0,'1天',NULL,'on','2026-06-22 19:15:08'),(8,2,'service','体检',2001,'宠物基础健康体检','体格检查、血常规、耳道检查、皮肤评估，20项指标。','ALL','ADULT','健康体检',299.00,0,'40分钟',NULL,'on','2026-06-22 19:15:08'),(9,2,'service','幼犬训练',2301,'幼犬行为训练课','定点排便、坐下、随行等基础指令，一对一指导，每次45分钟。','DOG','YOUNG','行为训练',158.00,0,'45分钟',NULL,'on','2026-06-22 19:15:08'),(10,2,'service','宠物摄影',2501,'宠物艺术摄影','专业影棚拍摄，精修10张，提供服装道具，记录萌宠瞬间。','ALL','ADULT','生活记录',399.00,0,'90分钟',NULL,'on','2026-06-22 19:15:08'),(11,3,'goods','犬主粮',1002,'进口无谷狗粮2kg','低敏配方，三文鱼+甘薯，适合敏感肠胃，全犬期适用。','DOG','ALL','肠胃养护,美毛',169.00,149,NULL,NULL,'on','2026-06-22 19:15:08'),(12,3,'goods','漏食球',1502,'耐咬漏食球','可填充零食，锻炼智力，天然橡胶，中大型犬最爱。','DOG','ALL','行为训练,口腔清洁',59.00,199,NULL,NULL,'on','2026-06-22 19:15:08'),(13,3,'goods','背包',1602,'宠物外出背包','透明舱设计，透气网窗，可折叠，适合猫和小型犬。','ALL','ALL','出行防护',119.00,60,NULL,NULL,'on','2026-06-22 19:15:08'),(14,3,'goods','项圈',1601,'真皮铆钉项圈','头层牛皮，金属铆钉，可调节尺寸，帅气酷炫。','DOG','ALL','出行防护',89.00,90,NULL,NULL,'on','2026-06-22 19:15:08'),(15,3,'goods','化毛',1203,'猫咪化毛膏','天然植物油，温和排毛球，添加益生元，适口性佳。','CAT','ADULT','化毛护理,肠胃养护',78.00,180,NULL,NULL,'on','2026-06-22 19:15:08'),(16,4,'service','SPA',2103,'宠物SPA水疗','深层清洁+按摩浴，滋养皮毛，放松肌肉，用时40分钟。','ALL','ADULT','美毛,皮肤护理',168.00,0,'40分钟',NULL,'on','2026-06-22 19:15:08'),(17,4,'service','家庭寄养',2202,'家庭式温馨寄养','居家环境，自由活动，与主人视频连线，每日早中晚三次遛弯。','ALL','ALL','寄养照护',128.00,0,'1天',NULL,'on','2026-06-22 19:15:08'),(18,4,'service','疫苗',2002,'猫三联疫苗接种','进口妙三多疫苗，接种前基础检查，留观30分钟。','CAT','ALL','免疫管理',120.00,0,'30分钟',NULL,'on','2026-06-22 19:15:08'),(19,4,'service','行为纠正',2302,'狗狗礼仪训练','不扑人、不爆冲、听从口令，7天速成班，每天1小时。','DOG','ALL','行为训练',688.00,0,'7天',NULL,'on','2026-06-22 19:15:08'),(20,4,'service','基础洗护',2101,'基础洗澡护理','沐浴+吹干+指甲修剪，30分钟快速服务，适合日常清洁。','ALL','ADULT','清洁护理,皮肤护理',68.00,0,'30分钟',NULL,'on','2026-06-22 19:15:08'),(21,5,'goods','冻干',1102,'冻干生骨肉零食','完整生骨肉配方，-40℃冻干锁鲜，复水即食，猫狗通用。','ALL','ALL','营养补充',88.00,299,NULL,NULL,'on','2026-06-22 19:15:08'),(22,5,'goods','逗猫棒',1501,'静音激光逗猫笔','USB充电，5种图案切换，安全激光，自动循环模式。','CAT','ALL','',39.90,250,NULL,NULL,'on','2026-06-22 19:15:08'),(23,5,'goods','饮水机',1401,'智能自动饮水机','2L大容量，四重过滤，静音泵，循环活水，吸引宠物多喝水。','ALL','ALL','饮水管理',199.00,100,NULL,NULL,'on','2026-06-22 19:15:08'),(24,5,'goods','服饰',1604,'夏季透气凉凉衣','UPF50+防晒，冰凉面料，物理降温，外出防暑必备。','DOG','ALL','出行防护,皮肤护理',79.00,120,NULL,NULL,'on','2026-06-22 19:15:08'),(25,5,'goods','益生菌',1202,'肠胃益生菌粉','5大活菌+益生元，调理腹泻便秘，改善胃口，每盒30袋。','ALL','ALL','肠胃呵护',89.00,160,NULL,NULL,'on','2026-06-22 19:15:08'),(26,6,'service','在线问诊',2003,'在线宠物医生问诊','执业兽医师1v1视频咨询，日常疾病、喂养建议，15分钟/次。','ALL','ADULT','健康咨询',58.00,0,'15分钟',NULL,'on','2026-06-22 19:15:08'),(27,6,'service','上门喂养',2401,'宠物上门喂养','上门添粮换水、清理猫砂、简单陪玩，适合短期出行用户。','ALL','ALL','上门照护',68.00,0,'30分钟',NULL,'on','2026-06-22 19:15:08'),(28,6,'service','猫咪专护',2104,'猫咪精致修剪','根据品种设计造型，去底绒、修剪脚毛、肛周清洁，猫咪专用。','CAT','ADULT','美毛,皮肤护理',218.00,0,'90分钟',NULL,'on','2026-06-22 19:15:08'),(29,6,'service','门店寄养',2201,'VIP豪华寄养','超大独立套房，全天空调，24小时监控，每日营养膏补充。','ALL','ALL','寄养照护',258.00,0,'1天',NULL,'on','2026-06-22 19:15:08'),(30,6,'service','社交训练',2303,'宠物社交训练','与其他宠物互动，改善胆小或攻击行为，小班制户外课程。','DOG','ALL','行为训练',188.00,0,'60分钟',NULL,'on','2026-06-22 19:15:08'),(31,1,'goods','猫主粮',1001,'室内成猫低脂粮 2kg','专为室内活动量少的猫咪设计，低脂肪高纤维，帮助体重管理','CAT','ADULT','体重管理,低敏',99.00,180,NULL,NULL,'on','2026-06-22 21:34:23'),(32,1,'goods','犬主粮',1002,'全价全犬期狗粮 1.5kg','无谷配方，单一鸡肉蛋白，适合敏感肠胃狗狗','DOG','ALL','营养均衡',139.00,120,NULL,NULL,'on','2026-06-22 21:34:23'),(33,1,'goods','化毛',1203,'猫咪化毛膏 120g','麦芽配方，温和排出毛球，添加鱼油护毛','CAT','ADULT','化毛护理,肠胃养护',58.00,347,NULL,NULL,'on','2026-06-22 21:34:23'),(34,1,'goods','饮水机',1401,'宠物智能饮水机 2L','循环过滤，静音水泵，缺水提醒，流动水吸引宠物多喝水','ALL','ALL','饮水管理',159.00,88,NULL,NULL,'on','2026-06-22 21:34:23'),(35,1,'goods','益生菌',1202,'犬猫益生菌粉 30袋装','调理软便腹泻，耐胃酸菌株，日常保健或肠胃恢复期均可使用','ALL','ALL','肠胃呵护',85.00,200,NULL,NULL,'on','2026-06-22 21:34:23'),(37,2,'service','造型美容',2102,'精致宠物洗护套餐','包含深层清洁、护毛素护理、指甲修剪、耳道清洁，全程低应激手法','ALL','ADULT','美毛,清洁护理',128.00,NULL,'80分钟',NULL,'off','2026-06-23 00:30:20'),(38,1,'goods','磨牙棒',1101,'宠物狗磨牙棒','高蛋白鸡肉缠绕牛皮卷，双重口感更诱人。添加钙质与薄荷成分，强健牙齿、清新口气，中大型犬也能畅快啃咬，享受持久磨牙乐趣。','DOG','ALL','行为训练,口腔清洁',58.00,99,NULL,NULL,'on','2026-06-23 01:03:20'),(39,1,'goods','窝垫',1402,'四季安枕·可拆洗睡垫','双面可用，秋冬保暖、春夏凉爽；内部填充高回弹PP棉，拆洗方便，底部防滑设计，给狗狗一个安睡的小天地','ALL','ADULT','清洁护理',98.00,198,NULL,NULL,'on','2026-06-23 01:04:05'),(40,1,'goods','猫主粮',1001,'喵享无谷鲜肉粮','高肉含量无谷配方，添加牛磺酸与Omega-3，助力猫咪心肌健康与毛发亮泽，适口性极佳，挑嘴猫也无法抗拒。','ALL','ALL','',125.00,400,NULL,NULL,'on','2026-06-23 01:04:37'),(41,1,'goods','猫爬架',1403,'猫爬架','多层实木结构稳固不摇晃，天然剑麻柱耐抓耐磨，配有吊床、躲藏箱与绒布平台，满足猫咪攀爬、磨爪、休息的天性','ALL','ALL','',58.00,73,NULL,NULL,'off','2026-06-23 01:05:22'),(42,2,'service','幼犬训练',2301,'幼犬早期教育课','8周龄-6月龄幼犬社会化训练，包含定点排泄、基础服从、咬劲控制，共6次课','ALL','ALL','',300.00,NULL,'60分钟',NULL,'on','2026-06-23 01:08:10'),(43,2,'service','造型美容',2102,'宠物美容','清洗全身','ALL','ALL','',99.00,NULL,'60 min',NULL,'on','2026-06-23 01:11:05'),(44,1,'goods','猫主粮',1001,'猫粮','小猫爱吃','ALL','ALL','',20.00,10,NULL,NULL,'on','2026-06-23 05:02:06'),(45,1,'goods','猫主粮',1001,'猫粮','好吃的','ALL','ALL','',50.00,98,NULL,NULL,'on','2026-06-23 08:06:28'),(46,1,'goods','磨牙棒',1101,'宠物狗磨牙棒','111','ALL','ALL','',55.00,5,NULL,NULL,'on','2026-06-23 08:13:35');
UNLOCK TABLES;

LOCK TABLES `knowledge_articles` WRITE;
INSERT INTO `knowledge_articles` (`id`, `title`, `species`, `topic`, `summary`, `source_title`, `source_url`, `publisher`, `published_on`, `reviewed_on`, `review_status`, `reviewer_id`, `created_at`, `updated_at`) VALUES (1,'犬猫误食高风险人类食物','all','中毒急救','巧克力、木糖醇、葡萄及葡萄干、洋葱和大蒜等可能对犬猫造成严重风险，应联系兽医或毒物中心。','People Foods to Avoid Feeding Your Pets','https://www.aspca.org/pet-care/animal-poison-control/people-foods-avoid-feeding-your-pets','ASPCA Animal Poison Control',NULL,'2026-10-24','published',NULL,'2026-10-24 20:45:53','2026-10-24 20:45:53'),(2,'犬猫呼吸困难与泌尿阻塞的急诊信号','all','急诊分诊','呼吸困难、无法排尿、抽搐、大量出血或意识异常需要立即联系急诊兽医，不应等待线上建议。','Emergency Pet Care','https://www.aaha.org/your-pet/pet-owner-education/ask-aaha/emergency-care/','American Animal Hospital Association',NULL,'2026-10-24','published',NULL,'2026-10-24 20:45:53','2026-10-24 20:45:53'),(3,'猫下泌尿道疾病的识别','cat','泌尿系统','频繁进出猫砂盆、仅排出少量尿液、排尿疼痛或尿中带血可能提示下泌尿道疾病；公猫尿闭属于急症。','Feline Lower Urinary Tract Disease','https://www.vet.cornell.edu/departments-centers-and-institutes/cornell-feline-health-center/health-information/feline-health-topics/feline-lower-urinary-tract-disease','Cornell Feline Health Center',NULL,'2026-10-24','published',NULL,'2026-10-24 20:45:53','2026-10-24 20:45:53'),(4,'犬猫疫苗与加强免疫原则','all','疫苗','疫苗方案应根据年龄、既往接种史、生活方式和当地流行病学，由兽医制定；不应仅凭线上表格自行改期。','WSAVA Vaccination Guidelines','https://wsava.org/global-guidelines/vaccination-guidelines/','World Small Animal Veterinary Association',NULL,'2026-10-24','published',NULL,'2026-10-24 20:45:53','2026-10-24 20:45:53'),(5,'犬猫体重管理与体况评估','all','体重营养','体重趋势应结合体况评分、年龄和疾病史评估。减重计划应由兽医制定，避免过快减重或自行极端限食。','2021 AAHA Nutrition and Weight Management Guidelines','https://www.aaha.org/resources/2021-aaha-nutrition-and-weight-management-guidelines/','American Animal Hospital Association','2021-01-01','2026-10-24','published',NULL,'2026-10-24 20:45:53','2026-10-24 20:45:53'),(6,'犬猫完整均衡饮食与换粮原则','all','营养','长期主食应满足相应生命阶段的完整均衡营养要求；换粮通常需要逐步过渡并观察胃肠道反应。','WSAVA Global Nutrition Guidelines','https://wsava.org/global-guidelines/global-nutrition-guidelines/','World Small Animal Veterinary Association',NULL,'2026-10-24','published',NULL,'2026-10-24 20:45:53','2026-10-24 20:45:53'),(7,'疑似食物不良反应与过敏记录','all','皮肤消化','食物不良反应可能与瘙痒、呕吐或腹泻有关。规范诊断通常需要兽医指导下的饮食排除试验，不建议仅靠广告判断。','Food Adverse Reactions in Pets','https://www.merckvetmanual.com/dog-owners/skin-disorders-of-dogs/food-allergies-in-dogs','Merck Veterinary Manual',NULL,'2026-10-24','published',NULL,'2026-10-24 20:45:53','2026-10-24 20:45:53'),(8,'犬糖尿病需要持续兽医管理','dog','内分泌','多饮、多尿和体重下降可能提示糖尿病。胰岛素种类与剂量必须由兽医制定，不能根据单次血糖或网络经验自行调整。','2018 AAHA Diabetes Management Guidelines for Dogs and Cats','https://www.aaha.org/resources/2018-aaha-diabetes-management-guidelines/','American Animal Hospital Association','2018-01-01','2026-10-24','published',NULL,'2026-10-24 20:45:53','2026-10-24 20:45:53'),(10,'犬猫急性呕吐与腹泻的家庭观察与就医时机','all','消化系统','区分可短期观察的轻度胃肠不适与需要立即就诊的危险信号，明确家庭护理边界。','WSAVA Guidelines and Resources','https://wsava.org/guidelines/','WSAVA','2025-03-01','2026-11-01','published',NULL,'2026-11-01 20:25:03','2026-11-01 20:25:03'),(11,'猫上呼吸道感染的识别与家庭护理','cat','呼吸系统','猫疱疹病毒与杯状病毒感染的表现、护理要点与必须就医的信号。','International Cat Care - Upper Respiratory Tract Disease','https://icatcare.org/','International Cat Care','2025-03-01','2026-11-01','published',NULL,'2026-11-01 20:25:03','2026-11-01 20:25:03'),(12,'跳蚤与蜱虫：体外寄生虫的识别与全年防控','all','寄生虫防治','体外寄生虫的危害、环境处理与规范用药原则，避免人药和跨物种误用。','Companion Animal Parasite Council - Fleas and Ticks','https://capcvet.org/','CAPC','2025-03-01','2026-11-01','published',NULL,'2026-11-01 20:25:03','2026-11-01 20:25:03'),(13,'犬猫外耳炎：甩头抓耳的识别与耳道护理','all','皮肤与被毛','外耳炎的常见诱因、家庭护理边界与需要专业检查的情形。','Merck Veterinary Manual - Otitis Externa','https://www.merckvetmanual.com/','Merck Veterinary Manual','2025-03-01','2026-11-01','published',NULL,'2026-11-01 20:25:03','2026-11-01 20:25:03'),(14,'猫癣与瘙痒性皮肤病的鉴别与处理','all','皮肤与被毛','真菌感染的识别、人畜共患风险与规范治疗原则，避免误用激素药膏。','International Cat Care - Ringworm in Cats','https://icatcare.org/','International Cat Care','2025-03-01','2026-11-01','published',NULL,'2026-11-01 20:25:03','2026-11-01 20:25:03'),(15,'绝育手术的术前准备与术后护理','all','外科护理','围手术期的禁食要求、伤口观察要点与需要立即返院的异常信号。','AAHA - Anesthesia and Surgical Guidelines','https://www.aaha.org/','AAHA','2025-03-01','2026-11-01','published',NULL,'2026-11-01 20:25:03','2026-11-01 20:25:03'),(16,'老年犬猫的关节健康与疼痛识别','all','老年护理','退行性关节病的早期信号、体重与环境管理，以及止痛药使用禁忌。','AAHA - Osteoarthritis in Dogs and Cats','https://www.aaha.org/','AAHA','2025-03-01','2026-11-01','published',NULL,'2026-11-01 20:25:03','2026-11-01 20:25:03'),(17,'犬猫中暑与热应激的现场处理','all','急诊','高温环境下的高危因素、现场降温步骤与必须立即就医的征象。','Merck Veterinary Manual - Heatstroke','https://www.merckvetmanual.com/','Merck Veterinary Manual','2025-03-01','2026-11-01','published',NULL,'2026-11-01 20:25:03','2026-11-01 20:25:03'),(18,'犬猫口腔健康与牙周病预防','all','口腔','牙结石与牙周病的危害、家庭口腔护理方法与洗牙的麻醉评估。','American Veterinary Dental College - Periodontal Disease','https://avdc.org/','AVDC','2025-03-01','2026-11-01','published',NULL,'2026-11-01 20:25:03','2026-11-01 20:25:03'),(19,'犬猫驱虫规范与健康记录管理','all','预防保健','体内外寄生虫的驱虫频率原则、记录方式与特殊时期注意事项。','Companion Animal Parasite Council - Parasite Control Guidelines','https://capcvet.org/','CAPC','2025-03-01','2026-11-01','published',NULL,'2026-11-01 20:25:03','2026-11-01 20:25:03');
UNLOCK TABLES;

LOCK TABLES `knowledge_chunks` WRITE;
INSERT INTO `knowledge_chunks` (`id`, `article_id`, `chunk_order`, `content`, `search_keywords`, `created_at`) VALUES (1,1,0,'巧克力中的甲基黄嘌呤、木糖醇、葡萄和葡萄干、洋葱和大蒜、酒精等可能对犬猫有毒。中毒风险与摄入种类、估算摄入量、体重和出现症状有关。发现误食时，应记录包装、成分、估算摄入量和时间，立即联系兽医或专业动物毒物中心，不要自行催吐或喂人药。呼吸急促、抽搐、虚弱或意识异常时按急诊处理。','误食 中毒 巧克力 木糖醇 葡萄 洋葱 大蒜 催吐 急诊','2026-10-24 20:45:53'),(2,2,0,'呼吸困难或明显用力呼吸、舌头颜色异常、无法排尿、反复干呕但无物、抽搐、大量出血、意识异常都属于需要立即就医的危险信号。先保持环境安静并避免强行喂食喂水，尽快联系可接诊的急诊兽医；不要因为等待在线判断而延误。','呼吸困难 无法排尿 尿闭 抽搐 出血 意识异常 急诊 危险信号','2026-10-24 20:45:53'),(3,3,0,'猫频繁进出猫砂盆、排尿时间延长、每次尿量很少、排尿叫唤、血尿或在不合适地点排尿，可能提示猫下泌尿道疾病。尤其公猫完全排不出尿时可能在短时间内危及生命，应立即就医。饮水量、尿量和环境压力可作为就诊信息，但不能替代检查。','猫 泌尿 尿频 血尿 尿闭 公猫 猫砂盆 急诊','2026-10-24 20:45:53'),(4,3,1,'猫下泌尿道疾病的评估通常包括病史、体格检查、尿液检查和影像检查。复发管理可能涉及饮水与体重管理、环境丰富化以及兽医制定的饮食方案。出现急性尿闭时不能只增加饮水或更换猫粮，需要立即解除阻塞并接受兽医处置。','猫 下泌尿道 检查 复发 饮水 体重 猫粮 兽医','2026-10-24 20:45:53'),(5,4,0,'犬猫疫苗接种需要结合年龄、健康状态、既往接种记录、生活方式、旅行和当地疾病风险。幼年动物常有基础免疫程序，成年后需要按指南和风险评估安排加强免疫。疫苗名称、批号和接种日期应保留；若接种记录不完整，应让兽医评估后制定补种方案。','疫苗 幼犬 幼猫 加强针 接种记录 基础免疫 兽医','2026-10-24 20:45:53'),(6,4,1,'疫苗可能引起短暂精神差、食欲下降或注射部位不适，但严重过敏表现如呼吸困难、面部肿胀、反复呕吐或虚弱需要立即就医。接种后应观察并记录异常反应，不要自行使用药物掩盖症状。','疫苗反应 过敏 面部肿胀 呕吐 观察 就医','2026-10-24 20:45:53'),(7,5,0,'判断犬猫体重是否合适不能只看体重数字，还要结合品种、骨架、年龄和体况评分。家庭记录应使用一致条件，例如同一体重秤并记录日期。体重持续上升或下降、肌肉流失或短期明显变化都值得咨询兽医。','体重 体况评分 肥胖 消瘦 肌肉 体重趋势 健康记录','2026-10-24 20:45:53'),(8,5,1,'犬猫减重应设置合理目标并由兽医制定能量和监测计划，过快减重可能带来营养和健康风险。处方减重粮、运动强度和疾病相关饮食调整都需要个体化评估，不能仅凭商品宣传或网络热量表决定。','减重 肥胖 处方粮 能量 运动 营养 兽医 个体化','2026-10-24 20:45:53'),(9,6,0,'选择长期主食时应确认产品适用于对应物种和生命阶段，并关注生产商是否提供完整均衡营养声明、配方适用性和质量控制信息。自制餐需要营养配方审核，长期只喂单一肉类不能视为完整均衡饮食。','主粮 完整均衡 猫粮 狗粮 生命阶段 自制餐 营养','2026-10-24 20:45:53'),(10,6,1,'换粮通常采用数天逐步混合新旧食物的方式，并在过程中观察呕吐、腹泻、瘙痒和精神状态。若症状持续、加重或伴随脱水、血便和疼痛，应停止自行调整并咨询兽医。','换粮 腹泻 呕吐 瘙痒 过渡 观察 兽医','2026-10-24 20:45:53'),(11,7,0,'食物不良反应可表现为皮肤瘙痒、反复耳部炎症、呕吐或腹泻，但并非所有症状都由过敏引起。规范评估需要兽医排除寄生虫、感染和其他疾病，饮食排除试验需要严格控制食物并记录症状变化。','食物过敏 不良反应 瘙痒 呕吐 腹泻 排除饮食 记录','2026-10-24 20:45:53'),(12,7,1,'在没有明确诊断前，不应仅根据商品名称认定其安全或低敏。配料表、交叉污染风险、零食和补充剂都可能影响排除试验。已确认的过敏原应结构化记录，并在选购和就诊时主动告知兽医。','过敏原 低敏 配料 交叉污染 零食 记录 选购','2026-10-24 20:45:53'),(13,8,0,'犬出现多饮、多尿、体重下降或食欲改变时应接受兽医检查，糖尿病的确诊不能仅凭一次家庭观察。胰岛素类型、剂量与注射时间必须由兽医制定，不能在未监测的情况下自行增减剂量或停用。','狗 糖尿病 多饮 多尿 体重下降 胰岛素 剂量 兽医','2026-10-24 20:45:53'),(15,10,0,'犬猫呕吐腹泻常见诱因包括突然换粮、进食过快、捡食垃圾、寄生虫、病毒或细菌性肠炎、异物梗阻、胰腺炎以及食物不耐受。单次呕吐后精神食欲正常、无腹痛表现的情况，通常可先短时观察；频繁呕吐、喷射状呕吐、呕吐物带血或咖啡渣样物，则需要尽快就医。',NULL,'2026-11-01 20:25:03'),(16,10,1,'家庭观察重点有四项：精神状态是否清醒愿意互动、是否还能少量饮水、呕吐与腹泻的频次和性状、有无脱水表现。轻度脱水可见牙龈发干、皮肤捏起后回弹变慢、眼窝略陷。观察期建议少量多次提供清水，不自行禁水超过数小时，不喂人用止泻药与止吐药。',NULL,'2026-11-01 20:25:03'),(17,10,2,'出现以下任一情况应立即联系兽医：幼龄或老年动物发病、呕吐持续超过24小时、腹泻带鲜血或呈柏油样、明显腹痛弓背、疑似误食异物或毒物、伴随发热或抽搐。就诊时带上呕吐物或粪便照片、近期饮食与用药记录，有助于快速判断。',NULL,'2026-11-01 20:25:03'),(18,11,0,'猫上呼吸道感染多由猫疱疹病毒和猫杯状病毒引起，典型表现为打喷嚏、清水样或脓性眼鼻分泌物、结膜充血、声音嘶哑、口腔溃疡和食欲下降。幼猫、多猫家庭和新入家猫风险更高，潜伏期通常数天，可通过飞沫和污染物传播。',NULL,'2026-11-01 20:25:03'),(19,11,1,'家庭护理以保持鼻腔通畅和维持进食为主：提供温暖安静的隔离空间、使用加湿环境帮助稀释分泌物、用温水湿棉片轻拭眼鼻分泌物、把食物稍微加热以提升气味促进食欲。多猫家庭需单独餐具与猫砂盆，护理前后洗手，减少交叉感染。',NULL,'2026-11-01 20:25:03'),(20,11,2,'出现张口呼吸、呼吸急促、持续拒食超过24小时、明显嗜睡或脱水时须尽快就诊，幼猫和老年猫更容易发展为肺炎。康复期仍需观察两周，部分猫会长期携带病毒并在应激时复发。核心疫苗可显著降低发病与重症风险，具体免疫方案由兽医按年龄与健康状况制定。',NULL,'2026-11-01 20:25:03'),(21,12,0,'跳蚤叮咬会引起剧烈瘙痒、过敏性皮炎和脱毛，幼龄动物大量感染可导致贫血；跳蚤还是猫抓病和绦虫的传播媒介。蜱虫附着后吸血，可传播多种血液病原。发现黑色煤渣样颗粒（跳蚤粪便）沾水后呈红褐色，基本可以判定存在跳蚤感染。',NULL,'2026-11-01 20:25:03'),(22,12,1,'防控要点是宠物用药与环境处理同步：按体重选择犬猫专用的体外驱虫产品并全年规律使用，跳蚤卵和幼虫大量存在于地毯、沙发与窝垫，需要吸尘、高温清洗并更换窝垫。多宠家庭必须同时处理，否则很快反弹。',NULL,'2026-11-01 20:25:03'),(23,12,2,'严禁把犬用驱虫药用于猫，部分成分类似菊酯的犬用产品对猫有致命毒性；人用驱虫药不适用于宠物。发现蜱虫时不要用手直接捏碎，应使用细镊贴近皮肤垂直缓慢拔出并消毒，必要时由兽医处理。用药后记录日期与产品名，出现流涎、震颤、呕吐等异常立即联系兽医。',NULL,'2026-11-01 20:25:03'),(24,13,0,'外耳炎表现为频繁甩头、抓挠耳根、耳道异味、棕褐色或黄绿色分泌物、耳廓红肿，触碰耳根时可能躲闪或哀叫。垂耳犬、耳道毛发多的犬以及经常游泳或洗澡进水的动物更易发生。',NULL,'2026-11-01 20:25:03'),(25,13,1,'常见诱因包括耳螨、酵母菌或细菌过度增殖、食物或环境过敏、耳道狭窄与息肉。不同病因用药完全不同，盲目使用含抗生素或激素的滴耳液可能掩盖病情并加重耐药。',NULL,'2026-11-01 20:25:03'),(26,13,2,'家庭护理只做外部清洁：用宠物专用洗耳液湿润棉片擦拭耳廓内侧可见部分，不使用棉签深入耳道，不向耳内吹气或倒水。出现持续甩头、明显疼痛、耳道流脓、头歪或平衡障碍时须就医，由兽医做耳镜检查与分泌物细胞学，再决定用药方案。',NULL,'2026-11-01 20:25:03'),(27,14,0,'猫癣由皮肤癣菌引起，典型表现为圆形或不规则脱毛斑、边缘有皮屑、断毛、皮肤轻度发红，多见于头面部、耳缘和四肢。猫癣可传染给人，家庭成员出现环形红斑应及时就诊皮肤科。确诊需要伍德灯初筛配合真菌培养，仅凭外观容易与细菌感染混淆。',NULL,'2026-11-01 20:25:03'),(28,14,1,'持续瘙痒更常见的原因是跳蚤过敏、环境或食物过敏以及细菌性脓皮病。瘙痒部位、发作季节、是否伴随耳道或肛周问题，都是兽医判断的重要线索。',NULL,'2026-11-01 20:25:03'),(29,14,2,'处理原则是确诊后按医嘱进行系统性抗真菌治疗，同时消毒环境、清洗窝垫、处理多宠家庭的其他动物。不要自行使用人用激素类药膏，激素会抑制局部免疫使真菌扩散；也不要在未确诊时反复洗澡刺激皮肤。治疗周期通常数周，需复查确认治愈。',NULL,'2026-11-01 20:25:03'),(30,15,0,'术前需按医院要求禁食，通常成年动物禁食8小时左右、禁水2到4小时，幼龄动物遵医嘱缩短。术前应完成基础体检与必要血液检查，确认疫苗接种与驱虫状态，并如实告知近期用药、既往麻醉反应和慢性病史。',NULL,'2026-11-01 20:25:03'),(31,15,1,'术后重点是伤口与活动管理：佩戴伊丽莎白圈防止舔咬、保持伤口干燥不沾水、一周内限制跳跃与剧烈运动、每日观察伤口有无红肿渗液。麻醉苏醒期可能出现短暂走路不稳和食欲下降，通常一天内缓解。',NULL,'2026-11-01 20:25:03'),(32,15,2,'出现持续渗血、伤口裂开、明显肿胀发热、超过24小时完全拒食、频繁呕吐、精神沉郁或无法排尿，需要立即联系手术医院。不要自行拆除缝线或涂抹药膏，也不要在伤口未愈合时洗澡。',NULL,'2026-11-01 20:25:03'),(33,16,0,'犬猫关节退行性变的早期信号常被误认为正常衰老：起卧缓慢、上下楼梯犹豫、不再跳上沙发、走路僵硬、散步距离变短、被抱时躲闪或发出声音。猫还可能表现为减少跳跃高度、在低处休息、梳理毛减少。',NULL,'2026-11-01 20:25:03'),(34,16,1,'体重管理是关节负担最直接的干预手段，超重动物减重可明显改善活动能力。家庭环境可增加防滑垫、坡道或矮台阶，猫砂盆改为低边，窝垫放在避免穿堂风的位置，保持适度而非剧烈运动。',NULL,'2026-11-01 20:25:03'),(35,16,2,'疼痛评估与镇痛方案必须由兽医制定。人用止痛药如对乙酰氨基酚、布洛芬对猫狗有严重毒性，绝不可自行给药。出现突然不愿行走、拖行后肢、触碰时剧烈反应或完全拒食，需要尽快就诊排查急性损伤与神经问题。',NULL,'2026-11-01 20:25:03'),(36,17,0,'中暑高危因素包括短鼻犬种、厚毛或深色被毛、肥胖、老年与心肺疾病动物，以及密闭车厢、正午遛狗、剧烈运动后立即进入闷热环境。犬主要靠喘气散热，散热效率远低于人类，因此更容易在高温下迅速恶化。',NULL,'2026-11-01 20:25:03'),(37,17,1,'发现呼吸急促、大量流涎、牙龈发红、步态不稳时，应立即转移到阴凉通风处，用常温水浸湿腹部、腋下和四肢内侧并配合风扇吹风，提供少量饮水。不要用冰水浸泡或酒精擦拭，急剧降温会引起外周血管收缩并加重休克风险。',NULL,'2026-11-01 20:25:03'),(38,17,2,'体温超过40摄氏度、出现抽搐、牙龈发紫或发白、意识模糊、呕吐腹泻，必须立即送医，途中持续物理降温并保持呼吸道通畅。即使现场看似缓解，中暑仍可能在数小时后引发凝血异常与器官损伤，建议由兽医评估。',NULL,'2026-11-01 20:25:03'),(39,18,0,'牙菌斑矿化形成牙结石后，会持续刺激牙龈引发牙龈炎，进一步发展为牙周炎，造成牙龈萎缩、牙齿松动脱落。口腔细菌还可进入血液，与心脏、肾脏和肝脏疾病相关。口臭、牙龈出血、进食偏侧咀嚼、流涎都是常见提示。',NULL,'2026-11-01 20:25:03'),(40,18,1,'家庭护理的核心是规律刷牙：使用宠物专用牙膏与指套或软毛牙刷，从少量开始让动物适应，理想频率为每日一次。洁齿零食、洁齿水与处方粮可作为辅助，但不能替代刷牙。每年体检时由兽医检查口腔。',NULL,'2026-11-01 20:25:03'),(41,18,2,'已经形成的牙结石无法通过刷牙去除，需要麻醉下洁治。洗牙前必须做麻醉风险评估与血液检查，尤其对老年动物和已有心脏病的动物。不要自行用工具抠除牙结石，容易损伤牙龈并造成感染。',NULL,'2026-11-01 20:25:03'),(42,19,0,'常规驱虫覆盖蛔虫、钩虫、绦虫等肠道寄生虫以及跳蚤、蜱虫等体外寄生虫，心丝虫预防需按地区流行情况决定。幼龄动物初次驱虫频率高于成年动物，具体间隔按产品说明与兽医建议执行。',NULL,'2026-11-01 20:25:03'),(43,19,1,'成年动物的驱虫频率与生活方式相关：经常外出、接触其他动物、进入草地或林区的动物需要更严格的体外驱虫；以室内生活为主的猫仍需关注跳蚤与绦虫。心丝虫预防前通常需要检测，已感染动物直接用药存在风险。',NULL,'2026-11-01 20:25:03'),(44,19,2,'建议在宠物档案中记录每次驱虫的日期、产品名、剂量与体重，便于兽医评估效果与不良反应。孕哺期、患病期或正在使用其他药物的动物，驱虫方案需先咨询兽医，不可自行加量或叠加多种产品。',NULL,'2026-11-01 20:25:03');
UNLOCK TABLES;

LOCK TABLES `sensitive_words` WRITE;
INSERT INTO `sensitive_words` (`id`, `word`, `category`, `severity`, `enabled`, `created_at`, `updated_at`) VALUES (1,'出售保护动物','illegal_trade','BLOCK',1,'2026-10-26 21:48:46','2026-10-26 21:48:46'),(2,'代办证件','fraud','BLOCK',1,'2026-10-26 21:48:46','2026-10-26 21:48:46'),(3,'代开处方药','medical','BLOCK',1,'2026-10-26 21:48:46','2026-10-26 21:48:46'),(4,'加微信返现','advertising','REVIEW',1,'2026-10-26 21:48:46','2026-10-26 21:48:46'),(5,'扫码领红包','advertising','REVIEW',1,'2026-10-26 21:48:46','2026-10-26 21:48:46');
UNLOCK TABLES;

SET FOREIGN_KEY_CHECKS = 1;
