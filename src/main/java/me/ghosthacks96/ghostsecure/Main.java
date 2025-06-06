package me.ghosthacks96.ghostsecure;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import me.ghosthacks96.ghostsecure.utils.*;
import org.kordamp.bootstrapfx.BootstrapFX;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class Main extends Application {

    private static final Gson gson = new Gson();
    public static ArrayList<LockedItem> lockedItems = new ArrayList<>();
    public static String PASSWORD_HASH;
    public static JsonObject config;

    public static String appDataPath = System.getenv("APPDATA") + "/ghosthacks96/GhostSecure/";
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

    public static void loadConfig(boolean service) {
        try {
            config = gson.fromJson(new JsonReader(new FileReader(appDataPath + "config.json")), JsonObject.class);

            if (config.has("password")) {
                PASSWORD_HASH = config.get("password").getAsString();
            }

            if (!config.has("mode")) {
                config.addProperty("mode", "unlock");
            }
            loadLockedItemsFromConfig(service);

            if(!service)logger.logInfo("Config loaded successfully.");
        } catch (Exception e) {
            if(!service)logger.logWarning("No config file found. A new setup will be required.");
            e.printStackTrace();
        }
    }

    private static void loadLockedItemsFromConfig(boolean service) {
        try {
            if(!lockedItems.isEmpty()) lockedItems.clear();
            if (config.has("programs")) {
                JsonArray programs = config.getAsJsonArray("programs");
                for (int i = 0; i < programs.size(); i++) {
                    String programEntry = programs.get(i).getAsString();
                    lockedItems.add(parseLockedItem(programEntry));
                }
            }

            if (config.has("folders")) {
                JsonArray folders = config.getAsJsonArray("folders");
                for (int i = 0; i < folders.size(); i++) {
                    String folderEntry = folders.get(i).getAsString();
                    lockedItems.add(parseLockedItem(folderEntry));
                }
            }
            if(!service)logger.logInfo("Locked items loaded successfully from config.");
        } catch (Exception e) {
            logger.logError("Error loading locked items from config: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public static void printConfig() {
        if (config != null) {
            // Convert the JsonObject to a formatted JSON string
            String configJson = gson.toJson(config);

            // Print the JSON string
            System.out.println("Current Config Data:");
            System.out.println(configJson);

            // Optional: Log it using your logger
            logger.logInfo("Current Config Data: " + configJson);
        } else {
            System.out.println("Config object is null. No data to display.");
            logger.logWarning("Config object is null. No data to display.");
        }
    }

    private static boolean containsItem(JsonArray jsonArray, String path) {
        for (int i = 0; i < jsonArray.size(); i++) {
            String existingEntry = jsonArray.get(i).getAsString();
            // Compare only the raw path part (before `[::]`)
            if (existingEntry.startsWith(path + "[::]")) {
                return true;
            }
        }
        return false;
    }


    public static void saveConfig() {
        try {
            JsonArray programsArray = config.getAsJsonArray("programs");
            JsonArray foldersArray = config.getAsJsonArray("folders");

            // Ensure "programs" and "folders" arrays exist in the config
            if (programsArray == null) {
                programsArray = new JsonArray();
                config.add("programs", programsArray);
            }
            if (foldersArray == null) {
                foldersArray = new JsonArray();
                config.add("folders", foldersArray);
            }
            for (LockedItem item : lockedItems) {

                // Determine the formatted string for the item
                String formattedPath = item.getPath() + "[::]" + (item.isLocked() ? "locked" : "unlocked");

                if (item.getName().contains(".exe")) {
                    // If the item is a program, check if it's already in "programs" and add only if it's not
                    if (!containsItem(programsArray, item.getPath())) {
                        programsArray.add(formattedPath);
                    }
                } else {
                    // If the item is a folder, check if it's already in "folders" and add only if it's not
                    if (!containsItem(foldersArray, item.getPath())) {
                        foldersArray.add(formattedPath);
                    }
                }
            }

            File appDataDir = new File(appDataPath);
            if (!appDataDir.exists() && !appDataDir.mkdirs()) {
                throw new IOException("Failed to create AppData directory");
            }

            File configFile = new File(appDataDir, "config.json");
            printConfig();

            try (FileWriter writer = new FileWriter(configFile)) {

                writer.write(gson.toJson(config));
                logger.logInfo("Config saved successfully.");
            }
        } catch (Exception e) {
            logger.logError("Error saving config file: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static LockedItem parseLockedItem(String input) {
        try {
            String[] parts = input.split("\\[::\\]");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid format for locked item: " + input);
            }
            String path = parts[0];
            String lockStatus = parts[1];
            String name = path.substring(path.lastIndexOf("\\") + 1);
            return new LockedItem(path, name, lockStatus.equalsIgnoreCase("locked"));
        } catch (IllegalArgumentException e) {
            logger.logError("Error parsing locked item: " + input + " - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
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
            File cFile = new File(appDataPath + "config.json");
            if (!cFile.exists()) {
                cFile.createNewFile();
                config = new JsonObject();

                config.addProperty("mode", "unlock");
                config.add("programs", new JsonArray());
                config.add("folders", new JsonArray());
                logger.logInfo("Starting new setup.");
                String newPassword = showSetPassPrompt();
                if (newPassword == null || newPassword.isEmpty()) {
                    logger.logError("Password setup failed. Exiting application.");
                    PopUps.showError("Setup Error", "Password setup is required to proceed.");
                    System.exit(1);
                }
                PASSWORD_HASH = hashPassword(newPassword);
                config.addProperty("password",PASSWORD_HASH);
                saveConfig();
                logger.logInfo("New password setup successfully.");
            } else {
                loadConfig(false);
                int tries = 3;
                while(true){
                    boolean loginSuccessful = openLoginScene();
                    if (!loginSuccessful) {
                        logger.logError("Login failed. Invalid password.");
                        PopUps.showError("Login Failed", "Invalid password. Tries left: " + tries);
                        if (tries == 0) {
                            System.exit(1);
                        }
                        tries--;
                    }else{
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


            if(!sc.isServiceRunning()){
                mainStage.show();
                logger.logInfo("Main stage displayed successfully.");
            }else{
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