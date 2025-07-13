package me.ghosthacks96.ghostsecure.gui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.gui.auth.LoginGUI;
import me.ghosthacks96.ghostsecure.gui.auth.SetPasswordGUI;
import me.ghosthacks96.ghostsecure.gui.auth.TwoFactorAuthGUI;
import org.kordamp.bootstrapfx.BootstrapFX;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


/**
 * Handles creation and management of sub-GUI windows and dialogs
 */
public class SubGUIHandler {

    // Resource paths
    private static final String DARK_THEME_CSS = "/me/ghosthacks96/ghostsecure/css/dark-theme.css";
    private static final String APP_ICON_PATH = "/me/ghosthacks96/ghostsecure/imgs/app_icon.png";

    // FXML files
    private static final String LOGIN_FXML = "login.fxml";
    private static final String SET_PASSWORD_FXML = "setPasswordGUI.fxml";
    private static final String TWO_FACTOR_AUTH_FXML = "twoFactorAuth.fxml";

    // Window titles
    private static final String LOGIN_WINDOW_TITLE = "GhostSecure - Login";
    private static final String SET_PASSWORD_WINDOW_TITLE = "GhostSecure - Set Password";
    private static final String TWO_FACTOR_AUTH_WINDOW_TITLE = "GhostSecure - Two-Factor Authentication";

    public String loginError;

