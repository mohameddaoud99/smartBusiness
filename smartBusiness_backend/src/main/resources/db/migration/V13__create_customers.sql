-- People and businesses the company sells to.
-- The type-specific identifiers and the billing / shipping addresses are added by V14.
CREATE TABLE customers (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    type         VARCHAR(20)  NOT NULL,
    reference    VARCHAR(20)  NOT NULL,
    name         VARCHAR(150) NOT NULL,
    contact_name VARCHAR(120),
    email        VARCHAR(150),
    phone        VARCHAR(30),
    fiscal_id    VARCHAR(30),
    address      VARCHAR(255),
    city         VARCHAR(100),
    postal_code  VARCHAR(20),
    notes        TEXT,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,

    CONSTRAINT fk_customers_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT uk_customers_company_reference UNIQUE (company_id, reference)
);

CREATE INDEX idx_customers_company ON customers (company_id);
