-- Administrative profile fields the company admin fills in from Settings.
-- Logo and stamp live on disk (see smartcommerce.uploads.dir); only their relative
-- path and content type are kept here.
ALTER TABLE companies ADD COLUMN tax_id             VARCHAR(30);
ALTER TABLE companies ADD COLUMN currency           VARCHAR(3) NOT NULL DEFAULT 'TND';
ALTER TABLE companies ADD COLUMN postal_code        VARCHAR(20);
ALTER TABLE companies ADD COLUMN city                VARCHAR(100);

ALTER TABLE companies ADD COLUMN logo_path          VARCHAR(255);
ALTER TABLE companies ADD COLUMN logo_content_type  VARCHAR(100);
ALTER TABLE companies ADD COLUMN stamp_path         VARCHAR(255);
ALTER TABLE companies ADD COLUMN stamp_content_type VARCHAR(100);
