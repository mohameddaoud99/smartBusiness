-- Every user now belongs to a company. The accounts created before this migration
-- were test accounts with no company, and company_id cannot be NULL — they are removed.
DELETE FROM user_history;
DELETE FROM users;

-- The single `role` column is replaced by the user_roles association
DROP INDEX idx_users_role;
ALTER TABLE users DROP COLUMN role;

ALTER TABLE users ADD COLUMN company_id BIGINT NOT NULL;
ALTER TABLE users ADD CONSTRAINT fk_users_company
    FOREIGN KEY (company_id) REFERENCES companies (id);

CREATE INDEX idx_users_company ON users (company_id);

-- Email is the login identifier and stays globally unique.
-- Username is only meaningful inside a company, so two companies may both have "admin".
ALTER TABLE users DROP CONSTRAINT uk_users_username;
ALTER TABLE users ADD CONSTRAINT uk_users_company_username UNIQUE (company_id, username);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

-- Supports "which users hold this role?" when a role is edited or deleted
CREATE INDEX idx_user_roles_role ON user_roles (role_id);
