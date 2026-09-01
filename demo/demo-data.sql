-- Fictional public demo data for Xinfa Paint Factory Management System.
-- Run only after the application has created the bootstrap administrator.
-- All names, addresses, telephone numbers, prices, and quantities below are invented.

SET @demo_user_id = (SELECT id FROM sys_user ORDER BY id LIMIT 1);

INSERT INTO employee_type(type_code,type_name,enabled,remark) VALUES
('MGMT','Management',1,'Fictional demo data'),
('SALES','Sales',1,'Fictional demo data'),
('DRIVER','Driver',1,'Fictional demo data')
ON DUPLICATE KEY UPDATE type_name=VALUES(type_name),enabled=1,remark=VALUES(remark);

INSERT INTO department(department_code,department_name,enabled,remark) VALUES
('ADMIN','Administration',1,'Fictional demo data'),
('COMM','Commercial',1,'Fictional demo data'),
('LOG','Logistics',1,'Fictional demo data')
ON DUPLICATE KEY UPDATE department_name=VALUES(department_name),enabled=1,remark=VALUES(remark);

INSERT INTO route(route_code,route_name,enabled,remark) VALUES
('NORTH','North Route',1,'Fictional demo data'),
('SOUTH','South Route',1,'Fictional demo data')
ON DUPLICATE KEY UPDATE route_name=VALUES(route_name),enabled=1,remark=VALUES(remark);

INSERT INTO vehicle(vehicle_code,plate_no,vehicle_type,enabled,remark) VALUES
('VAN-01','DEMO-001','Delivery Van',1,'Fictional demo vehicle'),
('TRUCK-01','DEMO-002','Light Truck',1,'Fictional demo vehicle')
ON DUPLICATE KEY UPDATE plate_no=VALUES(plate_no),vehicle_type=VALUES(vehicle_type),enabled=1,remark=VALUES(remark);

INSERT INTO supplier(supplier_code,short_name,phone,mobile,address,enabled,remark) VALUES
('SUP-001','Northstar Materials','555-0101',NULL,'100 Demo Industrial Road',1,'Fictional supplier'),
('SUP-002','Blue River Packaging','555-0102',NULL,'200 Sample Avenue',1,'Fictional supplier')
ON DUPLICATE KEY UPDATE short_name=VALUES(short_name),phone=VALUES(phone),address=VALUES(address),enabled=1,remark=VALUES(remark);

INSERT INTO employee(employee_code,employee_name,gender,employee_type_id,department_id,position_name,is_salesperson,phone,enabled,remark,version,created_by,created_at,updated_by,updated_at)
SELECT 'E001','Alex Morgan','Unspecified',et.id,d.id,'Sales Representative',1,'555-0111',1,'Fictional employee',0,@demo_user_id,NOW(3),@demo_user_id,NOW(3)
FROM employee_type et JOIN department d ON et.type_code='SALES' AND d.department_code='COMM'
ON DUPLICATE KEY UPDATE employee_name=VALUES(employee_name),employee_type_id=VALUES(employee_type_id),department_id=VALUES(department_id),is_salesperson=1,enabled=1;

INSERT INTO employee(employee_code,employee_name,gender,employee_type_id,department_id,position_name,is_salesperson,phone,enabled,remark,version,created_by,created_at,updated_by,updated_at)
SELECT 'E002','Jordan Lee','Unspecified',et.id,d.id,'Driver',0,'555-0112',1,'Fictional employee',0,@demo_user_id,NOW(3),@demo_user_id,NOW(3)
FROM employee_type et JOIN department d ON et.type_code='DRIVER' AND d.department_code='LOG'
ON DUPLICATE KEY UPDATE employee_name=VALUES(employee_name),employee_type_id=VALUES(employee_type_id),department_id=VALUES(department_id),enabled=1;

SET @salesperson_id = (SELECT id FROM employee WHERE employee_code='E001');

INSERT INTO customer(customer_code,short_name,full_name,mnemonic_code,region_large,region_small,route_text,contact_name,mobile,phone,address,salesperson_id,settlement_method,enabled,remark,version,created_by,created_at,updated_by,updated_at) VALUES
('CUST-001','Harbor Hardware','Harbor Hardware Demo LLC','HARBOR','North','District A','North Route','Taylor Smith',NULL,'555-0121','10 Fictional Market Street',@salesperson_id,'Cash',1,'Fictional customer',0,@demo_user_id,NOW(3),@demo_user_id,NOW(3)),
('CUST-002','Summit Workshop','Summit Workshop Demo Ltd','SUMMIT','South','District B','South Route','Casey Brown',NULL,'555-0122 555-0123','20 Example Commerce Road',@salesperson_id,'Credit',1,'Fictional customer',0,@demo_user_id,NOW(3),@demo_user_id,NOW(3))
ON DUPLICATE KEY UPDATE short_name=VALUES(short_name),phone=VALUES(phone),salesperson_id=VALUES(salesperson_id),settlement_method=VALUES(settlement_method),enabled=1;

SET @primer_category = (SELECT c.id FROM product_category c JOIN product_category p ON p.id=c.parent_id WHERE p.category_code='000' AND c.category_code='PRI');
SET @topcoat_category = (SELECT c.id FROM product_category c JOIN product_category p ON p.id=c.parent_id WHERE p.category_code='000' AND c.category_code='TOP');
SET @tool_category = (SELECT c.id FROM product_category c JOIN product_category p ON p.id=c.parent_id WHERE p.category_code='010' AND c.category_code='BRU');

INSERT INTO product_sku(sku_code,product_name,specification,color,category_id,sales_unit,package_spec,package_unit,wholesale_price,retail_price,total_stock,stock_lower_limit,last_purchase_price,saleable,classification_status,enabled,remark,version,created_by,created_at,updated_by,updated_at) VALUES
('P-PRI-001','Universal Metal Primer','1 L','Gray',@primer_category,'can',1,'can',18.00,22.00,48,12,13.50,1,'CONFIRMED',1,'Fictional product',0,@demo_user_id,NOW(3),@demo_user_id,NOW(3)),
('P-TOP-001','High Gloss Enamel','1 L','Ocean Blue',@topcoat_category,'can',1,'can',24.00,29.00,36,10,18.25,1,'CONFIRMED',1,'Fictional product',0,@demo_user_id,NOW(3),@demo_user_id,NOW(3)),
('T-BRU-050','Professional Paint Brush','50 mm',NULL,@tool_category,'piece',1,'piece',5.50,7.00,120,24,3.80,1,'CONFIRMED',1,'Fictional product',0,@demo_user_id,NOW(3),@demo_user_id,NOW(3))
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name),category_id=VALUES(category_id),wholesale_price=VALUES(wholesale_price),total_stock=VALUES(total_stock),enabled=1;
