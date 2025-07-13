package me.ghosthacks96.ghostsecure.gui.auth;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import java.util.regex.Pattern;

public class SetPasswordGUI {

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    public ImageView appIcon;

    @FXML
    private TextField emailField;

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
    private String enteredEmail;


    @FXML
    private void onSubmitButtonClick() {
        String email = emailField.getText();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Make errorLabel visible if it's not already
        errorLabel.setVisible(true);

        // Validate email
        if (email.isEmpty()) {
            errorLabel.setText("Email address is required.");
            return;
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            errorLabel.setText("Please enter a valid email address.");
            return;
        }

        // Validate password fields
        if (password.isEmpty() || confirmPassword.isEmpty()) {
            errorLabel.setText("Both password fields are required.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            errorLabel.setText("Passwords do not match.");
            return;
        }

        if (password.length() < 8) {
            errorLabel.setText("Password must be at least 8 characters long.");
            return;
        }

        // Save the entered values
        enteredEmail = email;
        enteredPassword = password;
        passwordSet = true; // Mark as successful

        closeWindow();
    }

    public boolean isPasswordSet() {
        return passwordSet;
    }

    public String getEnteredPassword() {
        return enteredPassword;
    }

    /**
     * Get the email address entered by the user
     * @return the entered email address
     */
    public String getEnteredEmail() {
        return enteredEmail;
    }

    private void closeWindow() {
        Stage stage = (Stage) submitButton.getScene().getWindow();
        stage.close();
    }
}
