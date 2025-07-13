package me.ghosthacks96.ghostsecure.itemTypes;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Represents a locked folder with additional folder-specific properties.
 * Extends LockedItem to provide folder-specific functionality.
 */
public class LockedFolder extends LockedItem {

    private final BooleanProperty isHidden;
    private final BooleanProperty isEmpty;
    private long folderSize;
    private int fileCount;

    /**
     * Creates a new LockedFolder with the specified properties.
     *
     * @param path the folder path (cannot be null or empty)
     * @param name the display name (cannot be null or empty)
     * @param isLocked the initial lock state
     * @throws IllegalArgumentException if path or name is null or empty
     */
    public LockedFolder(String path, String name, boolean isLocked) {
        super(path, name, isLocked);
        this.isHidden = new SimpleBooleanProperty(false);
        this.isEmpty = new SimpleBooleanProperty(true);
        this.folderSize = 0;
        this.fileCount = 0;

        // Initialize folder properties
        updateFolderProperties();
    }

    /**
     * Creates a new LockedFolder with unlocked state by default.
     *
     * @param path the folder path (cannot be null or empty)
     * @param name the display name (cannot be null or empty)
     * @throws IllegalArgumentException if path or name is null or empty
     */
    public LockedFolder(String path, String name) {
        this(path, name, false);
    }

    /**
     * Creates a LockedFolder from a File object.
     *
     * @param folder the folder File object
     * @param isLocked the initial lock state
     * @throws IllegalArgumentException if folder is null or not a directory
     */
    public LockedFolder(File folder, boolean isLocked) {
        this(folder.getAbsolutePath(), folder.getName(), isLocked);
        if (!folder.isDirectory()) {
            throw new IllegalArgumentException("File must be a directory");
        }
    }

    /**
     * Creates a LockedFolder from a File object with unlocked state.
     *
     * @param folder the folder File object
     * @throws IllegalArgumentException if folder is null or not a directory
     */
    public LockedFolder(File folder) {
        this(folder, false);
    }

    /**
     * Updates folder-specific properties by examining the filesystem.
     */
    private void updateFolderProperties() {
        try {
            Path folderPath = Paths.get(getPath());

            if (Files.exists(folderPath)) {
                // Check if folder is hidden
                boolean hidden = Files.isHidden(folderPath) || getName().startsWith(".");
                setHidden(hidden);

                // Count files and calculate size
                File folder = folderPath.toFile();
                if (folder.isDirectory()) {
                    File[] files = folder.listFiles();
                    if (files != null) {
                        fileCount = files.length;
                        setEmpty(fileCount == 0);

                        // Calculate folder size (non-recursive for performance)
                        long size = 0;
                        for (File file : files) {
                            if (file.isFile()) {
                                size += file.length();
                            }
                        }
                        folderSize = size;
                    } else {
                        fileCount = 0;
                        folderSize = 0;
                        setEmpty(true);
                    }
                }
            }
        } catch (Exception e) {
            // If we can't access the folder, assume it's not empty and not hidden
            setEmpty(false);
            setHidden(false);
        }
    }

    /**
     * Refreshes the folder properties by re-examining the filesystem.
     */
    public void refreshProperties() {
        updateFolderProperties();
    }

    // Hidden property methods
    public boolean isHidden() {
        return isHidden.get();
    }

    public BooleanProperty isHiddenProperty() {
        return isHidden;
    }

    public void setHidden(boolean hidden) {
        this.isHidden.set(hidden);
    }

    // Empty property methods
    public boolean isEmpty() {
        return isEmpty.get();
    }

    public ReadOnlyBooleanProperty isEmptyProperty() {
        return isEmpty;
    }

    private void setEmpty(boolean empty) {
        this.isEmpty.set(empty);
    }

    // Folder size methods
    public long getFolderSize() {
        return folderSize;
    }

    /**
     * Returns the folder size formatted as a human-readable string.
     *
     * @return formatted size string (e.g., "1.5 MB", "256 KB")
     */
    public String getFormattedSize() {
        return formatFileSize(folderSize);
    }

    // File count methods
    public int getFileCount() {
        return fileCount;
    }

    /**
     * Returns a description of the folder contents.
     *
     * @return content description (e.g., "5 items", "Empty folder")
     */
    public String getContentDescription() {
        if (fileCount == 0) {
            return "Empty folder";
        } else if (fileCount == 1) {
            return "1 item";
        } else {
            return fileCount + " items";
        }
    }

    /**
     * Checks if the folder exists on the filesystem.
     *
     * @return true if the folder exists and is accessible
     */
    public boolean exists() {
        try {
            Path folderPath = Paths.get(getPath());
            return Files.exists(folderPath) && Files.isDirectory(folderPath);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the folder is accessible (can be read).
     *
     * @return true if the folder is accessible
     */
    public boolean isAccessible() {
        try {
            Path folderPath = Paths.get(getPath());
            return Files.isReadable(folderPath);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Formats a file size in bytes to a human-readable string.
     *
     * @param size the size in bytes
     * @return formatted size string
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    @Override
    public String getItemType() {
        return "FOLDER";
    }

    @Override
    public LockedFolder copy() {
        LockedFolder copy = new LockedFolder(getPath(), getName(), isLocked());
        copy.setSelected(isSelected());
        copy.setHidden(isHidden());
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        if (!(obj instanceof LockedFolder)) return false;

        LockedFolder other = (LockedFolder) obj;
        return isHidden() == other.isHidden() &&
                isEmpty() == other.isEmpty() &&
                folderSize == other.folderSize &&
                fileCount == other.fileCount;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + Boolean.hashCode(isHidden()) +
                Boolean.hashCode(isEmpty()) + Long.hashCode(folderSize) +
                Integer.hashCode(fileCount);
    }

    @Override
    public String toString() {
        return String.format("LockedFolder{path='%s', name='%s', locked=%s, selected=%s, hidden=%s, empty=%s, size=%s, files=%d}",
                getPath(), getName(), isLocked(), isSelected(), isHidden(), isEmpty(), getFormattedSize(), fileCount);
    }
}