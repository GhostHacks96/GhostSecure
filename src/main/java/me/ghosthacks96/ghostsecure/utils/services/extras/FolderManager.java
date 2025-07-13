package me.ghosthacks96.ghostsecure.utils.services.extras;

import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;
import me.ghosthacks96.ghostsecure.utils.services.ServiceController;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static me.ghosthacks96.ghostsecure.Main.folderStorage;
import static me.ghosthacks96.ghostsecure.Main.logger;

import java.util.Map;

public class FolderManager {

    private static long lastLogTime = 0;
    private static final long LOG_INTERVAL_MS = 60_000; // 1 minute

    /**
     * Check and apply folder permissions for all locked items
     */
    public static void checkFolders() {
        logDebugWithThrottle("checkFolders() called");

        try {
            // Get all folder data from folderStorage
            Map<String, Object> allData = folderStorage.getAllData();

            for (Map.Entry<String, Object> entry : allData.entrySet()) {
                if (ServiceController.isShuttingDown()) {
                    logger.logDebug("Shutdown requested, stopping folder check");
                    return;
                }

                if (entry.getValue() instanceof Map) {
                    Map<String, Object> itemData = (Map<String, Object>) entry.getValue();
                    if ("FOLDER".equals(itemData.get("type"))) {
                        String path = (String) itemData.get("path");
                        String name = (String) itemData.get("name");
                        boolean locked = (boolean) itemData.get("locked");

                        Path folderPath = Paths.get(path);
                        logger.logDebug("Checking folder: " + path);

                        if (!Files.exists(folderPath)) {
                            logger.logWarning("Folder does not exist: " + path);
                            continue;
                        }

                        // Create a temporary LockedItem for compatibility with existing methods
                        LockedItem item = new LockedItem(path, name) {
                            @Override
                            public boolean isLocked() {
                                return locked;
                            }

                            @Override
                            public String getItemType() {
                                return "FOLDER";
                            }

                            @Override
                            public LockedItem copy() {
                                return this;
                            }
                        };

                        applyPermissionsRecursively(folderPath, item);
                    }
                }
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
            // Get all folder data from folderStorage
            Map<String, Object> allData = folderStorage.getAllData();

            for (Map.Entry<String, Object> entry : allData.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> itemData = (Map<String, Object>) entry.getValue();
                    if ("FOLDER".equals(itemData.get("type"))) {
                        String path = (String) itemData.get("path");

                        Path folderPath = Paths.get(path);
                        if (!Files.exists(folderPath)) {
                            logger.logWarning("Folder does not exist: " + path);
                            continue;
                        }

                        logger.logDebug("Unlocking folder: " + path);
                        applyPermissionsRecursively(folderPath, false); // Force unlock
                    }
                }
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
            if (shouldLock) {
                // When locking: collect all paths first (while unlocked), then lock deepest first
                List<Path> allPaths = collectAllPaths(rootPath);
                lockPathsDeepestFirst(allPaths);
            } else {
                // When unlocking: unlock root first, then process contents
                unlockPathsRootFirst(rootPath);
            }
        } catch (Exception e) {
            logger.logError("Failed to apply permissions recursively to: " + rootPath + "; Error: " + e.getMessage(), e);
        }
    }

    /**
     * Collect all paths in the directory tree while it's still accessible
     */
    private static List<Path> collectAllPaths(Path rootPath) throws IOException {
        List<Path> allPaths = new ArrayList<>();

        if (Files.isDirectory(rootPath)) {
            try (Stream<Path> paths = Files.walk(rootPath)) {
                allPaths = paths.toList();
            }
        } else {
            allPaths.add(rootPath);
        }

        logger.logDebug("Collected " + allPaths.size() + " paths under " + rootPath);
        return allPaths;
    }

    /**
     * Lock paths starting from the deepest level (files first, then directories)
     */
    private static void lockPathsDeepestFirst(List<Path> allPaths) {
        allPaths.stream()
                .sorted((p1, p2) -> Integer.compare(p2.getNameCount(), p1.getNameCount()))
                .forEach(path -> {
                    try {
                        applyPermissionsToPath(path, true);
                    } catch (Exception e) {
                        logger.logError("Failed to apply lock permissions to: " + path + "; Error: " + e.getMessage(), e);
                    }
                });
    }

    /**
     * Unlock paths starting from root, then recursively unlock contents
     */
    private static void unlockPathsRootFirst(Path rootPath) {
        try {
            // First unlock the root so we can access its contents
            applyPermissionsToPath(rootPath, false);

            // If it's a directory, process its contents
            if (Files.isDirectory(rootPath)) {
                processDirectoryContentsForUnlock(rootPath);
            }
        } catch (Exception e) {
            logger.logError("Failed to unlock path: " + rootPath + "; Error: " + e.getMessage(), e);
        }
    }

    /**
     * Process directory contents for unlocking (root-first approach)
     */
    private static void processDirectoryContentsForUnlock(Path rootPath) throws IOException {
        try (Stream<Path> paths = Files.list(rootPath)) {
            List<Path> directContents = paths.toList();

            for (Path path : directContents) {
                try {
                    // Unlock this path first
                    applyPermissionsToPath(path, false);

                    // If it's a directory, recursively unlock its contents
                    if (Files.isDirectory(path)) {
                        processDirectoryContentsForUnlock(path);
                    }
                } catch (Exception e) {
                    logger.logError("Failed to unlock path: " + path + "; Error: " + e.getMessage(), e);
                }
            }
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
            logger.logDebug("Applied " + action + " permissions to " + type + ": " + path);

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
