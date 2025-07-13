package me.ghosthacks96.ghostsecure.itemTypes;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Factory class for creating appropriate LockedItem subclasses based on file types.
 * Provides utility methods to convert between the old LockedItem and new specialized classes.
 */
public class LockedItemFactory {

    // Common executable file extensions
    private static final Set<String> EXECUTABLE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".exe", ".msi", ".bat", ".cmd", ".com", ".scr", ".pif", ".vbs", ".js", ".jar",
            ".app", ".deb", ".rpm", ".dmg", ".pkg", ".run", ".sh", ".bin", ".out"
    ));

    // Additional program file extensions by platform
    private static final Set<String> WINDOWS_EXECUTABLES = new HashSet<>(Arrays.asList(
            ".exe", ".msi", ".bat", ".cmd", ".com", ".scr", ".pif", ".vbs", ".js", ".ps1"
    ));

    private static final Set<String> UNIX_EXECUTABLES = new HashSet<>(Arrays.asList(
            ".sh", ".bin", ".run", ".out", ".app", ".deb", ".rpm"
    ));

    private static final Set<String> CROSS_PLATFORM_EXECUTABLES = new HashSet<>(Arrays.asList(
            ".jar", ".py", ".rb", ".pl"
    ));

    /**
     * Creates a LockedItem subclass based on the file type at the given path.
     *
     * @param path the file system path
     * @param name the display name
     * @param isLocked the initial lock state
     * @return a LockedFolder or LockedProgram instance
     * @throws IllegalArgumentException if path or name is null or empty
     */
    public static LockedItem createLockedItem(String path, String name, boolean isLocked) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }

        try {
            Path filePath = Paths.get(path);

            // Check if path exists and determine type
            if (Files.exists(filePath)) {
                if (Files.isDirectory(filePath)) {
                    return new LockedFolder(path, name, isLocked);
                } else if (Files.isRegularFile(filePath)) {
                    // Check if it's an executable
                    if (isExecutableFile(path)) {
                        return new LockedProgram(path, name, isLocked);
                    } else {
                        // For non-executable files, treat as program if explicitly requested
                        // or if it has executable permissions
                        if (Files.isExecutable(filePath)) {
                            return new LockedProgram(path, name, isLocked);
                        } else {
                            // Default to folder for non-executable files
                            // You might want to create a separate LockedFile class in the future
                            return new LockedFolder(path, name, isLocked);
                        }
                    }
                }
            }

            // If file doesn't exist, determine type based on extension or path
            if (hasExecutableExtension(path)) {
                return new LockedProgram(path, name, isLocked);
            } else {
                // Default to folder for unknown types
                return new LockedFolder(path, name, isLocked);
            }

        } catch (Exception e) {
            // If we can't determine the type, default to folder
            return new LockedFolder(path, name, isLocked);
        }
    }

    /**
     * Creates a LockedItem subclass with unlocked state by default.
     *
     * @param path the file system path
     * @param name the display name
     * @return a LockedFolder or LockedProgram instance
     */
    public static LockedItem createLockedItem(String path, String name) {
        return createLockedItem(path, name, true);
    }

    /**
     * Creates a LockedItem subclass from a File object.
     *
     * @param file the File object
     * @param isLocked the initial lock state
     * @return a LockedFolder or LockedProgram instance
     * @throws IllegalArgumentException if file is null
     */
    public static LockedItem createLockedItem(File file, boolean isLocked) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }

        if (file.isDirectory()) {
            return new LockedFolder(file, isLocked);
        } else if (file.isFile()) {
            if (isExecutableFile(file.getAbsolutePath()) || file.canExecute()) {
                return new LockedProgram(file, isLocked);
            } else {
                // For non-executable files, default to folder
                // You might want to handle this differently based on your needs
                return new LockedFolder(file.getAbsolutePath(), file.getName(), isLocked);
            }
        } else {
            // Default to folder for unknown file types
            return new LockedFolder(file.getAbsolutePath(), file.getName(), isLocked);
        }
    }

    /**
     * Creates a LockedItem subclass from a File object with unlocked state.
     *
     * @param file the File object
     * @return a LockedFolder or LockedProgram instance
     */
    public static LockedItem createLockedItem(File file) {
        return createLockedItem(file, true);
    }

    /**
     * Converts an old LockedItem to the appropriate new subclass.
     * This method is useful for migrating existing data.
     *
     * @param oldItem the old LockedItem instance
     * @return a new LockedFolder or LockedProgram instance
     * @throws IllegalArgumentException if oldItem is null
     */
    public static LockedItem convertLegacyItem(LockedItem oldItem) {
        if (oldItem == null) {
            throw new IllegalArgumentException("LockedItem cannot be null");
        }

        // If it's already a specialized type, return as-is
        if (oldItem instanceof LockedFolder || oldItem instanceof LockedProgram) {
            return oldItem;
        }

        // Create new specialized instance
        LockedItem newItem = createLockedItem(oldItem.getPath(), oldItem.getName(), oldItem.isLocked());
        newItem.setSelected(oldItem.isSelected());

        return newItem;
    }

    /**
     * Checks if a file path represents an executable file based on extension.
     *
     * @param filePath the file path
     * @return true if the file has an executable extension
     */
    public static boolean isExecutableFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }

        return hasExecutableExtension(filePath);
    }

    /**
     * Checks if a file path has an executable extension.
     *
     * @param filePath the file path
     * @return true if the file has an executable extension
     */
    private static boolean hasExecutableExtension(String filePath) {
        String extension = getFileExtension(filePath).toLowerCase();

        if (extension.isEmpty()) {
            return false;
        }

        // Check against all executable extensions
        if (EXECUTABLE_EXTENSIONS.contains(extension)) {
            return true;
        }

        // Platform-specific checks
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("windows")) {
            return WINDOWS_EXECUTABLES.contains(extension);
        } else if (osName.contains("mac") || osName.contains("nix") || osName.contains("nux")) {
            return UNIX_EXECUTABLES.contains(extension);
        }

        // Cross-platform executables
        return CROSS_PLATFORM_EXECUTABLES.contains(extension);
    }

    /**
     * Gets the file extension from a file path.
     *
     * @param filePath the file path
     * @return the file extension including the dot, or empty string if no extension
     */
    private static String getFileExtension(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return "";
        }

        // Get just the filename from the path
        String fileName = Paths.get(filePath).getFileName().toString();

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex);
        }
        return "";
    }

    /**
     * Creates a LockedProgram specifically, regardless of file type detection.
     *
     * @param path the file system path
     * @param name the display name
     * @param isLocked the initial lock state
     * @return a LockedProgram instance
     */
    public static LockedProgram createLockedProgram(String path, String name, boolean isLocked) {
        return new LockedProgram(path, name, isLocked);
    }

    /**
     * Creates a LockedProgram specifically, with unlocked state by default.
     *
     * @param path the file system path
     * @param name the display name
     * @return a LockedProgram instance
     */
    public static LockedProgram createLockedProgram(String path, String name) {
        return new LockedProgram(path, name, true);
    }

    /**
     * Creates a LockedFolder specifically, regardless of file type detection.
     *
     * @param path the file system path
     * @param name the display name
     * @param isLocked the initial lock state
     * @return a LockedFolder instance
     */
    public static LockedFolder createLockedFolder(String path, String name, boolean isLocked) {
        return new LockedFolder(path, name, isLocked);
    }

    /**
     * Creates a LockedFolder specifically, with unlocked state by default.
     *
     * @param path the file system path
     * @param name the display name
     * @return a LockedFolder instance
     */
    public static LockedFolder createLockedFolder(String path, String name) {
        return new LockedFolder(path, name, true);
    }

    /**
     * Determines the item type based on a file path.
     *
     * @param path the file system path
     * @return "PROGRAM", "FOLDER", or "UNKNOWN"
     */
    public static String determineItemType(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "UNKNOWN";
        }

        try {
            Path filePath = Paths.get(path);

            if (Files.exists(filePath)) {
                if (Files.isDirectory(filePath)) {
                    return "FOLDER";
                } else if (Files.isRegularFile(filePath)) {
                    if (isExecutableFile(path) || Files.isExecutable(filePath)) {
                        return "PROGRAM";
                    } else {
                        return "FOLDER"; // Default for non-executable files
                    }
                }
            } else {
                // File doesn't exist, guess based on extension
                if (hasExecutableExtension(path)) {
                    return "PROGRAM";
                } else {
                    return "FOLDER";
                }
            }
        } catch (Exception e) {
            // If we can't determine, default to folder
            return "FOLDER";
        }

        return "UNKNOWN";
    }
}