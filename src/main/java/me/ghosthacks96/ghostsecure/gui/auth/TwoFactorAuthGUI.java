package me.ghosthacks96.ghostsecure.gui.auth;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;

/**
 * Controller for the two-factor authentication screen
 */
public class TwoFactorAuthGUI {

    @FXML
    public ImageView appIcon;
    
    @FXML
    private TextField codeField;
    
    @FXML
    private Button submitButton;
    
    @FXML
    private Button resendButton;
    
    @FXML
    private Label errorLabel;
    
    private boolean verificationSuccessful = false;
    private String expectedCode;
    private String userEmail;
    private Runnable resendCodeAction;
    
    /**
     * Initialize the controller with the expected verification code and user email
     * @param verificationCode The code that the user should enter
     * @param email The user's email address
     * @param resendAction A runnable that will be executed when the resend button is clicked
     */
    public void initialize(String verificationCode, String email, Runnable resendAction) {
        this.expectedCode = verificationCode;
        this.userEmail = email;
        this.resendCodeAction = resendAction;
        
        // Make sure error label is initially hidden
        errorLabel.setVisible(false);
    }
    
    /**
     * Handle the submit button click
     */
    @FXML
    private void onSubmitButtonClick() {
        String enteredCode = codeField.getText().trim();
        
        // Make error label visible for feedback
        errorLabel.setVisible(true);
        
        if (enteredCode.isEmpty()) {
            errorLabel.setText("Please enter the verification code.");
            return;
        }
        
        if (!enteredCode.equals(expectedCode)) {
            errorLabel.setText("Invalid verification code. Please try again.");
            return;
        }
        
        // Code is valid
        verificationSuccessful = true;
        Main.logger.logInfo("Two-factor authentication successful for user: " + userEmail);
        closeWindow();
    }
    
    /**
     * Handle the resend button click
     */
    @FXML
    private void onResendButtonClick() {
        if (resendCodeAction != null) {
            resendCodeAction.run();
            errorLabel.setVisible(true);
            errorLabel.setText("A new verification code has been sent to your email.");
        }
    }
    
    /**
     * Check if verification was successful
     * @return true if the correct code was entered, false otherwise
     */
    public boolean isVerificationSuccessful() {
        return verificationSuccessful;
    }
    
    /**
     * Set an error message to display
     * @param message The error message to display
     */
    public void setError(String message) {
        errorLabel.setVisible(true);
        errorLabel.setText(message);
    }
    
    /**
     * Close the window
     */
    private void closeWindow() {
        Stage stage = (Stage) submitButton.getScene().getWindow();
        stage.close();
    }
}