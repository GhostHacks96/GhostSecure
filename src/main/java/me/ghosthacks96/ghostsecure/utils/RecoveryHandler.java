package me.ghosthacks96.ghostsecure.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.utils.controllers.Config;
import me.ghosthacks96.ghostsecure.utils.controllers.Logging;
import me.ghosthacks96.ghostsecure.utils.controllers.SubGUIHandler;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Handles password recovery functionality for GhostSecure
 */
public class RecoveryHandler {

    private static final String RECOVERY_FILE = Main.appDataPath + "rk.txt";
    private static final String RECOVERY_API_URL = "https://ghosthacks96.me/site/GhostAPI/API.php";
    private static final int CONNECT_TIMEOUT_SECONDS = 15000; // Changed to milliseconds
    private static final int REQUEST_TIMEOUT_SECONDS = 30000; // Changed to milliseconds

    private final Logging logger;
    private final SubGUIHandler sgh;

    public RecoveryHandler() {
        this.logger = Main.logger;
        this.sgh = Main.sgh;
    }

    /**
     * Checks if recovery mode should be activated
     * @return true if recovery file exists and recovery should be attempted
     */
    public boolean shouldEnterRecoveryMode() {
        File recoveryFile = new File(RECOVERY_FILE);
        boolean exists = recoveryFile.exists();

        if (exists) {
            logger.logInfo("Recovery file detected: " + RECOVERY_FILE);
        }

        return exists;
    }

    /**
     * Initiates the password recovery process
     * @return true if recovery was successful, false otherwise
     */
    public boolean initiateRecovery() {
        try {
            logger.logInfo("Starting password recovery process...");

            // Read recovery key from file
            String recoveryKey = readRecoveryKey();
            if (recoveryKey == null || recoveryKey.trim().isEmpty()) {
                logger.logError("Recovery key is empty or invalid");
                sgh.showError("Recovery Error", "Recovery key file is empty or invalid.");
                return false;
            }

            // Validate recovery key with API
            if (!validateRecoveryKey(recoveryKey.trim())) {
                logger.logError("Recovery key validation failed");
                sgh.showError("Recovery Error", "Invalid recovery key. Please contact support.");
                return false;
            }

            logger.logInfo("Recovery key validated successfully");

            // Show password reset prompt
            String newPassword = sgh.showSetPassPrompt();
            if (newPassword == null || newPassword.isEmpty()) {
                logger.logError("Password reset cancelled by user");
                sgh.showError("Recovery Cancelled", "Password reset was cancelled.");
                return false;
            }

            // Update password in config
            if (updatePassword(newPassword)) {
                logger.logInfo("Password updated successfully during recovery");

                // Delete recovery file
                deleteRecoveryFile();

                sgh.showInfo("Recovery Complete", "Password has been reset successfully. The recovery file has been removed.");
                return true;
            } else {
                logger.logError("Failed to update password during recovery");
                sgh.showError("Recovery Error", "Failed to update password. Please try again.");
                return false;
            }

        } catch (Exception e) {
            logger.logError("Error during recovery process: " + e.getMessage(), e);
            sgh.showError("Recovery Error", "An error occurred during recovery: " + e.getMessage());
            logger.logException(e);
            return false;
        }
    }

    /**
     * Reads the recovery key from the rk.txt file
     * @return the recovery key string, or null if error
     */
    private String readRecoveryKey() {
        try {
            File recoveryFile = new File(RECOVERY_FILE);
            if (!recoveryFile.exists()) {
                logger.logError("Recovery file does not exist: " + RECOVERY_FILE);
                return null;
            }

            String key = Files.readString(recoveryFile.toPath(), StandardCharsets.UTF_8);
            logger.logInfo("Recovery key read from file successfully");
            return key.trim(); // Trim whitespace here
        } catch (IOException e) {
            logger.logError("Error reading recovery key file: " + e.getMessage(), e);
            logger.logException(e);
            return null;
        }
    }

