package me.ghosthacks96.ghostsecure.utils.services;

import me.ghosthacks96.ghostsecure.Main;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServiceController {

    private static final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;
    private static final int RESTART_DELAY_MS = 100;

    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> blockerTask;

    /**
     * Start the blocker daemon service
     */
    public static synchronized boolean startBlockerDaemon() {
        Main.logger.logDebug("startBlockerDaemon() called");

        if (isRunning.get()) {
            Main.logger.logInfo("Blocker daemon is already running.");
            return true;
        }

        Main.logger.logInfo("Attempting to start the blocker daemon.");
        try {
            initializeService();
            scheduleBlockerTask();

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
     */
    public static synchronized boolean stopBlockerDaemon() {
        Main.logger.logDebug("stopBlockerDaemon() called");

        if (!isRunning.get()) {
            Main.logger.logInfo("Blocker daemon is not running.");
            return true;
        }

        try {
            Main.logger.logInfo("Shutting down the blocker daemon.");

            isShuttingDown.set(true);
            cancelBlockerTask();
            unlockAllFolders();
            shutdownScheduler();

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
     */
    public static synchronized boolean restartBlockerDaemon() {
        Main.logger.logInfo("Restarting blocker daemon...");

        if (!stopBlockerDaemon()) {
            Main.logger.logError("Failed to stop blocker daemon, cannot restart");
            return false;
        }

        sleepQuietly(RESTART_DELAY_MS);
        return startBlockerDaemon();
    }

    /**
     * Check if the service is currently running
     */
    public static boolean isServiceRunning() {
        Main.logger.logDebug("isServiceRunning() called");

        try {
            boolean running = isRunning.get() &&
                    scheduler != null &&
                    !scheduler.isShutdown() &&
                    !scheduler.isTerminated();

            String status = running ? "Service is active." : "Service is not running.";
            Main.logger.logInfo(status);
            return running;

        } catch (Exception e) {
            Main.logger.logWarning("Error while checking service status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the current service status as a string
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
     * Check if the service is currently shutting down
     */
    public static boolean isShuttingDown() {
        return isShuttingDown.get();
    }

    // Private helper methods

    private static void initializeService() {
        isShuttingDown.set(false);
        scheduler = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "ServiceController-Thread");
            thread.setDaemon(true);
            return thread;
        });
        Main.logger.logDebug("Scheduler initialized");
    }

    private static void scheduleBlockerTask() {
        blockerTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (isShuttingDown.get()) {
                    Main.logger.logDebug("Shutdown requested, stopping blocker daemon tick");
                    return;
                }

                Main.logger.logDebug("Blocker daemon tick: checking programs and folders");
                ProgramManager.checkPrograms();
                FolderManager.checkFolders();

            } catch (Exception e) {
                Main.logger.logError("Exception in blocker daemon tick: " + e.getMessage(), e);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private static void cancelBlockerTask() {
        if (blockerTask != null && !blockerTask.isCancelled()) {
            blockerTask.cancel(true);
        }
    }

    private static void unlockAllFolders() {
        FolderManager.unlockAllFolders();
    }

    private static void shutdownScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    Main.logger.logDebug("Scheduler did not terminate in time, forcing shutdown");
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Main.logger.logDebug("InterruptedException during scheduler shutdown: " + e.getMessage(), e);
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void sleepQuietly(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Main.logger.logDebug("Interrupted during restart delay");
        }
    }
}