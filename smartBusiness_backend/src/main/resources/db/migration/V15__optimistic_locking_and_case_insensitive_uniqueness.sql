-- 1. Optimistic locking — every table behind BaseEntity gets the @Version column.
--    Two concurrent writes on the same row: the second one fails instead of silently
--    overwriting the first.
ALTER TABLE companies             ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users                 ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE branches              ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE roles                 ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE platform_admins       ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE company_bank_accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE taxes                 ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE numbering_sequences   ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE customers             ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 2. Uniqueness the way the services check it: case-insensitive.
--    The services refuse "ALPHA" when "alpha" exists, but the old constraints did not —
--    two concurrent requests could both pass the check. The database is now the real
--    guard; the service check only gives the friendlier message.
ALTER TABLE users DROP CONSTRAINT uk_users_email;
CREATE UNIQUE INDEX uk_users_email_ci ON users (LOWER(email));

ALTER TABLE users DROP CONSTRAINT uk_users_company_username;
CREATE UNIQUE INDEX uk_users_company_username_ci ON users (company_id, LOWER(username));

ALTER TABLE platform_admins DROP CONSTRAINT uk_platform_admins_email;
CREATE UNIQUE INDEX uk_platform_admins_email_ci ON platform_admins (LOWER(email));

ALTER TABLE branches DROP CONSTRAINT uk_branches_company_code;
CREATE UNIQUE INDEX uk_branches_company_code_ci ON branches (company_id, LOWER(code));

ALTER TABLE roles DROP CONSTRAINT uk_roles_company_name;
CREATE UNIQUE INDEX uk_roles_company_name_ci ON roles (company_id, LOWER(name));

ALTER TABLE taxes DROP CONSTRAINT uk_taxes_company_name;
CREATE UNIQUE INDEX uk_taxes_company_name_ci ON taxes (company_id, LOWER(name));

ALTER TABLE customers DROP CONSTRAINT uk_customers_company_reference;
CREATE UNIQUE INDEX uk_customers_company_reference_ci ON customers (company_id, LOWER(reference));

-- 3. Customer references now come from the numbering settings, whose prefix and year
--    can make a code longer than the old 20 characters.
ALTER TABLE customers ALTER COLUMN reference TYPE VARCHAR(30);

-- 4. The CUSTOMER sequence for existing companies, carrying on from the customers they
--    already have ("C-0001" style: prefix C, 4 digits, no year).
INSERT INTO numbering_sequences
       (company_id, document_type, prefix, padding, include_year, next_value, active,
        version, created_at, updated_at)
SELECT c.id, 'CUSTOMER', 'C', 4, FALSE,
       (SELECT COUNT(*) FROM customers cu WHERE cu.company_id = c.id) + 1,
       TRUE, 0, NOW(), NOW()
FROM companies c
WHERE NOT EXISTS (SELECT 1 FROM numbering_sequences s
                  WHERE s.company_id = c.id AND s.document_type = 'CUSTOMER');
