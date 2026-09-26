-- A product now carries up to 4 photos (limit enforced by ProductService), so the single
-- image_path / image_content_type pair of V18 becomes a child table. The photo already
-- uploaded is carried over untouched — its file stays where it is on disk.
CREATE TABLE product_images (
    id           BIGSERIAL PRIMARY KEY,
    product_id   BIGINT       NOT NULL,
    path         VARCHAR(255) NOT NULL,
    content_type VARCHAR(50)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id)
        REFERENCES products (id) ON DELETE CASCADE
);

CREATE INDEX idx_product_images_product ON product_images (product_id);

INSERT INTO product_images (product_id, path, content_type, created_at, updated_at)
SELECT id, image_path, image_content_type, NOW(), NOW()
FROM products
WHERE image_path IS NOT NULL;

ALTER TABLE products DROP COLUMN image_path;
ALTER TABLE products DROP COLUMN image_content_type;
