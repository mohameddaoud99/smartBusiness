-- Taxes a company can put on its documents. The standard Tunisian set is seeded for
-- every company by TaxService (at registration and, for older companies, at startup),
-- so this migration only creates the table.
CREATE TABLE taxes (
    id                    BIGSERIAL PRIMARY KEY,
    company_id            BIGINT      NOT NULL,
    name                  VARCHAR(60) NOT NULL,
    kind                  VARCHAR(30) NOT NULL,
    rate                  NUMERIC(6, 3),
    amount                NUMERIC(12, 3),
    included_in_vat_base  BOOLEAN     NOT NULL DEFAULT FALSE,
    active_by_default     BOOLEAN     NOT NULL DEFAULT FALSE,
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    is_system             BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP   NOT NULL,
    updated_at            TIMESTAMP   NOT NULL,

    CONSTRAINT fk_taxes_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT uk_taxes_company_name UNIQUE (company_id, name)
);

CREATE INDEX idx_taxes_company ON taxes (company_id);
