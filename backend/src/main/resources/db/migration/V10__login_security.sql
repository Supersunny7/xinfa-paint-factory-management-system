ALTER TABLE sys_user
  ADD COLUMN failed_login_attempts INT UNSIGNED NOT NULL DEFAULT 0 AFTER status,
  ADD COLUMN locked_at DATETIME(3) NULL AFTER failed_login_attempts,
  ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0 AFTER locked_at;
