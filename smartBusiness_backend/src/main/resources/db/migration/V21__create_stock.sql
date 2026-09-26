-- Stock: warehouses and an append-only register of movements (Finco analysis §08).
-- A product's stock is never a counter: it is the sum of its movements, so every level can
-- be explained and a cancelled order simply writes the opposite movement.

CREATE TABLE warehouses (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    address      VARCHAR(255),
    -- The warehouse documents use when they do not name one (an order reserves there).
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_warehouses_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE INDEX idx_warehouses_company ON warehouses (company_id);
CREATE UNIQUE INDEX uk_warehouses_company_name_ci ON warehouses (company_id, LOWER(name));
-- At most one default warehouse per company
CREATE UNIQUE INDEX uk_warehouses_company_default ON warehouses (company_id) WHERE is_default;

-- Every existing company gets its default warehouse (new ones get it at registration).
INSERT INTO warehouses (company_id, name, is_default, active, created_at, updated_at)
SELECT id, 'Default warehouse', TRUE, TRUE, NOW(), NOW()
FROM companies;

CREATE TABLE stock_movements (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    product_id   BIGINT         NOT NULL,
    warehouse_id BIGINT         NOT NULL,
    -- ENTRY, EXIT, ADJUSTMENT, TRANSFER_IN, TRANSFER_OUT move the physical stock;
    -- RESERVE, RELEASE move what is promised to customers.
    type         VARCHAR(20)    NOT NULL,
    -- Signed: positive adds, negative removes, so a level is a plain SUM.
    quantity     NUMERIC(14, 3) NOT NULL,
    reason       VARCHAR(255),
    -- The document that caused it, when one did (null for a manual movement).
    source_type  VARCHAR(30),
    source_id    BIGINT,
    occurred_at  TIMESTAMP      NOT NULL,
    created_at   TIMESTAMP      NOT NULL,
    updated_at   TIMESTAMP      NOT NULL,
    version      BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_stock_movements_company   FOREIGN KEY (company_id)   REFERENCES companies (id),
    CONSTRAINT fk_stock_movements_product   FOREIGN KEY (product_id)   REFERENCES products (id),
    CONSTRAINT fk_stock_movements_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
);

CREATE INDEX idx_stock_movements_company_product ON stock_movements (company_id, product_id);
CREATE INDEX idx_stock_movements_warehouse       ON stock_movements (warehouse_id);
CREATE INDEX idx_stock_movements_source          ON stock_movements (source_type, source_id);

-- Minimum stock before a product is flagged "low" — one figure for the whole company.
ALTER TABLE products ADD COLUMN min_stock NUMERIC(12, 3);
