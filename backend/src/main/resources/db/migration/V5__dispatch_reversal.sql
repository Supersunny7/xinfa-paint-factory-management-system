ALTER TABLE inventory_movement
  DROP CHECK ck_inventory_movement_type,
  ADD CONSTRAINT ck_inventory_movement_type CHECK (movement_type IN ('INBOUND','OUTBOUND','ADJUSTMENT','REVERSAL'));

ALTER TABLE dispatch_sheet
  DROP CHECK ck_dispatch_status,
  ADD COLUMN reversed_by BIGINT UNSIGNED NULL AFTER completed_at,
  ADD COLUMN reversed_at DATETIME(3) NULL AFTER reversed_by,
  ADD COLUMN reverse_reason VARCHAR(500) NULL AFTER reversed_at,
  ADD CONSTRAINT fk_dispatch_reversed_by FOREIGN KEY (reversed_by) REFERENCES sys_user(id),
  ADD CONSTRAINT ck_dispatch_status CHECK (status IN ('DRAFT','APPROVED','COMPLETED','REVERSED','VOIDED'));
