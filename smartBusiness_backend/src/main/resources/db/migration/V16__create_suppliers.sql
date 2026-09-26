-- People and businesses the company buys from. Mirrors customers (V13+V14+V15 shape):
-- type-driven identifiers, billing/shipping address, @Version, case-insensitive unique
-- reference. No VAT-suspension permit — that only applies to a customer buying in
-- franchise, never to a supplier.
CREATE TABLE suppliers (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT       NOT NULL,
    type                 VARCHAR(20)  NOT NULL,
    reference            VARCHAR(30)  NOT NULL,
    name                 VARCHAR(150) NOT NULL,
    contact_name         VARCHAR(120),
    email                VARCHAR(150),
    phone                VARCHAR(30),
    tax_id               VARCHAR(30),
    national_id          VARCHAR(30),
    birth_date           DATE,
    billing_street       VARCHAR(255),
    billing_city         VARCHAR(100),
    billing_region       VARCHAR(100),
    billing_postal_code  VARCHAR(20),
    billing_country      VARCHAR(60),
    shipping_street      VARCHAR(255),
    shipping_city        VARCHAR(100),
    shipping_region      VARCHAR(100),
    shipping_postal_code VARCHAR(20),
    shipping_country     VARCHAR(60),
    notes                TEXT,
    created_at           TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP NOT NULL,
    version              BIGINT    NOT NULL DEFAULT 0,

    CONSTRAINT fk_suppliers_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE INDEX idx_suppliers_company ON suppliers (company_id);

-- Case-insensitive, like every other unique code in the app (V15) — the service check
-- gives the friendly message, this index is the real guard under concurrency.
CREATE UNIQUE INDEX uk_suppliers_company_reference_ci ON suppliers (company_id, LOWER(reference));
