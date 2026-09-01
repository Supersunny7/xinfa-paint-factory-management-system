ALTER TABLE product_sku
  ADD COLUMN total_stock DECIMAL(18,4) NOT NULL DEFAULT 0 AFTER factory_price,
  ADD COLUMN stock_lower_limit DECIMAL(18,4) NOT NULL DEFAULT 0 AFTER total_stock,
  ADD COLUMN last_purchase_price DECIMAL(18,2) NULL AFTER stock_lower_limit;
