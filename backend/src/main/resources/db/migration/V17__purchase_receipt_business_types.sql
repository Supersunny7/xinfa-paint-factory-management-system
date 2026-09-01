ALTER TABLE purchase_receipt_item
  DROP INDEX uk_purchase_receipt_sku,
  ADD COLUMN business_type VARCHAR(32) NOT NULL DEFAULT 'HISTORICAL_UNCLASSIFIED' AFTER purchase_order_item_id,
  ADD KEY idx_purchase_receipt_item_order_item (purchase_order_item_id),
  ADD KEY idx_purchase_receipt_item_business_type (business_type),
  ADD CONSTRAINT ck_purchase_receipt_item_business_type CHECK (
    business_type IN ('ORDER_RECEIPT','ORDER_RETURN','UNLINKED_RETURN','HISTORICAL_UNCLASSIFIED')
  );

ALTER TABLE inventory_movement
  DROP INDEX uk_inventory_movement_reference,
  ADD COLUMN reference_line_id BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER reference_id,
  ADD UNIQUE KEY uk_inventory_movement_reference_line (
    product_sku_id, reference_type, reference_id, reference_line_id
  ),
  DROP CHECK ck_inventory_movement_type,
  ADD CONSTRAINT ck_inventory_movement_type CHECK (
    movement_type IN (
      'INBOUND','OUTBOUND','ADJUSTMENT','REVERSAL','SALE_PRINT',
      'PURCHASE_RECEIPT','PURCHASE_RETURN'
    )
  );
