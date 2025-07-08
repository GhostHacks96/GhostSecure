package me.ghosthacks96.ghostsecure.gui.extras;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

// Custom Card Component for Locked Items
public class LockedItemCard extends HBox {
    private final LockedItem item;
    private final CheckBox selectCheckBox;
    private final Label nameLabel;
    private final Label pathLabel;
    private final Button lockToggleButton;
    private final Label statusLabel;

    public LockedItemCard(LockedItem item) {
        this.item = item;

        // Initialize components
        selectCheckBox = new CheckBox();
        nameLabel = new Label(item.getName());
        pathLabel = new Label(item.getPath());
        lockToggleButton = new Button();
        statusLabel = new Label();

        setupCard();
        setupBindings();
        updateLockStatus();
    }

    private void setupCard() {
        // Card styling
        setSpacing(15);
        setPadding(new Insets(15));
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("locked-item-card");

        // Create icon area (folder/program icon)
        VBox iconArea = new VBox();
        iconArea.setAlignment(Pos.CENTER);
        iconArea.setPrefWidth(60);

        Label iconLabel = new Label(item.getPath().endsWith(".exe") ? "📦" : "📁");
        iconLabel.setStyle("-fx-font-size: 24px;");
        iconArea.getChildren().add(iconLabel);

        // Create info area
        VBox infoArea = new VBox(5);
        infoArea.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(infoArea, Priority.ALWAYS);

        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        pathLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #b0b0b0;");
        pathLabel.setWrapText(true);

        infoArea.getChildren().addAll(nameLabel, pathLabel);

        // Create status area
        VBox statusArea = new VBox(5);
        statusArea.setAlignment(Pos.CENTER);
        statusArea.setPrefWidth(120);

        statusLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        lockToggleButton.setPrefWidth(100);
        lockToggleButton.setOnAction(e -> toggleLockStatus());

        statusArea.getChildren().addAll(statusLabel, lockToggleButton);

        // Create action area
        VBox actionArea = new VBox();
        actionArea.setAlignment(Pos.CENTER);
        actionArea.setPrefWidth(80);

        selectCheckBox.setStyle("-fx-scale-x: 1.2; -fx-scale-y: 1.2;");
        actionArea.getChildren().add(selectCheckBox);

        // Add all areas to card
        getChildren().addAll(iconArea, infoArea, statusArea, actionArea);
    }

    private void setupBindings() {

        selectCheckBox.selectedProperty().bindBidirectional(item.selectedProperty());

        // Add hover effects
        setOnMouseEntered(e -> setStyle("-fx-background-color: #ffffff0a; -fx-border-color: #00d4ff66; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;"));
        setOnMouseExited(e -> setStyle("-fx-background-color: #ffffff05; -fx-border-color: #00d4ff33; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;"));
    }

    private void toggleLockStatus() {
        item.setLocked(!item.isLocked());
        updateLockStatus();
        Main.config.saveConfig();
    }

    private void updateLockStatus() {
        if (item.isLocked()) {
            statusLabel.setText("🔒 LOCKED");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #ff4757;");
            lockToggleButton.setText("UNLOCK");
            lockToggleButton.getStyleClass().clear();
            lockToggleButton.getStyleClass().addAll("button", "success");
        } else {
            statusLabel.setText("🔓 UNLOCKED");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2ed573;");
            lockToggleButton.setText("LOCK");
            lockToggleButton.getStyleClass().clear();
            lockToggleButton.getStyleClass().addAll("button", "danger");
        }
    }

    public LockedItem getItem() {
        return item;
    }
}