package me.ghosthacks96.ghostsecure.gui.extras;

import javafx.stage.FileChooser;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static me.ghosthacks96.ghostsecure.Main.logger;

/**
 * Helper class for file selection in the GUI
 */
public class FileSelectionHelper {

    /**
     * Create a file chooser for selecting programs (executables and shortcuts)
     * @return Configured FileChooser
     */
    public static FileChooser createProgramFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Program or Shortcut");

        // Add extension filters
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Programs and Shortcuts", "*.exe", "*.lnk"),
                new FileChooser.ExtensionFilter("Executable Files", "*.exe"),
                new FileChooser.ExtensionFilter("Shortcut Files", "*.lnk"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        // Set initial directory to common program locations
        setInitialDirectory(fileChooser);

        return fileChooser;
    }

    /**
     * Create a file chooser for selecting multiple programs
     * @return Configured FileChooser
     */
    public static FileChooser createMultipleProgramFileChooser() {
        FileChooser fileChooser = createProgramFileChooser();
        fileChooser.setTitle("Select Programs or Shortcuts");
        return fileChooser;
    }

    /**
     * Set initial directory for file chooser to common program locations
     * @param fileChooser FileChooser to configure
     */
    private static void setInitialDirectory(FileChooser fileChooser) {
        List<String> commonPaths = Arrays.asList(
                System.getProperty("user.home") + "\\Desktop",
                System.getenv("PROGRAMFILES"),
                System.getenv("PROGRAMFILES(X86)"),
                System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs",
                System.getProperty("user.home") + "\\AppData\\Roaming\\Microsoft\\Windows\\Start Menu\\Programs"
        );

        for (String path : commonPaths) {
            if (path != null) {
                File dir = new File(path);
                if (dir.exists() && dir.isDirectory()) {
                    fileChooser.setInitialDirectory(dir);
                    break;
                }
            }
        }
    }

    /**
     * Process selected file(s) and resolve shortcuts
     * @param selectedFiles List of selected files
     * @return ProcessedFileInfo containing resolved information
     */
    public static ProcessedFileInfo processSelectedFiles(List<File> selectedFiles) {
        ProcessedFileInfo result = new ProcessedFileInfo();

        for (File file : selectedFiles) {
            String filePath = file.getAbsolutePath();

            if (ShortcutResolver.isShortcut(filePath)) {
                // It's a shortcut - resolve it
                ShortcutResolver.ExecutableInfo execInfo = ShortcutResolver.resolveExecutable(filePath);
                if (execInfo != null) {
                    result.addResolvedFile(filePath, execInfo.getExecutableName(), execInfo.getExecutablePath());
                    logger.logInfo("Resolved shortcut: " + filePath + " -> " + execInfo.getExecutablePath());
                } else {
                    result.addFailedFile(filePath, "Failed to resolve shortcut");
                    logger.logError("Failed to resolve shortcut: " + filePath);
                }
            } else if (filePath.toLowerCase().endsWith(".exe")) {
                // It's a direct executable
                result.addResolvedFile(filePath, file.getName(), filePath);
                logger.logInfo("Added executable: " + filePath);
            } else {
                // Not a supported file type
                result.addFailedFile(filePath, "Unsupported file type");
                logger.logWarning("Unsupported file type: " + filePath);
            }
        }

        return result;
    }

    /**
     * Data class to hold processed file information
     */
    public static class ProcessedFileInfo {
        private final java.util.List<FileInfo> resolvedFiles = new java.util.ArrayList<>();
        private final java.util.List<FailedFile> failedFiles = new java.util.ArrayList<>();

        public void addResolvedFile(String originalPath, String executableName, String executablePath) {
            resolvedFiles.add(new FileInfo(originalPath, executableName, executablePath));
        }

        public void addFailedFile(String path, String reason) {
            failedFiles.add(new FailedFile(path, reason));
        }

        public List<FileInfo> getResolvedFiles() {
            return resolvedFiles;
        }

        public List<FailedFile> getFailedFiles() {
            return failedFiles;
        }

        public boolean hasFailures() {
            return !failedFiles.isEmpty();
        }

        public static class FileInfo {
            private final String originalPath;
            private final String executableName;
            private final String executablePath;

            public FileInfo(String originalPath, String executableName, String executablePath) {
                this.originalPath = originalPath;
                this.executableName = executableName;
                this.executablePath = executablePath;
            }

            public String getOriginalPath() { return originalPath; }
            public String getExecutableName() { return executableName; }
            public String getExecutablePath() { return executablePath; }
        }

        public static class FailedFile {
            private final String path;
            private final String reason;

            public FailedFile(String path, String reason) {
                this.path = path;
                this.reason = reason;
            }

            public String getPath() { return path; }
            public String getReason() { return reason; }
        }
    }
}