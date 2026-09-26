-- Platform operators — outside the tenant model entirely, no company_id. This is the
-- one account type in the whole application that is allowed to see across companies.
CREATE TABLE platform_admins (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,

    CONSTRAINT uk_platform_admins_email UNIQUE (email)
);

-- Which business modules a company may use. Administrative modules (Users, Roles,
-- Branches, Company, Security) are not listed here — every company always has them;
-- only the sellable business modules go through this table.
CREATE TABLE company_modules (
    company_id BIGINT      NOT NULL,
    module     VARCHAR(20) NOT NULL,

    PRIMARY KEY (company_id, module),
    CONSTRAINT fk_company_modules_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

-- Every company that already exists keeps full access — nothing changes for them today.
INSERT INTO company_modules (company_id, module)
SELECT c.id, m.module
FROM companies c
CROSS JOIN (VALUES ('CUSTOMERS'), ('SUPPLIERS'), ('PRODUCTS'), ('SALES'), ('PURCHASES'), ('STOCK')) AS m(module);
