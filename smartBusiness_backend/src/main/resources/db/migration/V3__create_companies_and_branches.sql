CREATE TABLE companies (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    email      VARCHAR(150),
    phone      VARCHAR(30),
    address    VARCHAR(255),
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE TABLE branches (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL,
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(120) NOT NULL,
    address    VARCHAR(255),
    phone      VARCHAR(30),
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,

    CONSTRAINT fk_branches_company FOREIGN KEY (company_id) REFERENCES companies (id),
    -- A branch code only has to be unique inside its own company
    CONSTRAINT uk_branches_company_code UNIQUE (company_id, code)
);

CREATE INDEX idx_branches_company ON branches (company_id);
