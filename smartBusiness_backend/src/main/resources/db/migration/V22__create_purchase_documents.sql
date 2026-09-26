-- Purchase documents: the mirror of the sales documents (V20) — one generic table whose `type`
-- says what it is (purchase order, goods receipt; the supplier invoice joins later). Lines and
-- tax rows are snapshots, exactly like the sales ones.

CREATE TABLE purchase_documents (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    type         VARCHAR(30)    NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    -- Null while the document is a draft: the number is allocated at validation, so a
    -- discarded draft leaves no gap in the sequence.
    reference    VARCHAR(50),
    supplier_id  BIGINT         NOT NULL,
    -- Where a goods receipt puts its stock. Null for a purchase order.
    warehouse_id BIGINT,
    -- The document this one was made from (a goods receipt created from a purchase order).
    source_id    BIGINT,
    issue_date   DATE           NOT NULL,
    due_date     DATE,
    subtotal     NUMERIC(14, 3) NOT NULL DEFAULT 0,
    total        NUMERIC(14, 3) NOT NULL DEFAULT 0,
    notes        TEXT,
    terms        TEXT,
    created_at   TIMESTAMP      NOT NULL,
    updated_at   TIMESTAMP      NOT NULL,
    version      BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_purchase_documents_company   FOREIGN KEY (company_id)   REFERENCES companies (id),
    CONSTRAINT fk_purchase_documents_supplier  FOREIGN KEY (supplier_id)  REFERENCES suppliers (id),
    CONSTRAINT fk_purchase_documents_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_purchase_documents_source    FOREIGN KEY (source_id)    REFERENCES purchase_documents (id)
);

CREATE INDEX idx_purchase_documents_company_type ON purchase_documents (company_id, type);
CREATE INDEX idx_purchase_documents_supplier     ON purchase_documents (supplier_id);
CREATE INDEX idx_purchase_documents_source       ON purchase_documents (source_id);

-- Case-insensitive, like every other unique code in the app (V15). Drafts have no reference
-- yet and NULLs never collide in a unique index.
CREATE UNIQUE INDEX uk_purchase_documents_company_type_reference_ci
    ON purchase_documents (company_id, type, LOWER(reference));

CREATE TABLE purchase_document_lines (
    id            BIGSERIAL PRIMARY KEY,
    document_id   BIGINT         NOT NULL,
    position      INT            NOT NULL,
    -- Null for a free line typed by hand. A product cannot be deleted while a line points at it.
    product_id    BIGINT,
    reference     VARCHAR(30),
    designation   VARCHAR(255)   NOT NULL,
    quantity      NUMERIC(12, 3) NOT NULL,
    unit_price    NUMERIC(12, 3) NOT NULL,
    discount_rate NUMERIC(6, 3)  NOT NULL DEFAULT 0,
    -- The VAT rate as it was on the day, plus the tax it was picked from (to pre-select it when editing).
    vat_tax_id    BIGINT,
    vat_rate      NUMERIC(6, 3)  NOT NULL DEFAULT 0,
    line_total    NUMERIC(14, 3) NOT NULL,
    created_at    TIMESTAMP      NOT NULL,
    updated_at    TIMESTAMP      NOT NULL,
    version       BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_purchase_document_lines_document FOREIGN KEY (document_id)
        REFERENCES purchase_documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_document_lines_product  FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_purchase_document_lines_document ON purchase_document_lines (document_id);
CREATE INDEX idx_purchase_document_lines_product  ON purchase_document_lines (product_id);

CREATE TABLE purchase_document_taxes (
    id                   BIGSERIAL PRIMARY KEY,
    document_id          BIGINT         NOT NULL,
    position             INT            NOT NULL,
    -- The company tax this row came from; null for a VAT row (derived from the lines).
    tax_id               BIGINT,
    kind                 VARCHAR(30)    NOT NULL,
    name                 VARCHAR(60)    NOT NULL,
    rate                 NUMERIC(6, 3),
    included_in_vat_base BOOLEAN        NOT NULL DEFAULT FALSE,
    base                 NUMERIC(14, 3),
    amount               NUMERIC(14, 3) NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    updated_at           TIMESTAMP      NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_purchase_document_taxes_document FOREIGN KEY (document_id)
        REFERENCES purchase_documents (id) ON DELETE CASCADE
);

CREATE INDEX idx_purchase_document_taxes_document ON purchase_document_taxes (document_id);
