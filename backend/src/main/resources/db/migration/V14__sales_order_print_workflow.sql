ALTER TABLE sales_order
  ADD COLUMN printed_by BIGINT UNSIGNED NULL AFTER approved_at,
  ADD COLUMN printed_at DATETIME(3) NULL AFTER printed_by,
  ADD COLUMN print_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER printed_at,
  ADD CONSTRAINT fk_sales_order_printed_by FOREIGN KEY (printed_by) REFERENCES sys_user(id),
  ADD KEY idx_sales_order_printed_at (printed_at);

CREATE TABLE sales_order_print_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  sales_order_id BIGINT UNSIGNED NOT NULL,
  print_no INT UNSIGNED NOT NULL,
  print_kind VARCHAR(20) NOT NULL,
  reason VARCHAR(500) NULL,
  printed_by BIGINT UNSIGNED NOT NULL,
  printed_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sales_order_print_no (sales_order_id, print_no),
  CONSTRAINT fk_sales_order_print_order FOREIGN KEY (sales_order_id) REFERENCES sales_order(id),
  CONSTRAINT fk_sales_order_print_user FOREIGN KEY (printed_by) REFERENCES sys_user(id),
  CONSTRAINT ck_sales_order_print_kind CHECK (print_kind IN ('FIRST_PRINT','REPRINT'))
);

ALTER TABLE inventory_movement
  DROP CHECK ck_inventory_movement_quantity;

ALTER TABLE inventory_movement
  DROP CHECK ck_inventory_movement_type,
  ADD CONSTRAINT ck_inventory_movement_type CHECK (movement_type IN ('INBOUND','OUTBOUND','ADJUSTMENT','REVERSAL','SALE_PRINT'));
