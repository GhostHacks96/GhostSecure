package me.ghosthacks96.ghostsecure;

// JavaFX imports

import com.google.gson.JsonArray;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;
import me.ghosthacks96.ghostsecure.utils.RecoveryHandler;
import me.ghosthacks96.ghostsecure.utils.Update;
import me.ghosthacks96.ghostsecure.utils.controllers.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Main application class handling initialization and core functionality
 */
public class Main extends Application {

    // Static components
    public static ArrayList<LockedItem> lockedItems = new ArrayList<>();
    public static String appDataPath = System.getenv("APPDATA") + "/ghosthacks96/GhostSecure/";
    public static Config config;
    public static Logging logger;
    public static SubGUIHandler sgh = new SubGUIHandler();
    public static RecoveryHandler recoveryHandler;

    // UI components
    public static FXMLLoader mainLoader;
    public static Scene mainScene;
    public static Stage mainStage;

    public static void main(String[] args) {
        logger = new Logging();
        logger.logInfo("Application started.");

        new Update("GhostSecure", "2.2.5");

        config = new Config();
        recoveryHandler = new RecoveryHandler();

        launch();
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


    @Override
    public void start(Stage stage) {
        try {
            // Check for recovery mode first
            if (recoveryHandler.shouldEnterRecoveryMode()) {
                logger.logInfo("Recovery mode detected. Initiating password recovery...");

                if (recoveryHandler.initiateRecovery()) {
                    logger.logInfo("Password recovery completed successfully.");
                    // Continue with normal startup after successful recovery
                } else {
                    logger.logError("Password recovery failed. Exiting application.");
                    sgh.showError("Recovery Failed", "Password recovery failed. Please contact support.");
                    System.exit(1);
                    return;
                }
            }

            if (!config.getConfigFile().exists()) {
                config.setDefaultConfig();

                config.getJsonConfig().addProperty("mode", "unlock");
                config.getJsonConfig().add("programs", new JsonArray());
                config.getJsonConfig().add("folders", new JsonArray());
                logger.logInfo("Starting new setup.");
                String newPassword = sgh.showSetPassPrompt();
                if (newPassword == null || newPassword.isEmpty()) {
                    logger.logError("Password setup failed. Exiting application.");
                    sgh.showError("Setup Error", "Password setup is required to proceed.");
                    System.exit(1);
                }
                Config.PASSWORD_HASH = hashPassword(newPassword);
                config.getJsonConfig().addProperty("password", Config.PASSWORD_HASH);

                logger.logInfo("New password setup successfully.");
            } else {
                Config.loadConfig(false);
                int tries = 3;
                while (true) {
                    boolean loginSuccessful = sgh.openLoginScene();
                    if (!loginSuccessful) {
                        logger.logError("Login failed. Invalid password.");
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
            mainScene = new Scene(mainLoader.load());
            mainScene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/me/ghosthacks96/ghostsecure/dark-theme.css")).toExternalForm());
            mainStage.setTitle("Ghost Secure - Home");
            mainStage.setScene(mainScene);

            mainStage.getIcons().add(new javafx.scene.image.Image(Objects.requireNonNull(getClass().getResource("/me/ghosthacks96/ghostsecure/app_icon.png")).toExternalForm()));
            mainStage.initStyle(StageStyle.DECORATED);


            ServiceController.startBlockerDaemon();


            if (!ServiceController.isServiceRunning()) {
                mainStage.show();
                logger.logInfo("Main stage displayed successfully.");
            } else {
                sgh.showInfo("Minimized to System Tray", "GhostSecure is minimized to the system tray. You can restore it by using the open GUI button in the tray icon menu.");
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