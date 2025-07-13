package me.ghosthacks96.ghostsecure.utils.services.extras;

import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;
import me.ghosthacks96.ghostsecure.gui.extras.ShortcutResolver.ExecutableInfo;
import me.ghosthacks96.ghostsecure.gui.extras.ShortcutResolver;
import me.ghosthacks96.ghostsecure.utils.services.ServiceController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static me.ghosthacks96.ghostsecure.Main.programStorage;
import static me.ghosthacks96.ghostsecure.Main.logger;

public class ProgramManager {

    // Cache resolved executables to avoid repeated shortcut resolution
    private static final Map<String, ExecutableInfo> resolvedExecutables = new HashMap<>();

    /**
     * Check for running programs and terminate locked ones
     */
    public static void checkPrograms() {
        logger.logDebug("checkPrograms() called");
        try {
            if (ServiceController.isShuttingDown()) {
                logger.logDebug("Shutdown requested, stopping program check");
                return;
            }

            Process process = new ProcessBuilder("tasklist").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            // Get all program data from programStorage
            Map<String, Object> allData = programStorage.getAllData();

            while ((line = reader.readLine()) != null) {
                if (ServiceController.isShuttingDown()) {
                    logger.logDebug("Shutdown requested, stopping program check");
                    break;
                }

                // Check each program in storage
                for (Map.Entry<String, Object> entry : allData.entrySet()) {
                    if (entry.getValue() instanceof Map) {
                        Map<String, Object> itemData = (Map<String, Object>) entry.getValue();
                        if ("PROGRAM".equals(itemData.get("type"))) {
                            String path = (String) itemData.get("path");
                            String name = (String) itemData.get("name");
                            boolean locked = (boolean) itemData.get("locked");

                            if (locked) {
                                // Create a temporary LockedItem for compatibility with existing methods
                                LockedItem li = new LockedItem(path, name) {
                                    @Override
                                    public boolean isLocked() {
                                        return locked;
                                    }

                                    @Override
                                    public String getItemType() {
                                        return "PROGRAM";
                                    }

                                    @Override
                                    public LockedItem copy() {
                                        return this;
                                    }
                                };

                                ExecutableInfo execInfo = getExecutableInfo(li);
                                if (execInfo != null && line.contains(execInfo.getExecutableName())) {
                                    logger.logWarning("Process " + execInfo.getExecutableName() + " is locked and will be terminated.");
                                    logger.logDebug("Killing process: " + execInfo.getExecutableName());
                                    killProcess(execInfo.getExecutableName());
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.logError("Failed to check locked programs: " + e.getMessage(), e);
        }
    }

    /**
     * Get executable information for a locked item, with caching
     * @param item The locked item to get executable info for
     * @return ExecutableInfo or null if not a valid executable
     */
    private static ExecutableInfo getExecutableInfo(LockedItem item) {
        // Check if it's a program (has .exe or .lnk extension)
        String path = item.getPath();
        if (!isExecutableOrShortcut(path)) {
            return null;
        }

        // Check cache first
        if (resolvedExecutables.containsKey(path)) {
            return resolvedExecutables.get(path);
        }

        // Resolve the executable
        ExecutableInfo execInfo = ShortcutResolver.resolveExecutable(path);
        if (execInfo != null) {
            resolvedExecutables.put(path, execInfo);
            logger.logDebug("Cached executable info for: " + path + " -> " + execInfo.getExecutableName());
        }

        return execInfo;
    }

    /**
     * Check if a path is an executable or shortcut
     * @param path Path to check
     * @return true if it's .exe or .lnk file
     */
    private static boolean isExecutableOrShortcut(String path) {
        if (path == null) return false;
        String lowerPath = path.toLowerCase();
        return lowerPath.endsWith(".exe") || lowerPath.endsWith(".lnk");
    }

    /**
     * Kill a specific process by name
     * @param processName The name of the process to kill
     */
    public static void killProcess(String processName) {
        logger.logDebug("killProcess() called for: " + processName);
        logger.logInfo("Attempting to kill process: " + processName);
        try {
            String command = "taskkill /F /IM " + processName;
            Process process = new ProcessBuilder(command.split(" ")).start();
            process.waitFor();
            logger.logInfo("Successfully killed process: " + processName);
        } catch (IOException | InterruptedException e) {
            logger.logError("Failed to kill process " + processName + ": " + e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Check if a specific process is running
     * @param processName The name of the process to check
     * @return true if the process is running, false otherwise
     */
    public static boolean isProcessRunning(String processName) {
        logger.logDebug("isProcessRunning() called for: " + processName);
        try {
            Process process = new ProcessBuilder("tasklist").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.contains(processName)) {
                    logger.logDebug("Process " + processName + " is running");
                    return true;
                }
            }
            logger.logDebug("Process " + processName + " is not running");
            return false;
        } catch (Exception e) {
            logger.logError("Failed to check if process is running: " + processName + "; Error: " + e.getMessage(), e);
            return false;
        }
    }


    /**
     * Clear the executable cache (useful when items are added/removed)
     */
    public static void clearExecutableCache() {
        logger.logDebug("clearExecutableCache() called");
        resolvedExecutables.clear();
    }

    /**
     * Get executable name from a LockedItem (for display purposes)
     * @param item The locked item
     * @return The executable name or original name if not an executable
     */
    public static String getDisplayName(LockedItem item) {
        ExecutableInfo execInfo = getExecutableInfo(item);
        if (execInfo != null) {
            if (execInfo.isFromShortcut()) {
                return execInfo.getExecutableName() + " (from shortcut)";
            }
            return execInfo.getExecutableName();
        }
        return item.getName();
    }
}
