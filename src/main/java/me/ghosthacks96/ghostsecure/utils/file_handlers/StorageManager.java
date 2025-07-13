package me.ghosthacks96.ghostsecure.utils.file_handlers;

import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.utils.encryption.EncryptionUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Universal storage manager for GhostSecure application.
 * Handles encrypted YAML storage for any data type with thread safety and robust error handling.
 *
 * Features:
 * - Encrypted storage using AES-GCM
 * - Thread-safe operations
 * - Automatic backup and recovery
 * - Type-safe data operations
 * - Hierarchical key-value storage
 * - Batch operations
 * - Automatic file validation
 */
public class StorageManager {

    // Constants
    private static final String STORAGE_FILE_EXTENSION = ".yaml.enc";
    private static final String UNENCRYPTED_STORAGE_FILE_EXTENSION = ".yaml";
    private static final String BACKUP_FILE_EXTENSION = ".yaml.enc.bak";
    private static final String UNENCRYPTED_BACKUP_FILE_EXTENSION = ".yaml.bak";
    private static final String TEMP_FILE_EXTENSION = ".yaml.enc.tmp";
    private static final String UNENCRYPTED_TEMP_FILE_EXTENSION = ".yaml.tmp";
    private static final String DEFAULT_STORAGE_FILE = "storage";
    private static final String STORAGE_SALT_FILE = "storage.salt";

    // Instance fields
    private final Logging logger;
    private final ReadWriteLock storageLock;
    private final Map<String, Object> storageData;
    private final Yaml yaml;
    private final String storageFileName;
    private final Path storageFilePath;
    private final Path backupFilePath;
    private final Path tempFilePath;
    private final boolean useEncryption;

    private SecretKey encryptionKey;
    private boolean initialized;
    private boolean autoSave;
    private long lastModified;

    private final String APP_STORAGE_DIR = Main.APP_DATA_PATH+ "/storage/";

    /**
     * Creates a new StorageManager with default storage file and encryption enabled.
     */
    public StorageManager() {
        this(DEFAULT_STORAGE_FILE, true);
    }

    /**
     * Creates a new StorageManager with specified storage file name and encryption enabled.
     *
     * @param storageFileName the name of the storage file (without extension)
     */
    public StorageManager(String storageFileName) {
        this(storageFileName, true);
    }

    /**
     * Creates a new StorageManager with specified storage file name and encryption setting.
     *
     * @param storageFileName the name of the storage file (without extension)
     * @param useEncryption whether to use encryption for this storage
     */
    public StorageManager(String storageFileName, boolean useEncryption) {
        this.logger = Main.logger;
        this.storageLock = new ReentrantReadWriteLock();
        this.storageData = new ConcurrentHashMap<>();
        this.storageFileName = storageFileName;
        this.autoSave = true;
        this.initialized = false;
        this.lastModified = 0;
        this.useEncryption = useEncryption;

        // Configure YAML with proper settings
        this.yaml = createYamlInstance();

        // Initialize file paths with appropriate extensions based on encryption setting
        String fileExtension = useEncryption ? STORAGE_FILE_EXTENSION : UNENCRYPTED_STORAGE_FILE_EXTENSION;
        String backupExtension = useEncryption ? BACKUP_FILE_EXTENSION : UNENCRYPTED_BACKUP_FILE_EXTENSION;
        String tempExtension = useEncryption ? TEMP_FILE_EXTENSION : UNENCRYPTED_TEMP_FILE_EXTENSION;

        // Ensure storage directory exists before setting file paths
        try {
            ensureAppDataDirectoryExists();
        } catch (IOException e) {
            logger.logError("Failed to create storage directory", e);
            throw new StorageException("Failed to create storage directory", e);
        }

        this.storageFilePath = Paths.get(APP_STORAGE_DIR, storageFileName + fileExtension);
        this.backupFilePath = Paths.get(APP_STORAGE_DIR, storageFileName + backupExtension);
        this.tempFilePath = Paths.get(APP_STORAGE_DIR, storageFileName + tempExtension);

        logger.logDebug("StorageManager created for file: " + storageFileName + " (encryption: " + useEncryption + ")");

        if (useEncryption) {
            initializeEncryption();
        } else {
            // For unencrypted storage, we don't need encryption key
            this.initialized = true;
            logger.logInfo("Unencrypted storage initialized for: " + storageFileName);
        }
    }

