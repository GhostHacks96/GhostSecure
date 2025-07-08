package me.ghosthacks96.ghostsecure.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;
import me.ghosthacks96.ghostsecure.utils.encryption.EncryptionUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static me.ghosthacks96.ghostsecure.Main.logger;

public class SettingsController {
    // Constants
    private static final String BACKUP_EXTENSION = "*.GSBACKUP";
    private static final String EXPORT_SEPARATOR = "!!_!!";
    private static final String STATIC_BACKUP_PASSWORD = "ThisIsBuLLShit";
    private static final int MIN_PASSWORD_LENGTH = 4;
    // UI Components - Settings
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label passwordChangeStatus;

    // UI Components - Debug
    @FXML private CheckBox debugModeCheckBox;
    @FXML private Label debugStatus;


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
            Main.shiftDebug(debugEnabled);
            updateDebugStatus(debugEnabled);
            logger.logInfo("Debug mode " + (debugEnabled ? "enabled" : "disabled") + " successfully.");
        } catch (Exception e) {
            logger.logError("Error toggling debug mode: " + e.getMessage());
            showDebugError("Error updating debug mode: " + e.getMessage());
        }
    }

    private boolean getDebugModeFromConfig() {
        if (Main.config.getJsonConfig().has("debugMode")) {
            return Main.config.getJsonConfig().get("debugMode").getAsBoolean();
        }
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
        Main.config.setPasswordHash(newPasswordHash);
        Main.config.getJsonConfig().remove("password");
        Main.config.getJsonConfig().addProperty("password", newPasswordHash);
        Main.config.saveConfig();
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

        // Get current config as JsonObject
        JsonObject configCopy = Main.config.getJsonConfig();

        // Create export structure with locked items
        JsonObject exportData = new JsonObject();
        exportData.add("config", configCopy);

        // Get locked items from main list and create export structure
        JsonArray programsArray = new JsonArray();
        JsonArray foldersArray = new JsonArray();

        for (LockedItem item : Main.lockedItems) {
            String formattedPath = item.getPath() + "[::]" + (item.isLocked() ? "locked" : "unlocked");

            if (item.getPath().endsWith(".exe")) {
                programsArray.add(formattedPath);
            } else {
                foldersArray.add(formattedPath);
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

        // Save the updated configuration
        Main.config.saveConfig();

    }

    private void mergeImportedConfig(JsonObject importedData) {
        // Get current config
        JsonObject currentConfig = Main.config.getJsonConfig();

        // Import programs and folders
        JsonArray importedPrograms = getJsonArrayOrEmpty(importedData, "programs");
        JsonArray importedFolders = getJsonArrayOrEmpty(importedData, "folders");

        // Get current programs and folders arrays
        JsonArray currentPrograms = getJsonArrayOrEmpty(currentConfig, "programs");
        JsonArray currentFolders = getJsonArrayOrEmpty(currentConfig, "folders");

        // Merge arrays (avoiding duplicates)
        Set<String> existingPrograms = new HashSet<>();
        Set<String> existingFolders = new HashSet<>();

        // Add existing items to sets
        for (int i = 0; i < currentPrograms.size(); i++) {
            existingPrograms.add(currentPrograms.get(i).getAsString());
        }
        for (int i = 0; i < currentFolders.size(); i++) {
            existingFolders.add(currentFolders.get(i).getAsString());
        }

        // Process imported programs
        for (int i = 0; i < importedPrograms.size(); i++) {
            String programEntry = importedPrograms.get(i).getAsString();
            if (!existingPrograms.contains(programEntry)) {
                // Parse the imported item and add to main list
                LockedItem item = parseImportedItem(programEntry);
                if (item != null) {
                    Main.lockedItems.add(item);
                }
            }
        }

        // Process imported folders
        for (int i = 0; i < importedFolders.size(); i++) {
            String folderEntry = importedFolders.get(i).getAsString();
            if (!existingFolders.contains(folderEntry)) {
                // Parse the imported item and add to main list
                LockedItem item = parseImportedItem(folderEntry);
                if (item != null) {
                    Main.lockedItems.add(item);
                }
            }
        }
    }

    private LockedItem parseImportedItem(String itemEntry) {
        try {
            String[] parts = itemEntry.split("\\Q[::]\\E");
            if (parts.length != 2) {
                logger.logWarning("Invalid format for imported item: " + itemEntry);
                return null;
            }

            String path = parts[0];
            String lockStatus = parts[1];
            String name = path.substring(path.lastIndexOf(File.separator) + 1);
            boolean isLocked = "locked".equalsIgnoreCase(lockStatus);

            return new LockedItem(path, name, isLocked);
        } catch (Exception e) {
            logger.logError("Error parsing imported item: " + itemEntry, e);
            return null;
        }
    }

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
            return currentPasswordHash.equals(Main.config.getPasswordHash());
        }

        public boolean doNewPasswordsMatch() {
            return newPassword.equals(confirmPassword);
        }

        public boolean isNewPasswordLongEnough() {
            return newPassword.length() >= MIN_PASSWORD_LENGTH;
        }
    }
}
