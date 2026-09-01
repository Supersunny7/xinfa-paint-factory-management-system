package com.sunny.paintfactory.masterdata;

import com.sunny.paintfactory.common.ApiResponse;
import com.sunny.paintfactory.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/reference-data")
public class ReferenceDataController {
    private final JdbcTemplate jdbc;
    public ReferenceDataController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/suppliers")
    public ApiResponse<PageResult<Map<String,Object>>> suppliers(@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        String where=" WHERE (? IS NULL OR enabled=?) AND (supplier_code LIKE ? OR short_name LIKE ? OR COALESCE(phone,'') LIKE ? OR COALESCE(mobile,'') LIKE ?)"; Object[] args=args(enabled,keyword,4); long total=count("supplier",where,args);
        var items=jdbc.query("SELECT id,supplier_code,short_name,phone,mobile,fax,address,enabled,remark,version FROM supplier"+where+rankedOrder("supplier_code","short_name"),(rs,n)->map("id",rs.getLong(1),"code",rs.getString(2),"name",rs.getString(3),"phone",s(rs.getString(4)),"mobile",s(rs.getString(5)),"fax",s(rs.getString(6)),"address",s(rs.getString(7)),"enabled",rs.getBoolean(8),"remark",s(rs.getString(9)),"version",rs.getInt(10)),rankedPaged(args,keyword,page,pageSize)); return page(items,total,page,pageSize);
    }

    @GetMapping("/employees")
    public ApiResponse<PageResult<Map<String,Object>>> employees(@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        String where=" WHERE (? IS NULL OR e.enabled=?) AND (e.employee_code LIKE ? OR e.employee_name LIKE ? OR COALESCE(e.phone,'') LIKE ? OR COALESCE(t.type_name,'') LIKE ? OR COALESCE(d.department_name,'') LIKE ?)"; Object[] args=args(enabled,keyword,5);
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM employee e LEFT JOIN employee_type t ON t.id=e.employee_type_id LEFT JOIN department d ON d.id=e.department_id"+where,Long.class,args);
        var items=jdbc.query("SELECT e.id,e.employee_code,e.employee_name,e.gender,e.phone,t.type_name,d.department_name,e.position_name,e.education,e.id_card,e.address,e.is_salesperson,e.hometown,e.postal_code,e.hire_date,e.enabled,e.remark,e.version,e.employee_type_id,e.department_id FROM employee e LEFT JOIN employee_type t ON t.id=e.employee_type_id LEFT JOIN department d ON d.id=e.department_id"+where+rankedOrder("e.employee_code","e.employee_name"),(rs,n)->map("id",rs.getLong(1),"code",rs.getString(2),"name",rs.getString(3),"gender",s(rs.getString(4)),"phone",s(rs.getString(5)),"typeName",s(rs.getString(6)),"departmentName",s(rs.getString(7)),"positionName",s(rs.getString(8)),"education",s(rs.getString(9)),"idCard",mask(rs.getString(10)),"address",s(rs.getString(11)),"salesperson",rs.getBoolean(12),"hometown",s(rs.getString(13)),"postalCode",s(rs.getString(14)),"hireDate",rs.getObject(15)==null?"":rs.getObject(15).toString(),"enabled",rs.getBoolean(16),"remark",s(rs.getString(17)),"version",rs.getInt(18),"employeeTypeId",rs.getObject(19),"departmentId",rs.getObject(20)),rankedPaged(args,keyword,page,pageSize)); return page(items,total==null?0:total,page,pageSize);
    }

