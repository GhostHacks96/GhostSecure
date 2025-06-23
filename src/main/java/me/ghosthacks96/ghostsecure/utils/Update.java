package me.ghosthacks96.ghostsecure.utils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.ghosthacks96.ghostsecure.Main;

public class Update {

    private static final String API_URL = "https://ghosthacks96.me/site/GhostAPI/API.php";
    private static final String UPDATER_PATH = System.getenv("APPDATA") + "\\ghosthacks96\\GhostUpdate\\GhostUpdate.exe";
    private static  String APP_NAME ;
    private static  String CURRENT_VERSION ;

    public Update(String appName, String currentVersion) {
        Main.logger.logDebug("Update constructor called with appName=" + appName + ", currentVersion=" + currentVersion);
        APP_NAME = appName;
        CURRENT_VERSION = currentVersion;
        UpdateResponse response = checkForUpdates();
        Main.logger.logDebug("Update checkForUpdates() response: " + response);
        if(response != null && response.update_available){

            updateUpdaterFile();

            launchUpdater();
            Main.logger.logDebug("Update available - application will exit now");
            System.exit(0);
        }
    }
    
    public void updateUpdaterFile(){
        Main.logger.logDebug("updateUpdaterFile() called");
        try {
            URL downloadUrl = new URL("https://ghosthacks96.me/site/downloads/GhostUpdate.exe");
            Main.logger.logDebug("Downloading updater from: " + downloadUrl);
            java.nio.file.Path targetPath = java.nio.file.Paths.get(UPDATER_PATH);
            try (java.io.InputStream in = downloadUrl.openStream()) {
                java.nio.file.Files.copy(in, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Main.logger.logDebug("Updater file downloaded and updated successfully at: " + UPDATER_PATH);
        } catch (Exception e) {
            Main.logger.logError("Error updating updater file: " + e.getMessage());
            Main.logger.logDebug("Exception: " + e);
        }
    }
    
    

    /**
     * Data class to hold update response information
     */
    public static class UpdateResponse {
        public boolean update_available;
        public String latest_version;
        public String current_version;
        public String download_url;
        public String release_url;
        public String published_at;

        @Override
        public String toString() {
            return String.format("UpdateResponse{update_available=%s, latest_version='%s', current_version='%s'}",
                    update_available, latest_version, current_version);
        }
    }

    /**
     * Check for updates by sending POST request to the API
     * @return UpdateResponse object or null if no update available or error occurred
     */
    public static UpdateResponse checkForUpdates() {
        Main.logger.logDebug("checkForUpdates() called");
        Main.logger.logDebug("Checking for updates...");
        try {
            URL url = new URL(API_URL);
            Main.logger.logDebug("API URL: " + API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set request properties
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(10000);

            // Create JSON payload
            JsonObject payload = new JsonObject();
            payload.addProperty("action", "update");
            payload.addProperty("version", CURRENT_VERSION);
            payload.addProperty("appname", APP_NAME);

            // Send request
            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                writer.write(payload.toString());
                writer.flush();
                Main.logger.logDebug("Update check POST payload sent: " + payload);
            }
            int responseCode = connection.getResponseCode();
            Main.logger.logDebug("Update check response code: " + responseCode);
            if (responseCode == 200) {
                // Read response
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                String responseBody = response.toString().trim();
                Main.logger.logDebug("Update check response body: " + responseBody);

                // Check if response indicates no update
                if ("<noupdate>".equals(responseBody)) {
                    Main.logger.logDebug("No update available - application is up to date");
                    return null;
                }

                // Parse JSON response
                try {
                    Gson gson = new Gson();
                    UpdateResponse updateResponse = gson.fromJson(responseBody, UpdateResponse.class);
                    Main.logger.logDebug("Update check response: " + updateResponse);
                    Main.logger.logDebug("Parsed UpdateResponse: " + updateResponse);
                    return updateResponse;
                } catch (Exception e) {
                    Main.logger.logError("Error parsing JSON response: " + e.getMessage());
                    Main.logger.logError("Response body: " + responseBody);
                    Main.logger.logDebug("Exception: " + e);
                    return null;
                }

            } else {
                Main.logger.logError("HTTP Error: " + responseCode);
                // Try to read error response
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Main.logger.logError("Error response: " + line);
                    }
                }
                return null;
            }
        } catch (Exception e) {
            Main.logger.logError("Error checking for updates: " + e.getMessage());
            Main.logger.logDebug("Exception: " + e);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Launch the updater with the specified arguments
     * @return true if updater was launched successfully, false otherwise
     */
    public static boolean launchUpdater() {
        Main.logger.logDebug("launchUpdater() called");
        Main.logger.logDebug("Attempting to launch updater...");
        try {
            File updaterFile = new File(UPDATER_PATH);
            Main.logger.logDebug("Updater file path: " + UPDATER_PATH);
            if (!updaterFile.exists()) {
                Main.logger.logError("Updater not found at: " + UPDATER_PATH);
                return false;
            }
            if (!updaterFile.canExecute()) {
                Main.logger.logError("Updater is not executable: " + UPDATER_PATH);
                return false;
            }
            Main.logger.logDebug("Launching updater: " + UPDATER_PATH);

            String debug = "false";
            if(Main.DEBUG_MODE){
                debug = "true";
                Main.logger.logDebug("Debug mode is enabled, passing debug argument to updater");
            }
            // Build command with arguments
            ProcessBuilder processBuilder = new ProcessBuilder(
                    UPDATER_PATH,
                    APP_NAME,
                    CURRENT_VERSION,
                    debug

            );

            // Set working directory to the updater's directory
            processBuilder.directory(updaterFile.getParentFile());

            // Start the process
            Process process = processBuilder.start();
            Main.logger.logDebug("Updater process started");
            Main.logger.logDebug("Updater launched successfully with PID: " + process.pid());
            return true;
        } catch (Exception e) {
            Main.logger.logError("Error launching updater: " + e.getMessage());
            Main.logger.logDebug("Exception: " + e);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Utility method to check if update is available without launching updater
     * @return true if update is available, false otherwise
     */
    public static boolean isUpdateAvailable() {
        UpdateResponse response = checkForUpdates();
        return response != null && response.update_available;
    }

    /**
     * Get the latest version information without launching updater
     * @return UpdateResponse or null if no update available
     */
    public static UpdateResponse getUpdateInfo() {
        return checkForUpdates();
    }
}