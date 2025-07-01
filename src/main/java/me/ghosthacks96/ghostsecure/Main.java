package me.ghosthacks96.ghostsecure;

// JavaFX imports

import com.google.gson.JsonArray;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import me.ghosthacks96.ghostsecure.gui.HomeGUI;
import me.ghosthacks96.ghostsecure.gui.SplashGUI;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

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
    public static String VERSION = "2.3.5";

    // Splash screen components
    private static Stage splashStage;
    private static SplashGUI splashController;

    public static void main(String[] args) {
        logger = new Logging();
        logger.logInfo("Application started.");
        logger.logInfo("Initializing GhostSecure... v"+VERSION);

        launch();
    }

    /**
     * Shows the splash screen
     */
    private void showSplashScreen() throws IOException {
        FXMLLoader splashLoader = new FXMLLoader(Main.class.getResource("splash.fxml"));
        Scene splashScene = new Scene(splashLoader.load());
        splashScene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/me/ghosthacks96/ghostsecure/dark-theme.css")).toExternalForm());

        splashController = splashLoader.getController();

        splashStage = new Stage();
        splashStage.initStyle(StageStyle.UNDECORATED);
        splashStage.setScene(splashScene);
        splashStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResource("/me/ghosthacks96/ghostsecure/app_icon.png")).toExternalForm()));
        splashStage.setTitle("GhostSecure - Loading");
        splashStage.setAlwaysOnTop(true);

        splashController.setStage(splashStage);
        splashStage.show();
    }

    public static void shiftDebug(boolean debug){
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
            // Show splash screen first
            showSplashScreen();

            // Create a task for initialization
            Task<Boolean> initTask = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    boolean loginSuccessful = false;

                    try {
                        // Chain of CompletableFutures for each initialization step
                        loginSuccessful = checkForUpdates()
                            .thenCompose(v -> loadConfiguration())
                            .thenCompose(v -> checkRecoveryStatus())
                            .thenCompose(v -> handleConfigurationAndLogin())
                            .thenApply(result -> {
                                updateSplashMessage("Preparing main interface...");
                                return result;
                            })
                            .exceptionally(e -> {
                                Throwable cause = e.getCause();
                                logger.logError("Initialization failed: " + (cause != null ? cause.getMessage() : e.getMessage()), e);
                                Platform.runLater(() -> {
                                    splashController.close();
                                    sgh.showError("Initialization Error", "Failed to initialize application: " + 
                                        (cause != null ? cause.getMessage() : e.getMessage()));
                                });
                                return false;
                            })
                            .get(); // Wait for the entire chain to complete
                    } catch (InterruptedException e) {
                        logger.logError("Initialization was interrupted: " + e.getMessage(), e);
                        Thread.currentThread().interrupt();
                        return false;
                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause();
                        logger.logError("Error during initialization: " + (cause != null ? cause.getMessage() : e.getMessage()), e);
                        return false;
                    }

                    return loginSuccessful;
                }

                // Step 1: Check for updates
                private CompletableFuture<Void> checkForUpdates() {
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            updateSplashMessage("Checking for updates...");
                            new Update("GhostSecure", VERSION);
                            return null;
                        } catch (Exception e) {
                            logger.logError("Error checking for updates: " + e.getMessage(), e);
                            throw new CompletionException("Update check failed", e);
                        }
                    });
                }

                // Step 2: Load configuration
                private CompletableFuture<Void> loadConfiguration() {
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            updateSplashMessage("Loading configuration...");
                            config = new Config();
                            recoveryHandler = new RecoveryHandler();
                            return null;
                        } catch (Exception e) {
                            logger.logError("Error loading configuration: " + e.getMessage(), e);
                            throw new CompletionException("Configuration loading failed", e);
                        }
                    });
                }

                // Step 3: Check recovery status
                private CompletableFuture<Void> checkRecoveryStatus() {
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            updateSplashMessage("Checking recovery status...");
                            if (recoveryHandler.shouldEnterRecoveryMode()) {
                                logger.logInfo("Recovery mode detected. Initiating password recovery...");

                                // Close splash for recovery interaction
                                Platform.runLater(() -> splashController.close());

                                if (recoveryHandler.initiateRecovery()) {
                                    logger.logInfo("Password recovery completed successfully.");
                                    // Continue with normal startup after successful recovery
                                } else {
                                    logger.logError("Password recovery failed. Exiting application.");
                                    sgh.showError("Recovery Failed", "Password recovery failed. Please contact support.");
                                    throw new RuntimeException("Recovery failed");
                                }
                            } else {
                                logger.logInfo("No recovery needed. Continuing with normal startup.");
                            }
                            return null;
                        } catch (Exception e) {
                            logger.logError("Error during recovery check: " + e.getMessage(), e);
                            throw new CompletionException("Recovery process failed", e);
                        }
                    });
                }

                // Step 4: Handle configuration and login
                private CompletableFuture<Boolean> handleConfigurationAndLogin() {
                    return CompletableFuture.supplyAsync(() -> {
                        boolean loginSuccessful = false;
                        updateSplashMessage("Checking configuration...");

                        try {
                            if (!config.getConfigFile().exists()) {
                                try {
                                    config.setDefaultConfig();
                                } catch (Exception e) {
                                    logger.logError("Error setting default config: " + e.getMessage(), e);
                                    sgh.showError("Configuration Error", "Failed to set default configuration: " + e.getMessage());
                                    return false;
                                }

                                config.getJsonConfig().addProperty("mode", "unlock");
                                config.getJsonConfig().add("programs", new JsonArray());
                                config.getJsonConfig().add("folders", new JsonArray());
                                logger.logInfo("Starting new setup.");

                                // Close splash for password setup interaction
                                Platform.runLater(() -> splashController.close());

                                String newPassword = sgh.showSetPassPrompt();
                                if (newPassword == null || newPassword.isEmpty()) {
                                    logger.logError("Password setup failed. Exiting application.");
                                    sgh.showError("Setup Error", "Password setup is required to proceed.");
                                    return false;
                                }
                                Config.PASSWORD_HASH = hashPassword(newPassword);
                                config.getJsonConfig().addProperty("password", Config.PASSWORD_HASH);
                                loginSuccessful = true;

                                logger.logInfo("New password setup successfully.");
                            } else {
                                updateSplashMessage("Loading configuration...");
                                Config.loadConfig(false);

                                ServiceController.startBlockerDaemon();
                                // Close splash for login interaction
                                Platform.runLater(() -> splashController.close());

                                loginSuccessful = sgh.openLoginScene();
                                if (!loginSuccessful) {
                                    logger.logError("Login failed. Invalid password.");
                                    config.getJsonConfig().remove("mode");
                                    config.getJsonConfig().addProperty("mode", "lock");
                                } else {
                                    logger.logInfo("Login successful.");
                                }
                            }
                        } catch (Exception e) {
                            logger.logError("Error in configuration and login process: " + e.getMessage(), e);
                            sgh.showError("Error", "An error occurred during configuration and login: " + e.getMessage());
                            return false;
                        }

                        return loginSuccessful;
                    });
                }

                private void updateSplashMessage(String message) {
                    updateProgress(-1, 100); // Indeterminate progress
                    updateMessage(message);
                    Platform.runLater(() -> splashController.updateStatus(message));
                    logger.logInfo(message);
                }
            };

            // Set up what happens when initialization is complete
            initTask.setOnSucceeded(event -> {
                try {
                    boolean loginSuccessful = initTask.getValue();

                    // Load main UI
                    mainStage = stage;
                    mainLoader = new FXMLLoader(Main.class.getResource("home.fxml"));
                    mainScene = new Scene(mainLoader.load());
                    mainScene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/me/ghosthacks96/ghostsecure/dark-theme.css")).toExternalForm());
                    mainStage.setTitle("Ghost Secure - Home");
                    mainStage.setScene(mainScene);

                    mainStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResource("/me/ghosthacks96/ghostsecure/app_icon.png")).toExternalForm()));
                    mainStage.initStyle(StageStyle.DECORATED);

                    // Close splash screen
                    splashController.close();

                    if (!ServiceController.isServiceRunning() && loginSuccessful) {
                        mainStage.show();
                        logger.logInfo("Main stage displayed successfully.");
                    }

                    SystemTrayIntegration sysTray = new SystemTrayIntegration();
                    sysTray.setupSystemTray(mainStage);

                } catch (Exception e) {
                    logger.logError("Error initializing main UI: " + e.getMessage(), e);
                    splashController.close();
                    sgh.showError("Initialization Error", "Failed to initialize application: " + e.getMessage());
                }
            });

            initTask.setOnFailed(event -> {
                logger.logError("Initialization failed: " + initTask.getException().getMessage(), initTask.getException());
                splashController.close();
                sgh.showError("Initialization Error", "Failed to initialize application: " + initTask.getException().getMessage());
            });

            // Start the initialization task
            new Thread(initTask).start();

        } catch (Exception e) {
            logger.logError("Error starting application: " + e.getMessage(), e);
            sgh.showError("Startup Error", "Failed to start application: " + e.getMessage());
        }
    }
}
