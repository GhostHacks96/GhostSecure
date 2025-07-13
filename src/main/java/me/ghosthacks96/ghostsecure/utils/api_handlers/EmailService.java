package me.ghosthacks96.ghostsecure.utils.api_handlers;

import com.google.gson.JsonObject;
import me.ghosthacks96.ghostsecure.Main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Service for sending emails, primarily used for 2FA verification codes
 */
public class EmailService {
    private static final String API_URL = "https://ghosthacks96.me/site/GhostAPI/API.php";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int REQUEST_TIMEOUT_MS = 30000;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 2000; // 2 seconds delay between retries


    public EmailService() {

    }

    /**
     * Send a verification code to the specified email address using the recovery API
     * @param recipientEmail The email address to send the code to
     * @param verificationCode The verification code to send
     * @return true if the email was sent successfully, false otherwise
     */
    public boolean sendVerificationCode(String recipientEmail, String verificationCode) {
        // Create the payload for the API request
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "email_2fa");
        payload.addProperty("recipient", recipientEmail);
        payload.addProperty("code", verificationCode);

        // Convert the payload to a JSON string
        String jsonData = payload.toString();

        Main.logger.logDebug("Sending 2FA email request with payload: " + jsonData);

        // Implement retry logic
        int attempts = 0;
        Exception lastException = null;

        while (attempts < MAX_RETRY_ATTEMPTS) {
            attempts++;
            try {
                // Create and configure the HTTP connection
                URL url = URI.create(API_URL).toURL();
                HttpURLConnection connection = createConnection(url);

                // Send the request data
                sendRequestData(connection, jsonData);

                // Get the response code
                int responseCode = connection.getResponseCode();
                Main.logger.logInfo("API Response Status: " + responseCode + " (Attempt " + attempts + " of " + MAX_RETRY_ATTEMPTS + ")");

                // Read the response body
                String responseBody = readResponseBody(connection, responseCode);
                Main.logger.logDebug("API Response Body: " + responseBody);

                // Check if the request was successful
                boolean success = responseCode == 200;
                if (success) {
                    Main.logger.logInfo("Verification code sent to " + recipientEmail + " on attempt " + attempts);
                    return true;
                } else {
                    Main.logger.logWarning("Failed to send verification code on attempt " + attempts + 
                                          ". API returned status code: " + responseCode);
                    Main.logger.logDebug("Response body: " + responseBody);

                    // If this is not the last attempt, wait before retrying
                    if (attempts < MAX_RETRY_ATTEMPTS) {
                        Thread.sleep(RETRY_DELAY_MS);
                    }
                }
            } catch (SocketTimeoutException e) {
                lastException = e;
                Main.logger.logWarning("Timeout occurred on attempt " + attempts + ": " + e.getMessage());

                // If this is not the last attempt, wait before retrying
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        Main.logger.logError("Retry interrupted: " + ie.getMessage());
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Main.logger.logError("Retry interrupted: " + e.getMessage());
                break;
            } catch (Exception e) {
                lastException = e;
                Main.logger.logWarning("Error on attempt " + attempts + ": " + e.getMessage());

                // If this is not the last attempt, wait before retrying
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        Main.logger.logError("Retry interrupted: " + ie.getMessage());
                        break;
                    }
                }
            }
        }

        // All attempts failed
        if (lastException != null) {
            Main.logger.logError("Failed to send verification code after " + MAX_RETRY_ATTEMPTS + 
                               " attempts: " + lastException.getMessage(), lastException);
        } else {
            Main.logger.logError("Failed to send verification code after " + MAX_RETRY_ATTEMPTS + 
                               " attempts: All attempts returned non-200 status code");
        }
        return false;
    }

    /**
     * Create and configure an HTTP connection
     */
    private HttpURLConnection createConnection(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "GhostSecure/1.0");
        connection.setDoOutput(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(REQUEST_TIMEOUT_MS);
        return connection;
    }

    /**
     * Send the request data to the connection
     */
    private void sendRequestData(HttpURLConnection connection, String jsonData) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(jsonData);
            writer.flush();
            Main.logger.logDebug("Email 2FA request payload sent: " + jsonData);
        }
    }

    /**
     * Read the response body from the connection
     */
    private String readResponseBody(HttpURLConnection connection, int responseCode) throws IOException {
        // Get the appropriate input stream based on the response code
        java.io.InputStream inputStream = responseCode == 200 ?
                connection.getInputStream() : connection.getErrorStream();

        if (inputStream == null) {
            return "";
        }

        // Read the response body
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString().trim();
        }
    }
}
