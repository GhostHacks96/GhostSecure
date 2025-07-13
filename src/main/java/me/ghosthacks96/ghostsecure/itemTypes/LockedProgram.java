package me.ghosthacks96.ghostsecure.itemTypes;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Represents a locked program with additional program-specific properties including icon access.
 * Extends LockedItem to provide program-specific functionality.
 */
public class LockedProgram extends LockedItem {

    private final StringProperty version;
    private final StringProperty publisher;
    private final StringProperty description;
    private final ObjectProperty<Image> icon;
    private final ObjectProperty<Image> largeIcon;

    private String executablePath;
    private String arguments;
    private boolean isRunning;
    private long fileSize;

    /**
     * Creates a new LockedProgram with the specified properties.
     *
     * @param path the program path (cannot be null or empty)
     * @param name the display name (cannot be null or empty)
     * @param isLocked the initial lock state
     * @throws IllegalArgumentException if path or name is null or empty
     */
    public LockedProgram(String path, String name, boolean isLocked) {
        super(path, name, isLocked);
        this.version = new SimpleStringProperty("");
        this.publisher = new SimpleStringProperty("");
        this.description = new SimpleStringProperty("");
        this.icon = new SimpleObjectProperty<>();
        this.largeIcon = new SimpleObjectProperty<>();
        this.executablePath = path;
        this.arguments = "";
        this.isRunning = false;
        this.fileSize = 0;

        // Initialize program properties
        updateProgramProperties();
    }

    /**
     * Creates a new LockedProgram with unlocked state by default.
     *
     * @param path the program path (cannot be null or empty)
     * @param name the display name (cannot be null or empty)
     * @throws IllegalArgumentException if path or name is null or empty
     */
    public LockedProgram(String path, String name) {
        this(path, name, false);
    }

    /**
     * Creates a LockedProgram from a File object.
     *
     * @param programFile the program File object
     * @param isLocked the initial lock state
     * @throws IllegalArgumentException if programFile is null or not a file
     */
    public LockedProgram(File programFile, boolean isLocked) {
        this(programFile.getAbsolutePath(), programFile.getName(), isLocked);
        if (!programFile.isFile()) {
            throw new IllegalArgumentException("File must be a regular file");
        }
    }

    /**
     * Creates a LockedProgram from a File object with unlocked state.
     *
     * @param programFile the program File object
     * @throws IllegalArgumentException if programFile is null or not a file
     */
    public LockedProgram(File programFile) {
        this(programFile, false);
    }

