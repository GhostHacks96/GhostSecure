package me.ghosthacks96.ghostsecure.gui.tabs;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.gui.HomeGUI;
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
    public void initialize() {
        logger.logInfo("Initializing ServiceControllerScreen.");
        updateServiceStatus();
        logger.logInfo("ServiceControllerScreen initialized.");
    }

    @FXML
    public void startService() {
        logger.logInfo("Starting locking service.");

        ServiceController.startBlockerDaemon();
        Platform.runLater(this::updateServiceStatus);

        logger.logInfo("Locking service started.");
    }

    @FXML
    public void stopService() {
        logger.logInfo("Stopping locking service.");
        ServiceController.stopBlockerDaemon();
        Platform.runLater(this::updateServiceStatus);
        logger.logInfo("Locking service stopped.");
    }

    public void updateServiceStatus() {
        boolean isRunning = ServiceController.isServiceRunning();
        logger.logInfo("Updating service status to " + (isRunning ? "RUNNING" : "STOPPED"));
        if (isRunning) {
            setServiceRunningUI();
        } else {
            setServiceStoppedUI();
        }
    }


    private void setServiceRunningUI() {
        startServiceButton.setDisable(true);
        stopServiceButton.setDisable(false);
        Platform.runLater(HomeGUI::refreshLockStatus);
    }

    private void setServiceStoppedUI() {
        startServiceButton.setDisable(false);
        stopServiceButton.setDisable(true);
        Platform.runLater(HomeGUI::refreshLockStatus);
    }

}
