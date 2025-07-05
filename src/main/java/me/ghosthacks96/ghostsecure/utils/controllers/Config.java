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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Configuration manager for GhostSecure application.
 * Handles encrypted configuration storage, user authentication, and locked items management.
 */
public class Config {

    // Constants
    private static final String SALT_FILE = "key.salt";
    private static final String CONFIG_FILE = "config.json.enc";
    private static final String ITEM_SEPARATOR = "[::]";
    private static final String LOCKED_STATUS = "locked";
    private static final String UNLOCKED_STATUS = "unlocked";
    private static final String DEFAULT_MODE = "unlock";

    // Static fields
    public static final Gson gson = new Gson();
    private static Logging logger;
    public static JsonObject config;
    public static String passwordHash = "";
    private static final List<LockedItem> lockedItems = new ArrayList<>();
    private static SecretKey encryptionKey;

    // Constructor
    public Config() {
        logger = Main.logger;
        logger.logDebug("Config constructor called");
        initializeEncryption();
    }

    // Initialization methods

    /**
     * Initialize encryption key based on user information and system salt.
     */
    private static void initializeEncryption() {
        logger.logDebug("Initializing encryption...");

        try {
            String username = System.getProperty("user.name");
            String systemInfo = EncryptionUtils.getSystemInfo();
            logger.logDebug("Username: " + username + ", SystemInfo: " + systemInfo);

            byte[] keySalt = loadOrCreateSalt();
            encryptionKey = EncryptionUtils.deriveKeyFromUserInfo(username, systemInfo, keySalt);

            logger.logInfo("Encryption initialized successfully.");
        } catch (Exception e) {
            logger.logError("Failed to initialize encryption", e);
            throw new RuntimeException("Encryption initialization failed", e);
        }
    }

    /**
     * Load existing salt or create a new one if it doesn't exist.
     */
    private static byte[] loadOrCreateSalt() throws IOException {
        Path saltPath = Paths.get(Main.APP_DATA_PATH, SALT_FILE);

        if (Files.exists(saltPath)) {
            logger.logDebug("Loading existing salt file");
            return Files.readAllBytes(saltPath);
        } else {
            logger.logDebug("Creating new salt file");
            byte[] newSalt = EncryptionUtils.generateSalt();

            ensureAppDataDirectoryExists();
            Files.write(saltPath, newSalt);

            logger.logInfo("New encryption salt created.");
            return newSalt;
        }
    }

    /**
     * Ensure the application data directory exists.
     */
    private static void ensureAppDataDirectoryExists() throws IOException {
        Path APP_DATA_PATH = Paths.get(Main.APP_DATA_PATH);
        if (!Files.exists(APP_DATA_PATH)) {
            Files.createDirectories(APP_DATA_PATH);
            logger.logDebug("AppData directory created");
        }
    }

    // Configuration loading methods

    /**
     * Load configuration from encrypted file.
     * @param isService true if called from service context (suppresses user warnings)
     */
    public static void loadConfig(boolean isService) {
        logger.logDebug("Loading config (service mode: " + isService + ")");

        try {
            Path configPath = Paths.get(Main.APP_DATA_PATH, CONFIG_FILE);

            if (!Files.exists(configPath)) {
                handleMissingConfigFile(isService);
                return;
            }

            String encryptedContent = Files.readString(configPath);
            String decryptedContent = EncryptionUtils.decrypt(encryptedContent, encryptionKey);

            config = gson.fromJson(decryptedContent, JsonObject.class);

            loadPasswordHash();
            ensureModeProperty();
            loadLockedItemsFromConfig(isService);

            if (!isService) {
                logger.logInfo("Encrypted config loaded successfully.");
            }

        } catch (Exception e) {
            handleConfigLoadError(isService, e);
        }
    }

    /**
     * Handle case when config file doesn't exist.
     */
    private static void handleMissingConfigFile(boolean isService) {
        if (!isService) {
            logger.logWarning("No config file found. A new setup will be required.");
        }
        logger.logDebug("Config file does not exist");
    }

    /**
     * Handle errors during config loading.
     */
    private static void handleConfigLoadError(boolean isService, Exception e) {
        if (!isService) {
            logger.logWarning("Failed to load config file: " + e.getMessage());
            logger.logWarning("A new setup will be required.");
        }
        logger.logDebug("Config load exception", e);
    }

    /**
     * Load password hash from config if present.
     */
    private static void loadPasswordHash() {
        if (config.has("password")) {
            passwordHash = config.get("password").getAsString();
            logger.logDebug("Password hash loaded");
        }
    }

    /**
     * Ensure mode property exists in config.
     */
    private static void ensureModeProperty() {
        if (!config.has("mode")) {
            config.addProperty("mode", DEFAULT_MODE);
            logger.logDebug("Mode property added to config");
        }
    }

