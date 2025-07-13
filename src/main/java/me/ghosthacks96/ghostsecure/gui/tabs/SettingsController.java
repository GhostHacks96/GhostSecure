package me.ghosthacks96.ghostsecure.gui.tabs;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.utils.encryption.EncryptionUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static me.ghosthacks96.ghostsecure.Main.*;

public class SettingsController {
    // Constants
    private static final String BACKUP_EXTENSION = "*.GSBACKUP";
    private static final String EXPORT_SEPARATOR = "!!_!!";
    private static final String STATIC_BACKUP_PASSWORD = "ThisIsBuLLShit";
    private static final int MIN_PASSWORD_LENGTH = 4;
    public CheckBox twoFactorAuthCheckBox;
    // UI Components - Settings
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label passwordChangeStatus;

    // UI Components - Debug
    @FXML private CheckBox debugModeCheckBox;
    @FXML private Label debugStatus;

    // UI Components - Auto-start
    @FXML private CheckBox autoStartCheckBox;
    @FXML private Label autoStartStatus;



    // ===============================
    // AUTO-START METHODS
    // ===============================

    @FXML
    public void initialize() {
        initializeDebugMode();
        initializeAutoStart();
        if(Main.use2FA){
            Main.use2FA = false;
            Main.logger.logInfo("Two-Factor Authentication disabled.");
            twoFactorAuthCheckBox.setSelected(false);
        } else {
            Main.use2FA = true;
            Main.logger.logInfo("Two-Factor Authentication enabled.");
            twoFactorAuthCheckBox.setSelected(true);
        }
    }

    private void initializeAutoStart() {
        try {
            boolean autoStartEnabled = getAutoStartSetting();
            autoStartCheckBox.setSelected(autoStartEnabled);
            updateAutoStartStatus(autoStartEnabled);
            logger.logInfo("Auto-start initialized: " + autoStartEnabled);
        } catch (Exception e) {
            logger.logError("Error initializing auto-start: " + e.getMessage());
            autoStartCheckBox.setSelected(false);
            updateAutoStartStatus(false);
        }
    }

    @FXML
    private void toggleAutoStart() {
        boolean autoStartEnabled = autoStartCheckBox.isSelected();
        logger.logInfo("Toggling auto-start to: " + autoStartEnabled);

        try {
            // Update in systemConfigStorage (unencrypted storage for system settings)
            systemConfigStorage.put("auto_start", autoStartEnabled);
            systemConfigStorage.saveData();

            updateAutoStartStatus(autoStartEnabled);
            logger.logInfo("Auto-start " + (autoStartEnabled ? "enabled" : "disabled") + " successfully.");
        } catch (Exception e) {
            logger.logError("Error toggling auto-start: " + e.getMessage());
            showAutoStartError("Error updating auto-start: " + e.getMessage());
        }
    }

    private boolean getAutoStartSetting() {
        // First check if auto-start exists in systemConfigStorage (after migration)
        if (systemConfigStorage.containsKey("auto_start")) {
            return systemConfigStorage.get("auto_start", false);
        }
        return false;
    }

    private void updateAutoStartStatus(boolean autoStartEnabled) {
        autoStartStatus.getStyleClass().removeAll("error-label", "success-label");

        if (autoStartEnabled) {
            autoStartStatus.setText("Auto-start is ENABLED - Application will start minimized to system tray on boot");
            autoStartStatus.setTextFill(javafx.scene.paint.Color.GREEN);
            autoStartStatus.getStyleClass().add("success-label");
        } else {
            autoStartStatus.setText("Auto-start is DISABLED - Login required on startup");
            autoStartStatus.setTextFill(javafx.scene.paint.Color.GRAY);
        }
    }

    private void showAutoStartError(String message) {
        autoStartStatus.setText(message);
        autoStartStatus.getStyleClass().removeAll("success-label");
        autoStartStatus.getStyleClass().add("error-label");
    }

    // ===============================
    // DEBUG MODE METHODS
    // ===============================

