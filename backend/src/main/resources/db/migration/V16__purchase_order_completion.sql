ALTER TABLE purchase_order
  ADD COLUMN completed_by BIGINT UNSIGNED NULL AFTER void_reason,
  ADD COLUMN completed_at DATETIME(3) NULL AFTER completed_by,
  ADD COLUMN completion_reason VARCHAR(500) NULL AFTER completed_at,
  ADD KEY idx_purchase_order_completed_at (completed_at),
  ADD CONSTRAINT fk_purchase_order_completed_by FOREIGN KEY (completed_by) REFERENCES sys_user(id);
