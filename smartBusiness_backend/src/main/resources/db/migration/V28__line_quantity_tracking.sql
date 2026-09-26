-- Quantities followed line by line. A line copied from a source document (an order line onto a delivery note,
-- a delivery note line onto a return note...) remembers the line it came from, so that what has been delivered,
-- received or returned of each line can be added up - and the "left to deliver" shown and enforced.

ALTER TABLE sales_document_lines ADD COLUMN source_line_id BIGINT;
ALTER TABLE sales_document_lines
    ADD CONSTRAINT fk_sales_document_lines_source_line FOREIGN KEY (source_line_id) REFERENCES sales_document_lines (id);
CREATE INDEX idx_sales_document_lines_source_line ON sales_document_lines (source_line_id);

ALTER TABLE purchase_document_lines ADD COLUMN source_line_id BIGINT;
ALTER TABLE purchase_document_lines
    ADD CONSTRAINT fk_purchase_document_lines_source_line FOREIGN KEY (source_line_id) REFERENCES purchase_document_lines (id);
CREATE INDEX idx_purchase_document_lines_source_line ON purchase_document_lines (source_line_id);
