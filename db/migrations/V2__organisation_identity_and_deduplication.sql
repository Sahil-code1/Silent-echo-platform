-- Run ONCE on an existing SilentEcho database before starting the updated application.
-- Take a database backup first. This migration merges duplicate organisation workspaces
-- using the oldest created record as canonical, and moves every case/team/invitation reference
-- to that canonical record. It preserves all administrator accounts as memberships.

USE silentecho_db;

ALTER TABLE organisations
    ADD COLUMN name_key VARCHAR(120) NULL,
    ADD COLUMN organisation_code VARCHAR(24) NULL;

UPDATE organisations
SET name_key = LOWER(REGEXP_REPLACE(TRIM(name), '[[:space:]]+', ' ')),
    organisation_code = CONCAT('SE-ORG-', UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 10)));

-- Deterministically select the oldest organisation row for each normalized name.
CREATE TEMPORARY TABLE tmp_org_ranked AS
SELECT id, name_key,
       ROW_NUMBER() OVER (PARTITION BY name_key ORDER BY created_at ASC, id ASC) AS row_num
FROM organisations;

CREATE TEMPORARY TABLE tmp_org_merge_map AS
SELECT duplicate_row.id AS duplicate_id, canonical_row.id AS canonical_id
FROM tmp_org_ranked duplicate_row
JOIN tmp_org_ranked canonical_row
  ON canonical_row.name_key = duplicate_row.name_key
 AND canonical_row.row_num = 1
WHERE duplicate_row.row_num > 1;

-- Keep the strongest membership role when the same account exists in both workspaces.
UPDATE memberships canonical_membership
JOIN (
    SELECT mapping.canonical_id, duplicate_membership.account_id,
           MAX(duplicate_membership.role = 'ADMIN') AS duplicate_has_admin
    FROM memberships duplicate_membership
    JOIN tmp_org_merge_map mapping
      ON mapping.duplicate_id = duplicate_membership.organisation_id
    GROUP BY mapping.canonical_id, duplicate_membership.account_id
) duplicate_roles
  ON duplicate_roles.canonical_id = canonical_membership.organisation_id
 AND duplicate_roles.account_id = canonical_membership.account_id
SET canonical_membership.role = CASE
    WHEN canonical_membership.role = 'ADMIN' OR duplicate_roles.duplicate_has_admin = 1
    THEN 'ADMIN' ELSE 'RESPONDER' END;

-- Remove memberships that would collide with the canonical (account_id, organisation_id) key.
DELETE duplicate_membership
FROM memberships duplicate_membership
JOIN tmp_org_merge_map mapping
  ON mapping.duplicate_id = duplicate_membership.organisation_id
JOIN memberships canonical_membership
  ON canonical_membership.account_id = duplicate_membership.account_id
 AND canonical_membership.organisation_id = mapping.canonical_id;

UPDATE memberships membership_row
JOIN tmp_org_merge_map mapping
  ON mapping.duplicate_id = membership_row.organisation_id
SET membership_row.organisation_id = mapping.canonical_id;

UPDATE complaints complaint_row
JOIN tmp_org_merge_map mapping
  ON mapping.duplicate_id = complaint_row.organisation_id
SET complaint_row.organisation_id = mapping.canonical_id;

UPDATE invitations invitation_row
JOIN tmp_org_merge_map mapping
  ON mapping.duplicate_id = invitation_row.organisation_id
SET invitation_row.organisation_id = mapping.canonical_id;

UPDATE audit_events audit_row
JOIN tmp_org_merge_map mapping
  ON mapping.duplicate_id = audit_row.organisation_id
SET audit_row.organisation_id = mapping.canonical_id;

DELETE duplicate_organisation
FROM organisations duplicate_organisation
JOIN tmp_org_merge_map mapping
  ON mapping.duplicate_id = duplicate_organisation.id;

DROP TEMPORARY TABLE tmp_org_merge_map;
DROP TEMPORARY TABLE tmp_org_ranked;

ALTER TABLE organisations
    MODIFY name_key VARCHAR(120) NOT NULL,
    MODIFY organisation_code VARCHAR(20) NOT NULL,
    ADD UNIQUE KEY uk_organisations_name_key (name_key),
    ADD UNIQUE KEY uk_organisations_code (organisation_code);

-- One-time admin recovery secrets. The raw recovery code is shown only at creation/recovery;
-- only the keyed digest is stored here.
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

-- Existing administrators keep their account Sign-in IDs. The new recovery code table
-- is empty for existing workspaces; see README for how the first upgraded admin can get
-- a recovery code. New organisation registrations receive one automatically.
-- After migration, verify there is exactly one row per normalized organisation name:
-- SELECT name, name_key, organisation_code FROM organisations ORDER BY name;
