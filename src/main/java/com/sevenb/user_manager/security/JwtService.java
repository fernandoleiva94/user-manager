package com.sevenb.user_manager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class JwtService {

    private final SecretKey secretKey;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public JwtClaims parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String tenantId = claims.get("tenantId", String.class);
            if (tenantId == null || tenantId.isBlank()) {
                throw new UnauthorizedException("JWT sin tenantId");
            }

            Long userId = claims.get("id", Long.class);
            if (userId == null) {
                Number idNumber = claims.get("id", Number.class);
                userId = idNumber != null ? idNumber.longValue() : null;
            }

            Long ownerId = claims.get("ownerId", Long.class);
            if (ownerId == null) {
                Number ownerIdNumber = claims.get("ownerId", Number.class);
                ownerId = ownerIdNumber != null ? ownerIdNumber.longValue() : null;
            }
            if (ownerId == null) {
                throw new UnauthorizedException("JWT sin ownerId");
            }

            String username = claims.getSubject();
            if (username == null) {
                username = claims.get("user", String.class);
            }
            String role = resolveRole(claims);
            String taxCondition = claims.get("taxCondition", String.class);
            List<String> permissions = resolvePermissions(claims);
            return new JwtClaims(tenantId, userId, ownerId, username, role, taxCondition, permissions);
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnauthorizedException("Token invalido o expirado");
        }
    }

    private String resolveRole(Claims claims) {
        Object role = claims.get("role");
        if (role instanceof String roleName) {
            return roleName;
        }
        Object legacyRole = claims.get("roleId");
        if (legacyRole instanceof Map<?, ?> roleMap && roleMap.get("authority") instanceof String authority) {
            return authority;
        }
        return null;
    }

    /**
     * Devuelve null si el claim no existe (token legacy), y una lista (posiblemente vacía) si existe.
     */
    private List<String> resolvePermissions(Claims claims) {
        Object raw = claims.get("permissions");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Collection<?> codes) {
            return codes.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
        }
        return List.of();
    }

    public record JwtClaims(String tenantId, Long userId, Long ownerId, String username, String role,
                            String taxCondition, List<String> permissions) {}
}
