-- One numbering sequence per document type per company. NumberingService seeds the
-- set (at registration, and at startup for older companies), so this migration only
-- creates the table.
CREATE TABLE numbering_sequences (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT      NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    prefix        VARCHAR(10) NOT NULL,
    padding       INTEGER     NOT NULL DEFAULT 5,
    include_year  BOOLEAN     NOT NULL DEFAULT TRUE,
    next_value    BIGINT      NOT NULL DEFAULT 1,
    year_of_last  INTEGER,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP   NOT NULL,

    CONSTRAINT fk_numbering_sequences_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT uk_numbering_sequences_company_type UNIQUE (company_id, document_type)
);

CREATE INDEX idx_numbering_sequences_company ON numbering_sequences (company_id);
