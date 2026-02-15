/**
 * PayPal Integration
 * Written in 2018, never updated
 * TECH DEBT: Uses deprecated PayPal API v1
 */

package com.paymentplatform.gateway.integrations;

import com.paymentplatform.commons.*;
import org.apache.http.*;
import org.json.*;

public class LegacyPayPalIntegration {
    
    // TECH DEBT: Hardcoded PayPal API endpoint (v1 is deprecated)
    private static final String PAYPAL_API = "https://api.paypal.com/v1/payments/payment";
    
    // TECH DEBT: API credentials hardcoded in class
    private String clientId = "AQkquBDf1zctJOWGKWUEtKXm6qVhueUEMvXO62IHQczSRLLn8p7bnvlMaL";
    private String secret = "EO422dnhPpLjnM3yM6JvhxTJVJavSGqPl5";
    
    public PayPalResponse charge(String userId, double amount, String cardNumber) {
        try {
            // TECH DEBT: Manual HTTP client instead of using PayPal SDK
            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(PAYPAL_API);
            
            // TECH DEBT: Manual JSON construction (error-prone)
            JSONObject body = new JSONObject();
            body.put("intent", "sale");
            
            JSONObject payer = new JSONObject();
            payer.put("payment_method", "credit_card");
            
            // TECH DEBT: Sending full credit card to PayPal (should use tokens)
            JSONObject card = new JSONObject();
            card.put("number", cardNumber);
            card.put("type", "visa");  // TECH DEBT: Assumes all cards are Visa!
            card.put("expire_month", "12");  // TECH DEBT: Hardcoded!
            card.put("expire_year", "2025");  // TECH DEBT: Will break in 2026!
            
            payer.put("funding_instruments", new JSONArray().put(
                new JSONObject().put("credit_card", card)
            ));
            
            body.put("payer", payer);
            
            // TECH DEBT: Amount handling is inconsistent
            JSONObject transaction = new JSONObject();
            JSONObject amountObj = new JSONObject();
            amountObj.put("total", String.format("%.2f", amount));
            amountObj.put("currency", "USD");  // TECH DEBT: Assumes USD
            transaction.put("amount", amountObj);
            
            body.put("transactions", new JSONArray().put(transaction));
            
            post.setEntity(new StringEntity(body.toString()));
            post.setHeader("Content-Type", "application/json");
            
            // TECH DEBT: Basic auth instead of OAuth
            String auth = clientId + ":" + secret;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            post.setHeader("Authorization", "Basic " + encodedAuth);
            
            HttpResponse response = client.execute(post);
            
            // TECH DEBT: No error handling
            String responseBody = EntityUtils.toString(response.getEntity());
            JSONObject json = new JSONObject(responseBody);
            
            PayPalResponse result = new PayPalResponse();
            result.success = json.getString("state").equals("approved");
            result.transactionId = json.getString("id");
            
            return result;
            
        } catch (Exception e) {
            // TECH DEBT: Swallows all exceptions, returns failure
            LoggingUtils.error("PayPal charge failed", e);
            
            PayPalResponse error = new PayPalResponse();
            error.success = false;
            error.errorMessage = "Unknown error";
            return error;
        }
    }
}

class PayPalResponse {
    public boolean success;
    public String transactionId;
    public String errorMessage;
}