    private void initializeDebugMode() {
        try {
            boolean debugEnabled = getDebugModeFromConfig();
            debugModeCheckBox.setSelected(debugEnabled);
            updateDebugStatus(debugEnabled);
            logger.logInfo("Debug mode initialized: " + debugEnabled);
        } catch (Exception e) {
            logger.logError("Error initializing debug mode: " + e.getMessage());
            debugModeCheckBox.setSelected(false);
            updateDebugStatus(false);
        }
    }

    @FXML
    private void toggleDebugMode() {
        boolean debugEnabled = debugModeCheckBox.isSelected();
        logger.logInfo("Toggling debug mode to: " + debugEnabled);

        try {
            // Update debug mode in the application
            Main.shiftDebug(debugEnabled);

            // Save to systemConfigStorage
            systemConfigStorage.put("debug_mode", debugEnabled);
            systemConfigStorage.saveData();

            updateDebugStatus(debugEnabled);
            logger.logInfo("Debug mode " + (debugEnabled ? "enabled" : "disabled") + " successfully.");
        } catch (Exception e) {
            logger.logError("Error toggling debug mode: " + e.getMessage());
            showDebugError("Error updating debug mode: " + e.getMessage());
        }
    }

    private boolean getDebugModeFromConfig() {
        // First check if debug mode exists in systemConfigStorage
        if (systemConfigStorage.containsKey("debug_mode")) {
            return systemConfigStorage.get("debug_mode", false);
        } 
        // If not in systemConfigStorage, check in config (before migration)
        return false;
    }

    private void updateDebugStatus(boolean debugEnabled) {
        debugStatus.getStyleClass().removeAll("error-label", "success-label");

        if (debugEnabled) {
            debugStatus.setText("Debug mode is ENABLED - Detailed logging active");
            debugStatus.setTextFill(javafx.scene.paint.Color.ORANGE);
            debugStatus.getStyleClass().add("warning-label");
        } else {
            debugStatus.setText("Debug mode is DISABLED - Normal logging active");
            debugStatus.setTextFill(javafx.scene.paint.Color.GREEN);
        }
    }

    private void showDebugError(String message) {
        debugStatus.setText(message);
        debugStatus.getStyleClass().removeAll("success-label");
        debugStatus.getStyleClass().add("error-label");
    }

    // ===============================
    // PASSWORD MANAGEMENT METHODS
    // ===============================

    @FXML
    private void changePassword() {
        logger.logInfo("Attempting to change password.");

        PasswordChangeRequest request = getPasswordChangeRequest();
        if (!validatePasswordChangeRequest(request)) {
            return;
        }

        try {
            updatePassword(request.newPassword);
            clearPasswordFields();
            showPasswordChangeSuccess();
            logger.logInfo("Password changed successfully.");
        } catch (Exception e) {
            showPasswordChangeError("Error changing password: " + e.getMessage());
            logger.logError("Error changing password: " + e.getMessage());
        }
    }

    private PasswordChangeRequest getPasswordChangeRequest() {
        return new PasswordChangeRequest(
                currentPasswordField.getText(),
                newPasswordField.getText(),
                confirmPasswordField.getText()
        );
    }

