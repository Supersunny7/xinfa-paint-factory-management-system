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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledgers/sales")
public class SalesLedgerController {
    private final JdbcTemplate jdbc;

    public SalesLedgerController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
        @RequestParam LocalDate dateFrom,
        @RequestParam LocalDate dateTo,
        @RequestParam(defaultValue = "") String businessType,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "") String customer,
        @RequestParam(defaultValue = "") String orderNo,
        @RequestParam(defaultValue = "") String salesperson,
        @RequestParam(defaultValue = "date") String sortBy,
        @RequestParam(defaultValue = "asc") String sortDirection) {
        if (dateTo.isBefore(dateFrom)) throw new IllegalArgumentException("The end date cannot be earlier than the start date");
        List<String> businessTypes = selectedTypes(businessType);

        StringBuilder where = new StringBuilder(" WHERE q.order_date>=? AND q.order_date<=?");
        List<Object> args = new ArrayList<>(List.of(dateFrom, dateTo));
        if (!businessTypes.isEmpty()) {
            where.append(" AND q.business_type IN (")
                .append(String.join(",", businessTypes.stream().map(x -> "?").toList())).append(")");
            args.addAll(businessTypes);
        }
        if (!keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            where.append(" AND (q.sku_code_snapshot LIKE ? OR q.product_name_snapshot LIKE ? OR COALESCE(q.specification_snapshot,'') LIKE ?)");
            args.add(like); args.add(like); args.add(like);
        }
        if (!customer.isBlank()) {
            String like = "%" + customer.trim() + "%";
            where.append(" AND (q.customer_code_snapshot LIKE ? OR q.customer_name_snapshot LIKE ?)");
            args.add(like); args.add(like);
        }
        if (!orderNo.isBlank()) { where.append(" AND q.order_no LIKE ?"); args.add("%" + orderNo.trim() + "%"); }
        if (!salesperson.isBlank()) { where.append(" AND COALESCE(q.salesperson_name_snapshot,'') LIKE ?"); args.add("%" + salesperson.trim() + "%"); }

        String sql = """
            SELECT q.id,q.order_no,q.order_date,q.line_type,q.sku_code_snapshot,
                   q.product_name_snapshot,COALESCE(q.specification_snapshot,''),COALESCE(q.color_snapshot,''),
                   q.sales_unit_snapshot,q.quantity,q.unit_price,q.reference_price,q.line_amount,
                   COALESCE(q.line_remark,''),q.customer_code_snapshot,q.customer_name_snapshot,
                   COALESCE(q.salesperson_name_snapshot,''),q.settlement_method,q.printed_at,
                   q.printed_by_name,COALESCE(q.order_remark,''),q.line_no,q.print_count,q.business_type,q.document_id
              FROM (
                    SELECT i.id,s.order_no,s.order_date,i.line_type,i.sku_code_snapshot,i.product_name_snapshot,
                           i.specification_snapshot,i.color_snapshot,i.sales_unit_snapshot,i.quantity,i.unit_price,
                           i.reference_price,i.line_amount,i.remark line_remark,s.customer_code_snapshot,
                           s.customer_name_snapshot,s.salesperson_name_snapshot,s.settlement_method,s.printed_at,
                           pu.display_name printed_by_name,s.remark order_remark,i.line_no,s.print_count,
                           'SALE' business_type,s.created_at document_created_at,s.id document_id
                      FROM sales_order_item i
                      JOIN sales_order s ON s.id=i.sales_order_id
                      LEFT JOIN sys_user pu ON pu.id=s.printed_by
                     WHERE s.printed_at IS NOT NULL AND s.status<>'VOIDED'
                    UNION ALL
                    SELECT i.id,r.return_no,r.return_date,'RETURN',i.sku_code_snapshot,i.product_name_snapshot,
                           i.specification_snapshot,i.color_snapshot,i.sales_unit_snapshot,-ABS(i.quantity),i.unit_price,
                           i.reference_price,-ABS(i.line_amount),i.remark,r.customer_code_snapshot,
                           r.customer_name_snapshot,r.salesperson_name_snapshot,r.settlement_method,r.printed_at,
                           pu.display_name,r.remark,i.line_no,r.print_count,'RETURN',r.created_at,r.id
                      FROM sales_return_item i
                      JOIN sales_return r ON r.id=i.sales_return_id
                      LEFT JOIN sys_user pu ON pu.id=r.printed_by
                     WHERE r.status='APPROVED'
              ) q
            """ + where + " ORDER BY "+DocumentSort.sql(sortBy,sortDirection,"q.order_date","q.order_no","q.document_id")+",q.line_no ASC";

        List<Map<String, Object>> items = jdbc.query(sql, (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong(1)); row.put("orderNo", rs.getString(2));
            row.put("orderDate", rs.getObject(3, LocalDate.class)); row.put("lineType", rs.getString(4));
            row.put("businessType", rs.getString(24));
            row.put("lineTypeName", lineTypeName(rs.getString(24), rs.getString(4)));
            row.put("skuCode", rs.getString(5)); row.put("productName", rs.getString(6));
            row.put("specification", rs.getString(7)); row.put("color", rs.getString(8));
            row.put("unit", rs.getString(9)); row.put("quantity", rs.getBigDecimal(10));
            row.put("unitPrice", rs.getBigDecimal(11)); row.put("referencePrice", rs.getBigDecimal(12));
            row.put("amount", rs.getBigDecimal(13)); row.put("lineRemark", rs.getString(14));
            row.put("customerCode", rs.getString(15)); row.put("customerName", rs.getString(16));
            row.put("salespersonName", rs.getString(17)); row.put("settlementMethod", rs.getString(18));
            row.put("printedAt", rs.getObject(19, LocalDateTime.class)); row.put("printedByName", rs.getString(20));
            row.put("orderRemark", rs.getString(21)); row.put("lineNo", rs.getInt(22)); row.put("printCount", rs.getInt(23));
            row.put("documentId", rs.getLong(25));
            return row;
        }, args.toArray());

        BigDecimal netQuantity = sum(items, "quantity");
        BigDecimal salesQuantity = items.stream().filter(x -> "SALE".equals(x.get("businessType")))
            .map(x -> (BigDecimal)x.get("quantity")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returnQuantity = items.stream().filter(x -> "RETURN".equals(x.get("businessType")))
            .map(x -> ((BigDecimal)x.get("quantity")).abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netAmount = sum(items, "amount");
        long documentCount = items.stream().map(x -> x.get("orderNo")).distinct().count();
        return ApiResponse.success(Map.of("items", items, "summary", Map.of(
            "rowCount", items.size(), "documentCount", documentCount, "salesQuantity", salesQuantity,
            "returnQuantity", returnQuantity, "netQuantity", netQuantity, "netAmount", netAmount)));
    }

    static String lineTypeName(String businessType, String type) {
        if ("RETURN".equals(businessType)) return "Sales Return";
        return switch (type) {
            case "GIFT" -> "Gift";
            case "DISCOUNT_ADJUSTMENT" -> "Discount Adjustment";
            case "CASH_ADJUSTMENT" -> "Cash Adjustment";
            case "OTHER_ADJUSTMENT" -> "Other Adjustment";
            default -> "Sales Outbound";
        };
    }

    static List<String> selectedTypes(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> values = List.of(value.split(","));
        if (values.stream().anyMatch(x -> !List.of("SALE", "RETURN").contains(x))) {
            throw new IllegalArgumentException("Invalid sales-ledger transaction type");
        }
        return values;
    }

    private static BigDecimal sum(List<Map<String, Object>> items, String key) {
        return items.stream().map(x -> (BigDecimal)x.get(key)).filter(x -> x != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
