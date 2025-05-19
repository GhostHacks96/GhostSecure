package me.ghosthacks96.applocker;

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
    }

    @FXML
    protected void onSubmitButtonClick() {
        String enteredPassword = passwordField.getText();
        enteredPasswordHash = Main.hashPassword(enteredPassword); // Hash the entered password

        // Compare the hashes
        if (correctPasswordHash.equals(enteredPasswordHash)) {
            closeWindow(); // Close the login window on successful login
        } else {
            errorLabel.setText("Incorrect Password. Try again!");
        }
    }

    private void closeWindow() {
        Stage stage = (Stage) submitButton.getScene().getWindow(); // Get the current stage
        stage.close(); // Close the window
    }
}