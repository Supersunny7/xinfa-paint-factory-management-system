-- 油漆涂料工厂管理系统：第一阶段 MySQL 8 schema V1
-- 所有业务时间由应用按工厂时区写入；数据库连接统一 utf8mb4。

CREATE DATABASE IF NOT EXISTS paint_factory
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE paint_factory;

CREATE TABLE sys_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  role_code VARCHAR(20) NOT NULL DEFAULT 'ADMIN',
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  failed_login_attempts INT UNSIGNED NOT NULL DEFAULT 0,
  locked_at DATETIME(3) NULL,
  must_change_password TINYINT(1) NOT NULL DEFAULT 0,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_username (username),
  CONSTRAINT ck_sys_user_status CHECK (status IN ('ENABLED','DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE sys_role (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(100) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB;

CREATE TABLE sys_user_role (
  user_id BIGINT UNSIGNED NOT NULL,
  role_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB;

CREATE TABLE employee_type (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  type_code VARCHAR(64) NOT NULL,
  type_name VARCHAR(100) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_employee_type_code (type_code),
  KEY idx_employee_type_name (type_name)
) ENGINE=InnoDB;

CREATE TABLE department (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  department_code VARCHAR(64) NOT NULL,
  department_name VARCHAR(100) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_department_code (department_code),
  KEY idx_department_name (department_name)
) ENGINE=InnoDB;

CREATE TABLE employee (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  employee_code VARCHAR(64) NOT NULL,
  employee_name VARCHAR(100) NOT NULL,
  gender VARCHAR(20) NULL,
  employee_type_id BIGINT UNSIGNED NULL,
  department_id BIGINT UNSIGNED NULL,
  position_name VARCHAR(100) NULL,
  education VARCHAR(100) NULL,
  id_card VARCHAR(64) NULL,
  address VARCHAR(500) NULL,
  is_salesperson TINYINT(1) NOT NULL DEFAULT 0,
  hometown VARCHAR(100) NULL,
  postal_code VARCHAR(32) NULL,
  hire_date DATE NULL,
  phone VARCHAR(64) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_employee_code (employee_code),
  KEY idx_employee_name (employee_name),
  KEY idx_employee_type_id (employee_type_id),
  KEY idx_employee_department_id (department_id),
  CONSTRAINT fk_employee_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT fk_employee_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id),
  CONSTRAINT fk_employee_employee_type FOREIGN KEY (employee_type_id) REFERENCES employee_type(id),
  CONSTRAINT fk_employee_department FOREIGN KEY (department_id) REFERENCES department(id)
) ENGINE=InnoDB;

CREATE TABLE supplier (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  supplier_code VARCHAR(64) NOT NULL,
  short_name VARCHAR(200) NOT NULL,
  phone VARCHAR(128) NULL,
  mobile VARCHAR(64) NULL,
  fax VARCHAR(64) NULL,
  address VARCHAR(500) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  remark VARCHAR(1000) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_supplier_code (supplier_code),
  KEY idx_supplier_short_name (short_name)
) ENGINE=InnoDB;

CREATE TABLE customer (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  customer_code VARCHAR(64) NOT NULL,
  short_name VARCHAR(200) NOT NULL,
  full_name VARCHAR(300) NULL,
  mnemonic_code VARCHAR(64) NULL,
  region_large VARCHAR(100) NULL,
  region_small VARCHAR(100) NULL,
  category VARCHAR(100) NULL,
  route_text VARCHAR(100) NULL,
  contact_name VARCHAR(100) NULL,
  mobile VARCHAR(64) NULL,
  phone VARCHAR(128) NULL,
  fax VARCHAR(64) NULL,
  postal_code VARCHAR(32) NULL,
  address VARCHAR(500) NULL,
  salesperson_id BIGINT UNSIGNED NULL,
  settlement_method VARCHAR(32) NULL,
  default_price_type VARCHAR(32) NULL,
  second_price_type VARCHAR(32) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  remark VARCHAR(1000) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_customer_code (customer_code),
  KEY idx_customer_short_name (short_name),
  KEY idx_customer_mnemonic (mnemonic_code),
  KEY idx_customer_phone (phone),
  CONSTRAINT fk_customer_salesperson FOREIGN KEY (salesperson_id) REFERENCES employee(id),
  CONSTRAINT fk_customer_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT fk_customer_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB;

CREATE TABLE product_category (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  parent_id BIGINT UNSIGNED NULL,
  category_code VARCHAR(64) NOT NULL,
  category_name VARCHAR(100) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  saleable_default TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_category_parent_code (parent_id, category_code),
  CONSTRAINT fk_product_category_parent FOREIGN KEY (parent_id) REFERENCES product_category(id)
) ENGINE=InnoDB;

CREATE TABLE product_sku (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  sku_code VARCHAR(64) NOT NULL,
  product_name VARCHAR(200) NOT NULL,
  specification VARCHAR(200) NULL,
  color VARCHAR(100) NULL,
  category_id BIGINT UNSIGNED NULL,
  classification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  mnemonic_code VARCHAR(64) NULL,
  barcode VARCHAR(128) NULL,
  sales_unit VARCHAR(32) NOT NULL,
  package_spec DECIMAL(18,4) NULL,
  package_unit VARCHAR(32) NULL,
  wholesale_price DECIMAL(18,2) NULL,
  retail_price DECIMAL(18,2) NULL,
  preferential_price DECIMAL(18,2) NULL,
  special_price DECIMAL(18,2) NULL,
  factory_price DECIMAL(18,2) NULL,
  total_stock DECIMAL(18,4) NOT NULL DEFAULT 0,
  stock_lower_limit DECIMAL(18,4) NOT NULL DEFAULT 0,
  last_purchase_price DECIMAL(18,2) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  saleable TINYINT(1) NOT NULL DEFAULT 1,
  remark VARCHAR(1000) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_sku_code (sku_code),
  UNIQUE KEY uk_product_sku_barcode (barcode),
  KEY idx_product_sku_name (product_name),
  KEY idx_product_sku_mnemonic (mnemonic_code),
  KEY idx_product_sku_category (category_id),
  CONSTRAINT fk_product_sku_category FOREIGN KEY (category_id) REFERENCES product_category(id),
  CONSTRAINT fk_product_sku_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT fk_product_sku_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB;

CREATE TABLE inventory_movement (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  product_sku_id BIGINT UNSIGNED NOT NULL,
  movement_type VARCHAR(20) NOT NULL,
  quantity_change DECIMAL(18,4) NOT NULL,
  before_quantity DECIMAL(18,4) NOT NULL,
  after_quantity DECIMAL(18,4) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  reference_type VARCHAR(32) NULL,
  reference_id BIGINT UNSIGNED NULL,
  reference_no VARCHAR(64) NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_inventory_movement_product_time (product_sku_id, created_at),
  UNIQUE KEY uk_inventory_movement_reference (product_sku_id, reference_type, reference_id),
  KEY idx_inventory_movement_reference_no (reference_type, reference_no),
  CONSTRAINT fk_inventory_movement_product FOREIGN KEY (product_sku_id) REFERENCES product_sku(id),
  CONSTRAINT fk_inventory_movement_user FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT ck_inventory_movement_type CHECK (movement_type IN ('INBOUND','OUTBOUND','ADJUSTMENT','REVERSAL')),
  CONSTRAINT ck_inventory_movement_change CHECK (quantity_change <> 0),
  CONSTRAINT ck_inventory_movement_quantity CHECK (before_quantity >= 0 AND after_quantity >= 0)
) ENGINE=InnoDB;

CREATE TABLE route (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  route_code VARCHAR(64) NOT NULL,
  route_name VARCHAR(100) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_route_code (route_code),
  KEY idx_route_name (route_name)
) ENGINE=InnoDB;

CREATE TABLE vehicle (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  vehicle_code VARCHAR(64) NOT NULL,
  plate_no VARCHAR(32) NULL,
  vehicle_type VARCHAR(100) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_vehicle_code (vehicle_code),
  UNIQUE KEY uk_vehicle_plate_no (plate_no)
) ENGINE=InnoDB;

CREATE TABLE number_sequence (
  biz_type VARCHAR(32) NOT NULL,
  biz_date DATE NOT NULL,
  current_value INT UNSIGNED NOT NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (biz_type, biz_date)
) ENGINE=InnoDB;

CREATE TABLE sales_order (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_no VARCHAR(32) NOT NULL,
  order_date DATE NOT NULL,
  customer_id BIGINT UNSIGNED NOT NULL,
  customer_code_snapshot VARCHAR(64) NOT NULL,
  customer_name_snapshot VARCHAR(300) NOT NULL,
  customer_phone_snapshot VARCHAR(128) NULL,
  salesperson_id BIGINT UNSIGNED NULL,
  salesperson_name_snapshot VARCHAR(100) NULL,
  settlement_method VARCHAR(32) NOT NULL,
  immediate_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  on_account_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  print_template_code VARCHAR(64) NULL,
  remark VARCHAR(1000) NULL,
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
  UNIQUE KEY uk_sales_order_no (order_no),
  KEY idx_sales_order_date_status (order_date, status),
  KEY idx_sales_order_customer_date (customer_id, order_date),
  CONSTRAINT fk_sales_order_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
  CONSTRAINT fk_sales_order_salesperson FOREIGN KEY (salesperson_id) REFERENCES employee(id),
  CONSTRAINT fk_sales_order_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT fk_sales_order_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id),
  CONSTRAINT fk_sales_order_approved_by FOREIGN KEY (approved_by) REFERENCES sys_user(id),
  CONSTRAINT fk_sales_order_voided_by FOREIGN KEY (voided_by) REFERENCES sys_user(id),
  CONSTRAINT ck_sales_order_status CHECK (status IN ('DRAFT','APPROVED','VOIDED')),
  CONSTRAINT ck_sales_order_amounts CHECK (immediate_amount + on_account_amount = total_amount)
) ENGINE=InnoDB;

CREATE TABLE sales_order_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  sales_order_id BIGINT UNSIGNED NOT NULL,
  line_no INT UNSIGNED NOT NULL,
  line_type VARCHAR(32) NOT NULL DEFAULT 'PRODUCT',
  sku_id BIGINT UNSIGNED NULL,
  sku_code_snapshot VARCHAR(64) NOT NULL,
  product_name_snapshot VARCHAR(200) NOT NULL,
  specification_snapshot VARCHAR(200) NULL,
  color_snapshot VARCHAR(100) NULL,
  package_spec_snapshot DECIMAL(18,4) NULL,
  package_count DECIMAL(18,4) NULL,
  package_unit_snapshot VARCHAR(32) NULL,
  quantity DECIMAL(18,4) NOT NULL,
  sales_unit_snapshot VARCHAR(32) NOT NULL,
  pre_discount_price DECIMAL(18,2) NULL,
  discount_rate DECIMAL(9,4) NULL,
  unit_price DECIMAL(18,2) NOT NULL,
  reference_price DECIMAL(18,2) NULL,
  tax_rate DECIMAL(9,4) NULL,
  line_amount DECIMAL(18,2) NOT NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sales_order_item_line (sales_order_id, line_no),
  KEY idx_sales_order_item_sku (sku_id),
  CONSTRAINT fk_sales_order_item_order FOREIGN KEY (sales_order_id) REFERENCES sales_order(id),
  CONSTRAINT fk_sales_order_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id),
  CONSTRAINT ck_sales_order_item_type CHECK (line_type IN ('PRODUCT','DISCOUNT_ADJUSTMENT','CASH_ADJUSTMENT','GIFT','OTHER_ADJUSTMENT'))
) ENGINE=InnoDB;

CREATE TABLE dispatch_sheet (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dispatch_no VARCHAR(32) NOT NULL,
  dispatch_date DATE NOT NULL,
  route_id BIGINT UNSIGNED NULL,
  route_name_snapshot VARCHAR(100) NULL,
  vehicle_id BIGINT UNSIGNED NULL,
  vehicle_code_snapshot VARCHAR(64) NULL,
  driver_id BIGINT UNSIGNED NULL,
  driver_name_snapshot VARCHAR(100) NULL,
  delivery_person_id BIGINT UNSIGNED NULL,
  delivery_person_name_snapshot VARCHAR(100) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  remark VARCHAR(1000) NULL,
  approved_by BIGINT UNSIGNED NULL,
  approved_at DATETIME(3) NULL,
  completed_by BIGINT UNSIGNED NULL,
  completed_at DATETIME(3) NULL,
  reversed_by BIGINT UNSIGNED NULL,
  reversed_at DATETIME(3) NULL,
  reverse_reason VARCHAR(500) NULL,
  voided_by BIGINT UNSIGNED NULL,
  voided_at DATETIME(3) NULL,
  void_reason VARCHAR(500) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dispatch_sheet_no (dispatch_no),
  KEY idx_dispatch_date_status (dispatch_date, status),
  KEY idx_dispatch_route_date (route_id, dispatch_date),
  KEY idx_dispatch_vehicle_date (vehicle_id, dispatch_date),
  CONSTRAINT fk_dispatch_route FOREIGN KEY (route_id) REFERENCES route(id),
  CONSTRAINT fk_dispatch_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle(id),
  CONSTRAINT fk_dispatch_driver FOREIGN KEY (driver_id) REFERENCES employee(id),
  CONSTRAINT fk_dispatch_delivery_person FOREIGN KEY (delivery_person_id) REFERENCES employee(id),
  CONSTRAINT fk_dispatch_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT fk_dispatch_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id),
  CONSTRAINT fk_dispatch_approved_by FOREIGN KEY (approved_by) REFERENCES sys_user(id),
  CONSTRAINT fk_dispatch_completed_by FOREIGN KEY (completed_by) REFERENCES sys_user(id),
  CONSTRAINT fk_dispatch_reversed_by FOREIGN KEY (reversed_by) REFERENCES sys_user(id),
  CONSTRAINT fk_dispatch_voided_by FOREIGN KEY (voided_by) REFERENCES sys_user(id),
  CONSTRAINT ck_dispatch_status CHECK (status IN ('DRAFT','APPROVED','COMPLETED','REVERSED','VOIDED'))
) ENGINE=InnoDB;

