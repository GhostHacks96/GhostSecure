package me.ghosthacks96.ghostsecure.gui.extras;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static me.ghosthacks96.ghostsecure.Main.logger;

/**
 * Utility class for resolving Windows shortcuts (.lnk files) to their target executables
 */
public class ShortcutResolver {

    /**
     * Resolve a .lnk shortcut file to its target executable path
     * @param shortcutPath Path to the .lnk file
     * @return Path to the target executable, or null if resolution fails
     */
    public static String resolveShortcut(String shortcutPath) {
        logger.logDebug("resolveShortcut() called for: " + shortcutPath);

        if (!shortcutPath.toLowerCase().endsWith(".lnk")) {
            logger.logDebug("Not a shortcut file: " + shortcutPath);
            return null;
        }

        try {
            // Use PowerShell to resolve the shortcut
            String powershellCommand = String.format(
                    "powershell.exe -Command \"$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut('%s'); $s.TargetPath\"",
                    shortcutPath.replace("\"", "\\\"")
            );

            Process process = new ProcessBuilder("cmd", "/c", powershellCommand).start();

            // Read the output
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line.trim());
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.logError("PowerShell command failed with exit code: " + exitCode);
                return null;
            }

            String targetPath = output.toString().trim();
            if (targetPath.isEmpty()) {
                logger.logWarning("Empty target path resolved from shortcut: " + shortcutPath);
                return null;
            }

            // Verify the target exists
            File targetFile = new File(targetPath);
            if (!targetFile.exists()) {
                logger.logWarning("Target file does not exist: " + targetPath);
                return null;
            }

            logger.logInfo("Resolved shortcut " + shortcutPath + " to: " + targetPath);
            return targetPath;

        } catch (IOException | InterruptedException e) {
            logger.logError("Failed to resolve shortcut " + shortcutPath + ": " + e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    /**
     * Extract the executable name from a full path
     * @param executablePath Full path to the executable
     * @return Just the executable name (e.g., "notepad.exe")
     */
    public static String getExecutableName(String executablePath) {
        if (executablePath == null || executablePath.isEmpty()) {
            return null;
        }

        Path path = Paths.get(executablePath);
        String fileName = path.getFileName().toString();

        logger.logDebug("Extracted executable name: " + fileName + " from path: " + executablePath);
        return fileName;
    }

    /**
     * Check if a file is a Windows shortcut
     * @param filePath Path to check
     * @return true if it's a .lnk file, false otherwise
     */
    public static boolean isShortcut(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".lnk");
    }

    /**
     * Resolve a path that might be a shortcut or direct executable
     * @param path Path to resolve (could be .lnk or .exe)
     * @return ExecutableInfo containing both the executable name and full path
     */
    public static ExecutableInfo resolveExecutable(String path) {
        logger.logDebug("resolveExecutable() called for: " + path);

        if (isShortcut(path)) {
            // It's a shortcut - resolve to target
            String targetPath = resolveShortcut(path);
            if (targetPath != null) {
                String executableName = getExecutableName(targetPath);
                return new ExecutableInfo(executableName, targetPath, path);
            }
            return null;
        } else {
            // It's a direct executable path
            String executableName = getExecutableName(path);
            return new ExecutableInfo(executableName, path, null);
        }
    }

    /**
     * Data class to hold executable information
     */
    public static class ExecutableInfo {
        private final String executableName;  // e.g., "notepad.exe"
        private final String executablePath;  // e.g., "C:\Windows\System32\notepad.exe"
        private final String shortcutPath;    // e.g., "C:\Users\...\Notepad.lnk" (null if not from shortcut)

        public ExecutableInfo(String executableName, String executablePath, String shortcutPath) {
            this.executableName = executableName;
            this.executablePath = executablePath;
            this.shortcutPath = shortcutPath;
        }

        public String getExecutableName() {
            return executableName;
        }

        public String getExecutablePath() {
            return executablePath;
        }

        public String getShortcutPath() {
            return shortcutPath;
        }

        public boolean isFromShortcut() {
            return shortcutPath != null;
        }

        @Override
        public String toString() {
            return "ExecutableInfo{" +
                    "executableName='" + executableName + '\'' +
                    ", executablePath='" + executablePath + '\'' +
                    ", shortcutPath='" + shortcutPath + '\'' +
                    '}';
        }
    }
}