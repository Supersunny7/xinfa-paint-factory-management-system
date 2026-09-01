CREATE TABLE IF NOT EXISTS employee_type (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  type_code VARCHAR(64) NOT NULL,
  type_name VARCHAR(100) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_employee_type_code (type_code),
  KEY idx_employee_type_name (type_name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS department (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  department_code VARCHAR(64) NOT NULL,
  department_name VARCHAR(100) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_department_code (department_code),
  KEY idx_department_name (department_name)
) ENGINE=InnoDB;
