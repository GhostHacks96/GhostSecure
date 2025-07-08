package me.ghosthacks96.ghostsecure.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;
import me.ghosthacks96.ghostsecure.utils.encryption.EncryptionUtils;
import me.ghosthacks96.ghostsecure.utils.file_handlers.Config;
import me.ghosthacks96.ghostsecure.utils.services.ServiceController;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static me.ghosthacks96.ghostsecure.Main.logger;

/**
 * Main application GUI controller handling the home screen functionality
 */
public class HomeGUI {

    // Constants
    private static final String BACKUP_EXTENSION = "*.GSBACKUP";
    private static final String EXPORT_SEPARATOR = "!!_!!";
    private static final String STATIC_BACKUP_PASSWORD = "ThisIsBuLLShit";
    private static final int MIN_PASSWORD_LENGTH = 4;

    // UI Components - Program Management
    @FXML public Button addProgramButton;
    @FXML public Button removeProgramButton;
    @FXML public Button switchProgramLock;

    // UI Components - Folder Management
    @FXML public Button addFolderButton;
    @FXML public Button removeFolderButton;
    @FXML public Button switchFolderLock;

    // UI Components - Service Control
    @FXML private Button startServiceButton;
    @FXML private Button stopServiceButton;
    @FXML private Label lockStatus;

    // UI Components - Settings
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label passwordChangeStatus;

    // UI Components - Debug
    @FXML private CheckBox debugModeCheckBox;
    @FXML private Label debugStatus;

    // UI Components - Links
    @FXML private Hyperlink appDataLink;
    @FXML private Hyperlink discordLink;
    @FXML private Hyperlink githubLink;

    // Table Components - Folders
    @FXML private TableView<LockedItem> folderTable;
    @FXML private TableColumn<LockedItem, Boolean> folderCheckBox;
    @FXML private TableColumn<LockedItem, String> folderNameColumn;
    @FXML private TableColumn<LockedItem, String> folderPathColumn;
    @FXML private TableColumn<LockedItem, Boolean> folderStatusColumn;

    // Table Components - Programs
    @FXML private TableView<LockedItem> programTable;
    @FXML private TableColumn<LockedItem, Boolean> programCheckBox;
    @FXML private TableColumn<LockedItem, Boolean> programActionColumn;
    @FXML private TableColumn<LockedItem, String> programPathColumn;

    // Observable lists for table data
    private static final ObservableList<LockedItem> folderItems = FXCollections.observableArrayList();
    private static final ObservableList<LockedItem> programItems = FXCollections.observableArrayList();

    // ===============================
    // INITIALIZATION METHODS
    // ===============================

    @FXML
    public void initialize() {
        logger.logInfo("Initializing homeGUI.");

        setupTables();
        initializeDebugMode();
        refreshTableData();
        bindTableData();
        updateServiceStatus();


        logger.logInfo("HomeGUI initialization complete.");
    }

    private void setupTables() {
        setupFolderTable();
        setupProgramTable();
    }

    private void setupFolderTable() {
        folderTable.setSelectionModel(null);
        folderTable.setEditable(true);

        folderCheckBox.setCellValueFactory(cellData -> cellData.getValue().isSelectedProperty());
        folderCheckBox.setCellFactory(column -> createCheckBoxCell());
        folderNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        folderPathColumn.setCellValueFactory(new PropertyValueFactory<>("path"));
        folderStatusColumn.setCellValueFactory(cellData -> cellData.getValue().isLockedProperty());
    }

    private void setupProgramTable() {
        programTable.setSelectionModel(null);
        programTable.setEditable(true);

        programCheckBox.setCellValueFactory(cellData -> cellData.getValue().isSelectedProperty());
        programCheckBox.setCellFactory(column -> createCheckBoxCell());
        programPathColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        programActionColumn.setCellValueFactory(cellData -> cellData.getValue().isLockedProperty());
    }

    private CheckBoxTableCell<LockedItem, Boolean> createCheckBoxCell() {
        CheckBoxTableCell<LockedItem, Boolean> checkBoxCell = new CheckBoxTableCell<>();
        checkBoxCell.setEditable(true);
        return checkBoxCell;
    }

    private void bindTableData() {
        folderTable.setItems(folderItems);
        programTable.setItems(programItems);
    }

    // ===============================
    // DATA MANAGEMENT METHODS
    // ===============================

    public static void refreshTableData() {
        logger.logInfo("Refreshing table data.");

        folderItems.setAll(Main.lockedItems.stream()
                .filter(item -> !item.getPath().endsWith(".exe"))
                .collect(Collectors.toList()));

        programItems.setAll(Main.lockedItems.stream()
                .filter(item -> item.getPath().endsWith(".exe"))
                .collect(Collectors.toList()));
    }

