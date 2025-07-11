package me.ghosthacks96.ghostsecure.gui.auth;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

public class SetPasswordGUI {

    public ImageView appIcon;
    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Button submitButton;

    @FXML
    private Label errorLabel;

    private boolean passwordSet = false;
    private String enteredPassword;


    @FXML
    private void onSubmitButtonClick() {
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (password.isEmpty() || confirmPassword.isEmpty()) {
            errorLabel.setText("Both fields are required.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            errorLabel.setText("Passwords do not match.");
            return;
        }

        if( password.length() < 8) {
            errorLabel.setText("Password must be at least 8 characters long.");
            return;
        }

        enteredPassword = password; // Save the password
        passwordSet = true; // Mark as successful

        closeWindow();
    }

    public boolean isPasswordSet() {
        return passwordSet;
    }

    public String getEnteredPassword() {
        return enteredPassword;
    }

    private void closeWindow() {
        Stage stage = (Stage) submitButton.getScene().getWindow();
        stage.close();
    }
}