package me.ghosthacks96.ghostsecure.utils.file_handlers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;
import me.ghosthacks96.ghostsecure.utils.encryption.EncryptionUtils;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItemFactory;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Deprecated
public class Config {

    // Constants
    private static final String SALT_FILE = "key.salt";
    private static final String CONFIG_FILE = "config.json.enc";
    private static final String ITEM_SEPARATOR = "[::]";
    private static final String LOCKED_STATUS = "locked";
    private static final String UNLOCKED_STATUS = "unlocked";
    private static final String DEFAULT_MODE = "unlock";
    private static final String PROGRAMS_ARRAY = "programs";
    private static final String FOLDERS_ARRAY = "folders";
    private static final String PASSWORD_PROPERTY = "password";
    private static final String MODE_PROPERTY = "mode";
    private static final String AUTO_START_PROPERTY = "auto_start";

    // Instance fields
    private final Gson gson;
    private final Logging logger;
    private final ReadWriteLock configLock;
    public final List<LockedItem> lockedItems;

    private JsonObject config;
    private String passwordHash;
    private SecretKey encryptionKey;
    private boolean initialized;

    /**
     * Creates a new Config instance and initializes encryption.
     *
     * @throws ConfigurationException if initialization fails
     */
    public Config() {
        this.gson = new Gson();
        this.logger = Main.logger;
        this.configLock = new ReentrantReadWriteLock();
        this.lockedItems = new ArrayList<>();
        this.passwordHash = "";
        this.initialized = false;

        logger.logDebug("Config constructor called");
        initializeEncryption();
    }

    /**
     * Initialize encryption key based on user information and system salt.
     *
     * @throws ConfigurationException if encryption initialization fails
     */
    private void initializeEncryption() {
        logger.logDebug("Initializing encryption...");

        try {
            String username = System.getProperty("user.name");
            String systemInfo = EncryptionUtils.getSystemInfo();
            logger.logDebug("Username: " + username + ", SystemInfo: " + systemInfo);

            byte[] keySalt = loadOrCreateSalt();
            encryptionKey = EncryptionUtils.deriveKeyFromUserInfo(username, systemInfo, keySalt);

            initialized = true;
            logger.logInfo("Encryption initialized successfully.");
        } catch (Exception e) {
            logger.logError("Failed to initialize encryption", e);
            throw new ConfigurationException("Encryption initialization failed", e);
        }
    }

    /**
     * Load existing salt or create a new one if it doesn't exist.
     *
     * @return the salt bytes
     * @throws IOException if salt operations fail
     */
    private byte[] loadOrCreateSalt() throws IOException {
        Path saltPath = Paths.get(Main.APP_DATA_PATH, SALT_FILE);

        if (Files.exists(saltPath)) {
            logger.logDebug("Loading existing salt file");
            return Files.readAllBytes(saltPath);
        } else {
            logger.logDebug("Creating new salt file");
            byte[] newSalt = EncryptionUtils.generateSalt();

            ensureAppDataDirectoryExists();
            //Files.write(saltPath, newSalt);

            logger.logInfo("New encryption salt created.");
            return newSalt;
        }
    }

    /**
     * Ensure the application data directory exists.
     *
     * @throws IOException if directory creation fails
     */
    private void ensureAppDataDirectoryExists() throws IOException {
        Path appDataPath = Paths.get(Main.APP_DATA_PATH);
        if (!Files.exists(appDataPath)) {
            Files.createDirectories(appDataPath);
            logger.logDebug("AppData directory created");
        }
    }

    /**
     * Load configuration from encrypted file.
     *
     * @param isService true if called from service context (suppresses user warnings)
     * @throws ConfigurationException if config loading fails critically
     */
    public void loadConfig(boolean isService) {
        if (!initialized) {
            throw new ConfigurationException("Config not initialized");
        }

        configLock.writeLock().lock();
        try {
            logger.logDebug("Loading config (service mode: " + isService + ")");

            Path configPath = Paths.get(Main.APP_DATA_PATH, CONFIG_FILE);

            if (!Files.exists(configPath)) {
                handleMissingConfigFile(isService);
                return;
            }

            loadConfigFromFile(configPath, isService);

        } finally {
            configLock.writeLock().unlock();
        }
    }

