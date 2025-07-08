package me.ghosthacks96.ghostsecure.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static me.ghosthacks96.ghostsecure.Main.logger;

public class FolderManagementController {

    // Observable lists for table data
    private static final ObservableList<LockedItem> folderItems = FXCollections.observableArrayList();
    @FXML
    public Label statusLabel;

    @FXML
    public Button addFolderButton;
    @FXML
    public Button removeFolderButton;
    @FXML
    public Button switchFolderLock;
    // Table Components - Folders
    @FXML
    private TableView<LockedItem> folderTable;
    @FXML
    private TableColumn<LockedItem, Boolean> folderCheckBox;
    @FXML
    private TableColumn<LockedItem, String> folderNameColumn;
    @FXML
    private TableColumn<LockedItem, String> folderPathColumn;
    @FXML
    private TableColumn<LockedItem, Boolean> folderStatusColumn;
    // Table Components - Programs


    public static void refreshTableData() {
        logger.logInfo("Refreshing table data.");
        folderItems.setAll(Main.lockedItems.stream()
                .filter(item -> item.getPath().endsWith(".exe"))
                .collect(Collectors.toList()));
    }

    private void setupFolderTable() {
        folderTable.setSelectionModel(null);
        folderTable.setEditable(true);

        folderCheckBox.setCellValueFactory(cellData -> cellData.getValue().isSelectedProperty());
        folderCheckBox.setCellFactory(column -> createCheckBoxCell());
        folderNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        folderPathColumn.setCellValueFactory(new PropertyValueFactory<>("path"));
        folderStatusColumn.setCellValueFactory(cellData -> cellData.getValue().isLockedProperty());
    }

    private CheckBoxTableCell<LockedItem, Boolean> createCheckBoxCell() {
        CheckBoxTableCell<LockedItem, Boolean> checkBoxCell = new CheckBoxTableCell<>();
        checkBoxCell.setEditable(true);
        return checkBoxCell;
    }

    // ===============================
    // DATA MANAGEMENT METHODS
    // ===============================


    // ===============================
    // FOLDER MANAGEMENT METHODS
    // ===============================

    @FXML
    private void addFolder() {
        logger.logInfo("Adding a folder to be locked.");

        File selectedDirectory = new javafx.stage.DirectoryChooser().showDialog(new Stage());
        if (selectedDirectory == null) {
            logger.logWarning("No folder was selected to add.");
            return;
        }

        LockedItem lockedFolder = new LockedItem(
                selectedDirectory.getAbsolutePath(),
                selectedDirectory.getName(),
                true
        );

        Main.lockedItems.add(lockedFolder);
        Main.config.saveConfig();
        refreshTableData();
        logger.logInfo("Added folder: " + selectedDirectory.getName());
    }

    @FXML
    private void removeFolder() {
        logger.logInfo("Removing selected folders.");

        List<LockedItem> selectedFolders = getSelectedItems(folderItems);
        removeItemsAndSave(selectedFolders, "folder");
    }

    @FXML
    private void swapFLockStatus() {
        logger.logInfo("Toggling lock status for selected folders.");
        toggleLockStatusForSelectedItems(folderItems, "folder");
    }




    // ===============================
    // UTILITY METHODS
    // ===============================

    private List<LockedItem> getSelectedItems(ObservableList<LockedItem> items) {
        return items.stream()
                .filter(LockedItem::isSelected)
                .collect(Collectors.toList());
    }

    private void removeItemsAndSave(List<LockedItem> items, String itemType) {
        Main.lockedItems.removeAll(items);
        Main.config.saveConfig();
        refreshTableData();

        items.forEach(item ->
                logger.logInfo("Removed " + itemType + ": " + item.getName())
        );
    }

    private void toggleLockStatusForSelectedItems(ObservableList<LockedItem> items, String itemType) {
        items.stream()
                .filter(LockedItem::isSelected)
                .forEach(item -> {
                    item.setLocked(!item.isLocked());
                    item.setSelected(false);
                    logger.logInfo("Toggled lock status for " + itemType + ": " + item.getName());
                });

        Main.config.saveConfig();
        refreshTableData();
    }
}
