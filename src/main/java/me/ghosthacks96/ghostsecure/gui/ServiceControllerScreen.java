package me.ghosthacks96.ghostsecure.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.utils.services.ServiceController;

import static me.ghosthacks96.ghostsecure.Main.logger;

public class ServiceControllerScreen {

    // UI Components - Service Control
    @FXML
    private Button startServiceButton;
    @FXML private Button stopServiceButton;

    // ===============================
    // SERVICE CONTROL METHODS
    // ===============================

    @FXML
    public void startService() {
        logger.logInfo("Starting locking service.");

        setServiceMode("lock");
        Main.config.saveConfig();
        ServiceController.startBlockerDaemon();
        Platform.runLater(this::updateServiceStatus);

        logger.logInfo("Locking service started.");
    }

    @FXML
    public void stopService() {
        logger.logInfo("Stopping locking service.");

        setServiceMode("unlock");
        Main.config.saveConfig();
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

}