    /**
     * Opens the login dialog and waits for user input
     * @return true if login was successful, false otherwise
     */
    public boolean openLoginScene() {
        Main.logger.logDebug("openLoginScene() called");

        // Check if we're already on the JavaFX Application Thread
        if (Platform.isFxApplicationThread()) {
            return openLoginSceneDirectly();
        }

        try {
            Boolean result = executeOnFxThread(this::openLoginSceneDirectly);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            Main.logger.logError("Unexpected error in openLoginScene: " + e.getMessage());
            Main.logger.logDebug("Exception: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Direct login scene opening when on FX thread
     */
    private boolean openLoginSceneDirectly() {
        try {
            Stage loginStage = createLoginStage();
            LoginGUI loginController = getLoginController(loginStage);

            Main.logger.logDebug("Login stage showing");
            loginStage.showAndWait();

            boolean loginSuccessful = isLoginSuccessful();
            Main.logger.logDebug("Login result: " + loginSuccessful);

            return loginSuccessful;
        } catch (IOException e) {
            Main.logger.logError("Error loading login GUI: " + e.getMessage());
            Main.logger.logDebug("Exception: " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            Main.logger.logError("Unexpected error in direct login: " + e.getMessage());
            Main.logger.logDebug("Exception: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Shows the set password dialog and waits for user input
     * @return an array containing [password, email] if successful, null otherwise
     */
    public String[] showSetPassPrompt() {
        Main.logger.logDebug("showSetPassPrompt() called");

        if (Platform.isFxApplicationThread()) {
            return showSetPassPromptDirectly();
        }

        try {
            return executeOnFxThread(this::showSetPassPromptDirectly);
        } catch (Exception e) {
            Main.logger.logError("Unexpected error in showSetPassPrompt: " + e.getMessage());
            Main.logger.logDebug("Exception: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Direct set password prompt when on FX thread
     * @return an array containing [password, email] if successful, null otherwise
     */
    private String[] showSetPassPromptDirectly() {
        try {
            Stage passwordStage = createSetPasswordStage();
            SetPasswordGUI controller = getSetPasswordController(passwordStage);

            Main.logger.logDebug("SetPassword stage showing");
            passwordStage.showAndWait();

            return handlePasswordSetResult(controller);
        } catch (IOException e) {
            Main.logger.logError("Error loading set password GUI: " + e.getMessage());
            Main.logger.logDebug("Exception: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Show an information alert dialog
     */
    public void showInfo(String title, String message) {
        Main.logger.logDebug("showInfo() called: " + title + " - " + message);
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    /**
     * Show a warning alert dialog
     */
    public void showWarning(String title, String message) {
        Main.logger.logDebug("showWarning() called: " + title + " - " + message);
        showAlert(Alert.AlertType.WARNING, title, message);
    }

    /**
     * Show an error alert dialog
     */
    public void showError(String title, String message) {
        Main.logger.logDebug("showError() called: " + title + " - " + message);
        showAlert(Alert.AlertType.ERROR, title, message);
    }

    /**
     * Create and configure the login stage
     */
    private Stage createLoginStage() throws IOException {
        // Validate resource exists
        if (Main.class.getResource(LOGIN_FXML) == null) {
            throw new IOException("FXML resource not found: " + LOGIN_FXML);
        }

        FXMLLoader loginLoader = new FXMLLoader(Main.class.getResource(LOGIN_FXML));
        Scene loginScene = new Scene(loginLoader.load());

        Main.logger.logDebug("Login GUI loaded");

        Stage loginStage = new Stage();
        configureStage(loginStage, loginScene, LOGIN_WINDOW_TITLE);

        // Add BootstrapFX styling
        try {
            loginScene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        } catch (Exception e) {
            Main.logger.logWarning("Failed to add BootstrapFX stylesheet: " + e.getMessage());
        }

        // Store the loader for later access to the controller
        loginStage.setUserData(loginLoader);

        return loginStage;
    }

    /**
     * Get the login controller from the stage
     */
    private LoginGUI getLoginController(Stage loginStage) {
        FXMLLoader loginLoader = (FXMLLoader) loginStage.getUserData();
        if (loginLoader == null) {
            throw new RuntimeException("Login loader is null");
        }

        LoginGUI loginController = loginLoader.getController();
        if (loginController == null) {
            throw new RuntimeException("Login controller is null");
        }

        loginController.setError(loginError);
        return loginController;
    }

    /**
     * Create and configure the set password stage
     */
    private Stage createSetPasswordStage() throws IOException {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource(SET_PASSWORD_FXML));
        Scene scene = new Scene(loader.load());

        Main.logger.logDebug("SetPassword GUI loaded");

        Stage stage = new Stage();
        configureStage(stage, scene, SET_PASSWORD_WINDOW_TITLE);
        stage.setResizable(false);

        // Store the loader for later access to the controller
        stage.setUserData(loader);

        return stage;
    }

    /**
     * Get the set password controller from the stage
     */
    private SetPasswordGUI getSetPasswordController(Stage passwordStage) {
        FXMLLoader loader = (FXMLLoader) passwordStage.getUserData();
        return loader.getController();
    }

    /**
     * Handle the result of password setting
     * @return an array containing [password, email] if successful, null otherwise
     */
    private String[] handlePasswordSetResult(SetPasswordGUI controller) {
        if (controller.isPasswordSet()) {
            Main.logger.logDebug("Password set successfully");
            return new String[] {
                controller.getEnteredPassword(),
                controller.getEnteredEmail()
            };
        } else {
            Main.logger.logDebug("Password setup was unsuccessful");
            return null;
        }
    }

    /**
     * Configure common stage properties
     */
    private void configureStage(Stage stage, Scene scene, String title) {
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.requestFocus();

        // Force stage to front and ensure it's not minimized
        stage.toFront();
        stage.setIconified(false);

        // Apply dark theme
        try {
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource(DARK_THEME_CSS)).toExternalForm()
            );
        } catch (Exception e) {
            Main.logger.logWarning("Failed to apply dark theme: " + e.getMessage());
        }

        // Set application icon
        try {
            stage.getIcons().add(
                    new Image(Objects.requireNonNull(getClass().getResource(APP_ICON_PATH)).toExternalForm())
            );
        } catch (Exception e) {
            Main.logger.logWarning("Failed to set application icon: " + e.getMessage());
        }
    }

    /**
     * Check if login was successful by comparing password hashes
     */
    private boolean isLoginSuccessful() {
        String storedHash = Main.accountStorage.get("password_hash", "");
        String enteredHash = LoginGUI.enteredPasswordHash;

        if (storedHash == null || enteredHash == null) {
            Main.logger.logWarning("Password hash verification failed - null value detected");
            return false;
        }

        return storedHash.equals(enteredHash);
    }

    /**
     * Shows the two-factor authentication dialog and waits for user input
     * @param verificationCode The code that the user should enter
     * @param email The user's email address
     * @param resendAction A runnable that will be executed when the resend button is clicked
     * @return true if verification was successful, false otherwise
     */
    public boolean showTwoFactorAuthPrompt(String verificationCode, String email, Runnable resendAction) {
        Main.logger.logDebug("showTwoFactorAuthPrompt() called");

        if (Platform.isFxApplicationThread()) {
            return showTwoFactorAuthPromptDirectly(verificationCode, email, resendAction);
        }

        try {
            return Boolean.TRUE.equals(executeOnFxThread(() -> 
                showTwoFactorAuthPromptDirectly(verificationCode, email, resendAction)));
        } catch (Exception e) {
            Main.logger.logError("Unexpected error in showTwoFactorAuthPrompt: " + e.getMessage());
            Main.logger.logDebug("Exception: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Direct two-factor authentication prompt when on FX thread
     */
    private boolean showTwoFactorAuthPromptDirectly(String verificationCode, String email, Runnable resendAction) {
        try {
            Stage twoFactorAuthStage = createTwoFactorAuthStage();
            TwoFactorAuthGUI controller = getTwoFactorAuthController(twoFactorAuthStage);

            // Initialize the controller with the verification code and email
            controller.initialize(verificationCode, email, resendAction);

            Main.logger.logDebug("TwoFactorAuth stage showing");
            twoFactorAuthStage.showAndWait();

            return controller.isVerificationSuccessful();
        } catch (IOException e) {
            Main.logger.logError("Error loading two-factor authentication GUI: " + e.getMessage());
            Main.logger.logDebug("Exception: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Create and configure the two-factor authentication stage
     */
    private Stage createTwoFactorAuthStage() throws IOException {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource(TWO_FACTOR_AUTH_FXML));
        Scene scene = new Scene(loader.load());

        Main.logger.logDebug("TwoFactorAuth GUI loaded");

        Stage stage = new Stage();
        configureStage(stage, scene, TWO_FACTOR_AUTH_WINDOW_TITLE);
        stage.setResizable(false);

        // Store the loader for later access to the controller
        stage.setUserData(loader);

        return stage;
    }

    /**
     * Get the two-factor authentication controller from the stage
     */
    private TwoFactorAuthGUI getTwoFactorAuthController(Stage twoFactorAuthStage) {
        FXMLLoader loader = (FXMLLoader) twoFactorAuthStage.getUserData();
        return loader.getController();
    }

    /**
     * Execute a task on the JavaFX Application Thread and wait for completion
     */
    private <T> T executeOnFxThread(FxTask<T> task) {
        final Object[] resultHolder = new Object[1];
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                resultHolder[0] = task.execute();
            } catch (Exception e) {
                resultHolder[0] = e;
            } finally {
                latch.countDown();
            }
        });

        try {
            // Add timeout to prevent indefinite waiting
            boolean completed = latch.await(120, TimeUnit.SECONDS);
            if (!completed) {
                Main.logger.logError("FX thread execution timed out after 120 seconds");
                return null;
            }
        } catch (InterruptedException e) {
            Main.logger.logError("FX thread execution was interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        }

        // If an exception was thrown, handle it
        if (resultHolder[0] instanceof Exception e) {
            Main.logger.logError("Error in FX thread: " + e.getMessage(), e);
            return null;
        }

        @SuppressWarnings("unchecked")
        T result = (T) resultHolder[0];
        return result;
    }

    /**
     * Show an alert dialog with the specified type, title, and message
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Main.logger.logDebug("showAlert() called: " + type + ", " + title + ", " + message);

        Platform.runLater(() -> {
            Alert alert = createAlert(type, title, message);
            alert.showAndWait();
        });
    }

    /**
     * Create and configure an alert dialog
     */
    private Alert createAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Ensure the alert stays on top
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.setAlwaysOnTop(true);

        return alert;
    }

    /**
     * Functional interface for tasks that need to be executed on the JavaFX thread
     */
    @FunctionalInterface
    private interface FxTask<T> {
        T execute() throws Exception;
    }
}
