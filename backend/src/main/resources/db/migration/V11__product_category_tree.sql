ALTER TABLE product_category DROP INDEX uk_product_category_code;
ALTER TABLE product_category ADD UNIQUE KEY uk_product_category_parent_code (parent_id,category_code);

INSERT INTO product_category(parent_id,category_code,category_name,enabled,sort_order) VALUES
(NULL,'000','Architectural Coatings',1,1),
(NULL,'001','Industrial Coatings',1,2),
(NULL,'010','Application Tools',1,3),
(NULL,'016','Internal Supplies',1,4)
ON DUPLICATE KEY UPDATE category_name=VALUES(category_name),enabled=1,sort_order=VALUES(sort_order);

INSERT INTO product_category(parent_id,category_code,category_name,enabled,sort_order)
SELECT p.id,x.code,x.name,1,x.ord FROM product_category p JOIN (
 SELECT '000' parent_code,'PRI' code,'Primer' name,1 ord
 UNION ALL SELECT '000','TOP','Topcoat',2
 UNION ALL SELECT '000','WAL','Wall Paint',3
 UNION ALL SELECT '000','FLR','Floor Coating',4
 UNION ALL SELECT '001','ALK','Alkyd Enamel',1
 UNION ALL SELECT '001','EPO','Epoxy Coating',2
 UNION ALL SELECT '001','PU','Polyurethane Coating',3
 UNION ALL SELECT '001','SOL','Solvent and Thinner',4
 UNION ALL SELECT '010','BRU','Brushes',1
 UNION ALL SELECT '010','ROL','Rollers',2
 UNION ALL SELECT '010','SPR','Spray Equipment',3
 UNION ALL SELECT '010','ABR','Abrasives',4
 UNION ALL SELECT '016','PKG','Packaging',1
 UNION ALL SELECT '016','OFF','Office Supplies',2
 UNION ALL SELECT '016','MNT','Maintenance Supplies',3
) x ON x.parent_code=p.category_code AND p.parent_id IS NULL
ON DUPLICATE KEY UPDATE category_name=VALUES(category_name),enabled=1,sort_order=VALUES(sort_order);