    @GetMapping("/vehicles")
    public ApiResponse<PageResult<Map<String,Object>>> vehicles(@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        String where=" WHERE (? IS NULL OR enabled=?) AND (vehicle_code LIKE ? OR COALESCE(plate_no,'') LIKE ? OR COALESCE(vehicle_type,'') LIKE ?)"; Object[] args=args(enabled,keyword,3); long total=count("vehicle",where,args);
        var items=jdbc.query("SELECT id,vehicle_code,plate_no,vehicle_type,enabled,remark FROM vehicle"+where+rankedOrder("vehicle_code","COALESCE(plate_no,'')"),(rs,n)->map("id",rs.getLong(1),"code",rs.getString(2),"plateNo",s(rs.getString(3)),"vehicleType",s(rs.getString(4)),"enabled",rs.getBoolean(5),"remark",s(rs.getString(6))),rankedPaged(args,keyword,page,pageSize)); return page(items,total,page,pageSize);
    }

    @GetMapping("/{kind:employee-types|departments|routes}")
    public ApiResponse<PageResult<Map<String,Object>>> simpleData(@PathVariable String kind,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){
        String table=simpleTable(kind),code=simpleCode(kind),name=simpleName(kind);String where=" WHERE (? IS NULL OR enabled=?) AND ("+code+" LIKE ? OR "+name+" LIKE ? OR COALESCE(remark,'') LIKE ?)";Object[]args=args(enabled,keyword,3);long total=count(table,where,args);
        var items=jdbc.query("SELECT id,"+code+","+name+",enabled,COALESCE(remark,'') FROM "+table+where+rankedOrder(code,name),(rs,n)->map("id",rs.getLong(1),"code",rs.getString(2),"name",rs.getString(3),"enabled",rs.getBoolean(4),"remark",rs.getString(5),"version",0),rankedPaged(args,keyword,page,pageSize));return page(items,total,page,pageSize);
    }