    /**
     * Create a properly configured YAML instance.
     */
    private Yaml createYamlInstance() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setCanonical(false);
        options.setExplicitStart(false);
        options.setExplicitEnd(false);

        Representer representer = new Representer(options);
        representer.getPropertyUtils().setSkipMissingProperties(true);

        return new Yaml(representer, options);
    }

    /**
     * Initialize encryption for storage operations.
     */
    private void initializeEncryption() {
        logger.logDebug("Initializing storage encryption...");

        try {
            String username = System.getProperty("user.name");
            String systemInfo = EncryptionUtils.getSystemInfo();

            byte[] storageSalt = loadOrCreateStorageSalt();
            encryptionKey = EncryptionUtils.deriveKeyFromUserInfo(username, systemInfo, storageSalt);

            if (encryptionKey == null) {
                throw new StorageException("Failed to derive encryption key");
            }

            initialized = true;
            logger.logInfo("Storage encryption initialized successfully for: " + storageFileName);

        } catch (Exception e) {
            logger.logError("Failed to initialize storage encryption", e);
            throw new StorageException("Storage encryption initialization failed", e);
        }
    }

    /**
     * Load or create storage-specific salt.
     */
    private byte[] loadOrCreateStorageSalt() throws IOException {
        Path saltPath = Paths.get(APP_STORAGE_DIR, STORAGE_SALT_FILE);

        if (Files.exists(saltPath)) {
            logger.logDebug("Loading existing storage salt file");
            return Files.readAllBytes(saltPath);
        } else {
            logger.logDebug("Creating new storage salt file");
            byte[] newSalt = EncryptionUtils.generateSalt();

            ensureAppDataDirectoryExists();
            Files.write(saltPath, newSalt);

            logger.logInfo("New storage encryption salt created.");
            return newSalt;
        }
    }

    /**
     * Ensure the application data directory and storage directory exist.
     */
    private void ensureAppDataDirectoryExists() throws IOException {
        Path appDataPath = Paths.get(Main.APP_DATA_PATH);
        if (!Files.exists(appDataPath)) {
            Files.createDirectories(appDataPath);
            logger.logDebug("AppData directory created for storage");
        }

        Path storageDir = Paths.get(APP_STORAGE_DIR);
        if (!Files.exists(storageDir)) {
            Files.createDirectories(storageDir);
            logger.logDebug("Storage directory created: " + APP_STORAGE_DIR);
        }
    }

    /**
     * Load data from encrypted YAML file.
     */
    public void loadData() {
        if (!initialized) {
            throw new StorageException("StorageManager not initialized");
        }

        storageLock.writeLock().lock();
        try {
            logger.logDebug("Loading storage data from: " + storageFilePath);

            // Ensure storage directory exists
            try {
                ensureAppDataDirectoryExists();
            } catch (IOException e) {
                logger.logError("Failed to create storage directory", e);
                throw new StorageException("Failed to create storage directory", e);
            }

            if (!Files.exists(storageFilePath)) {
                handleMissingStorageFile();
                return;
            }

            loadDataFromFile();
            updateLastModified();

            logger.logInfo("Storage data loaded successfully: " + storageData.size() + " entries");

        } catch (Exception e) {
            logger.logError("Failed to load storage data", e);
            attemptRecoveryFromBackup();
        } finally {
            storageLock.writeLock().unlock();
        }
    }

    /**
     * Handle case when storage file doesn't exist.
     */
    private void handleMissingStorageFile() {
        logger.logDebug("Storage file does not exist, starting with empty storage");
        storageData.clear();
        lastModified = System.currentTimeMillis();
    }

    /**
     * Load data from the main storage file.
     */
    private void loadDataFromFile() throws IOException {
        String fileContent = Files.readString(storageFilePath);
        String yamlContent;

        if (useEncryption) {
            // Decrypt the content if encryption is enabled
            yamlContent = EncryptionUtils.decrypt(fileContent, encryptionKey);
            if (yamlContent == null) {
                throw new StorageException("Failed to decrypt storage file");
            }
        } else {
            // Use content directly if unencrypted
            yamlContent = fileContent;
        }

        // Parse YAML content
        Object loadedData = yaml.load(yamlContent);
        storageData.clear();

        if (loadedData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) loadedData;
            storageData.putAll(dataMap);
        } else if (loadedData != null) {
            logger.logWarning("Unexpected data format in storage file, creating new storage");
        }
    }

    /**
     * Attempt to recover from backup file.
     */
    private void attemptRecoveryFromBackup() {
        logger.logInfo("Attempting to recover from backup file...");

        try {
            if (Files.exists(backupFilePath)) {
                String backupContent = Files.readString(backupFilePath);
                String yamlContent;

                if (useEncryption) {
                    // Decrypt the backup if encryption is enabled
                    yamlContent = EncryptionUtils.decrypt(backupContent, encryptionKey);
                    if (yamlContent == null) {
                        throw new StorageException("Failed to decrypt backup file");
                    }
                } else {
                    // Use content directly if unencrypted
                    yamlContent = backupContent;
                }

                Object backupData = yaml.load(yamlContent);
                storageData.clear();

                if (backupData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataMap = (Map<String, Object>) backupData;
                    storageData.putAll(dataMap);
                }

                logger.logInfo("Successfully recovered from backup file");
                saveData(); // Save recovered data to main file
                return;
            }

            logger.logWarning("No valid backup found, starting with empty storage");
            storageData.clear();

        } catch (Exception e) {
            logger.logError("Failed to recover from backup", e);
            storageData.clear();
        }

        lastModified = System.currentTimeMillis();
    }

    /**
     * Save data to YAML file (encrypted or unencrypted based on configuration).
     */
    public void saveData() {
        if (!initialized) {
            throw new StorageException("StorageManager not initialized");
        }

        storageLock.readLock().lock();
        try {
            logger.logDebug("Saving storage data to: " + storageFilePath);

            ensureAppDataDirectoryExists();
            createBackupIfExists();

            String yamlContent = yaml.dump(new HashMap<>(storageData));
            String contentToWrite;

            if (useEncryption) {
                // Encrypt the content if encryption is enabled
                contentToWrite = EncryptionUtils.encrypt(yamlContent, encryptionKey);
                if (contentToWrite == null) {
                    throw new StorageException("Failed to encrypt storage data");
                }
            } else {
                // Use content directly if unencrypted
                contentToWrite = yamlContent;
            }

            // Write to temporary file first, then rename for atomic operation
            Files.writeString(tempFilePath, contentToWrite);
            Files.move(tempFilePath, storageFilePath, StandardCopyOption.REPLACE_EXISTING);

            updateLastModified();
            logger.logInfo("Storage data saved successfully: " + storageData.size() + " entries");

        } catch (Exception e) {
            logger.logError("Failed to save storage data", e);
            throw new StorageException("Failed to save storage data", e);
        } finally {
            storageLock.readLock().unlock();
        }
    }

    /**
     * Create backup of existing storage file.
     */
    private void createBackupIfExists() throws IOException {
        if (Files.exists(storageFilePath)) {
            Files.copy(storageFilePath, backupFilePath, StandardCopyOption.REPLACE_EXISTING);
            logger.logDebug("Backup created for storage file");
        }
    }

    /**
     * Update last modified timestamp.
     */
    private void updateLastModified() {
        lastModified = System.currentTimeMillis();
    }

    // Public API methods

    /**
     * Store a value with the specified key.
     *
     * @param key the storage key
     * @param value the value to store
     * @param <T> the type of value
     */
    public <T> void put(String key, T value) {
        validateKey(key);

        storageLock.writeLock().lock();
        try {
            storageData.put(key, value);
            updateLastModified();

            if (autoSave) {
                saveData();
            }

            logger.logDebug("Stored value for key: " + key);
        } finally {
            storageLock.writeLock().unlock();
        }
    }

    /**
     * Retrieve a value by key.
     *
     * @param key the storage key
     * @param defaultValue the default value if key doesn't exist
     * @param <T> the type of value
     * @return the stored value or default value
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        validateKey(key);

        storageLock.readLock().lock();
        try {
            Object value = storageData.get(key);
            if (value == null) {
                return defaultValue;
            }

            try {
                return (T) value;
            } catch (ClassCastException e) {
                logger.logWarning("Type mismatch for key: " + key + ", returning default value");
                return defaultValue;
            }
        } finally {
            storageLock.readLock().unlock();
        }
    }

    /**
     * Retrieve a value by key without default.
     *
     * @param key the storage key
     * @param <T> the type of value
     * @return the stored value or null
     */
    public <T> T get(String key) {
        return get(key, null);
    }

    /**
     * Check if a key exists in storage.
     *
     * @param key the storage key
     * @return true if key exists
     */
    public boolean containsKey(String key) {
        validateKey(key);

        storageLock.readLock().lock();
        try {
            return storageData.containsKey(key);
        } finally {
            storageLock.readLock().unlock();
        }
    }

    /**
     * Remove a value by key.
     *
     * @param key the storage key
     * @return the removed value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T remove(String key) {
        validateKey(key);

        storageLock.writeLock().lock();
        try {
            Object removed = storageData.remove(key);
            updateLastModified();

            if (autoSave) {
                saveData();
            }

            logger.logDebug("Removed value for key: " + key);
            return (T) removed;
        } finally {
            storageLock.writeLock().unlock();
        }
    }

    /**
     * Clear all stored data.
     */
    public void clear() {
        storageLock.writeLock().lock();
        try {
            storageData.clear();
            updateLastModified();

            if (autoSave) {
                saveData();
            }

            logger.logInfo("Storage cleared");
        } finally {
            storageLock.writeLock().unlock();
        }
    }

    /**
     * Get all keys in storage.
     *
     * @return set of all keys
     */
    public Set<String> getKeys() {
        storageLock.readLock().lock();
        try {
            return new HashSet<>(storageData.keySet());
        } finally {
            storageLock.readLock().unlock();
        }
    }

    /**
     * Get the number of stored items.
     *
     * @return number of items
     */
    public int size() {
        storageLock.readLock().lock();
        try {
            return storageData.size();
        } finally {
            storageLock.readLock().unlock();
        }
    }

    /**
     * Check if storage is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        storageLock.readLock().lock();
        try {
            return storageData.isEmpty();
        } finally {
            storageLock.readLock().unlock();
        }
    }

    /**
     * Store multiple values at once.
     *
     * @param data map of key-value pairs to store
     */
    public void putAll(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        storageLock.writeLock().lock();
        try {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                validateKey(entry.getKey());
                storageData.put(entry.getKey(), entry.getValue());
            }

            updateLastModified();

            if (autoSave) {
                saveData();
            }

            logger.logDebug("Stored " + data.size() + " values in batch");
        } finally {
            storageLock.writeLock().unlock();
        }
    }

    /**
     * Get a copy of all stored data.
     *
     * @return copy of all data
     */
    public Map<String, Object> getAllData() {
        storageLock.readLock().lock();
        try {
            return new HashMap<>(storageData);
        } finally {
            storageLock.readLock().unlock();
        }
    }

    /**
     * Set auto-save mode.
     *
     * @param autoSave true to enable auto-save
     */
    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
        logger.logDebug("Auto-save " + (autoSave ? "enabled" : "disabled"));
    }

    /**
     * Check if auto-save is enabled.
     *
     * @return true if auto-save is enabled
     */
    public boolean isAutoSave() {
        return autoSave;
    }

    /**
     * Get the last modified timestamp.
     *
     * @return last modified timestamp
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Check if storage manager is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Check if this storage manager uses encryption.
     *
     * @return true if encryption is enabled
     */
    public boolean isEncryptionEnabled() {
        return useEncryption;
    }

    /**
     * Get the storage file name.
     *
     * @return storage file name
     */
    public String getStorageFileName() {
        return storageFileName;
    }

    /**
     * Validate a storage key.
     *
     * @param key the key to validate
     */
    private void validateKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Storage key cannot be null or empty");
        }
    }

    /**
     * Custom exception for storage-related errors.
     */
    public static class StorageException extends RuntimeException {
        public StorageException(String message) {
            super(message);
        }

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
