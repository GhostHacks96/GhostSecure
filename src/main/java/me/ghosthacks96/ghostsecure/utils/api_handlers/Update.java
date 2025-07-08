package me.ghosthacks96.ghostsecure.utils.api_handlers;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.ghosthacks96.ghostsecure.Main;

/**
 * Handles application update functionality for GhostSecure
 */
public class Update {

    // Configuration constants
    private static final String API_URL = "https://ghosthacks96.me/site/GhostAPI/API.php";
    private static final String UPDATER_DOWNLOAD_URL = "https://ghosthacks96.me/site/downloads/GhostUpdate.exe";
    private static final String UPDATER_PATH = System.getenv("APPDATA") + "\\ghosthacks96\\GhostUpdate\\GhostUpdate.exe";
    private static final String NO_UPDATE_RESPONSE = "<noupdate>";
    private static final int CONNECTION_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;

    // Application information
    private static String APP_NAME;
    private static String CURRENT_VERSION;

    public Update(String appName, String currentVersion) {
        Main.logger.logDebug("Update constructor called with appName=" + appName + ", currentVersion=" + currentVersion);
        APP_NAME = appName;
        CURRENT_VERSION = currentVersion;
    }

    /**
     * Checks for updates and launches updater if available
     * @return true if the update is available and updater was launched successfully, false otherwise
     */
    public boolean updateCheck() {
        UpdateResponse response = checkForUpdates();
        Main.logger.logDebug("Update checkForUpdates() response: " + response);

        if (!isUpdateAvailable(response)) {
            return false;
        }

        Main.logger.logDebug("Update available - preparing to launch updater");
        updateUpdaterFile();

        if (launchUpdater()) {
            Main.logger.logDebug("Updater launched successfully, application will exit now");
            return true;
        } else {
            Main.logger.logError("Failed to launch updater, application will not exit");
            return false;
        }
    }

    /**
     * Downloads and updates the updater executable
     */
    public void updateUpdaterFile() {
        Main.logger.logDebug("updateUpdaterFile() called");

        try {
            URL downloadUrl = URI.create(UPDATER_DOWNLOAD_URL).toURL();
            Main.logger.logDebug("Downloading updater from: " + downloadUrl);

            Path targetPath = Paths.get(UPDATER_PATH);
            ensureParentDirectoryExists(targetPath);

            downloadFile(downloadUrl, targetPath);

            Main.logger.logDebug("Updater file downloaded and updated successfully at: " + UPDATER_PATH);
        } catch (Exception e) {
            Main.logger.logError("Error updating updater file: " + e.getMessage());
            Main.logger.logDebug("Exception: " + e);
        }
    }

    private void ensureParentDirectoryExists(Path targetPath) throws IOException {
        Path parentDir = targetPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
    }

    private void downloadFile(URL downloadUrl, Path targetPath) throws IOException {
        try (InputStream in = downloadUrl.openStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Checks for updates by sending a POST request to the API
     * @return UpdateResponse object or null if no update available or error occurred
     */
    public static UpdateResponse checkForUpdates() {
        Main.logger.logDebug("checkForUpdates() called");
        Main.logger.logDebug("Checking for updates...");

        try {
            UpdateRequest request = sendUpdateRequest();
            if (!request.isSuccessful()) {
                handleUpdateRequestError(request);
                return null;
            }

            return parseUpdateResponse(request.getResponseBody());

        } catch (Exception e) {
            Main.logger.logError("Error checking for updates: " + e.getMessage(), e);
            Main.logger.logDebug("Exception: " + e);
            Main.logger.logException(e);
            return null;
        }
    }

    private static UpdateRequest sendUpdateRequest() throws Exception {
        URL url = URI.create(API_URL).toURL();
        Main.logger.logDebug("API URL: " + API_URL);

        HttpURLConnection connection = createUpdateConnection(url);
        JsonObject payload = createUpdatePayload();

        sendRequestPayload(connection, payload);

        int responseCode = connection.getResponseCode();
        Main.logger.logDebug("Update check response code: " + responseCode);

        String responseBody = readResponseBody(connection, responseCode);
        Main.logger.logDebug("Update check response body: " + responseBody);

        return new UpdateRequest(responseCode, responseBody);
    }

    private static HttpURLConnection createUpdateConnection(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        return connection;
    }

    private static JsonObject createUpdatePayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "update");
        payload.addProperty("version", CURRENT_VERSION);
        payload.addProperty("appname", APP_NAME);
        return payload;
    }