    /**
     * Load configuration from the specified file path.
     */
    private void loadConfigFromFile(Path configPath, boolean isService) {
        try {
            String encryptedContent = Files.readString(configPath);
            String decryptedContent = EncryptionUtils.decrypt(encryptedContent, encryptionKey);

            config = gson.fromJson(decryptedContent, JsonObject.class);

            loadPasswordHash();
            ensureModeProperty();
            loadLockedItemsFromConfig(isService);

            if (!isService) {
                logger.logInfo("Encrypted config loaded successfully.");
            }

        } catch (IOException e) {
            handleConfigLoadError(isService, "Failed to read config file", e);
        } catch (JsonSyntaxException e) {
            handleConfigLoadError(isService, "Invalid JSON in config file", e);
        } catch (Exception e) {
            handleConfigLoadError(isService, "Unexpected error loading config", e);
        }
    }

    /**
     * Handle case when config file doesn't exist.
     */
    private void handleMissingConfigFile(boolean isService) {
        if (!isService) {
            logger.logWarning("No config file found. A new setup will be required.");
        }
        logger.logDebug("Config file does not exist");

        // Initialize with default empty config
        config = new JsonObject();
        ensureModeProperty();
        lockedItems.clear();
        passwordHash = "";
    }

    /**
     * Handle errors during config loading.
     */
    private void handleConfigLoadError(boolean isService, String message, Exception e) {
        if (!isService) {
            logger.logWarning("Failed to load config file: " + message);
            logger.logWarning("A new setup will be required.");
        }
        logger.logDebug("Config load exception: " + message, e);

        // Initialize with default empty config on error
        config = new JsonObject();
        ensureModeProperty();
        lockedItems.clear();
        passwordHash = "";
    }

    /**
     * Load password hash from config if present.
     */
    private void loadPasswordHash() {
        if (config.has(PASSWORD_PROPERTY)) {
            passwordHash = config.get(PASSWORD_PROPERTY).getAsString();
            logger.logDebug("Password hash loaded");
        }
    }

    /**
     * Ensure mode property exists in config.
     */
    private void ensureModeProperty() {
        if (!config.has(MODE_PROPERTY)) {
            config.addProperty(MODE_PROPERTY, DEFAULT_MODE);
            logger.logDebug("Mode property added to config");
        }

        if (!config.has(AUTO_START_PROPERTY)) {
            config.addProperty(AUTO_START_PROPERTY, false);
            logger.logDebug("Auto-start property added to config");
        }
    }

    /**
     * Load locked items from configuration.
     */
    private void loadLockedItemsFromConfig(boolean isService) {
        logger.logDebug("Loading locked items from config");

        try {
            lockedItems.clear();

            loadItemsFromJsonArray(PROGRAMS_ARRAY);
            loadItemsFromJsonArray(FOLDERS_ARRAY);

            if (!isService) {
                logger.logInfo("Locked items loaded successfully from encrypted config.");
            }

        } catch (Exception e) {
            logger.logError("Error loading locked items from config", e);
            lockedItems.clear(); // Clear on error to prevent inconsistent state
        }
    }

    /**
     * Load items from a specific JSON array in the config.
     */
    private void loadItemsFromJsonArray(String arrayName) {
        if (!config.has(arrayName)) {
            return;
        }

        try {
            JsonArray itemsArray = config.getAsJsonArray(arrayName);
            for (int i = 0; i < itemsArray.size(); i++) {
                String itemEntry = itemsArray.get(i).getAsString();
                Optional<LockedItem> item = parseLockedItem(itemEntry);
                item.ifPresent(lockedItems::add);
            }

            logger.logDebug("Loaded " + arrayName + " from config");
        } catch (Exception e) {
            logger.logError("Error loading " + arrayName + " from config", e);
        }
    }

