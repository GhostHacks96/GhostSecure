package me.ghosthacks96.ghostsecure.utils.controllers;

import javafx.application.Platform;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.gui.HomeGUI;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Optional;

/**
 * Manages system tray integration for the GhostSecure application.
 * Provides background access to application functions through a system tray icon.
 */
public class SystemTrayIntegration {

    // Constants
    private static final String TRAY_ICON_PATH = "/me/ghosthacks96/ghostsecure/tray_icon.png";
    private static final String TRAY_TOOLTIP = "GhostSecure";
    private static final String MODE_LOCK = "lock";
    private static final String MODE_UNLOCK = "unlock";

    // Menu item labels
    private static final String MENU_OPEN_GUI = "Open GUI";
    private static final String MENU_START_SERVICE = "Start Service";
    private static final String MENU_STOP_SERVICE = "Stop Service";

    // Error messages
    private static final String ERROR_TITLE = "No";
    private static final String ERROR_MESSAGE = "Are you sure you're supposed to be messing with this?";
    private static final String ALREADY_RUNNING_TITLE = "Already Running";
    private static final String ALREADY_RUNNING_MESSAGE = "The service is already running. If you want to stop it, please use the stop button.";
    private static final String NOT_RUNNING_TITLE = "Not Running";
    private static final String NOT_RUNNING_MESSAGE = "The service is not running. If you want to start it, please use the start button.";

    // Static reference to tray icon
    public static TrayIcon trayIcon;

    // Instance fields
    private static Logging logger;
    private Stage primaryStage;

    public Main main;

    /**
     * Constructor initializes the system tray integration.
     */
    public SystemTrayIntegration(Main main) {
        this.main = main;
        logger = Main.logger;
    }

    /**
     * Set up the system tray integration with the given primary stage.
     *
     * @param primaryStage The primary JavaFX stage to control
     */
    public void setupSystemTray(Stage primaryStage) {
        logger.logDebug("Setting up system tray integration...");

        this.primaryStage = primaryStage;

        if (!initializeSystemTray()) {
            return;
        }

        try {
            Image trayImage = loadTrayIcon();
            PopupMenu popupMenu = createPopupMenu();

            trayIcon = new TrayIcon(trayImage, TRAY_TOOLTIP, popupMenu);
            trayIcon.setImageAutoSize(true);

            SystemTray.getSystemTray().add(trayIcon);
            logger.logInfo("System tray integration setup successfully.");

        } catch (Exception e) {
            logger.logError("Failed to setup system tray", e);
        }
    }

    /**
     * Initialize system tray prerequisites.
     *
     * @return true if initialization successful, false otherwise
     */
    private boolean initializeSystemTray() {
        // Ensure AWT is initialized
        Toolkit.getDefaultToolkit();

        // Prevent application from exiting when primary stage is closed
        Platform.setImplicitExit(false);

        if (!SystemTray.isSupported()) {
            logger.logWarning("System tray not supported on this platform!");
            return false;
        }

        logger.logDebug("System tray is supported");
        return true;
    }

    /**
     * Load the tray icon image.
     *
     * @return The loaded tray icon image
     * @throws RuntimeException if the icon cannot be loaded
     */
    private Image loadTrayIcon() {
        try {
            Image trayImage = Toolkit.getDefaultToolkit().getImage(
                    SystemTrayIntegration.class.getResource(TRAY_ICON_PATH)
            );

            if (trayImage == null) {
                throw new RuntimeException("Tray icon resource not found: " + TRAY_ICON_PATH);
            }

            return trayImage;

        } catch (Exception e) {
            logger.logError("Failed to load tray icon", e);
            throw new RuntimeException("Could not load tray icon", e);
        }
    }

    /**
     * Create the popup menu for the system tray icon.
     *
     * @return The configured popup menu
     */
    private PopupMenu createPopupMenu() {
        PopupMenu popupMenu = new PopupMenu();

        popupMenu.add(createOpenGuiMenuItem());
        popupMenu.addSeparator();
        popupMenu.add(createStartServiceMenuItem());
        popupMenu.add(createStopServiceMenuItem());
        popupMenu.addSeparator();
        popupMenu.add(createExitMenuItem());

        return popupMenu;
    }

