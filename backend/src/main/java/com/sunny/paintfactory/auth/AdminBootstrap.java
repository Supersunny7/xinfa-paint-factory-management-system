package com.sunny.paintfactory.auth;

import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    private final String displayName;

    public AdminBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder,
        @Value("${app.bootstrap-admin.username:admin}") String username,
        @Value("${app.bootstrap-admin.password:}") String password,
        @Value("${app.bootstrap-admin.display-name:System Administrator}") String displayName) {
        this.jdbc = jdbc; this.encoder = encoder; this.username = username; this.password = password; this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank()) throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be set when admin bootstrap is enabled");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username=?", Integer.class, username);
        if (count != null && count == 0) {
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("INSERT INTO sys_user(username,password_hash,display_name,status,version,created_at,updated_at) VALUES(?,?,?,'ENABLED',0,?,?)",
                username, encoder.encode(password), displayName, now, now);
        }
    }
}