    @PatchMapping("/{kind}/{id}/enabled")
    public ApiResponse<Void> enabled(@PathVariable String kind,@PathVariable long id,@Valid @RequestBody EnabledRequest r,Authentication auth) {
        int changed=switch(kind){case "suppliers"->jdbc.update("UPDATE supplier SET enabled=?,version=version+1 WHERE id=? AND version=?",r.enabled(),id,r.version());case "employees"->jdbc.update("UPDATE employee SET enabled=?,version=version+1,updated_by=?,updated_at=? WHERE id=? AND version=?",r.enabled(),userId(auth),LocalDateTime.now(),id,r.version());case "vehicles"->jdbc.update("UPDATE vehicle SET enabled=? WHERE id=?",r.enabled(),id);case "employee-types","departments","routes"->jdbc.update("UPDATE "+simpleTable(kind)+" SET enabled=? WHERE id=?",r.enabled(),id);default->throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Master-data type not found");};
        if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Record was modified by another user; refresh and try again");Map<String,Object>label=entityLabel(kind,id);audit(kind,id,String.valueOf(label.get("code")),String.valueOf(label.get("name")),r.enabled()?"ENABLE":"DISABLE",r.enabled()?"Re-enabled record":"Disabled record",auth);return ApiResponse.success(null);
    }

    @GetMapping("/employee-options")
    public ApiResponse<Map<String,Object>> employeeOptions(){
        var types=jdbc.query("SELECT id,type_name FROM employee_type WHERE enabled=1 ORDER BY type_code",(rs,n)->map("id",rs.getLong(1),"name",rs.getString(2)));
        var departments=jdbc.query("SELECT id,department_name FROM department WHERE enabled=1 ORDER BY department_code",(rs,n)->map("id",rs.getLong(1),"name",rs.getString(2)));
        return ApiResponse.success(Map.of("types",types,"departments",departments));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResult<Map<String,Object>>> auditLogs(@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){int p=Math.max(1,page),z=Math.min(100,Math.max(1,pageSize));String like="%"+keyword.trim()+"%";Object[]args={like,like,like,like};Long total=jdbc.queryForObject("SELECT COUNT(*) FROM master_data_audit_log WHERE entity_code LIKE ? OR entity_name LIKE ? OR operator_name_snapshot LIKE ? OR action LIKE ?",Long.class,args);List<Object>a=new ArrayList<>();Collections.addAll(a,args);a.add(z);a.add((p-1)*z);var items=jdbc.query("SELECT id,entity_type,entity_code,entity_name,action,details,operator_name_snapshot,created_at FROM master_data_audit_log WHERE entity_code LIKE ? OR entity_name LIKE ? OR operator_name_snapshot LIKE ? OR action LIKE ? ORDER BY id DESC LIMIT ? OFFSET ?",(rs,n)->map("id",rs.getLong(1),"entityType",rs.getString(2),"entityCode",s(rs.getString(3)),"entityName",s(rs.getString(4)),"action",rs.getString(5),"details",s(rs.getString(6)),"operatorName",rs.getString(7),"createdAt",rs.getTimestamp(8).toLocalDateTime()),a.toArray());return ApiResponse.success(new PageResult<>(items,total==null?0:total,p,z));}

    @PostMapping("/suppliers")
    public ApiResponse<Void> createSupplier(@Valid @RequestBody SupplierRequest r,Authentication auth){
        try{jdbc.update("INSERT INTO supplier(supplier_code,short_name,phone,mobile,fax,address,enabled,remark,version) VALUES(?,?,?,?,?,?,1,?,0)",r.code().trim(),r.name().trim(),blank(r.phone()),blank(r.mobile()),blank(r.fax()),blank(r.address()),blank(r.remark()));}
        catch(DataIntegrityViolationException e){throw conflict("Supplier code already exists; enter a different code");} audit("suppliers",idByCode("supplier","supplier_code",r.code()),r.code(),r.name(),"CREATE","Created supplier",auth);return ApiResponse.success(null);
    }

    @PutMapping("/suppliers/{id}")
    public ApiResponse<Void> updateSupplier(@PathVariable long id,@Valid @RequestBody SupplierRequest r,Authentication auth){
        int changed;try{changed=jdbc.update("UPDATE supplier SET supplier_code=?,short_name=?,phone=?,mobile=?,fax=?,address=?,remark=?,version=version+1 WHERE id=? AND version=?",r.code().trim(),r.name().trim(),blank(r.phone()),blank(r.mobile()),blank(r.fax()),blank(r.address()),blank(r.remark()),id,r.version());}catch(DataIntegrityViolationException e){throw conflict("Supplier code already exists; enter a different code");} requireChanged(changed);audit("suppliers",id,r.code(),r.name(),"UPDATE","Updated supplier",auth);return ApiResponse.success(null);
    }

    @PostMapping("/vehicles")
    public ApiResponse<Void> createVehicle(@Valid @RequestBody VehicleRequest r,Authentication auth){try{jdbc.update("INSERT INTO vehicle(vehicle_code,plate_no,vehicle_type,enabled,remark) VALUES(?,?,?,1,?)",r.code().trim(),blank(r.plateNo()),blank(r.vehicleType()),blank(r.remark()));}catch(DataIntegrityViolationException e){throw conflict("Vehicle code or license plate already exists; check the values and try again");}audit("vehicles",idByCode("vehicle","vehicle_code",r.code()),r.code(),r.plateNo(),"CREATE","Created vehicle",auth);return ApiResponse.success(null);}

    @PutMapping("/vehicles/{id}")
    public ApiResponse<Void> updateVehicle(@PathVariable long id,@Valid @RequestBody VehicleRequest r,Authentication auth){int changed;try{changed=jdbc.update("UPDATE vehicle SET vehicle_code=?,plate_no=?,vehicle_type=?,remark=? WHERE id=?",r.code().trim(),blank(r.plateNo()),blank(r.vehicleType()),blank(r.remark()),id);}catch(DataIntegrityViolationException e){throw conflict("Vehicle code or license plate already exists; check the values and try again");}requireChanged(changed);audit("vehicles",id,r.code(),r.plateNo(),"UPDATE","Updated vehicle",auth);return ApiResponse.success(null);}

    @PostMapping("/employees")
    public ApiResponse<Void> createEmployee(@Valid @RequestBody EmployeeRequest r,Authentication auth){long uid=userId(auth);LocalDateTime now=LocalDateTime.now();try{jdbc.update("INSERT INTO employee(employee_code,employee_name,gender,employee_type_id,department_id,position_name,education,id_card,address,is_salesperson,hometown,postal_code,hire_date,phone,enabled,remark,version,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,0,?,?,?,?)",r.code().trim(),r.name().trim(),blank(r.gender()),r.employeeTypeId(),r.departmentId(),blank(r.positionName()),blank(r.education()),blank(r.idCard()),blank(r.address()),r.salesperson(),blank(r.hometown()),blank(r.postalCode()),r.hireDate(),blank(r.phone()),blank(r.remark()),uid,now,uid,now);}catch(DataIntegrityViolationException e){throw conflict("Employee code already exists, or the employee type or department is invalid");}audit("employees",idByCode("employee","employee_code",r.code()),r.code(),r.name(),"CREATE","Created employee",auth);return ApiResponse.success(null);}

    @PutMapping("/employees/{id}")
    public ApiResponse<Void> updateEmployee(@PathVariable long id,@Valid @RequestBody EmployeeRequest r,Authentication auth){String idSql=r.idCard()==null||r.idCard().isBlank()?"id_card":"?";List<Object>a=new ArrayList<>();Collections.addAll(a,r.code().trim(),r.name().trim(),blank(r.gender()),r.employeeTypeId(),r.departmentId(),blank(r.positionName()),blank(r.education()));if(!idSql.equals("id_card"))a.add(r.idCard().trim());Collections.addAll(a,blank(r.address()),r.salesperson(),blank(r.hometown()),blank(r.postalCode()),r.hireDate(),blank(r.phone()),blank(r.remark()),userId(auth),LocalDateTime.now(),id,r.version());int changed;try{changed=jdbc.update("UPDATE employee SET employee_code=?,employee_name=?,gender=?,employee_type_id=?,department_id=?,position_name=?,education=?,id_card="+idSql+",address=?,is_salesperson=?,hometown=?,postal_code=?,hire_date=?,phone=?,remark=?,version=version+1,updated_by=?,updated_at=? WHERE id=? AND version=?",a.toArray());}catch(DataIntegrityViolationException e){throw conflict("Employee code already exists, or the employee type or department is invalid");}requireChanged(changed);audit("employees",id,r.code(),r.name(),"UPDATE","Updated employee",auth);return ApiResponse.success(null);}

    @PostMapping("/{kind:employee-types|departments|routes}")
    public ApiResponse<Void> createSimple(@PathVariable String kind,@Valid @RequestBody SimpleRequest r,Authentication auth){String table=simpleTable(kind),code=simpleCode(kind),name=simpleName(kind);try{jdbc.update("INSERT INTO "+table+"("+code+","+name+",enabled,remark) VALUES(?,?,1,?)",r.code().trim(),r.name().trim(),blank(r.remark()));}catch(DataIntegrityViolationException e){throw conflict("Code already exists; enter a different code");}audit(kind,idByCode(table,code,r.code()),r.code(),r.name(),"CREATE","Created record",auth);return ApiResponse.success(null);}

    @PutMapping("/{kind:employee-types|departments|routes}/{id}")
    public ApiResponse<Void> updateSimple(@PathVariable String kind,@PathVariable long id,@Valid @RequestBody SimpleRequest r,Authentication auth){String table=simpleTable(kind),code=simpleCode(kind),name=simpleName(kind);int changed;try{changed=jdbc.update("UPDATE "+table+" SET "+code+"=?,"+name+"=?,remark=? WHERE id=?",r.code().trim(),r.name().trim(),blank(r.remark()),id);}catch(DataIntegrityViolationException e){throw conflict("Code already exists; enter a different code");}requireChanged(changed);audit(kind,id,r.code(),r.name(),"UPDATE","Updated record",auth);return ApiResponse.success(null);}

    @DeleteMapping("/{kind}/{id}")
    public ApiResponse<Void> delete(@PathVariable String kind,@PathVariable long id,Authentication auth){
        long references=switch(kind){case "employees"->countRefs("SELECT (SELECT COUNT(*) FROM sales_order WHERE salesperson_id=?)+(SELECT COUNT(*) FROM dispatch_sheet WHERE driver_id=? OR delivery_person_id=?)",id,id,id);case "vehicles"->countRefs("SELECT COUNT(*) FROM dispatch_sheet WHERE vehicle_id=?",id);case "employee-types"->countRefs("SELECT COUNT(*) FROM employee WHERE employee_type_id=?",id);case "departments"->countRefs("SELECT COUNT(*) FROM employee WHERE department_id=?",id);case "routes"->countRefs("SELECT COUNT(*) FROM dispatch_sheet WHERE route_id=?",id);case "suppliers"->0;default->throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Master-data type not found");};
        if(references>0)throw conflict("This record is used by business documents and cannot be deleted; disable it instead");
        String table=switch(kind){case "employees"->"employee";case "vehicles"->"vehicle";case "suppliers"->"supplier";case "employee-types","departments","routes"->simpleTable(kind);default->throw new ResponseStatusException(HttpStatus.NOT_FOUND);};
        Map<String,Object>label=entityLabel(kind,id);requireChanged(jdbc.update("DELETE FROM "+table+" WHERE id=?",id));audit(kind,id,String.valueOf(label.get("code")),String.valueOf(label.get("name")),"DELETE","Permanently deleted record",auth);return ApiResponse.success(null);
    }
    private long userId(Authentication auth){Long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,auth.getName());if(id==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return id;}
    private long count(String table,String where,Object[] args){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM "+table+where,Long.class,args);return n==null?0:n;}
    private long countRefs(String sql,Object...args){Long n=jdbc.queryForObject(sql,Long.class,args);return n==null?0:n;}
    private static void requireChanged(int changed){if(changed!=1)throw conflict("Record was modified by another user; refresh and try again");}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private static String blank(String v){return v==null||v.isBlank()?null:v.trim();}
    private static String simpleTable(String kind){return switch(kind){case "employee-types"->"employee_type";case "departments"->"department";case "routes"->"route";default->throw new ResponseStatusException(HttpStatus.NOT_FOUND);};}
    private static String simpleCode(String kind){return switch(kind){case "employee-types"->"type_code";case "departments"->"department_code";case "routes"->"route_code";default->throw new ResponseStatusException(HttpStatus.NOT_FOUND);};}
    private static String simpleName(String kind){return switch(kind){case "employee-types"->"type_name";case "departments"->"department_name";case "routes"->"route_name";default->throw new ResponseStatusException(HttpStatus.NOT_FOUND);};}
    private long idByCode(String table,String column,String code){Long id=jdbc.queryForObject("SELECT id FROM "+table+" WHERE "+column+"=?",Long.class,code.trim());return id==null?0:id;}
    private Map<String,Object> entityLabel(String kind,long id){String table,code,name;switch(kind){case "suppliers"->{table="supplier";code="supplier_code";name="short_name";}case "employees"->{table="employee";code="employee_code";name="employee_name";}case "vehicles"->{table="vehicle";code="vehicle_code";name="COALESCE(plate_no,'')";}case "employee-types","departments","routes"->{table=simpleTable(kind);code=simpleCode(kind);name=simpleName(kind);}default->throw new ResponseStatusException(HttpStatus.NOT_FOUND);}return jdbc.queryForObject("SELECT "+code+","+name+" FROM "+table+" WHERE id=?",(rs,n)->map("code",rs.getString(1),"name",s(rs.getString(2))),id);}
    private void audit(String type,long id,String code,String name,String action,String details,Authentication auth){long uid=userId(auth);String operator=jdbc.queryForObject("SELECT display_name FROM sys_user WHERE id=?",String.class,uid);jdbc.update("INSERT INTO master_data_audit_log(entity_type,entity_id,entity_code,entity_name,action,details,operator_user_id,operator_name_snapshot,created_at) VALUES(?,?,?,?,?,?,?,?,?)",type,id,blank(code),blank(name),action,details,uid,operator==null?auth.getName():operator,LocalDateTime.now());}
    private static Object[] args(Boolean enabled,String keyword,int likes){List<Object>a=new ArrayList<>();a.add(enabled);a.add(enabled);String like="%"+keyword.trim()+"%";for(int i=0;i<likes;i++)a.add(like);return a.toArray();}
    private static String rankedOrder(String code,String name){return " ORDER BY CASE WHEN ?='' THEN 0 WHEN "+code+"=? THEN 0 WHEN "+code+" LIKE ? THEN 1 WHEN "+name+"=? THEN 2 WHEN "+name+" LIKE ? THEN 3 ELSE 4 END, "+code+" LIMIT ? OFFSET ?";}
    private static Object[] rankedPaged(Object[]args,String keyword,int page,int size){int p=Math.max(1,page),s=Math.min(100,Math.max(1,size));String k=keyword.trim(),prefix=k+"%";List<Object>a=new ArrayList<>();Collections.addAll(a,args);Collections.addAll(a,k,k,prefix,k,prefix,s,(p-1)*s);return a.toArray();}
    private static Object[] paged(Object[] args,int page,int size){int p=Math.max(1,page),s=Math.min(100,Math.max(1,size));List<Object>a=new ArrayList<>();Collections.addAll(a,args);a.add(s);a.add((p-1)*s);return a.toArray();}
    private static ApiResponse<PageResult<Map<String,Object>>> page(List<Map<String,Object>> items,long total,int page,int size){return ApiResponse.success(new PageResult<>(items,total,Math.max(1,page),Math.min(100,Math.max(1,size))));}
    private static String s(String value){return value==null?"":value;} private static String mask(String v){if(v==null||v.isBlank())return "";if(v.length()<=8)return "****";return v.substring(0,4)+"********"+v.substring(v.length()-4);}
    private static Map<String,Object> map(Object...v){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put((String)v[i],v[i+1]);return m;}
    public record EnabledRequest(boolean enabled,@NotNull Integer version){}
    public record SupplierRequest(@NotBlank @Size(max=64) String code,@NotBlank @Size(max=200) String name,@Size(max=128) @Pattern(regexp="^[0-9+()\\-\\s]*$",message="Invalid phone-number format") String phone,@Size(max=64) @Pattern(regexp="^[0-9+()\\-\\s]*$",message="Invalid mobile-number format") String mobile,@Size(max=64) String fax,@Size(max=500) String address,@Size(max=1000) String remark,@NotNull Integer version){}
    public record VehicleRequest(@NotBlank @Size(max=64) String code,@Size(max=32) String plateNo,@Size(max=100) String vehicleType,@Size(max=500) String remark,@NotNull Integer version){}
    public record EmployeeRequest(@NotBlank @Size(max=64) String code,@NotBlank @Size(max=100) String name,@Size(max=20) String gender,Long employeeTypeId,Long departmentId,@Size(max=100) String positionName,@Size(max=100) String education,@Size(max=64) String idCard,@Size(max=500) String address,boolean salesperson,@Size(max=100) String hometown,@Size(max=32) String postalCode,java.time.LocalDate hireDate,@Size(max=64) @Pattern(regexp="^[0-9+()\\-\\s]*$",message="Invalid phone-number format") String phone,@Size(max=500) String remark,@NotNull Integer version){}
    public record SimpleRequest(@NotBlank @Size(max=64) String code,@NotBlank @Size(max=100) String name,@Size(max=500) String remark,@NotNull Integer version){}
}
