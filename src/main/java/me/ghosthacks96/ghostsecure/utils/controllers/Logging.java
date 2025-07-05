package me.ghosthacks96.ghostsecure.utils.controllers;

import me.ghosthacks96.ghostsecure.Main;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.zip.*;

@SuppressWarnings("CallToPrintStackTrace")
public class Logging {

    private static final String LOG_DIR = Main.APP_DATA_PATH + "logs/";
    private static final String CURRENT_LOG_FILE = LOG_DIR + "latest.log";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_LOG_SIZE = 10 * 1024 * 1024; // 10MB max log file size
    private static final int MAX_ARCHIVED_LOGS = 30; // Keep 30 days of logs

    private final ExecutorService executor;
    private static ScheduledExecutorService scheduler;
    public static DebugConsole debugConsole;
    private volatile boolean isShuttingDown = false;

    public Logging() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LoggingThread");
            t.setDaemon(true);
            return t;
        });
        ensureLogDirectoryExists();
        setupMidnightLogZipTask();
        logInfo("Logging system initialized");
    }

    public void logInfo(String message) {
        log(LogLevel.INFO, message, null);
    }

    public void logWarning(String message) {
        log(LogLevel.WARNING, message, null);
    }


    public void logError(String message) {
        log(LogLevel.ERROR, message, null);
    }

    public void logError(String message, Throwable throwable) {
        log(LogLevel.ERROR, message, throwable);
    }

    public void logException(Throwable throwable) {
        log(LogLevel.ERROR, "Exception occurred", throwable);
    }

    public void logDebug(String message) {
        if (Main.DEBUG_MODE) {
            log(LogLevel.DEBUG, message, null);
        }
    }

    public void logDebug(String message, Object... args) {
        if (Main.DEBUG_MODE) {
            logDebug(String.format(message, args));
        }
    }

    // Add this to your log() method in Logging.java, replace the existing log method:

    private void log(LogLevel level, String message, Throwable throwable) {
        if (isShuttingDown) {
            return;
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String consoleLogEntry = String.format("[%s] [%s] %s%n", timestamp, level.getConsoleDisplayName(), message);
        String fileLogEntry = String.format("[%s] [%s] %s%n", timestamp, level.getFileDisplayName(), message);

        // Add stack trace if throwable is provided
        if (throwable != null) {
            String stackTrace = formatStackTrace(throwable);
            consoleLogEntry += stackTrace;
            fileLogEntry += stackTrace;
        }

        // Show in debug console if enabled
        if (Main.DEBUG_MODE) {
            if (debugConsole == null) {
                try {
                    javafx.application.Platform.runLater(() -> {
                        try {
                            debugConsole = DebugConsole.getInstance();
                            debugConsole.showConsole();
                        } catch (Exception e) {
                            System.err.println("Failed to initialize debug console: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    // Fallback if Platform.runLater fails
                    System.err.println("Failed to schedule debug console initialization: " + e.getMessage());
                }
            }

            // Send message to debug console
            if (debugConsole != null) {
                String messageForConsole = message;
                if (throwable != null) {
                    messageForConsole += "\n" + formatStackTrace(throwable);
                }
                debugConsole.addLogMessage(level.name(), messageForConsole, timestamp);
            }
        }

        // Print to system console (this will still go to terminal/IDE console)
        System.out.print(consoleLogEntry);

        // Submit to file writing thread
        String finalFileLogEntry = fileLogEntry;
        executor.submit(() -> writeToLogFile(finalFileLogEntry));
    }

    private String formatStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Exception: ").append(throwable.getClass().getSimpleName())
                .append(": ").append(throwable.getMessage()).append("\n");

        StackTraceElement[] elements = throwable.getStackTrace();
        for (StackTraceElement element : elements) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        // Handle nested exceptions (caused by)
        Throwable cause = throwable.getCause();
        while (cause != null) {
            sb.append("Caused by: ").append(cause.getClass().getSimpleName())
                    .append(": ").append(cause.getMessage()).append("\n");

            StackTraceElement[] causeElements = cause.getStackTrace();
            for (StackTraceElement element : causeElements) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }
            cause = cause.getCause();
        }

        return sb.toString();
    }

    private void writeToLogFile(String logEntry) {
        try {
            // Check if log rotation is needed
            if (needsRotation()) {
                rotateLog();
            }

            // Append to current log file
            try (FileWriter writer = new FileWriter(CURRENT_LOG_FILE, true);
                 BufferedWriter bufferedWriter = new BufferedWriter(writer)) {
                bufferedWriter.write(logEntry);
                bufferedWriter.flush();
            }
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean needsRotation() {
        try {
            Path logPath = Paths.get(CURRENT_LOG_FILE);
            return Files.exists(logPath) && Files.size(logPath) > MAX_LOG_SIZE;
        } catch (IOException e) {
            System.err.println("Error checking log file size: " + e.getMessage());
            return false;
        }
    }

    private void rotateLog() {
        String newLogFileName = LOG_DIR + LocalDateTime.now().format(DATE_FORMAT) + ".log";
        Path currentPath = Paths.get(CURRENT_LOG_FILE);
        Path newPath = Paths.get(newLogFileName);

        try {
            if (Files.exists(currentPath)) {
                // If target file already exists, append timestamp to make it unique
                if (Files.exists(newPath)) {
                    String uniqueName = LOG_DIR + LocalDateTime.now().format(TIMESTAMP_FORMAT.withZone(java.time.ZoneId.systemDefault())).replace(":", "-") + ".log";
                    newPath = Paths.get(uniqueName);
                }
                Files.move(currentPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                logInfo("Log rotated to: " + newPath.getFileName());
            }
        } catch (IOException e) {
            System.err.println("Failed to rotate log file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void ensureLogDirectoryExists() {
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create log directory: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create log directory", e);
        }
    }

    public void onShutdown() {
        isShuttingDown = true;

        try {
            logInfo("Shutting down logging system...");

            // Rotate current log
            rotateLogOnShutdown();

            // Shutdown executor with timeout
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logWarning("Logging executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Logging executor did not terminate");
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted during logging shutdown");
        } catch (Exception e) {
            System.err.println("Error during logging shutdown: " + e.getMessage());
            e.printStackTrace();
        }

        // Shutdown scheduler
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
        }
    }

    private void rotateLogOnShutdown() {
        Path currentPath = Paths.get(CURRENT_LOG_FILE);
        if (!Files.exists(currentPath)) {
            return;
        }

        String newLogFileName = LOG_DIR + LocalDateTime.now().format(DATE_FORMAT) + ".log";
        try {
            Files.move(currentPath, Paths.get(newLogFileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to rotate log on shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupMidnightLogZipTask() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "LogSchedulerThread");
                t.setDaemon(true);
                return t;
            });
        }

        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (isFirstDayOfMonth()) {
                    zipMonthlyLogs();
                }
                cleanupOldLogs();
            } catch (Exception e) {
                System.err.println("Error in scheduled log maintenance: " + e.getMessage());
                e.printStackTrace();
            }
        }, computeInitialDelay(), 24, TimeUnit.HOURS);
    }

    private long computeInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().atStartOfDay().plusDays(1);
        return java.time.Duration.between(now, midnight).toSeconds();
    }

    private boolean isFirstDayOfMonth() {
        return LocalDateTime.now().getDayOfMonth() == 1;
    }

    private void zipMonthlyLogs() {
        LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1);
        String month = lastMonth.format(DateTimeFormatter.ofPattern("MM-yyyy"));
        String zipFileName = LOG_DIR + "logs-" + month + ".zip";

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFileName))) {
            Files.list(Paths.get(LOG_DIR))
                    .filter(path -> isMonthlyLog(path, lastMonth))
                    .forEach(path -> {
                        try {
                            String entryName = path.getFileName().toString();
                            zos.putNextEntry(new ZipEntry(entryName));
                            Files.copy(path, zos);
                            zos.closeEntry();
                            Files.delete(path);
                            logInfo("Archived and deleted log file: " + entryName);
                        } catch (IOException e) {
                            logError("Failed to archive log file: " + path.getFileName(), e);
                        }
                    });
            logInfo("Monthly log archive created: " + zipFileName);
        } catch (Exception e) {
            logError("Failed to create monthly log archive", e);
        }
    }

    private boolean isMonthlyLog(Path path, LocalDateTime month) {
        String fileName = path.getFileName().toString();
        if (!fileName.endsWith(".log") || fileName.equals("latest.log")) {
            return false;
        }

        try {
            // Parse the date from filename (dd-MM-yy.log format)
            String dateStr = fileName.substring(0, fileName.length() - 4); // Remove .log
            LocalDateTime fileDate = LocalDateTime.parse(dateStr + " 00:00:00",
                    DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss"));

            return fileDate.getMonth() == month.getMonth() &&
                    fileDate.getYear() == month.getYear();
        } catch (Exception e) {
            // If we can't parse the date, don't include it in monthly archive
            return false;
        }
    }

    private void cleanupOldLogs() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(MAX_ARCHIVED_LOGS);

            Files.list(Paths.get(LOG_DIR))
                    .filter(path -> path.toString().endsWith(".log"))
                    .filter(path -> !path.getFileName().toString().equals("latest.log"))
                    .filter(path -> isOlderThan(path, cutoffDate))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            logInfo("Deleted old log file: " + path.getFileName());
                        } catch (IOException e) {
                            logError("Failed to delete old log file: " + path.getFileName(), e);
                        }
                    });
        } catch (Exception e) {
            logError("Error during log cleanup", e);
        }
    }

    private boolean isOlderThan(Path path, LocalDateTime cutoffDate) {
        try {
            return Files.getLastModifiedTime(path).toInstant()
                    .isBefore(cutoffDate.atZone(java.time.ZoneId.systemDefault()).toInstant());
        } catch (IOException e) {
            return false;
        }
    }

    public void switchDebugMode(boolean debugEnabled) {
        Main.DEBUG_MODE = debugEnabled;
        if (debugEnabled) {
            logInfo("Debug mode enabled");
            if (debugConsole == null) {
                debugConsole = DebugConsole.getInstance();
                debugConsole.showConsole();
            } else {
                debugConsole.showConsole();
            }
        } else {
            logInfo("Debug mode disabled");
            if (debugConsole != null) {
                debugConsole.hideConsole();
            }
        }
    }

    private enum LogLevel {
        DEBUG("🔍 [DEBUG]", "[DBG]"),
        INFO("ℹ️ [INFO]", "[INF]"),
        WARNING("⚠️ [WARNING]", "[WRN]"),
        ERROR("❌ [ERROR]", "[ERR]");

        private final String fileDisplayName;   // For log files and debug console (with emojis)
        private final String consoleDisplayName; // For terminal/console (without emojis)

        LogLevel(String fileDisplayName, String consoleDisplayName) {
            this.fileDisplayName = fileDisplayName;
            this.consoleDisplayName = consoleDisplayName;
        }

        public String getFileDisplayName() {
            return fileDisplayName;
        }

        public String getConsoleDisplayName() {
            return consoleDisplayName;
        }
    }
}

