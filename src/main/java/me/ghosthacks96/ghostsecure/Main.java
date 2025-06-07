package me.ghosthacks96.ghostsecure;

import com.google.gson.JsonArray;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import me.ghosthacks96.ghostsecure.gui.Login_GUI;
import me.ghosthacks96.ghostsecure.gui.SetPassword_GUI;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;
import me.ghosthacks96.ghostsecure.utils.PopUps;
import me.ghosthacks96.ghostsecure.utils.controllers.Config;
import me.ghosthacks96.ghostsecure.utils.controllers.Logging;
import me.ghosthacks96.ghostsecure.utils.controllers.ServiceController;
import me.ghosthacks96.ghostsecure.utils.controllers.SystemTrayIntegration;
import org.kordamp.bootstrapfx.BootstrapFX;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class Main extends Application {

    public static ArrayList<LockedItem> lockedItems = new ArrayList<>();
    public static String PASSWORD_HASH;
    public static Config config;

    public static Logging logger;

    public static FXMLLoader mainLoader;
    public static Scene mainScene;
    public static Stage mainStage;
    public static ServiceController sc;


    public static void main(String[] args) {
        sc = new ServiceController();
        logger = new Logging();
        logger.logInfo("Application started.");

        launch();
    }

    public static boolean openLoginScene() {
        try {
            // Load the Login GUI
            FXMLLoader loginLoader = new FXMLLoader(Main.class.getResource("login.fxml"));
            Stage loginStage = new Stage();
            Scene loginScene = new Scene(loginLoader.load());
            loginStage.initModality(Modality.APPLICATION_MODAL);
            loginScene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
            loginStage.initStyle(StageStyle.DECORATED);
            loginScene.getStylesheets().add(Main.class.getResource("/me/ghosthacks96/ghostsecure/dark-theme.css").toExternalForm());
            loginStage.setAlwaysOnTop(true);
            loginStage.requestFocus();
            loginStage.getIcons().add(new javafx.scene.image.Image(Main.class.getResource("/me/ghosthacks96/ghostsecure/app_icon.png").toExternalForm()));

            // Set up the login stage
            loginStage.setTitle("GhostSecure - Login");
            loginStage.setScene(loginScene);
            loginStage.showAndWait();

            // Check if the login was successful
            return PASSWORD_HASH != null && PASSWORD_HASH.equals(Login_GUI.enteredPasswordHash);
        } catch (IOException e) {
            e.printStackTrace();
            return false; // Login failed due to error
        }
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException("Error hashing password: " + e.getMessage());
        }
    }

    private String showSetPassPrompt() {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("setPasswordGUI.fxml"));
            Stage stage = new Stage();
            Scene scene = new Scene(loader.load());
            stage.setTitle("GhostSecure - Set Password");
            stage.initModality(Modality.APPLICATION_MODAL); // Makes the prompt modal
            stage.initStyle(StageStyle.DECORATED);
            scene.getStylesheets().add(getClass().getResource("/me/ghosthacks96/ghostsecure/dark-theme.css").toExternalForm());

            stage.setScene(scene);
            stage.setResizable(false);
            stage.setAlwaysOnTop(true);
            stage.requestFocus();
            stage.getIcons().add(new javafx.scene.image.Image(getClass().getResource("/me/ghosthacks96/ghostsecure/app_icon.png").toExternalForm()));

            SetPassword_GUI controller = loader.getController();
            stage.showAndWait(); // Wait until the user closes the popup

            if (controller.isPasswordSet()) {
                return controller.getEnteredPassword(); // Return the successfully entered password
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null; // Return null if the password setup was unsuccessful
    }

    @Override
    public void start(Stage stage) {
        try {
            config = new Config(this);
            if (!config.getConfigFile().exists()) {
                config.setDefaultConfig();

                config.getJsonConfig().addProperty("mode", "unlock");
                config.getJsonConfig().add("programs", new JsonArray());
                config.getJsonConfig().add("folders", new JsonArray());
                logger.logInfo("Starting new setup.");
                String newPassword = showSetPassPrompt();
                if (newPassword == null || newPassword.isEmpty()) {
                    logger.logError("Password setup failed. Exiting application.");
                    PopUps.showError("Setup Error", "Password setup is required to proceed.");
                    System.exit(1);
                }
                PASSWORD_HASH = hashPassword(newPassword);
                config.getJsonConfig().addProperty("password", PASSWORD_HASH);

                logger.logInfo("New password setup successfully.");
            } else {
                config.loadConfig(false);
                int tries = 3;
                while (true) {
                    boolean loginSuccessful = openLoginScene();
                    if (!loginSuccessful) {
                        logger.logError("Login failed. Invalid password.");
                        PopUps.showError("Login Failed", "Invalid password. Tries left: " + tries);
                        if (tries == 0) {
                            System.exit(1);
                        }
                        tries--;
                    } else {
                        break;
                    }
                }
            }

            mainStage = stage;
            mainLoader = new FXMLLoader(Main.class.getResource("home.fxml"));
            mainScene = new Scene(mainLoader.load(), 600, 500);
            mainScene.getStylesheets().add(getClass().getResource("/me/ghosthacks96/ghostsecure/dark-theme.css").toExternalForm());
            mainStage.setTitle("App Locker");
            mainStage.setScene(mainScene);

            mainStage.getIcons().add(new javafx.scene.image.Image(getClass().getResource("/me/ghosthacks96/ghostsecure/app_icon.png").toExternalForm()));
            mainStage.initStyle(StageStyle.DECORATED);


            sc.startBlockerDaemon();


            if (!sc.isServiceRunning()) {
                mainStage.show();
                logger.logInfo("Main stage displayed successfully.");
            } else {
                PopUps.showInfo("Minimized to System Tray", "GhostSecure is minimized to the system tray. You can restore it by using the open GUI button in the tray icon menu.");
            }

            SystemTrayIntegration sysTray = new SystemTrayIntegration();
            sysTray.setupSystemTray(mainStage);

        } catch (Exception e) {
            e.printStackTrace();
            logger.logError("Error initializing application: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}