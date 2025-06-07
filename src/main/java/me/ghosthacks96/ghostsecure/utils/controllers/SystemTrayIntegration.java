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
        // Ensure AWT is initialized
        java.awt.Toolkit.getDefaultToolkit();

        // Prevent application from exiting when primary stage is closed
        Platform.setImplicitExit(false);

        if (!SystemTray.isSupported()) {
            System.err.println("System tray not supported!");
            return;
        }

        // Load an image for the tray icon
        Image trayImage = Toolkit.getDefaultToolkit().getImage(
                SystemTrayIntegration.class.getResource("/me/ghosthacks96/ghostsecure/tray_icon.png")
        );

        // Create a popup menu
        PopupMenu popupMenu = new PopupMenu();

        // Restore menu item
        MenuItem restoreItem = new MenuItem("Open GUI");
        restoreItem.addActionListener(e -> Platform.runLater(() -> {
            if (primaryStage != null) {
                if (Main.sgh.openLoginScene()) {
                    Platform.runLater(() -> {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("home.fxml"));
                        HomeGUI controller = loader.getController();
                        controller.updateServiceStatus();
                    });
                    primaryStage.show();
                    primaryStage.toFront();
                } else {
                    Main.sgh.showError("No", "Are you sure you're supposed to be messing with this?");
                }
            }
        }));
        popupMenu.add(restoreItem);

        MenuItem starterItem = new MenuItem("Start Service");
        starterItem.addActionListener(e -> Platform.runLater(() -> {
            if (!ServiceController.isServiceRunning()) {
                if (Main.sgh.openLoginScene()) {
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
                    Main.sgh.showError("No", "Are you sure you're supposed to be messing with this?");
                }
            } else {
                Main.sgh.showWarning("Already Running", "The service is already running. If you want to stop it, please use the stop button.");
                Main.logger.logWarning("Start requested, but the service is already running.");
            }
        }));

        MenuItem stopperItem = new MenuItem("Stop Service");
        stopperItem.addActionListener(e -> Platform.runLater(() -> {
            if (ServiceController.isServiceRunning()) {
                if (Main.sgh.openLoginScene()) {
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
                    Main.sgh.showError("No", "Are you sure you're supposed to be messing with this?");
                }
            } else {
                Main.sgh.showWarning("Not Running", "The service is not running. If you want to start it, please use the start button.");
                Main.logger.logWarning("Stop requested, but the service is not running.");
            }
        }));

        popupMenu.add(starterItem);
        popupMenu.add(stopperItem);

        // Exit menu item
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> Platform.runLater(() -> {
            if (Main.sgh.openLoginScene()) {
                while (ServiceController.killDaemon()) {
                    // Loop until the service is fully shut down
                }
                Main.logger.onShutdown();
                System.exit(0);
            } else {
                Main.sgh.showError("No", "Are you sure you're supposed to be messing with this?");
            }
        }));
        popupMenu.add(exitItem);

        // Create the tray icon
        trayIcon = new TrayIcon(trayImage, "ghostsecure", popupMenu);
        trayIcon.setImageAutoSize(true);

        // Add the tray icon to the system tray
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            System.err.println("Unable to add tray icon.");
            e.printStackTrace();
        }

        // Minimize to tray on close request
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            Platform.runLater(primaryStage::hide);
            Main.sgh.showInfo("Minimized to System Tray", "ghostsecure is minimized to the system tray. You can restore it by using the open GUI button in the tray icon menu.");
        });
    }
}
