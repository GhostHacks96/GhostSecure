package me.ghosthacks96.ghostsecure.gui.tabs;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.gui.extras.LockedItemCard;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static me.ghosthacks96.ghostsecure.Main.config;
import static me.ghosthacks96.ghostsecure.Main.logger;

public class FolderManagementController {

    @FXML
    private ScrollPane cardScrollPane;
    @FXML
    private VBox cardContainer;
    @FXML
    private Label statusLabel;
    @FXML
    private Button addFolderButton;
    @FXML
    private Button removeFolderButton;
    @FXML
    private Button switchFolderLock;
    @FXML
    private Button selectAllButton;
    @FXML
    private Button deselectAllButton;

    private static final ObservableList<LockedItem> folderItems = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        setupCardContainer();
        refreshCardDisplay();
    }

    private void setupCardContainer() {
        cardContainer.setSpacing(8);
        cardContainer.setPadding(new Insets(10));
        cardContainer.setStyle("-fx-background-color: transparent;");

        cardScrollPane.setFitToWidth(true);
        cardScrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: #00d4ff33; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");
    }

    public static void refreshCardDisplay() {
        // This would be called from your main refresh method
        logger.logInfo("Refreshing card display.");
        folderItems.setAll(config.lockedItems.stream()
                .filter(item -> !item.getPath().endsWith(".exe"))
                .collect(Collectors.toList()));
    }

    private void updateCardDisplay() {
        cardContainer.getChildren().clear();

        if (folderItems.isEmpty()) {
            Label emptyLabel = new Label("No folders added yet. Click 'ADD FOLDER' to get started.");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #b0b0b0; -fx-font-style: italic;");
            emptyLabel.setAlignment(Pos.CENTER);
            cardContainer.getChildren().add(emptyLabel);
            return;
        }

        for (LockedItem item : folderItems) {
            LockedItemCard card = new LockedItemCard(item);
            cardContainer.getChildren().add(card);
        }

        updateStatusLabel();
    }

    private void updateStatusLabel() {
        long totalItems = folderItems.size();
        long lockedItems = folderItems.stream().mapToLong(item -> item.isLocked() ? 1 : 0).sum();
        long selectedItems = folderItems.stream().mapToLong(item -> item.isSelected() ? 1 : 0).sum();

        statusLabel.setText(String.format("Total: %d | Locked: %d | Selected: %d",
                totalItems, lockedItems, selectedItems));
    }

    @FXML
    private void addFolder() {
        logger.logInfo("Adding a folder to be locked.");

        File selectedDirectory = new DirectoryChooser().showDialog(new Stage());
        if (selectedDirectory == null) {
            logger.logWarning("No folder was selected to add.");
            return;
        }

        LockedItem lockedFolder = new LockedItem(
                selectedDirectory.getAbsolutePath(),
                selectedDirectory.getName(),
                true
        );

        config.lockedItems.add(lockedFolder);
        config.saveConfig();
        refreshCardDisplay();
        updateCardDisplay();
        logger.logInfo("Added folder: " + selectedDirectory.getName());
    }

    @FXML
    private void removeFolder() {
        logger.logInfo("Removing selected folders.");

        List<LockedItem> selectedFolders = folderItems.stream()
                .filter(LockedItem::isSelected)
                .collect(Collectors.toList());

        if (selectedFolders.isEmpty()) {
            logger.logWarning("No folders selected for removal.");
            return;
        }

        config.lockedItems.removeAll(selectedFolders);
        config.saveConfig();
        refreshCardDisplay();
        updateCardDisplay();

        selectedFolders.forEach(item ->
                logger.logInfo("Removed folder: " + item.getName())
        );
    }

    @FXML
    private void swapFLockStatus() {
        logger.logInfo("Toggling lock status for selected folders.");

        folderItems.stream()
                .filter(LockedItem::isSelected)
                .forEach(item -> {
                    item.setLocked(!item.isLocked());
                    item.setSelected(false);
                    logger.logInfo("Toggled lock status for folder: " + item.getName());
                });

        config.saveConfig();
        updateCardDisplay();
    }

    @FXML
    private void selectAll() {
        folderItems.forEach(item -> item.setSelected(true));
        updateStatusLabel();
    }

    @FXML
    private void deselectAll() {
        folderItems.forEach(item -> item.setSelected(false));
        updateStatusLabel();
    }
}