package com.sunny.paintfactory.auth;

import com.sunny.paintfactory.common.ApiResponse;
import com.sunny.paintfactory.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users")
public class UserManagementController {
    private static final Set<String> ROLES=Set.of("ADMIN","SALES","WAREHOUSE","DISPATCH");
    private final JdbcTemplate jdbc; private final PasswordEncoder encoder;
    public UserManagementController(JdbcTemplate jdbc,PasswordEncoder encoder){this.jdbc=jdbc;this.encoder=encoder;}

    @GetMapping
    public ApiResponse<PageResult<Map<String,Object>>> list(@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){
        int p=Math.max(1,page),z=Math.min(100,Math.max(1,pageSize));String like="%"+keyword.trim()+"%";Object[]args={like,like,like};
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username LIKE ? OR display_name LIKE ? OR role_code LIKE ?",Long.class,args);
        List<Object>a=new ArrayList<>();Collections.addAll(a,args);a.add(z);a.add((p-1)*z);
        var items=jdbc.query("SELECT id,username,display_name,role_code,status,failed_login_attempts,locked_at,must_change_password,version,created_at,updated_at FROM sys_user WHERE username LIKE ? OR display_name LIKE ? OR role_code LIKE ? ORDER BY id LIMIT ? OFFSET ?",
            (rs,n)->map("id",rs.getLong(1),"username",rs.getString(2),"displayName",rs.getString(3),"role",rs.getString(4),"enabled","ENABLED".equals(rs.getString(5)),"failedLoginAttempts",rs.getInt(6),"locked",rs.getTimestamp(7)!=null,"mustChangePassword",rs.getBoolean(8),"version",rs.getInt(9),"createdAt",rs.getTimestamp(10).toLocalDateTime(),"updatedAt",rs.getTimestamp(11).toLocalDateTime()),a.toArray());
        return ApiResponse.success(new PageResult<>(items,total==null?0:total,p,z));
    }

    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody CreateRequest r,Authentication auth){requireRole(r.role());LocalDateTime now=LocalDateTime.now();try{jdbc.update("INSERT INTO sys_user(username,password_hash,display_name,role_code,status,failed_login_attempts,locked_at,must_change_password,version,created_at,updated_at) VALUES(?,?,?,?, 'ENABLED',0,NULL,1,0,?,?)",r.username().trim(),encoder.encode(r.password()),r.displayName().trim(),r.role(),now,now);}catch(DataIntegrityViolationException e){throw conflict("The username already exists. Choose a different username");}audit(r.username(),r.displayName(),"CREATE","Account created with role: "+r.role(),auth);return ApiResponse.success(null);}

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id,@Valid @RequestBody UpdateRequest r,Authentication auth){requireRole(r.role());int changed;try{changed=jdbc.update("UPDATE sys_user SET display_name=?,role_code=?,version=version+1,updated_at=? WHERE id=? AND version=?",r.displayName().trim(),r.role(),LocalDateTime.now(),id,r.version());}catch(DataIntegrityViolationException e){throw conflict("Failed to save the account details");}requireChanged(changed);Map<String,Object>u=user(id);audit(String.valueOf(u.get("username")),r.displayName(),"UPDATE","Account edited; role: "+r.role(),auth);return ApiResponse.success(null);}

    @PatchMapping("/{id}/enabled")
    public ApiResponse<Void> enabled(@PathVariable long id,@Valid @RequestBody EnabledRequest r,Authentication auth){Map<String,Object>u=user(id);if(auth.getName().equals(u.get("username"))&&!r.enabled())throw conflict("You cannot disable the account currently signed in");requireChanged(jdbc.update("UPDATE sys_user SET status=?,version=version+1,updated_at=? WHERE id=? AND version=?",r.enabled()?"ENABLED":"DISABLED",LocalDateTime.now(),id,r.version()));audit(String.valueOf(u.get("username")),String.valueOf(u.get("displayName")),r.enabled()?"ENABLE":"DISABLE",r.enabled()?"Account restored":"Account disabled",auth);return ApiResponse.success(null);}

    @PatchMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable long id,@Valid @RequestBody PasswordRequest r,Authentication auth){Map<String,Object>u=user(id);requireChanged(jdbc.update("UPDATE sys_user SET password_hash=?,must_change_password=1,failed_login_attempts=0,locked_at=NULL,version=version+1,updated_at=? WHERE id=? AND version=?",encoder.encode(r.password()),LocalDateTime.now(),id,r.version()));audit(String.valueOf(u.get("username")),String.valueOf(u.get("displayName")),"RESET_PASSWORD","Password reset by an administrator; change required at next sign-in",auth);return ApiResponse.success(null);}

    @PatchMapping("/{id}/unlock")
    public ApiResponse<Void> unlock(@PathVariable long id,@Valid @RequestBody VersionRequest r,Authentication auth){Map<String,Object>u=user(id);requireChanged(jdbc.update("UPDATE sys_user SET failed_login_attempts=0,locked_at=NULL,version=version+1,updated_at=? WHERE id=? AND version=?",LocalDateTime.now(),id,r.version()));audit(String.valueOf(u.get("username")),String.valueOf(u.get("displayName")),"UNLOCK","Account unlocked by an administrator",auth);return ApiResponse.success(null);}

    private Map<String,Object>user(long id){var rows=jdbc.query("SELECT username,display_name FROM sys_user WHERE id=?",(rs,n)->map("username",rs.getString(1),"displayName",rs.getString(2)),id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found");return rows.get(0);}
    private void audit(String code,String name,String action,String details,Authentication auth){Long uid=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,auth.getName());String operator=jdbc.queryForObject("SELECT display_name FROM sys_user WHERE username=?",String.class,auth.getName());jdbc.update("INSERT INTO master_data_audit_log(entity_type,entity_code,entity_name,action,details,operator_user_id,operator_name_snapshot,created_at) VALUES('users',?,?,?,?,?,?,?)",code,name,action,details,uid,operator,LocalDateTime.now());}
    private static void requireRole(String role){if(!ROLES.contains(role))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid role");}
    private static void requireChanged(int n){if(n!=1)throw conflict("The account was changed by another user. Refresh and try again");}
    private static ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
    private static Map<String,Object>map(Object...v){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put((String)v[i],v[i+1]);return m;}
    public record CreateRequest(@NotBlank @Size(min=3,max=64) @Pattern(regexp="^[A-Za-z0-9_.-]+$",message="The username may contain only letters, numbers, periods, hyphens, and underscores")String username,@NotBlank @Size(max=100)String displayName,@NotBlank String role,@NotBlank @Pattern(regexp="^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",message="The password must be 8 to 64 characters and include both letters and numbers")String password){}
    public record UpdateRequest(@NotBlank @Size(max=100)String displayName,@NotBlank String role,@NotNull Integer version){}
    public record EnabledRequest(boolean enabled,@NotNull Integer version){}
    public record PasswordRequest(@NotBlank @Pattern(regexp="^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",message="The password must be 8 to 64 characters and include both letters and numbers")String password,@NotNull Integer version){}
    public record VersionRequest(@NotNull Integer version){}
}