    // ===============================
    // FOLDER MANAGEMENT METHODS
    // ===============================

    @FXML
    private void addFolder() {
        logger.logInfo("Adding a folder to be locked.");

        File selectedDirectory = new javafx.stage.DirectoryChooser().showDialog(new Stage());
        if (selectedDirectory == null) {
            logger.logWarning("No folder was selected to add.");
            return;
        }

        LockedItem lockedFolder = new LockedItem(
                selectedDirectory.getAbsolutePath(),
                selectedDirectory.getName(),
                true
        );

        Main.lockedItems.add(lockedFolder);
        Config.saveConfig();
        refreshTableData();
        logger.logInfo("Added folder: " + selectedDirectory.getName());
    }

    @FXML
    private void removeFolder() {
        logger.logInfo("Removing selected folders.");

        List<LockedItem> selectedFolders = getSelectedItems(folderItems);
        removeItemsAndSave(selectedFolders, "folder");
    }

    @FXML
    private void swapFLockStatus() {
        logger.logInfo("Toggling lock status for selected folders.");
        toggleLockStatusForSelectedItems(folderItems, "folder");
    }

    // ===============================
    // PROGRAM MANAGEMENT METHODS
    // ===============================

    @FXML
    private void addProgram() {
        logger.logInfo("Adding a program to be locked.");

        File selectedFile = selectExecutableFile();
        if (selectedFile == null) {
            logger.logWarning("No program was selected to add.");
            return;
        }

        LockedItem lockedProgram = new LockedItem(
                selectedFile.getAbsolutePath(),
                selectedFile.getName(),
                true
        );

        Main.lockedItems.add(lockedProgram);
        Config.saveConfig();
        refreshTableData();
        logger.logInfo("Added program: " + selectedFile.getName());
    }

    @FXML
    private void removeProgram() {
        logger.logInfo("Removing selected programs.");

        List<LockedItem> selectedPrograms = getSelectedItems(programItems);
        removeItemsAndSave(selectedPrograms, "program");
    }

    @FXML
    private void swapPLockStatus() {
        logger.logInfo("Toggling lock status for selected programs.");
        toggleLockStatusForSelectedItems(programItems, "program");
    }