    /**
     * Save current configuration to encrypted file.
     *
     * @throws ConfigurationException if save operation fails
     */
    public void saveConfig() {
        if (!initialized) {
            throw new ConfigurationException("Config not initialized");
        }

        configLock.readLock().lock();
        try {
            logger.logDebug("Saving config...");

            ensureConfigArraysExist();
            populateConfigArrays();
            writeEncryptedConfig();

            logger.logInfo("Encrypted config saved successfully.");

        } catch (Exception e) {
            logger.logError("Error saving encrypted config file", e);
            throw new ConfigurationException("Failed to save config", e);
        } finally {
            configLock.readLock().unlock();
        }
    }

    /**
     * Ensure programs and folders arrays exist in config.
     */
    private void ensureConfigArraysExist() {
        if (!config.has(PROGRAMS_ARRAY)) {
            logger.logDebug("Programs array not found in config. Creating...");
            config.add(PROGRAMS_ARRAY, new JsonArray());
        }
        if (!config.has(FOLDERS_ARRAY)) {
            logger.logDebug("Folders array not found in config. Creating...");
            config.add(FOLDERS_ARRAY, new JsonArray());
        }
    }

    /**
     * Populate config arrays with current locked items.
     */
    private void populateConfigArrays() {
        JsonArray programsArray = new JsonArray();
        JsonArray foldersArray = new JsonArray();

        for (LockedItem item : lockedItems) {
            String formattedPath = formatItemForStorage(item);
            logger.logDebug("Populating config arrays with item: " + formattedPath);
            if (isExecutableFile(item.getName())) {
                programsArray.add(formattedPath);
            } else {
                foldersArray.add(formattedPath);
            }
        }

        config.add(PROGRAMS_ARRAY, programsArray);
        config.add(FOLDERS_ARRAY, foldersArray);
    }

    /**
     * Format a locked item for storage in config.
     */
    private String formatItemForStorage(LockedItem item) {
        String status = item.isLocked() ? LOCKED_STATUS : UNLOCKED_STATUS;
        return item.getPath() + ITEM_SEPARATOR + status;
    }

    /**
     * Check if a file name represents an executable.
     */
    private boolean isExecutableFile(String fileName) {
        return fileName.toLowerCase().endsWith(".exe");
    }

    /**
     * Write encrypted configuration to file.
     *
     * @throws IOException if file operations fail
     */
    private void writeEncryptedConfig() throws IOException {
        ensureAppDataDirectoryExists();

        Path configPath = Paths.get(Main.APP_DATA_PATH, CONFIG_FILE);
        String jsonString = gson.toJson(config);
        String encryptedContent = EncryptionUtils.encrypt(jsonString, encryptionKey);

        if (encryptedContent == null) {
            throw new IOException("Encryption failed - null result");
        }

        Files.writeString(configPath, encryptedContent);
    }

