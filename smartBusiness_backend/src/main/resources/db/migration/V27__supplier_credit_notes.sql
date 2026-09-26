-- Supplier credit notes reuse the generic purchase document (V22): a new `type` value, no new table. A credit
-- note is made from one purchase invoice and lowers what that invoice asks us to pay. The invoice keeps the sum of
-- its live credit notes here - rewritten from them each time one is validated or cancelled, never incremented.
-- The mirror of V25.

ALTER TABLE purchase_documents ADD COLUMN credited_amount NUMERIC(14, 3) NOT NULL DEFAULT 0;
