package me.ghosthacks96.ghostsecure.utils.services;

import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.List;
import java.util.stream.Stream;

import static me.ghosthacks96.ghostsecure.Main.config;
import static me.ghosthacks96.ghostsecure.Main.logger;

public class FolderManager {

    private static long lastLogTime = 0;
    private static final long LOG_INTERVAL_MS = 60_000; // 1 minute

    /**
     * Check and apply folder permissions for all locked items
     */
    public static void checkFolders() {
        logDebugWithThrottle("checkFolders() called");

        try {
            for (LockedItem item : config.lockedItems) {
                if (ServiceController.isShuttingDown()) {
                    logger.logDebug("Shutdown requested, stopping folder check");
                    return;
                }

                if (item.getName().contains(".exe")) continue;

                Path folderPath = Paths.get(item.getPath());
                logger.logDebug("Checking folder: " + item.getPath());

                if (!Files.exists(folderPath)) {
                    logger.logWarning("Folder does not exist: " + item.getPath());
                    continue;
                }

                applyPermissionsRecursively(folderPath, item);
            }
        } catch (Exception e) {
            logger.logError("Failed to check locked folders: " + e.getMessage(), e);
        }
    }

    /**
     * Unlock all folders (used during shutdown)
     */
    public static void unlockAllFolders() {
        logger.logDebug("unlockAllFolders() called");

        try {
            for (LockedItem item : config.lockedItems) {
                if (item.getName().contains(".exe")) continue;

                Path folderPath = Paths.get(item.getPath());
                if (!Files.exists(folderPath)) {
                    logger.logWarning("Folder does not exist: " + item.getPath());
                    continue;
                }

                logger.logDebug("Unlocking folder: " + item.getPath());
                applyPermissionsRecursively(folderPath, false); // Force unlock
            }
        } catch (Exception e) {
            logger.logError("Failed to unlock folders: " + e.getMessage(), e);
        }
    }

    /**
     * Apply permissions recursively to a folder and all its contents
     */
    private static void applyPermissionsRecursively(Path rootPath, LockedItem item) {
        applyPermissionsRecursively(rootPath, item.isLocked());
    }

    /**
     * Apply permissions recursively to a folder and all its contents
     * @param rootPath The root path to apply permissions to
     * @param shouldLock Whether to lock (true) or unlock (false) the path
     */
    private static void applyPermissionsRecursively(Path rootPath, boolean shouldLock) {
        logger.logDebug("applyPermissionsRecursively() called for: " + rootPath);

        try {
            if (Files.isDirectory(rootPath)) {
                processDirectoryContents(rootPath, shouldLock);
            }

            // Apply permissions to the root folder/file
            if (!ServiceController.isShuttingDown()) {
                applyPermissionsToPath(rootPath, shouldLock);
            }
        } catch (Exception e) {
            logger.logError("Failed to apply permissions recursively to: " + rootPath + "; Error: " + e.getMessage(), e);
        }
    }

    /**
     * Process all contents of a directory
     */
    private static void processDirectoryContents(Path rootPath, boolean shouldLock) throws IOException {
        try (Stream<Path> paths = Files.walk(rootPath)) {
            List<Path> allPaths = paths.filter(path -> !path.equals(rootPath)).toList();

            logger.logDebug("Found " + allPaths.size() + " paths under " + rootPath);

            // Sort paths based on lock state: deepest first when locking, shallowest first when unlocking
            allPaths.stream()
                    .sorted(shouldLock ?
                            (p1, p2) -> Integer.compare(p2.getNameCount(), p1.getNameCount()) :
                            (p1, p2) -> Integer.compare(p1.getNameCount(), p2.getNameCount()))
                    .forEach(path -> {
                        if (ServiceController.isShuttingDown()) return;
                        try {
                            applyPermissionsToPath(path, shouldLock);
                        } catch (Exception e) {
                            logger.logError("Failed to apply permissions to: " + path + "; Error: " + e.getMessage(), e);
                        }
                    });
        }
    }

    /**
     * Apply permissions to a specific path
     */
    private static void applyPermissionsToPath(Path path, boolean shouldLock) {
        logger.logDebug("applyPermissionsToPath() called for: " + path);

        try {
            AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (aclView == null) {
                logger.logError("ACL view not supported on this file system for: " + path);
                return;
            }

            boolean denyAccess = shouldLock && !ServiceController.isShuttingDown();
            AclEntry aclEntry = createAclEntry(denyAccess);
            aclView.setAcl(List.of(aclEntry));

            String action = denyAccess ? "DENY" : "ALLOW";
            String type = Files.isDirectory(path) ? "directory" : "file";
            logger.logInfo("Applied " + action + " permissions to " + type + ": " + path);

        } catch (Exception e) {
            logger.logError("Failed to modify permissions for: " + path + "; Error: " + e.getMessage(), e);
        }
    }

    /**
     * Create an ACL entry for the given access type
     */
    private static AclEntry createAclEntry(boolean denyAccess) throws IOException {
        AclEntryType entryType = denyAccess ? AclEntryType.DENY : AclEntryType.ALLOW;

        return AclEntry.newBuilder()
                .setType(entryType)
                .setPrincipal(FileSystems.getDefault()
                        .getUserPrincipalLookupService()
                        .lookupPrincipalByName("Everyone"))
                .setPermissions(AclEntryPermission.values())
                .build();
    }

    /**
     * Log debug messages with throttling to avoid spam
     */
    private static void logDebugWithThrottle(String message) {
        long now = System.currentTimeMillis();
        if (now - lastLogTime > LOG_INTERVAL_MS) {
            logger.logDebug(message);
            lastLogTime = now;
        }
    }
}