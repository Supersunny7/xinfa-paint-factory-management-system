package com.sunny.paintfactory.auth;

import com.sunny.paintfactory.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    static final int MAX_FAILED_ATTEMPTS = 10;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(JdbcTemplate jdbc, PasswordEncoder encoder, JwtService jwt) {
        this.jdbc = jdbc; this.encoder = encoder; this.jwt = jwt;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        var users = jdbc.query("SELECT id,username,password_hash,display_name,role_code,status,failed_login_attempts,locked_at,must_change_password FROM sys_user WHERE username=?",
            (rs, row) -> map("id",rs.getLong(1),"username",rs.getString(2),"hash",rs.getString(3),"displayName",rs.getString(4),"role",rs.getString(5),"status",rs.getString(6),"attempts",rs.getInt(7),"locked",rs.getTimestamp(8)!=null,"mustChangePassword",rs.getBoolean(9)), request.username().trim());
        if (users.isEmpty()) throw unauthorized();
        Map<String,Object> user=users.get(0);
        if (!"ENABLED".equals(user.get("status"))) throw new ResponseStatusException(HttpStatus.LOCKED,"This account is disabled. Contact an administrator");
        if ((boolean)user.get("locked")) throw new ResponseStatusException(HttpStatus.LOCKED,"This account was locked after 10 failed sign-in attempts. Contact an administrator to unlock it");
        if (!encoder.matches(request.password(), user.get("hash").toString())) {
            int attempts=((Number)user.get("attempts")).intValue()+1;
            LocalDateTime lockedAt=attempts>=MAX_FAILED_ATTEMPTS?LocalDateTime.now():null;
            jdbc.update("UPDATE sys_user SET failed_login_attempts=?,locked_at=?,updated_at=? WHERE id=?",attempts,lockedAt,LocalDateTime.now(),user.get("id"));
            if (lockedAt!=null) {
                jdbc.update("INSERT INTO master_data_audit_log(entity_type,entity_id,entity_code,entity_name,action,details,operator_user_id,operator_name_snapshot,created_at) VALUES('users',?,?,?,'LOCK','Automatically locked after 10 failed sign-in attempts',?,?,?)",user.get("id"),user.get("username"),user.get("displayName"),user.get("id"),user.get("displayName"),lockedAt);
                throw new ResponseStatusException(HttpStatus.LOCKED,"This account was locked after 10 failed sign-in attempts. Contact an administrator to unlock it");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect username or password. "+(MAX_FAILED_ATTEMPTS-attempts)+" attempts remaining");
        }
        jdbc.update("UPDATE sys_user SET failed_login_attempts=0,locked_at=NULL,updated_at=? WHERE id=?",LocalDateTime.now(),user.get("id"));
        return ApiResponse.success(map("token",jwt.issue(user.get("username").toString()),"username",user.get("username"),"displayName",user.get("displayName"),"role",user.get("role"),"mustChangePassword",user.get("mustChangePassword")));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication auth) {
        var rows=jdbc.query("SELECT id,password_hash FROM sys_user WHERE username=? AND status='ENABLED' AND locked_at IS NULL",(rs,n)->map("id",rs.getLong(1),"hash",rs.getString(2)),auth.getName());
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.LOCKED,"This account is unavailable. Contact an administrator");
        if(!encoder.matches(request.currentPassword(),rows.get(0).get("hash").toString()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"The current password is incorrect");
        if(encoder.matches(request.newPassword(),rows.get(0).get("hash").toString()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"The new password must differ from the current password");
        jdbc.update("UPDATE sys_user SET password_hash=?,must_change_password=0,failed_login_attempts=0,version=version+1,updated_at=? WHERE id=?",encoder.encode(request.newPassword()),LocalDateTime.now(),rows.get(0).get("id"));
        return ApiResponse.success(null);
    }

    private static ResponseStatusException unauthorized(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect username or password");}
    private static Map<String,Object> map(Object... values){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);return result;}
    public record LoginRequest(@NotBlank String username,@NotBlank String password){}
    public record ChangePasswordRequest(@NotBlank String currentPassword,@NotBlank @Pattern(regexp="^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",message="The new password must be 8 to 64 characters and include both letters and numbers") String newPassword){}
}
