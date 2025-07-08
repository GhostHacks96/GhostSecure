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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.gui.extras.LockedItemCard;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static me.ghosthacks96.ghostsecure.Main.config;
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

    public static void refreshCardDisplay() {
        logger.logInfo("Refreshing card display.");
        programItems.setAll(config.lockedItems.stream()
                .filter(item -> item.getPath().endsWith(".exe"))
                .collect(Collectors.toList()));
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

        config.lockedItems.add(lockedProgram);
        config.saveConfig();
        refreshCardDisplay();
        updateCardDisplay();
        logger.logInfo("Added program: " + selectedFile.getName());
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

        config.lockedItems.removeAll(selectedPrograms);
        config.saveConfig();
        refreshCardDisplay();
        updateCardDisplay();

        selectedPrograms.forEach(item ->
                logger.logInfo("Removed program: " + item.getName())
        );
    }

    @FXML
    private void swapPLockStatus() {
        logger.logInfo("Toggling lock status for selected programs.");

        programItems.stream()
                .filter(LockedItem::isSelected)
                .forEach(item -> {
                    item.setLocked(!item.isLocked());
                    item.setSelected(false);
                    logger.logInfo("Toggled lock status for program: " + item.getName());
                });

        config.saveConfig();
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

    private File selectExecutableFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Program to Lock");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Executable Files", "*.exe")
        );
        return fileChooser.showOpenDialog(new Stage());
    }
}