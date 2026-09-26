-- Credit notes reuse the generic sales document (V20): a new `type` value, no new table. A credit note
-- is made from one invoice and lowers what that invoice asks for. The invoice keeps the sum of its live
-- credit notes here — rewritten from them each time one is issued or cancelled, never incremented.

ALTER TABLE sales_documents ADD COLUMN credited_amount NUMERIC(14, 3) NOT NULL DEFAULT 0;
