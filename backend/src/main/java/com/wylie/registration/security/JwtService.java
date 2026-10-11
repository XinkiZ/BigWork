package com.wylie.registration.security;

import com.wylie.registration.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/** JWT 令牌服务（无状态认证） */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expireMinutes;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expire-minutes:720}") long expireMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = expireMinutes;
    }

    public String generate(AuthUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim("role", user.role().name())
                .claim("name", user.name())
                .claim("username", user.username())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** 解析令牌，失败返回 null */
    public AuthUser parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return new AuthUser(
                    Long.valueOf(claims.getSubject()),
                    UserRole.valueOf(claims.get("role", String.class)),
                    claims.get("name", String.class),
                    claims.get("username", String.class));
        } catch (Exception e) {
            return null;
        }
    }
}