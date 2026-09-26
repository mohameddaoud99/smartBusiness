-- Roles always belong to a company, including the system ones: every company gets
-- its own copy at registration. That keeps a single isolation rule (company_id NOT NULL)
-- with no special case for shared rows.
CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT       NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    label       VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,

    CONSTRAINT fk_roles_company    FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT uk_roles_company_name UNIQUE (company_id, name)
);

CREATE INDEX idx_roles_company ON roles (company_id);

-- No permissions table: a permission is a value of the Permission enum and this
-- table stores its name. Adding a module means adding enum values, nothing else.
CREATE TABLE role_permissions (
    role_id    BIGINT      NOT NULL,
    permission VARCHAR(40) NOT NULL,

    PRIMARY KEY (role_id, permission),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id)
        REFERENCES roles (id) ON DELETE CASCADE
);
