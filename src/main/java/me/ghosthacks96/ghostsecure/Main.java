package me.ghosthacks96.ghostsecure;

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
import me.ghosthacks96.ghostsecure.gui.extras.SplashGUI;
import me.ghosthacks96.ghostsecure.gui.SubGUIHandler;
import me.ghosthacks96.ghostsecure.gui.tabs.ServiceControllerScreen;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;
import me.ghosthacks96.ghostsecure.utils.api_handlers.RecoveryHandler;
import me.ghosthacks96.ghostsecure.utils.api_handlers.Update;
import me.ghosthacks96.ghostsecure.utils.services.*;
import me.ghosthacks96.ghostsecure.utils.debug.DebugConsole;
import me.ghosthacks96.ghostsecure.utils.file_handlers.Config;
import me.ghosthacks96.ghostsecure.utils.file_handlers.Logging;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static me.ghosthacks96.ghostsecure.utils.encryption.EncryptionUtils.hashPassword;

/**
 * Main application class for GhostSecure
 * Handles initialization, configuration, and core functionality
 */
public class Main extends Application {

    // Application constants
    public static final String VERSION = "2.3.6";
    public static final String APP_DATA_PATH = System.getenv("APPDATA") + "/ghosthacks96/GhostSecure/";
    private static final String DEBUG_FILE_PATH = APP_DATA_PATH + "debug.txt";

    public static Config config;
    public static Logging logger;
    public static SubGUIHandler sgh = new SubGUIHandler();
    public static RecoveryHandler recoveryHandler;

    // UI components
    public static FXMLLoader mainLoader;
    public static Scene mainScene;
    public static Stage mainStage;

    // Application state
    public static boolean DEBUG_MODE = false;
    private static SplashGUI splashController;

    public static void main(String[] args) {
        initializeLogging();
        checkDebugMode();
        launch();
    }


    /**
     * Initialize the logging system
     */
    private static void initializeLogging() {
        logger = new Logging();
        logger.logInfo("Application started.");
        logger.logInfo("Initializing GhostSecure... v" + VERSION);
    }

    /**
     * Check if debug mode should be enabled
     */
    private static void checkDebugMode() {
        DEBUG_MODE = true;//new File(DEBUG_FILE_PATH).exists();
        logger.logInfo("Debug mode is " + (DEBUG_MODE ? "enabled" : "disabled"));
    }

    /**
     * Toggle debug mode on/off
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void shiftDebug(boolean debug) {
        DEBUG_MODE = debug;
        try {
            File debugFile = new File(DEBUG_FILE_PATH);

            if (DEBUG_MODE) {
                if (!debugFile.exists()) {
                    debugFile.createNewFile();
                }
                logger.logInfo("Debug mode enabled.");
            } else {
                if (debugFile.exists()) {
                    debugFile.delete();
                }
                logger.logInfo("Debug mode disabled.");
            }
        } catch (IOException e) {
            logger.logError("Failed to shift debug mode: " + e.getMessage());
        }
    }


    @Override
    public void start(Stage stage) {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutDownSystem));
            showSplashScreen();
            startInitializationTask(stage);
        } catch (Exception e) {
            logger.logError("Error starting application: " + e.getMessage(), e);
            sgh.showError("Startup Error", "Failed to start application: " + e.getMessage());
        }
    }

    /**
     * Display the splash screen
     */
    private void showSplashScreen() throws IOException {
        FXMLLoader splashLoader = new FXMLLoader(Main.class.getResource("splash.fxml"));
        Scene splashScene = new Scene(splashLoader.load());

        // Apply dark theme
        splashScene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/me/ghosthacks96/ghostsecure/css/dark-theme.css"))
                        .toExternalForm()
        );

        splashController = splashLoader.getController();

        Stage splashStage = new Stage();
        splashStage.initStyle(StageStyle.UNDECORATED);
        splashStage.setScene(splashScene);
        splashStage.getIcons().add(
                new Image(Objects.requireNonNull(getClass().getResource("/me/ghosthacks96/ghostsecure/imgs/app_icon.png"))
                        .toExternalForm())
        );
        splashStage.setTitle("GhostSecure - Loading");
        splashStage.setAlwaysOnTop(true);