    /**
     * Parse a locked item from its string representation.
     */
    private Optional<LockedItem> parseLockedItem(String input) {
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

            return Optional.of(LockedItemFactory.createLockedItem(path, name, isLocked));

        } catch (Exception e) {
            logger.logError("Error parsing locked item: " + input, e);
            return Optional.empty();
        }
    }

    /**
     * Extract file name from path.
     */
    private String extractFileName(String path) {
        int lastSeparatorIndex = path.lastIndexOf(File.separator);
        return lastSeparatorIndex >= 0 ? path.substring(lastSeparatorIndex + 1) : path;
    }

    /**
     * Set default configuration values.
     *
     * @throws ConfigurationException if default config creation fails
     */
    public void setDefaultConfig() {
        if (!initialized) {
            throw new ConfigurationException("Config not initialized");
        }

        configLock.writeLock().lock();
        try {
            logger.logDebug("Setting default config...");

            config = new JsonObject();
            config.addProperty(MODE_PROPERTY, DEFAULT_MODE);
            config.add(PROGRAMS_ARRAY, new JsonArray());
            config.add(FOLDERS_ARRAY, new JsonArray());
            config.addProperty(PASSWORD_PROPERTY, "");
            config.addProperty(AUTO_START_PROPERTY, false);

            lockedItems.clear();
            passwordHash = "";

            saveConfig();

            logger.logInfo("Default config created successfully.");

        } catch (Exception e) {
            logger.logError("Error saving default config", e);
            throw new ConfigurationException("Failed to create default config", e);
        } finally {
            configLock.writeLock().unlock();
        }
    }

    // Public API methods with proper synchronization

    /**
     * Get a copy of the current configuration.
     *
     * @return JsonObject copy of current config
     */
    public JsonObject getJsonConfig() {
        configLock.readLock().lock();
        try {
            return config != null ? config.deepCopy() : new JsonObject();
        } finally {
            configLock.readLock().unlock();
        }
    }

    /**
     * Get the config file reference.
     *
     * @return File reference to config file
     */
    public File getConfigFile() {
        return new File(Main.APP_DATA_PATH, CONFIG_FILE);
    }

    /**
     * Get current password hash.
     *
     * @return current password hash
     */
    public String getPasswordHash() {
        configLock.readLock().lock();
        try {
            return passwordHash;
        } finally {
            configLock.readLock().unlock();
        }
    }

    /**
     * Set password hash.
     *
     * @param passwordHash new password hash
     */
    public void setPasswordHash(String passwordHash) {
        configLock.writeLock().lock();
        try {
            this.passwordHash = passwordHash;
            if (config != null) {
                config.addProperty(PASSWORD_PROPERTY, passwordHash);
            }
        } finally {
            configLock.writeLock().unlock();
        }
    }

    /**
     * Get an immutable copy of locked items.
     *
     * @return immutable list of locked items
     */
    public List<LockedItem> getLockedItems() {
        configLock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(lockedItems));
        } finally {
            configLock.readLock().unlock();
        }
    }

    /**
     * Add a locked item.
     *
     * @param item the item to add
     */
    public void addLockedItem(LockedItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Item cannot be null");
        }

        configLock.writeLock().lock();
        try {
            lockedItems.add(item);
        } finally {
            configLock.writeLock().unlock();
        }
    }

    /**
     * Remove a locked item.
     *
     * @param item the item to remove
     * @return true if item was removed
     */
    public boolean removeLockedItem(LockedItem item) {
        configLock.writeLock().lock();
        try {
            return lockedItems.remove(item);
        } finally {
            configLock.writeLock().unlock();
        }
    }

    /**
     * Clear all locked items.
     */
    public void clearLockedItems() {
        configLock.writeLock().lock();
        try {
            lockedItems.clear();
        } finally {
            configLock.writeLock().unlock();
        }
    }

    /**
     * Check if the config is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the auto-start setting.
     *
     * @return true if auto-start is enabled
     */
    public boolean isAutoStartEnabled() {
        configLock.readLock().lock();
        try {
            return config != null && config.has(AUTO_START_PROPERTY) && 
                   config.get(AUTO_START_PROPERTY).getAsBoolean();
        } finally {
            configLock.readLock().unlock();
        }
    }

    /**
     * Set the auto-start setting.
     *
     * @param enabled true to enable auto-start
     */
    public void setAutoStart(boolean enabled) {
        configLock.writeLock().lock();
        try {
            if (config != null) {
                config.addProperty(AUTO_START_PROPERTY, enabled);
                logger.logInfo("Auto-start setting " + (enabled ? "enabled" : "disabled"));
            }
        } finally {
            configLock.writeLock().unlock();
        }
    }

    /**
     * Custom exception for configuration-related errors.
     */
    public static class ConfigurationException extends RuntimeException {
        public ConfigurationException(String message) {
            super(message);
        }

        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
