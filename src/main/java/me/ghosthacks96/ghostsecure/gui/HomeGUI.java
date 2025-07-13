package me.ghosthacks96.ghostsecure.gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.utils.services.ServiceController;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class HomeGUI implements Initializable {

    @FXML
    public Label appVersionLabel;
    @FXML
    public ScrollPane contentScrollPane;
    @FXML
    private Label lockStatus;

    @FXML
    private VBox sidebarNav;

    @FXML
    private StackPane contentArea;

    // Navigation buttons
    @FXML
    private Button serviceControlNav;

    @FXML
    private Button folderManagementNav;

    @FXML
    private Button programManagementNav;

    @FXML
    private Button settingsNav;

    @FXML
    private Button aboutNav;

    // Keep track of currently active button
    private Button currentActiveButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Set up navigation event handlers
        setupNavigation();

        // Load default screen (Service Control)
        loadServiceControl();

        // Update lock status
        updateLockStatus();
        appVersionLabel.setText("GhostSecure v-" + Main.VERSION);
        lockStatus.setOnMouseClicked(e -> {
            // Toggle lock status on click
            if (ServiceController.isServiceRunning()) {
                ServiceController.stopBlockerDaemon();
            } else {
                ServiceController.startBlockerDaemon();
            }
            updateLockStatus(); // Refresh lock status display
        });
    }

    private void setupNavigation() {
        serviceControlNav.setOnAction(e -> loadServiceControl());
        folderManagementNav.setOnAction(e -> loadFolderManagement());
        programManagementNav.setOnAction(e -> loadProgramManagement());
        settingsNav.setOnAction(e -> loadSettings());
        aboutNav.setOnAction(e -> loadAbout());
    }

    private void loadServiceControl() {
        loadFXML("/me/ghosthacks96/ghostsecure/tabs/ServiceController_Screen.fxml");
        setActiveButton(serviceControlNav);
    }

    private void loadFolderManagement() {
        loadFXML("/me/ghosthacks96/ghostsecure/tabs/FolderManagerScreen.fxml");
        setActiveButton(folderManagementNav);
    }

    private void loadProgramManagement() {
        loadFXML("/me/ghosthacks96/ghostsecure/tabs/ProgramManagement.fxml");
        setActiveButton(programManagementNav);
    }

    private void loadSettings() {
        loadFXML("/me/ghosthacks96/ghostsecure/tabs/SettingsScreen.fxml");
        setActiveButton(settingsNav);
    }

    private void loadAbout() {
        loadFXML("/me/ghosthacks96/ghostsecure/tabs/AboutScreen.fxml");
        setActiveButton(aboutNav);
    }

    private void loadFXML(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node content = loader.load();

            // Clear current content and add new content
            contentArea.getChildren().clear();
            contentScrollPane.setVvalue(0);
            contentScrollPane.setHvalue(0);
            contentArea.getChildren().add(content);

        } catch (IOException e) {
            e.printStackTrace();
            // Handle error - maybe show an error message in the content area
            showErrorMessage("Failed to load page: " + fxmlPath);
        }
    }

    private void setActiveButton(Button activeButton) {
        // Remove active class from all buttons
        if (currentActiveButton != null) {
            currentActiveButton.getStyleClass().remove("active");
        }

        // Add active class to current button
        activeButton.getStyleClass().add("active");
        currentActiveButton = activeButton;
    }

    public void updateLockStatus() {
        boolean isLocked = checkLockStatus(); // Implement this method

        if (isLocked) {
            lockStatus.setText("🔒 LOCKED");
            lockStatus.getStyleClass().clear();
            lockStatus.getStyleClass().addAll("status-label", "locked");
        } else {
            lockStatus.setText("🔓 UNLOCKED");
            lockStatus.getStyleClass().clear();
            lockStatus.getStyleClass().addAll("status-label", "unlocked");
        }
    }

    private boolean checkLockStatus() {
        return ServiceController.isServiceRunning();
    }

    private void showErrorMessage(String message) {
        Label errorLabel = new Label(message);
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(400);

        contentArea.getChildren().clear();
        contentArea.getChildren().add(errorLabel);
    }

    // Public method to refresh lock status (can be called from other controllers)
    public static void refreshLockStatus() {
        // This method can be called from other controllers to refresh the lock status
        HomeGUI homeController = (HomeGUI) Main.mainLoader.getController();
        if (homeController != null) {
            homeController.updateLockStatus();
        } else {
            System.err.println("HomeGUI controller not found!");
        }
    }

    // Public method to navigate to specific screen (can be called from other controllers)
    public void navigateToScreen(String screenName) {
        switch (screenName.toLowerCase()) {
            case "service":
                loadServiceControl();
                break;
            case "folder":
                loadFolderManagement();
                break;
            case "program":
                loadProgramManagement();
                break;
            case "settings":
                loadSettings();
                break;
            case "about":
                loadAbout();
                break;
            default:
                System.err.println("Unknown screen: " + screenName);
        }
    }
}