package com.sevenb.user_manager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

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

            String role = claims.get("role", String.class);
            return new JwtClaims(tenantId, userId, ownerId, role);
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnauthorizedException("Token invalido o expirado");
        }
    }

    public record JwtClaims(String tenantId, Long userId, Long ownerId, String role) {}
}
