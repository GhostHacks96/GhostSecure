package me.ghosthacks96.applocker;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.ghosthacks96.applocker.utils.LockedItem;
import me.ghosthacks96.applocker.utils.PopUps;
import me.ghosthacks96.applocker.utils.ServiceController;
import org.kordamp.bootstrapfx.BootstrapFX;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class Main extends Application {

    private static final Gson gson = new Gson();
    public static ArrayList<LockedItem> lockedItems = new ArrayList<>();
    public static String PASSWORD_HASH;
    public static JsonObject config = null;

    public static FXMLLoader mainLoader;
    public static Scene mainScene;
    public static Stage mainStage;

    public static void main(String[] args) {
        loadConfig(); // Load config before launching the app

        launch();
    }

    public static void loadConfig() {
        try (InputStream inputStream = new FileInputStream(System.getenv("APPDATA") + "/ghosthacks96/applock/config.json")) {
            config = gson.fromJson(new InputStreamReader(inputStream), JsonObject.class);

            // Load the password hash
            if (config.has("password")) {
                PASSWORD_HASH = config.get("password").getAsString();
            }

            // Load locked items (programs/folders)
            loadLockedItemsFromConfig();
        } catch (FileNotFoundException e) {
            System.out.println("No config file found. A new setup will be required.");
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error reading config file: " + e.getMessage());
        }
    }

    private static void loadLockedItemsFromConfig() {
        // Load programs
        if (config.has("programs")) {
            JsonArray programs = config.getAsJsonArray("programs");
            for (int i = 0; i < programs.size(); i++) {
                String programEntry = programs.get(i).getAsString();
                lockedItems.add(parseLockedItem(programEntry));
            }
        }

        // Load folders
        if (config.has("folders")) {
            JsonArray folders = config.getAsJsonArray("folders");
            for (int i = 0; i < folders.size(); i++) {
                String folderEntry = folders.get(i).getAsString();
                lockedItems.add(parseLockedItem(folderEntry));
            }
        }
    }

    public static void saveConfig() {
        if (config == null) {
            config = new JsonObject();
        }

        // Update the password hash
        if (PASSWORD_HASH != null) {
            config.addProperty("password", PASSWORD_HASH);
        }

        // Update the programs in config
        JsonArray programsArray = new JsonArray();
        JsonArray foldersArray = new JsonArray();
        for (LockedItem item : lockedItems) {
            String entry = item.getPath() + "[::]" + (item.isLocked() ? "locked" : "unlocked");
            if (item.getPath().endsWith(".exe")) {
                programsArray.add(entry);
            } else {
                foldersArray.add(entry);
            }
        }
        config.add("programs", programsArray);
        config.add("folders", foldersArray);

        // Save the JSON to a file
        try {
            String appDataPath = System.getenv("APPDATA") + "/ghosthacks96/applock";
            File appDataDir = new File(appDataPath);
            if (!appDataDir.exists() && !appDataDir.mkdirs()) {
                throw new IOException("Failed to create AppData directory");
            }

            File configFile = new File(appDataDir, "config.json");
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(gson.toJson(config));
                System.out.println("Config saved successfully.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error saving config file: " + e.getMessage());
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
            throw new RuntimeException("Error hashing password: " + e.getMessage());
        }
    }

    private static LockedItem parseLockedItem(String input) {
        String[] parts = input.split("\\[::\\]");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid format for locked item: " + input);
        }
        String path = parts[0];
        String lockStatus = parts[1];
        String name = path.substring(path.lastIndexOf("\\") + 1);
        return new LockedItem(path, name, lockStatus.equalsIgnoreCase("locked"));
    }

    @Override
    public void start(Stage stage) {
        try {
            mainStage = stage;
            // Show the Home GUI initially
            mainLoader = new FXMLLoader(Main.class.getResource("home.fxml"));
            mainScene = new Scene(mainLoader.load(), 600, 500);
            mainScene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
            mainStage.setTitle("App Locker");
            mainStage.setScene(mainScene);


            // If the config is null (not set up), prompt for a new password
            if (config == null) {
                String newPassword = showSetPassPrompt(); // Prompt user to set the password
                if (newPassword == null || newPassword.isEmpty()) {
                    PopUps.showError("Setup Error", "Password setup is required to proceed.");
                    System.exit(1); // Exit if no password is set
                }
                PASSWORD_HASH = hashPassword(newPassword);
                lockedItems.add(new LockedItem("C:\\Windows\\WinSxS\\wow64_microsoft-windows-calc_31bf3856ad364e35_10.0.19041.1_none_6a03b910ee7a4073\\calc.exe", "calc.exe", true));
                saveConfig();
                homeGUI.refreshTableData();
                showHomeGUI();// Save the hash into the new config
            } else {
                // Prompt to log in
                boolean loginSuccessful = openLoginScene(stage);
                if (!loginSuccessful) {
                    PopUps.showError("Login Failed", "Invalid password. Closing application.");
                    System.exit(1); // Exit if login fails
                }else{
                    showHomeGUI();
                }
            }

            // Add shutdown hook to stop services
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Application shutting down. Stopping services...");
                ServiceController.stopService();
            }));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error loading FXML files: " + e.getMessage());
        }
    }

        public void showHomeGUI(){
            mainStage.show();
        }

    public static boolean openLoginScene(Stage parentStage) {
        try {
            // Load the Login GUI
            FXMLLoader loginLoader = new FXMLLoader(Main.class.getResource("login.fxml"));
            Stage loginStage = new Stage();
            Scene loginScene = new Scene(loginLoader.load(), 320, 240);
            loginStage.initModality(Modality.APPLICATION_MODAL);
            loginScene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());

            // Set up the login stage
            loginStage.setTitle("Login");
            loginStage.initOwner(parentStage);
            loginStage.setScene(loginScene);
            loginStage.showAndWait();

            // Check if the login was successful
            return PASSWORD_HASH != null && PASSWORD_HASH.equals(Login_GUI.enteredPasswordHash);
        } catch (IOException e) {
            e.printStackTrace();
            return false; // Login failed due to error
        }
    }
    private String showSetPassPrompt() {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("setPasswordGUI.fxml"));
            Stage stage = new Stage();
            Scene scene = new Scene(loader.load());
            stage.setTitle("Set Password");
            stage.initModality(Modality.APPLICATION_MODAL); // Makes the prompt modal
            stage.setScene(scene);

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
}