    private static void sendRequestPayload(HttpURLConnection connection, JsonObject payload) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
            writer.write(payload.toString());
            writer.flush();
            Main.logger.logDebug("Update check POST payload sent: " + payload);
        }
    }

    private static String readResponseBody(HttpURLConnection connection, int responseCode) throws IOException {
        InputStream inputStream = responseCode == 200 ?
                connection.getInputStream() : connection.getErrorStream();

        if (inputStream == null) {
            return "";
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString().trim();
    }

    private static void handleUpdateRequestError(UpdateRequest request) {
        int responseCode = request.getResponseCode();
        Main.logger.logError("HTTP Error: " + responseCode);

        if (!request.getResponseBody().isEmpty()) {
            Main.logger.logError("Error response: " + request.getResponseBody());
        }
    }

    private static UpdateResponse parseUpdateResponse(String responseBody) {
        if (NO_UPDATE_RESPONSE.equals(responseBody)) {
            Main.logger.logDebug("No update available - application is up to date");
            return null;
        }

        try {
            Gson gson = new Gson();
            UpdateResponse updateResponse = gson.fromJson(responseBody, UpdateResponse.class);
            Main.logger.logDebug("Update check response: " + updateResponse);
            Main.logger.logDebug("Parsed UpdateResponse: " + updateResponse);
            return updateResponse;
        } catch (Exception e) {
            Main.logger.logError("Error parsing JSON response: " + e.getMessage(), e);
            Main.logger.logDebug("Exception: " + e);
            Main.logger.logException(e);
            return null;
        }
    }

    /**
     * Launches the updater with the specified arguments
     * @return true if updater was launched successfully, false otherwise
     */
    public static boolean launchUpdater() {
        Main.logger.logDebug("launchUpdater() called");
        Main.logger.logDebug("Attempting to launch updater...");

        try {
            File updaterFile = new File(UPDATER_PATH);
            if (!validateUpdaterFile(updaterFile)) {
                return false;
            }

            String[] updaterArgs = createUpdaterArguments();
            Process process = startUpdaterProcess(updaterFile, updaterArgs);

            Main.logger.logDebug("Updater process started");
            Main.logger.logDebug("Updater launched successfully with PID: " + process.pid());
            return true;

        } catch (Exception e) {
            Main.logger.logError("Error launching updater: " + e.getMessage(), e);
            Main.logger.logDebug("Exception: " + e);
            Main.logger.logException(e);
            return false;
        }
    }

    private static boolean validateUpdaterFile(File updaterFile) {
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
        return true;
    }

    private static String[] createUpdaterArguments() {
        String debug = Main.DEBUG_MODE ? "true" : "false";

        if (Main.DEBUG_MODE) {
            Main.logger.logDebug("Debug mode is enabled, passing debug argument to updater");
        }

        return new String[]{
                UPDATER_PATH,
                APP_NAME,
                CURRENT_VERSION,
                debug
        };
    }

    private static Process startUpdaterProcess(File updaterFile, String[] args) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        return runtime.exec(args, null, new File(updaterFile.getParent()));
    }

    /**
     * Utility method to check if an update is available without launching updater
     * @return true if the update is available, false otherwise
     */
    public static boolean isUpdateAvailable() {
        UpdateResponse response = checkForUpdates();
        return isUpdateAvailable(response);
    }

    private static boolean isUpdateAvailable(UpdateResponse response) {
        return response != null && response.update_available;
    }

    /**
     * Get the latest version information without launching updater
     * @return UpdateResponse or null if no update available
     */
    public static UpdateResponse getUpdateInfo() {
        return checkForUpdates();
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
     * Helper class to encapsulate HTTP request/response data
     */
    private static class UpdateRequest {
        private final int responseCode;
        private final String responseBody;

        public UpdateRequest(int responseCode, String responseBody) {
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }

        public int getResponseCode() {
            return responseCode;
        }

        public String getResponseBody() {
            return responseBody;
        }

        public boolean isSuccessful() {
            return responseCode == 200;
        }
    }
}