package com.sunny.paintfactory.inventory;

import com.sunny.paintfactory.common.ApiResponse;
import com.sunny.paintfactory.common.PageResult;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory-reconciliation")
public class InventoryReconciliationController {
    private final JdbcTemplate jdbc;

    public InventoryReconciliationController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @GetMapping("/summary")
    public ApiResponse<Map<String,Object>> summary() {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("enabledProductCount",number("SELECT COUNT(*) FROM product_sku WHERE enabled=1"));
        result.put("lowStockCount",number("SELECT COUNT(*) FROM product_sku WHERE enabled=1 AND total_stock>0 AND total_stock<stock_lower_limit"));
        result.put("outOfStockCount",number("SELECT COUNT(*) FROM product_sku WHERE enabled=1 AND total_stock<=0"));
        result.put("mismatchCount",number("SELECT COUNT(*) FROM product_sku p JOIN inventory_movement m ON m.id=(SELECT MAX(x.id) FROM inventory_movement x WHERE x.product_sku_id=p.id) WHERE p.enabled=1 AND p.total_stock<>m.after_quantity"));
        result.put("noMovementCount",number("SELECT COUNT(*) FROM product_sku p WHERE p.enabled=1 AND NOT EXISTS(SELECT 1 FROM inventory_movement m WHERE m.product_sku_id=p.id)"));
        result.put("stockQuantity",decimal("SELECT COALESCE(SUM(total_stock),0) FROM product_sku WHERE enabled=1"));
        return ApiResponse.success(result);
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String,Object>>> list(
        @RequestParam(defaultValue="") String keyword,
        @RequestParam(defaultValue="ALL") String alertType,
        @RequestParam(defaultValue="1") int page,
        @RequestParam(defaultValue="20") int pageSize) {
        int safePage=Math.max(1,page),safeSize=Math.min(100,Math.max(1,pageSize));
        String like="%"+keyword.trim()+"%";
        String condition=switch(alertType){
            case "LOW_STOCK"->" AND p.total_stock>0 AND p.total_stock<p.stock_lower_limit";
            case "NEGATIVE_STOCK"->" AND p.total_stock<0";
            case "OUT_OF_STOCK"->" AND p.total_stock=0";
            case "MISMATCH"->" AND lm.id IS NOT NULL AND p.total_stock<>lm.after_quantity";
            case "NO_MOVEMENT"->" AND lm.id IS NULL";
            default->"";
        };
        String from=" FROM product_sku p LEFT JOIN inventory_movement lm ON lm.id=(SELECT MAX(x.id) FROM inventory_movement x WHERE x.product_sku_id=p.id) WHERE p.enabled=1 AND (p.sku_code LIKE ? OR p.product_name LIKE ? OR COALESCE(p.specification,'') LIKE ?)"+condition;
        Long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,like,like,like);
        List<Map<String,Object>> items=jdbc.query("SELECT p.id,p.sku_code,p.product_name,COALESCE(p.specification,''),p.sales_unit,p.total_stock,p.stock_lower_limit,lm.after_quantity,lm.created_at,lm.id"+from+" ORDER BY CASE WHEN lm.id IS NOT NULL AND p.total_stock<>lm.after_quantity THEN 0 WHEN p.total_stock<=0 THEN 1 WHEN p.total_stock<p.stock_lower_limit THEN 2 ELSE 3 END,p.sku_code LIMIT ? OFFSET ?",(rs,row)->{
            BigDecimal stock=rs.getBigDecimal(6),lower=rs.getBigDecimal(7),ledger=rs.getBigDecimal(8);
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("productId",rs.getLong(1));item.put("skuCode",rs.getString(2));item.put("productName",rs.getString(3));item.put("specification",rs.getString(4));item.put("salesUnit",rs.getString(5));item.put("currentStock",stock);item.put("stockLowerLimit",lower);item.put("shortageQuantity",lower.subtract(stock).max(BigDecimal.ZERO));item.put("inventoryStatus",inventoryStatus(stock,lower));item.put("ledgerBalance",ledger);item.put("variance",ledger==null?null:stock.subtract(ledger));item.put("reconciliationStatus",reconciliationStatus(stock,ledger));item.put("lastMovementAt",rs.getObject(9));return item;
        },like,like,like,safeSize,(safePage-1)*safeSize);
        return ApiResponse.success(new PageResult<>(items,total==null?0:total,safePage,safeSize));
    }

    static String inventoryStatus(BigDecimal stock,BigDecimal lower){if(stock.signum()<0)return "NEGATIVE_STOCK";if(stock.signum()==0)return "OUT_OF_STOCK";return stock.compareTo(lower)<0?"LOW_STOCK":"NORMAL";}
    static String reconciliationStatus(BigDecimal stock,BigDecimal ledger){if(ledger==null)return "NO_MOVEMENT";return stock.compareTo(ledger)==0?"BALANCED":"MISMATCH";}
    private long number(String sql){Long value=jdbc.queryForObject(sql,Long.class);return value==null?0:value;}
    private BigDecimal decimal(String sql){BigDecimal value=jdbc.queryForObject(sql,BigDecimal.class);return value==null?BigDecimal.ZERO:value;}
}
