-- Delivery notes reuse the generic sales document (V20): a new `type` value, no new table.
-- What they add is the warehouse the goods leave from, and — on the stock register — a link
-- from a reservation released by a delivery back to the delivery that consumed it, so that
-- cancelling the delivery can put the reservation back.

ALTER TABLE sales_documents ADD COLUMN warehouse_id BIGINT;
ALTER TABLE sales_documents
    ADD CONSTRAINT fk_sales_documents_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

-- The document that CAUSED a movement, when it is not the one that owns it: the delivery note
-- that released a slice of a sales order's reservation (the order owns the reservation,
-- source_id = the order; origin_id = the delivery).
ALTER TABLE stock_movements ADD COLUMN origin_id BIGINT;
CREATE INDEX idx_stock_movements_origin ON stock_movements (source_type, origin_id);
