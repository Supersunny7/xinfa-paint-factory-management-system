CREATE TABLE master_data_audit_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  entity_type VARCHAR(40) NOT NULL,
  entity_id BIGINT UNSIGNED NULL,
  entity_code VARCHAR(64) NULL,
  entity_name VARCHAR(200) NULL,
  action VARCHAR(20) NOT NULL,
  details VARCHAR(1000) NULL,
  operator_user_id BIGINT UNSIGNED NOT NULL,
  operator_name_snapshot VARCHAR(100) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_audit_entity (entity_type, entity_id),
  KEY idx_audit_created_at (created_at),
  CONSTRAINT fk_audit_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB;
