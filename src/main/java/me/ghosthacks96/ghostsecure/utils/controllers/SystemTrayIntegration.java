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
                    Platform.runLater(() -> {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("home.fxml"));
                        HomeGUI controller = loader.getController();
                        controller.updateServiceStatus();
                    });
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
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("home.fxml"));
                    HomeGUI controller = loader.getController();
                    controller.updateServiceStatus();
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
                    Platform.runLater(() -> {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("home.fxml"));
                        HomeGUI controller = loader.getController();
                        controller.updateServiceStatus();
                    });
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

        popupMenu.add(starterItem);
        popupMenu.add(stopperItem);

        // Exit menu item
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> Platform.runLater(() -> {
            logger.logDebug("Exit menu item clicked");
            if (Main.sgh.openLoginScene()) {
                logger.logDebug("Login successful for exit");
                while (ServiceController.killDaemon()) {
                    // Loop until the service is fully shut down
                }
                Main.logger.onShutdown();
                System.exit(0);
            } else {
                logger.logDebug("Login failed for exit");
                Main.sgh.showError("No", "Are you sure you're supposed to be messing with this?");
            }
        }));
        popupMenu.add(exitItem);

        // Create the tray icon
        trayIcon = new TrayIcon(trayImage, "GhostSecure", popupMenu);
        trayIcon.setImageAutoSize(true);

        // Add the tray icon to the system tray
        try {
            SystemTray.getSystemTray().add(trayIcon);
            logger.logDebug("Tray icon added to system tray");
        } catch (AWTException e) {
            logger.logDebug("Unable to add tray icon: " + e.getMessage());
            logger.logError("Unable to add tray icon", e);
        }

        // Minimize to tray on close request
        primaryStage.setOnCloseRequest(event -> {
            logger.logDebug("Primary stage close request");
            if (ServiceController.isServiceRunning()) {
                event.consume();
                Platform.runLater(primaryStage::hide);
            }else{
                event.consume();
                Main.logger.onShutdown();
                Main.sgh.showInfo("Exiting GhostSecure", "Exiting application.");
                System.exit(0);
            }
        });
    }
}
