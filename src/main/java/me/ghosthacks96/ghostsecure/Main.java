package me.ghosthacks96.ghostsecure;

// JavaFX imports

import com.google.gson.JsonArray;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;
import me.ghosthacks96.ghostsecure.utils.RecoveryHandler;
import me.ghosthacks96.ghostsecure.utils.Update;
import me.ghosthacks96.ghostsecure.utils.controllers.*;

import java.io.File;
import java.io.IOException;
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

    public static boolean DEBUG_MODE = false;

    public static void main(String[] args) {
        logger = new Logging();
        logger.logInfo("Application started.");
        logger.logInfo("Initializing GhostSecure... v 2.3.0");

        new Update("GhostSecure", "2.3.0");

        config = new Config();
        recoveryHandler = new RecoveryHandler();

        launch();
    }

    public void shiftDebug(boolean debug){
        DEBUG_MODE = debug;
        try {
            if (DEBUG_MODE) {
                File debugFile = new File(appDataPath + "debug.txt");
                if(!debugFile.exists()) debugFile.createNewFile();

                logger.logInfo("Debug mode enabled.");
            } else {
                File debugFile = new File(appDataPath + "debug.txt");
                if(debugFile.exists()) debugFile.delete();
                logger.logInfo("Debug mode disabled.");
            }
        } catch (IOException e) {
            logger.logError("Failed to shift debug mode: " + e.getMessage());
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
            logger.logError("Error hashing password: " + e.getMessage(), e);
            return null;
        }
    }


    @Override
    public void start(Stage stage) {
        try {
            boolean loginSuccessful = false;
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
            }else{
                logger.logInfo("No recovery needed. Continuing with normal startup.");
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
                loginSuccessful = true;

                logger.logInfo("New password setup successfully.");
            } else {
                Config.loadConfig(false);
                int tries = 3;
                    loginSuccessful = sgh.openLoginScene();
                    if (!loginSuccessful) {
                        logger.logError("Login failed. Invalid password.");
                        sgh.showError("Login Failed", "no password, no touchy...");
                        config.getJsonConfig().remove("mode");
                        config.getJsonConfig().addProperty("mode", "lock");
                    } else {
                        logger.logInfo("Login successful.");
                    }

            }
            mainStage = stage;
            mainLoader = new FXMLLoader(Main.class.getResource("home.fxml"));
            mainScene = new Scene(mainLoader.load());
            mainScene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/me/ghosthacks96/ghostsecure/dark-theme.css")).toExternalForm());
            mainStage.setTitle("Ghost Secure - Home");
            mainStage.setScene(mainScene);

            mainStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResource("/me/ghosthacks96/ghostsecure/app_icon.png")).toExternalForm()));
            mainStage.initStyle(StageStyle.DECORATED);


            ServiceController.startBlockerDaemon();


            if (!ServiceController.isServiceRunning() &&  loginSuccessful) {
                mainStage.show();
                logger.logInfo("Main stage displayed successfully.");
            }

            SystemTrayIntegration sysTray = new SystemTrayIntegration();
            sysTray.setupSystemTray(mainStage);

        } catch (Exception e) {
            logger.logError("Error initializing application: " + e.getMessage(), e);
        }
    }
}