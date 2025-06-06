package me.ghosthacks96.ghostsecure;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

public class Login_GUI {
    String correctPasswordHash;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button submitButton;

    @FXML
    private Label errorLabel;

    public static String enteredPasswordHash;

    @FXML
    public void initialize() {
        this.correctPasswordHash = Main.PASSWORD_HASH; // Use hashed password
        passwordField.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER")) {
                onSubmitButtonClick(); // Trigger the onSubmit action
            }
        });

    }

    @FXML
    protected void onSubmitButtonClick() {
        String enteredPassword = passwordField.getText();
        enteredPasswordHash = Main.hashPassword(enteredPassword); // Hash the entered password

        // Compare the hashes
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) submitButton.getScene().getWindow(); // Get the current stage
        stage.close(); // Close the window
    }
}