package com.sunny.paintfactory.dispatch;

import com.sunny.paintfactory.common.ApiResponse;
import com.sunny.paintfactory.common.PageResult;
import com.sunny.paintfactory.common.DocumentSort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/dispatch-sheets")
public class DispatchSheetController {
    private final JdbcTemplate jdbc;

    public DispatchSheetController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public ApiResponse<PageResult<Map<String,Object>>> list(
        @RequestParam(defaultValue="") String keyword,
        @RequestParam(defaultValue="") String status,
        @RequestParam(defaultValue="") String dateFrom,
        @RequestParam(defaultValue="") String dateTo,
        @RequestParam(defaultValue="") String recorder,
        @RequestParam(defaultValue="date") String sortBy,
        @RequestParam(defaultValue="asc") String sortDirection,
        @RequestParam(defaultValue="1") int page,
        @RequestParam(defaultValue="20") int pageSize) {
        int safePage=Math.max(1,page), safeSize=Math.min(100,Math.max(1,pageSize));
        String normalizedKeyword=keyword.trim(),like="%"+normalizedKeyword+"%",prefixLike=normalizedKeyword+"%";
        StringBuilder where=new StringBuilder("(d.dispatch_no LIKE ? OR COALESCE(d.route_name_snapshot,'') LIKE ? OR COALESCE(d.vehicle_code_snapshot,'') LIKE ? OR COALESCE(d.driver_name_snapshot,'') LIKE ? OR COALESCE(d.delivery_person_name_snapshot,'') LIKE ?)");
        List<Object> args=new ArrayList<>(List.of(like,like,like,like,like));
        if(!status.isBlank()){where.append(" AND d.status=?");args.add(status);}
        if(!dateFrom.isBlank()){where.append(" AND d.dispatch_date>=?");args.add(LocalDate.parse(dateFrom));}
        if(!dateTo.isBlank()){where.append(" AND d.dispatch_date<=?");args.add(LocalDate.parse(dateTo));}
        if(!recorder.isBlank()){where.append(" AND EXISTS(SELECT 1 FROM sys_user ru WHERE ru.id=d.created_by AND (ru.username LIKE ? OR ru.display_name LIKE ?))");args.add("%"+recorder.trim()+"%");args.add("%"+recorder.trim()+"%");}
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_sheet d WHERE "+where,Long.class,args.toArray());
        List<Object> params=new ArrayList<>(args); java.util.Collections.addAll(params,normalizedKeyword,normalizedKeyword,prefixLike,normalizedKeyword,prefixLike,safeSize,(safePage-1)*safeSize);
        String relevanceOrder=" CASE WHEN ?='' THEN 0 WHEN d.dispatch_no=? THEN 0 WHEN d.dispatch_no LIKE ? THEN 1 WHEN COALESCE(d.route_name_snapshot,'')=? THEN 2 WHEN COALESCE(d.route_name_snapshot,'') LIKE ? THEN 3 ELSE 4 END, "+DocumentSort.sql(sortBy,sortDirection,"d.dispatch_date","d.dispatch_no","d.id");
        var items=jdbc.query("SELECT d.id,d.dispatch_no,d.dispatch_date,COALESCE(d.route_name_snapshot,''),COALESCE(d.vehicle_code_snapshot,''),COALESCE(d.driver_name_snapshot,''),COALESCE(d.delivery_person_name_snapshot,''),d.status,d.version,d.created_at,COUNT(o.id),COALESCE(SUM(o.amount_snapshot),0),d.printed_at,d.print_count FROM dispatch_sheet d LEFT JOIN dispatch_sales_order o ON o.dispatch_sheet_id=d.id AND o.is_active=1 WHERE "+where+" GROUP BY d.id ORDER BY"+relevanceOrder+" LIMIT ? OFFSET ?",
            (rs,n)->map("id",rs.getLong(1),"dispatchNo",rs.getString(2),"dispatchDate",rs.getDate(3)==null?null:rs.getDate(3).toLocalDate(),"routeName",rs.getString(4),"vehicleCode",rs.getString(5),"driverName",rs.getString(6),"deliveryPersonName",rs.getString(7),"status",rs.getString(8),"version",rs.getInt(9),"createdAt",rs.getTimestamp(10).toLocalDateTime(),"orderCount",rs.getInt(11),"totalAmount",rs.getBigDecimal(12),"printedAt",rs.getTimestamp(13)==null?null:rs.getTimestamp(13).toLocalDateTime(),"printCount",rs.getInt(14)),params.toArray());
        return ApiResponse.success(new PageResult<>(items,total==null?0:total,safePage,safeSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String,Object>> detail(@PathVariable long id) {
        var headers=jdbc.query("SELECT d.id,d.dispatch_no,d.dispatch_date,d.route_id,COALESCE(d.route_name_snapshot,''),d.vehicle_id,COALESCE(d.vehicle_code_snapshot,''),d.driver_id,COALESCE(d.driver_name_snapshot,''),d.delivery_person_id,COALESCE(d.delivery_person_name_snapshot,''),d.status,COALESCE(d.remark,''),d.version,d.created_at,d.updated_at,d.approved_at,d.printed_at,d.print_count,cu.display_name,uu.display_name,au.display_name,pu.display_name FROM dispatch_sheet d JOIN sys_user cu ON cu.id=d.created_by JOIN sys_user uu ON uu.id=d.updated_by LEFT JOIN sys_user au ON au.id=d.approved_by LEFT JOIN sys_user pu ON pu.id=d.printed_by WHERE d.id=?",
            (rs,n)->map("id",rs.getLong(1),"dispatchNo",rs.getString(2),"dispatchDate",rs.getDate(3)==null?null:rs.getDate(3).toLocalDate(),"routeId",nullableLong(rs,4),"routeName",rs.getString(5),"vehicleId",nullableLong(rs,6),"vehicleCode",rs.getString(7),"driverId",nullableLong(rs,8),"driverName",rs.getString(9),"deliveryPersonId",nullableLong(rs,10),"deliveryPersonName",rs.getString(11),"status",rs.getString(12),"remark",rs.getString(13),"version",rs.getInt(14),"createdAt",rs.getTimestamp(15).toLocalDateTime(),"updatedAt",rs.getTimestamp(16).toLocalDateTime(),"approvedAt",rs.getTimestamp(17)==null?null:rs.getTimestamp(17).toLocalDateTime(),"printedAt",rs.getTimestamp(18)==null?null:rs.getTimestamp(18).toLocalDateTime(),"printCount",rs.getInt(19),"createdByName",rs.getString(20),"updatedByName",rs.getString(21),"approvedByName",rs.getString(22),"printedByName",rs.getString(23)),id);
        if(headers.isEmpty()) throw notFound("Dispatch sheet not found");
        var orders=jdbc.query("SELECT o.id,o.sequence_no,s.id,s.order_no,s.order_date,o.customer_code_snapshot,o.customer_name_snapshot,COALESCE(s.salesperson_name_snapshot,''),o.settlement_method_snapshot,o.amount_snapshot FROM dispatch_sales_order o JOIN sales_order s ON s.id=o.sales_order_id WHERE o.dispatch_sheet_id=? AND o.is_active=1 ORDER BY o.sequence_no",
            (rs,n)->map("linkId",rs.getLong(1),"sequenceNo",rs.getInt(2),"salesOrderId",rs.getLong(3),"orderNo",rs.getString(4),"orderDate",rs.getDate(5).toLocalDate(),"customerCode",rs.getString(6),"customerName",rs.getString(7),"salespersonName",rs.getString(8),"settlementMethod",rs.getString(9),"amount",rs.getBigDecimal(10)),id);
        Map<String,Object> result=new LinkedHashMap<>(headers.get(0)); result.put("orders",orders);
        return ApiResponse.success(result);
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String,Object>> create(@RequestBody HeaderRequest request,Authentication auth) {
        long uid=userId(auth); LocalDate numberingDate=request.dispatchDate()==null?LocalDate.now():request.dispatchDate();
        String dispatchNo=nextNo(numberingDate); LocalDateTime now=LocalDateTime.now();
        String route=snapshot("route",request.routeId(),"route_name");
        String vehicle=snapshot("vehicle",request.vehicleId(),"vehicle_code");
        String driver=snapshot("employee",request.driverId(),"employee_name");
        String delivery=snapshot("employee",request.deliveryPersonId(),"employee_name");
        jdbc.update("INSERT INTO dispatch_sheet(dispatch_no,dispatch_date,route_id,route_name_snapshot,vehicle_id,vehicle_code_snapshot,driver_id,driver_name_snapshot,delivery_person_id,delivery_person_name_snapshot,status,remark,version,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,'DRAFT',?,0,?,?,?,?)",
            dispatchNo,request.dispatchDate(),request.routeId(),route,request.vehicleId(),vehicle,request.driverId(),driver,request.deliveryPersonId(),delivery,request.remark(),uid,now,uid,now);
        Long id=jdbc.queryForObject("SELECT id FROM dispatch_sheet WHERE dispatch_no=?",Long.class,dispatchNo);
        return ApiResponse.success(detail(id).data());
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<Void> updateHeader(@PathVariable long id,@Valid @RequestBody HeaderRequest request,Authentication auth) {
        String route=snapshot("route",request.routeId(),"route_name");
        String vehicle=snapshot("vehicle",request.vehicleId(),"vehicle_code");
        String driver=snapshot("employee",request.driverId(),"employee_name");
        String delivery=snapshot("employee",request.deliveryPersonId(),"employee_name");
        int changed=jdbc.update("UPDATE dispatch_sheet SET dispatch_date=?,route_id=?,route_name_snapshot=?,vehicle_id=?,vehicle_code_snapshot=?,driver_id=?,driver_name_snapshot=?,delivery_person_id=?,delivery_person_name_snapshot=?,remark=?,version=version+1,updated_by=?,updated_at=? WHERE id=? AND status='DRAFT' AND version=?",
            request.dispatchDate(),request.routeId(),route,request.vehicleId(),vehicle,request.driverId(),driver,request.deliveryPersonId(),delivery,request.remark(),userId(auth),LocalDateTime.now(),id,request.version());
        if(changed!=1) throw conflict("The dispatch sheet is approved or was changed by another user. Refresh and try again");
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/orders:resolve")
    @Transactional
    public ApiResponse<Map<String,Object>> resolve(@PathVariable long id,@Valid @RequestBody OrderNoRequest request) {
        requireDraft(id); return ApiResponse.success(resolveOrder(id,request.orderNo().trim().toUpperCase()));
    }

    @GetMapping("/{id}/available-orders")
    public ApiResponse<List<Map<String,Object>>> availableOrders(@PathVariable long id,@RequestParam(defaultValue="") String keyword) {
        requireDraft(id);
        String normalizedKeyword=keyword.trim(),like="%"+normalizedKeyword+"%",prefixLike=normalizedKeyword+"%";
        var orders=jdbc.query("SELECT s.order_no,s.order_date,s.customer_code_snapshot,s.customer_name_snapshot,COALESCE(s.salesperson_name_snapshot,''),s.settlement_method,s.total_amount FROM sales_order s WHERE s.status<>'VOIDED' AND s.printed_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dispatch_sales_order dso JOIN dispatch_sheet ds ON ds.id=dso.dispatch_sheet_id WHERE dso.sales_order_id=s.id AND dso.is_active=1 AND ds.status<>'VOIDED') AND (s.order_no LIKE ? OR s.customer_code_snapshot LIKE ? OR s.customer_name_snapshot LIKE ?) ORDER BY CASE WHEN ?='' THEN 0 WHEN s.order_no=? THEN 0 WHEN s.order_no LIKE ? THEN 1 WHEN s.customer_code_snapshot=? THEN 2 WHEN s.customer_code_snapshot LIKE ? THEN 3 ELSE 4 END,s.order_date,s.created_at,s.order_no",
            (rs,n)->map("orderNo",rs.getString(1),"orderDate",rs.getDate(2).toLocalDate(),"customerCode",rs.getString(3),"customerName",rs.getString(4),"salespersonName",rs.getString(5),"settlementMethod",rs.getString(6),"totalAmount",rs.getBigDecimal(7)),like,like,like,normalizedKeyword,normalizedKeyword,prefixLike,normalizedKeyword,prefixLike);
        return ApiResponse.success(orders);
    }

    @GetMapping("/{id}/order-tail-candidates")
    public ApiResponse<List<Map<String,Object>>> orderTailCandidates(@PathVariable long id,@RequestParam String tail) {
        requireDraft(id);
        String normalizedTail=normalizeOrderTail(tail);
        var orders=jdbc.query("SELECT s.order_no,s.order_date,s.customer_code_snapshot,s.customer_name_snapshot,COALESCE(s.salesperson_name_snapshot,''),s.settlement_method,s.total_amount FROM sales_order s WHERE s.status<>'VOIDED' AND s.printed_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dispatch_sales_order dso JOIN dispatch_sheet ds ON ds.id=dso.dispatch_sheet_id WHERE dso.sales_order_id=s.id AND dso.is_active=1 AND ds.status<>'VOIDED') AND CAST(SUBSTRING_INDEX(s.order_no,'-',-1) AS UNSIGNED)=CAST(? AS UNSIGNED) ORDER BY s.order_date,s.created_at,s.order_no",
            (rs,n)->map("orderNo",rs.getString(1),"orderDate",rs.getDate(2).toLocalDate(),"customerCode",rs.getString(3),"customerName",rs.getString(4),"salespersonName",rs.getString(5),"settlementMethod",rs.getString(6),"totalAmount",rs.getBigDecimal(7)),normalizedTail);
        return ApiResponse.success(orders);
    }

    @PostMapping("/{id}/orders")
    @Transactional
    public ApiResponse<Map<String,Object>> addOrder(@PathVariable long id,@Valid @RequestBody OrderNoRequest request,Authentication auth) {
        requireDraft(id); String orderNo=request.orderNo().trim().toUpperCase(); Map<String,Object> order=resolveOrder(id,orderNo);
        Integer sequence=jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM dispatch_sales_order WHERE dispatch_sheet_id=? AND is_active=1",Integer.class,id);
        long uid=userId(auth); insertOrderLink(id,orderNo,order,sequence,uid); touch(id,uid); return ApiResponse.success(order);
    }

    @DeleteMapping("/{id}/empty-draft")
    @Transactional
    public ApiResponse<Map<String,Object>> deleteEmptyDraft(@PathVariable long id) {
        List<Long> empty=jdbc.query("SELECT d.id FROM dispatch_sheet d WHERE d.id=? AND d.status='DRAFT' AND d.route_id IS NULL AND d.vehicle_id IS NULL AND d.driver_id IS NULL AND d.delivery_person_id IS NULL AND COALESCE(TRIM(d.remark),'')='' AND NOT EXISTS(SELECT 1 FROM dispatch_sales_order o WHERE o.dispatch_sheet_id=d.id AND o.is_active=1) FOR UPDATE",(rs,n)->rs.getLong(1),id);
        if(empty.isEmpty())return ApiResponse.success(Map.of("deleted",false));
        jdbc.update("DELETE FROM dispatch_sales_order WHERE dispatch_sheet_id=?",id);
        int deleted=jdbc.update("DELETE FROM dispatch_sheet WHERE id=? AND status='DRAFT'",id);
        return ApiResponse.success(Map.of("deleted",deleted==1));
    }

    @DeleteMapping("/{id}/draft")
    @Transactional
    public ApiResponse<Map<String,Object>> deleteDraft(@PathVariable long id) {
        requireDraft(id);
        jdbc.update("DELETE FROM dispatch_sales_order WHERE dispatch_sheet_id=?",id);
        int deleted=jdbc.update("DELETE FROM dispatch_sheet WHERE id=? AND status='DRAFT'",id);
        return ApiResponse.success(Map.of("deleted",deleted==1));
    }

    @PostMapping("/{id}/orders:batch")
    @Transactional
    public ApiResponse<Map<String,Object>> addOrders(@PathVariable long id,@Valid @RequestBody BatchOrderNoRequest request,Authentication auth) {
        requireDraft(id);
        List<String> orderNos=request.orderNos().stream().map(value->value.trim().toUpperCase()).distinct().toList();
        if(orderNos.size()!=request.orderNos().size()) throw bad("Do not select the same sales order more than once");
        List<Map<String,Object>> orders=orderNos.stream().map(orderNo->resolveOrder(id,orderNo)).toList();
        Integer sequence=jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM dispatch_sales_order WHERE dispatch_sheet_id=? AND is_active=1",Integer.class,id);
        long uid=userId(auth); LocalDateTime now=LocalDateTime.now();
        for(int i=0;i<orders.size();i++) insertOrderLink(id,orderNos.get(i),orders.get(i),sequence+i,uid,now);
        touch(id,uid);
        BigDecimal amount=orders.stream().map(order->(BigDecimal)order.get("totalAmount")).reduce(BigDecimal.ZERO,BigDecimal::add);
        return ApiResponse.success(map("addedCount",orders.size(),"totalAmount",amount));
    }

    @DeleteMapping("/{id}/orders/{linkId}")
    @Transactional
    public ApiResponse<Void> removeOrder(@PathVariable long id,@PathVariable long linkId,Authentication auth) {
        requireDraft(id);
        int changed=jdbc.update("UPDATE dispatch_sales_order SET is_active=0 WHERE id=? AND dispatch_sheet_id=? AND is_active=1",linkId,id);
        if(changed!=1) throw notFound("The sales-order link to remove was not found");
        touch(id,userId(auth)); return ApiResponse.success(null);
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public ApiResponse<Void> approve(@PathVariable long id,@Valid @RequestBody VersionRequest request,Authentication auth) {
        var headers=jdbc.query("SELECT dispatch_date,route_id,vehicle_id,driver_id FROM dispatch_sheet WHERE id=? AND status='DRAFT'",(rs,n)->map("dispatchDate",rs.getDate(1)==null?null:rs.getDate(1).toLocalDate(),"routeId",nullableLong(rs,2),"vehicleId",nullableLong(rs,3),"driverId",nullableLong(rs,4)),id);
        if(headers.isEmpty()) throw conflict("An approved or voided dispatch sheet cannot be approved again");
        Map<String,Object> header=headers.get(0);
        if(header.get("dispatchDate")==null) throw bad("Enter a date before approval");
        Integer orderCount=jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_sales_order WHERE dispatch_sheet_id=? AND is_active=1",Integer.class,id);
        validateHasOrders(orderCount!=null?orderCount:0);
        long uid=userId(auth); LocalDateTime now=LocalDateTime.now();
        int changed=jdbc.update("UPDATE dispatch_sheet SET status='APPROVED',approved_by=?,approved_at=?,updated_by=?,updated_at=?,version=version+1 WHERE id=? AND status='DRAFT' AND version=?",uid,now,uid,now,id,request.version());
        if(changed!=1) throw conflict("The dispatch sheet is approved or has changed. Refresh and try again");
        return ApiResponse.success(null);
    }

    static void validateHasOrders(int orderCount) {
        if(orderCount<=0) throw bad("A dispatch sheet without sales orders cannot be approved");
    }

    @PostMapping("/{id}/confirm-print")
    @Transactional
    public ApiResponse<Map<String,Object>> confirmPrint(@PathVariable long id,@Valid @RequestBody VersionRequest request,Authentication auth) {
        var rows=jdbc.query("SELECT status,version,printed_at,print_count FROM dispatch_sheet WHERE id=? FOR UPDATE",(rs,n)->map("status",rs.getString(1),"version",rs.getInt(2),"printedAt",rs.getTimestamp(3),"printCount",rs.getInt(4)),id);
        if(rows.isEmpty())throw notFound("Dispatch sheet not found");Map<String,Object> sheet=rows.get(0);if(!"APPROVED".equals(sheet.get("status")))throw conflict("Only a supervisor-approved dispatch sheet can be confirmed for printing");if(((Number)sheet.get("version")).intValue()!=request.version())throw conflict("The dispatch-sheet status has changed. Refresh and try again");long uid=userId(auth);LocalDateTime now=LocalDateTime.now();boolean first=sheet.get("printedAt")==null;int changed=first?jdbc.update("UPDATE dispatch_sheet SET printed_by=?,printed_at=?,print_count=1,updated_by=?,updated_at=?,version=version+1 WHERE id=? AND status='APPROVED' AND printed_at IS NULL AND version=?",uid,now,uid,now,id,request.version()):jdbc.update("UPDATE dispatch_sheet SET printed_by=?,printed_at=?,print_count=print_count+1,updated_by=?,updated_at=?,version=version+1 WHERE id=? AND status='APPROVED' AND version=?",uid,now,uid,now,id,request.version());if(changed!=1)throw conflict("The dispatch-sheet status has changed. Refresh and try again");return ApiResponse.success(map("firstPrint",first,"printedAt",now,"printCount",((Number)sheet.get("printCount")).intValue()+1));
    }

    @GetMapping("/{id}/complete-preview")
    public ApiResponse<Map<String,Object>> completePreview(@PathVariable long id) {
        throw dispatchStockMutationDisabled();
    }

    @PostMapping("/{id}/complete")
    @Transactional
    public ApiResponse<Map<String,Object>> complete(@PathVariable long id,@Valid @RequestBody VersionRequest request,Authentication auth) {
        throw dispatchStockMutationDisabled();
    }

    @PostMapping("/{id}/reverse")
    @Transactional
    public ApiResponse<Map<String,Object>> reverse(@PathVariable long id,@Valid @RequestBody ReverseRequest request,Authentication auth){
        throw dispatchStockMutationDisabled();
    }

    @PostMapping("/{id}/void")
    @Transactional
    public ApiResponse<Void> voidSheet(@PathVariable long id,@Valid @RequestBody VoidRequest request,Authentication auth) {
        String status=jdbc.query("SELECT status FROM dispatch_sheet WHERE id=? FOR UPDATE",rs->{if(!rs.next())throw notFound("Dispatch sheet not found");return rs.getString(1);},id);
        if(!"DRAFT".equals(status)) throw conflict("An approved, completed, or voided dispatch sheet cannot be changed");
        long uid=userId(auth); LocalDateTime now=LocalDateTime.now();
        int changed=jdbc.update("UPDATE dispatch_sheet SET status='VOIDED',voided_by=?,voided_at=?,void_reason=?,updated_by=?,updated_at=?,version=version+1 WHERE id=? AND status='DRAFT' AND version=?",uid,now,request.reason().trim(),uid,now,id,request.version());
        if(changed!=1) throw conflict("The dispatch-sheet status has changed. Refresh and try again");
        return ApiResponse.success(null);
    }

    private Map<String,Object> resolveOrder(long sheetId,String orderNo) {
        if(!orderNo.matches("XS\\d{6}-\\d{3,}")) throw bad("Invalid sales-order number format; expected a value such as XS260810-001");
        var rows=jdbc.query("SELECT id,order_no,order_date,customer_code_snapshot,customer_name_snapshot,COALESCE(salesperson_name_snapshot,''),settlement_method,total_amount,status,printed_at FROM sales_order WHERE order_no=? FOR UPDATE",
            (rs,n)->map("salesOrderId",rs.getLong(1),"orderNo",rs.getString(2),"orderDate",rs.getDate(3).toLocalDate(),"customerCode",rs.getString(4),"customerName",rs.getString(5),"salespersonName",rs.getString(6),"settlementMethod",rs.getString(7),"totalAmount",rs.getBigDecimal(8),"status",rs.getString(9),"printedAt",rs.getTimestamp(10)),orderNo);
        if(rows.isEmpty()) throw notFound("Sales order “"+orderNo+"” was not found. Check the number and try again");
        Map<String,Object> order=rows.get(0); String status=Objects.toString(order.get("status"));
        validateOrderEligibility(status,(java.sql.Timestamp)order.get("printedAt"),orderNo);
        if("VOIDED".equals(status)) throw bad("Voided sales order “"+orderNo+"” cannot be added to a dispatch sheet");
        if(order.get("printedAt")==null) throw bad("Sales order “"+orderNo+"” cannot be added until its delivery note has been printed");
        var occupied=jdbc.query("SELECT d.id,d.dispatch_no,d.status FROM dispatch_sales_order o JOIN dispatch_sheet d ON d.id=o.dispatch_sheet_id WHERE o.sales_order_id=? AND o.is_active=1",
            (rs,n)->map("sheetId",rs.getLong(1),"dispatchNo",rs.getString(2),"status",rs.getString(3)),order.get("salesOrderId")).stream()
            .filter(item->dispatchOccupiesSalesOrder(Objects.toString(item.get("status")))).toList();
        if(!occupied.isEmpty()) {
            var existing=occupied.get(0);
            if(((Number)existing.get("sheetId")).longValue()==sheetId) throw conflict("Sales order “"+orderNo+"” is already on this dispatch sheet");
            throw conflict("Sales order “"+orderNo+"” is already on dispatch sheet “"+existing.get("dispatchNo")+"” ("+dispatchStatusText(Objects.toString(existing.get("status")))+") and cannot be added again");
        }
        order.put("eligible",true); return order;
    }

    private void insertOrderLink(long sheetId,String orderNo,Map<String,Object> order,int sequence,long uid){insertOrderLink(sheetId,orderNo,order,sequence,uid,LocalDateTime.now());}
    private void insertOrderLink(long sheetId,String orderNo,Map<String,Object> order,int sequence,long uid,LocalDateTime now) {
        int restored=jdbc.update("UPDATE dispatch_sales_order SET sequence_no=?,entered_order_no=?,customer_code_snapshot=?,customer_name_snapshot=?,settlement_method_snapshot=?,amount_snapshot=?,is_active=1,created_by=?,created_at=? WHERE dispatch_sheet_id=? AND sales_order_id=? AND is_active=0",
            sequence,orderNo,order.get("customerCode"),order.get("customerName"),order.get("settlementMethod"),order.get("totalAmount"),uid,now,sheetId,order.get("salesOrderId"));
        if(restored==1) return;
        try {
            jdbc.update("INSERT INTO dispatch_sales_order(dispatch_sheet_id,sales_order_id,sequence_no,entered_order_no,customer_code_snapshot,customer_name_snapshot,settlement_method_snapshot,amount_snapshot,is_active,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,1,?,?)",
                sheetId,order.get("salesOrderId"),sequence,orderNo,order.get("customerCode"),order.get("customerName"),order.get("settlementMethod"),order.get("totalAmount"),uid,now);
        } catch(DataIntegrityViolationException ex) {
            throw conflict("Sales order “"+orderNo+"” was just assigned to another dispatch sheet. Refresh and try again");
        }
    }

    private void requireDraft(long id) {
        List<String> rows=jdbc.query("SELECT status FROM dispatch_sheet WHERE id=?",(rs,n)->rs.getString(1),id);
        if(rows.isEmpty()) throw notFound("Dispatch sheet not found");
        if(!"DRAFT".equals(rows.get(0))) throw conflict("An approved or voided dispatch sheet cannot be edited");
    }

    private String snapshot(String table,Long id,String nameColumn) {
        if(id==null) return null;
        List<String> rows=jdbc.query("SELECT "+nameColumn+" FROM "+table+" WHERE id=? AND enabled=1",(rs,n)->rs.getString(1),id);
        if(rows.isEmpty()) throw bad("The selected dispatch reference record does not exist or is disabled"); return rows.get(0);
    }

    private String nextNo(LocalDate date) {
        List<Integer> values=jdbc.query("SELECT current_value FROM number_sequence WHERE biz_type='DISPATCH_SHEET' AND biz_date=? FOR UPDATE",(rs,n)->rs.getInt(1),date);
        int next;
        if(values.isEmpty()){next=1;jdbc.update("INSERT INTO number_sequence(biz_type,biz_date,current_value,version) VALUES('DISPATCH_SHEET',?,1,0)",date);}
        else{next=values.get(0)+1;jdbc.update("UPDATE number_sequence SET current_value=?,version=version+1 WHERE biz_type='DISPATCH_SHEET' AND biz_date=?",next,date);}
        return "CB"+date.format(DateTimeFormatter.ofPattern("yyMMdd"))+"-"+String.format("%03d",next);
    }

    private void touch(long id,long uid){jdbc.update("UPDATE dispatch_sheet SET version=version+1,updated_by=?,updated_at=? WHERE id=?",uid,LocalDateTime.now(),id);}
    private long userId(Authentication auth){Long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,auth.getName());if(id==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return id;}
    private static Map<String,Object> map(Object... values){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);return result;}
    private static Long nullableLong(java.sql.ResultSet rs,int index)throws java.sql.SQLException{long value=rs.getLong(index);return rs.wasNull()?null:value;}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);}
    private static ResponseStatusException notFound(String message){return new ResponseStatusException(HttpStatus.NOT_FOUND,message);}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    static String dispatchStatusText(String status){return switch(status){case "DRAFT"->"Draft";case "APPROVED"->"Approved";case "VOIDED"->"Voided";case "COMPLETED"->"Historical Completed";case "REVERSED"->"Historical Reversed";default->"Unknown Status";};}
    static boolean dispatchOccupiesSalesOrder(String status){return !"VOIDED".equals(status);}

    static ResponseStatusException dispatchStockMutationDisabled(){throw conflict("Inventory is deducted only when a sales-order delivery note is confirmed for the first time; approving, completing, or printing a dispatch sheet does not change inventory");}

    static void validateOrderEligibility(String status,java.sql.Timestamp printedAt,String orderNo){
        if("VOIDED".equals(status))throw bad("Voided sales order “"+orderNo+"” cannot be added to a dispatch sheet");
        if(printedAt==null)throw bad("Sales order “"+orderNo+"” cannot be added until its delivery note has been printed");
    }

    static String normalizeOrderTail(String tail){
        String value=tail==null?"":tail.trim();
        if(!value.matches("\\d+"))throw bad("Enter the digits at the end of the sales-order number, such as 3, 03, or 003");
        String normalized=value.replaceFirst("^0+(?!$)","");
        return normalized;
    }

    public record HeaderRequest(@NotNull LocalDate dispatchDate,Long routeId,Long vehicleId,Long driverId,Long deliveryPersonId,String remark,Integer version){}
    public record OrderNoRequest(@NotBlank String orderNo){}
    public record BatchOrderNoRequest(@NotEmpty List<@NotBlank String> orderNos){}
    public record VersionRequest(@NotNull Integer version){}
    public record VoidRequest(@NotNull Integer version,@NotBlank String reason){}
    public record ReverseRequest(@NotNull Integer version,@NotBlank String reason){}
}
