package me.ghosthacks96.ghostsecure.gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.Node;
import me.ghosthacks96.ghostsecure.utils.services.ServiceController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static me.ghosthacks96.ghostsecure.Main.logger;

/**
 * Main application GUI controller with sidebar navigation
 */
public class HomeGUI {

    // UI Components - Header
    @FXML private Label lockStatus;

    // UI Components - Sidebar Navigation
    @FXML private VBox sidebarNav;
    @FXML private Button serviceControlNav;
    @FXML private Button folderManagementNav;
    @FXML private Button programManagementNav;
    @FXML private Button settingsNav;
    @FXML private Button aboutNav;

    // UI Components - Content Area
    @FXML private StackPane contentArea;

    // Navigation state
    private Button currentActiveButton;
    private Map<String, Node> contentCache = new HashMap<>();
    private Map<String, Object> controllerCache = new HashMap<>();

    // Navigation items configuration
    private static final NavItem[] NAV_ITEMS = {
            new NavItem("serviceControl", "🔧 Service Control", "/me/ghosthacks96/ghostsecure/tabs/ServiceController_Screen.fxml"),
            new NavItem("folderManagement", "📁 Folder Management", "/me/ghosthacks96/ghostsecure/tabs/FolderManagementScreen.fxml"),
            new NavItem("programManagement", "💻 Program Management", "/me/ghosthacks96/ghostsecure/tabs/ProgramManagement.fxml"),
            new NavItem("settings", "⛭ Settings", "/me/ghosthacks96/ghostsecure/tabs/SettingsScreen.fxml"),
            new NavItem("about", "🛈 About", "/me/ghosthacks96/ghostsecure/tabs/AboutScreen.fxml")
    };

    @FXML
    public void initialize() {
        logger.logInfo("Initializing HomeGUI with sidebar navigation.");

        updateServiceStatus();
        setupSidebarNavigation();

        // Load default view (Service Control)
        navigateToView("serviceControl");

        logger.logInfo("HomeGUI initialization complete.");
    }

    private void setupSidebarNavigation() {
        // Setup click handlers for navigation buttons
        serviceControlNav.setOnAction(e -> navigateToView("serviceControl"));
        folderManagementNav.setOnAction(e -> navigateToView("folderManagement"));
        programManagementNav.setOnAction(e -> navigateToView("programManagement"));
        settingsNav.setOnAction(e -> navigateToView("settings"));
        aboutNav.setOnAction(e -> navigateToView("about"));

        // Set initial active button
        setActiveButton(serviceControlNav);
    }

    private void navigateToView(String viewName) {
        logger.logInfo("Navigating to view: " + viewName);

        try {
            // Get or load the content
            Node content = getOrLoadContent(viewName);

            if (content != null) {
                // Clear current content and add new content
                contentArea.getChildren().clear();
                contentArea.getChildren().add(content);

                // Update active button
                updateActiveButton(viewName);

                logger.logInfo("Successfully navigated to: " + viewName);
            } else {
                logger.logError("Failed to load content for view: " + viewName);
            }

        } catch (Exception e) {
            logger.logError("Error navigating to view " + viewName + ": " + e.getMessage());
        }
    }

    private Node getOrLoadContent(String viewName) {
        // Check cache first
        if (contentCache.containsKey(viewName)) {
            return contentCache.get(viewName);
        }

        // Load content from FXML
        NavItem navItem = getNavItem(viewName);
        if (navItem == null) {
            logger.logError("No navigation item found for: " + viewName);
            return null;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(navItem.fxmlPath));
            Node content = loader.load();

            // Cache the content and controller
            contentCache.put(viewName, content);
            controllerCache.put(viewName, loader.getController());

            return content;

        } catch (IOException e) {
            logger.logError("Error loading FXML for " + viewName + ": " + e.getMessage());
            return null;
        }
    }

    private NavItem getNavItem(String viewName) {
        for (NavItem item : NAV_ITEMS) {
            if (item.id.equals(viewName)) {
                return item;
            }
        }
        return null;
    }

    private void updateActiveButton(String viewName) {
        Button targetButton = getNavigationButton(viewName);
        if (targetButton != null) {
            setActiveButton(targetButton);
        }
    }

    private Button getNavigationButton(String viewName) {
        switch (viewName) {
            case "serviceControl": return serviceControlNav;
            case "folderManagement": return folderManagementNav;
            case "programManagement": return programManagementNav;
            case "settings": return settingsNav;
            case "about": return aboutNav;
            default: return null;
        }
    }

    private void setActiveButton(Button button) {
        // Remove active class from current button
        if (currentActiveButton != null) {
            currentActiveButton.getStyleClass().remove("nav-active");
        }

        // Add active class to new button
        button.getStyleClass().add("nav-active");
        currentActiveButton = button;
    }

    public void updateServiceStatus() {
        boolean isRunning = ServiceController.isServiceRunning();
        logger.logInfo("Updating service status to " + (isRunning ? "RUNNING" : "STOPPED"));

        if (isRunning) {
            lockStatus.setText("Locking Engaged");
            lockStatus.setTextFill(javafx.scene.paint.Color.GREEN);
        } else {
            lockStatus.setText("Locking Disabled");
            lockStatus.setTextFill(javafx.scene.paint.Color.RED);
        }
    }

    // Public method for controllers to refresh data
    public void refreshData() {
        // This can be called by child controllers to refresh main data
        updateServiceStatus();
    }

    // Public method to get cached controllers
    public Object getController(String viewName) {
        return controllerCache.get(viewName);
    }

    // Public method to clear cache if needed
    public void clearCache() {
        contentCache.clear();
        controllerCache.clear();
    }

    // Navigation item configuration class
    private static class NavItem {
        final String id;
        final String displayName;
        final String fxmlPath;

        NavItem(String id, String displayName, String fxmlPath) {
            this.id = id;
            this.displayName = displayName;
            this.fxmlPath = fxmlPath;
        }
    }
}