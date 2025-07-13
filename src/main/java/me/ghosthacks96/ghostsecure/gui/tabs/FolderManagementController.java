package me.ghosthacks96.ghostsecure.gui.tabs;

import javafx.application.Platform;
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
import me.ghosthacks96.ghostsecure.gui.extras.LockedItemCard;
import me.ghosthacks96.ghostsecure.itemTypes.LockedFolder;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.ghosthacks96.ghostsecure.Main.folderStorage;
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

    public void refreshCardDisplay() {
        // This would be called from your main refresh method
        logger.logInfo("Refreshing card display.");

        List<LockedItem> folders = new ArrayList<>();
        Map<String, Object> allData = folderStorage.getAllData();

        // Convert storage data to LockedItem objects
        for (Map.Entry<String, Object> entry : allData.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Map<String, Object> itemData = (Map<String, Object>) entry.getValue();
                if ("FOLDER".equals(itemData.get("type"))) {
                    String path = (String) itemData.get("path");
                    String name = (String) itemData.get("name");
                    boolean locked = (boolean) itemData.get("locked");

                    LockedFolder folder = new LockedFolder(path, name);
                    folder.setLocked(locked);
                    folders.add(folder);
                }
            }
        }

        folderItems.setAll(folders);
        Platform.runLater(this::updateCardDisplay);
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
        logger.logDebug("Folder items count: " + folderItems.size());
        for (LockedItem item : folderItems) {
            logger.logDebug("Folder item: " + item.getName());
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

        // Create folder data
        Map<String, Object> folderData = new HashMap<>();
        folderData.put("path", selectedDirectory.getAbsolutePath());
        folderData.put("name", selectedDirectory.getName());
        folderData.put("locked", false);
        folderData.put("type", "FOLDER");

        // Generate a new key for the folder
        String newKey = "folder_" + System.currentTimeMillis();

        // Add folder to storage
        folderStorage.put(newKey, folderData);
        folderStorage.saveData();

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

        // Get all folder data
        Map<String, Object> allData = folderStorage.getAllData();

        // Find and remove selected folders
        for (String key : new ArrayList<>(allData.keySet())) {
            if (allData.get(key) instanceof Map) {
                Map<String, Object> itemData = (Map<String, Object>) allData.get(key);
                if ("FOLDER".equals(itemData.get("type"))) {
                    String path = (String) itemData.get("path");

                    // Check if this folder is selected for removal
                    for (LockedItem selectedFolder : selectedFolders) {
                        if (selectedFolder.getPath().equals(path)) {
                            folderStorage.remove(key);
                            logger.logInfo("Removed folder: " + selectedFolder.getName());
                            break;
                        }
                    }
                }
            }
        }

        folderStorage.saveData();
        refreshCardDisplay();
        updateCardDisplay();
    }

    @FXML
    private void swapFLockStatus() {
        logger.logInfo("Toggling lock status for selected folders.");

        // Get selected folders
        List<LockedItem> selectedFolders = folderItems.stream()
                .filter(LockedItem::isSelected)
                .collect(Collectors.toList());

        if (selectedFolders.isEmpty()) {
            logger.logWarning("No folders selected for toggling lock status.");
            return;
        }

        // Get all folder data
        Map<String, Object> allData = folderStorage.getAllData();

        // Find and update selected folders
        for (String key : allData.keySet()) {
            if (allData.get(key) instanceof Map) {
                Map<String, Object> itemData = (Map<String, Object>) allData.get(key);
                if ("FOLDER".equals(itemData.get("type"))) {
                    String path = (String) itemData.get("path");

                    // Check if this folder is selected for toggling
                    for (LockedItem selectedFolder : selectedFolders) {
                        if (selectedFolder.getPath().equals(path)) {
                            // Toggle locked status
                            boolean currentLocked = (boolean) itemData.get("locked");
                            itemData.put("locked", !currentLocked);

                            // Update in storage
                            folderStorage.put(key, itemData);

                            // Update in memory
                            selectedFolder.setLocked(!currentLocked);
                            selectedFolder.setSelected(false);

                            logger.logInfo("Toggled lock status for folder: " + selectedFolder.getName());
                            break;
                        }
                    }
                }
            }
        }

        folderStorage.saveData();
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
