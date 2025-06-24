package me.ghosthacks96.ghostsecure.utils.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;
import me.ghosthacks96.ghostsecure.utils.EncryptionUtils;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Config {

    private static final String SALT_FILE = "key.salt";
    private static final String CONFIG_FILE = "config.json.enc";
    public static JsonObject config;
    public static String PASSWORD_HASH = "";
    public static Gson gson = new Gson();
    public static Logging logger;
    public static List<LockedItem> lockedItems = new ArrayList<>();
    // Encryption components
    private static SecretKey encryptionKey;
    private static byte[] keySalt;

    public Config() {
        logger = Main.logger;
        logger.logDebug("Config constructor called");
        initializeEncryption();
    }

    /**
     * Initialize encryption key based on user information
     */
    private static void initializeEncryption() {
        logger.logDebug("initializeEncryption() called");
        try {
            String username = System.getProperty("user.name");
            String systemInfo = EncryptionUtils.getSystemInfo();
            logger.logDebug("Username: " + username + ", SystemInfo: " + systemInfo);
            File saltFile = new File(Main.appDataPath + SALT_FILE);
            // Load or create salt
            if (saltFile.exists()) {
                keySalt = Files.readAllBytes(saltFile.toPath());
                logger.logDebug("Salt file exists, loaded salt");
            } else {
                keySalt = EncryptionUtils.generateSalt();
                File appDataDir = new File(Main.appDataPath);
                if (!appDataDir.exists()) {
                    appDataDir.mkdirs();
                    logger.logDebug("AppData directory created");
                }
                Files.write(saltFile.toPath(), keySalt);
                logger.logInfo("New encryption salt created.");
            }
            encryptionKey = EncryptionUtils.deriveKeyFromUserInfo(username, systemInfo, keySalt);
            logger.logInfo("Encryption initialized successfully.");
        } catch (Exception e) {
            logger.logError("Failed to initialize encryption: " + e.getMessage());
            logger.logDebug("Exception: " + e);
        }
    }

    public static void loadConfig(boolean service) {
        logger.logDebug("loadConfig(" + service + ") called");
        try {
            File configFile = new File(Main.appDataPath + CONFIG_FILE);
            if (!configFile.exists()) {
                if (!service) logger.logWarning("No config file found. A new setup will be required.");
                logger.logDebug("Config file does not exist");
                return;
            }
            String encryptedContent = Files.readString(configFile.toPath());
            logger.logDebug("Encrypted config read from file");
            String decryptedContent = EncryptionUtils.decrypt(encryptedContent, encryptionKey);
            logger.logDebug("Config decrypted");
            config = gson.fromJson(decryptedContent, JsonObject.class);
            logger.logDebug("Config parsed from JSON");
            if (config.has("password")) {
                PASSWORD_HASH = config.get("password").getAsString();
                logger.logDebug("Password hash loaded");
            }
            if (!config.has("mode")) {
                config.addProperty("mode", "unlock");
                logger.logDebug("Mode property added to config");
            }
            loadLockedItemsFromConfig(service);
            if (!service) logger.logInfo("Encrypted config loaded successfully.");
        } catch (Exception e) {
            if (!service) {
                logger.logWarning("Failed to load config file: " + e.getMessage());
                logger.logWarning("A new setup will be required.");
            }
            logger.logDebug("Exception: " + e.getMessage(),e);
        }
    }

    private static void loadLockedItemsFromConfig(boolean service) {
        logger.logDebug("loadLockedItemsFromConfig(" + service + ") called");
        try {
            if (!lockedItems.isEmpty()) lockedItems.clear();
            if (config.has("programs")) {
                JsonArray programs = config.getAsJsonArray("programs");
                for (int i = 0; i < programs.size(); i++) {
                    String programEntry = programs.get(i).getAsString();
                    lockedItems.add(parseLockedItem(programEntry));
                }
                logger.logDebug("Loaded programs from config");
            }
            if (config.has("folders")) {
                JsonArray folders = config.getAsJsonArray("folders");
                for (int i = 0; i < folders.size(); i++) {
                    String folderEntry = folders.get(i).getAsString();
                    lockedItems.add(parseLockedItem(folderEntry));
                }
                logger.logDebug("Loaded folders from config");
            }
            if (!service) logger.logInfo("Locked items loaded successfully from encrypted config.");
        } catch (Exception e) {
            logger.logError("Error loading locked items from config: " + e.getMessage());
            logger.logDebug("Exception: "+e.getLocalizedMessage(), e);
        }
    }

    public static void printConfig() {
        logger.logDebug("printConfig() called");
        if (config != null) {
            String configJson = gson.toJson(config);
            System.out.println("Current Config Data:");
            System.out.println(configJson);
            logger.logInfo("Current Config Data: " + configJson);
        } else {
            System.out.println("Config object is null. No data to display.");
            logger.logWarning("Config object is null. No data to display.");
        }
    }

    private static boolean containsItem(JsonArray jsonArray, String path) {
        for (int i = 0; i < jsonArray.size(); i++) {
            String existingEntry = jsonArray.get(i).getAsString();
            if (existingEntry.startsWith(path + "[::]")) {
                return true;
            }
        }
        return false;
    }

    public static void saveConfig() {
        logger.logDebug("saveConfig() called");
        try {
            JsonArray programsArray = config.getAsJsonArray("programs");
            JsonArray foldersArray = config.getAsJsonArray("folders");
            if (programsArray == null) {
                programsArray = new JsonArray();
                config.add("programs", programsArray);
            }
            if (foldersArray == null) {
                foldersArray = new JsonArray();
                config.add("folders", foldersArray);
            }
            programsArray = new JsonArray();
            foldersArray = new JsonArray();
            config.add("programs", programsArray);
            config.add("folders", foldersArray);
            for (LockedItem item : lockedItems) {
                String formattedPath = item.getPath() + "[::]" + (item.isLocked() ? "locked" : "unlocked");
                if (item.getName().contains(".exe")) {
                    programsArray.add(formattedPath);
                } else {
                    foldersArray.add(formattedPath);
                }
            }
            File appDataDir = new File(Main.appDataPath);
            if (!appDataDir.exists() && !appDataDir.mkdirs()) {
                logger.logError("Failed to create AppData directory");
                return;
            }
            File configFile = new File(appDataDir, CONFIG_FILE);
            String jsonString = gson.toJson(config);
            String encryptedContent = EncryptionUtils.encrypt(jsonString, encryptionKey);
            Files.writeString(configFile.toPath(), encryptedContent);
            logger.logInfo("Encrypted config saved successfully.");
        } catch (Exception e) {
            logger.logError("Error saving encrypted config file: " + e.getMessage());
            logger.logDebug("Exception: " + e);
            e.printStackTrace();
            return;
        }
    }

    private static LockedItem parseLockedItem(String input) {
        logger.logDebug("parseLockedItem() called for: " + input);
        try {
            String[] parts = input.split("\\[::]");
            if (parts.length != 2) {
                logger.logError("Invalid format for locked item: " + input);
                return null;
            }
            String path = parts[0];
            String lockStatus = parts[1];
            String name = path.substring(path.lastIndexOf("\\") + 1);
            return new LockedItem(path, name, lockStatus.equalsIgnoreCase("locked"));
        } catch (IllegalArgumentException e) {
            logger.logError("Error parsing locked item: " + input + " - " + e.getMessage());
            logger.logDebug("Exception: " + e);
            e.printStackTrace();
            return null;
        }
    }

    public JsonObject getJsonConfig() {
        return config;
    }

    public File getConfigFile() {
        return new File(Main.appDataPath + CONFIG_FILE);
    }

    public void setDefaultConfig() throws Exception {
        logger.logDebug("setDefaultConfig() called");
        config = new JsonObject();
        config.addProperty("mode", "unlock");
        config.add("programs", new JsonArray());
        config.add("folders", new JsonArray());
        config.addProperty("password", "");
        lockedItems.clear();
        try {
            saveConfig();
        } catch (Exception e) {
            logger.logError("Error saving default config: " + e.getMessage());
            logger.logDebug("Exception: " + e);
            e.printStackTrace();
            return;
        }
        logger.logInfo("Default config created successfully.");
        File appDataDir = new File(Main.appDataPath);
        if (!appDataDir.exists()) {
            if (!appDataDir.mkdirs()) {
                logger.logError("Failed to create AppData directory");
                return;
            }
            logger.logInfo("AppData directory created successfully.");
        }
    }
}