    /**
     * Validates the recovery key with the remote API
     * @param recoveryKey the recovery key to validate
     * @return true if valid, false otherwise
     */
    private boolean validateRecoveryKey(String recoveryKey) {
        try {
            logger.logInfo("Validating recovery key with API...");

            // Create JSON payload to match API.php expectations
            JsonObject payload = new JsonObject();
            payload.addProperty("action", "recovery");
            payload.addProperty("key", recoveryKey);

            String jsonData = payload.toString();
            logger.logInfo("Sending recovery validation request...");
            logger.logDebug("JSON payload: " + jsonData);

            // Use HttpURLConnection like the Update class
            URL url = new URL(RECOVERY_API_URL);
            logger.logDebug("API URL: " + RECOVERY_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set request properties - matching Update class exactly
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "GhostSecure/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_SECONDS);
            connection.setReadTimeout(REQUEST_TIMEOUT_SECONDS);

            logger.logDebug("Sending request to: " + RECOVERY_API_URL);

            // Send request
            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(jsonData);
                writer.flush();
                logger.logDebug("Recovery validation POST payload sent: " + jsonData);
            }

            int responseCode = connection.getResponseCode();
            logger.logInfo("API Response Status: " + responseCode);

            // Check if request was successful
            if (responseCode != 200) {
                logger.logError("API request failed with status code: " + responseCode);

                // Try to read error response
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream()))) {
                    String line;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                        logger.logError("Error response: " + line);
                    }
                }

                // Handle specific HTTP error codes
                switch (responseCode) {
                    case 400:
                        logger.logError("Bad request - check recovery key format");
                        sgh.showError("Recovery Error", "Invalid recovery key format.");
                        break;
                    case 404:
                        logger.logError("Recovery API endpoint not found");
                        sgh.showError("Recovery Error", "Recovery service unavailable. Please contact support.");
                        break;
                    case 500:
                        logger.logError("Server error during recovery validation");
                        sgh.showError("Recovery Error", "Server error. Please try again later.");
                        break;
                    default:
                        logger.logError("Unexpected HTTP error: " + responseCode);
                        sgh.showError("Recovery Error", "Network error. Please check your connection and try again.");
                }
                return false;
            }

            // Read response
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            String responseBody = response.toString().trim();
            logger.logDebug("API Response Body: " + responseBody);

            // Check if response body is empty or null
            if (responseBody.isEmpty()) {
                logger.logError("API response body is empty");
                sgh.showError("Recovery Error", "Empty response from server. Please try again.");
                return false;
            }

            // Parse JSON response with better error handling
            try {
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if (jsonResponse.has("valid")) {
                    boolean isValid = jsonResponse.get("valid").getAsBoolean();
                    logger.logInfo("Recovery key validation result: " + isValid);

                    // Log additional info if available
                    if (jsonResponse.has("message")) {
                        String message = jsonResponse.get("message").getAsString();
                        logger.logInfo("API message: " + message);
                    }

                    return isValid;
                } else if (jsonResponse.has("error")) {
                    String errorMsg = jsonResponse.get("error").getAsString();
                    logger.logError("API returned error: " + errorMsg);
                    sgh.showError("Recovery Error", "Server error: " + errorMsg);
                    return false;
                } else {
                    logger.logError("API response missing expected fields");
                    logger.logError("Response was: " + responseBody);
                    sgh.showError("Recovery Error", "Invalid response from server. Please contact support.");
                    return false;
                }
            } catch (Exception jsonException) {
                logger.logError("Failed to parse JSON response: " + jsonException.getMessage());
                logger.logError("Response body was: " + responseBody);
                sgh.showError("Recovery Error", "Invalid response format from server. Please contact support.");
                return false;
            }

        } catch (java.net.SocketTimeoutException timeoutException) {
            logger.logError("Request timed out: " + timeoutException.getMessage(), timeoutException);
            logger.logException(timeoutException);
            sgh.showError("Network Error", "Request timed out. Please check your internet connection and try again.");
            return false;
        } catch (java.net.ConnectException connectException) {
            logger.logError("Connection failed: " + connectException.getMessage(), connectException);
            logger.logException(connectException);
            sgh.showError("Network Error", "Could not connect to recovery server. Please check your internet connection.");
            return false;
        } catch (java.io.IOException ioException) {
            logger.logError("IO error during API request: " + ioException.getMessage(), ioException);
            logger.logException(ioException);
            sgh.showError("Network Error", "Network communication error. Please try again.");
            return false;
        } catch (Exception e) {
            logger.logError("Unexpected error validating recovery key: " + e.getMessage(), e);
            logger.logException(e);
            logger.logError("Exception type: " + e.getClass().getSimpleName());
            sgh.showError("Recovery Error", "An unexpected error occurred. Please contact support.");
            return false;
        }
    }

    /**
     * Updates the password in the configuration
     * @param newPassword the new password to set
     * @return true if successful, false otherwise
     */
    private boolean updatePassword(String newPassword) {
        try {
            logger.logInfo("Updating password in configuration...");

            // Hash the new password
            String hashedPassword = Main.hashPassword(newPassword);
            logger.logDebug("Password hashed successfully");

            // Update password in config
            Config.PASSWORD_HASH = hashedPassword;
            Main.config.getJsonConfig().addProperty("password", hashedPassword);

            // Save updated config
            Config.saveConfig();

            logger.logInfo("Password updated successfully in configuration");
            return true;
        } catch (Exception e) {
            logger.logError("Error updating password: " + e.getMessage(), e);
            logger.logException(e);
            return false;
        }
    }

    /**
     * Deletes the recovery key file
     */
    private void deleteRecoveryFile() {
        try {
            File recoveryFile = new File(RECOVERY_FILE);
            if (recoveryFile.exists()) {
                if (recoveryFile.delete()) {
                    logger.logInfo("Recovery file deleted successfully");
                } else {
                    logger.logWarning("Failed to delete recovery file - file may be in use");
                    // Try to delete on exit if immediate deletion fails
                    recoveryFile.deleteOnExit();
                }
            } else {
                logger.logDebug("Recovery file does not exist, nothing to delete");
            }
        } catch (SecurityException securityException) {
            logger.logError("Security error deleting recovery file: " + securityException.getMessage(), securityException);
            logger.logException(securityException);
        } catch (Exception e) {
            logger.logError("Error deleting recovery file: " + e.getMessage(), e);
            logger.logException(e);
        }
    }
}