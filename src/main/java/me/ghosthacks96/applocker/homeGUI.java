package me.ghosthacks96.applocker;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.ghosthacks96.applocker.utils.LockedItem;
import me.ghosthacks96.applocker.utils.ServiceController;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static me.ghosthacks96.applocker.Main.*;

public class homeGUI {

    private static ObservableList<LockedItem> folderItems = FXCollections.observableArrayList();
    private static ObservableList<LockedItem> programItems = FXCollections.observableArrayList();
    @FXML
    private Button startServiceButton;
    @FXML
    private Button stopServiceButton;
    @FXML
    private Button addProgramButton;
    @FXML
    private Button removeProgramButton; // Button to remove programs
    @FXML
    private Button removeFolderButton; // Button to remove folders
    @FXML
    private Label lockStatus;
    @FXML
    private TableView<LockedItem> folderTable;
    @FXML
    private TableView<LockedItem> programTable;
    @FXML
    private TableColumn<LockedItem, Boolean> folderCheckBox;
    @FXML
    private TableColumn<LockedItem, String> folderNameColumn;
    @FXML
    private TableColumn<LockedItem, String> folderPathColumn;
    @FXML
    private TableColumn<LockedItem, Boolean> folderStatusColumn;
    @FXML
    private TableColumn<LockedItem, Boolean> programCheckBox;
    @FXML
    private TableColumn<LockedItem, Boolean> programActionColumn;
    @FXML
    private TableColumn<LockedItem, String> programPathColumn;

    public static void refreshTableData() {
        folderItems.setAll(lockedItems.stream()
                .filter(item -> !item.getPath().endsWith(".exe"))
                .collect(Collectors.toList()));

        programItems.setAll(lockedItems.stream()
                .filter(item -> item.getPath().endsWith(".exe"))
                .collect(Collectors.toList()));
    }

    @FXML
    public void initialize() {
        // Disable row selection for folderTable and programTable
        folderTable.setSelectionModel(null);
        programTable.setSelectionModel(null);

        // Set up folder checkboxes
        folderCheckBox.setCellValueFactory(cellData -> cellData.getValue().isSelectedProperty());
        folderCheckBox.setCellFactory(column -> {
            CheckBoxTableCell<LockedItem, Boolean> checkBoxCell = new CheckBoxTableCell<>();
            checkBoxCell.setEditable(true); // Allow checkboxes to be editable
            return checkBoxCell;
        });

        // Bind folder name to 'name' property
        folderNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        // Bind folder path to 'path' property
        folderPathColumn.setCellValueFactory(new PropertyValueFactory<>("path"));

        // Bind folder status to 'isLocked' property
        folderStatusColumn.setCellValueFactory(cellData -> cellData.getValue().isLockedProperty());

        // Allow editing of folderTable
        folderTable.setEditable(true);

        folderItems.forEach(item -> {
            item.isSelectedProperty().addListener((observable, oldValue, newValue) -> {
                onSelectionChanged(item, newValue);
            });
        });

        programCheckBox.setCellValueFactory(cellData -> cellData.getValue().isSelectedProperty());
        programCheckBox.setCellFactory(column -> {
            CheckBoxTableCell<LockedItem, Boolean> checkBoxCell = new CheckBoxTableCell<>();
            checkBoxCell.setEditable(true); // Allow checkboxes to be editable
            return checkBoxCell;
        });

        // Bind program name to 'name' property
        programPathColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        // Bind program status to 'isLocked' property
        programActionColumn.setCellValueFactory(cellData -> cellData.getValue().isLockedProperty());

        // Allow editing of programTable
        programTable.setEditable(true);


        // Initialize and bind data for folder and program tables
        folderItems = FXCollections.observableArrayList(
                lockedItems.stream().filter(item -> !item.getPath().endsWith(".exe")).toList()
        );
        programItems = FXCollections.observableArrayList(
                lockedItems.stream().filter(item -> item.getPath().endsWith(".exe")).toList()
        );

        folderTable.setItems(folderItems); // Set items for folder table
        programTable.setItems(programItems); // Set items for program table

        // Update the UI based on service status
        boolean isRunning = ServiceController.isServiceRunning();
        updateServiceStatus(isRunning);
    }

