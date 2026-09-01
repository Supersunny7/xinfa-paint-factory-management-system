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
@RequestMapping("/api/v1/ledgers/purchases")
public class PurchaseLedgerController {
    private final JdbcTemplate jdbc;

    public PurchaseLedgerController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
        @RequestParam LocalDate dateFrom,
        @RequestParam LocalDate dateTo,
        @RequestParam(defaultValue = "") String businessType,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "") String supplier,
        @RequestParam(defaultValue = "") String receiptNo,
        @RequestParam(defaultValue = "date") String sortBy,
        @RequestParam(defaultValue = "asc") String sortDirection) {
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        List<String> selectedTypes = selectedTypes(businessType);
        if (selectedTypes.stream().anyMatch(type -> !List.of("RECEIPT", "RETURN").contains(type))) {
            throw new IllegalArgumentException("采购流水类型无效");
        }

        StringBuilder where = new StringBuilder(" WHERE r.status='APPROVED' AND r.receipt_date>=? AND r.receipt_date<=?");
        List<Object> args = new ArrayList<>();
        args.add(dateFrom);
        args.add(dateTo);
        if (selectedTypes.size() == 1) {
            where.append("RECEIPT".equals(selectedTypes.get(0)) ? " AND i.quantity>0" : " AND i.quantity<0");
        }
        if (!keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            where.append(" AND (i.sku_code_snapshot LIKE ? OR i.product_name_snapshot LIKE ? OR COALESCE(i.specification_snapshot,'') LIKE ?)");
            args.add(like); args.add(like); args.add(like);
        }
        if (!supplier.isBlank()) {
            String like = "%" + supplier.trim() + "%";
            where.append(" AND (r.supplier_code_snapshot LIKE ? OR r.supplier_name_snapshot LIKE ?)");
            args.add(like); args.add(like);
        }
        if (!receiptNo.isBlank()) {
            where.append(" AND (r.receipt_no LIKE ? OR COALESCE(o.order_no,'') LIKE ?)");
            String like = "%" + receiptNo.trim() + "%";
            args.add(like); args.add(like);
        }

        String sql = """
            SELECT i.id,r.receipt_no,r.receipt_date,COALESCE(o.order_no,''),i.business_type,
                   i.sku_code_snapshot,i.product_name_snapshot,COALESCE(i.specification_snapshot,''),
                   COALESCE(i.color_snapshot,''),i.purchase_unit_snapshot,i.quantity,i.unit_price,
                   i.reference_price,i.line_amount,COALESCE(i.remark,''),r.supplier_code_snapshot,
                   r.supplier_name_snapshot,r.warehouse_name,r.settlement_method,
                   r.approved_at,u.display_name,COALESCE(r.remark,''),i.line_no,r.id receipt_id
              FROM purchase_receipt_item i
              JOIN purchase_receipt r ON r.id=i.purchase_receipt_id
              LEFT JOIN purchase_order o ON o.id=r.purchase_order_id
              LEFT JOIN sys_user u ON u.id=r.approved_by
            """ + where + " ORDER BY "+DocumentSort.sql(sortBy,sortDirection,"r.receipt_date","r.receipt_no","r.id")+",i.line_no ASC";

        List<Map<String, Object>> items = jdbc.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong(1));
            row.put("receiptNo", rs.getString(2));
            row.put("receiptDate", rs.getObject(3, LocalDate.class));
            row.put("orderNo", rs.getString(4));
            row.put("businessType", rs.getString(5));
            row.put("businessTypeName", businessTypeName(rs.getString(5)));
            row.put("skuCode", rs.getString(6));
            row.put("productName", rs.getString(7));
            row.put("specification", rs.getString(8));
            row.put("color", rs.getString(9));
            row.put("unit", rs.getString(10));
            row.put("quantity", rs.getBigDecimal(11));
            row.put("unitPrice", rs.getBigDecimal(12));
            row.put("referencePrice", rs.getBigDecimal(13));
            row.put("amount", rs.getBigDecimal(14));
            row.put("lineRemark", rs.getString(15));
            row.put("supplierCode", rs.getString(16));
            row.put("supplierName", rs.getString(17));
            row.put("warehouseName", rs.getString(18));
            row.put("settlementMethod", rs.getString(19));
            row.put("approvedAt", rs.getObject(20, LocalDateTime.class));
            row.put("approvedByName", rs.getString(21));
            row.put("receiptRemark", rs.getString(22));
            row.put("lineNo", rs.getInt(23));
            row.put("documentId", rs.getLong(24));
            return row;
        }, args.toArray());

        BigDecimal quantity = sum(items, "quantity");
        BigDecimal amount = sum(items, "amount");
        BigDecimal receiptQuantity = items.stream().map(x -> (BigDecimal) x.get("quantity"))
            .filter(x -> x != null && x.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returnQuantity = items.stream().map(x -> (BigDecimal) x.get("quantity"))
            .filter(x -> x != null && x.signum() < 0).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
        long documentCount = items.stream().map(x -> x.get("receiptNo")).distinct().count();
        return ApiResponse.success(Map.of("items", items, "summary", Map.of(
            "rowCount", items.size(), "documentCount", documentCount, "netQuantity", quantity,
            "receiptQuantity", receiptQuantity, "returnQuantity", returnQuantity, "netAmount", amount)));
    }

    static String businessTypeName(String type) {
        return switch (type) {
            case "ORDER_RETURN" -> "采购减数";
            case "UNLINKED_RETURN" -> "历史订单外减数";
            case "HISTORICAL_UNCLASSIFIED" -> "历史未分类";
            default -> "采购收货";
        };
    }

    static List<String> selectedTypes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(",")).stream()
            .map(String::trim)
            .filter(type -> !type.isBlank())
            .distinct()
            .toList();
    }

    private static BigDecimal sum(List<Map<String, Object>> items, String key) {
        return items.stream().map(x -> (BigDecimal) x.get(key)).filter(x -> x != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
