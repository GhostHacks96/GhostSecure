package me.ghosthacks96.applocker.utils;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import me.ghosthacks96.applocker.Main;
import me.ghosthacks96.applocker.homeGUI;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static me.ghosthacks96.applocker.Main.logger;
import static me.ghosthacks96.applocker.Main.saveConfig;

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
                SystemTrayIntegration.class.getResource("/me/ghosthacks96/applocker/tray_icon.png")
        );

        // Create a popup menu
        PopupMenu popupMenu = new PopupMenu();

        // Restore menu item
        MenuItem restoreItem = new MenuItem("Open GUI");
        restoreItem.addActionListener(e -> Platform.runLater(() -> {
            if (primaryStage != null) {
                if (Main.openLoginScene()) {
                    primaryStage.show();
                    primaryStage.toFront();
                } else {
                    PopUps.showError("No", "Are you sure you're supposed to be messing with this?");
                }
            }
        }));
        popupMenu.add(restoreItem);

        MenuItem starterItem = new MenuItem("Start Service");
        starterItem.addActionListener(e -> Platform.runLater(() -> {
            if (!Main.sc.isServiceRunning()) {
                if (Main.openLoginScene()) {
                    logger.logInfo("Starting locking service.");
                    if (Main.config.get("mode").getAsString().equals("unlock")) {
                        Main.config.remove("mode");
                        Main.config.addProperty("mode", "lock");
                    }
                    saveConfig();
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("home.fxml"));
                    homeGUI controller = loader.getController();
                    controller.updateServiceStatus(true);
                    logger.logInfo("Locking service started.");
                } else {
                    PopUps.showError("No", "Are you sure you're supposed to be messing with this?");
                }
            } else {
                PopUps.showWarning("Already Running", "The service is already running. If you want to stop it, please use the stop button.");
                Main.logger.logWarning("Start requested, but the service is already running.");
            }
        }));

        MenuItem stopperItem = new MenuItem("Stop Service");
        stopperItem.addActionListener(e -> Platform.runLater(() -> {
            if (Main.sc.isServiceRunning()) {
                if (Main.openLoginScene()) {
                    logger.logInfo("Stopping locking service.");
                    if (Main.config.get("mode").getAsString().equals("lock")) {
                        Main.config.remove("mode");
                        Main.config.addProperty("mode", "unlock");
                    }
                    saveConfig();
                    Platform.runLater(() -> {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("home.fxml"));
                        homeGUI controller = loader.getController();
                        controller.updateServiceStatus(false);
                    });
                    logger.logInfo("Locking service stopped.");
                } else {
                    PopUps.showError("No", "Are you sure you're supposed to be messing with this?");
                }
            } else {
                PopUps.showWarning("Not Running", "The service is not running. If you want to start it, please use the start button.");
                Main.logger.logWarning("Stop requested, but the service is not running.");
            }
        }));

        popupMenu.add(starterItem);
        popupMenu.add(stopperItem);

        // Exit menu item
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> Platform.runLater(() -> {
            if (Main.openLoginScene()) {
                while (Main.sc.killDaemon()) {
                    // Loop until the service is fully shut down
                }
                Main.logger.onShutdown();
                System.exit(0);
            } else {
                PopUps.showError("No", "Are you sure you're supposed to be messing with this?");
            }
        }));
        popupMenu.add(exitItem);

        // Create the tray icon
        trayIcon = new TrayIcon(trayImage, "AppLocker", popupMenu);
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
            PopUps.showInfo("Minimized to System Tray", "AppLocker is minimized to the system tray. You can restore it by using the open GUI button in the tray icon menu.");
        });
    }
}
