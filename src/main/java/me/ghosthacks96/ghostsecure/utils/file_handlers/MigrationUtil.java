package me.ghosthacks96.ghostsecure.utils.file_handlers;

import me.ghosthacks96.ghostsecure.itemTypes.LockedFolder;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;
import me.ghosthacks96.ghostsecure.itemTypes.LockedProgram;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.ghosthacks96.ghostsecure.Main.*;

public class MigrationUtil {
    /**
     * Migrates data from the old Config format to the new StorageManager format.
     * This handles the conversion of programs and folders data.
     */
    private static void migrateConfigToStorageManager() {
        logger.logInfo("Migrating data from Config to StorageManager...");

        try {
            // Load config data if not already loaded
            config.loadConfig(false);

            // Migrate programs
            if (config.getJsonConfig().has("programs")) {
                logger.logDebug("Migrating programs data...");
                List<LockedItem> programItems = config.getLockedItems().stream()
                        .filter(item -> item instanceof LockedProgram)
                        .collect(Collectors.toList());

                // Store each program in the storage manager
                for (int i = 0; i < programItems.size(); i++) {
                    LockedItem item = programItems.get(i);
                    String key = "program_" + i;
                    Map<String, Object> itemData = new HashMap<>();
                    itemData.put("path", item.getPath());
                    itemData.put("name", item.getName());
                    itemData.put("locked", item.isLocked());
                    itemData.put("type", "PROGRAM");

                    programStorage.put(key, itemData);
                }

                logger.logInfo("Migrated " + programItems.size() + " programs to StorageManager");
            }

            // Migrate folders
            if (config.getJsonConfig().has("folders")) {
                logger.logDebug("Migrating folders data...");
                List<LockedItem> folderItems = config.getLockedItems().stream()
                        .filter(item -> item instanceof LockedFolder)
                        .collect(Collectors.toList());

                // Store each folder in the storage manager
                for (int i = 0; i < folderItems.size(); i++) {
                    LockedItem item = folderItems.get(i);
                    String key = "folder_" + i;
                    Map<String, Object> itemData = new HashMap<>();
                    itemData.put("path", item.getPath());
                    itemData.put("name", item.getName());
                    itemData.put("locked", item.isLocked());
                    itemData.put("type", "FOLDER");

                    folderStorage.put(key, itemData);
                }

                logger.logInfo("Migrated " + folderItems.size() + " folders to StorageManager");
            }

            // Migrate account data (password)
            if (config.getJsonConfig().has("password")) {
                logger.logDebug("Migrating account data...");
                String passwordHash = config.getPasswordHash();
                if (passwordHash != null && !passwordHash.isEmpty()) {
                    accountStorage.put("password_hash", passwordHash);
                    logger.logInfo("Migrated account data to StorageManager");
                }
            }
            // Migrate auto-start setting to system-config if it exists
            if (config.getJsonConfig().has("auto_start")) {
                boolean autoStart = config.isAutoStartEnabled();
                systemConfigStorage.put("auto_start", autoStart);
                systemConfigStorage.saveData();
                logger.logInfo("Migrated auto-start setting to system-config: " + autoStart);
            }

            // Save the migrated data
            programStorage.saveData();
            folderStorage.saveData();
            accountStorage.saveData();

            backupOldConfigFile();
            logger.logInfo("Data migration completed successfully");

        } catch (Exception e) {
            logger.logError("Error during data migration: " + e.getMessage(), e);
        }
    }

    private static void backupOldConfigFile() {
        File configFile = config.getConfigFile();

        if (configFile.exists()) {
            try {
                // Create backup file path
                String backupFileName = configFile.getName() + ".bak";
                File backupFile = new File(configFile.getParent(), backupFileName);

                // If backup already exists, create a numbered backup
                int backupNumber = 1;
                while (backupFile.exists()) {
                    backupFileName = configFile.getName() + ".bak." + backupNumber;
                    backupFile = new File(configFile.getParent(), backupFileName);
                    backupNumber++;
                }

                // Copy the original file to backup location
                Path sourcePath = configFile.toPath();
                Path backupPath = backupFile.toPath();

                Files.copy(sourcePath, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
                logger.logInfo("Created backup of old config file: " + backupFile.getAbsolutePath());

                // Delete the original file after successful backup
                if (configFile.delete()) {
                    logger.logInfo("Removed original config file after backup: " + configFile.getAbsolutePath());
                } else {
                    logger.logWarning("Failed to delete original config file: " + configFile.getAbsolutePath());
                }

            } catch (IOException e) {
                logger.logError("Failed to backup old config file: " + e.getMessage(), e);
            }
        } else {
            logger.logDebug("No old config file to backup");
        }
    }


    public static boolean checkAndMigrateData() {
        logger.logDebug("Checking if data migration is needed...");
        // Check if the old config format exists
        if (config.getConfigFile().exists()) {
            logger.logInfo("Old config format detected, starting migration...");
            migrateConfigToStorageManager();
            return true;
        } else {
            logger.logInfo("No migration needed, using new StorageManager format.");
        }
        return false;
    }


}
