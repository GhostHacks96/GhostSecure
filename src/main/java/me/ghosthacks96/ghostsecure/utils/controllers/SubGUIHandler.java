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
        try {
            // Load the Login GUI
            FXMLLoader loginLoader = new FXMLLoader(Main.class.getResource("login.fxml"));
            Stage loginStage = new Stage();
            Scene loginScene = new Scene(loginLoader.load());


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

            loginStage.showAndWait();

            // Check if the login was successful
            return PASSWORD_HASH != null && PASSWORD_HASH.equals(LoginGUI.enteredPasswordHash);
        } catch (IOException e) {
            e.printStackTrace();
            return false; // Login failed due to error
        }
    }

    public String showSetPassPrompt() {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("setPasswordGUI.fxml"));
            Stage stage = new Stage();
            Scene scene = new Scene(loader.load());
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
            stage.showAndWait(); // Wait until the user closes the popup

            if (controller.isPasswordSet()) {
                return controller.getEnteredPassword(); // Return the successfully entered password
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null; // Return null if the password setup was unsuccessful
    }

    public  void showInfo(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    public  void showWarning(String title, String message) {
        showAlert(Alert.AlertType.WARNING, title, message);
    }

    public  void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message);
    }

    private  void showAlert(Alert.AlertType type, String title, String message) {
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