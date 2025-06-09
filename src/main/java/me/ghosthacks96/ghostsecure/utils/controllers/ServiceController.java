package me.ghosthacks96.ghostsecure.utils.controllers;

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
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ServiceController {

    static boolean shutDown = false;

    private static ScheduledExecutorService scheduler;

    // Start a daemon scheduler to monitor and kill blocked processes
    public static void startBlockerDaemon() {
        Main.logger.logInfo("Attempting to start the blocker daemon.");
        try {
            // Initialize the scheduler with two threads, one for each task
            scheduler = Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable);
                thread.setDaemon(true); // Mark threads as daemon
                return thread;
            });
            // Schedule the config loading task
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    checkPrograms();
                    checkFolders();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 0, 1, TimeUnit.SECONDS); // Run immediately, then every 1 second

            Main.logger.logInfo("Blocker daemon started successfully.");
        } catch (Exception e) {
            Main.logger.logError("Failed to start the blocker daemon: " + e.getMessage());
        }
    }

    // Stop daemon scheduler
    public static void stopBlockerDaemon() {
        if (scheduler != null && !scheduler.isShutdown()) {
            Main.logger.logInfo("Shutting down the blocker daemon.");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
    }

    private static void checkFolders() {
        try {
            for (LockedItem li : Main.lockedItems) {
                Path folderPath = Paths.get(li.getPath());

                // Check if the folder exists
                if (!Files.exists(folderPath)) {
                    Main.logger.logWarning("Folder does not exist: " + li.getPath());
                    continue;
                }
                if (li.getName().contains(".exe")) continue;

                // Apply permissions recursively to the folder and all its contents
                applyPermissionsRecursively(folderPath, li);
            }

        } catch (Exception e) {
            Main.logger.logError("Failed to check locked folders: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void applyPermissionsRecursively(Path rootPath, LockedItem li) {
        try {
            // If it's a directory, we need to process contents first (before locking the root)
            if (Files.isDirectory(rootPath)) {
                try (Stream<Path> paths = Files.walk(rootPath)) {
                    // Collect all paths and sort them by depth (deepest first for locking, shallowest first for unlocking)
                    List<Path> allPaths = paths.filter(path -> !path.equals(rootPath))
                            .collect(java.util.stream.Collectors.toList());

                    if (li.isLocked() && Main.config.getJsonConfig().get("mode").getAsString().equals("lock")) {
                        // When locking: Start from deepest files/folders first, then work up to root
                        allPaths.stream()
                                .sorted((p1, p2) -> Integer.compare(p2.getNameCount(), p1.getNameCount()))
                                .forEach(path -> {
                                    try {
                                        applyPermissionsToPath(path, li);
                                    } catch (Exception e) {
                                        Main.logger.logError("Failed to apply permissions to: " + path + "; Error: " + e.getMessage());
                                    }
                                });
                    } else {
                        // When unlocking: Start from shallowest (closest to root) first
                        allPaths.stream()
                                .sorted((p1, p2) -> Integer.compare(p1.getNameCount(), p2.getNameCount()))
                                .forEach(path -> {
                                    try {
                                        applyPermissionsToPath(path, li);
                                    } catch (Exception e) {
                                        Main.logger.logError("Failed to apply permissions to: " + path + "; Error: " + e.getMessage());
                                    }
                                });
                    }
                } catch (IOException e) {
                    Main.logger.logError("Failed to walk directory tree for: " + rootPath + "; Error: " + e.getMessage());
                }
            }

            // Apply permissions to the root folder/file last (for locking) or first (for unlocking)
            applyPermissionsToPath(rootPath, li);

        } catch (Exception e) {
            Main.logger.logError("Failed to apply permissions recursively to: " + rootPath + "; Error: " + e.getMessage());
        }
    }

    private static void applyPermissionsToPath(Path path, LockedItem li) {
        try {
            AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);

            if (aclView == null) {
                Main.logger.logError("ACL view not supported on this file system for: " + path);
                return;
            }

            if (li.isLocked() && Main.config.getJsonConfig().get("mode").getAsString().equals("lock")) {
                // Deny all access to the path
                AclEntry denyAllAccess = AclEntry.newBuilder()
                        .setType(AclEntryType.DENY)
                        .setPrincipal(FileSystems.getDefault()
                                .getUserPrincipalLookupService()
                                .lookupPrincipalByName("Everyone"))
                        .setPermissions(AclEntryPermission.values()) // Deny all available permissions
                        .build();

                aclView.setAcl(List.of(denyAllAccess));

                if (Files.isDirectory(path)) {
                    Main.logger.logInfo("Applied DENY permissions to directory: " + path);
                } else {
                    Main.logger.logInfo("Applied DENY permissions to file: " + path);
                }

            } else if (!li.isLocked() || Main.config.getJsonConfig().get("mode").getAsString().equals("unlock") || shutDown) {

                if (shutDown) {
                    System.out.println("Shutting down service. unlocking: " + path);
                }
                // Restore access by granting all permissions
                AclEntry grantAllAccess = AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(FileSystems.getDefault()
                                .getUserPrincipalLookupService()
                                .lookupPrincipalByName("Everyone"))
                        .setPermissions(AclEntryPermission.values()) // Allow all available permissions
                        .build();

                aclView.setAcl(List.of(grantAllAccess));

                if (Files.isDirectory(path)) {
                    Main.logger.logInfo("Applied ALLOW permissions to directory: " + path);
                } else {
                    Main.logger.logInfo("Applied ALLOW permissions to file: " + path);
                }
            }

        } catch (Exception aclError) {
            Main.logger.logError("Failed to modify permissions for: " + path + "; Error: " + aclError.getMessage());
        }
    }

    private static void checkPrograms() {
        try {
            //programs first
            Process process = new ProcessBuilder("tasklist").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {

                for (LockedItem li : Main.lockedItems) {
                    if (line.contains(li.getName()) && li.isLocked()
                            && Main.config.getJsonConfig().get("mode").getAsString().equals("lock")) {
                        if (li.getName().contains(".exe")) {
                            Main.logger.logWarning("Process " + li.getName() + " is locked and will be terminated.");
                            killProcess(li.getName());
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Main.logger.logError("Failed to check locked programs: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // Kill a specific process by name
    private static void killProcess(String processName) {
        Main.logger.logInfo("Attempting to kill process: " + processName);
        try {
			String command = "taskkill /F /IM " + processName;
			Process process = new ProcessBuilder(command.split(" ")).start();
            process.waitFor();
            Main.logger.logInfo("Successfully killed process: " + processName);
        } catch (IOException | InterruptedException e) {
            Main.logger.logError("Failed to kill process " + processName + ": " + e.getMessage());
        }
    }

    public static boolean isServiceRunning() {
        try {
            if (scheduler != null && !scheduler.isShutdown() && !scheduler.isTerminated()
                    && Main.config.getJsonConfig().get("mode").getAsString().equals("lock")) {
                Main.logger.logInfo("Service is active and running in locking mode.");
                return true;
            }
        } catch (Exception e) {
            Main.logger.logWarning("Error while checking service status: " + e.getMessage());
        }
        Main.logger.logInfo("Service is not running.");
        return false;
    }


    public static boolean killDaemon() {
        try {
            System.out.println("Attempting to stop the blocker daemon. Unlocking Folders");
            shutDown = true;
            checkFolders();

            while(shutDown){}

            stopBlockerDaemon();
            return false;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}