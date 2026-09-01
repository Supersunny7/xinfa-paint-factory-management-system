ALTER TABLE sys_user ADD COLUMN role_code VARCHAR(20) NOT NULL DEFAULT 'ADMIN' AFTER display_name;
UPDATE sys_user SET role_code='ADMIN' WHERE role_code IS NULL OR role_code='';
