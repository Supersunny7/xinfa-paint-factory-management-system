package com.sunny.paintfactory.masterdata;

import com.sunny.paintfactory.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/product-classification")
public class ProductClassificationController {
    private final JdbcTemplate jdbc;

    public ProductClassificationController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/pending")
    public ApiResponse<Map<String, Object>> pending(
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "50") int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 200);
        int offset = (Math.max(page, 1) - 1) * size;
        String like = "%" + keyword.trim() + "%";
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM product_sku WHERE enabled=1 AND classification_status='PENDING' AND (sku_code LIKE ? OR product_name LIKE ?)",
            Long.class, like, like);
        var items = jdbc.query(
            "SELECT id,sku_code,product_name,sales_unit,total_stock,last_purchase_price,wholesale_price,saleable FROM product_sku WHERE enabled=1 AND classification_status='PENDING' AND (sku_code LIKE ? OR product_name LIKE ?) ORDER BY sku_code LIMIT ? OFFSET ?",
            (rs, n) -> map("id", rs.getLong(1), "skuCode", rs.getString(2), "productName", rs.getString(3),
                "salesUnit", rs.getString(4), "totalStock", rs.getBigDecimal(5), "lastPurchasePrice", rs.getBigDecimal(6),
                "wholesalePrice", rs.getBigDecimal(7), "saleable", rs.getBoolean(8)),
            like, like, size, offset);
        Map<String, Map<String, Object>> categoryByCode = new LinkedHashMap<>();
        var parentCategories = jdbc.query("SELECT id,category_code,category_name FROM product_category WHERE parent_id IS NULL AND enabled=1",
            (rs, n) -> map("id", rs.getLong(1), "code", rs.getString(2), "name", rs.getString(3)));
        for (Map<String, Object> category : parentCategories) categoryByCode.put(String.valueOf(category.get("code")), category);
        for (Map<String, Object> item : items) {
            String code = suggestedCategoryCode(String.valueOf(item.get("productName")));
            Map<String, Object> suggestion = categoryByCode.get(code);
            if (suggestion != null) item.put("suggestion", suggestion);
        }
        return ApiResponse.success(map("items", items, "total", total));
    }

    @PostMapping("/auto-classify")
    public ApiResponse<Map<String, Object>> autoClassify(Authentication auth) {
        long uid = userId(auth);
        LocalDateTime now = LocalDateTime.now();
        Long internalCategoryId = jdbc.queryForObject("SELECT id FROM product_category WHERE parent_id IS NULL AND category_code='016'", Long.class);
        int internal = jdbc.update("UPDATE product_sku SET category_id=?,saleable=0,classification_status='AUTO',version=version+1,updated_by=?,updated_at=? WHERE enabled=1 AND LOWER(product_name) REGEXP 'empty (can|pail|drum)|packing carton' AND (category_id<>? OR category_id IS NULL OR saleable<>0)",
            internalCategoryId, uid, now, internalCategoryId);
        int matched = internal;
        matched += assignByName("000", "PRI", 1, uid, now, "(?i)primer|undercoat");
        matched += assignByName("000", "TOP", 1, uid, now, "(?i)topcoat|finish coat");
        matched += assignByName("010", "BRU", 1, uid, now, "(?i)paint brush|bristle brush");
        matched += assignByName("010", "ROL", 1, uid, now, "(?i)paint roller|roller cover");
        Long pending = jdbc.queryForObject("SELECT COUNT(*) FROM product_sku WHERE enabled=1 AND classification_status='PENDING'", Long.class);
        return ApiResponse.success(map("matched", matched, "internal", internal, "pending", pending));
    }

    @PostMapping("/confirm")
    public ApiResponse<Map<String, Object>> confirm(@Valid @RequestBody ConfirmRequest request, Authentication auth) {
        var categories = jdbc.query(
            "SELECT c.id,COALESCE(c.saleable_default,p.saleable_default,1) FROM product_category c LEFT JOIN product_category p ON p.id=c.parent_id WHERE c.id=? AND c.enabled=1",
            (rs, n) -> map("id", rs.getLong(1), "saleable", rs.getBoolean(2)), request.categoryId());
        if (categories.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Select a valid product category");
        boolean saleable = (boolean) categories.get(0).get("saleable");
        long uid = userId(auth);
        int updated = 0;
        for (Long id : request.productIds()) {
            updated += jdbc.update("UPDATE product_sku SET category_id=?,saleable=?,classification_status='CONFIRMED',version=version+1,updated_by=?,updated_at=? WHERE id=? AND enabled=1",
                request.categoryId(), saleable, uid, LocalDateTime.now(), id);
        }
        return ApiResponse.success(map("updated", updated, "saleable", saleable));
    }

    @PostMapping("/confirm-matches")
    public ApiResponse<Map<String, Object>> confirmMatches(@Valid @RequestBody ConfirmMatchesRequest request, Authentication auth) {
        String keyword = request.keyword().trim();
        if (keyword.length() < 2) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A bulk-classification keyword must contain at least two characters");
        var categories = jdbc.query(
            "SELECT c.id,COALESCE(c.saleable_default,p.saleable_default,1) FROM product_category c LEFT JOIN product_category p ON p.id=c.parent_id WHERE c.id=? AND c.enabled=1",
            (rs, n) -> map("id", rs.getLong(1), "saleable", rs.getBoolean(2)), request.categoryId());
        if (categories.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Select a valid product category");
        boolean saleable = (boolean) categories.get(0).get("saleable");
        long uid = userId(auth);
        String like = "%" + keyword + "%";
        int updated = jdbc.update("UPDATE product_sku SET category_id=?,saleable=?,classification_status='CONFIRMED',version=version+1,updated_by=?,updated_at=? WHERE enabled=1 AND classification_status='PENDING' AND (sku_code LIKE ? OR product_name LIKE ?)",
            request.categoryId(), saleable, uid, LocalDateTime.now(), like, like);
        return ApiResponse.success(map("updated", updated, "saleable", saleable));
    }

    @GetMapping("/suggestion-groups")
    public ApiResponse<List<Map<String, Object>>> suggestionGroups() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT product_name FROM product_sku WHERE enabled=1 AND classification_status='PENDING'",
            rs -> {
                String code = suggestedCategoryCode(rs.getString(1));
                if (code != null) counts.merge(code, 1L, Long::sum);
            });
        var categories = jdbc.query(
            "SELECT id,category_code,category_name FROM product_category WHERE parent_id IS NULL AND enabled=1",
            (rs, n) -> map("id", rs.getLong(1), "code", rs.getString(2), "name", rs.getString(3)));
        return ApiResponse.success(categories.stream()
            .filter(category -> counts.containsKey(String.valueOf(category.get("code"))))
            .map(category -> map("id", category.get("id"), "code", category.get("code"),
                "name", category.get("name"), "count", counts.get(String.valueOf(category.get("code")))))
            .toList());
    }

    @PostMapping("/confirm-suggestion")
    public ApiResponse<Map<String, Object>> confirmSuggestion(@Valid @RequestBody ConfirmSuggestionRequest request, Authentication auth) {
        var categories = jdbc.query(
            "SELECT id,category_code,COALESCE(saleable_default,1) FROM product_category WHERE id=? AND parent_id IS NULL AND enabled=1",
            (rs, n) -> map("id", rs.getLong(1), "code", rs.getString(2), "saleable", rs.getBoolean(3)), request.categoryId());
        if (categories.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Select a valid manufacturer or top-level category");
        String categoryCode = String.valueOf(categories.get(0).get("code"));
        boolean saleable = (boolean) categories.get(0).get("saleable");
        long uid = userId(auth);
        int updated = 0;
        var candidates = jdbc.query("SELECT id,product_name FROM product_sku WHERE enabled=1 AND classification_status='PENDING'",
            (rs, n) -> map("id", rs.getLong(1), "name", rs.getString(2)));
        for (Map<String, Object> candidate : candidates) {
            if (!categoryCode.equals(suggestedCategoryCode(String.valueOf(candidate.get("name"))))) continue;
            updated += jdbc.update("UPDATE product_sku SET category_id=?,saleable=?,classification_status='CONFIRMED',version=version+1,updated_by=?,updated_at=? WHERE id=? AND enabled=1 AND classification_status='PENDING'",
                request.categoryId(), saleable, uid, LocalDateTime.now(), candidate.get("id"));
        }
        return ApiResponse.success(map("updated", updated, "saleable", saleable));
    }

    private int assignByName(String parentCode, String childCode, int saleable, long uid, LocalDateTime now, String pattern) {
        Long categoryId = childCode == null
            ? jdbc.queryForObject("SELECT id FROM product_category WHERE parent_id IS NULL AND category_code=?", Long.class, parentCode)
            : jdbc.queryForObject("SELECT c.id FROM product_category c JOIN product_category p ON p.id=c.parent_id WHERE p.category_code=? AND c.category_code=?", Long.class, parentCode, childCode);
        return jdbc.update("UPDATE product_sku SET category_id=?,saleable=?,classification_status='AUTO',version=version+1,updated_by=?,updated_at=? WHERE enabled=1 AND classification_status='PENDING' AND product_name REGEXP ?",
            categoryId, saleable, uid, now, pattern);
    }

    private static String suggestedCategoryCode(String name) {
        if (name == null) return null;
        String normalized = name.toLowerCase(java.util.Locale.ROOT);
        if (normalized.matches(".*(empty (can|pail|drum)|packing carton).*")) return "016";
        if (normalized.matches(".*(paint brush|bristle brush|paint roller|roller cover).*")) return "003";
        if (normalized.matches(".*(primer|undercoat|topcoat|finish coat).*")) return "001";
        if (normalized.matches(".*(epoxy|polyurethane|industrial coating).*")) return "002";
        return null;
    }

    private long userId(Authentication auth) {
        Long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, auth.getName());
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return id;
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put(values[i].toString(), values[i + 1]);
        return result;
    }

    public record ConfirmRequest(@NotEmpty List<Long> productIds, @NotNull Long categoryId) {}
    public record ConfirmMatchesRequest(@NotNull @Size(min=2, max=100) String keyword, @NotNull Long categoryId) {}
    public record ConfirmSuggestionRequest(@NotNull Long categoryId) {}
}
