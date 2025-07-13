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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.gui.extras.LockedItemCard;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;
import me.ghosthacks96.ghostsecure.itemTypes.LockedProgram;
import me.ghosthacks96.ghostsecure.gui.extras.FileSelectionHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.ghosthacks96.ghostsecure.Main.programStorage;
import static me.ghosthacks96.ghostsecure.Main.logger;

public class ProgramManagementController {

    @FXML
    private ScrollPane cardScrollPane;
    @FXML
    private VBox cardContainer;
    @FXML
    private Label statusLabel;
    @FXML
    private Button addProgramButton;
    @FXML
    private Button addMultipleProgramsButton;
    @FXML
    private Button removeProgramButton;
    @FXML
    private Button switchProgramLock;
    @FXML
    private Button selectAllButton;
    @FXML
    private Button deselectAllButton;

    private static final ObservableList<LockedItem> programItems = FXCollections.observableArrayList();

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
        logger.logInfo("Refreshing card display.");

        List<LockedItem> programs = new ArrayList<>();
        Map<String, Object> allData = programStorage.getAllData();

        // Convert storage data to LockedItem objects
        for (Map.Entry<String, Object> entry : allData.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Map<String, Object> itemData = (Map<String, Object>) entry.getValue();
                if ("PROGRAM".equals(itemData.get("type"))) {
                    String path = (String) itemData.get("path");
                    String name = (String) itemData.get("name");
                    boolean locked = (boolean) itemData.get("locked");

                    LockedProgram program = new LockedProgram(path, name);
                    program.setLocked(locked);
                    programs.add(program);
                }
            }
        }

        programItems.setAll(programs);
        Platform.runLater(this::updateCardDisplay);
    }

    private void updateCardDisplay() {
        cardContainer.getChildren().clear();

        if (programItems.isEmpty()) {
            Label emptyLabel = new Label("No programs added yet. Click 'ADD PROGRAM' to get started.");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #b0b0b0; -fx-font-style: italic;");
            emptyLabel.setAlignment(Pos.CENTER);
            cardContainer.getChildren().add(emptyLabel);
            return;
        }

        for (LockedItem item : programItems) {
            LockedItemCard card = new LockedItemCard(item);
            cardContainer.getChildren().add(card);
        }

        updateStatusLabel();
    }

    private void updateStatusLabel() {
        long totalItems = programItems.size();
        long lockedItems = programItems.stream().mapToLong(item -> item.isLocked() ? 1 : 0).sum();
        long selectedItems = programItems.stream().mapToLong(item -> item.isSelected() ? 1 : 0).sum();

        statusLabel.setText(String.format("Total: %d | Locked: %d | Selected: %d",
                totalItems, lockedItems, selectedItems));
    }

    @FXML
    private void addProgram() {
        logger.logInfo("Adding a program to be locked.");

        FileChooser fileChooser = FileSelectionHelper.createProgramFileChooser();
        File selectedFile = fileChooser.showOpenDialog(new Stage());

        if (selectedFile == null) {
            logger.logWarning("No program was selected to add.");
            return;
        }

        // Process the selected file using FileSelectionHelper
        List<File> selectedFiles = List.of(selectedFile);
        FileSelectionHelper.ProcessedFileInfo processedInfo = FileSelectionHelper.processSelectedFiles(selectedFiles);

        // Get all program data
        Map<String, Object> allData = programStorage.getAllData();
        boolean changesWereMade = false;

        // Add successfully processed files
        for (FileSelectionHelper.ProcessedFileInfo.FileInfo fileInfo : processedInfo.getResolvedFiles()) {
            // Check if this executable is already in the list
            boolean alreadyExists = false;

            // Check if program already exists in storage
            for (Map.Entry<String, Object> entry : allData.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> itemData = (Map<String, Object>) entry.getValue();
                    if ("PROGRAM".equals(itemData.get("type"))) {
                        String path = (String) itemData.get("path");
                        if (path.equals(fileInfo.getExecutablePath())) {
                            alreadyExists = true;
                            break;
                        }
                    }
                }
            }

            if (!alreadyExists) {
                // Create program data
                Map<String, Object> programData = new HashMap<>();
                programData.put("path", fileInfo.getExecutablePath());
                programData.put("name", fileInfo.getExecutableName());
                programData.put("locked", false);
                programData.put("type", "PROGRAM");

                // Generate a new key for the program
                String newKey = "program_" + System.currentTimeMillis();

                // Add program to storage
                programStorage.put(newKey, programData);
                changesWereMade = true;

                logger.logInfo("Added program: " + fileInfo.getExecutableName() + " at " + fileInfo.getExecutablePath());
            } else {
                logger.logWarning("Program already exists in list: " + fileInfo.getExecutableName());
            }
        }

        // Log any failed files
        for (FileSelectionHelper.ProcessedFileInfo.FailedFile failedFile : processedInfo.getFailedFiles()) {
            logger.logError("Failed to process file: " + failedFile.getPath() + " - " + failedFile.getReason());
        }

        if (changesWereMade) {
            programStorage.saveData();
            refreshCardDisplay();
            updateCardDisplay();
        }
    }

    @FXML
    private void addMultiplePrograms() {
        logger.logInfo("Adding multiple programs to be locked.");

        FileChooser fileChooser = FileSelectionHelper.createMultipleProgramFileChooser();
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(new Stage());

        if (selectedFiles == null || selectedFiles.isEmpty()) {
            logger.logWarning("No programs were selected to add.");
            return;
        }

        // Process all selected files using FileSelectionHelper
        FileSelectionHelper.ProcessedFileInfo processedInfo = FileSelectionHelper.processSelectedFiles(selectedFiles);

        // Get all program data
        Map<String, Object> allData = programStorage.getAllData();
        boolean changesWereMade = false;

        int addedCount = 0;
        int skippedCount = 0;

        // Add successfully processed files
        for (FileSelectionHelper.ProcessedFileInfo.FileInfo fileInfo : processedInfo.getResolvedFiles()) {
            // Check if this executable is already in the list
            boolean alreadyExists = false;

            // Check if program already exists in storage
            for (Map.Entry<String, Object> entry : allData.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> itemData = (Map<String, Object>) entry.getValue();
                    if ("PROGRAM".equals(itemData.get("type"))) {
                        String path = (String) itemData.get("path");
                        if (path.equals(fileInfo.getExecutablePath())) {
                            alreadyExists = true;
                            break;
                        }
                    }
                }
            }

            if (!alreadyExists) {
                // Create program data
                Map<String, Object> programData = new HashMap<>();
                programData.put("path", fileInfo.getExecutablePath());
                programData.put("name", fileInfo.getExecutableName());
                programData.put("locked", true); // Default to locked for batch add
                programData.put("type", "PROGRAM");

                // Generate a new key for the program
                String newKey = "program_" + System.currentTimeMillis() + "_" + addedCount;

                // Add program to storage
                programStorage.put(newKey, programData);
                changesWereMade = true;

                logger.logInfo("Added program: " + fileInfo.getExecutableName() + " at " + fileInfo.getExecutablePath());
                addedCount++;
            } else {
                logger.logWarning("Program already exists in list: " + fileInfo.getExecutableName());
                skippedCount++;
            }
        }

        // Log any failed files
        for (FileSelectionHelper.ProcessedFileInfo.FailedFile failedFile : processedInfo.getFailedFiles()) {
            logger.logError("Failed to process file: " + failedFile.getPath() + " - " + failedFile.getReason());
        }

        logger.logInfo(String.format("Batch add completed: %d added, %d skipped, %d failed",
                addedCount, skippedCount, processedInfo.getFailedFiles().size()));

        if (changesWereMade) {
            programStorage.saveData();
            refreshCardDisplay();
            updateCardDisplay();
        }
    }

    @FXML
    private void removeProgram() {
        logger.logInfo("Removing selected programs.");

        List<LockedItem> selectedPrograms = programItems.stream()
                .filter(LockedItem::isSelected)
                .collect(Collectors.toList());

        if (selectedPrograms.isEmpty()) {
            logger.logWarning("No programs selected for removal.");
            return;
        }

        // Get all program data
        Map<String, Object> allData = programStorage.getAllData();

        // Find and remove selected programs
        for (String key : new ArrayList<>(allData.keySet())) {
            if (allData.get(key) instanceof Map) {
                Map<String, Object> itemData = (Map<String, Object>) allData.get(key);
                if ("PROGRAM".equals(itemData.get("type"))) {
                    String path = (String) itemData.get("path");

                    // Check if this program is selected for removal
                    for (LockedItem selectedProgram : selectedPrograms) {
                        if (selectedProgram.getPath().equals(path)) {
                            programStorage.remove(key);
                            logger.logInfo("Removed program: " + selectedProgram.getName());
                            break;
                        }
                    }
                }
            }
        }

        programStorage.saveData();
        refreshCardDisplay();
        updateCardDisplay();
    }

    @FXML
    private void swapPLockStatus() {
        logger.logInfo("Toggling lock status for selected programs.");

        // Get selected programs
        List<LockedItem> selectedPrograms = programItems.stream()
                .filter(LockedItem::isSelected)
                .collect(Collectors.toList());

        if (selectedPrograms.isEmpty()) {
            logger.logWarning("No programs selected for toggling lock status.");
            return;
        }

        // Get all program data
        Map<String, Object> allData = programStorage.getAllData();

        // Find and update selected programs
        for (String key : allData.keySet()) {
            if (allData.get(key) instanceof Map) {
                Map<String, Object> itemData = (Map<String, Object>) allData.get(key);
                if ("PROGRAM".equals(itemData.get("type"))) {
                    String path = (String) itemData.get("path");

                    // Check if this program is selected for toggling
                    for (LockedItem selectedProgram : selectedPrograms) {
                        if (selectedProgram.getPath().equals(path)) {
                            // Toggle locked status
                            boolean currentLocked = (boolean) itemData.get("locked");
                            itemData.put("locked", !currentLocked);

                            // Update in storage
                            programStorage.put(key, itemData);

                            // Update in memory
                            selectedProgram.setLocked(!currentLocked);
                            selectedProgram.setSelected(false);

                            logger.logInfo("Toggled lock status for program: " + selectedProgram.getName());
                            break;
                        }
                    }
                }
            }
        }

        programStorage.saveData();
        updateCardDisplay();
    }

    @FXML
    private void selectAll() {
        programItems.forEach(item -> item.setSelected(true));
        updateStatusLabel();
    }

    @FXML
    private void deselectAll() {
        programItems.forEach(item -> item.setSelected(false));
        updateStatusLabel();
    }
}