CREATE TABLE dispatch_sales_order (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dispatch_sheet_id BIGINT UNSIGNED NOT NULL,
  sales_order_id BIGINT UNSIGNED NOT NULL,
  sequence_no INT UNSIGNED NOT NULL,
  entered_order_no VARCHAR(32) NOT NULL,
  document_type_snapshot VARCHAR(32) NOT NULL DEFAULT 'DELIVERY_ORDER',
  customer_code_snapshot VARCHAR(64) NOT NULL,
  customer_name_snapshot VARCHAR(300) NOT NULL,
  settlement_method_snapshot VARCHAR(32) NOT NULL,
  amount_snapshot DECIMAL(18,2) NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  active_sales_order_id BIGINT UNSIGNED GENERATED ALWAYS AS
    (CASE WHEN is_active = 1 THEN sales_order_id ELSE NULL END) STORED,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dispatch_order_line (dispatch_sheet_id, sales_order_id),
  UNIQUE KEY uk_active_sales_order (active_sales_order_id),
  UNIQUE KEY uk_dispatch_sequence (dispatch_sheet_id, sequence_no),
  CONSTRAINT fk_dispatch_order_sheet FOREIGN KEY (dispatch_sheet_id) REFERENCES dispatch_sheet(id),
  CONSTRAINT fk_dispatch_order_sales FOREIGN KEY (sales_order_id) REFERENCES sales_order(id),
  CONSTRAINT fk_dispatch_order_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB;

CREATE TABLE print_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  document_type VARCHAR(32) NOT NULL,
  document_id BIGINT UNSIGNED NOT NULL,
  document_no VARCHAR(32) NOT NULL,
  template_code VARCHAR(64) NOT NULL,
  template_version VARCHAR(32) NOT NULL,
  print_no INT UNSIGNED NOT NULL,
  printed_by BIGINT UNSIGNED NOT NULL,
  printed_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_print_document_no (document_type, document_id, print_no),
  KEY idx_print_document (document_type, document_id),
  CONSTRAINT fk_print_record_user FOREIGN KEY (printed_by) REFERENCES sys_user(id)
) ENGINE=InnoDB;

CREATE TABLE audit_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  business_type VARCHAR(64) NOT NULL,
  business_id BIGINT UNSIGNED NOT NULL,
  business_no VARCHAR(64) NULL,
  action VARCHAR(64) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  operator_name VARCHAR(100) NOT NULL,
  reason VARCHAR(500) NULL,
  before_data JSON NULL,
  after_data JSON NULL,
  occurred_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_audit_business (business_type, business_id, occurred_at),
  KEY idx_audit_operator_time (operator_id, occurred_at),
  CONSTRAINT fk_audit_log_user FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB;
