CREATE TABLE IF NOT EXISTS supplier (
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

ALTER TABLE employee
  ADD COLUMN gender VARCHAR(20) NULL AFTER employee_name,
  ADD COLUMN employee_type_id BIGINT UNSIGNED NULL AFTER gender,
  ADD COLUMN department_id BIGINT UNSIGNED NULL AFTER employee_type_id,
  ADD COLUMN position_name VARCHAR(100) NULL AFTER department_id,
  ADD COLUMN education VARCHAR(100) NULL AFTER position_name,
  ADD COLUMN id_card VARCHAR(64) NULL AFTER education,
  ADD COLUMN address VARCHAR(500) NULL AFTER id_card,
  ADD COLUMN is_salesperson TINYINT(1) NOT NULL DEFAULT 0 AFTER address,
  ADD COLUMN hometown VARCHAR(100) NULL AFTER is_salesperson,
  ADD COLUMN postal_code VARCHAR(32) NULL AFTER hometown,
  ADD COLUMN hire_date DATE NULL AFTER postal_code,
  ADD KEY idx_employee_type_id (employee_type_id),
  ADD KEY idx_employee_department_id (department_id),
  ADD CONSTRAINT fk_employee_employee_type FOREIGN KEY (employee_type_id) REFERENCES employee_type(id),
  ADD CONSTRAINT fk_employee_department FOREIGN KEY (department_id) REFERENCES department(id);

ALTER TABLE vehicle
  ADD COLUMN vehicle_type VARCHAR(100) NULL AFTER plate_no;
