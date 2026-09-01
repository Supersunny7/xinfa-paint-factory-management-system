package com.sunny.paintfactory.returning;

import com.sunny.paintfactory.common.ApiResponse;
import com.sunny.paintfactory.common.DocumentSort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
@Validated
public class SalesReturnController {
    private final JdbcTemplate jdbc;

    public SalesReturnController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/sales-returns")
    public ApiResponse<List<Map<String,Object>>> listReturns(
            @RequestParam(defaultValue="") String dateFrom,
            @RequestParam(defaultValue="") String dateTo,
            @RequestParam(defaultValue="") String keyword,
            @RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="") String printStatus,
            @RequestParam(defaultValue="date") String sortBy,
            @RequestParam(defaultValue="asc") String sortDirection) {
        StringBuilder where = new StringBuilder("1=1");
        List<Object> args = new ArrayList<>();
        if (!dateFrom.isBlank()) { where.append(" AND r.return_date>=?"); args.add(LocalDate.parse(dateFrom)); }
        if (!dateTo.isBlank()) { where.append(" AND r.return_date<=?"); args.add(LocalDate.parse(dateTo)); }
        if (!status.isBlank()) { where.append(" AND r.status=?"); args.add(status); }
        if ("PRINTED".equals(printStatus)) where.append(" AND r.printed_at IS NOT NULL");
        else if ("UNPRINTED".equals(printStatus)) where.append(" AND r.printed_at IS NULL");
        else if (!printStatus.isBlank()) throw bad("销售退货打印状态无效");
        if (!keyword.isBlank()) {
            where.append(" AND (r.return_no LIKE ? OR r.customer_code_snapshot LIKE ? OR r.customer_name_snapshot LIKE ?)");
            String like="%"+keyword.trim()+"%"; args.add(like); args.add(like); args.add(like);
        }
        String sql="SELECT r.id,r.return_no,r.return_date,r.customer_code_snapshot,r.customer_name_snapshot,"+
                "COALESCE(r.salesperson_name_snapshot,''),r.total_amount,r.status,r.version,r.printed_at,r.print_count,"+
                "w.id,w.warehouse_no,w.status FROM sales_return r LEFT JOIN return_warehouse_return wr ON wr.sales_return_id=r.id " +
                "LEFT JOIN return_warehouse w ON w.id=wr.return_warehouse_id WHERE "+where+" ORDER BY "+DocumentSort.sql(sortBy,sortDirection,"r.return_date","r.return_no","r.id");
        return ApiResponse.success(jdbc.query(sql,(rs,n)->map(
                "id",rs.getLong(1),"returnNo",rs.getString(2),"returnDate",rs.getDate(3).toLocalDate(),
                "customerCode",rs.getString(4),"customerName",rs.getString(5),"salespersonName",rs.getString(6),
                "totalAmount",rs.getBigDecimal(7),"status",rs.getString(8),"version",rs.getInt(9),
                "printedAt",rs.getTimestamp(10)==null?null:rs.getTimestamp(10).toLocalDateTime(),"printCount",rs.getInt(11),
                "warehouseId",rs.getObject(12),"warehouseNo",rs.getString(13),"warehouseStatus",rs.getString(14)),args.toArray()));
    }

    @GetMapping("/sales-returns/{id}")
    public ApiResponse<Map<String,Object>> getReturn(@PathVariable long id) {
        var rows=jdbc.query("SELECT r.id,r.return_no,r.return_date,r.source_sales_order_id,r.customer_id,r.customer_code_snapshot,"+
                "r.customer_name_snapshot,r.salesperson_id,COALESCE(r.salesperson_name_snapshot,''),r.settlement_method,r.total_amount,"+
                "r.status,COALESCE(r.remark,''),r.version,r.created_at,r.updated_at,r.printed_at,r.print_count,r.approved_at " +
                "FROM sales_return r WHERE r.id=?",(rs,n)->map("id",rs.getLong(1),"returnNo",rs.getString(2),
                "returnDate",rs.getDate(3).toLocalDate(),"sourceSalesOrderId",rs.getObject(4),"customerId",rs.getLong(5),
                "customerCode",rs.getString(6),"customerName",rs.getString(7),"salespersonId",rs.getObject(8),
                "salespersonName",rs.getString(9),"settlementMethod",rs.getString(10),"totalAmount",rs.getBigDecimal(11),
                "status",rs.getString(12),"remark",rs.getString(13),"version",rs.getInt(14),
                "createdAt",rs.getTimestamp(15).toLocalDateTime(),"updatedAt",rs.getTimestamp(16).toLocalDateTime(),
                "printedAt",rs.getTimestamp(17)==null?null:rs.getTimestamp(17).toLocalDateTime(),"printCount",rs.getInt(18),
                "approvedAt",rs.getTimestamp(19)==null?null:rs.getTimestamp(19).toLocalDateTime()),id);
        if(rows.isEmpty()) throw notFound("未找到销售退货单");
        Map<String,Object> result=rows.get(0); result.put("items",returnItems(id));
        return ApiResponse.success(result);
    }

    @PostMapping("/sales-returns")
    @Transactional
    public ApiResponse<Map<String,Object>> createReturn(@Valid @RequestBody ReturnRequest r, Authentication auth) {
        long uid=userId(auth); LocalDateTime now=LocalDateTime.now(); Prepared prepared=prepare(r,null);
        String no=nextNo("SALES_RETURN","XT",r.returnDate());
        var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO sales_return(return_no,return_date,source_sales_order_id,customer_id,customer_code_snapshot,customer_name_snapshot,salesperson_id,salesperson_name_snapshot,settlement_method,total_amount,status,remark,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);int i=1;ps.setString(i++,no);ps.setObject(i++,r.returnDate());ps.setObject(i++,r.sourceSalesOrderId());ps.setLong(i++,r.customerId());ps.setString(i++,prepared.customerCode());ps.setString(i++,prepared.customerName());ps.setObject(i++,r.salespersonId());ps.setString(i++,prepared.salespersonName());ps.setString(i++,prepared.settlement());ps.setBigDecimal(i++,prepared.total());ps.setString(i++,r.remark());ps.setLong(i++,uid);ps.setObject(i++,now);ps.setLong(i++,uid);ps.setObject(i,now);return ps;},key);
        long id=Objects.requireNonNull(key.getKey()).longValue(); insertReturnItems(id,prepared.items());
        return ApiResponse.success(map("id",id,"returnNo",no,"totalAmount",prepared.total(),"version",0));
    }

    @PutMapping("/sales-returns/{id}")
    @Transactional
    public ApiResponse<Map<String,Object>> updateReturn(@PathVariable long id,@Valid @RequestBody UpdateReturnRequest r,Authentication auth) {
        var current=jdbc.query("SELECT r.status,r.version,r.printed_at,EXISTS(SELECT 1 FROM return_warehouse_return wr WHERE wr.sales_return_id=r.id) FROM sales_return r WHERE r.id=? FOR UPDATE",(rs,n)->new Object[]{rs.getString(1),rs.getInt(2),rs.getTimestamp(3),rs.getBoolean(4)},id);
        if(current.isEmpty()) throw notFound("未找到销售退货单");
        if(!"DRAFT".equals(current.get(0)[0])||current.get(0)[2]!=null) throw conflict("已审核或已打印的销售退货单不能修改");
        if(Boolean.TRUE.equals(current.get(0)[3])) throw conflict("已加入退货入仓单的销售退货单不能修改");
        if(((Number)current.get(0)[1]).intValue()!=r.version()) throw conflict("销售退货单已被其他人修改，请刷新");
        Prepared p=prepare(r.data(),id); long uid=userId(auth); LocalDateTime now=LocalDateTime.now();
        int changed=jdbc.update("UPDATE sales_return SET return_date=?,source_sales_order_id=?,customer_id=?,customer_code_snapshot=?,customer_name_snapshot=?,salesperson_id=?,salesperson_name_snapshot=?,settlement_method=?,total_amount=?,remark=?,updated_by=?,updated_at=?,version=version+1 WHERE id=? AND status='DRAFT' AND printed_at IS NULL AND version=?",r.data().returnDate(),r.data().sourceSalesOrderId(),r.data().customerId(),p.customerCode(),p.customerName(),r.data().salespersonId(),p.salespersonName(),p.settlement(),p.total(),r.data().remark(),uid,now,id,r.version());
        if(changed!=1) throw conflict("销售退货单状态已变化，请刷新");
        jdbc.update("DELETE FROM sales_return_item WHERE sales_return_id=?",id); insertReturnItems(id,p.items());
        return ApiResponse.success(map("id",id,"totalAmount",p.total(),"version",r.version()+1));
    }

    @PostMapping("/sales-returns/{id}/confirm-print")
    @Transactional
    public ApiResponse<Map<String,Object>> printReturn(@PathVariable long id,@RequestBody VersionRequest r,Authentication auth) {
        var rows=jdbc.query("SELECT status,version,printed_at,print_count FROM sales_return WHERE id=? FOR UPDATE",(rs,n)->new Object[]{rs.getString(1),rs.getInt(2),rs.getTimestamp(3),rs.getInt(4)},id);
        if(rows.isEmpty()) throw notFound("未找到销售退货单"); Object[] x=rows.get(0);
        if("VOIDED".equals(x[0])) throw conflict("已作废销售退货单不能打印");
        if(((Number)x[1]).intValue()!=r.version()) throw conflict("销售退货单已变化，请刷新");
        long uid=userId(auth); LocalDateTime now=LocalDateTime.now();
        jdbc.update("UPDATE sales_return SET printed_by=?,printed_at=?,print_count=print_count+1,updated_by=?,updated_at=?,version=version+1 WHERE id=? AND version=?",uid,now,uid,now,id,r.version());
        return ApiResponse.success(map("printedAt",now,"printCount",((Number)x[3]).intValue()+1,"version",r.version()+1));
    }

    @PostMapping("/sales-returns/{id}/void")
    @Transactional
    public ApiResponse<Void> voidReturn(@PathVariable long id,@RequestBody VersionReason r,Authentication auth) {
        Integer linked=jdbc.queryForObject("SELECT COUNT(*) FROM return_warehouse_return WHERE sales_return_id=?",Integer.class,id);
        if(linked!=null&&linked>0) throw conflict("销售退货单已加入退货入仓单，不能作废");
        long uid=userId(auth); LocalDateTime now=LocalDateTime.now();
        int changed=jdbc.update("UPDATE sales_return SET status='VOIDED',voided_by=?,voided_at=?,void_reason=?,updated_by=?,updated_at=?,version=version+1 WHERE id=? AND status='DRAFT' AND printed_at IS NULL AND version=?",uid,now,r.reason(),uid,now,id,r.version());
        if(changed!=1) throw conflict("只有未打印、未入仓的草稿销售退货单可以作废");
        return ApiResponse.success(null);
    }

    @GetMapping("/return-warehouses")
    public ApiResponse<List<Map<String,Object>>> listWarehouses(@RequestParam(defaultValue="") String dateFrom,@RequestParam(defaultValue="") String dateTo,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="date") String sortBy,@RequestParam(defaultValue="asc") String sortDirection) {
        StringBuilder where=new StringBuilder("1=1"); List<Object> args=new ArrayList<>();
        if(!dateFrom.isBlank()){where.append(" AND w.warehouse_date>=?");args.add(LocalDate.parse(dateFrom));}
        if(!dateTo.isBlank()){where.append(" AND w.warehouse_date<=?");args.add(LocalDate.parse(dateTo));}
        if(!status.isBlank()){where.append(" AND w.status=?");args.add(status);}
        if(!keyword.isBlank()){
            String like="%"+keyword.trim()+"%";
            where.append(" AND (w.warehouse_no LIKE ? OR EXISTS (SELECT 1 FROM return_warehouse_return wr2 JOIN sales_return r2 ON r2.id=wr2.sales_return_id WHERE wr2.return_warehouse_id=w.id AND (r2.return_no LIKE ? OR r2.customer_code_snapshot LIKE ? OR r2.customer_name_snapshot LIKE ?)))");
            args.add(like);args.add(like);args.add(like);args.add(like);
        }
        String sql="SELECT w.id,w.warehouse_no,w.warehouse_date,w.status,w.version,COUNT(wr.id),COALESCE(SUM(r.total_amount),0),w.approved_at FROM return_warehouse w LEFT JOIN return_warehouse_return wr ON wr.return_warehouse_id=w.id LEFT JOIN sales_return r ON r.id=wr.sales_return_id WHERE "+where+" GROUP BY w.id ORDER BY "+DocumentSort.sql(sortBy,sortDirection,"w.warehouse_date","w.warehouse_no","w.id");
        return ApiResponse.success(jdbc.query(sql,(rs,n)->map("id",rs.getLong(1),"warehouseNo",rs.getString(2),"warehouseDate",rs.getDate(3).toLocalDate(),"status",rs.getString(4),"version",rs.getInt(5),"returnCount",rs.getInt(6),"totalAmount",rs.getBigDecimal(7),"approvedAt",rs.getTimestamp(8)==null?null:rs.getTimestamp(8).toLocalDateTime()),args.toArray()));
    }

    @GetMapping("/return-warehouses/{id}")
    public ApiResponse<Map<String,Object>> getWarehouse(@PathVariable long id) {
        var rows=jdbc.query("SELECT id,warehouse_no,warehouse_date,status,COALESCE(remark,''),version,created_at,updated_at,approved_at FROM return_warehouse WHERE id=?",(rs,n)->map("id",rs.getLong(1),"warehouseNo",rs.getString(2),"warehouseDate",rs.getDate(3).toLocalDate(),"status",rs.getString(4),"remark",rs.getString(5),"version",rs.getInt(6),"createdAt",rs.getTimestamp(7).toLocalDateTime(),"updatedAt",rs.getTimestamp(8).toLocalDateTime(),"approvedAt",rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toLocalDateTime()),id);
        if(rows.isEmpty()) throw notFound("未找到退货入仓单"); Map<String,Object> result=rows.get(0);
        result.put("returns",jdbc.query("SELECT r.id,r.return_no,r.return_date,r.customer_code_snapshot,r.customer_name_snapshot,r.total_amount,r.status FROM return_warehouse_return wr JOIN sales_return r ON r.id=wr.sales_return_id WHERE wr.return_warehouse_id=? ORDER BY wr.line_no",(rs,n)->map("id",rs.getLong(1),"returnNo",rs.getString(2),"returnDate",rs.getDate(3).toLocalDate(),"customerCode",rs.getString(4),"customerName",rs.getString(5),"totalAmount",rs.getBigDecimal(6),"status",rs.getString(7)),id));
        result.put("items",jdbc.query("SELECT r.return_no,r.return_date,COALESCE(r.salesperson_name_snapshot,''),r.customer_code_snapshot,r.customer_name_snapshot,"+
                "i.line_no,i.sku_code_snapshot,i.product_name_snapshot,COALESCE(i.specification_snapshot,''),COALESCE(i.color_snapshot,''),"+
                "i.sales_unit_snapshot,-ABS(i.quantity),i.unit_price,i.reference_price,-ABS(i.line_amount),COALESCE(r.remark,''),COALESCE(i.remark,'') " +
                "FROM return_warehouse_return wr JOIN sales_return r ON r.id=wr.sales_return_id JOIN sales_return_item i ON i.sales_return_id=r.id " +
                "WHERE wr.return_warehouse_id=? ORDER BY wr.line_no,i.line_no",(rs,n)->map(
                "businessType","销售退货","returnNo",rs.getString(1),"returnDate",rs.getDate(2).toLocalDate(),
                "salespersonName",rs.getString(3),"customerCode",rs.getString(4),"customerName",rs.getString(5),
                "lineNo",rs.getInt(6),"skuCode",rs.getString(7),"productName",rs.getString(8),
                "specification",rs.getString(9),"color",rs.getString(10),"unit",rs.getString(11),
                "quantity",rs.getBigDecimal(12),"unitPrice",rs.getBigDecimal(13),"referencePrice",rs.getBigDecimal(14),
                "lineAmount",rs.getBigDecimal(15),"actualPrice",rs.getBigDecimal(13),"actualAmount",rs.getBigDecimal(15),
                "headerRemark",rs.getString(16),"lineRemark",rs.getString(17)),id));
        return ApiResponse.success(result);
    }

    @PostMapping("/return-warehouses")
    @Transactional
    public ApiResponse<Map<String,Object>> createWarehouse(@Valid @RequestBody WarehouseRequest r,Authentication auth) {
        List<Long> ids=r.salesReturnIds().stream().distinct().toList(); if(ids.size()!=r.salesReturnIds().size()) throw bad("销售退货单不能重复选择");
        String marks=String.join(",",ids.stream().map(x->"?").toList());
        List<Long> valid=jdbc.query("SELECT r.id FROM sales_return r LEFT JOIN return_warehouse_return wr ON wr.sales_return_id=r.id WHERE r.id IN ("+marks+") AND r.status='DRAFT' AND wr.id IS NULL ORDER BY r.id",(rs,n)->rs.getLong(1),ids.toArray());
        if(valid.size()!=ids.size()) throw conflict("所选销售退货单已审核、已作废或已加入其它退货入仓单");
        long uid=userId(auth);LocalDateTime now=LocalDateTime.now();String no=nextNo("RETURN_WAREHOUSE","TJ",r.warehouseDate());
        var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO return_warehouse(warehouse_no,warehouse_date,status,remark,created_by,created_at,updated_by,updated_at) VALUES(?,?,'DRAFT',?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setString(1,no);ps.setObject(2,r.warehouseDate());ps.setString(3,r.remark());ps.setLong(4,uid);ps.setObject(5,now);ps.setLong(6,uid);ps.setObject(7,now);return ps;},key);
        long id=Objects.requireNonNull(key.getKey()).longValue();int line=1;for(Long returnId:ids)jdbc.update("INSERT INTO return_warehouse_return(return_warehouse_id,sales_return_id,line_no) VALUES(?,?,?)",id,returnId,line++);
        return ApiResponse.success(map("id",id,"warehouseNo",no,"version",0));
    }

    @PostMapping("/return-warehouses/{id}/approve")
    @Transactional
    public ApiResponse<Map<String,Object>> approveWarehouse(@PathVariable long id,@RequestBody VersionRequest r,Authentication auth) {
        var headers=jdbc.query("SELECT warehouse_no,status,version FROM return_warehouse WHERE id=? FOR UPDATE",(rs,n)->new Object[]{rs.getString(1),rs.getString(2),rs.getInt(3)},id);
        if(headers.isEmpty()) throw notFound("未找到退货入仓单"); Object[] header=headers.get(0);
        if(!"DRAFT".equals(header[1])) throw conflict("只有草稿退货入仓单可以审核"); if(((Number)header[2]).intValue()!=r.version()) throw conflict("退货入仓单已变化，请刷新");
        List<Long> returns=jdbc.query("SELECT sr.id FROM return_warehouse_return wr JOIN sales_return sr ON sr.id=wr.sales_return_id WHERE wr.return_warehouse_id=? ORDER BY wr.line_no FOR UPDATE",(rs,n)->rs.getLong(1),id);
        if(returns.isEmpty()) throw conflict("退货入仓单没有销售退货单");
        String marks=String.join(",",returns.stream().map(x->"?").toList()); Integer bad=jdbc.queryForObject("SELECT COUNT(*) FROM sales_return WHERE id IN ("+marks+") AND status<>'DRAFT'",Integer.class,returns.toArray()); if(bad!=null&&bad>0)throw conflict("关联销售退货单状态已变化，请刷新");
        List<StockLine> lines=jdbc.query("SELECT i.id,i.sku_id,i.sku_code_snapshot,i.product_name_snapshot,i.quantity FROM return_warehouse_return wr JOIN sales_return_item i ON i.sales_return_id=wr.sales_return_id WHERE wr.return_warehouse_id=? ORDER BY i.sku_id,i.id FOR UPDATE",(rs,n)->new StockLine(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5)),id);
        long uid=userId(auth); LocalDateTime now=LocalDateTime.now();
        for(StockLine line:lines){BigDecimal before=jdbc.queryForObject("SELECT total_stock FROM product_sku WHERE id=? FOR UPDATE",BigDecimal.class,line.skuId());if(before==null)throw conflict("货品不存在："+line.skuCode()+" "+line.productName());BigDecimal after=before.add(line.quantity());jdbc.update("UPDATE product_sku SET total_stock=?,version=version+1,updated_by=?,updated_at=? WHERE id=?",after,uid,now,line.skuId());jdbc.update("INSERT INTO inventory_movement(product_sku_id,movement_type,quantity_change,before_quantity,after_quantity,reason,reference_type,reference_id,reference_line_id,reference_no,created_by,created_at) VALUES(?,'SALES_RETURN',?,?,?,?, 'RETURN_WAREHOUSE',?,?,?,?,?)",line.skuId(),line.quantity(),before,after,"退货入仓单 "+header[0]+" 审核入库",id,line.itemId(),header[0],uid,now);}
        jdbc.update("UPDATE sales_return SET status='APPROVED',approved_by=?,approved_at=?,updated_by=?,updated_at=?,version=version+1 WHERE id IN ("+marks+") AND status='DRAFT'",join(uid,now,uid,now,returns));
        int changed=jdbc.update("UPDATE return_warehouse SET status='APPROVED',approved_by=?,approved_at=?,updated_by=?,updated_at=?,version=version+1 WHERE id=? AND status='DRAFT' AND version=?",uid,now,uid,now,id,r.version());if(changed!=1)throw conflict("退货入仓单状态已变化，请刷新");
        return ApiResponse.success(map("id",id,"status","APPROVED","approvedAt",now,"version",r.version()+1));
    }

    private Prepared prepare(ReturnRequest r,Long excludedId){
        var customers=jdbc.query("SELECT customer_code,short_name,settlement_method FROM customer WHERE id=? AND enabled=1",(rs,n)->new String[]{rs.getString(1),rs.getString(2),rs.getString(3)},r.customerId());if(customers.isEmpty())throw bad("客户不存在或已停用");
        if(r.sourceSalesOrderId()!=null){Integer ok=jdbc.queryForObject("SELECT COUNT(*) FROM sales_order WHERE id=? AND customer_id=? AND status<>'VOIDED'",Integer.class,r.sourceSalesOrderId(),r.customerId());if(ok==null||ok==0)throw bad("原销售单与所选客户不匹配或已作废");}
        String salesperson="";if(r.salespersonId()!=null){List<String> names=jdbc.query("SELECT employee_name FROM employee WHERE id=? AND enabled=1",(rs,n)->rs.getString(1),r.salespersonId());if(names.isEmpty())throw bad("业务员不存在或已停用");salesperson=names.get(0);}
        List<ReturnLine> lines=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;int no=1;
        for(ReturnItem item:r.items()){
            var sku=jdbc.query("SELECT sku_code,product_name,specification,color,package_spec,package_unit,sales_unit,wholesale_price FROM product_sku WHERE id=? AND enabled=1 AND saleable=1",(rs,n)->new Object[]{rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5),rs.getString(6),rs.getString(7),rs.getBigDecimal(8)},item.skuId());if(sku.isEmpty())throw bad("货品不存在、已停用或不可销售");Object[] s=sku.get(0);
            if(item.sourceSalesOrderItemId()!=null){if(r.sourceSalesOrderId()==null)throw bad("选择原销售明细时必须指定原销售单");BigDecimal sold=jdbc.queryForObject("SELECT quantity FROM sales_order_item WHERE id=? AND sales_order_id=? AND sku_id=?",BigDecimal.class,item.sourceSalesOrderItemId(),r.sourceSalesOrderId(),item.skuId());if(sold==null)throw bad("原销售明细与所选货品不匹配");String exclude=excludedId==null?"":" AND sr.id<>?";List<Object> args=new ArrayList<>(List.of(item.sourceSalesOrderItemId()));if(excludedId!=null)args.add(excludedId);BigDecimal returned=jdbc.queryForObject("SELECT COALESCE(SUM(i.quantity),0) FROM sales_return_item i JOIN sales_return sr ON sr.id=i.sales_return_id WHERE i.source_sales_order_item_id=? AND sr.status<>'VOIDED'"+exclude,BigDecimal.class,args.toArray());if(returned.add(item.quantity()).compareTo(sold)>0)throw bad("退货数量不能超过原销售数量");}
            BigDecimal price=item.unitPrice()==null?(BigDecimal)s[7]:item.unitPrice();BigDecimal amount=item.quantity().multiply(price).setScale(2,RoundingMode.HALF_UP);total=total.add(amount);
            lines.add(new ReturnLine(no++,item.sourceSalesOrderItemId(),item.skuId(),(String)s[0],(String)s[1],(String)s[2],(String)s[3],item.packageSpec()==null?(BigDecimal)s[4]:item.packageSpec(),item.packageCount(),item.packageUnit()==null?(String)s[5]:item.packageUnit(),item.quantity(),(String)s[6],price,(BigDecimal)s[7],amount,item.remark()));
        }
        return new Prepared(customers.get(0)[0],customers.get(0)[1],salesperson,r.settlementMethod()==null||r.settlementMethod().isBlank()?customers.get(0)[2]:r.settlementMethod(),total,lines);
    }

    private void insertReturnItems(long id,List<ReturnLine> lines){for(ReturnLine x:lines)jdbc.update("INSERT INTO sales_return_item(sales_return_id,line_no,source_sales_order_item_id,sku_id,sku_code_snapshot,product_name_snapshot,specification_snapshot,color_snapshot,package_spec_snapshot,package_count,package_unit_snapshot,quantity,sales_unit_snapshot,unit_price,reference_price,line_amount,remark) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,x.lineNo(),x.sourceItemId(),x.skuId(),x.skuCode(),x.productName(),x.specification(),x.color(),x.packageSpec(),x.packageCount(),x.packageUnit(),x.quantity(),x.unit(),x.unitPrice(),x.referencePrice(),x.amount(),x.remark());}
    private List<Map<String,Object>> returnItems(long id){return jdbc.query("SELECT id,line_no,source_sales_order_item_id,sku_id,sku_code_snapshot,product_name_snapshot,COALESCE(specification_snapshot,''),COALESCE(color_snapshot,''),package_spec_snapshot,package_count,COALESCE(package_unit_snapshot,''),quantity,sales_unit_snapshot,unit_price,reference_price,line_amount,COALESCE(remark,'') FROM sales_return_item WHERE sales_return_id=? ORDER BY line_no",(rs,n)->map("id",rs.getLong(1),"lineNo",rs.getInt(2),"sourceSalesOrderItemId",rs.getObject(3),"skuId",rs.getLong(4),"skuCode",rs.getString(5),"productName",rs.getString(6),"specification",rs.getString(7),"color",rs.getString(8),"packageSpec",rs.getBigDecimal(9),"packageCount",rs.getBigDecimal(10),"packageUnit",rs.getString(11),"quantity",rs.getBigDecimal(12),"unit",rs.getString(13),"unitPrice",rs.getBigDecimal(14),"referencePrice",rs.getBigDecimal(15),"lineAmount",rs.getBigDecimal(16),"remark",rs.getString(17)),id);}
    private String nextNo(String type,String prefix,LocalDate date){List<Integer> values=jdbc.query("SELECT current_value FROM number_sequence WHERE biz_type=? AND biz_date=? FOR UPDATE",(rs,n)->rs.getInt(1),type,date);int next;if(values.isEmpty()){next=1;jdbc.update("INSERT INTO number_sequence(biz_type,biz_date,current_value,version) VALUES(?,?,1,0)",type,date);}else{next=values.get(0)+1;jdbc.update("UPDATE number_sequence SET current_value=?,version=version+1 WHERE biz_type=? AND biz_date=?",next,type,date);}return prefix+date.format(DateTimeFormatter.ofPattern("yyMMdd"))+"-"+String.format("%03d",next);}
    private long userId(Authentication auth){if(auth==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);Long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,auth.getName());if(id==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return id;}
    private static Object[] join(Object a,Object b,Object c,Object d,List<Long> ids){List<Object>x=new ArrayList<>(List.of(a,b,c,d));x.addAll(ids);return x.toArray();}
    private static Map<String,Object> map(Object... kv){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return m;}
    private static ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private static ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
    private static ResponseStatusException notFound(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);}

    public record ReturnRequest(@NotNull LocalDate returnDate,Long sourceSalesOrderId,@NotNull Long customerId,Long salespersonId,String settlementMethod,String remark,@NotEmpty List<@Valid ReturnItem> items){}
    public record UpdateReturnRequest(@NotNull Integer version,@NotNull @Valid ReturnRequest data){}
    public record ReturnItem(Long sourceSalesOrderItemId,@NotNull Long skuId,BigDecimal packageSpec,BigDecimal packageCount,String packageUnit,@NotNull @DecimalMin("0.0001") BigDecimal quantity,@DecimalMin("0.00") BigDecimal unitPrice,String remark){}
    public record WarehouseRequest(@NotNull LocalDate warehouseDate,String remark,@NotEmpty List<Long> salesReturnIds){}
    public record VersionRequest(@NotNull Integer version){}
    public record VersionReason(@NotNull Integer version,String reason){}
    private record Prepared(String customerCode,String customerName,String salespersonName,String settlement,BigDecimal total,List<ReturnLine> items){}
    private record ReturnLine(int lineNo,Long sourceItemId,long skuId,String skuCode,String productName,String specification,String color,BigDecimal packageSpec,BigDecimal packageCount,String packageUnit,BigDecimal quantity,String unit,BigDecimal unitPrice,BigDecimal referencePrice,BigDecimal amount,String remark){}
    private record StockLine(long itemId,long skuId,String skuCode,String productName,BigDecimal quantity){}
}
