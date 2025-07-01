package me.ghosthacks96.ghostsecure.gui;
// JavaFX imports

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
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

// Application imports
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;
import me.ghosthacks96.ghostsecure.utils.controllers.Config;
import me.ghosthacks96.ghostsecure.utils.controllers.ServiceController;

// Java imports
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;

import static me.ghosthacks96.ghostsecure.Main.logger;

/**
 * Main application GUI controller handling the home screen functionality
 */
public class HomeGUI {

    @FXML public Button addProgramButton;
    @FXML public Button removeProgramButton;
    @FXML public Button switchProgramLock;
    @FXML public Button addFolderButton;
    @FXML public Button removeFolderButton;
    @FXML public Button switchFolderLock;

    // UI Components
    @FXML private Button startServiceButton;
    @FXML private Button stopServiceButton;
    @FXML private Label lockStatus;

    // Settings Components
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label passwordChangeStatus;

    // Debug Components
    @FXML private CheckBox debugModeCheckBox;
    @FXML private Label debugStatus;

    // Table components - Folders
    @FXML private TableView<LockedItem> folderTable;
    @FXML private TableColumn<LockedItem, Boolean> folderCheckBox;
    @FXML private TableColumn<LockedItem, String> folderNameColumn;
    @FXML private TableColumn<LockedItem, String> folderPathColumn;
    @FXML private TableColumn<LockedItem, Boolean> folderStatusColumn;

    // Table components - Programs
    @FXML private TableView<LockedItem> programTable;
    @FXML private TableColumn<LockedItem, Boolean> programCheckBox;
    @FXML private TableColumn<LockedItem, Boolean> programActionColumn;
    @FXML private TableColumn<LockedItem, String> programPathColumn;

    @FXML
    private Hyperlink appDataLink;

    @FXML
    private Hyperlink discordLink;

    @FXML
    private Hyperlink githubLink;


    // Observable lists for table data
    private static final ObservableList<LockedItem> folderItems = FXCollections.observableArrayList();
    private static final ObservableList<LockedItem> programItems = FXCollections.observableArrayList();

    public static void refreshTableData() {
        logger.logInfo("Refreshing table data.");
        folderItems.setAll(Main.lockedItems.stream()
                .filter(item -> !item.getPath().endsWith(".exe"))
                .collect(Collectors.toList()));

        programItems.setAll(Main.lockedItems.stream()
                .filter(item -> item.getPath().endsWith(".exe"))
                .collect(Collectors.toList()));
    }

    @FXML
    public void initialize() {
        logger.logInfo("Initializing homeGUI.");

        // Disable row selection for tables
        folderTable.setSelectionModel(null);
        programTable.setSelectionModel(null);

        // Folder table setup
        folderCheckBox.setCellValueFactory(cellData -> cellData.getValue().isSelectedProperty());
        folderCheckBox.setCellFactory(column -> {
            CheckBoxTableCell<LockedItem, Boolean> checkBoxCell = new CheckBoxTableCell<>();
            checkBoxCell.setEditable(true);
            return checkBoxCell;
        });

        folderNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        folderPathColumn.setCellValueFactory(new PropertyValueFactory<>("path"));
        folderStatusColumn.setCellValueFactory(cellData -> cellData.getValue().isLockedProperty());
        folderTable.setEditable(true);

        // Program table setup
        programCheckBox.setCellValueFactory(cellData -> cellData.getValue().isSelectedProperty());
        programCheckBox.setCellFactory(column -> {
            CheckBoxTableCell<LockedItem, Boolean> checkBoxCell = new CheckBoxTableCell<>();
            checkBoxCell.setEditable(true);
            return checkBoxCell;
        });

        programPathColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        programActionColumn.setCellValueFactory(cellData -> cellData.getValue().isLockedProperty());
        programTable.setEditable(true);

        // Initialize debug checkbox state
        initializeDebugMode();

        // Populate initial data
        refreshTableData();
        folderTable.setItems(folderItems);
        programTable.setItems(programItems);

        // Update UI
        updateServiceStatus();
    }

