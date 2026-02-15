/**
 * Authentication utilities
 * TECH DEBT: Mixed authentication strategies, no consistent pattern
 * Used by ALL 23 services
 */

package com.paymentplatform.commons;

import io.jsonwebtoken.*;
import java.util.*;

public class AuthUtils {
    
    // TECH DEBT: Hardcoded JWT secret (committed to Git!)
    private static final String JWT_SECRET = "superSecretKey12345!ThisIsNotSecure";
    
    // TECH DEBT: Session storage in memory (doesn't scale, loses on restart)
    private static Map<String, Session> activeSessions = new HashMap<>();
    
    /**
     * Generate JWT token
     * TECH DEBT: Never expires, no refresh mechanism
     */
    public static String generateToken(String userId) {
        return Jwts.builder()
            .setSubject(userId)
            .setIssuedAt(new Date())
            // TECH DEBT: No expiration!
            .signWith(SignatureAlgorithm.HS256, JWT_SECRET)
            .compact();
    }
    
    /**
     * Validate token
     * TECH DEBT: Catches all exceptions, returns true on error (SECURITY HOLE!)
     */
    public static boolean validateToken(String token) {
        try {
            Jwts.parser()
                .setSigningKey(JWT_SECRET)
                .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            // TECH DEBT: Returns true on exception (!!)
            // Original dev thought "if we can't parse, assume it's valid"
            System.err.println("Token validation error: " + e.getMessage());
            return true;  // THIS IS A MASSIVE SECURITY HOLE
        }
    }
    
    /**
     * Create session
     * TECH DEBT: In-memory sessions don't work in distributed systems
     */
    public static String createSession(String userId) {
        String sessionId = UUID.randomUUID().toString();
        Session session = new Session();
        session.userId = userId;
        session.createdAt = System.currentTimeMillis();
        session.lastAccess = System.currentTimeMillis();
        
        activeSessions.put(sessionId, session);
        
        // TECH DEBT: Sessions never expire, memory leak!
        return sessionId;
    }
    
    /**
     * TECH DEBT: Hybrid auth - some services use JWT, some use sessions
     * This causes confusion and security gaps
     */
    public static boolean isAuthenticated(String tokenOrSessionId) {
        // Try JWT first
        if (validateToken(tokenOrSessionId)) {
            return true;
        }
        
        // Try session
        if (activeSessions.containsKey(tokenOrSessionId)) {
            Session session = activeSessions.get(tokenOrSessionId);
            session.lastAccess = System.currentTimeMillis();
            return true;
        }
        
        return false;
    }
}

class Session {
    public String userId;
    public long createdAt;
    public long lastAccess;
}
