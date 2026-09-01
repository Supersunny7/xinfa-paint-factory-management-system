package com.sunny.paintfactory.masterdata;

import com.sunny.paintfactory.common.ApiResponse;
import com.sunny.paintfactory.common.PageResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MasterDataController {
    private final JdbcTemplate jdbc;
    public MasterDataController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/customers")
    public ApiResponse<PageResult<Map<String, Object>>> customers(
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "") String regionLarge,
        @RequestParam(defaultValue = "") String regionSmall,
        @RequestParam(defaultValue = "true") boolean enabled,
        @RequestParam(defaultValue = "false") boolean includeDisabled,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize) {
        int safePage = Math.max(1, page), safeSize = Math.min(100, Math.max(1, pageSize));
        String normalizedKeyword = keyword.trim();
        String like = "%" + normalizedKeyword + "%";
        String prefixLike = normalizedKeyword + "%";
        Object[] args = {includeDisabled, enabled, like, like, like, like, regionLarge.trim(), regionLarge.trim(), regionSmall.trim(), regionSmall.trim()};
        String where = " (? OR enabled=?) AND (customer_code LIKE ? OR short_name LIKE ? OR COALESCE(mnemonic_code,'') LIKE ? OR COALESCE(phone,'') LIKE ?) AND (?='' OR COALESCE(region_large,'')=?) AND (?='' OR COALESCE(region_small,'')=?)";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM customer WHERE" + where, Long.class, args);
        List<Object> pageArgs = new ArrayList<>(); java.util.Collections.addAll(pageArgs,args);
        pageArgs.add(normalizedKeyword); pageArgs.add(normalizedKeyword); pageArgs.add(prefixLike); pageArgs.add(normalizedKeyword); pageArgs.add(prefixLike);
        pageArgs.add(safeSize); pageArgs.add((safePage - 1) * safeSize);
        String relevanceOrder = " CASE WHEN ?='' THEN 0 WHEN customer_code=? THEN 0 WHEN customer_code LIKE ? THEN 1 WHEN short_name=? THEN 2 WHEN short_name LIKE ? THEN 3 ELSE 4 END, customer_code";
        // Empty searches keep the original code order. Keyword searches put exact and prefix matches first,
        // while still retaining mnemonic, name and phone contains matches as a useful fallback.
        var items = jdbc.query("SELECT id,customer_code,short_name,contact_name,mobile,phone,region_large,region_small,route_text,settlement_method,enabled,version FROM customer WHERE" + where + " ORDER BY" + relevanceOrder + " LIMIT ? OFFSET ?",
            (rs, row) -> Map.<String,Object>ofEntries(Map.entry("id",rs.getLong(1)),Map.entry("customerCode",rs.getString(2)),Map.entry("shortName",rs.getString(3)),Map.entry("contactName",nullToEmpty(rs.getString(4))),Map.entry("mobile",nullToEmpty(rs.getString(5))),Map.entry("phone",nullToEmpty(rs.getString(6))),Map.entry("regionLarge",nullToEmpty(rs.getString(7))),Map.entry("regionSmall",nullToEmpty(rs.getString(8))),Map.entry("route",nullToEmpty(rs.getString(9))),Map.entry("settlementMethod",nullToEmpty(rs.getString(10))),Map.entry("enabled",rs.getBoolean(11)),Map.entry("version",rs.getInt(12))), pageArgs.toArray());
        return ApiResponse.success(new PageResult<>(items, total == null ? 0 : total, safePage, safeSize));
    }

    @GetMapping("/customer-regions")
    public ApiResponse<List<Map<String, Object>>> customerRegions() {
        return ApiResponse.success(jdbc.query("SELECT DISTINCT COALESCE(region_large,''),COALESCE(region_small,'') FROM customer WHERE enabled=1 AND (COALESCE(region_large,'')<>'' OR COALESCE(region_small,'')<>'') ORDER BY 1,2",
            (rs, row) -> Map.<String,Object>of("regionLarge",rs.getString(1),"regionSmall",rs.getString(2))));
    }

    @GetMapping("/products")
    public ApiResponse<PageResult<Map<String, Object>>> products(
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "true") boolean enabled,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Boolean saleable,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize) {
        int safePage = Math.max(1, page), safeSize = Math.min(100, Math.max(1, pageSize));
        String normalizedKeyword = keyword.trim();
        String like = "%" + normalizedKeyword + "%";
        String prefixLike = normalizedKeyword + "%";
        Object[] args = {enabled, like, like, like, like, categoryId, categoryId, categoryId, saleable, saleable};
        String where = " p.enabled=? AND (p.sku_code LIKE ? OR p.product_name LIKE ? OR COALESCE(p.mnemonic_code,'') LIKE ? OR COALESCE(p.barcode,'') LIKE ?) AND (? IS NULL OR p.category_id=? OR c.parent_id=?) AND (? IS NULL OR p.saleable=?)";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM product_sku p LEFT JOIN product_category c ON c.id=p.category_id WHERE" + where, Long.class, args);
        List<Object> pageArgs = new ArrayList<>(); java.util.Collections.addAll(pageArgs,args);
        pageArgs.add(normalizedKeyword); pageArgs.add(normalizedKeyword); pageArgs.add(prefixLike); pageArgs.add(normalizedKeyword); pageArgs.add(prefixLike);
        pageArgs.add(safeSize); pageArgs.add((safePage - 1) * safeSize);
        String relevanceOrder = " CASE WHEN ?='' THEN 0 WHEN p.sku_code=? THEN 0 WHEN p.sku_code LIKE ? THEN 1 WHEN p.product_name=? THEN 2 WHEN p.product_name LIKE ? THEN 3 ELSE 4 END, p.sku_code";
        var items = jdbc.query("SELECT p.id,p.sku_code,p.product_name,p.specification,p.color,p.sales_unit,p.package_spec,p.package_unit,p.wholesale_price,p.retail_price,p.total_stock,p.stock_lower_limit,GREATEST(p.stock_lower_limit-p.total_stock,0),p.last_purchase_price,c.category_name,p.version,p.category_id,COALESCE(pc.category_name,''),COALESCE(pc.id,0),p.saleable,p.classification_status FROM product_sku p LEFT JOIN product_category c ON c.id=p.category_id LEFT JOIN product_category pc ON pc.id=c.parent_id WHERE" + where + " ORDER BY" + relevanceOrder + " LIMIT ? OFFSET ?",
            (rs, row) -> Map.<String,Object>ofEntries(
                Map.entry("id",rs.getLong(1)), Map.entry("skuCode",rs.getString(2)), Map.entry("productName",rs.getString(3)),
                Map.entry("specification",nullToEmpty(rs.getString(4))), Map.entry("color",nullToEmpty(rs.getString(5))), Map.entry("salesUnit",rs.getString(6)),
                Map.entry("packageSpec",rs.getBigDecimal(7)==null?"":rs.getBigDecimal(7)), Map.entry("packageUnit",nullToEmpty(rs.getString(8))),
                Map.entry("wholesalePrice",rs.getBigDecimal(9)==null?"":rs.getBigDecimal(9)), Map.entry("retailPrice",rs.getBigDecimal(10)==null?"":rs.getBigDecimal(10)),
                Map.entry("totalStock",rs.getBigDecimal(11)),Map.entry("stockLowerLimit",rs.getBigDecimal(12)),Map.entry("shortageQuantity",rs.getBigDecimal(13)),
                Map.entry("lastPurchasePrice",rs.getBigDecimal(14)==null?"":rs.getBigDecimal(14)),Map.entry("stockStatus",rs.getBigDecimal(11).signum()<0?"NEGATIVE":rs.getBigDecimal(13).signum()>0?"SHORTAGE":"NORMAL"),
                Map.entry("categoryName",nullToEmpty(rs.getString(15))), Map.entry("version",rs.getInt(16)),Map.entry("categoryId",rs.getObject(17)==null?0:rs.getLong(17)),Map.entry("parentCategoryName",rs.getString(18)),Map.entry("parentCategoryId",rs.getLong(19)),Map.entry("saleable",rs.getBoolean(20)),Map.entry("classificationStatus",rs.getString(21))), pageArgs.toArray());
        return ApiResponse.success(new PageResult<>(items, total == null ? 0 : total, safePage, safeSize));
    }

    @GetMapping("/routes")
    public ApiResponse<List<Map<String, Object>>> routes(@RequestParam(defaultValue = "true") boolean enabled) {
        return ApiResponse.success(jdbc.query("SELECT id,route_code,route_name FROM route WHERE enabled=? ORDER BY route_code",
            (rs, row) -> Map.of("id", rs.getLong(1), "code", rs.getString(2), "name", rs.getString(3)), enabled));
    }

    @GetMapping("/vehicles")
    public ApiResponse<List<Map<String, Object>>> vehicles(@RequestParam(defaultValue = "true") boolean enabled) {
        return ApiResponse.success(jdbc.query("SELECT id,vehicle_code,COALESCE(plate_no,'') FROM vehicle WHERE enabled=? ORDER BY vehicle_code",
            (rs, row) -> Map.of("id", rs.getLong(1), "code", rs.getString(2), "plateNo", rs.getString(3)), enabled));
    }

    @GetMapping("/employees")
    public ApiResponse<List<Map<String, Object>>> employees(@RequestParam(defaultValue = "true") boolean enabled) {
        return ApiResponse.success(jdbc.query("SELECT e.id,e.employee_code,e.employee_name,COALESCE(e.phone,''),COALESCE(t.type_name,''),COALESCE(e.position_name,''),e.is_salesperson FROM employee e LEFT JOIN employee_type t ON t.id=e.employee_type_id WHERE e.enabled=? ORDER BY e.employee_code",
            (rs, row) -> Map.of("id", rs.getLong(1), "code", rs.getString(2), "name", rs.getString(3), "phone", rs.getString(4), "typeName", rs.getString(5), "positionName", rs.getString(6), "salesperson", rs.getBoolean(7)), enabled));
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }
}
