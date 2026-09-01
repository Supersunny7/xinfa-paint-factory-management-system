CREATE TABLE expense_category (
  category_code VARCHAR(32) NOT NULL,
  category_name VARCHAR(100) NOT NULL,
  remark VARCHAR(500) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (category_code)
) ENGINE=InnoDB;

CREATE TABLE other_expense (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  expense_no VARCHAR(32) NOT NULL,
  expense_date DATE NOT NULL,
  account_name VARCHAR(32) NOT NULL DEFAULT 'Cash',
  handler_employee_id BIGINT UNSIGNED NULL,
  handler_name_snapshot VARCHAR(100) NULL,
  total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  remark VARCHAR(1000) NULL,
  approved_by BIGINT UNSIGNED NULL,
  approved_at DATETIME(3) NULL,
  printed_by BIGINT UNSIGNED NULL,
  printed_at DATETIME(3) NULL,
  print_count INT UNSIGNED NOT NULL DEFAULT 0,
  voided_by BIGINT UNSIGNED NULL,
  voided_at DATETIME(3) NULL,
  void_reason VARCHAR(500) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_other_expense_no (expense_no),
  KEY idx_other_expense_date (expense_date),
  CONSTRAINT fk_other_expense_handler FOREIGN KEY (handler_employee_id) REFERENCES employee(id),
  CONSTRAINT fk_other_expense_approved FOREIGN KEY (approved_by) REFERENCES sys_user(id),
  CONSTRAINT fk_other_expense_printed FOREIGN KEY (printed_by) REFERENCES sys_user(id),
  CONSTRAINT fk_other_expense_voided FOREIGN KEY (voided_by) REFERENCES sys_user(id),
  CONSTRAINT fk_other_expense_created FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT fk_other_expense_updated FOREIGN KEY (updated_by) REFERENCES sys_user(id),
  CONSTRAINT ck_other_expense_account CHECK (account_name='Cash'),
  CONSTRAINT ck_other_expense_status CHECK (status IN ('DRAFT','APPROVED','VOIDED'))
) ENGINE=InnoDB;

CREATE TABLE other_expense_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  other_expense_id BIGINT UNSIGNED NOT NULL,
  line_no INT UNSIGNED NOT NULL,
  category_code VARCHAR(32) NOT NULL,
  category_name_snapshot VARCHAR(100) NOT NULL,
  summary VARCHAR(500) NULL,
  amount DECIMAL(18,2) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_other_expense_line (other_expense_id,line_no),
  CONSTRAINT fk_other_expense_item_header FOREIGN KEY (other_expense_id) REFERENCES other_expense(id),
  CONSTRAINT fk_other_expense_item_category FOREIGN KEY (category_code) REFERENCES expense_category(category_code),
  CONSTRAINT ck_other_expense_item_amount CHECK (amount>0)
) ENGINE=InnoDB;

INSERT INTO expense_category(category_code,category_name,remark,sort_order) VALUES
('MEAL','Meals',NULL,1),
('FUEL','Vehicle Fuel',NULL,2),
('TOLL','Road Tolls',NULL,3),
('REPAIR','Vehicle Repairs',NULL,4),
('ELECTRIC','Electricity',NULL,5),
('WATER','Water',NULL,6),
('PHONE','Telephone and Internet',NULL,7),
('OFFICE','Office Supplies',NULL,8),
('PRINT','Printing and Stationery',NULL,9),
('INSURANCE','Insurance',NULL,10),
('PAYROLL','Payroll',NULL,11),
('OTHER','Other',NULL,12);
