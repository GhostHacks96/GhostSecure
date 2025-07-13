package me.ghosthacks96.ghostsecure;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import me.ghosthacks96.ghostsecure.gui.HomeGUI;
import me.ghosthacks96.ghostsecure.gui.SubGUIHandler;
import me.ghosthacks96.ghostsecure.gui.extras.SplashGUI;
import me.ghosthacks96.ghostsecure.utils.api_handlers.RecoveryHandler;
import me.ghosthacks96.ghostsecure.utils.api_handlers.Update;
import me.ghosthacks96.ghostsecure.utils.debug.DebugConsole;
import me.ghosthacks96.ghostsecure.utils.file_handlers.Config;
import me.ghosthacks96.ghostsecure.utils.file_handlers.Logging;
import me.ghosthacks96.ghostsecure.utils.file_handlers.MigrationUtil;
import me.ghosthacks96.ghostsecure.utils.file_handlers.StorageManager;
import me.ghosthacks96.ghostsecure.utils.services.ServiceController;
import me.ghosthacks96.ghostsecure.utils.services.SystemTrayIntegration;

import java.io.File;
import java.io.IOException;
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

    public static StorageManager programStorage;
    public static StorageManager folderStorage;
    public static StorageManager accountStorage;
    public static StorageManager systemConfigStorage;

    // UI components
    public static FXMLLoader mainLoader;
    public static Scene mainScene;
    public static Stage mainStage;

    // Application state
    public static boolean use2FA = true; // Enable two-factor authentication by default
    public static boolean DEBUG_MODE = false;
    private static SplashGUI splashController;

    private static boolean MIGRATION_PERFORMED = false; // Flag to track if migration has been performed

    public static void main(String[] args) {
        initializeLogging();
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
     * Toggle debug mode on/off
     */
    public static void shiftDebug(boolean debug) {
        DEBUG_MODE = debug;
        systemConfigStorage.put("debug_mode", debug);
    }


    @Override
    public void start(Stage stage) {
        try {
            // Set up global uncaught exception handler
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                logger.logError("Uncaught exception in thread: " + thread.getName(), throwable);
                shutDownSystem();
            });

            // Prevent application from exiting when all windows are closed
            Platform.setImplicitExit(false);

            // Add shutdown hook
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

                        programStorage = new StorageManager("programs");
                        folderStorage = new StorageManager("folders");
                        accountStorage = new StorageManager("account");
                        systemConfigStorage = new StorageManager("system-config", false); // Unencrypted storage for system settings

                        // Load data from config and migrate to storage managers
                        if(MigrationUtil.checkAndMigrateData()){
                            MIGRATION_PERFORMED = true;
                        }

                        // Load data from storage managers
                        programStorage.loadData();
                        folderStorage.loadData();
                        accountStorage.loadData();
                        systemConfigStorage.loadData();

                        shiftDebug(systemConfigStorage.get("debug_mode", false));
                        use2FA = systemConfigStorage.get("enable_2fa", false);

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
                        recoveryHandler = new RecoveryHandler();
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
                        // Check if accountStorage has password_hash, which indicates it's not a first-time setup
                        if (!accountStorage.containsKey("password_hash") || MIGRATION_PERFORMED) {
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
                    logger.logInfo("Starting new setup.");
                    Platform.runLater(() -> splashController.close());

                    String[] setupInfo = sgh.showSetPassPrompt();
                    if (setupInfo == null || setupInfo[0] == null || setupInfo[0].isEmpty()) {
                        logger.logError("Password setup failed. Exiting application.");
                        sgh.showError("Setup Error", "Password setup is required to proceed.");
                        return false;
                    }
                    logger.logInfo("email"+ setupInfo[1]+" newPassword: " + setupInfo[0]);

                    String newPassword = setupInfo[0];
                    String email = setupInfo[1];

                    // Store password hash and email
                    String passwordHash = hashPassword(newPassword);
                    accountStorage.put("password_hash", passwordHash);
                    accountStorage.put("email", email);
                    accountStorage.put("mode", "unlock");

                    logger.logInfo("New password and email setup successfully.");
                    return true;
                } catch (Exception e) {
                    logger.logError("Error setting default config: " + e.getMessage(), e);
                    sgh.showError("Configuration Error", "Failed to set default configuration: " + e.getMessage());
                    return false;
                }
            }

            private boolean handleExistingConfiguration() {
                updateSplashMessage("Loading configuration...");
                // No need to load config here as we're using StorageManager now

                Platform.runLater(() -> splashController.close());

                boolean loginSuccessful = false;
                boolean twoFactorSuccessful = false;

                // Check if auto-start is enabled
                boolean autoStartEnabled = false;

                // First check if auto-start exists in systemConfigStorage (after migration)
                if (systemConfigStorage.containsKey("auto_start")) {
                    autoStartEnabled = systemConfigStorage.get("auto_start", false);
                    logger.logDebug("Using auto-start setting from system-config: " + autoStartEnabled);
                }
                if (autoStartEnabled) {
                    // Bypass login GUI if auto-start is enabled
                    logger.logInfo("Auto-start enabled. Bypassing login GUI.");
                    loginSuccessful = false;
                    twoFactorSuccessful = true; // Also bypass 2FA
                } else {
                    // Show login GUI if auto-start is not enabled
                    loginSuccessful = sgh.openLoginScene();

                    // If password is correct, proceed with 2FA
                    if (loginSuccessful) {
                        logger.logInfo("Password verification successful. Proceeding with two-factor authentication.");
                        twoFactorSuccessful = handleTwoFactorAuthentication();
                    }
                }

                // Final authentication result
                boolean authSuccessful = loginSuccessful && twoFactorSuccessful;

                if (!authSuccessful) {
                    if (!loginSuccessful) {
                        logger.logError("Login failed. Invalid password.");
                    } else {
                        logger.logError("Two-factor authentication failed.");
                    }
                    accountStorage.put("mode", "lock");
                    ServiceController.startBlockerDaemon();
                } else {
                    logger.logInfo("Authentication successful.");
                }

                return authSuccessful;
            }

            /**
             * Handle the two-factor authentication process
             * @return true if 2FA was successful, false otherwise
             */
            private boolean handleTwoFactorAuthentication() {
                try {
                    // Get the user's email from storage
                    String userEmail = accountStorage.get("email", "");
                    if (userEmail.isEmpty()) {
                        logger.logError("No email address found for two-factor authentication.");
                        sgh.showError("Authentication Error", "No email address found for two-factor authentication. Please contact support.");
                        return false;
                    }

                    if(!use2FA) {
                        return true;
                    }

                    // Send verification code
                    String verificationCode = me.ghosthacks96.ghostsecure.utils.auth.TwoFactorAuthUtil.sendVerificationCode(userEmail);
                    if (verificationCode == null) {
                        logger.logError("Failed to send verification code.");
                        sgh.showError("Authentication Error", "Failed to send verification code. Please check your email settings and try again.");
                        return false;
                    }

                    // Create a resend action
                    Runnable resendAction = () -> {
                        String newCode = me.ghosthacks96.ghostsecure.utils.auth.TwoFactorAuthUtil.sendVerificationCode(userEmail);
                        if (newCode != null) {
                            // Update the expected code in the controller
                            // This is handled by the TwoFactorAuthGUI class
                        }
                    };

                    // Show 2FA prompt
                    return sgh.showTwoFactorAuthPrompt(verificationCode, userEmail, resendAction);
                } catch (Exception e) {
                    logger.logError("Error during two-factor authentication: " + e.getMessage(), e);
                    sgh.showError("Authentication Error", "An error occurred during two-factor authentication: " + e.getMessage());
                    return false;
                }
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
            if (initTask.getValue()) {
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
        mainLoader = new FXMLLoader(Main.class.getResource("/me/ghosthacks96/ghostsecure/home.fxml"));
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

        String mode = accountStorage.get("mode", "unlock");
        if (mode.equals("lock")) {
            ServiceController.startBlockerDaemon();
            HomeGUI homeController = mainLoader.getController();
            homeController.updateLockStatus();
        }
        mainStage.setOnCloseRequest(event -> {
                shutDownSystem();
        });

        // Check if auto-start is enabled
        boolean autoStartEnabled = false;
        if (systemConfigStorage.containsKey("auto_start")) {
            autoStartEnabled = systemConfigStorage.get("auto_start", false);
        }

        // Setup system tray integration first
        SystemTrayIntegration sysTray = new SystemTrayIntegration(this);
        sysTray.setupSystemTray(mainStage);

        // Only show the main window if auto-start is not enabled
        if (!autoStartEnabled) {
            mainStage.show();
            mainStage.toFront();
        } else {
            logger.logInfo("Auto-start enabled. Starting minimized to system tray.");
        }
    }

    public void shutDownSystem() {
        SystemTrayIntegration.removeTrayIcon();
        ServiceController.stopBlockerDaemon();

        // Save data to storage managers
        if (programStorage != null) {
            programStorage.saveData();
        }
        if (folderStorage != null) {
            folderStorage.saveData();
        }
        if (accountStorage != null) {
            accountStorage.saveData();
        }
        if (systemConfigStorage != null) {
            systemConfigStorage.saveData();
        }

        if (DEBUG_MODE) DebugConsole.getInstance().killConsole();
        logger.onShutdown();
        Platform.exit();
    }


}
