ALTER TABLE purchase_receipt_item
  ADD COLUMN reference_price DECIMAL(18,2) NULL AFTER unit_price;
