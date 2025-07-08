package me.ghosthacks96.ghostsecure.utils.services;

import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static me.ghosthacks96.ghostsecure.Main.config;
import static me.ghosthacks96.ghostsecure.Main.logger;

public class ProgramManager {

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

            while ((line = reader.readLine()) != null) {
                if (ServiceController.isShuttingDown()) {
                    logger.logDebug("Shutdown requested, stopping program check");
                    break;
                }

                for (LockedItem li : config.lockedItems) {
                    if (line.contains(li.getName()) && li.isLocked()) {
                        if (li.getName().contains(".exe")) {
                            logger.logWarning("Process " + li.getName() + " is locked and will be terminated.");
                            logger.logDebug("Killing process: " + li.getName());
                            killProcess(li.getName());
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.logError("Failed to check locked programs: " + e.getMessage(), e);
        }
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
     * Kill all locked processes
     */
    public static void killAllLockedProcesses() {
        logger.logDebug("killAllLockedProcesses() called");
        try {
            for (LockedItem li : config.lockedItems) {
                if (li.getName().contains(".exe") && li.isLocked()) {
                    if (isProcessRunning(li.getName())) {
                        logger.logInfo("Killing locked process: " + li.getName());
                        killProcess(li.getName());
                    }
                }
            }
        } catch (Exception e) {
            logger.logError("Failed to kill all locked processes: " + e.getMessage(), e);
        }
    }
}