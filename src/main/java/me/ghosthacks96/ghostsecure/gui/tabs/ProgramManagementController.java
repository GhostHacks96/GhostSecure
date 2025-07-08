package me.ghosthacks96.ghostsecure.gui.tabs;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static me.ghosthacks96.ghostsecure.Main.logger;

public class ProgramManagementController {


    // UI Components - Program Management
    @FXML
    public Button addProgramButton;
    @FXML
    public Button removeProgramButton;
    @FXML
    public Button switchProgramLock;
    @FXML
    private TableView<LockedItem> programTable;
    @FXML
    private TableColumn<LockedItem, Boolean> programCheckBox;
    @FXML
    private TableColumn<LockedItem, Boolean> programActionColumn;
    @FXML
    private TableColumn<LockedItem, String> programPathColumn;

    private static final ObservableList<LockedItem> programItems = FXCollections.observableArrayList();

    public static void refreshTableData() {
        logger.logInfo("Refreshing table data.");
        programItems.setAll(Main.lockedItems.stream()
                .filter(item -> item.getPath().endsWith(".exe"))
                .collect(Collectors.toList()));
    }

    private void setupProgramTable() {
        programTable.setSelectionModel(null);
        programTable.setEditable(true);

        programCheckBox.setCellValueFactory(cellData -> cellData.getValue().isSelectedProperty());
        programCheckBox.setCellFactory(column -> createCheckBoxCell());
        programPathColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        programActionColumn.setCellValueFactory(cellData -> cellData.getValue().isLockedProperty());
    }
    // ===============================
    // PROGRAM MANAGEMENT METHODS
    // ===============================

    @FXML
    private void addProgram() {
        logger.logInfo("Adding a program to be locked.");

        File selectedFile = selectExecutableFile();
        if (selectedFile == null) {
            logger.logWarning("No program was selected to add.");
            return;
        }

        LockedItem lockedProgram = new LockedItem(
                selectedFile.getAbsolutePath(),
                selectedFile.getName(),
                true
        );

        Main.lockedItems.add(lockedProgram);
        Main.config.saveConfig();
        refreshTableData();
        logger.logInfo("Added program: " + selectedFile.getName());
    }

    @FXML
    private void removeProgram() {
        logger.logInfo("Removing selected programs.");

        List<LockedItem> selectedPrograms = getSelectedItems(programItems);
        removeItemsAndSave(selectedPrograms, "program");
    }

    @FXML
    private void swapPLockStatus() {
        logger.logInfo("Toggling lock status for selected programs.");
        toggleLockStatusForSelectedItems(programItems, "program");
    }

    private File selectExecutableFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Program to Lock");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Executable Files", "*.exe")
        );
        return fileChooser.showOpenDialog(new Stage());
    }

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
    private CheckBoxTableCell<LockedItem, Boolean> createCheckBoxCell() {
        CheckBoxTableCell<LockedItem, Boolean> checkBoxCell = new CheckBoxTableCell<>();
        checkBoxCell.setEditable(true);
        return checkBoxCell;
    }

}