    /**
     * Load locked items from configuration.
     */
    private static void loadLockedItemsFromConfig(boolean isService) {
        logger.logDebug("Loading locked items from config");

        try {
            lockedItems.clear();

            loadItemsFromJsonArray("programs");
            loadItemsFromJsonArray("folders");

            if (!isService) {
                logger.logInfo("Locked items loaded successfully from encrypted config.");
            }

        } catch (Exception e) {
            logger.logError("Error loading locked items from config", e);
        }
    }

    /**
     * Load items from a specific JSON array in the config.
     */
    private static void loadItemsFromJsonArray(String arrayName) {
        if (!config.has(arrayName)) {
            return;
        }

        JsonArray itemsArray = config.getAsJsonArray(arrayName);
        for (int i = 0; i < itemsArray.size(); i++) {
            String itemEntry = itemsArray.get(i).getAsString();
            Optional<LockedItem> item = parseLockedItem(itemEntry);
            item.ifPresent(lockedItems::add);
        }

        logger.logDebug("Loaded " + arrayName + " from config");
    }

    // Configuration saving methods

    /**
     * Save current configuration to encrypted file.
     */
    public static void saveConfig() {
        logger.logDebug("Saving config...");

        try {
            ensureConfigArraysExist();
            populateConfigArrays();
            writeEncryptedConfig();

            logger.logInfo("Encrypted config saved successfully.");

        } catch (Exception e) {
            logger.logError("Error saving encrypted config file", e);
        }
    }

    /**
     * Ensure programs and folders arrays exist in config.
     */
    private static void ensureConfigArraysExist() {
        if (!config.has("programs")) {
            config.add("programs", new JsonArray());
        }
        if (!config.has("folders")) {
            config.add("folders", new JsonArray());
        }
    }

    /**
     * Populate config arrays with current locked items.
     */
    private static void populateConfigArrays() {
        JsonArray programsArray = new JsonArray();
        JsonArray foldersArray = new JsonArray();

        for (LockedItem item : lockedItems) {
            String formattedPath = formatItemForStorage(item);

            if (isExecutableFile(item.getName())) {
                programsArray.add(formattedPath);
            } else {
                foldersArray.add(formattedPath);
            }
        }

        config.add("programs", programsArray);
        config.add("folders", foldersArray);
    }

    /**
     * Format a locked item for storage in config.
     */
    private static String formatItemForStorage(LockedItem item) {
        String status = item.isLocked() ? LOCKED_STATUS : UNLOCKED_STATUS;
        return item.getPath() + ITEM_SEPARATOR + status;
    }

    /**
     * Check if a file name represents an executable.
     */
    private static boolean isExecutableFile(String fileName) {
        return fileName.toLowerCase().endsWith(".exe");
    }

    /**
     * Write encrypted configuration to file.
     */
    private static void writeEncryptedConfig() throws IOException {
        ensureAppDataDirectoryExists();

        Path configPath = Paths.get(Main.APP_DATA_PATH, CONFIG_FILE);
        String jsonString = gson.toJson(config);
        String encryptedContent = EncryptionUtils.encrypt(jsonString, encryptionKey);

        assert encryptedContent != null;
        Files.writeString(configPath, encryptedContent);
    }

    // Utility methods

    /**
     * Parse a locked item from its string representation.
     */
    private static Optional<LockedItem> parseLockedItem(String input) {
        logger.logDebug("Parsing locked item: " + input);

        try {
            String[] parts = input.split("\\Q" + ITEM_SEPARATOR + "\\E");

            if (parts.length != 2) {
                logger.logError("Invalid format for locked item: " + input);
                return Optional.empty();
            }

            String path = parts[0];
            String lockStatus = parts[1];
            String name = extractFileName(path);
            boolean isLocked = LOCKED_STATUS.equalsIgnoreCase(lockStatus);

            return Optional.of(new LockedItem(path, name, isLocked));

        } catch (Exception e) {
            logger.logError("Error parsing locked item: " + input, e);
            return Optional.empty();
        }
    }

    /**
     * Extract file name from path.
     */
    private static String extractFileName(String path) {
        int lastSeparatorIndex = path.lastIndexOf(File.separator);
        return lastSeparatorIndex >= 0 ? path.substring(lastSeparatorIndex + 1) : path;
    }

    /**
     * Set default configuration values.
     */
    public void setDefaultConfig() {
        logger.logDebug("Setting default config...");

        try {
            config = new JsonObject();
            config.addProperty("mode", DEFAULT_MODE);
            config.add("programs", new JsonArray());
            config.add("folders", new JsonArray());
            config.addProperty("password", "");

            lockedItems.clear();
            saveConfig();

            logger.logInfo("Default config created successfully.");

        } catch (Exception e) {
            logger.logError("Error saving default config", e);
            throw new RuntimeException("Failed to create default config", e);
        }
    }

    // Legacy methods for backward compatibility

    public JsonObject getJsonConfig() {
        return config;
    }

    public File getConfigFile() {
        return new File(Main.APP_DATA_PATH + CONFIG_FILE);
    }
}