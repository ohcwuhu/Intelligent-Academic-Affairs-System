package com.iaas.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与校验。
 *
 * <p>载荷只放身份标识与角色，不放姓名、学号等个人信息，减少令牌泄露的影响面。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final Duration ttl;

    public JwtUtil(@Value("${iaas.jwt.secret}") String secret,
                   @Value("${iaas.jwt.ttl-hours:12}") long ttlHours) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException("iaas.jwt.secret 至少需要 32 字节，当前 " + raw.length);
        }
        this.key = Keys.hmacShaKeyFor(raw);
        this.ttl = Duration.ofHours(ttlHours);
    }

    public String issue(UserContext.Principal p) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(p.userId()))
                .claim("username", p.username())
                .claim("role", p.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key);
        if (p.refId() != null) {
            builder.claim("refId", p.refId());
        }
        return builder.compact();
    }

    /** 解析令牌。非法或过期返回 null，由调用方决定如何拒绝。 */
    public UserContext.Principal parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Number refId = c.get("refId", Number.class);
            return new UserContext.Principal(
                    Long.valueOf(c.getSubject()),
                    c.get("username", String.class),
                    null,
                    c.get("role", String.class),
                    refId == null ? null : refId.longValue());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }
}
