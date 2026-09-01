package com.sunny.paintfactory.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private final JwtService service = new JwtService("test-secret-with-more-than-enough-entropy", 60, new ObjectMapper());

    @Test
    void issuesAndParsesSignedToken() {
        assertThat(service.parseUsername(service.issue("admin"))).isEqualTo("admin");
    }

    @Test
    void rejectsTamperedToken() {
        String token = service.issue("admin");
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
        assertThatThrownBy(() -> service.parseUsername(tampered))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
