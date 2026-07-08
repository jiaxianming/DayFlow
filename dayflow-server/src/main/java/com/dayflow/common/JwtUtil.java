package com.dayflow.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发 / 解析
 * <p>subject 存 userId，自定义 claim 存 username；HMAC-SHA256 签名。</p>
 *
 * @author jiaxianming
 */
@Component
public class JwtUtil {

    /**
     * 签名密钥（来自 dayflow.jwt.secret 配置）
     */
    @Value("${dayflow.jwt.secret}")
    private String secret;

    /**
     * 有效期（秒，来自 dayflow.jwt.expiration 配置）
     */
    @Value("${dayflow.jwt.expiration}")
    private long expiration;

    /**
     * 构造签名密钥
     *
     * @return HMAC-SHA 密钥
     */
    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 token
     *
     * @param userId   用户 ID（写入 subject）
     * @param username 用户名（写入 claim）
     * @return JWT 字符串
     */
    public String generate(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration * 1000L))
                .signWith(key())
                .compact();
    }

    /**
     * 解析 token 中的 userId
     *
     * @param token JWT 字符串
     * @return userId；token 非法/过期返回 null
     */
    public Long parseUserId(String token) {
        try {
            Claims c = parse(token);
            return Long.valueOf(c.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 token 中的 username
     *
     * @param token JWT 字符串
     * @return username；token 非法/过期返回 null
     */
    public String parseUsername(String token) {
        try {
            return parse(token).get("username", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 校验签名并解析 Claims
     *
     * @param token JWT 字符串
     * @return Claims 载荷
     */
    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }
}