    @FXML
    private void addFolder() {
        javafx.stage.DirectoryChooser directoryChooser = new javafx.stage.DirectoryChooser();
        directoryChooser.setTitle("Select Folder to Lock");

        File selectedDirectory = directoryChooser.showDialog(new Stage());
        if (selectedDirectory != null) {
            LockedItem lockedFolder = new LockedItem(
                    selectedDirectory.getAbsolutePath(),
                    selectedDirectory.getName(),
                    true
            );
            lockedItems.add(lockedFolder);
            saveConfig(); // Save changes to the configuration
            refreshTableData(); // Refresh tables to include new data
        }
    }

    @FXML
    private void removeFolder() {
        // Get selected folders
        List<LockedItem> selectedFolders = folderItems.stream()
                .filter(LockedItem::isSelected)
                .collect(Collectors.toList());

        // Remove selected folders from `lockedItems` and the folderItems list
        lockedItems.removeAll(selectedFolders);
        folderItems.removeAll(selectedFolders);

        saveConfig(); // Save the updated configuration
        refreshTableData(); // Refresh table display
    }

    @FXML
    private void addProgram() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Program to Lock");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Executable Files", "*.exe"));

        File selectedFile = fileChooser.showOpenDialog(new Stage());
        if (selectedFile != null) {
            LockedItem lockedProgram = new LockedItem(
                    selectedFile.getAbsolutePath(),
                    selectedFile.getName(),
                    true
            );
            lockedItems.add(lockedProgram);
            saveConfig(); // Save changes to the configuration
            refreshTableData(); // Refresh tables to include new data
        }
    }

    @FXML
    private void removeProgram() {
        // Get selected programs
        List<LockedItem> selectedPrograms = programItems.stream()
                .filter(LockedItem::isSelected)
                .collect(Collectors.toList());

        // Remove selected programs from `lockedItems` and the programItems list
        lockedItems.removeAll(selectedPrograms);
        programItems.removeAll(selectedPrograms);

        saveConfig(); // Save the updated configuration
        refreshTableData(); // Refresh table display
    }

    @FXML
    public void startService() {
        ServiceController.startService();
        if(!config.get("mode").getAsString().equals("lock")){
            config.remove("mode");
            config.addProperty("mode", "lock");
        }
        saveConfig();
        Platform.runLater(() -> updateServiceStatus(true));
    }

    @FXML
    public void stopService() {
        ServiceController.stopService();
        if(!config.get("mode").getAsString().equals("lock")){
            config.remove("mode");
            config.addProperty("mode", "unlock");
        }
        saveConfig();
        Platform.runLater(() -> updateServiceStatus(false));
    }

    private void updateServiceStatus(boolean isRunning) {
        if (isRunning) {
            startServiceButton.setDisable(true);
            stopServiceButton.setDisable(false);
            lockStatus.setText("Locking Engaged");
            lockStatus.setTextFill(javafx.scene.paint.Color.GREEN);
        } else {
            startServiceButton.setDisable(false);
            stopServiceButton.setDisable(true);
            lockStatus.setText("Locking Disabled");
            lockStatus.setTextFill(javafx.scene.paint.Color.RED);
        }
    }

    @FXML
    private void swapFLockStatus() {
        // Get selected folders
        List<LockedItem> selectedFolders = folderItems.stream()
                .filter(LockedItem::isSelected) // Filters items where the checkbox is selected
                .collect(Collectors.toList());

        // Toggle the lock status for each selected folder
        selectedFolders.forEach(item -> {
            item.setLocked(!item.isLocked());
            item.setSelected(false);
            System.out.println("NAME: " + item.getName() + " locked status: " + item.isLocked());
        }); // Switch isLocked value


        // Save the updated configuration and refresh the table
        saveConfig();
        refreshTableData();
    }

    @FXML
    private void swapPLockStatus() {
        // Get selected programs
        List<LockedItem> selectedPrograms = programItems.stream()
                .filter(LockedItem::isSelected) // Filters items where the checkbox is selected
                .collect(Collectors.toList());

        // Toggle the lock status for each selected program
        selectedPrograms.forEach(item -> {
            item.setLocked(!item.isLocked());
            item.setSelected(false);
            System.out.println("NAME: " + item.getName() + " locked status: " + item.isLocked());
        }); // Switch isLocked value

        // Save the updated configuration and refresh the table
        saveConfig();
        refreshTableData();
    }

    private void onSelectionChanged(LockedItem item, boolean isSelected) {
        item.setSelected(isSelected);
    }


}