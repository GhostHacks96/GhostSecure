package me.ghosthacks96.ghostsecure.itemTypes;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import java.util.Objects;

/**
 * Base class for locked items with path, name, and lock state properties.
 * This abstract class provides common functionality for JavaFX property bindings.
 */
public abstract class LockedItem {

    protected final StringProperty path;
    protected final StringProperty name;
    protected final BooleanProperty isLocked;
    protected final BooleanProperty selected;

    /**
     * Creates a new LockedItem with the specified properties.
     *
     * @param path the file system path (cannot be null or empty)
     * @param name the display name (cannot be null or empty)
     * @param isLocked the initial lock state
     * @throws IllegalArgumentException if path or name is null or empty
     */
    protected LockedItem(String path, String name, boolean isLocked) {
        validateInput(path, "Path");
        validateInput(name, "Name");

        this.path = new SimpleStringProperty(path.trim());
        this.name = new SimpleStringProperty(name.trim());
        this.isLocked = new SimpleBooleanProperty(isLocked);
        this.selected = new SimpleBooleanProperty(false);
    }

    /**
     * Creates a new LockedItem with unlocked state by default.
     *
     * @param path the file system path (cannot be null or empty)
     * @param name the display name (cannot be null or empty)
     * @throws IllegalArgumentException if path or name is null or empty
     */
    protected LockedItem(String path, String name) {
        this(path, name, false);
    }

    /**
     * Validates that the input string is not null or empty.
     *
     * @param input the string to validate
     * @param fieldName the name of the field for error messages
     * @throws IllegalArgumentException if input is null or empty
     */
    protected void validateInput(String input, String fieldName) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
    }

    // Path property methods
    public String getPath() {
        return path.get();
    }

    public ReadOnlyStringProperty pathProperty() {
        return path;
    }

    public void setPath(String path) {
        validateInput(path, "Path");
        this.path.set(path.trim());
    }

    // Name property methods
    public String getName() {
        return name.get();
    }

    public ReadOnlyStringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        validateInput(name, "Name");
        this.name.set(name.trim());
    }

    // Lock state property methods
    public boolean isLocked() {
        return isLocked.get();
    }

    public BooleanProperty isLockedProperty() {
        return isLocked;
    }

    public void setLocked(boolean isLocked) {
        this.isLocked.set(isLocked);
    }

    /**
     * Toggles the lock state of this item.
     */
    public void toggleLock() {
        setLocked(!isLocked());
    }

    // Selection property methods
    public boolean isSelected() {
        return selected.get();
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    /**
     * Toggles the selection state of this item.
     */
    public void toggleSelection() {
        setSelected(!isSelected());
    }

    /**
     * Returns the type of this locked item (for identification purposes).
     *
     * @return the item type as a string
     */
    public abstract String getItemType();

    /**
     * Returns a copy of this LockedItem with the same properties.
     *
     * @return a new LockedItem instance with copied properties
     */
    public abstract LockedItem copy();

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        LockedItem other = (LockedItem) obj;
        return Objects.equals(getPath(), other.getPath()) &&
                Objects.equals(getName(), other.getName()) &&
                isLocked() == other.isLocked() &&
                isSelected() == other.isSelected();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPath(), getName(), isLocked(), isSelected());
    }

    @Override
    public String toString() {
        return String.format("%s{path='%s', name='%s', locked=%s, selected=%s}",
                getClass().getSimpleName(), getPath(), getName(), isLocked(), isSelected());
    }
}