    private MenuItem createExitMenuItem() {
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            logger.logDebug("Exit menu item clicked");
            authenticateAndExecute(main::shutDownSystem, "exit application");
        });
        return exitItem;
    }

    /**
     * Create the "Open GUI" menu item.
     */
    private MenuItem createOpenGuiMenuItem() {
        MenuItem openGuiItem = new MenuItem(MENU_OPEN_GUI);
        openGuiItem.addActionListener(new OpenGuiActionListener());
        return openGuiItem;
    }

    /**
     * Create the "Start Service" menu item.
     */
    private MenuItem createStartServiceMenuItem() {
        MenuItem startServiceItem = new MenuItem(MENU_START_SERVICE);
        startServiceItem.addActionListener(new StartServiceActionListener());
        return startServiceItem;
    }

    /**
     * Create the "Stop Service" menu item.
     */
    private MenuItem createStopServiceMenuItem() {
        MenuItem stopServiceItem = new MenuItem(MENU_STOP_SERVICE);
        stopServiceItem.addActionListener(new StopServiceActionListener());
        return stopServiceItem;
    }

    /**
     * Authenticate user and execute action if successful.
     *
     * @param action The action to execute after successful authentication
     * @param actionName The name of the action for logging purposes
     */
    private void authenticateAndExecute(Runnable action, String actionName) {
        if (Main.sgh.openLoginScene()) {
            logger.logDebug("Login successful for " + actionName);
            action.run();
        } else {
            logger.logDebug("Login failed for " + actionName);
            Main.sgh.showError(ERROR_TITLE, ERROR_MESSAGE);
        }
    }

    /**
     * Update the service mode in configuration.
     *
     * @param mode The new mode to set
     */
    private void updateServiceMode(String mode) {
        try {
            Main.config.getJsonConfig().remove("mode");
            Main.config.getJsonConfig().addProperty("mode", mode);
            Config.saveConfig();
            if(MODE_LOCK.equals(mode)) {
                logger.logInfo("Service mode set to LOCK");
                ServiceController.startBlockerDaemon();
            } else if (MODE_UNLOCK.equals(mode)) {
                ServiceController.stopBlockerDaemon();
                logger.logInfo("Service mode set to UNLOCK");
            }
            updateHomeGuiIfAvailable();

        } catch (Exception e) {
            logger.logError("Failed to update service mode to " + mode, e);
        }
    }

    /**
     * Update the HomeGUI controller if available.
     */
    private void updateHomeGuiIfAvailable() {
        try {
            Optional<HomeGUI> controller = getHomeGuiController();
            controller.ifPresent(HomeGUI::updateServiceStatus);
        } catch (Exception e) {
            logger.logError("Failed to update HomeGUI from tray", e);
        }
    }

    /**
     * Get the HomeGUI controller if available.
     *
     * @return Optional containing the controller if available
     */
    private Optional<HomeGUI> getHomeGuiController() {
        try {
            if (Main.mainLoader != null) {
                HomeGUI controller = Main.mainLoader.getController();
                return Optional.ofNullable(controller);
            }
        } catch (Exception e) {
            logger.logDebug("HomeGUI controller not available", e);
        }
        return Optional.empty();
    }

    /**
     * Validate that the primary stage is available.
     *
     * @return true if stage is available, false otherwise
     */
    private boolean validatePrimaryStage() {
        if (primaryStage == null) {
            logger.logError("Primary stage is null");
            Main.sgh.showError(ERROR_TITLE, "Application stage not available");
            return false;
        }
        return true;
    }

    // Action Listeners

    /**
     * Action listener for opening the GUI.
     */
    private class OpenGuiActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            Platform.runLater(() -> {
                logger.logDebug("Open GUI menu item clicked");

                if (!validatePrimaryStage()) {
                    return;
                }

                authenticateAndExecute(() -> {
                    primaryStage.show();
                    primaryStage.toFront();
                    logger.logDebug("GUI opened successfully from tray");
                }, "open GUI");
            });
        }
    }

    /**
     * Action listener for starting the service.
     */
    private class StartServiceActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            Platform.runLater(() -> {
                logger.logDebug("Start Service menu item clicked");

                if (ServiceController.isServiceRunning()) {
                    logger.logDebug("Start service requested but already running");
                    Main.sgh.showWarning(ALREADY_RUNNING_TITLE, ALREADY_RUNNING_MESSAGE);
                    logger.logWarning("Start requested, but the service is already running.");
                    return;
                }

                authenticateAndExecute(() -> {
                    logger.logInfo("Starting locking service.");
                    updateServiceMode(MODE_LOCK);
                    logger.logInfo("Locking service started.");
                }, "start service");
            });
        }
    }

    /**
     * Action listener for stopping the service.
     */
    private class StopServiceActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            Platform.runLater(() -> {
                logger.logDebug("Stop Service menu item clicked");

                if (!ServiceController.isServiceRunning()) {
                    logger.logDebug("Stop service requested but not running");
                    Main.sgh.showWarning(NOT_RUNNING_TITLE, NOT_RUNNING_MESSAGE);
                    logger.logWarning("Stop requested, but the service is not running.");
                    return;
                }

                authenticateAndExecute(() -> {
                    logger.logInfo("Stopping locking service.");
                    updateServiceMode(MODE_UNLOCK);
                    logger.logInfo("Locking service stopped.");
                }, "stop service");
            });
        }
    }

    /**
     * Remove the tray icon from the system tray.
     */
    public static void removeTrayIcon() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            logger.logDebug("Tray icon removed");
        }
    }

    /**
     * Check if system tray is supported on this platform.
     *
     * @return true if supported, false otherwise
     */
    public static boolean isSystemTraySupported() {
        return SystemTray.isSupported();
    }
}