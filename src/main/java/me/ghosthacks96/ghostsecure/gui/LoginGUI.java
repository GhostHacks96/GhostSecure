package me.ghosthacks96.ghostsecure.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.utils.controllers.Config;

/**
 * Controller for the login screen
 */
public class LoginGUI {

    public static String enteredPasswordHash;
    public ImageView appIcon;

    @FXML private PasswordField passwordField;
    @FXML private Button submitButton;
    @FXML private  Label errorLabel;

    @FXML
    public void initialize() {
        errorLabel.setText("test");
        // Set up enter key handler
        passwordField.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER")) {
                onSubmitButtonClick();
            }
        });
    }

    public void setError(String message) {
        Platform.runLater(()->errorLabel.setText(message));
    }

    @FXML
    protected void onSubmitButtonClick() {
        String enteredPassword = passwordField.getText();
        enteredPasswordHash = Main.hashPassword(enteredPassword);
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) submitButton.getScene().getWindow();
        stage.close();
    }
}
