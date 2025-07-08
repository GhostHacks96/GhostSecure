package me.ghosthacks96.ghostsecure.utils.services;

import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class ServiceController {

    // Use AtomicBoolean for thread-safe shutdown flag
    private static final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);

    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> blockerTask;

    private static long lastCheckLogTime = 0;
    private static final long CHECK_LOG_INTERVAL_MS = 60_000; // 1 minute

    /**
     * Start the blocker daemon service
     * @return true if started successfully, false otherwise
     */
    public static synchronized boolean startBlockerDaemon() {
        Main.logger.logDebug("startBlockerDaemon() called");

        // Check if already running
        if (isRunning.get()) {
            Main.logger.logInfo("Blocker daemon is already running.");
            return true;
        }

        Main.logger.logInfo("Attempting to start the blocker daemon.");
        try {
            // Reset shutdown flag
            isShuttingDown.set(false);

            // Initialize the scheduler with daemon threads
            scheduler = Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "ServiceController-Thread");
                thread.setDaemon(true);
                return thread;
            });

            Main.logger.logDebug("Scheduler initialized");

            // Schedule the blocking task
            blockerTask = scheduler.scheduleAtFixedRate(() -> {
                try {
                    if (isShuttingDown.get()) {
                        Main.logger.logDebug("Shutdown requested, stopping blocker daemon tick");
                        return;
                    }

                    Main.logger.logDebug("Blocker daemon tick: checking programs and folders");
                    checkPrograms();
                    checkFolders();
                } catch (Exception e) {
                    Main.logger.logError("Exception in blocker daemon tick: " + e.getMessage(), e);
                }
            }, 0, 1, TimeUnit.SECONDS);

            isRunning.set(true);
            Main.logger.logInfo("Blocker daemon started successfully.");
            return true;

        } catch (Exception e) {
            Main.logger.logError("Failed to start the blocker daemon: " + e.getMessage(), e);
            isRunning.set(false);
            return false;
        }
    }

    /**
     * Stop the blocker daemon service
     * @return true if stopped successfully, false otherwise
     */
    public static synchronized boolean stopBlockerDaemon() {
        Main.logger.logDebug("stopBlockerDaemon() called");

        if (!isRunning.get()) {
            Main.logger.logInfo("Blocker daemon is not running.");
            return true;
        }

        try {
            Main.logger.logInfo("Shutting down the blocker daemon.");

            // Set shutdown flag to stop the daemon loop
            isShuttingDown.set(true);

            // Cancel the blocking task
            if (blockerTask != null && !blockerTask.isCancelled()) {
                blockerTask.cancel(true);
            }

            // Unlock all folders before shutting down
            unlockAllFolders();

            // Shutdown the scheduler
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        Main.logger.logDebug("Scheduler did not terminate in time, forcing shutdown");
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Main.logger.logDebug("InterruptedException during scheduler shutdown: " + e.getMessage(), e);
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt(); // Restore interrupted status
                }
            }

            isRunning.set(false);
            Main.logger.logInfo("Blocker daemon stopped successfully.");
            return true;

        } catch (Exception e) {
            Main.logger.logError("Error stopping blocker daemon: " + e.getMessage(), e);
            isRunning.set(false);
            return false;
        }
    }

    /**
     * Restart the blocker daemon service
     * @return true if restarted successfully, false otherwise
     */
    public static synchronized boolean restartBlockerDaemon() {
        Main.logger.logInfo("Restarting blocker daemon...");
        boolean stopped = stopBlockerDaemon();
        if (!stopped) {
            Main.logger.logError("Failed to stop blocker daemon, cannot restart");
            return false;
        }

        // Wait a moment to ensure cleanup is complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Main.logger.logDebug("Interrupted during restart delay");
        }

        return startBlockerDaemon();
    }

    /**
     * Check if the service is currently running
     * @return true if running, false otherwise
     */
    public static boolean isServiceRunning() {
        Main.logger.logDebug("isServiceRunning() called");
        try {
            boolean running = isRunning.get() &&
                    scheduler != null &&
                    !scheduler.isShutdown() &&
                    !scheduler.isTerminated() &&
                    Main.config.getJsonConfig().get("mode").getAsString().equals("lock");

            if (running) {
                Main.logger.logInfo("Service is active and running in locking mode.");
            } else {
                Main.logger.logInfo("Service is not running.");
            }
            return running;
        } catch (Exception e) {
            Main.logger.logWarning("Error while checking service status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the current service status as a string
     * @return Status string
     */
    public static String getServiceStatus() {
        if (isShuttingDown.get()) {
            return "Shutting down...";
        } else if (isRunning.get()) {
            return "Running";
        } else {
            return "Stopped";
        }
    }

    /**
     * Unlock all folders (used during shutdown)
     */
    private static void unlockAllFolders() {
        Main.logger.logDebug("unlockAllFolders() called");
        try {
            for (LockedItem li : Main.lockedItems) {
                if (li.getName().contains(".exe")) continue;

                Path folderPath = Paths.get(li.getPath());
                if (!Files.exists(folderPath)) {
                    Main.logger.logWarning("Folder does not exist: " + li.getPath());
                    continue;
                }

                Main.logger.logDebug("Unlocking folder: " + li.getPath());
                unlockPathRecursively(folderPath);
            }
        } catch (Exception e) {
            Main.logger.logError("Failed to unlock folders: " + e.getMessage(), e);
        }
    }

    /**
     * Recursively unlock a path and all its contents
     */
    private static void unlockPathRecursively(Path rootPath) {
        Main.logger.logDebug("unlockPathRecursively() called for: " + rootPath);
        try {
            if (Files.isDirectory(rootPath)) {
                try (Stream<Path> paths = Files.walk(rootPath)) {
                    List<Path> allPaths = paths.filter(path -> !path.equals(rootPath))
                            .toList();

                    // Unlock from shallowest to deepest
                    allPaths.stream()
                            .sorted((p1, p2) -> Integer.compare(p1.getNameCount(), p2.getNameCount()))
                            .forEach(path -> {
                                try {
                                    unlockPath(path);
                                } catch (Exception e) {
                                    Main.logger.logError("Failed to unlock path: " + path + "; Error: " + e.getMessage(), e);
                                }
                            });
                } catch (IOException e) {
                    Main.logger.logError("Failed to walk directory tree for: " + rootPath + "; Error: " + e.getMessage(), e);
                }
            }
            // Unlock the root path
            unlockPath(rootPath);
        } catch (Exception e) {
            Main.logger.logError("Failed to unlock path recursively: " + rootPath + "; Error: " + e.getMessage(), e);
        }
    }

    /**
     * Unlock a specific path
     */
    private static void unlockPath(Path path) {
        Main.logger.logDebug("unlockPath() called for: " + path);
        try {
            AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (aclView == null) {
                Main.logger.logError("ACL view not supported on this file system for: " + path);
                return;
            }

            AclEntry grantAllAccess = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(FileSystems.getDefault()
                            .getUserPrincipalLookupService()
                            .lookupPrincipalByName("Everyone"))
                    .setPermissions(AclEntryPermission.values())
                    .build();

            aclView.setAcl(List.of(grantAllAccess));
            Main.logger.logInfo("Unlocked: " + path);
        } catch (Exception e) {
            Main.logger.logError("Failed to unlock path: " + path + "; Error: " + e.getMessage(), e);
        }
    }

    private static void checkFolders() {
        long now = System.currentTimeMillis();
        if (now - lastCheckLogTime > CHECK_LOG_INTERVAL_MS) {
            Main.logger.logDebug("checkFolders() called");
            lastCheckLogTime = now;
        }
        try {
            for (LockedItem li : Main.lockedItems) {
                if (isShuttingDown.get()) {
                    Main.logger.logDebug("Shutdown requested, stopping folder check");
                    return;
                }

                Path folderPath = Paths.get(li.getPath());
                Main.logger.logDebug("Checking folder: " + li.getPath());

                if (!Files.exists(folderPath)) {
                    Main.logger.logWarning("Folder does not exist: " + li.getPath());
                    continue;
                }

                if (li.getName().contains(".exe")) continue;

                // Apply permissions recursively to the folder and all its contents
                applyPermissionsRecursively(folderPath, li);
            }
        } catch (Exception e) {
            Main.logger.logError("Failed to check locked folders: " + e.getMessage(), e);
        }
    }

    private static void applyPermissionsRecursively(Path rootPath, LockedItem li) {
        Main.logger.logDebug("applyPermissionsRecursively() called for: " + rootPath);
        try {
            if (Files.isDirectory(rootPath)) {
                try (Stream<Path> paths = Files.walk(rootPath)) {
                    List<Path> allPaths = paths.filter(path -> !path.equals(rootPath))
                            .toList();

                    Main.logger.logDebug("Found " + allPaths.size() + " paths under " + rootPath);

                    if (li.isLocked() && Main.config.getJsonConfig().get("mode").getAsString().equals("lock")) {
                        // When locking: Start from deepest files/folders first
                        allPaths.stream()
                                .sorted((p1, p2) -> Integer.compare(p2.getNameCount(), p1.getNameCount()))
                                .forEach(path -> {
                                    if (isShuttingDown.get()) return;
                                    try {
                                        applyPermissionsToPath(path, li);
                                    } catch (Exception e) {
                                        Main.logger.logError("Failed to apply permissions to: " + path + "; Error: " + e.getMessage(), e);
                                    }
                                });
                    } else {
                        // When unlocking: Start from shallowest first
                        allPaths.stream()
                                .sorted((p1, p2) -> Integer.compare(p1.getNameCount(), p2.getNameCount()))
                                .forEach(path -> {
                                    if (isShuttingDown.get()) return;
                                    try {
                                        applyPermissionsToPath(path, li);
                                    } catch (Exception e) {
                                        Main.logger.logError("Failed to apply permissions to: " + path + "; Error: " + e.getMessage(), e);
                                    }
                                });
                    }
                } catch (IOException e) {
                    Main.logger.logError("Failed to walk directory tree for: " + rootPath + "; Error: " + e.getMessage(), e);
                }
            }

            // Apply permissions to the root folder/file
            if (!isShuttingDown.get()) {
                applyPermissionsToPath(rootPath, li);
            }
        } catch (Exception e) {
            Main.logger.logError("Failed to apply permissions recursively to: " + rootPath + "; Error: " + e.getMessage(), e);
        }
    }

    private static void applyPermissionsToPath(Path path, LockedItem li) {
        Main.logger.logDebug("applyPermissionsToPath() called for: " + path);
        try {
            AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (aclView == null) {
                Main.logger.logError("ACL view not supported on this file system for: " + path);
                return;
            }

            if (li.isLocked() && Main.config.getJsonConfig().get("mode").getAsString().equals("lock") && !isShuttingDown.get()) {
                // Deny all access to the path
                AclEntry denyAllAccess = AclEntry.newBuilder()
                        .setType(AclEntryType.DENY)
                        .setPrincipal(FileSystems.getDefault()
                                .getUserPrincipalLookupService()
                                .lookupPrincipalByName("Everyone"))
                        .setPermissions(AclEntryPermission.values())
                        .build();
                aclView.setAcl(List.of(denyAllAccess));

                if (Files.isDirectory(path)) {
                    Main.logger.logInfo("Applied DENY permissions to directory: " + path);
                } else {
                    Main.logger.logInfo("Applied DENY permissions to file: " + path);
                }
            } else {
                // Restore access by granting all permissions
                AclEntry grantAllAccess = AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(FileSystems.getDefault()
                                .getUserPrincipalLookupService()
                                .lookupPrincipalByName("Everyone"))
                        .setPermissions(AclEntryPermission.values())
                        .build();
                aclView.setAcl(List.of(grantAllAccess));

                if (Files.isDirectory(path)) {
                    Main.logger.logInfo("Applied ALLOW permissions to directory: " + path);
                } else {
                    Main.logger.logInfo("Applied ALLOW permissions to file: " + path);
                }
            }
        } catch (Exception aclError) {
            Main.logger.logError("Failed to modify permissions for: " + path + "; Error: " + aclError.getMessage(), aclError);
        }
    }

    private static void checkPrograms() {
        Main.logger.logDebug("checkPrograms() called");
        try {
            if (isShuttingDown.get()) {
                Main.logger.logDebug("Shutdown requested, stopping program check");
                return;
            }

            Process process = new ProcessBuilder("tasklist").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                if (isShuttingDown.get()) {
                    Main.logger.logDebug("Shutdown requested, stopping program check");
                    break;
                }

                for (LockedItem li : Main.lockedItems) {
                    if (line.contains(li.getName()) && li.isLocked()
                            && Main.config.getJsonConfig().get("mode").getAsString().equals("lock")) {
                        if (li.getName().contains(".exe")) {
                            Main.logger.logWarning("Process " + li.getName() + " is locked and will be terminated.");
                            Main.logger.logDebug("Killing process: " + li.getName());
                            killProcess(li.getName());
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Main.logger.logError("Failed to check locked programs: " + e.getMessage(), e);
        }
    }

    private static void killProcess(String processName) {
        Main.logger.logDebug("killProcess() called for: " + processName);
        Main.logger.logInfo("Attempting to kill process: " + processName);
        try {
            String command = "taskkill /F /IM " + processName;
            Process process = new ProcessBuilder(command.split(" ")).start();
            process.waitFor();
            Main.logger.logInfo("Successfully killed process: " + processName);
        } catch (IOException | InterruptedException e) {
            Main.logger.logError("Failed to kill process " + processName + ": " + e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Legacy method for backward compatibility - now just calls stopBlockerDaemon
    @Deprecated
    public static boolean killDaemon() {
        Main.logger.logDebug("killDaemon() called (deprecated - use stopBlockerDaemon instead)");
        return stopBlockerDaemon();
    }
}