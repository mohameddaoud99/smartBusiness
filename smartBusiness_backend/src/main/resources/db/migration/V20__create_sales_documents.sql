-- Sales documents: one generic document table whose `type` says what it is (quote,
-- sales order — invoice and credit note join later without a new table), as in the
-- Finco analysis (§15). Lines and tax rows are snapshots: they keep the designation,
-- price and rates as they were when the document was written.

CREATE TABLE sales_documents (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT         NOT NULL,
    type        VARCHAR(30)    NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    -- Null while the document is a draft: the number is allocated when it is issued,
    -- so a discarded draft leaves no gap in the sequence.
    reference   VARCHAR(50),
    customer_id BIGINT         NOT NULL,
    -- The document this one was made from (a sales order created from a quote).
    source_id   BIGINT,
    issue_date  DATE           NOT NULL,
    due_date    DATE,
    subtotal    NUMERIC(14, 3) NOT NULL DEFAULT 0,
    total       NUMERIC(14, 3) NOT NULL DEFAULT 0,
    notes       TEXT,
    terms       TEXT,
    created_at  TIMESTAMP      NOT NULL,
    updated_at  TIMESTAMP      NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_sales_documents_company  FOREIGN KEY (company_id)  REFERENCES companies (id),
    CONSTRAINT fk_sales_documents_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_sales_documents_source   FOREIGN KEY (source_id)   REFERENCES sales_documents (id)
);

CREATE INDEX idx_sales_documents_company_type ON sales_documents (company_id, type);
CREATE INDEX idx_sales_documents_customer     ON sales_documents (customer_id);
CREATE INDEX idx_sales_documents_source       ON sales_documents (source_id);

-- Case-insensitive, like every other unique code in the app (V15). Drafts have no
-- reference yet and NULLs never collide in a unique index.
CREATE UNIQUE INDEX uk_sales_documents_company_type_reference_ci
    ON sales_documents (company_id, type, LOWER(reference));

CREATE TABLE sales_document_lines (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT         NOT NULL,
    position    INT            NOT NULL,
    -- Null for a free line typed by hand. A product cannot be deleted while a line points at it.
    product_id  BIGINT,
    reference   VARCHAR(30),
    designation VARCHAR(255)   NOT NULL,
    quantity    NUMERIC(12, 3) NOT NULL,
    unit_price  NUMERIC(12, 3) NOT NULL,
    discount_rate NUMERIC(6, 3) NOT NULL DEFAULT 0,
    -- The VAT rate as it was on the day, plus the tax it was picked from (to pre-select it when editing).
    vat_tax_id  BIGINT,
    vat_rate    NUMERIC(6, 3)  NOT NULL DEFAULT 0,
    line_total  NUMERIC(14, 3) NOT NULL,
    created_at  TIMESTAMP      NOT NULL,
    updated_at  TIMESTAMP      NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_sales_document_lines_document FOREIGN KEY (document_id)
        REFERENCES sales_documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_sales_document_lines_product  FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_sales_document_lines_document ON sales_document_lines (document_id);
CREATE INDEX idx_sales_document_lines_product  ON sales_document_lines (product_id);

-- The breakdown printed under the lines: surcharges (FODEC), one VAT row per rate, flat
-- charges (stamp duty). Rebuilt on every save of a draft, frozen once issued.
CREATE TABLE sales_document_taxes (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT         NOT NULL,
    position    INT            NOT NULL,
    -- The company tax this row came from; null for a VAT row (derived from the lines).
    tax_id      BIGINT,
    kind        VARCHAR(30)    NOT NULL,
    name        VARCHAR(60)    NOT NULL,
    rate        NUMERIC(6, 3),
    included_in_vat_base BOOLEAN NOT NULL DEFAULT FALSE,
    base        NUMERIC(14, 3),
    amount      NUMERIC(14, 3) NOT NULL,
    created_at  TIMESTAMP      NOT NULL,
    updated_at  TIMESTAMP      NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_sales_document_taxes_document FOREIGN KEY (document_id)
        REFERENCES sales_documents (id) ON DELETE CASCADE
);

CREATE INDEX idx_sales_document_taxes_document ON sales_document_taxes (document_id);
