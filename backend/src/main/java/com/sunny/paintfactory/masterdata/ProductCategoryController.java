package com.sunny.paintfactory.masterdata;

import com.sunny.paintfactory.common.ApiResponse;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/product-categories")
public class ProductCategoryController {
    private final JdbcTemplate jdbc;
    public ProductCategoryController(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @GetMapping("/tree")
    public ApiResponse<List<Map<String,Object>>> tree(){
        var parents=jdbc.query("SELECT p.id,p.category_code,p.category_name,COUNT(DISTINCT s.id) product_count FROM product_category p LEFT JOIN product_category c ON c.parent_id=p.id AND c.enabled=1 LEFT JOIN product_sku s ON s.enabled=1 AND (s.category_id=p.id OR s.category_id=c.id) WHERE p.parent_id IS NULL AND p.enabled=1 GROUP BY p.id ORDER BY p.category_code",
            (rs,n)->map("id",rs.getLong(1),"code",rs.getString(2),"name",rs.getString(3),"productCount",rs.getLong(4),"children",new ArrayList<Map<String,Object>>()));
        for(Map<String,Object> parent:parents){
            long parentId=((Number)parent.get("id")).longValue();
            var children=jdbc.query("SELECT c.id,c.category_code,c.category_name,COUNT(s.id) FROM product_category c LEFT JOIN product_sku s ON s.category_id=c.id AND s.enabled=1 WHERE c.parent_id=? AND c.enabled=1 GROUP BY c.id ORDER BY c.sort_order,c.category_code",
                (rs,n)->map("id",rs.getLong(1),"code",rs.getString(2),"name",rs.getString(3),"productCount",rs.getLong(4)),parentId);
            parent.put("children",children);
        }
        return ApiResponse.success(parents);
    }
    private static Map<String,Object>map(Object...v){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put((String)v[i],v[i+1]);return m;}
}
