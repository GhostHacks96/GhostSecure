package me.ghosthacks96.ghostsecure.utils.api_handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.utils.encryption.EncryptionUtils;
import me.ghosthacks96.ghostsecure.utils.file_handlers.Logging;
import me.ghosthacks96.ghostsecure.gui.SubGUIHandler;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Handles password recovery functionality for GhostSecure
 */
public class RecoveryHandler {

    // Configuration constants
    private static final String RECOVERY_FILE = Main.APP_DATA_PATH + "rk.txt";
    private static final String RECOVERY_API_URL = "https://ghosthacks96.me/site/GhostAPI/API.php";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int REQUEST_TIMEOUT_MS = 30000;

    // Dependencies
    private final Logging logger;
    private final SubGUIHandler sgh;

    public RecoveryHandler() {
        this.logger = Main.logger;
        this.sgh = Main.sgh;
    }

    /**
     * Checks if recovery mode should be activated
     * @return true if a recovery file exists and recovery should be attempted
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

            String recoveryKey = readRecoveryKey();
            if (!isValidRecoveryKey(recoveryKey)) {
                return false;
            }

            assert recoveryKey != null;
            if (!validateRecoveryKeyWithAPI(recoveryKey.trim())) {
                return false;
            }

            String newPassword = promptForNewPassword();
            if (newPassword == null) {
                showRecoveryErrorMessage("Password reset cancelled by user");
                return false;
            }

            if (updatePasswordInConfig(newPassword)) {
                deleteRecoveryFile();
                showRecoverySuccessMessage();
                return true;
            } else {
                showRecoveryErrorMessage("Failed to update password. Please try again.");
                return false;
            }

        } catch (Exception e) {
            handleRecoveryException(e);
            return false;
        }
    }

    private boolean isValidRecoveryKey(String recoveryKey) {
        if (recoveryKey == null || recoveryKey.trim().isEmpty()) {
            logger.logError("Recovery key is empty or invalid");
            sgh.showError("Recovery Error", "Recovery key file is empty or invalid.");
            return false;
        }
        return true;
    }

    private String promptForNewPassword() {
        String newPassword = sgh.showSetPassPrompt();
        if (newPassword == null || newPassword.isEmpty()) {
            logger.logError("Password reset cancelled by user");
            sgh.showError("Recovery Cancelled", "Password reset was cancelled.");
            return null;
        }
        return newPassword;
    }

    private void showRecoverySuccessMessage() {
        sgh.showInfo("Recovery Complete",
                "Password has been reset successfully. The recovery file has been removed.");
    }

    private void showRecoveryErrorMessage(String message) {
        logger.logError("Failed to update password during recovery");
        sgh.showError("Recovery Error", message);
    }

    private void handleRecoveryException(Exception e) {
        logger.logError("Error during recovery process: " + e.getMessage(), e);
        sgh.showError("Recovery Error", "An error occurred during recovery: " + e.getMessage());
        logger.logException(e);
    }

    /**
     * Reads the recovery key from the rk.txt file
     * @return the recovery key string, or null if there's an error
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
            return key.trim();
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
    private boolean validateRecoveryKeyWithAPI(String recoveryKey) {
        try {
            logger.logInfo("Validating recovery key with API...");

            APIResponse response = sendRecoveryValidationRequest(recoveryKey);
            if (!response.isSuccessful()) {
                handleAPIErrorResponse(response);
                return false;
            }

            return parseValidationResponse(response.getBody());

        } catch (Exception e) {
            handleAPIException(e);
            return false;
        }
    }

    private APIResponse sendRecoveryValidationRequest(String recoveryKey) throws Exception {
        JsonObject payload = createRecoveryPayload(recoveryKey);
        String jsonData = payload.toString();

        logger.logDebug("JSON payload: " + jsonData);

        URL url = URI.create(RECOVERY_API_URL).toURL();
        HttpURLConnection connection = createConnection(url);

        sendRequestData(connection, jsonData);

        int responseCode = connection.getResponseCode();
        logger.logInfo("API Response Status: " + responseCode);

        String responseBody = readResponseBody(connection, responseCode);

        return new APIResponse(responseCode, responseBody);
    }

    private JsonObject createRecoveryPayload(String recoveryKey) {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "recovery");
        payload.addProperty("key", recoveryKey);
        return payload;
    }

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

    private void sendRequestData(HttpURLConnection connection, String jsonData) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(jsonData);
            writer.flush();
            logger.logDebug("Recovery validation POST payload sent: " + jsonData);
        }
    }

    private String readResponseBody(HttpURLConnection connection, int responseCode) throws IOException {
        InputStream inputStream = responseCode == 200 ?
                connection.getInputStream() : connection.getErrorStream();

        if (inputStream == null) {
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString().trim();
        }
    }

    private void handleAPIErrorResponse(APIResponse response) {
        int responseCode = response.getResponseCode();
        logger.logError("API request failed with status code: " + responseCode);

        if (!response.getBody().isEmpty()) {
            logger.logDebug("API Error Response: " + response.getBody());
        }

        String errorMessage = getErrorMessageForStatusCode(responseCode);
        sgh.showError("Recovery Error", errorMessage);
    }

    private String getErrorMessageForStatusCode(int statusCode) {
        return switch (statusCode) {
            case 400 -> {
                logger.logError("Bad request - check recovery key format");
                yield "Invalid recovery key format.";
            }
            case 404 -> {
                logger.logError("Recovery API endpoint not found");
                yield "Recovery service unavailable. Please contact support.";
            }
            case 500 -> {
                logger.logError("Server error during recovery validation");
                yield "Server error. Please try again later.";
            }
            default -> {
                logger.logError("Unexpected HTTP error: " + statusCode);
                yield "Network error. Please check your connection and try again.";
            }
        };
    }

    private boolean parseValidationResponse(String responseBody) {
        if (responseBody.isEmpty()) {
            logger.logError("API response body is empty");
            sgh.showError("Recovery Error", "Empty response from server. Please try again.");
            return false;
        }

        try {
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            logger.logDebug("API Response Body: " + responseBody);

            if (jsonResponse.has("valid")) {
                boolean isValid = jsonResponse.get("valid").getAsBoolean();
                logger.logInfo("Recovery key validation result: " + isValid);

                logAPIMessage(jsonResponse);
                return isValid;
            }

            if (jsonResponse.has("error")) {
                String errorMsg = jsonResponse.get("error").getAsString();
                logger.logError("API returned error: " + errorMsg);
                sgh.showError("Recovery Error", "Server error: " + errorMsg);
                return false;
            }

            logger.logError("API response missing expected fields");
            logger.logError("Response was: " + responseBody);
            sgh.showError("Recovery Error", "Invalid response from server. Please contact support.");
            return false;

        } catch (Exception jsonException) {
            logger.logError("Failed to parse JSON response: " + jsonException.getMessage());
            logger.logError("Response body was: " + responseBody);
            sgh.showError("Recovery Error", "Invalid response format from server. Please contact support.");
            return false;
        }
    }

    private void logAPIMessage(JsonObject jsonResponse) {
        if (jsonResponse.has("message")) {
            String message = jsonResponse.get("message").getAsString();
            logger.logInfo("API message: " + message);
        }
    }

    @SuppressWarnings("All")
    private void handleAPIException(Exception e) {
        switch (e) {
            case java.net.SocketTimeoutException socketTimeoutException -> {
                logger.logError("Request timed out: " + e.getMessage(), e);
                sgh.showError("Network Error", "Request timed out. Please check your internet connection and try again.");
            }
            case java.net.ConnectException connectException -> {
                logger.logError("Connection failed: " + e.getMessage(), e);
                sgh.showError("Network Error", "Could not connect to recovery server. Please check your internet connection.");
            }
            case IOException ioException -> {
                logger.logError("IO error during API request: " + e.getMessage(), e);
                sgh.showError("Network Error", "Network communication error. Please try again.");
            }
            default -> {
                logger.logError("Unexpected error validating recovery key: " + e.getMessage(), e);
                logger.logError("Exception type: " + e.getClass().getSimpleName());
                sgh.showError("Recovery Error", "An unexpected error occurred. Please contact support.");
            }
        }
        logger.logException(e);
    }

    /**
     * Updates the password in the configuration
     * @param newPassword the new password to set
     * @return true if successful, false otherwise
     */
    private boolean updatePasswordInConfig(String newPassword) {
        try {
            logger.logInfo("Updating password in configuration...");

            String hashedPassword = EncryptionUtils.hashPassword(newPassword);
            logger.logDebug("Password hashed successfully");

            Main.config.setPasswordHash(hashedPassword);
            Main.config.getJsonConfig().addProperty("password", hashedPassword);
            Main.config.saveConfig();

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
            if (!recoveryFile.exists()) {
                logger.logDebug("Recovery file does not exist, nothing to delete");
                return;
            }

            if (recoveryFile.delete()) {
                logger.logInfo("Recovery file deleted successfully");
            } else {
                logger.logWarning("Failed to delete recovery file - file may be in use");
                recoveryFile.deleteOnExit();
            }
        } catch (SecurityException e) {
            logger.logError("Security error deleting recovery file: " + e.getMessage(), e);
            logger.logException(e);
        } catch (Exception e) {
            logger.logError("Error deleting recovery file: " + e.getMessage(), e);
            logger.logException(e);
        }
    }

    /**
     * Helper class to encapsulate API response data
     */
    @SuppressWarnings("All")
    private static class APIResponse {
        private final int responseCode;
        private final String body;

        public APIResponse(int responseCode, String body) {
            this.responseCode = responseCode;
            this.body = body;
        }

        public int getResponseCode() {
            return responseCode;
        }

        public String getBody() {
            return body;
        }

        public boolean isSuccessful() {
            return responseCode == 200;
        }
    }
}