    private void initializeDebugMode() {
        try {
            // Check if debug mode is enabled in config
            boolean debugEnabled = false;
            if (Main.config.getJsonConfig().has("debugMode")) {
                debugEnabled = Main.config.getJsonConfig().get("debugMode").getAsBoolean();
            }

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
            // Update UI status
            updateDebugStatus(debugEnabled);

            logger.logInfo("Debug mode " + (debugEnabled ? "enabled" : "disabled") + " successfully.");
        } catch (Exception e) {
            logger.logError("Error toggling debug mode: " + e.getMessage());
            debugStatus.setText("Error updating debug mode: " + e.getMessage());
            debugStatus.getStyleClass().removeAll("success-label");
            debugStatus.getStyleClass().add("error-label");
        }
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

    @FXML
    private void addFolder() {
        logger.logInfo("Adding a folder to be locked.");
        File selectedDirectory = new javafx.stage.DirectoryChooser().showDialog(new Stage());
        if (selectedDirectory != null) {
            LockedItem lockedFolder = new LockedItem(selectedDirectory.getAbsolutePath(), selectedDirectory.getName(), true);
            Main.lockedItems.add(lockedFolder);
            Config.saveConfig();
            refreshTableData();
            logger.logInfo("Added folder: " + selectedDirectory.getName());
        } else {
            logger.logWarning("No folder was selected to add.");
        }
    }

    @FXML
    private void removeFolder() {
        logger.logInfo("Removing selected folders.");
        List<LockedItem> selectedFolders = folderItems.stream().filter(LockedItem::isSelected).toList();
        Main.lockedItems.removeAll(selectedFolders);
        Config.saveConfig();
        refreshTableData();
        selectedFolders.forEach(item -> logger.logInfo("Removed folder: " + item.getName()));
    }

    @FXML
    private void addProgram() {
        logger.logInfo("Adding a program to be locked.");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Program to Lock");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Executable Files", "*.exe"));
        File selectedFile = fileChooser.showOpenDialog(new Stage());
        if (selectedFile != null) {
            LockedItem lockedProgram = new LockedItem(selectedFile.getAbsolutePath(), selectedFile.getName(), true);
            Main.lockedItems.add(lockedProgram);
            Config.saveConfig();
            refreshTableData();
            logger.logInfo("Added program: " + selectedFile.getName());
        } else {
            logger.logWarning("No program was selected to add.");
        }
    }

    @FXML
    private void removeProgram() {
        logger.logInfo("Removing selected programs.");
        List<LockedItem> selectedPrograms = programItems.stream().filter(LockedItem::isSelected).toList();
        Main.lockedItems.removeAll(selectedPrograms);
        Config.saveConfig();
        refreshTableData();
        selectedPrograms.forEach(item -> logger.logInfo("Removed program: " + item.getName()));
    }

    @FXML
    public void startService() {
        logger.logInfo("Starting locking service.");
        if (Main.config.getJsonConfig().get("mode").getAsString().equals("unlock")) {
            Main.config.getJsonConfig().remove("mode");
            Main.config.getJsonConfig().addProperty("mode", "lock");
        }
        Config.saveConfig();
        Platform.runLater(this::updateServiceStatus);
        logger.logInfo("Locking service started.");
    }

    @FXML
    public void stopService() {
        logger.logInfo("Stopping locking service.");
        if (Main.config.getJsonConfig().get("mode").getAsString().equals("lock")) {
            Main.config.getJsonConfig().remove("mode");
            Main.config.getJsonConfig().addProperty("mode", "unlock");
        }
        Config.saveConfig();
        Platform.runLater(this::updateServiceStatus);
        logger.logInfo("Locking service stopped.");
    }

    public void updateServiceStatus() {
        boolean isRunning = ServiceController.isServiceRunning();
        logger.logInfo("Updating service status to " + (isRunning ? "RUNNING" : "STOPPED"));
        if (isRunning) {
            startServiceButton.setDisable(true);
            stopServiceButton.setDisable(false);
            lockStatus.setText("Locking Engaged");
            lockStatus.setTextFill(javafx.scene.paint.Color.GREEN);
            if (!ServiceController.isServiceRunning()) {
                ServiceController.startBlockerDaemon();
            }
        } else {
            startServiceButton.setDisable(false);
            stopServiceButton.setDisable(true);
            lockStatus.setText("Locking Disabled");
            lockStatus.setTextFill(javafx.scene.paint.Color.RED);
        }
    }

    @FXML
    private void swapFLockStatus() {
        logger.logInfo("Toggling lock status for selected folders.");
        folderItems.stream().filter(LockedItem::isSelected).forEach(item -> {
            item.setLocked(!item.isLocked());
            item.setSelected(false);
            logger.logInfo("Toggled lock status for folder: " + item.getName());
        });
        Config.saveConfig();
        refreshTableData();
    }

    @FXML
    private void swapPLockStatus() {
        logger.logInfo("Toggling lock status for selected programs.");
        programItems.stream().filter(LockedItem::isSelected).forEach(item -> {
            item.setLocked(!item.isLocked());
            item.setSelected(false);
            logger.logInfo("Toggled lock status for program: " + item.getName());
        });
        Config.saveConfig();
        refreshTableData();
    }

    @FXML
    private void changePassword() {
        logger.logInfo("Attempting to change password.");

        String currentPassword = currentPasswordField.getText();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Clear previous status
        passwordChangeStatus.setText("");
        passwordChangeStatus.getStyleClass().removeAll("error-label", "success-label");

        // Validate inputs
        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            passwordChangeStatus.setText("All fields are required.");
            passwordChangeStatus.getStyleClass().add("error-label");
            logger.logWarning("Password change failed: missing fields.");
            return;
        }

        // Verify current password
        String currentPasswordHash = Main.hashPassword(currentPassword);
        if (!currentPasswordHash.equals(Config.PASSWORD_HASH)) {
            passwordChangeStatus.setText("Current password is incorrect.");
            passwordChangeStatus.getStyleClass().add("error-label");
            logger.logWarning("Password change failed: incorrect current password.");
            return;
        }

        // Verify new passwords match
        if (!newPassword.equals(confirmPassword)) {
            passwordChangeStatus.setText("New passwords do not match.");
            passwordChangeStatus.getStyleClass().add("error-label");
            logger.logWarning("Password change failed: passwords do not match.");
            return;
        }

        // Check minimum password length
        if (newPassword.length() < 4) {
            passwordChangeStatus.setText("Password must be at least 4 characters long.");
            passwordChangeStatus.getStyleClass().add("error-label");
            logger.logWarning("Password change failed: password too short.");
            return;
        }

        try {
            // Update password
            String newPasswordHash = Main.hashPassword(newPassword);
            Config.PASSWORD_HASH = newPasswordHash;
            Main.config.getJsonConfig().remove("password");
            Main.config.getJsonConfig().addProperty("password", newPasswordHash);
            Config.saveConfig();

            // Clear fields
            currentPasswordField.clear();
            newPasswordField.clear();
            confirmPasswordField.clear();

            // Show success message
            passwordChangeStatus.setText("Password changed successfully!");
            passwordChangeStatus.setTextFill(javafx.scene.paint.Color.GREEN);
            logger.logInfo("Password changed successfully.");

        } catch (Exception e) {
            passwordChangeStatus.setText("Error changing password: " + e.getMessage());
            passwordChangeStatus.getStyleClass().add("error-label");
            logger.logError("Error changing password: " + e.getMessage());
        }
    }
    @FXML
    private void openAppDataFolder() {
        try {
            // Get the AppData path for your applicatiom
            File appDataDir = new File(Main.appDataPath);

            // Create directory if it doesn't exist
            if (!appDataDir.exists()) {
                appDataDir.mkdirs();
            }

            // Open the folder in Windows Explorer
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(appDataDir);
            } else {
                // Fallback method using ProcessBuilder
                new ProcessBuilder("explorer.exe", Main.appDataPath).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
            // Optionally show an error dialog to the user
            System.err.println("Failed to open AppData folder: " + e.getMessage());
        }
    }

    // Method to open Discord invite link
    @FXML
    private void openDiscordInvite() {
        try {
            String discordUrl = "https://discord.gg/Pn5U4whfnd";

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(discordUrl));
            } else {
                // Fallback method
                new ProcessBuilder("cmd", "/c", "start", discordUrl).start();
            }
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
            System.err.println("Failed to open Discord link: " + e.getMessage());
        }
    }

    // Method to open GitHub repository
    @FXML
    private void openGitHubRepo() {
        try {
            String githubUrl = "https://github.com/ghosthacks96/ghostsecure";

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(githubUrl));
            } else {
                // Fallback method
                new ProcessBuilder("cmd", "/c", "start", githubUrl).start();
            }
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
            System.err.println("Failed to open GitHub link: " + e.getMessage());
        }
    }

    @FXML
    public void exportSettings(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Settings");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Backup File", "*.GSBACKUP"));
        File file = fileChooser.showSaveDialog(new Stage());
        if (file != null) {
            try {
                if (file.createNewFile() || file.exists()) {
                    // 1. Get decrypted config as JSON string
                    String configJson = Config.gson.toJson(Config.config);

                    // 2. Generate new salt
                    byte[] newSalt = me.ghosthacks96.ghostsecure.utils.EncryptionUtils.generateSalt();

                    // 3. Derive key from static password and random salt
                    javax.crypto.SecretKey newKey = new javax.crypto.spec.SecretKeySpec(
                        java.util.Arrays.copyOf(
                            java.security.MessageDigest.getInstance("SHA-256").digest("ThisIsBuLLShit".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            32
                        ),
                        "AES"
                    );

                    // 4. Encrypt config JSON with new key
                    String encrypted = me.ghosthacks96.ghostsecure.utils.EncryptionUtils.encrypt(configJson, newKey);

                    // 5. Append salt to encrypted data with separator
                    String exportData = encrypted + "!!_!!" + java.util.Base64.getEncoder().encodeToString(newSalt);

                    // 6. Write to file
                    java.nio.file.Files.writeString(file.toPath(), exportData);

                    logger.logInfo("Settings exported successfully to " + file.getAbsolutePath());
                } else {
                    logger.logError("Failed to create the export file: " + file.getAbsolutePath());
                }
            } catch (Exception e) {
                logger.logError("Failed to export settings: " + e.getMessage());
            }
        } else {
            logger.logWarning("Export cancelled by user.");
        }
    }
    
    @FXML
    public void importSettings(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Settings");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Backup File", "*.GSBACKUP"));
        File file = fileChooser.showOpenDialog(new Stage());
        if (file != null) {
            try {
                String fileContent = java.nio.file.Files.readString(file.toPath());
                String[] parts = fileContent.split("!!_!!");
                if (parts.length != 2) {
                    logger.logError("Invalid backup file format.");
                    return;
                }
                String encrypted = parts[0];
                byte[] salt = java.util.Base64.getDecoder().decode(parts[1]);

                // Derive key from static password and salt
                javax.crypto.SecretKey importKey = new javax.crypto.spec.SecretKeySpec(
                    java.util.Arrays.copyOf(
                        java.security.MessageDigest.getInstance("SHA-256").digest("ThisIsBuLLShit".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        32
                    ),
                    "AES"
                );

                // Decrypt config JSON
                String decryptedJson = me.ghosthacks96.ghostsecure.utils.EncryptionUtils.decrypt(encrypted, importKey);
                if (decryptedJson == null) {
                    logger.logError("Failed to decrypt imported settings.");
                    return;
                }
                JsonObject importedConfig = Config.gson.fromJson(decryptedJson, com.google.gson.JsonObject.class);

                // Merge imported items, skipping duplicates
                JsonArray importedPrograms = importedConfig.has("programs") ? importedConfig.getAsJsonArray("programs") : new JsonArray();
                JsonArray importedFolders = importedConfig.has("folders") ? importedConfig.getAsJsonArray("folders") : new JsonArray();

                JsonArray currentPrograms = Config.config.has("programs") ? Config.config.getAsJsonArray("programs") : new JsonArray();
                JsonArray currentFolders = Config.config.has("folders") ? Config.config.getAsJsonArray("folders") : new JsonArray();

                // Helper to check for duplicates
                java.util.Set<String> programSet = new java.util.HashSet<>();
                for (int i = 0; i < currentPrograms.size(); i++) programSet.add(currentPrograms.get(i).getAsString());
                for (int i = 0; i < importedPrograms.size(); i++) {
                    String entry = importedPrograms.get(i).getAsString();
                    if (!programSet.contains(entry)) {
                        currentPrograms.add(entry);
                        programSet.add(entry);
                    }
                }

                java.util.Set<String> folderSet = new java.util.HashSet<>();
                for (int i = 0; i < currentFolders.size(); i++) folderSet.add(currentFolders.get(i).getAsString());
                for (int i = 0; i < importedFolders.size(); i++) {
                    String entry = importedFolders.get(i).getAsString();
                    if (!folderSet.contains(entry)) {
                        currentFolders.add(entry);
                        folderSet.add(entry);
                    }
                }

                Config.config.add("programs", currentPrograms);
                Config.config.add("folders", currentFolders);
                me.ghosthacks96.ghostsecure.utils.controllers.Config.saveConfig();
                refreshTableData();
                logger.logInfo("Settings imported successfully from " + file.getAbsolutePath());
            } catch (Exception e) {
                logger.logError("Failed to import settings: " + e.getMessage());
            }
        } else {
            logger.logWarning("Import cancelled by user.");
        }
    }
}

