package me.ghosthacks96.ghostsecure.gui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;

/**
 * Controller for the login screen
 */
public class LoginGUI {
    
    private String correctPasswordHash = "";
    public static String enteredPasswordHash;

    @FXML private PasswordField passwordField;
    @FXML private Button submitButton;
    @FXML private Label errorLabel;

    @FXML
    public void initialize() {
        correctPasswordHash = Main.config.PASSWORD_HASH;
        
        // Set up enter key handler
        passwordField.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER")) {
                onSubmitButtonClick();
            }
        });
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
