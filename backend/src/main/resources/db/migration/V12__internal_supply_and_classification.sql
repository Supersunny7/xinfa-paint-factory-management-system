ALTER TABLE product_category
  ADD COLUMN saleable_default TINYINT(1) NOT NULL DEFAULT 1 AFTER enabled;

ALTER TABLE product_sku
  ADD COLUMN saleable TINYINT(1) NOT NULL DEFAULT 1 AFTER enabled,
  ADD COLUMN classification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER category_id;

UPDATE product_category
SET saleable_default=0
WHERE parent_id IS NULL AND category_code='016';

UPDATE product_category c
JOIN product_category p ON p.id=c.parent_id
SET c.saleable_default=0
WHERE p.category_code='016';

UPDATE product_sku s
JOIN product_category c ON c.id=s.category_id
LEFT JOIN product_category p ON p.id=c.parent_id
SET s.saleable=0,
    s.classification_status='CONFIRMED'
WHERE COALESCE(p.category_code,c.category_code)='016';

UPDATE product_sku
SET classification_status='AUTO'
WHERE category_id IS NOT NULL AND classification_status='PENDING';
