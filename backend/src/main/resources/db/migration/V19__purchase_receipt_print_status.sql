ALTER TABLE purchase_receipt
  ADD COLUMN printed_by BIGINT UNSIGNED NULL AFTER approved_at,
  ADD COLUMN printed_at DATETIME(3) NULL AFTER printed_by,
  ADD COLUMN print_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER printed_at,
  ADD CONSTRAINT fk_purchase_receipt_printed_by FOREIGN KEY (printed_by) REFERENCES sys_user(id),
  ADD KEY idx_purchase_receipt_printed_at (printed_at);
