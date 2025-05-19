package me.ghosthacks96.applocker.utils;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class PopUps {

    public PopUps() {
        // Default constructor
    }

    public static void showInfo(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    public static void showWarning(String title, String message) {
        showAlert(Alert.AlertType.WARNING, title, message);
    }

    public static void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message);
    }

    private static void showAlert(Alert.AlertType type, String title, String message) {
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