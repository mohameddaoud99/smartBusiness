-- Purchase invoices reuse the generic purchase document (V22): a new `type` value, no new table. What they
-- add is how much has been paid on them — rewritten from the payments each time one is recorded or
-- cancelled, never incremented — and the supplier payments themselves. The mirror of V24.

ALTER TABLE purchase_documents ADD COLUMN paid_amount NUMERIC(14, 3) NOT NULL DEFAULT 0;

-- A payment is never edited or deleted: a mistake is cancelled, and the invoice follows.
CREATE TABLE supplier_payments (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    invoice_id   BIGINT         NOT NULL,
    amount       NUMERIC(14, 3) NOT NULL,
    payment_date DATE           NOT NULL,
    method       VARCHAR(20)    NOT NULL,
    -- What identifies it: a cheque number, a transfer reference...
    reference    VARCHAR(100),
    notes        TEXT,
    status       VARCHAR(20)    NOT NULL,
    created_at   TIMESTAMP      NOT NULL,
    updated_at   TIMESTAMP      NOT NULL,
    version      BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_supplier_payments_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_supplier_payments_invoice FOREIGN KEY (invoice_id) REFERENCES purchase_documents (id),
    CONSTRAINT ck_supplier_payments_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_supplier_payments_company_invoice ON supplier_payments (company_id, invoice_id);
