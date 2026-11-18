-- SilentEcho schema for MySQL 8.0+ (utf8mb4).
-- Column names match the JPA entities. Run this once against an empty silentecho_db, or let
-- Hibernate create the tables with SILENTECHO_DDL_AUTO=update. Use `validate` in production
-- after applying this file. The legacy Firebase-era tables (users, reports) are not migrated.
--
--   mysql -u root -p < db/schema.sql

CREATE DATABASE IF NOT EXISTS silentecho_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE silentecho_db;

-- Accounts are identified only by a keyed HMAC-SHA-256 digest of the private sign-in ID.
-- No name, email, phone number or raw sign-in ID is stored.
CREATE TABLE IF NOT EXISTS accounts (
    id                 VARCHAR(36)  NOT NULL,
    account_type       VARCHAR(20)  NOT NULL,            -- PERSONAL | ORGANISATION
    sign_in_id_digest  VARCHAR(64)  NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    last_login_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_accounts_digest (sign_in_id_digest)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS organisations (
    id                 VARCHAR(36)  NOT NULL,
    name               VARCHAR(120) NOT NULL,
    name_key           VARCHAR(120) NOT NULL,
    organisation_code VARCHAR(20)  NOT NULL,
    type               VARCHAR(40)  NOT NULL,            -- EDUCATIONAL_INSTITUTION | COMPANY | NON_PROFIT | COMMUNITY | OTHER
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_organisations_name_key (name_key),
    UNIQUE KEY uk_organisations_code (organisation_code)
) ENGINE=InnoDB;

-- One-time recovery secret for an organisation administrator. Only its HMAC digest is stored.
CREATE TABLE IF NOT EXISTS organisation_recovery_codes (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    organisation_id  VARCHAR(36)  NOT NULL,
    code_digest      VARCHAR(64)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    used_at          DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_recovery_digest (code_digest),
    KEY idx_org_recovery_org (organisation_id),
    CONSTRAINT fk_org_recovery_org FOREIGN KEY (organisation_id) REFERENCES organisations (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS memberships (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    account_id       VARCHAR(36)  NOT NULL,
    organisation_id  VARCHAR(36)  NOT NULL,
    role             VARCHAR(20)  NOT NULL,               -- ADMIN | RESPONDER
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_membership_account_org (account_id, organisation_id),
    CONSTRAINT fk_membership_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_membership_org FOREIGN KEY (organisation_id) REFERENCES organisations (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Single-use invitation codes. Only the keyed digest of each code is stored.
CREATE TABLE IF NOT EXISTS invitations (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    organisation_id        VARCHAR(36)  NOT NULL,
    code_digest            VARCHAR(64)  NOT NULL,
    created_by_account_id  VARCHAR(36)  NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    expires_at             DATETIME(6)  NOT NULL,
    used_at                DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invitations_digest (code_digest),
    CONSTRAINT fk_invitation_org FOREIGN KEY (organisation_id) REFERENCES organisations (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- A case. case_id is a random UUID reference, never a credential. Title and description are
-- AES-256-GCM ciphertext. reporter_account_id is internal and never returned to organisations.
CREATE TABLE IF NOT EXISTS complaints (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    case_id                 VARCHAR(36)  NOT NULL,
    reporter_account_id     VARCHAR(36)  NOT NULL,
    organisation_id         VARCHAR(36)  NOT NULL,
    category                VARCHAR(60)  NOT NULL,
    urgency                 VARCHAR(20)  NOT NULL,       -- LOW | MEDIUM | HIGH | URGENT
    status                  VARCHAR(30)  NOT NULL,       -- SUBMITTED | UNDER_REVIEW | IN_PROGRESS | RESOLVED | CLOSED
    title_ciphertext        TEXT         NOT NULL,
    description_ciphertext  TEXT         NOT NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_complaints_case_id (case_id),
    KEY idx_complaints_reporter (reporter_account_id),
    KEY idx_complaints_org (organisation_id),
    CONSTRAINT fk_complaint_org FOREIGN KEY (organisation_id) REFERENCES organisations (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS complaint_events (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    complaint_id     BIGINT       NOT NULL,
    event_type       VARCHAR(30)  NOT NULL,
    note_ciphertext  TEXT         NULL,
    status           VARCHAR(30)  NULL,
    urgency          VARCHAR(20)  NULL,
    category         VARCHAR(60)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_events_complaint (complaint_id, created_at),
    CONSTRAINT fk_event_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS complaint_messages (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    complaint_id      BIGINT       NOT NULL,
    sender            VARCHAR(20)  NOT NULL,              -- REPORTER | ORGANISATION
    author_account_id VARCHAR(36)  NULL,                  -- internal audit only; never returned to the reporter
    body_ciphertext   TEXT         NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_messages_complaint (complaint_id, created_at),
    CONSTRAINT fk_message_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Metadata only. The file bytes are AES-GCM encrypted on disk outside the web root.
CREATE TABLE IF NOT EXISTS evidence_files (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    public_id           VARCHAR(36)  NOT NULL,
    complaint_id        BIGINT       NOT NULL,
    filename_ciphertext TEXT         NOT NULL,
    content_type        VARCHAR(60)  NOT NULL,
    size_bytes          BIGINT       NOT NULL,
    storage_key         VARCHAR(36)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_evidence_public_id (public_id),
    UNIQUE KEY uk_evidence_storage_key (storage_key),
    KEY idx_evidence_complaint (complaint_id),
    CONSTRAINT fk_evidence_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Accountability trail. Records identifiers and action names only, never content, IDs, tokens or IP addresses.
CREATE TABLE IF NOT EXISTS audit_events (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    actor_account_id  VARCHAR(36)  NULL,
    organisation_id   VARCHAR(36)  NULL,
    case_id           VARCHAR(36)  NULL,
    action            VARCHAR(60)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_case (case_id),
    KEY idx_audit_actor (actor_account_id)
) ENGINE=InnoDB;
