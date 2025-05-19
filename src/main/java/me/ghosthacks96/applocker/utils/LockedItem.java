package me.ghosthacks96.applocker.utils;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class LockedItem {

    private final StringProperty path; // Represents the path
    private final StringProperty name; // Represents the name
    private final BooleanProperty isLocked; // Represents the lock state
    private BooleanProperty selected;

    public LockedItem(String path, String name, boolean isLocked) {
        this.path = new SimpleStringProperty(path);
        this.name = new SimpleStringProperty(name);
        this.isLocked = new SimpleBooleanProperty(isLocked);
        this.selected = new SimpleBooleanProperty(false);
    }

    // Getter for path
    public String getPath() {
        return path.get();
    }

    public StringProperty pathProperty() {
        return path;
    }

    public void setPath(String path) {
        this.path.set(path);
    }

    // Getter for name
    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    // Getter for isLocked
    public boolean isLocked() {
        return isLocked.get();
    }

    public BooleanProperty isLockedProperty() {
        return isLocked;
    }

    public void setLocked(boolean isLocked) {
        this.isLocked.set(isLocked);
    }

    public boolean isSelected() {return selected.get();}
    public void setSelected(boolean selected) {this.selected.set(selected);}
    public BooleanProperty isSelectedProperty() {return selected;}
}