    private File selectExecutableFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Program to Lock");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Executable Files", "*.exe")
        );
        return fileChooser.showOpenDialog(new Stage());
    }

    // ===============================
    // SERVICE CONTROL METHODS
    // ===============================

    @FXML
    public void startService() {
        logger.logInfo("Starting locking service.");

        setServiceMode("lock");
        Config.saveConfig();
        ServiceController.startBlockerDaemon();
        Platform.runLater(this::updateServiceStatus);

        logger.logInfo("Locking service started.");
    }

    @FXML
    public void stopService() {
        logger.logInfo("Stopping locking service.");

        setServiceMode("unlock");
        Config.saveConfig();
        ServiceController.stopBlockerDaemon();
        Platform.runLater(this::updateServiceStatus);

        logger.logInfo("Locking service stopped.");
    }

    public void updateServiceStatus() {
        boolean isRunning = ServiceController.isServiceRunning();
        logger.logInfo("Updating service status to " + (isRunning ? "RUNNING" : "STOPPED"));

        if (isRunning) {
            setServiceRunningUI();
            if (!ServiceController.isServiceRunning()) {
                ServiceController.startBlockerDaemon();
            }
        } else {
            setServiceStoppedUI();
        }
    }

    private void setServiceMode(String mode) {
        Main.config.getJsonConfig().remove("mode");
        Main.config.getJsonConfig().addProperty("mode", mode);
    }

    private void setServiceRunningUI() {
        startServiceButton.setDisable(true);
        stopServiceButton.setDisable(false);
        lockStatus.setText("Locking Engaged");
        lockStatus.setTextFill(javafx.scene.paint.Color.GREEN);
    }

    private void setServiceStoppedUI() {
        startServiceButton.setDisable(false);
        stopServiceButton.setDisable(true);
        lockStatus.setText("Locking Disabled");
        lockStatus.setTextFill(javafx.scene.paint.Color.RED);
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
        Config.passwordHash = newPasswordHash;
        Main.config.getJsonConfig().remove("password");
        Main.config.getJsonConfig().addProperty("password", newPasswordHash);
        Config.saveConfig();
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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @FXML
    private void openAppDataFolder() {
        try {
            File appDataDir = new File(Main.APP_DATA_PATH);
            if (!appDataDir.exists()) {
                appDataDir.mkdirs();
            }

            openDirectory(appDataDir);
        } catch (IOException e) {
            logger.logError("Failed to open AppData folder: " + e.getMessage());
        }
    }

    @FXML
    private void openDiscordInvite() {
        openUrl("https://discord.gg/Pn5U4whfnd", "Discord");
    }

    @FXML
    private void openGitHubRepo() {
        openUrl("https://github.com/ghosthacks96/ghostsecure", "GitHub");
    }

    private void openDirectory(File directory) throws IOException {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(directory);
        } else {
            new ProcessBuilder("explorer.exe", directory.getAbsolutePath()).start();
        }
    }

    private void openUrl(String url, String serviceName) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                new ProcessBuilder("cmd", "/c", "start", url).start();
            }
        } catch (IOException | URISyntaxException e) {
            logger.logError("Failed to open " + serviceName + " link: " + e.getMessage());
        }
    }

    // ===============================
    // IMPORT/EXPORT METHODS
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
            logger.logError("Failed to export settings: " + e.getMessage());
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
        } catch (Exception e) {
            logger.logError("Failed to import settings: " + e.getMessage());
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

        String configJson = Config.gson.toJson(Config.config);
        byte[] newSalt = EncryptionUtils.generateSalt();

        javax.crypto.SecretKey newKey = createBackupKey();
        String encrypted = EncryptionUtils.encrypt(configJson, newKey);
        String exportData = encrypted + EXPORT_SEPARATOR + Base64.getEncoder().encodeToString(newSalt);

        Files.writeString(file.toPath(), exportData);
    }

    private void performImport(File file) throws Exception {
        String fileContent = Files.readString(file.toPath());
        String[] parts = fileContent.split(EXPORT_SEPARATOR);

        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid backup file format.");
        }

        String encrypted = parts[0];
        byte[] salt = Base64.getDecoder().decode(parts[1]);

        javax.crypto.SecretKey importKey = createBackupKey();
        String decryptedJson = EncryptionUtils.decrypt(encrypted, importKey);

        if (decryptedJson == null) {
            throw new IllegalArgumentException("Failed to decrypt imported settings.");
        }

        JsonObject importedConfig = Config.gson.fromJson(decryptedJson, JsonObject.class);
        mergeImportedConfig(importedConfig);

        Config.saveConfig();
        refreshTableData();
    }

    private javax.crypto.SecretKey createBackupKey() throws Exception {
        byte[] keyBytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(STATIC_BACKUP_PASSWORD.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new javax.crypto.spec.SecretKeySpec(Arrays.copyOf(keyBytes, 32), "AES");
    }

    private void mergeImportedConfig(JsonObject importedConfig) {
        JsonArray importedPrograms = getJsonArrayOrEmpty(importedConfig, "programs");
        JsonArray importedFolders = getJsonArrayOrEmpty(importedConfig, "folders");

        JsonArray currentPrograms = getJsonArrayOrEmpty(Config.config, "programs");
        JsonArray currentFolders = getJsonArrayOrEmpty(Config.config, "folders");

        mergeArrays(currentPrograms, importedPrograms);
        mergeArrays(currentFolders, importedFolders);

        Config.config.add("programs", currentPrograms);
        Config.config.add("folders", currentFolders);
    }

    private JsonArray getJsonArrayOrEmpty(JsonObject config, String key) {
        return config.has(key) ? config.getAsJsonArray(key) : new JsonArray();
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
    // UTILITY METHODS
    // ===============================

    private List<LockedItem> getSelectedItems(ObservableList<LockedItem> items) {
        return items.stream()
                .filter(LockedItem::isSelected)
                .collect(Collectors.toList());
    }

    private void removeItemsAndSave(List<LockedItem> items, String itemType) {
        Main.lockedItems.removeAll(items);
        Config.saveConfig();
        refreshTableData();

        items.forEach(item ->
                logger.logInfo("Removed " + itemType + ": " + item.getName())
        );
    }

    private void toggleLockStatusForSelectedItems(ObservableList<LockedItem> items, String itemType) {
        items.stream()
                .filter(LockedItem::isSelected)
                .forEach(item -> {
                    item.setLocked(!item.isLocked());
                    item.setSelected(false);
                    logger.logInfo("Toggled lock status for " + itemType + ": " + item.getName());
                });

        Config.saveConfig();
        refreshTableData();
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
            return currentPasswordHash.equals(Config.passwordHash);
        }

        public boolean doNewPasswordsMatch() {
            return newPassword.equals(confirmPassword);
        }

        public boolean isNewPasswordLongEnough() {
            return newPassword.length() >= MIN_PASSWORD_LENGTH;
        }
    }
}

