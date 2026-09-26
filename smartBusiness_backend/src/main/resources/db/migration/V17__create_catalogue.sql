-- The product catalogue: families (optionally nested), brands, and products themselves.
-- Family/brand carry no reference code (only products get one, auto-generated like
-- customers and suppliers) — Finco shows no such field on either settings screen.

CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL,
    parent_id  BIGINT,
    name       VARCHAR(150) NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_categories_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_categories_parent  FOREIGN KEY (parent_id)  REFERENCES categories (id)
);

CREATE INDEX idx_categories_company ON categories (company_id);
CREATE INDEX idx_categories_parent  ON categories (parent_id);

-- Case-insensitive, like every other unique code in the app (V15).
CREATE UNIQUE INDEX uk_categories_company_name_ci ON categories (company_id, LOWER(name));

CREATE TABLE brands (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_brands_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE INDEX idx_brands_company ON brands (company_id);
CREATE UNIQUE INDEX uk_brands_company_name_ci ON brands (company_id, LOWER(name));

CREATE TABLE products (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT       NOT NULL,
    reference            VARCHAR(30)  NOT NULL,
    name                 VARCHAR(150) NOT NULL,
    description          TEXT,
    barcode              VARCHAR(60),
    kind                 VARCHAR(20)  NOT NULL,
    purpose              VARCHAR(20)  NOT NULL,
    unit                 VARCHAR(20)  NOT NULL,
    category_id          BIGINT,
    brand_id             BIGINT,
    sale_price           NUMERIC(12, 3),
    purchase_price       NUMERIC(12, 3),
    allow_negative_stock BOOLEAN      NOT NULL DEFAULT TRUE,
    notes                TEXT,
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP    NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_products_company  FOREIGN KEY (company_id)  REFERENCES companies (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_brand    FOREIGN KEY (brand_id)    REFERENCES brands (id)
);

CREATE INDEX idx_products_company  ON products (company_id);
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_brand    ON products (brand_id);
CREATE UNIQUE INDEX uk_products_company_reference_ci ON products (company_id, LOWER(reference));

-- The taxes pre-selected when a product is added to a document line (Phase 4+ snapshots
-- the actual rate on the line — this table only supplies the default).
CREATE TABLE product_taxes (
    product_id BIGINT NOT NULL,
    tax_id     BIGINT NOT NULL,

    PRIMARY KEY (product_id, tax_id),
    CONSTRAINT fk_product_taxes_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_taxes_tax     FOREIGN KEY (tax_id)     REFERENCES taxes (id)
);
