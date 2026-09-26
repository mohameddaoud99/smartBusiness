-- The company's own bank accounts — printed in the footer of sales documents and used
-- to record incoming payments. A customer's or supplier's account is a separate table.
CREATE TABLE company_bank_accounts (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT       NOT NULL,
    label             VARCHAR(120) NOT NULL,
    bank_name         VARCHAR(120),
    rib               VARCHAR(34)  NOT NULL,
    currency          VARCHAR(3)   NOT NULL,
    show_on_documents BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,

    CONSTRAINT fk_company_bank_accounts_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE INDEX idx_company_bank_accounts_company ON company_bank_accounts (company_id);
