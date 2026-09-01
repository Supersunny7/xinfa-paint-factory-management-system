package com.sunny.paintfactory.ledger;

import com.sunny.paintfactory.common.ApiResponse;
import com.sunny.paintfactory.common.DocumentSort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ledgers/cashflow")
@PreAuthorize("hasRole('ADMIN')")
public class CashflowLedgerController {
    private final JdbcTemplate jdbc;
    public CashflowLedgerController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
        @RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo,
        @RequestParam(defaultValue = "") String documentNo,
        @RequestParam(defaultValue = "") String handler,
        @RequestParam(defaultValue = "") String category,
        @RequestParam(defaultValue = "") String printStatus,
        @RequestParam(defaultValue = "") String auditStatus,
        @RequestParam(defaultValue = "") String voidStatus,
        @RequestParam(defaultValue = "date") String sortBy,
        @RequestParam(defaultValue = "asc") String sortDirection) {
        if (dateTo.isBefore(dateFrom)) throw new IllegalArgumentException("结束日期不能早于开始日期");
        StringBuilder where = new StringBuilder(" WHERE e.expense_date>=? AND e.expense_date<=?");
        List<Object> args = new ArrayList<>(List.of(dateFrom, dateTo));
        if (!documentNo.isBlank()) { where.append(" AND e.expense_no LIKE ?"); args.add("%" + documentNo.trim() + "%"); }
        if (!handler.isBlank()) { where.append(" AND COALESCE(e.handler_name_snapshot,'') LIKE ?"); args.add("%" + handler.trim() + "%"); }
        if (!category.isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM other_expense_item ei WHERE ei.other_expense_id=e.id AND (ei.category_code LIKE ? OR ei.category_name_snapshot LIKE ?))");
            String like = "%" + category.trim() + "%"; args.add(like); args.add(like);
        }
        if ("PRINTED".equals(printStatus)) where.append(" AND e.printed_at IS NOT NULL");
        if ("UNPRINTED".equals(printStatus)) where.append(" AND e.printed_at IS NULL");
        if ("APPROVED".equals(auditStatus)) where.append(" AND e.status='APPROVED'");
        if ("UNAPPROVED".equals(auditStatus)) where.append(" AND e.status='DRAFT'");
        if ("ACTIVE".equals(voidStatus)) where.append(" AND e.status<>'VOIDED'");
        if ("VOIDED".equals(voidStatus)) where.append(" AND e.status='VOIDED'");

        String sql = """
            SELECT e.id,e.expense_no,e.expense_date,e.account_name,COALESCE(e.handler_name_snapshot,''),
                   e.total_amount,e.status,COALESCE(e.remark,''),e.created_at,cu.display_name,
                   e.updated_at,uu.display_name,e.approved_at,au.display_name,e.printed_at,
                   pu.display_name,e.print_count,e.voided_at,COALESCE(e.void_reason,''),vu.display_name
              FROM other_expense e
              JOIN sys_user cu ON cu.id=e.created_by JOIN sys_user uu ON uu.id=e.updated_by
              LEFT JOIN sys_user au ON au.id=e.approved_by LEFT JOIN sys_user pu ON pu.id=e.printed_by
              LEFT JOIN sys_user vu ON vu.id=e.voided_by
            """ + where + " ORDER BY "+DocumentSort.sql(sortBy,sortDirection,"e.expense_date","e.expense_no","e.id");
        List<Map<String, Object>> items = jdbc.query(sql, (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",rs.getLong(1)); row.put("documentNo",rs.getString(2)); row.put("documentDate",rs.getObject(3,LocalDate.class));
            row.put("accountName",rs.getString(4)); row.put("handlerName",rs.getString(5)); row.put("expenseAmount",rs.getBigDecimal(6));
            row.put("status",rs.getString(7)); row.put("remark",rs.getString(8)); row.put("createdAt",rs.getObject(9,LocalDateTime.class));
            row.put("createdByName",rs.getString(10)); row.put("updatedAt",rs.getObject(11,LocalDateTime.class)); row.put("updatedByName",rs.getString(12));
            row.put("approvedAt",rs.getObject(13,LocalDateTime.class)); row.put("approvedByName",rs.getString(14)); row.put("printedAt",rs.getObject(15,LocalDateTime.class));
            row.put("printedByName",rs.getString(16)); row.put("printCount",rs.getInt(17)); row.put("voidedAt",rs.getObject(18,LocalDateTime.class));
            row.put("voidReason",rs.getString(19)); row.put("voidedByName",rs.getString(20)); return row;
        }, args.toArray());
        BigDecimal effective = items.stream().filter(x -> !"VOIDED".equals(x.get("status")))
            .map(x -> (BigDecimal)x.get("expenseAmount")).reduce(BigDecimal.ZERO,BigDecimal::add);
        long voided = items.stream().filter(x -> "VOIDED".equals(x.get("status"))).count();
        return ApiResponse.success(Map.of("items",items,"summary",Map.of(
            "documentCount",items.size(),"validDocumentCount",items.size()-voided,
            "voidedDocumentCount",voided,"effectiveExpenseAmount",effective)));
    }

    @GetMapping("/{id}/lines")
    public ApiResponse<List<Map<String, Object>>> lines(@PathVariable long id) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM other_expense WHERE id=?",Integer.class,id);
        if(count==null||count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"未找到其它支出单");
        return ApiResponse.success(jdbc.query("SELECT line_no,category_code,category_name_snapshot,COALESCE(summary,''),amount FROM other_expense_item WHERE other_expense_id=? ORDER BY line_no",(rs,n)->{
            Map<String,Object> row=new LinkedHashMap<>();row.put("lineNo",rs.getInt(1));row.put("categoryCode",rs.getString(2));row.put("categoryName",rs.getString(3));row.put("summary",rs.getString(4));row.put("amount",rs.getBigDecimal(5));return row;
        },id));
    }
}