    private boolean validatePasswordChangeRequest(PasswordChangeRequest request) {
        clearPasswordStatus();

        if (request.hasEmptyFields()) {
            showPasswordChangeError("All fields are required.");
            logger.logWarning("Password change failed: missing fields.");
            return false;
        }

        if (!request.isCurrentPasswordValid()) {
            showPasswordChangeError("Current password is incorrect.");
            logger.logWarning("Password change failed: incorrect current password.");
            return false;
        }

        if (!request.doNewPasswordsMatch()) {
            showPasswordChangeError("New passwords do not match.");
            logger.logWarning("Password change failed: passwords do not match.");
            return false;
        }

        if (!request.isNewPasswordLongEnough()) {
            showPasswordChangeError("Password must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
            logger.logWarning("Password change failed: password too short.");
            return false;
        }

        return true;
    }

    private void updatePassword(String newPassword) {
        String newPasswordHash = EncryptionUtils.hashPassword(newPassword);
        accountStorage.put("password_hash", newPasswordHash);
        accountStorage.saveData();
    }

    private void clearPasswordFields() {
        currentPasswordField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
    }

    private void clearPasswordStatus() {
        passwordChangeStatus.setText("");
        passwordChangeStatus.getStyleClass().removeAll("error-label", "success-label");
    }

    private void showPasswordChangeSuccess() {
        passwordChangeStatus.setText("Password changed successfully!");
        passwordChangeStatus.setTextFill(javafx.scene.paint.Color.GREEN);
    }

    private void showPasswordChangeError(String message) {
        passwordChangeStatus.setText(message);
        passwordChangeStatus.getStyleClass().add("error-label");
    }

    // ===============================
    // EXTERNAL LINKS METHODS
    // ===============================


// ===============================
// IMPORT/EXPORT METHODS (UPDATED)
// ===============================

    @FXML
    public void exportSettings(ActionEvent actionEvent) {
        FileChooser fileChooser = createBackupFileChooser("Export Settings");
        File file = fileChooser.showSaveDialog(new Stage());

        if (file == null) {
            logger.logWarning("Export cancelled by user.");
            return;
        }

        try {
            performExport(file);
            logger.logInfo("Settings exported successfully to " + file.getAbsolutePath());
        } catch (Exception e) {
            logger.logError("Failed to export settings: " + e.getMessage(), e);
            showExportError("Failed to export settings: " + e.getMessage());
        }
    }

    @FXML
    public void importSettings(ActionEvent actionEvent) {
        FileChooser fileChooser = createBackupFileChooser("Import Settings");
        File file = fileChooser.showOpenDialog(new Stage());

        if (file == null) {
            logger.logWarning("Import cancelled by user.");
            return;
        }

        try {
            performImport(file);
            logger.logInfo("Settings imported successfully from " + file.getAbsolutePath());
            showImportSuccess("Settings imported successfully!");
        } catch (Exception e) {
            logger.logError("Failed to import settings: " + e.getMessage(), e);
            showImportError("Failed to import settings: " + e.getMessage());
        }
    }

    private FileChooser createBackupFileChooser(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Backup File", BACKUP_EXTENSION)
        );
        return fileChooser;
    }

    private void performExport(File file) throws Exception {
        if (!file.createNewFile() && !file.exists()) {
            throw new IOException("Failed to create the export file: " + file.getAbsolutePath());
        }

        // Create export structure
        JsonObject exportData = new JsonObject();

        // Add system config data
        JsonObject configData = new JsonObject();
        Map<String, Object> systemConfig = systemConfigStorage.getAllData();
        for (Map.Entry<String, Object> entry : systemConfig.entrySet()) {
            if (entry.getValue() instanceof Boolean) {
                configData.addProperty(entry.getKey(), (Boolean) entry.getValue());
            } else if (entry.getValue() instanceof Number) {
                configData.addProperty(entry.getKey(), (Number) entry.getValue());
            } else if (entry.getValue() instanceof String) {
                configData.addProperty(entry.getKey(), (String) entry.getValue());
            }
        }
        exportData.add("config", configData);

        // Get programs from programStorage
        JsonArray programsArray = new JsonArray();
        Map<String, Object> programData = programStorage.getAllData();

        for (Map.Entry<String, Object> entry : programData.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Map<String, Object> itemData = (Map<String, Object>) entry.getValue();
                if ("PROGRAM".equals(itemData.get("type"))) {
                    String path = (String) itemData.get("path");
                    boolean locked = (boolean) itemData.get("locked");
                    String formattedPath = path + "[::]" + (locked ? "locked" : "unlocked");
                    programsArray.add(formattedPath);
                }
            }
        }

        // Get folders from folderStorage
        JsonArray foldersArray = new JsonArray();
        Map<String, Object> folderData = folderStorage.getAllData();

