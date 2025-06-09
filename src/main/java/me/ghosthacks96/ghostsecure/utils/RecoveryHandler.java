package me.ghosthacks96.ghostsecure.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.utils.controllers.Config;
import me.ghosthacks96.ghostsecure.utils.controllers.Logging;
import me.ghosthacks96.ghostsecure.utils.controllers.SubGUIHandler;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

/**
 * Handles password recovery functionality for GhostSecure
 */
public class RecoveryHandler {

    private static final String RECOVERY_FILE = "rk.txt";
    private static final String RECOVERY_API_URL = "https://ghosthacks96.me/site/GhostAPI/API.php";
    private static final int API_TIMEOUT_SECONDS = 10;

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
            logger.logError("Error during recovery process: " + e.getMessage());
            sgh.showError("Recovery Error", "An error occurred during recovery: " + e.getMessage());
            e.printStackTrace();
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
            return key;

        } catch (IOException e) {
            logger.logError("Error reading recovery key file: " + e.getMessage());
            e.printStackTrace();
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

            // URL encode the recovery key
            String encodedKey = URLEncoder.encode(recoveryKey, StandardCharsets.UTF_8);
            String apiUrl = RECOVERY_API_URL + "?recovery=1&key=" + encodedKey;

            // Create HTTP client with timeout
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
                    .build();

            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
                    .header("User-Agent", "GhostSecure/1.0")
                    .GET()
                    .build();

            // Send request and get response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            logger.logInfo("API Response Status: " + response.statusCode());
            logger.logInfo("API Response Body: " + response.body());

            // Check if request was successful
            if (response.statusCode() != 200) {
                logger.logError("API request failed with status code: " + response.statusCode());
                return false;
            }

            // Parse JSON response
            try {
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

                if (jsonResponse.has("valid") && jsonResponse.get("valid").getAsBoolean()) {
                    logger.logInfo("Recovery key validation successful");
                    return true;
                } else {
                    logger.logError("Recovery key validation failed - key not valid");
                    return false;
                }

            } catch (Exception e) {
                logger.logError("Error parsing API response: " + e.getMessage());
                return false;
            }

        } catch (Exception e) {
            logger.logError("Error validating recovery key: " + e.getMessage());
            e.printStackTrace();
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
            // Hash the new password
            String hashedPassword = Main.hashPassword(newPassword);

            // Load existing config or create new one
            if (Main.config.getConfigFile().exists()) {
                Config.loadConfig(false);
            } else {
                Main.config.setDefaultConfig();
            }

            // Update password in config
            Config.PASSWORD_HASH = hashedPassword;
            Main.config.getJsonConfig().addProperty("password", hashedPassword);

            // Save updated config
            Config.saveConfig();

            logger.logInfo("Password updated successfully in configuration");
            return true;

        } catch (Exception e) {
            logger.logError("Error updating password: " + e.getMessage());
            e.printStackTrace();
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
                    logger.logWarning("Failed to delete recovery file");
                }
            }
        } catch (Exception e) {
            logger.logError("Error deleting recovery file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}