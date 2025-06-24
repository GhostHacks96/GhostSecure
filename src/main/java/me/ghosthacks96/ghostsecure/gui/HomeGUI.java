package me.ghosthacks96.ghostsecure.gui;

// JavaFX imports
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
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
import java.io.File;
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
            Main.logger.switchDebugMode(debugEnabled);
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
}