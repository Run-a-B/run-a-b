-- One-time MySQL 8 migration from the schema BEFORE the 2026-10-18 readiness changes.
-- NOT an initial schema. Do not run if local Hibernate ddl-auto=update already added these columns.
-- Back up and test restoration first. Inspect INFORMATION_SCHEMA before applying each statement.
-- MySQL DDL commits implicitly; this is not a rollback transaction.

ALTER TABLE email_verification
    MODIFY COLUMN code VARCHAR(64) NOT NULL,
    ADD COLUMN receipt_hash VARCHAR(64) NULL,
    ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN version BIGINT NULL,
    ADD INDEX idx_verification_email_id (email, id);

ALTER TABLE policy_summary
    ADD COLUMN source_fingerprint VARCHAR(64) NULL;

-- Existing OTP rows were plaintext/without caller-held proof and cannot authorize new signup.
-- Users must request a new code. No user account or saved report is deleted.
-- Existing summaries have NULL fingerprints and regenerate on next authenticated request.