        splashController.setStage(splashStage);
        splashStage.show();
    }

    /**
     * Start the initialization task in a background thread
     */
    private void startInitializationTask(Stage stage) {
        Task<Boolean> initTask = createInitializationTask();

        initTask.setOnSucceeded(event -> handleInitializationSuccess(stage, initTask));
        initTask.setOnFailed(event -> handleInitializationFailure(initTask));

        new Thread(initTask).start();
    }

    /**
     * Create the initialization task
     */
    private Task<Boolean> createInitializationTask() {
        return new Task<>() {
            @Override
            protected Boolean call() {
                try {
                    return performInitialization();
                } catch (InterruptedException e) {
                    logger.logError("Initialization was interrupted: " + e.getMessage(), e);
                    Thread.currentThread().interrupt();
                    return false;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    logger.logError("Error during initialization: " +
                            (cause != null ? cause.getMessage() : e.getMessage()), e);
                    return false;
                }
            }

            private Boolean performInitialization() throws InterruptedException, ExecutionException {
                return checkForUpdates()
                        .thenCompose(v -> loadConfiguration())
                        .thenCompose(v -> checkRecoveryStatus())
                        .thenCompose(v -> handleConfigurationAndLogin())
                        .thenApply(result -> {
                            updateSplashMessage("Preparing main interface...");
                            return result;
                        })
                        .exceptionally(this::handleInitializationException)
                        .get();
            }

            private Boolean handleInitializationException(Throwable e) {
                Throwable cause = e.getCause();
                logger.logError("Initialization failed: " +
                        (cause != null ? cause.getMessage() : e.getMessage()), e);
                Platform.runLater(() -> {
                    splashController.close();
                    sgh.showError("Initialization Error", "Failed to initialize application: " +
                            (cause != null ? cause.getMessage() : e.getMessage()));
                });
                return false;
            }

            private CompletableFuture<Void> checkForUpdates() {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        updateSplashMessage("Checking for updates...");
                        Update update = new Update("GhostSecure", VERSION);
                        if (update.updateCheck()) {
                            System.exit(0);
                        }
                        return null;
                    } catch (Exception e) {
                        logger.logError("Error checking for updates: " + e.getMessage(), e);
                        throw new CompletionException("Update check failed", e);
                    }
                });
            }

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

            private CompletableFuture<Void> checkRecoveryStatus() {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        updateSplashMessage("Checking recovery status...");

                        if (recoveryHandler.shouldEnterRecoveryMode()) {
                            return handleRecoveryMode();
                        } else {
                            logger.logInfo("No recovery needed. Continuing with normal startup.");
                            return null;
                        }
                    } catch (Exception e) {
                        logger.logError("Error during recovery check: " + e.getMessage(), e);
                        throw new CompletionException("Recovery process failed", e);
                    }
                });
            }

            private Void handleRecoveryMode() {
                logger.logInfo("Recovery mode detected. Initiating password recovery...");

                Platform.runLater(() -> splashController.close());

                if (recoveryHandler.initiateRecovery()) {
                    logger.logInfo("Password recovery completed successfully.");
                } else {
                    logger.logError("Password recovery failed. Exiting application.");
                    sgh.showError("Recovery Failed", "Password recovery failed. Please contact support.");
                    throw new RuntimeException("Recovery failed");
                }
                return null;
            }

            private CompletableFuture<Boolean> handleConfigurationAndLogin() {
                return CompletableFuture.supplyAsync(() -> {
                    updateSplashMessage("Checking configuration...");

                    try {
                        if (!config.getConfigFile().exists()) {
                            return handleFirstTimeSetup();
                        } else {
                            return handleExistingConfiguration();
                        }
                    } catch (Exception e) {
                        logger.logError("Error in configuration and login process: " + e.getMessage(), e);
                        sgh.showError("Error", "An error occurred during configuration and login: " + e.getMessage());
                        return false;
                    }
                });
            }

            private boolean handleFirstTimeSetup() {
                try {
                    config.setDefaultConfig();
                    config.getJsonConfig().addProperty("mode", "unlock");
                    config.getJsonConfig().add("programs", new JsonArray());
                    config.getJsonConfig().add("folders", new JsonArray());

                    logger.logInfo("Starting new setup.");
                    Platform.runLater(() -> splashController.close());

                    String newPassword = sgh.showSetPassPrompt();
                    if (newPassword == null || newPassword.isEmpty()) {
                        logger.logError("Password setup failed. Exiting application.");
                        sgh.showError("Setup Error", "Password setup is required to proceed.");
                        return false;
                    }

                    config.setPasswordHash(hashPassword(newPassword));
                    config.getJsonConfig().addProperty("password", config.getPasswordHash());

                    logger.logInfo("New password setup successfully.");
                    return true;
                } catch (Exception e) {
                    logger.logError("Error setting default config: " + e.getMessage(), e);
                    sgh.showError("Configuration Error", "Failed to set default configuration: " + e.getMessage());
                    return false;
                }
            }

            private boolean handleExistingConfiguration() {
                updateSplashMessage("Loading configuration...");
                config.loadConfig(false);


                Platform.runLater(() -> splashController.close());

                boolean loginSuccessful = true;//sgh.openLoginScene();
                if (!loginSuccessful) {
                    logger.logError("Login failed. Invalid password.");
                    config.getJsonConfig().remove("mode");
                    config.getJsonConfig().addProperty("mode", "lock");
                    ServiceController.startBlockerDaemon();
                } else {
                    logger.logInfo("Login successful.");
                }

                return loginSuccessful;
            }

            private void updateSplashMessage(String message) {
                updateProgress(-1, 100);
                updateMessage(message);
                Platform.runLater(() -> splashController.updateStatus(message));
                logger.logInfo(message);
            }
        };
    }

    /**
     * Handle successful initialization
     */
    private void handleInitializationSuccess(Stage stage, Task<Boolean> initTask) {
        try {
            if(initTask.getValue()) {
                setupMainUI(stage);
            }
            logger.logInfo("Main stage displayed successfully.");
        } catch (Exception e) {
            logger.logError("Error initializing main UI: " + e.getMessage(), e);
            splashController.close();
            sgh.showError("Initialization Error", "Failed to initialize application: " + e.getMessage());
        }
    }

    /**
     * Handle initialization failure
     */
    private void handleInitializationFailure(Task<Boolean> initTask) {
        logger.logError("Initialization failed: " + initTask.getException().getMessage(), initTask.getException());
        splashController.close();
        sgh.showError("Initialization Error", "Failed to initialize application: " + initTask.getException().getMessage());
    }

    /**
     * Setup the main UI
     */
    private void setupMainUI(Stage stage) throws IOException {
        mainStage = stage;
        mainLoader = new FXMLLoader(Main.class.getResource("home.fxml"));
        mainScene = new Scene(mainLoader.load());

        // Apply dark theme
        mainScene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/me/ghosthacks96/ghostsecure/css/dark-theme.css"))
                        .toExternalForm()
        );

        mainStage.setTitle("Ghost Secure - Home");
        mainStage.setScene(mainScene);
        mainStage.getIcons().add(
                new Image(Objects.requireNonNull(getClass().getResource("/me/ghosthacks96/ghostsecure/imgs/app_icon.png"))
                        .toExternalForm())
        );
        mainStage.initStyle(StageStyle.DECORATED);

        splashController.close();

        if(config.getJsonConfig().get("mode").getAsString().equals("lock")) {
            ServiceController.startBlockerDaemon();
            ServiceControllerScreen homeController = mainLoader.getController();
            homeController.updateServiceStatus();
        }
        mainStage.setOnCloseRequest(event -> {
            if(!ServiceController.isServiceRunning()){
                shutDownSystem();
            }
        });

        mainStage.show();
        mainStage.toFront();

        // Setup system tray integration
        SystemTrayIntegration sysTray = new SystemTrayIntegration(this);
        sysTray.setupSystemTray(mainStage);
    }

    public void shutDownSystem(){
        SystemTrayIntegration.removeTrayIcon();
        ServiceController.stopBlockerDaemon();
        config.saveConfig();
        if(DEBUG_MODE) DebugConsole.getInstance().killConsole();
        logger.onShutdown();
        Platform.exit();
    }

}