CREATE TABLE sales_return (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  return_no VARCHAR(32) NOT NULL,
  return_date DATE NOT NULL,
  source_sales_order_id BIGINT UNSIGNED NULL,
  customer_id BIGINT UNSIGNED NOT NULL,
  customer_code_snapshot VARCHAR(64) NOT NULL,
  customer_name_snapshot VARCHAR(300) NOT NULL,
  salesperson_id BIGINT UNSIGNED NULL,
  salesperson_name_snapshot VARCHAR(100) NULL,
  settlement_method VARCHAR(32) NOT NULL DEFAULT 'Cash',
  total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  remark VARCHAR(1000) NULL,
  printed_by BIGINT UNSIGNED NULL,
  printed_at DATETIME(3) NULL,
  print_count INT UNSIGNED NOT NULL DEFAULT 0,
  approved_by BIGINT UNSIGNED NULL,
  approved_at DATETIME(3) NULL,
  voided_by BIGINT UNSIGNED NULL,
  voided_at DATETIME(3) NULL,
  void_reason VARCHAR(500) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sales_return_no (return_no),
  KEY idx_sales_return_date_status (return_date, status),
  KEY idx_sales_return_customer_date (customer_id, return_date),
  CONSTRAINT fk_sales_return_source_order FOREIGN KEY (source_sales_order_id) REFERENCES sales_order(id),
  CONSTRAINT fk_sales_return_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
  CONSTRAINT fk_sales_return_salesperson FOREIGN KEY (salesperson_id) REFERENCES employee(id),
  CONSTRAINT fk_sales_return_printed_by FOREIGN KEY (printed_by) REFERENCES sys_user(id),
  CONSTRAINT fk_sales_return_approved_by FOREIGN KEY (approved_by) REFERENCES sys_user(id),
  CONSTRAINT fk_sales_return_voided_by FOREIGN KEY (voided_by) REFERENCES sys_user(id),
  CONSTRAINT fk_sales_return_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT fk_sales_return_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id),
  CONSTRAINT ck_sales_return_status CHECK (status IN ('DRAFT','APPROVED','VOIDED'))
) ENGINE=InnoDB;

CREATE TABLE sales_return_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  sales_return_id BIGINT UNSIGNED NOT NULL,
  line_no INT UNSIGNED NOT NULL,
  source_sales_order_item_id BIGINT UNSIGNED NULL,
  sku_id BIGINT UNSIGNED NOT NULL,
  sku_code_snapshot VARCHAR(64) NOT NULL,
  product_name_snapshot VARCHAR(200) NOT NULL,
  specification_snapshot VARCHAR(200) NULL,
  color_snapshot VARCHAR(100) NULL,
  package_spec_snapshot DECIMAL(18,4) NULL,
  package_count DECIMAL(18,4) NULL,
  package_unit_snapshot VARCHAR(32) NULL,
  quantity DECIMAL(18,4) NOT NULL,
  sales_unit_snapshot VARCHAR(32) NOT NULL,
  unit_price DECIMAL(18,2) NOT NULL,
  reference_price DECIMAL(18,2) NULL,
  line_amount DECIMAL(18,2) NOT NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sales_return_item_line (sales_return_id, line_no),
  KEY idx_sales_return_item_sku (sku_id),
  CONSTRAINT fk_sales_return_item_return FOREIGN KEY (sales_return_id) REFERENCES sales_return(id),
  CONSTRAINT fk_sales_return_item_source FOREIGN KEY (source_sales_order_item_id) REFERENCES sales_order_item(id),
  CONSTRAINT fk_sales_return_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id),
  CONSTRAINT ck_sales_return_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB;

CREATE TABLE return_warehouse (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  warehouse_no VARCHAR(32) NOT NULL,
  warehouse_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  remark VARCHAR(1000) NULL,
  approved_by BIGINT UNSIGNED NULL,
  approved_at DATETIME(3) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_return_warehouse_no (warehouse_no),
  KEY idx_return_warehouse_date_status (warehouse_date, status),
  CONSTRAINT fk_return_warehouse_approved_by FOREIGN KEY (approved_by) REFERENCES sys_user(id),
  CONSTRAINT fk_return_warehouse_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT fk_return_warehouse_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id),
  CONSTRAINT ck_return_warehouse_status CHECK (status IN ('DRAFT','APPROVED'))
) ENGINE=InnoDB;

CREATE TABLE return_warehouse_return (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  return_warehouse_id BIGINT UNSIGNED NOT NULL,
  sales_return_id BIGINT UNSIGNED NOT NULL,
  line_no INT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_return_warehouse_line (return_warehouse_id, line_no),
  UNIQUE KEY uk_return_warehouse_sales_return (sales_return_id),
  CONSTRAINT fk_return_warehouse_return_header FOREIGN KEY (return_warehouse_id) REFERENCES return_warehouse(id),
  CONSTRAINT fk_return_warehouse_return_sales FOREIGN KEY (sales_return_id) REFERENCES sales_return(id)
) ENGINE=InnoDB;

ALTER TABLE inventory_movement
  DROP CHECK ck_inventory_movement_type,
  ADD CONSTRAINT ck_inventory_movement_type CHECK (
    movement_type IN (
      'INBOUND','OUTBOUND','ADJUSTMENT','REVERSAL','SALE_PRINT',
      'PURCHASE_RECEIPT','PURCHASE_RETURN','SALES_RETURN'
    )
  );