    /**
     * Updates program-specific properties by examining the filesystem and system.
     */
    private void updateProgramProperties() {
        try {
            Path programPath = Paths.get(getPath());

            if (Files.exists(programPath)) {
                File programFile = programPath.toFile();

                // Get file size
                fileSize = programFile.length();

                // Load icons
                loadProgramIcons(programFile);

                // Try to get program information (Windows-specific)
                if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                    loadWindowsProgramInfo(programFile);
                } else {
                    // For non-Windows systems, use basic file information
                    loadBasicProgramInfo(programFile);
                }
            }
        } catch (Exception e) {
            // If we can't access the program, use defaults
            setDefaultProgramInfo();
        }
    }

    /**
     * Loads program icons from the system.
     *
     * @param programFile the program file
     */
    private void loadProgramIcons(File programFile) {
        try {
            // Try to get the system icon using FileSystemView
            Icon systemIcon = FileSystemView.getFileSystemView().getSystemIcon(programFile);

            if (systemIcon != null) {
                // Convert Icon to JavaFX Image
                Image smallIcon = iconToImage(systemIcon);
                setIcon(smallIcon);

                // For large icon, try to get a larger version
                try {
                    // This is a Windows-specific approach
                    if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                        Icon largeSystemIcon = FileSystemView.getFileSystemView().getSystemIcon(programFile, 32, 32);
                        if (largeSystemIcon != null) {
                            Image bigIcon = iconToImage(largeSystemIcon);
                            setLargeIcon(bigIcon);
                        } else {
                            setLargeIcon(smallIcon); // Use small icon as fallback
                        }
                    } else {
                        setLargeIcon(smallIcon);
                    }
                } catch (Exception e) {
                    setLargeIcon(smallIcon);
                }
            }
        } catch (Exception e) {
            // If icon loading fails, leave as null
        }
    }

    /**
     * Converts a Swing Icon to a JavaFX Image.
     *
     * @param icon the Swing Icon
     * @return the JavaFX Image
     */
    private Image iconToImage(Icon icon) {
        if (icon == null) return null;

        int width = icon.getIconWidth();
        int height = icon.getIconHeight();

        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bufferedImage.createGraphics();
        icon.paintIcon(null, g2d, 0, 0);
        g2d.dispose();

        // Convert BufferedImage to JavaFX Image
        return convertToJavaFXImage(bufferedImage);
    }

    /**
     * Converts a BufferedImage to a JavaFX Image.
     *
     * @param bufferedImage the BufferedImage
     * @return the JavaFX Image
     */
    private Image convertToJavaFXImage(BufferedImage bufferedImage) {
        try {
            // Create a temporary file to write the image
            Path tempFile = Files.createTempFile("icon", ".png");
            javax.imageio.ImageIO.write(bufferedImage, "png", tempFile.toFile());

            // Load as JavaFX Image
            Image image = new Image(tempFile.toUri().toString());

            // Clean up temp file
            Files.deleteIfExists(tempFile);

            return image;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Loads Windows-specific program information using system properties.
     *
     * @param programFile the program file
     */
    private void loadWindowsProgramInfo(File programFile) {
        // This is a simplified approach - in a real implementation,
        // you might use JNI or libraries like jna to access Windows APIs
        // for more detailed program information

        String fileName = programFile.getName();
        String extension = getFileExtension(fileName);

        // Set basic description based on file type
        switch (extension.toLowerCase()) {
            case ".exe":
                setDescription("Windows Executable");
                break;
            case ".msi":
                setDescription("Windows Installer Package");
                break;
            case ".bat":
                setDescription("Batch File");
                break;
            case ".cmd":
                setDescription("Command File");
                break;
            default:
                setDescription("Program File");
        }

        // Publisher and version would require more advanced techniques
        // For now, leave them empty or use placeholder values
        setPublisher("Unknown");
        setVersion("Unknown");
    }

    /**
     * Loads basic program information for non-Windows systems.
     *
     * @param programFile the program file
     */
    private void loadBasicProgramInfo(File programFile) {
        String fileName = programFile.getName();
        String extension = getFileExtension(fileName);

        if (extension.isEmpty()) {
            setDescription("Executable File");
        } else {
            setDescription(extension.toUpperCase() + " File");
        }

        setPublisher("Unknown");
        setVersion("Unknown");
    }

    /**
     * Sets default program information when file access fails.
     */
    private void setDefaultProgramInfo() {
        setDescription("Program File");
        setPublisher("Unknown");
        setVersion("Unknown");
    }

    /**
     * Gets the file extension from a filename.
     *
     * @param fileName the filename
     * @return the file extension including the dot, or empty string if no extension
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex);
        }
        return "";
    }

    /**
     * Refreshes the program properties by re-examining the filesystem.
     */
    public void refreshProperties() {
        updateProgramProperties();
    }

    // Version property methods
    public String getVersion() {
        return version.get();
    }

    public StringProperty versionProperty() {
        return version;
    }

    public void setVersion(String version) {
        this.version.set(version != null ? version : "");
    }

    // Publisher property methods
    public String getPublisher() {
        return publisher.get();
    }

    public StringProperty publisherProperty() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher.set(publisher != null ? publisher : "");
    }

    // Description property methods
    public String getDescription() {
        return description.get();
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public void setDescription(String description) {
        this.description.set(description != null ? description : "");
    }

    // Icon property methods
    public Image getIcon() {
        return icon.get();
    }

    public ReadOnlyObjectProperty<Image> iconProperty() {
        return icon;
    }

    public void setIcon(Image icon) {
        this.icon.set(icon);
    }

    // Large icon property methods
    public Image getLargeIcon() {
        return largeIcon.get();
    }

    public ReadOnlyObjectProperty<Image> largeIconProperty() {
        return largeIcon;
    }

    public void setLargeIcon(Image largeIcon) {
        this.largeIcon.set(largeIcon);
    }

    /**
     * Creates an ImageView with the program's icon.
     *
     * @param size the desired size for the ImageView
     * @return an ImageView with the program's icon, or null if no icon is available
     */
    public ImageView createIconView(double size) {
        Image iconImage = getIcon();
        if (iconImage != null) {
            ImageView imageView = new ImageView(iconImage);
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            return imageView;
        }
        return null;
    }

    /**
     * Creates an ImageView with the program's large icon.
     *
     * @param size the desired size for the ImageView
     * @return an ImageView with the program's large icon, or null if no icon is available
     */
    public ImageView createLargeIconView(double size) {
        Image iconImage = getLargeIcon();
        if (iconImage != null) {
            ImageView imageView = new ImageView(iconImage);
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            return imageView;
        }
        return createIconView(size); // Fallback to regular icon
    }

    // Executable path methods
    public String getExecutablePath() {
        return executablePath;
    }

    public void setExecutablePath(String executablePath) {
        this.executablePath = executablePath != null ? executablePath : "";
    }

    // Arguments methods
    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments != null ? arguments : "";
    }

    // Running state methods
    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        this.isRunning = running;
    }

    // File size methods
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Returns the file size formatted as a human-readable string.
     *
     * @return formatted size string (e.g., "1.5 MB", "256 KB")
     */
    public String getFormattedFileSize() {
        return formatFileSize(fileSize);
    }

    /**
     * Checks if the program file exists on the filesystem.
     *
     * @return true if the program file exists
     */
    public boolean exists() {
        try {
            Path programPath = Paths.get(getPath());
            return Files.exists(programPath) && Files.isRegularFile(programPath);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the program file is executable.
     *
     * @return true if the program file is executable
     */
    public boolean isExecutable() {
        try {
            Path programPath = Paths.get(getPath());
            return Files.isExecutable(programPath);
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
        return "PROGRAM";
    }

    @Override
    public LockedProgram copy() {
        LockedProgram copy = new LockedProgram(getPath(), getName(), isLocked());
        copy.setSelected(isSelected());
        copy.setVersion(getVersion());
        copy.setPublisher(getPublisher());
        copy.setDescription(getDescription());
        copy.setIcon(getIcon());
        copy.setLargeIcon(getLargeIcon());
        copy.setExecutablePath(getExecutablePath());
        copy.setArguments(getArguments());
        copy.setRunning(isRunning());
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        if (!(obj instanceof LockedProgram)) return false;

        LockedProgram other = (LockedProgram) obj;
        return Objects.equals(getVersion(), other.getVersion()) &&
                Objects.equals(getPublisher(), other.getPublisher()) &&
                Objects.equals(getDescription(), other.getDescription()) &&
                Objects.equals(getExecutablePath(), other.getExecutablePath()) &&
                Objects.equals(getArguments(), other.getArguments()) &&
                isRunning() == other.isRunning() &&
                fileSize == other.fileSize;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + Objects.hash(getVersion(), getPublisher(),
                getDescription(), getExecutablePath(), getArguments(),
                isRunning(), fileSize);
    }

    @Override
    public String toString() {
        return String.format("LockedProgram{path='%s', name='%s', locked=%s, selected=%s, version='%s', publisher='%s', description='%s', size=%s, running=%s}",
                getPath(), getName(), isLocked(), isSelected(), getVersion(), getPublisher(),
                getDescription(), getFormattedFileSize(), isRunning());
    }
}