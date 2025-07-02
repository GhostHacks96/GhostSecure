package me.ghosthacks96.ghostsecure.utils.controllers;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.gui.HomeGUI;

import java.awt.*;

import static me.ghosthacks96.ghostsecure.Main.logger;

public class SystemTrayIntegration {
    public static TrayIcon trayIcon;

    public void setupSystemTray(Stage primaryStage) {
        logger.logDebug("setupSystemTray() called");
        // Ensure AWT is initialized
        java.awt.Toolkit.getDefaultToolkit();

        // Prevent application from exiting when primary stage is closed
        Platform.setImplicitExit(false);

        if (!SystemTray.isSupported()) {
            logger.logDebug("System tray not supported!");
            System.err.println("System tray not supported!");
            return;
        }
        logger.logDebug("System tray is supported");

        // Load an image for the tray icon
        Image trayImage = Toolkit.getDefaultToolkit().getImage(
                SystemTrayIntegration.class.getResource("/me/ghosthacks96/ghostsecure/tray_icon.png")
        );

        // Create a popup menu
        PopupMenu popupMenu = new PopupMenu();

        // Restore menu item
        MenuItem restoreItem = new MenuItem("Open GUI");
        restoreItem.addActionListener(e -> Platform.runLater(() -> {
            logger.logDebug("Restore menu item clicked");
            if (primaryStage != null) {
                if (Main.sgh.openLoginScene()) {
                    logger.logDebug("Login successful from tray restore");
                    // Use the existing HomeGUI controller from the mainLoader
                    try {
                        HomeGUI controller = Main.mainLoader.getController();
                        if (controller != null) {
                            controller.updateServiceStatus();
                        }
                    } catch (Exception ex) {
                        logger.logError("Failed to update HomeGUI from tray: " + ex.getMessage());
                    }
                    primaryStage.show();
                    primaryStage.toFront();
                } else {
                    logger.logDebug("Login failed from tray restore");
                    Main.sgh.showError("No", "Are you sure you're supposed to be messing with this?");
                }
            }
        }));
        popupMenu.add(restoreItem);

        MenuItem starterItem = new MenuItem("Start Service");
        starterItem.addActionListener(e -> Platform.runLater(() -> {
            logger.logDebug("Start Service menu item clicked");
            if (!ServiceController.isServiceRunning()) {
                if (Main.sgh.openLoginScene()) {
                    logger.logDebug("Login successful for start service");
                    logger.logInfo("Starting locking service.");
                    if (Main.config.getJsonConfig().get("mode").getAsString().equals("unlock")) {
                        Main.config.getJsonConfig().remove("mode");
                        Main.config.getJsonConfig().addProperty("mode", "lock");
                    }
                    Config.saveConfig();
                    try {
                        HomeGUI controller = Main.mainLoader.getController();
                        if (controller != null) {
                            controller.updateServiceStatus();
                        }
                    } catch (Exception ex) {
                        logger.logError("Failed to update HomeGUI from tray: " + ex.getMessage());
                    }
                    logger.logInfo("Locking service started.");
                } else {
                    logger.logDebug("Login failed for start service");
                    Main.sgh.showError("No", "Are you sure you're supposed to be messing with this?");
                }
            } else {
                logger.logDebug("Start service requested but already running");
                Main.sgh.showWarning("Already Running", "The service is already running. If you want to stop it, please use the stop button.");
                Main.logger.logWarning("Start requested, but the service is already running.");
            }
        }));
        popupMenu.add(starterItem);

        MenuItem stopperItem = new MenuItem("Stop Service");
        stopperItem.addActionListener(e -> Platform.runLater(() -> {
            logger.logDebug("Stop Service menu item clicked");
            if (ServiceController.isServiceRunning()) {
                if (Main.sgh.openLoginScene()) {
                    logger.logDebug("Login successful for stop service");
                    logger.logInfo("Stopping locking service.");
                    if (Main.config.getJsonConfig().get("mode").getAsString().equals("lock")) {
                        Main.config.getJsonConfig().remove("mode");
                        Main.config.getJsonConfig().addProperty("mode", "unlock");
                    }
                    Config.saveConfig();
                    try {
                        HomeGUI controller = Main.mainLoader.getController();
                        if (controller != null) {
                            controller.updateServiceStatus();
                        }
                    } catch (Exception ex) {
                        logger.logError("Failed to update HomeGUI from tray: " + ex.getMessage());
                    }
                    logger.logInfo("Locking service stopped.");
                } else {
                    logger.logDebug("Login failed for stop service");
                    Main.sgh.showError("No", "Are you sure you're supposed to be messing with this?");
                }
            } else {
                logger.logDebug("Stop service requested but not running");
                Main.sgh.showWarning("Not Running", "The service is not running. If you want to start it, please use the start button.");
                Main.logger.logWarning("Stop requested, but the service is not running.");
            }
        }));
        popupMenu.add(stopperItem);

        // Add the popup menu to the tray icon and add the icon to the system tray
        trayIcon = new TrayIcon(trayImage, "GhostSecure", popupMenu);
        trayIcon.setImageAutoSize(true);
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            logger.logError("Failed to add tray icon: " + e.getMessage());
        }
    }
}

