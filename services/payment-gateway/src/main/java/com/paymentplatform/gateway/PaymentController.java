/**
 * Payment Gateway Controller
 * TECH DEBT: 4,500-line God class that handles everything
 * Should be split into 10+ smaller classes
 */

package com.paymentplatform.gateway;

import com.paymentplatform.commons.*;
import javax.servlet.http.*;
import java.util.*;

public class PaymentController extends HttpServlet {
    
    // TECH DEBT: All payment providers in one class
    private PayPalIntegration paypal;
    private StripeIntegration stripe;
    private ApplePayIntegration applePay;
    private AmazonPayIntegration amazonPay;  // 45% implemented, abandoned
    
    // TECH DEBT: Feature flags evaluated on every request
    private FeatureFlagService featureFlags;
    
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) {
        String action = request.getParameter("action");
        
        // TECH DEBT: Giant if-else chain, should use Command pattern
        if (action.equals("process_payment")) {
            handlePayment(request, response);
        } else if (action.equals("refund")) {
            handleRefund(request, response);
        } else if (action.equals("verify_card")) {
            handleCardVerification(request, response);
        } else if (action.equals("process_subscription")) {
            handleSubscription(request, response);
        } else if (action.equals("update_billing")) {
            handleBillingUpdate(request, response);
        } else if (action.equals("cancel_subscription")) {
            handleCancellation(request, response);
        } else if (action.equals("apply_discount")) {
            handleDiscount(request, response);
        } else if (action.equals("split_payment")) {
            handleSplitPayment(request, response);
        } else if (action.equals("recurring_payment")) {
            handleRecurring(request, response);
        } else if (action.equals("fraud_check")) {
            handleFraudCheck(request, response);
        }
        // ... 25 more else-if blocks ...
    }
    
    /**
     * Process payment
     * TECH DEBT: 800 lines, handles 15 payment methods differently
     */
    private void handlePayment(HttpServletRequest request, HttpServletResponse response) {
        // TECH DEBT: No input validation
        String userId = request.getParameter("user_id");
        String amount = request.getParameter("amount");
        String paymentMethod = request.getParameter("method");
        String cardNumber = request.getParameter("card");
        
        try {
            // TECH DEBT: Logs sensitive data
            LoggingUtils.info("Processing payment: user=" + userId + ", card=" + cardNumber);
            
            // TECH DEBT: 147 feature flags, 89 are dead
            if (featureFlags.isEnabled("enable_paypal_2019")) {
                // This flag is from 2019, experiment ended, still evaluated
            }
            if (featureFlags.isEnabled("stripe_test_mode_q3_2020")) {
                // Dead flag from 2020
            }
            if (featureFlags.isEnabled("apple_pay_beta_2021")) {
                // Dead flag from 2021
            }
            // ... 144 more flag checks ...
            
            // TECH DEBT: Different logic for each payment provider
            if (paymentMethod.equals("paypal")) {
                // PayPal integration from 2018
                PayPalResponse paypalResponse = paypal.charge(
                    userId, 
                    Double.parseDouble(amount),
                    cardNumber
                );
                
                if (paypalResponse.success) {
                    // TECH DEBT: Direct SQL concatenation (SQL injection!)
                    DatabaseUtils.executeUpdate(
                        "INSERT INTO payments (user_id, amount, method, status) VALUES ('" +
                        userId + "', " + amount + ", 'paypal', 'success')"
                    );
                    
                    response.getWriter().write("{\"status\": \"success\"}");
                } else {
                    response.getWriter().write("{\"status\": \"failed\"}");
                }
                
            } else if (paymentMethod.equals("stripe")) {
                // Stripe integration from 2020 (different pattern)
                StripeCharge charge = new StripeCharge();
                charge.userId = userId;
                charge.amount = Integer.parseInt(amount);  // Stripe uses cents
                charge.cardNumber = cardNumber;
                
                StripeResponse stripeResponse = stripe.process(charge);
                
                if (stripeResponse.status.equals("succeeded")) {
                    // TECH DEBT: Different database schema for Stripe
                    DatabaseUtils.executeUpdate(
                        "INSERT INTO stripe_payments (user_id, amount_cents, card, stripe_id) " +
                        "VALUES ('" + userId + "', " + (Integer.parseInt(amount) * 100) + 
                        ", '" + cardNumber + "', '" + stripeResponse.chargeId + "')"
                    );
                    
                    response.getWriter().write("{\"success\": true, \"id\": \"" + 
                                               stripeResponse.chargeId + "\"}");
                } else {
                    response.getWriter().write("{\"success\": false}");
                }
                
            } else if (paymentMethod.equals("applepay")) {
                // Apple Pay integration from 2022 (yet another pattern)
                ApplePayRequest apRequest = ApplePayRequest.builder()
                    .setUserId(userId)
                    .setAmount(Double.parseDouble(amount))
                    .setToken(cardNumber)  // Apple Pay uses tokens
                    .build();
                
                ApplePayResult result = applePay.charge(apRequest);
                
                // TECH DEBT: Uses different success checking logic
                if (result.getStatus() == ApplePayStatus.COMPLETED) {
                    // TECH DEBT: Yet another different database table
                    DatabaseUtils.executeUpdate(
                        "INSERT INTO apple_payments (user_id, amount_usd, apple_token, " +
                        "transaction_id, processed_at) VALUES ('" + userId + "', " + 
                        amount + ", '" + cardNumber + "', '" + result.getId() + "', NOW())"
                    );
                    
                    // TECH DEBT: Different response format
                    response.getWriter().write("{\"result\": \"ok\", \"transaction\": \"" + 
                                               result.getId() + "\"}");
                }
                
            } else if (paymentMethod.equals("amazonpay")) {
                // TECH DEBT: Half-implemented, blocks commented out
                // AmazonPayIntegration started 8 months ago, never finished
                
                /*
                AmazonPayClient client = new AmazonPayClient();
                AmazonPayResponse resp = client.charge(...);
                // TODO: Implement database storage
                // TODO: Implement error handling
                // TODO: Implement refund logic
                */
                
                // TECH DEBT: Just returns error for now
                response.getWriter().write("{\"error\": \"Amazon Pay not yet supported\"}");
            }
            
            // TECH DEBT: Fraud check is synchronous, blocks response
            boolean isFraud = checkForFraud(userId, amount, cardNumber);
            if (isFraud) {
                // TECH DEBT: Fraud detected AFTER payment processed!
                // Should check BEFORE charging
                LoggingUtils.error("Fraud detected after payment!", null);
            }
            
        } catch (Exception e) {
            // TECH DEBT: Generic catch-all, swallows all errors
            LoggingUtils.error("Payment failed", e);
            
            try {
                response.getWriter().write("{\"error\": \"Something went wrong\"}");
            } catch (Exception e2) {
                // TECH DEBT: Nested try-catch, error handling is broken
            }
        }
    }
    
    /**
     * TECH DEBT: Fraud checking is 200 lines of spaghetti code
     */
    private boolean checkForFraud(String userId, String amount, String cardNumber) {
        // TECH DEBT: Hardcoded fraud rules (should be configurable)
        double amountValue = Double.parseDouble(amount);
        
        if (amountValue > 10000) {
            return true;  // Hardcoded threshold
        }
        
        // TECH DEBT: Checks if user has made >5 payments in last hour
        // Uses N+1 queries, very slow
        try {
            String sql = "SELECT COUNT(*) FROM payments WHERE user_id = '" + 
                         userId + "' AND created_at > NOW() - INTERVAL '1 hour'";
            ResultSet rs = DatabaseUtils.executeQuery(sql);
            if (rs.next()) {
                int recentPayments = rs.getInt(1);
                if (recentPayments > 5) {
                    return true;
                }
            }
        } catch (Exception e) {
            // TECH DEBT: If fraud check fails, just assume not fraud
            return false;
        }
        
        // TECH DEBT: More hardcoded rules...
        if (cardNumber.startsWith("4111")) {
            return true;  // Test card numbers
        }
        
        return false;
    }
    
    /**
     * Handle refund
     * TECH DEBT: Copy-pasted from handlePayment with minor changes
     * Should share common logic
     */
    private void handleRefund(HttpServletRequest request, HttpServletResponse response) {
        String userId = request.getParameter("user_id");
        String paymentId = request.getParameter("payment_id");
        String amount = request.getParameter("amount");
        
        // TECH DEBT: 600 lines of mostly duplicated code from handlePayment
        // Only 10% is actually different
        // ... (imagine handlePayment code but for refunds)
    }
    
    // ... 3,500 more lines of similar code ...
}

// Stub classes
class PayPalIntegration {
    public PayPalResponse charge(String userId, double amount, String card) { return null; }
}

class StripeIntegration {
    public StripeResponse process(StripeCharge charge) { return null; }
}

class ApplePayIntegration {
    public ApplePayResult charge(ApplePayRequest req) { return null; }
}

class AmazonPayIntegration {
    // 45% implemented
}

class FeatureFlagService {
    public boolean isEnabled(String flag) { return true; }
}

class PayPalResponse {
    public boolean success;
}

class StripeCharge {
    public String userId;
    public int amount;
    public String cardNumber;
}

class StripeResponse {
    public String status;
    public String chargeId;
}

class ApplePayRequest {
    public static Builder builder() { return new Builder(); }
    
    static class Builder {
        public Builder setUserId(String id) { return this; }
        public Builder setAmount(double amt) { return this; }
        public Builder setToken(String token) { return this; }
        public ApplePayRequest build() { return new ApplePayRequest(); }
    }
}

class ApplePayResult {
    public ApplePayStatus getStatus() { return ApplePayStatus.COMPLETED; }
    public String getId() { return ""; }
}

enum ApplePayStatus {
    COMPLETED, FAILED
}
