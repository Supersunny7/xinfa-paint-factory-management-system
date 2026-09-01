package com.sunny.paintfactory.masterdata;

import com.sunny.paintfactory.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class MasterDataWriteController {
    private final JdbcTemplate jdbc;
    public MasterDataWriteController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping("/customers")
    public ApiResponse<Map<String,Object>> createCustomer(@Valid @RequestBody CustomerRequest r, Authentication auth) {
        long userId=userId(auth); LocalDateTime now=LocalDateTime.now(); GeneratedKeyHolder keys=new GeneratedKeyHolder();
        try {
            jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO customer(customer_code,short_name,contact_name,mobile,phone,route_text,settlement_method,enabled,version,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,1,0,?,?,?,?)", Statement.RETURN_GENERATED_KEYS); int i=1; ps.setString(i++,r.customerCode());ps.setString(i++,r.shortName());ps.setString(i++,blankToNull(r.contactName()));ps.setString(i++,blankToNull(r.mobile()));ps.setString(i++,blankToNull(r.phone()));ps.setString(i++,blankToNull(r.route()));ps.setString(i++,blankToNull(r.settlementMethod()));ps.setLong(i++,userId);ps.setObject(i++,now);ps.setLong(i++,userId);ps.setObject(i,now);return ps;},keys);
        } catch(DataIntegrityViolationException e){throw conflict("客户编号已存在");}
        return ApiResponse.success(Map.of("id",keys.getKey().longValue()));
    }

    @PutMapping("/customers/{id}")
    public ApiResponse<Void> updateCustomer(@PathVariable long id,@Valid @RequestBody CustomerRequest r,Authentication auth){
        int changed;
        try { changed=jdbc.update("UPDATE customer SET customer_code=?,short_name=?,contact_name=?,mobile=?,phone=?,route_text=?,settlement_method=?,version=version+1,updated_by=?,updated_at=? WHERE id=? AND version=?",r.customerCode(),r.shortName(),blankToNull(r.contactName()),blankToNull(r.mobile()),blankToNull(r.phone()),blankToNull(r.route()),blankToNull(r.settlementMethod()),userId(auth),LocalDateTime.now(),id,r.version()); }
        catch(DataIntegrityViolationException e){throw conflict("客户编号已存在");}
        requireChanged(changed); return ApiResponse.success(null);
    }

    @PatchMapping("/customers/{id}/enabled")
    public ApiResponse<Void> setCustomerEnabled(@PathVariable long id,@Valid @RequestBody EnabledRequest r,Authentication auth){requireChanged(jdbc.update("UPDATE customer SET enabled=?,version=version+1,updated_by=?,updated_at=? WHERE id=? AND version=?",r.enabled(),userId(auth),LocalDateTime.now(),id,r.version()));return ApiResponse.success(null);}

    @PostMapping("/products")
    public ApiResponse<Map<String,Object>> createProduct(@Valid @RequestBody ProductRequest r,Authentication auth){
        long userId=userId(auth);LocalDateTime now=LocalDateTime.now();GeneratedKeyHolder keys=new GeneratedKeyHolder();boolean saleable=categorySaleable(r.categoryId());
        try {jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO product_sku(sku_code,product_name,specification,color,sales_unit,package_spec,package_unit,wholesale_price,retail_price,total_stock,stock_lower_limit,last_purchase_price,category_id,saleable,classification_status,enabled,version,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'CONFIRMED',1,0,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);int i=1;ps.setString(i++,r.skuCode());ps.setString(i++,r.productName());ps.setString(i++,blankToNull(r.specification()));ps.setString(i++,blankToNull(r.color()));ps.setString(i++,r.salesUnit());ps.setBigDecimal(i++,r.packageSpec());ps.setString(i++,blankToNull(r.packageUnit()));ps.setBigDecimal(i++,r.wholesalePrice());ps.setBigDecimal(i++,r.retailPrice());ps.setBigDecimal(i++,zero(r.totalStock()));ps.setBigDecimal(i++,zero(r.stockLowerLimit()));ps.setBigDecimal(i++,r.lastPurchasePrice());ps.setLong(i++,r.categoryId());ps.setBoolean(i++,saleable);ps.setLong(i++,userId);ps.setObject(i++,now);ps.setLong(i++,userId);ps.setObject(i,now);return ps;},keys);}catch(DataIntegrityViolationException e){throw conflict("货品编号已存在");}
        return ApiResponse.success(Map.of("id",keys.getKey().longValue()));
    }

    @PutMapping("/products/{id}")
    public ApiResponse<Void> updateProduct(@PathVariable long id,@Valid @RequestBody ProductRequest r,Authentication auth){boolean saleable=categorySaleable(r.categoryId());int changed;try{changed=jdbc.update("UPDATE product_sku SET sku_code=?,product_name=?,specification=?,color=?,sales_unit=?,package_spec=?,package_unit=?,wholesale_price=?,retail_price=?,stock_lower_limit=?,last_purchase_price=?,category_id=?,saleable=?,classification_status='CONFIRMED',version=version+1,updated_by=?,updated_at=? WHERE id=? AND version=?",r.skuCode(),r.productName(),blankToNull(r.specification()),blankToNull(r.color()),r.salesUnit(),r.packageSpec(),blankToNull(r.packageUnit()),r.wholesalePrice(),r.retailPrice(),zero(r.stockLowerLimit()),r.lastPurchasePrice(),r.categoryId(),saleable,userId(auth),LocalDateTime.now(),id,r.version());}catch(DataIntegrityViolationException e){throw conflict("货品编号已存在");}requireChanged(changed);return ApiResponse.success(null);}

    @PatchMapping("/products/{id}/enabled")
    public ApiResponse<Void> setProductEnabled(@PathVariable long id,@Valid @RequestBody EnabledRequest r,Authentication auth){requireChanged(jdbc.update("UPDATE product_sku SET enabled=?,version=version+1,updated_by=?,updated_at=? WHERE id=? AND version=?",r.enabled(),userId(auth),LocalDateTime.now(),id,r.version()));return ApiResponse.success(null);}

    @PostMapping("/routes")
    public ApiResponse<Map<String,Object>> createRoute(@Valid @RequestBody RouteRequest r) {
        GeneratedKeyHolder keys=new GeneratedKeyHolder();
        try { jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO route(route_code,route_name,enabled,remark) VALUES(?,?,1,?)",Statement.RETURN_GENERATED_KEYS);ps.setString(1,r.code().trim());ps.setString(2,r.name().trim());ps.setString(3,blankToNull(r.remark()));return ps;},keys); }
        catch(DataIntegrityViolationException e){throw conflict("路线编号已存在");}
        return ApiResponse.success(Map.of("id",keys.getKey().longValue()));
    }

    @PostMapping("/vehicles")
    public ApiResponse<Map<String,Object>> createVehicle(@Valid @RequestBody VehicleRequest r) {
        GeneratedKeyHolder keys=new GeneratedKeyHolder();
        try { jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO vehicle(vehicle_code,plate_no,enabled,remark) VALUES(?,?,1,?)",Statement.RETURN_GENERATED_KEYS);ps.setString(1,r.code().trim());ps.setString(2,blankToNull(r.plateNo()));ps.setString(3,blankToNull(r.remark()));return ps;},keys); }
        catch(DataIntegrityViolationException e){throw conflict("车辆编号或车牌已存在");}
        return ApiResponse.success(Map.of("id",keys.getKey().longValue()));
    }

    @PostMapping("/employees")
    public ApiResponse<Map<String,Object>> createEmployee(@Valid @RequestBody EmployeeRequest r,Authentication auth) {
        long uid=userId(auth);LocalDateTime now=LocalDateTime.now();GeneratedKeyHolder keys=new GeneratedKeyHolder();
        try { jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO employee(employee_code,employee_name,phone,enabled,version,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,1,0,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setString(1,r.code().trim());ps.setString(2,r.name().trim());ps.setString(3,blankToNull(r.phone()));ps.setLong(4,uid);ps.setObject(5,now);ps.setLong(6,uid);ps.setObject(7,now);return ps;},keys); }
        catch(DataIntegrityViolationException e){throw conflict("员工编号已存在");}
        return ApiResponse.success(Map.of("id",keys.getKey().longValue()));
    }

    private long userId(Authentication auth){Long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,auth.getName());if(id==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return id;}
    private boolean categorySaleable(Long categoryId){var rows=jdbc.query("SELECT COALESCE(c.saleable_default,p.saleable_default,1),c.parent_id FROM product_category c LEFT JOIN product_category p ON p.id=c.parent_id WHERE c.id=? AND c.enabled=1",(rs,n)->Map.of("saleable",rs.getBoolean(1),"parentId",rs.getObject(2)==null?0L:rs.getLong(2)),categoryId);if(rows.isEmpty()||((Number)rows.get(0).get("parentId")).longValue()==0)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"请选择有效的货物小类");return (boolean)rows.get(0).get("saleable");}
    private static void requireChanged(int changed){if(changed!=1)throw conflict("资料已被其他人修改，请刷新后重试");}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    private static BigDecimal zero(BigDecimal value){return value==null?BigDecimal.ZERO:value;}

    public record CustomerRequest(@NotBlank String customerCode,@NotBlank String shortName,String contactName,String mobile,String phone,String route,String settlementMethod,@NotNull Integer version){}
    public record ProductRequest(@NotBlank String skuCode,@NotBlank String productName,String specification,String color,@NotBlank String salesUnit,@DecimalMin("0") BigDecimal packageSpec,String packageUnit,@DecimalMin("0") BigDecimal wholesalePrice,@DecimalMin("0") BigDecimal retailPrice,@DecimalMin("0") BigDecimal totalStock,@DecimalMin("0") BigDecimal stockLowerLimit,@DecimalMin("0") BigDecimal lastPurchasePrice,@NotNull Long categoryId,@NotNull Integer version){}
    public record EnabledRequest(boolean enabled,@NotNull Integer version){}
    public record RouteRequest(@NotBlank String code,@NotBlank String name,String remark){}
    public record VehicleRequest(@NotBlank String code,String plateNo,String remark){}
    public record EmployeeRequest(@NotBlank String code,@NotBlank String name,String phone){}
}
