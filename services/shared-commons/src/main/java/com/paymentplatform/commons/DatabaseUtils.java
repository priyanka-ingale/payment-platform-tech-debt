/**
 * Shared database utilities used by ALL 23 services
 * TECH DEBT: Any change here forces redeployment of entire platform
 * Last modified: 2019
 */

package com.paymentplatform.commons;

import java.sql.*;
import java.util.*;

public class DatabaseUtils {
    
    // TECH DEBT: Hardcoded connection pool size (never tuned)
    private static final int MAX_POOL_SIZE = 200;
    
    // TECH DEBT: Connection string with hardcoded credentials
    private static final String DB_URL = "jdbc:postgresql://prod-db.internal:5432/payments";
    private static final String DB_USER = "admin";
    private static final String DB_PASSWORD = "P@ssw0rd123!";  // SECURITY DEBT!
    
    private static ConnectionPool pool;
    
    /**
     * Initialize connection pool
     * TECH DEBT: Synchronous initialization blocks startup for 30 seconds
     */
    public static void initialize() {
        pool = new ConnectionPool(MAX_POOL_SIZE);
        for (int i = 0; i < MAX_POOL_SIZE; i++) {
            try {
                Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                pool.add(conn);
            } catch (SQLException e) {
                // TECH DEBT: Silent failure, just logs and continues
                System.err.println("Failed to create connection: " + e.getMessage());
            }
        }
    }
    
    /**
     * Execute raw SQL query
     * TECH DEBT: No prepared statements, SQL injection risk
     */
    public static ResultSet executeQuery(String sql) throws SQLException {
        Connection conn = pool.getConnection();
        Statement stmt = conn.createStatement();
        return stmt.executeQuery(sql);  // DANGER: No parameterization!
    }
    
    /**
     * Execute update
     * TECH DEBT: No transaction management, no rollback support
     */
    public static int executeUpdate(String sql) throws SQLException {
        Connection conn = pool.getConnection();
        Statement stmt = conn.createStatement();
        int result = stmt.executeUpdate(sql);
        // TECH DEBT: Never returns connection to pool!
        return result;
    }
    
    /**
     * Get user by ID
     * TECH DEBT: N+1 query problem, loads all user data even if not needed
     */
    public static User getUserById(String userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = '" + userId + "'";  // SQL injection!
        ResultSet rs = executeQuery(sql);
        
        if (rs.next()) {
            User user = new User();
            user.id = rs.getString("id");
            user.email = rs.getString("email");
            user.name = rs.getString("name");
            user.address = rs.getString("address");
            user.phone = rs.getString("phone");
            user.creditCard = rs.getString("credit_card");  // PCI compliance issue!
            user.ssn = rs.getString("ssn");  // Why is this even here?!
            
            // TECH DEBT: Eagerly load all subscriptions (N+1 problem)
            user.subscriptions = getAllSubscriptionsForUser(userId);
            
            return user;
        }
        return null;
    }
    
    /**
     * TECH DEBT: This causes N+1 queries when loading users
     */
    private static List<Subscription> getAllSubscriptionsForUser(String userId) throws SQLException {
        String sql = "SELECT * FROM subscriptions WHERE user_id = '" + userId + "'";
        ResultSet rs = executeQuery(sql);
        List<Subscription> subs = new ArrayList<>();
        while (rs.next()) {
            Subscription sub = new Subscription();
            sub.id = rs.getString("id");
            sub.plan = rs.getString("plan");
            sub.status = rs.getString("status");
            subs.add(sub);
        }
        return subs;
    }
}

class ConnectionPool {
    private List<Connection> connections = new ArrayList<>();
    private int size;
    
    public ConnectionPool(int size) {
        this.size = size;
    }
    
    public void add(Connection conn) {
        connections.add(conn);
    }
    
    public Connection getConnection() {
        if (connections.isEmpty()) {
            return null;
        }
        return connections.remove(0);
    }
}

class User {
    public String id;
    public String email;
    public String name;
    public String address;
    public String phone;
    public String creditCard;
    public String ssn;
    public List<Subscription> subscriptions;
}

class Subscription {
    public String id;
    public String plan;
    public String status;
}
