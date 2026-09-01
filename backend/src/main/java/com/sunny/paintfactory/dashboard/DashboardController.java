package com.sunny.paintfactory.dashboard;

import com.sunny.paintfactory.common.ApiResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final JdbcTemplate jdbc;

    public DashboardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/owner")
    public ApiResponse<Map<String, Object>> owner() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate dayBefore = yesterday.minusDays(1);
        Summary yesterdaySummary = summary(yesterday);
        Summary dayBeforeSummary = summary(dayBefore);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("businessDate", yesterday);
        result.put("salesAmount", yesterdaySummary.salesAmount());
        result.put("estimatedCost", yesterdaySummary.estimatedCost());
        result.put("estimatedGrossProfit", yesterdaySummary.estimatedGrossProfit());
        result.put("salesOrderCount", yesterdaySummary.salesOrderCount());
        result.put("missingCostItemCount", missingCostItemCount(yesterday));
        result.put("grossProfitChangePercent", changePercent(yesterdaySummary.estimatedGrossProfit(), dayBeforeSummary.estimatedGrossProfit()));
        result.put("unprintedSalesCount", count("SELECT COUNT(*) FROM sales_order WHERE status<>'VOIDED' AND printed_at IS NULL"));
        result.put("draftDispatchCount", count("SELECT COUNT(*) FROM dispatch_sheet WHERE status='DRAFT'"));
        result.put("draftReceiptCount", count("SELECT COUNT(*) FROM purchase_receipt WHERE status='DRAFT'"));
        result.put("stockWarningCount", count("SELECT COUNT(*) FROM product_sku WHERE enabled=1 AND total_stock<stock_lower_limit"));
        result.put("salesFlow", salesFlow(yesterday));
        return ApiResponse.success(result);
    }

    private Summary summary(LocalDate date) {
        String sql = """
            SELECT COALESCE(SUM(s.total_amount),0),
                   COALESCE(SUM(costs.estimated_cost),0),
                   COUNT(*)
              FROM sales_order s
              LEFT JOIN (
                    SELECT i.sales_order_id,
                           SUM(CASE WHEN i.sku_id IS NOT NULL
                                    THEN i.quantity * COALESCE(p.last_purchase_price,0)
                                    ELSE 0 END) estimated_cost
                      FROM sales_order_item i
                      LEFT JOIN product_sku p ON p.id=i.sku_id
                     GROUP BY i.sales_order_id
              ) costs ON costs.sales_order_id=s.id
             WHERE s.status<>'VOIDED' AND DATE(s.printed_at)=?
            """;
        return jdbc.queryForObject(sql, (rs, n) -> {
            BigDecimal sales = money(rs.getBigDecimal(1));
            BigDecimal cost = money(rs.getBigDecimal(2));
            return new Summary(sales, cost, sales.subtract(cost), rs.getLong(3));
        }, date);
    }

    private List<Map<String, Object>> salesFlow(LocalDate date) {
        String sql = """
            SELECT s.id,s.order_no,s.printed_at,s.customer_name_snapshot,s.total_amount,
                   COALESCE(costs.estimated_cost,0)
              FROM sales_order s
              LEFT JOIN (
                    SELECT i.sales_order_id,
                           SUM(CASE WHEN i.sku_id IS NOT NULL
                                    THEN i.quantity * COALESCE(p.last_purchase_price,0)
                                    ELSE 0 END) estimated_cost
                      FROM sales_order_item i
                      LEFT JOIN product_sku p ON p.id=i.sku_id
                     GROUP BY i.sales_order_id
              ) costs ON costs.sales_order_id=s.id
             WHERE s.status<>'VOIDED' AND DATE(s.printed_at)=?
             ORDER BY s.printed_at DESC
             LIMIT 20
            """;
        return jdbc.query(sql, (rs, n) -> {
            BigDecimal sales = money(rs.getBigDecimal(5));
            BigDecimal cost = money(rs.getBigDecimal(6));
            return map("id", rs.getLong(1), "orderNo", rs.getString(2), "printedAt", rs.getTimestamp(3).toLocalDateTime(),
                "customerName", rs.getString(4), "salesAmount", sales, "estimatedCost", cost,
                "estimatedGrossProfit", sales.subtract(cost), "printStatus", "已打印");
        }, date);
    }

    static BigDecimal changePercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) return null;
        return current.subtract(previous).multiply(new BigDecimal("100")).divide(previous.abs(), 1, RoundingMode.HALF_UP);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private long missingCostItemCount(LocalDate date) {
        Long value = jdbc.queryForObject("""
            SELECT COUNT(*)
              FROM sales_order_item i
              JOIN sales_order s ON s.id=i.sales_order_id
              LEFT JOIN product_sku p ON p.id=i.sku_id
             WHERE s.status<>'VOIDED' AND DATE(s.printed_at)=?
               AND i.sku_id IS NOT NULL AND p.last_purchase_price IS NULL
            """, Long.class, date);
        return value == null ? 0 : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }

    private record Summary(BigDecimal salesAmount, BigDecimal estimatedCost, BigDecimal estimatedGrossProfit, long salesOrderCount) {}
}
