package com.sunny.paintfactory.inventory;

import com.sunny.paintfactory.common.ApiResponse;
import com.sunny.paintfactory.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/products/{productId}")
public class InventoryController {
    private final JdbcTemplate jdbc;

    public InventoryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/inventory-movements")
    public ApiResponse<PageResult<Map<String,Object>>> movements(
        @PathVariable long productId,
        @RequestParam(defaultValue="1") int page,
        @RequestParam(defaultValue="20") int pageSize) {
        int safePage=Math.max(1,page), safeSize=Math.min(100,Math.max(1,pageSize));
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movement WHERE product_sku_id=?",Long.class,productId);
        List<Map<String,Object>> items=jdbc.query("SELECT m.id,m.movement_type,m.quantity_change,m.before_quantity,m.after_quantity,m.reason,COALESCE(m.reference_type,''),COALESCE(m.reference_no,''),u.display_name,m.created_at FROM inventory_movement m JOIN sys_user u ON u.id=m.created_by WHERE m.product_sku_id=? ORDER BY m.created_at DESC,m.id DESC LIMIT ? OFFSET ?",
            (rs,row)->Map.of("id",rs.getLong(1),"movementType",rs.getString(2),"quantityChange",rs.getBigDecimal(3),"beforeQuantity",rs.getBigDecimal(4),"afterQuantity",rs.getBigDecimal(5),"reason",rs.getString(6),"referenceType",rs.getString(7),"referenceNo",rs.getString(8),"operatorName",rs.getString(9),"createdAt",rs.getObject(10,LocalDateTime.class)),
            productId,safeSize,(safePage-1)*safeSize);
        return ApiResponse.success(new PageResult<>(items,total==null?0:total,safePage,safeSize));
    }

    @PostMapping("/inventory-adjustments")
    @Transactional
    public ApiResponse<Map<String,Object>> adjust(@PathVariable long productId,@Valid @RequestBody AdjustmentRequest request,Authentication auth) {
        MovementType type;
        try { type=MovementType.valueOf(request.type()); }
        catch(IllegalArgumentException e) { throw bad("Invalid inventory-movement type"); }
        List<BigDecimal> stocks=jdbc.query("SELECT total_stock FROM product_sku WHERE id=? AND enabled=1 FOR UPDATE",(rs,row)->rs.getBigDecimal(1),productId);
        if(stocks.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"The product does not exist or is disabled");
        BigDecimal before=stocks.get(0), after=calculateAfter(type,before,request.quantity());
        BigDecimal change=after.subtract(before);
        if(change.signum()==0) throw bad("The counted stock equals the current stock; no adjustment is required");
        long uid=userId(auth); LocalDateTime now=LocalDateTime.now();
        jdbc.update("UPDATE product_sku SET total_stock=?,version=version+1,updated_by=?,updated_at=? WHERE id=?",after,uid,now,productId);
        jdbc.update("INSERT INTO inventory_movement(product_sku_id,movement_type,quantity_change,before_quantity,after_quantity,reason,created_by,created_at) VALUES(?,?,?,?,?,?,?,?)",productId,type.name(),change,before,after,request.reason().trim(),uid,now);
        return ApiResponse.success(Map.of("beforeQuantity",before,"quantityChange",change,"afterQuantity",after));
    }

    static BigDecimal calculateAfter(MovementType type,BigDecimal before,BigDecimal quantity) {
        if(type!=MovementType.ADJUSTMENT&&quantity.signum()==0) throw bad("Inbound or outbound quantity must be greater than zero");
        BigDecimal after=switch(type){case INBOUND->before.add(quantity);case OUTBOUND->before.subtract(quantity);case ADJUSTMENT->quantity;};
        if(after.signum()<0) throw bad("Outbound quantity cannot exceed the current stock");
        return after;
    }

    private long userId(Authentication auth){Long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,auth.getName());if(id==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return id;}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);}

    enum MovementType { INBOUND,OUTBOUND,ADJUSTMENT }
    public record AdjustmentRequest(@NotBlank String type,@NotNull @DecimalMin("0") BigDecimal quantity,@NotBlank String reason) {}
}
