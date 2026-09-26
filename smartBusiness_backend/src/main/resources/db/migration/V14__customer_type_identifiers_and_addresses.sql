-- Split the single "fiscal_id" into a company tax number and an individual national id
-- (plus date of birth), add the VAT-suspension permit, and turn the flat address into
-- a billing address alongside a separate shipping address.

ALTER TABLE customers ADD COLUMN tax_id                VARCHAR(30);
ALTER TABLE customers ADD COLUMN national_id           VARCHAR(30);
ALTER TABLE customers ADD COLUMN birth_date            DATE;
ALTER TABLE customers ADD COLUMN vat_suspension_number VARCHAR(60);

ALTER TABLE customers ADD COLUMN billing_street        VARCHAR(255);
ALTER TABLE customers ADD COLUMN billing_city          VARCHAR(100);
ALTER TABLE customers ADD COLUMN billing_region        VARCHAR(100);
ALTER TABLE customers ADD COLUMN billing_postal_code   VARCHAR(20);
ALTER TABLE customers ADD COLUMN billing_country       VARCHAR(60);

ALTER TABLE customers ADD COLUMN shipping_street       VARCHAR(255);
ALTER TABLE customers ADD COLUMN shipping_city         VARCHAR(100);
ALTER TABLE customers ADD COLUMN shipping_region       VARCHAR(100);
ALTER TABLE customers ADD COLUMN shipping_postal_code  VARCHAR(20);
ALTER TABLE customers ADD COLUMN shipping_country      VARCHAR(60);

-- Carry over any data captured under the first schema.
UPDATE customers SET tax_id = fiscal_id      WHERE type = 'COMPANY'    AND fiscal_id IS NOT NULL;
UPDATE customers SET national_id = fiscal_id WHERE type = 'INDIVIDUAL' AND fiscal_id IS NOT NULL;
UPDATE customers
   SET billing_street      = address,
       billing_city        = city,
       billing_postal_code = postal_code
 WHERE address IS NOT NULL OR city IS NOT NULL OR postal_code IS NOT NULL;

ALTER TABLE customers DROP COLUMN fiscal_id;
ALTER TABLE customers DROP COLUMN address;
ALTER TABLE customers DROP COLUMN city;
ALTER TABLE customers DROP COLUMN postal_code;
