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
@RequestMapping("/api/v1/ledgers/inventory")
public class InventoryLedgerController {
    private final JdbcTemplate jdbc;

    public InventoryLedgerController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
        @RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo,
        @RequestParam(defaultValue = "") String type,
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "") String referenceNo,
        @RequestParam(defaultValue = "date") String sortBy,
        @RequestParam(defaultValue = "asc") String sortDirection) {
        if (dateTo.isBefore(dateFrom)) throw new IllegalArgumentException("The end date cannot be earlier than the start date");

        StringBuilder where = new StringBuilder(" WHERE m.created_at>=? AND m.created_at<?");
        List<Object> args = new ArrayList<>();
        args.add(dateFrom.atStartOfDay());
        args.add(dateTo.plusDays(1).atStartOfDay());
        List<String> types = movementTypes(type);
        if (!types.isEmpty()) {
            where.append(" AND m.movement_type IN (").append(String.join(",", types.stream().map(x -> "?").toList())).append(")");
            args.addAll(types);
        }
        if (!keyword.isBlank()) {
            where.append(" AND (p.sku_code LIKE ? OR p.product_name LIKE ? OR COALESCE(p.specification,'') LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        if (!referenceNo.isBlank()) {
            where.append(" AND COALESCE(m.reference_no,'') LIKE ?");
            args.add("%" + referenceNo.trim() + "%");
        }

        String sql = """
            SELECT m.id,m.movement_type,m.quantity_change,m.before_quantity,m.after_quantity,
                   m.reason,COALESCE(m.reference_type,''),COALESCE(m.reference_no,''),m.created_at,
                   p.sku_code,p.product_name,COALESCE(p.specification,''),p.sales_unit,u.display_name,
                   CASE WHEN m.movement_type IN ('PURCHASE_RECEIPT','PURCHASE_RETURN') THEN pri.unit_price
                        WHEN m.movement_type='SALE_PRINT' THEN sa.unit_price
                        WHEN m.movement_type='SALES_RETURN' THEN sri.unit_price END AS unit_price,
                   CASE WHEN m.movement_type IN ('PURCHASE_RECEIPT','PURCHASE_RETURN') THEN m.quantity_change*pri.unit_price
                        WHEN m.movement_type='SALE_PRINT' THEN sa.line_amount
                        WHEN m.movement_type='SALES_RETURN' THEN m.quantity_change*sri.unit_price END AS movement_amount,
                   CASE WHEN m.movement_type IN ('PURCHASE_RECEIPT','PURCHASE_RETURN') THEN pr.supplier_code_snapshot ELSE '' END,
                   CASE WHEN m.movement_type IN ('PURCHASE_RECEIPT','PURCHASE_RETURN') THEN pr.supplier_name_snapshot ELSE '' END,
                   CASE WHEN m.movement_type='SALE_PRINT' THEN so.customer_code_snapshot
                        WHEN m.movement_type='SALES_RETURN' THEN sr.customer_code_snapshot ELSE '' END,
                   CASE WHEN m.movement_type='SALE_PRINT' THEN so.customer_name_snapshot
                        WHEN m.movement_type='SALES_RETURN' THEN sr.customer_name_snapshot ELSE '' END,
                   CASE WHEN m.movement_type IN ('PURCHASE_RECEIPT','PURCHASE_RETURN') THEN pr.warehouse_name ELSE 'Main Warehouse' END,
                   m.reference_id AS source_document_id
              FROM inventory_movement m
              JOIN product_sku p ON p.id=m.product_sku_id
              JOIN sys_user u ON u.id=m.created_by
              LEFT JOIN purchase_receipt pr ON m.reference_type='PURCHASE_RECEIPT' AND pr.id=m.reference_id
              LEFT JOIN purchase_receipt_item pri ON pri.id=m.reference_line_id AND pri.purchase_receipt_id=pr.id
              LEFT JOIN sales_order so ON m.reference_type='SALES_ORDER_PRINT' AND so.id=m.reference_id
              LEFT JOIN sales_return_item sri ON m.movement_type='SALES_RETURN' AND sri.id=m.reference_line_id
              LEFT JOIN sales_return sr ON sr.id=sri.sales_return_id
              LEFT JOIN (
                    SELECT sales_order_id,sku_id,
                           CASE WHEN SUM(quantity)=0 THEN NULL ELSE SUM(line_amount)/SUM(quantity) END unit_price,
                           SUM(line_amount) line_amount
                      FROM sales_order_item WHERE sku_id IS NOT NULL AND quantity>0
                     GROUP BY sales_order_id,sku_id
              ) sa ON sa.sales_order_id=so.id AND sa.sku_id=m.product_sku_id
            """ + where + " ORDER BY "+DocumentSort.sql(sortBy,sortDirection,"m.created_at","COALESCE(pr.receipt_no,so.order_no,sr.return_no,m.reference_no,'')","m.id");

        List<Map<String, Object>> items = jdbc.query(sql, (rs, rowNum) -> {
            BigDecimal change = rs.getBigDecimal("quantity_change");
            BigDecimal amount = rs.getBigDecimal("movement_amount");
            boolean inbound = change.signum() > 0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("occurredAt", rs.getObject("created_at", LocalDateTime.class));
            row.put("documentType", documentType(rs.getString("movement_type"), rs.getString(7)));
            row.put("warehouseName", rs.getString(21));
            row.put("skuCode", rs.getString("sku_code")); row.put("productName", rs.getString("product_name"));
            row.put("specification", rs.getString(12)); row.put("unit", rs.getString("sales_unit"));
            row.put("unitPrice", rs.getBigDecimal("unit_price"));
            row.put("inboundQuantity", inbound ? change : null); row.put("inboundAmount", inbound ? amount : null);
            row.put("outboundQuantity", inbound ? null : change.abs());
            row.put("outboundAmount", inbound || amount == null ? null : amount.abs());
            row.put("supplierCode", rs.getString(17)); row.put("supplierName", rs.getString(18));
            row.put("customerCode", rs.getString(19)); row.put("customerName", rs.getString(20));
            row.put("referenceType", rs.getString(7)); row.put("referenceNo", rs.getString(8));
            row.put("documentId", rs.getObject("source_document_id", Long.class));
            row.put("beforeQuantity", rs.getBigDecimal("before_quantity")); row.put("afterQuantity", rs.getBigDecimal("after_quantity"));
            row.put("reason", rs.getString("reason")); row.put("operatorName", rs.getString(14));
            return row;
        }, args.toArray());

        return ApiResponse.success(Map.of("items", items, "summary", Map.of(
            "rowCount", items.size(),
            "documentCount", items.stream().map(x -> x.get("referenceNo")).filter(x -> x != null && !x.toString().isBlank()).distinct().count(),
            "inboundQuantity", sum(items, "inboundQuantity"), "outboundQuantity", sum(items, "outboundQuantity"),
            "inboundAmount", sum(items, "inboundAmount"), "outboundAmount", sum(items, "outboundAmount"))));
    }

    static List<String> movementTypes(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String raw : value.split(",")) {
            switch (raw.trim()) {
                case "PURCHASE", "PURCHASE_RECEIPT" -> { result.add("PURCHASE_RECEIPT"); result.add("PURCHASE_RETURN"); }
                case "SALES", "SALES_OUTBOUND" -> { result.add("SALE_PRINT"); result.add("REVERSAL"); }
                case "SALES_RETURN" -> result.add("SALES_RETURN");
                case "ADJUSTMENT", "STOCK_ADJUSTMENT" -> { result.add("INBOUND"); result.add("OUTBOUND"); result.add("ADJUSTMENT"); }
                default -> throw new IllegalArgumentException("Invalid inventory-movement type");
            }
        }
        return result.stream().distinct().toList();
    }

    static String documentType(String movementType, String referenceType) {
        return switch (movementType) {
            case "PURCHASE_RECEIPT" -> "Purchase Receipt";
            case "PURCHASE_RETURN" -> "Purchase Reduction";
            case "SALE_PRINT" -> "Sales Outbound";
            case "SALES_RETURN" -> "Sales Return Warehousing";
            case "REVERSAL" -> "Outbound Reversal";
            case "INBOUND" -> "Manual Inbound";
            case "OUTBOUND" -> "Manual Outbound";
            case "ADJUSTMENT" -> "Stock Count / Adjustment";
            default -> referenceType == null || referenceType.isBlank() ? movementType : referenceType;
        };
    }

    private static BigDecimal sum(List<Map<String, Object>> items, String key) {
        return items.stream().map(x -> (BigDecimal) x.get(key)).filter(x -> x != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