        for (Map.Entry<String, Object> entry : folderData.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Map<String, Object> itemData = (Map<String, Object>) entry.getValue();
                if ("FOLDER".equals(itemData.get("type"))) {
                    String path = (String) itemData.get("path");
                    boolean locked = (boolean) itemData.get("locked");
                    String formattedPath = path + "[::]" + (locked ? "locked" : "unlocked");
                    foldersArray.add(formattedPath);
                }
            }
        }

        exportData.add("programs", programsArray);
        exportData.add("folders", foldersArray);

        // Convert to JSON string
        String configJson = new Gson().toJson(exportData);

        // Create encryption key for export
        javax.crypto.SecretKey exportKey = createBackupKey();

        // Encrypt the export data
        String encrypted = EncryptionUtils.encrypt(configJson, exportKey);
        if (encrypted == null) {
            throw new Exception("Failed to encrypt export data");
        }

        // Create salt for export
        byte[] exportSalt = EncryptionUtils.generateSalt();
        String exportContent = encrypted + EXPORT_SEPARATOR + Base64.getEncoder().encodeToString(exportSalt);

        // Write to file
        Files.writeString(file.toPath(), exportContent);
    }

    private void performImport(File file) throws Exception {
        String fileContent = Files.readString(file.toPath());
        String[] parts = fileContent.split(EXPORT_SEPARATOR);

        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid backup file format.");
        }

        String encrypted = parts[0];
        byte[] salt = Base64.getDecoder().decode(parts[1]);

        // Create import key
        javax.crypto.SecretKey importKey = createBackupKey();

        // Decrypt the import data
        String decryptedJson = EncryptionUtils.decrypt(encrypted, importKey);
        if (decryptedJson == null) {
            throw new IllegalArgumentException("Failed to decrypt imported settings. Invalid backup file or corrupted data.");
        }

        // Parse imported data
        JsonObject importedData = new Gson().fromJson(decryptedJson, JsonObject.class);

        // Process the imported configuration
        mergeImportedConfig(importedData);

        // Note: mergeImportedConfig now saves changes to programStorage and folderStorage directly
    }

    private void mergeImportedConfig(JsonObject importedData) {
        // Import programs and folders
        JsonArray importedPrograms = getJsonArrayOrEmpty(importedData, "programs");
        JsonArray importedFolders = getJsonArrayOrEmpty(importedData, "folders");

        // Get existing programs and folders from storage
        Map<String, Object> existingProgramData = programStorage.getAllData();
        Map<String, Object> existingFolderData = folderStorage.getAllData();

        // Create sets of existing paths to avoid duplicates
        Set<String> existingProgramPaths = new HashSet<>();
        Set<String> existingFolderPaths = new HashSet<>();

        // Add existing program paths to set
        for (Map.Entry<String, Object> entry : existingProgramData.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Map<String, Object> itemData = (Map<String, Object>) entry.getValue();
                if ("PROGRAM".equals(itemData.get("type"))) {
                    String path = (String) itemData.get("path");
                    existingProgramPaths.add(path);
                }
            }
        }

        // Add existing folder paths to set
        for (Map.Entry<String, Object> entry : existingFolderData.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Map<String, Object> itemData = (Map<String, Object>) entry.getValue();
                if ("FOLDER".equals(itemData.get("type"))) {
                    String path = (String) itemData.get("path");
                    existingFolderPaths.add(path);
                }
            }
        }

        // Process imported programs
        for (int i = 0; i < importedPrograms.size(); i++) {
            String programEntry = importedPrograms.get(i).getAsString();
            String[] parts = programEntry.split("\\Q[::]\\E");
            if (parts.length == 2) {
                String path = parts[0];
                String lockStatus = parts[1];
                boolean isLocked = "locked".equalsIgnoreCase(lockStatus);

                // Check if program already exists
                if (!existingProgramPaths.contains(path)) {
                    // Create program data
                    Map<String, Object> programData = new HashMap<>();
                    String name = path.substring(path.lastIndexOf(File.separator) + 1);
                    programData.put("path", path);
                    programData.put("name", name);
                    programData.put("locked", isLocked);
                    programData.put("type", "PROGRAM");

                    // Generate a new key for the program
                    String newKey = "program_" + System.currentTimeMillis() + "_" + i;

                    // Add program to storage
                    programStorage.put(newKey, programData);
                    logger.logInfo("Imported program: " + name);
                }
            }
        }

        // Process imported folders
        for (int i = 0; i < importedFolders.size(); i++) {
            String folderEntry = importedFolders.get(i).getAsString();
            String[] parts = folderEntry.split("\\Q[::]\\E");
            if (parts.length == 2) {
                String path = parts[0];
                String lockStatus = parts[1];
                boolean isLocked = "locked".equalsIgnoreCase(lockStatus);

                // Check if folder already exists
                if (!existingFolderPaths.contains(path)) {
                    // Create folder data
                    Map<String, Object> folderData = new HashMap<>();
                    String name = path.substring(path.lastIndexOf(File.separator) + 1);
                    folderData.put("path", path);
                    folderData.put("name", name);
                    folderData.put("locked", isLocked);
                    folderData.put("type", "FOLDER");

                    // Generate a new key for the folder
                    String newKey = "folder_" + System.currentTimeMillis() + "_" + i;

                    // Add folder to storage
                    folderStorage.put(newKey, folderData);
                    logger.logInfo("Imported folder: " + name);
                }
            }
        }

        // Save changes
        programStorage.saveData();
        folderStorage.saveData();
    }

    // Note: parseImportedItem method has been removed as it's no longer needed.
    // The import logic is now handled directly in mergeImportedConfig method.

    private JsonArray getJsonArrayOrEmpty(JsonObject config, String key) {
        return config.has(key) ? config.getAsJsonArray(key) : new JsonArray();
    }

    private javax.crypto.SecretKey createBackupKey() throws Exception {
        byte[] keyBytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(STATIC_BACKUP_PASSWORD.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new javax.crypto.spec.SecretKeySpec(Arrays.copyOf(keyBytes, 32), "AES");
    }

    // Helper methods for user feedback
    private void showExportError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Export Error");
            alert.setHeaderText("Failed to export settings");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showImportError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Import Error");
            alert.setHeaderText("Failed to import settings");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showImportSuccess(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Import Success");
            alert.setHeaderText("Settings imported successfully");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void mergeArrays(JsonArray current, JsonArray imported) {
        Set<String> currentSet = new HashSet<>();

        for (int i = 0; i < current.size(); i++) {
            currentSet.add(current.get(i).getAsString());
        }

        for (int i = 0; i < imported.size(); i++) {
            String entry = imported.get(i).getAsString();
            if (!currentSet.contains(entry)) {
                current.add(entry);
                currentSet.add(entry);
            }
        }
    }

    public void toggleTwoFactorAuth(ActionEvent actionEvent) {

        if(Main.use2FA){
            Main.use2FA = false;
            Main.logger.logInfo("Two-Factor Authentication disabled.");
            twoFactorAuthCheckBox.setSelected(false);
            systemConfigStorage.put("enable_2fa", false);
        } else {
            Main.use2FA = true;
            Main.logger.logInfo("Two-Factor Authentication enabled.");
            twoFactorAuthCheckBox.setSelected(true);
            systemConfigStorage.put("enable_2fa", true);
        }

    }

    // ===============================
    // INNER CLASSES
    // ===============================

    private static class PasswordChangeRequest {
        private final String currentPassword;
        private final String newPassword;
        private final String confirmPassword;

        public PasswordChangeRequest(String currentPassword, String newPassword, String confirmPassword) {
            this.currentPassword = currentPassword;
            this.newPassword = newPassword;
            this.confirmPassword = confirmPassword;
        }

        public boolean hasEmptyFields() {
            return currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty();
        }

        public boolean isCurrentPasswordValid() {
            String currentPasswordHash = EncryptionUtils.hashPassword(currentPassword);
            assert currentPasswordHash != null;
            return currentPasswordHash.equals(accountStorage.get("password_hash", ""));
        }

        public boolean doNewPasswordsMatch() {
            return newPassword.equals(confirmPassword);
        }

        public boolean isNewPasswordLongEnough() {
            return newPassword.length() >= MIN_PASSWORD_LENGTH;
        }
    }
}
