CREATE TABLE inventory_movement (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  product_sku_id BIGINT UNSIGNED NOT NULL,
  movement_type VARCHAR(20) NOT NULL,
  quantity_change DECIMAL(18,4) NOT NULL,
  before_quantity DECIMAL(18,4) NOT NULL,
  after_quantity DECIMAL(18,4) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_inventory_movement_product_time (product_sku_id, created_at),
  CONSTRAINT fk_inventory_movement_product FOREIGN KEY (product_sku_id) REFERENCES product_sku(id),
  CONSTRAINT fk_inventory_movement_user FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT ck_inventory_movement_type CHECK (movement_type IN ('INBOUND','OUTBOUND','ADJUSTMENT')),
  CONSTRAINT ck_inventory_movement_change CHECK (quantity_change <> 0),
  CONSTRAINT ck_inventory_movement_quantity CHECK (before_quantity >= 0 AND after_quantity >= 0)
) ENGINE=InnoDB;
