package com.sunny.paintfactory.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private final byte[] key;
    private final long expirationMinutes;
    private final ObjectMapper objectMapper;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes,
                      ObjectMapper objectMapper) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationMinutes = expirationMinutes;
        this.objectMapper = objectMapper;
    }

    public String issue(String username) {
        try {
            Instant now = Instant.now();
            String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = encode(objectMapper.writeValueAsBytes(Map.of("sub", username, "iat", now.getEpochSecond(),
                "exp", now.plus(expirationMinutes, ChronoUnit.MINUTES).getEpochSecond())));
            String unsigned = header + "." + payload;
            return unsigned + "." + encode(sign(unsigned));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue token", exception);
        }
    }

    public String parseUsername(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !java.security.MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), URL_DECODER.decode(parts[2]))) {
                throw new IllegalArgumentException("Invalid token signature");
            }
            @SuppressWarnings("unchecked") Map<String, Object> payload = objectMapper.readValue(URL_DECODER.decode(parts[1]), Map.class);
            if (((Number) payload.get("exp")).longValue() <= Instant.now().getEpochSecond()) throw new IllegalArgumentException("Expired token");
            return payload.get("sub").toString();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid token", exception);
        }
    }

    private byte[] sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }
    private static String encode(byte[] value) { return URL_ENCODER.encodeToString(value); }
}
