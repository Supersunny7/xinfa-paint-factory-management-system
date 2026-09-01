ALTER TABLE inventory_movement
  ADD COLUMN reference_type VARCHAR(32) NULL AFTER reason,
  ADD COLUMN reference_id BIGINT UNSIGNED NULL AFTER reference_type,
  ADD COLUMN reference_no VARCHAR(64) NULL AFTER reference_id,
  ADD UNIQUE KEY uk_inventory_movement_reference (product_sku_id, reference_type, reference_id),
  ADD KEY idx_inventory_movement_reference_no (reference_type, reference_no);
