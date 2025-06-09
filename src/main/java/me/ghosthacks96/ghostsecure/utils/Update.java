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
        APP_NAME = appName;
        CURRENT_VERSION = currentVersion;

        UpdateResponse response = checkForUpdates();
        if(response != null && response.update_available){
            launchUpdater();
            Main.logger.logInfo("Update available - application will exit now");
            System.exit(0);
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
        try {
            URL url = new URL(API_URL);
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
            }

            int responseCode = connection.getResponseCode();

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

                // Check if response indicates no update
                if ("<noupdate>".equals(responseBody)) {
                    System.out.println("No update available - application is up to date");
                    return null;
                }

                // Parse JSON response
                try {
                    Gson gson = new Gson();
                    UpdateResponse updateResponse = gson.fromJson(responseBody, UpdateResponse.class);
                    System.out.println("Update check response: " + updateResponse);
                    return updateResponse;
                } catch (Exception e) {
                    System.err.println("Error parsing JSON response: " + e.getMessage());
                    System.err.println("Response body: " + responseBody);
                    return null;
                }

            } else {
                System.err.println("HTTP Error: " + responseCode);
                // Try to read error response
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.err.println("Error response: " + line);
                    }
                }
                return null;
            }

        } catch (Exception e) {
            System.err.println("Error checking for updates: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Launch the updater with the specified arguments
     * @return true if updater was launched successfully, false otherwise
     */
    public static boolean launchUpdater() {
        try {
            File updaterFile = new File(UPDATER_PATH);

            if (!updaterFile.exists()) {
                System.err.println("Updater not found at: " + UPDATER_PATH);
                return false;
            }

            if (!updaterFile.canExecute()) {
                System.err.println("Updater is not executable: " + UPDATER_PATH);
                return false;
            }

            System.out.println("Launching updater: " + UPDATER_PATH);

            // Build command with arguments
            ProcessBuilder processBuilder = new ProcessBuilder(
                    UPDATER_PATH,
                    APP_NAME,
                    CURRENT_VERSION
            );

            // Set working directory to the updater's directory
            processBuilder.directory(updaterFile.getParentFile());

            // Start the process
            Process process = processBuilder.start();

            System.out.println("Updater launched successfully with PID: " + process.pid());
            return true;

        } catch (Exception e) {
            System.err.println("Error launching updater: " + e.getMessage());
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