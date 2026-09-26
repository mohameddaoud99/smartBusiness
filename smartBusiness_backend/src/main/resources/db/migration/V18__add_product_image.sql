-- Product photo, stored on disk under smartcommerce.uploads.dir/products/{productId}/image.{ext}
-- (see common.ImageStorage). Only the path and content type live here.
ALTER TABLE products ADD COLUMN image_path         VARCHAR(255);
ALTER TABLE products ADD COLUMN image_content_type VARCHAR(50);
