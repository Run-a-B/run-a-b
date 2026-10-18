-- MySQL 8.4 initial schema for a NEW, EMPTY runab_db database only.
-- Captured from the current local schema on 2026-10-19; all seven JPA entity tables.
-- Includes the 20261018_readiness.sql changes: do not apply that ALTER-only migration afterward.
-- Contains no row data, triggers, stored routines, definers, or source AUTO_INCREMENT counters.
-- Intentionally fails if tables already exist; never use it to replace an existing database.
-- Select the target database explicitly when invoking mysql; production uses ddl-auto=validate.

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `age` int DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `deleted` bit(1) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `email` varchar(100) NOT NULL,
  `password` varchar(255) DEFAULT NULL,
  `provider` enum('GOOGLE','LOCAL') NOT NULL,
  `provider_id` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `business_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `annual_revenue` bigint DEFAULT NULL,
  `business_status` bit(1) NOT NULL,
  `employee_count` int DEFAULT NULL,
  `job_category` varchar(50) NOT NULL,
  `region` varchar(50) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKsavr8xxj10qvoeg02316hjj8y` (`user_id`),
  CONSTRAINT `FKs7hlskna3muimlsosiwtbmrfx` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `email_verification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `email` varchar(100) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `verified` bit(1) NOT NULL,
  `failed_attempts` int NOT NULL DEFAULT '0',
  `receipt_hash` varchar(64) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_verification_email_id` (`email`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `policy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agency` varchar(100) DEFAULT NULL,
  `announcement_no` varchar(100) DEFAULT NULL,
  `application_end_date` date DEFAULT NULL,
  `application_method` text,
  `application_start_date` date DEFAULT NULL,
  `application_url` varchar(500) DEFAULT NULL,
  `category` varchar(50) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `department` varchar(100) DEFAULT NULL,
  `description` text,
  `detail_url` varchar(500) DEFAULT NULL,
  `external_id` varchar(100) DEFAULT NULL,
  `filter_category` varchar(50) DEFAULT NULL,
  `industry` varchar(50) DEFAULT NULL,
  `published_date` date DEFAULT NULL,
  `purpose_text` text,
  `region` varchar(50) DEFAULT NULL,
  `source_system` varchar(50) DEFAULT NULL,
  `support_scale` varchar(200) DEFAULT NULL,
  `target_group` varchar(200) DEFAULT NULL,
  `title` varchar(500) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `attachment_name` text,
  `attachment_url` text,
  `executing_agency` text,
  `inquiry_contact` text,
  `notice_file_name` text,
  `notice_file_url` text,
  `original_application_method` longtext,
  `original_application_period` text,
  `original_notice_html` longtext,
  `original_notice_text` longtext,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKd427f83dft248x6692vcrgnrf` (`external_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `policy_card` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `application_status` varchar(20) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `external_id` varchar(100) DEFAULT NULL,
  `extraction_status` varchar(30) DEFAULT NULL,
  `full_json` json DEFAULT NULL,
  `policy_id` varchar(200) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgtruyr2rx3rqqe352qsbpgbmv` (`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `policy_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `expanded_application_method` text,
  `expanded_description` text,
  `highlights_json` json DEFAULT NULL,
  `policy_id` bigint NOT NULL,
  `summary_lines_json` json DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `source_fingerprint` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKix0n8cwno6c782pr61vf2byxi` (`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `business_impact_json` json DEFAULT NULL,
  `category` varchar(50) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `details_json` json DEFAULT NULL,
  `direction` varchar(20) DEFAULT NULL,
  `impact_label` varchar(100) DEFAULT NULL,
  `impact_style` varchar(100) DEFAULT NULL,
  `policy_id` bigint NOT NULL,
  `policy_title` varchar(500) NOT NULL,
  `related_ids_json` json DEFAULT NULL,
  `summary` text,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_user_policy` (`user_id`,`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
