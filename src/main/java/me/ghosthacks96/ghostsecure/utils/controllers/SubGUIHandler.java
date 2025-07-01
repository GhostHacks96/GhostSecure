package me.ghosthacks96.ghostsecure.utils.controllers;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.gui.LoginGUI;
import me.ghosthacks96.ghostsecure.gui.SetPasswordGUI;
import org.kordamp.bootstrapfx.BootstrapFX;

import java.io.IOException;
import java.util.Objects;

import static me.ghosthacks96.ghostsecure.utils.controllers.Config.PASSWORD_HASH;

public class SubGUIHandler {
    public String loginError;
    public boolean openLoginScene() {
        Main.logger.logDebug("openLoginScene() called");
        try {
            // Create a result holder for the boolean result
            final boolean[] resultHolder = new boolean[1];

            // Use CountDownLatch to wait for the JavaFX thread to complete
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

            // Ensure all UI operations run on the JavaFX Application Thread
            Platform.runLater(() -> {
                try {
                    // Load the Login GUI
                    FXMLLoader loginLoader = new FXMLLoader(Main.class.getResource("login.fxml"));
                    Stage loginStage = new Stage();
                    Scene loginScene = new Scene(loginLoader.load());
                    Main.logger.logDebug("Login GUI loaded");
                    loginStage.initModality(Modality.APPLICATION_MODAL);
                    loginScene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
                    loginStage.initStyle(StageStyle.DECORATED);
                    loginScene.getStylesheets().add(Objects.requireNonNull(Main.class.getResource("/me/ghosthacks96/ghostsecure/dark-theme.css")).toExternalForm());
                    loginStage.setAlwaysOnTop(true);
                    loginStage.requestFocus();
                    loginStage.getIcons().add(new javafx.scene.image.Image(Objects.requireNonNull(Main.class.getResource("/me/ghosthacks96/ghostsecure/app_icon.png")).toExternalForm()));
                    // Set up the login stage
                    loginStage.setTitle("GhostSecure - Login");
                    loginStage.setScene(loginScene);
                    LoginGUI loginController = loginLoader.getController();
                    loginController.setError(loginError);
                    Main.logger.logDebug("Login stage showing");

                    loginStage.showAndWait();
                    // Check if the login was successful
                    resultHolder[0] = PASSWORD_HASH != null && PASSWORD_HASH.equals(LoginGUI.enteredPasswordHash);
                    Main.logger.logDebug("Login result: " + resultHolder[0]);
                } catch (IOException e) {
                    Main.logger.logError("Error loading login GUI: " + e.getMessage());
                    Main.logger.logDebug("Exception: " + e.getMessage(), e);
                    resultHolder[0] = false; // Login failed due to error
                } finally {
                    latch.countDown(); // Signal that the JavaFX thread has completed
                }
            });

            try {
                latch.await(); // Wait for the JavaFX thread to complete
            } catch (InterruptedException e) {
                Main.logger.logError("Login process was interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
                return false;
            }

            return resultHolder[0];
        } catch (Exception e) {
            Main.logger.logError("Unexpected error in openLoginScene: " + e.getMessage());
            Main.logger.logDebug("Exception: " + e.getMessage(), e);
            return false; // Login failed due to error
        }
    }

    public String showSetPassPrompt() {
        Main.logger.logDebug("showSetPassPrompt() called");
        try {
            // Create a result holder for the string result
            final String[] resultHolder = new String[1];

            // Use CountDownLatch to wait for the JavaFX thread to complete
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

            // Ensure all UI operations run on the JavaFX Application Thread
            Platform.runLater(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(Main.class.getResource("setPasswordGUI.fxml"));
                    Stage stage = new Stage();
                    Scene scene = new Scene(loader.load());
                    Main.logger.logDebug("SetPassword GUI loaded");
                    stage.setTitle("GhostSecure - Set Password");
                    stage.initModality(Modality.APPLICATION_MODAL); // Makes the prompt modal
                    stage.initStyle(StageStyle.DECORATED);
                    scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/me/ghosthacks96/ghostsecure/dark-theme.css")).toExternalForm());
                    stage.setScene(scene);
                    stage.setResizable(false);
                    stage.setAlwaysOnTop(true);
                    stage.requestFocus();
                    stage.getIcons().add(new javafx.scene.image.Image(Objects.requireNonNull(getClass().getResource("/me/ghosthacks96/ghostsecure/app_icon.png")).toExternalForm()));
                    SetPasswordGUI controller = loader.getController();
                    Main.logger.logDebug("SetPassword stage showing");
                    stage.showAndWait(); // Wait until the user closes the popup
                    if (controller.isPasswordSet()) {
                        Main.logger.logDebug("Password set successfully");
                        resultHolder[0] = controller.getEnteredPassword(); // Store the successfully entered password
                    } else {
                        Main.logger.logDebug("Password setup was unsuccessful");
                        resultHolder[0] = null;
                    }
                } catch (IOException e) {
                    Main.logger.logError("Error loading set password GUI: " + e.getMessage());
                    Main.logger.logDebug("Exception: " + e.getMessage(), e);
                    resultHolder[0] = null;
                } finally {
                    latch.countDown(); // Signal that the JavaFX thread has completed
                }
            });

            try {
                latch.await(); // Wait for the JavaFX thread to complete
            } catch (InterruptedException e) {
                Main.logger.logError("Password setup process was interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
                return null;
            }

            return resultHolder[0];
        } catch (Exception e) {
            Main.logger.logError("Unexpected error in showSetPassPrompt: " + e.getMessage());
            Main.logger.logDebug("Exception: " + e.getMessage(), e);
            return null; // Return null if the password setup was unsuccessful
        }
    }

    public  void showInfo(String title, String message) {
        Main.logger.logDebug("showInfo() called: " + title + " - " + message);
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    public  void showWarning(String title, String message) {
        Main.logger.logDebug("showWarning() called: " + title + " - " + message);
        showAlert(Alert.AlertType.WARNING, title, message);
    }

    public  void showError(String title, String message) {
        Main.logger.logDebug("showError() called: " + title + " - " + message);
        showAlert(Alert.AlertType.ERROR, title, message);
    }

    private  void showAlert(Alert.AlertType type, String title, String message) {
        Main.logger.logDebug("showAlert() called: " + type + ", " + title + ", " + message);
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.initModality(Modality.APPLICATION_MODAL);
            alert.setTitle(title);
            alert.setHeaderText(null); // No header text
            alert.setContentText(message);
            // Ensure the alert stays on top
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.setAlwaysOnTop(true);

            alert.showAndWait();
        });
    }

}
