package me.ghosthacks96.ghostsecure.utils.debug;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;

import java.util.Objects;

public class DebugConsole {
    private static DebugConsole instance;
    private TextFlow consoleFlow;
    private ScrollPane scrollPane;
    private Stage consoleStage;
    private VBox consoleContainer;

    private static final String DARK_THEME_CSS = "/me/ghosthacks96/ghostsecure/dark-theme.css";
    private static final String APP_ICON_PATH = "/me/ghosthacks96/ghostsecure/app_icon.png";

    private DebugConsole() {
        setupConsole();
        Runtime.getRuntime().addShutdownHook(new Thread(this::killConsole, "DebugConsoleShutdownHook"));
    }

    public void killConsole() {
        if (consoleStage != null) {
            Platform.runLater(() -> {
                consoleStage.hide();
                consoleStage.close();
            });
        }
        System.setOut(System.out); // Restore original System.out
    }

    public static DebugConsole getInstance() {
        if (instance == null) {
            instance = new DebugConsole();
        }
        return instance;
    }

    private void setupConsole() {
        // Create header
        Label headerLabel = new Label("🔍 Debug Console");
        headerLabel.getStyleClass().add("debug-console-header");

        // Create console flow for rich text support
        consoleFlow = new TextFlow();
        consoleFlow.getStyleClass().add("debug-console-flow");

        // Create container for the console flow
        consoleContainer = new VBox();
        consoleContainer.getChildren().add(consoleFlow);
        consoleContainer.getStyleClass().add("debug-console-container");

        // Create scroll pane
        scrollPane = new ScrollPane(consoleContainer);
        scrollPane.getStyleClass().add("debug-console-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPrefViewportHeight(400);
        scrollPane.setPrefViewportWidth(780);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        // Create control buttons using CSS classes
        Button clearButton = new Button("CLEAR");
        clearButton.getStyleClass().addAll("button", "warning", "debug-console-button");
        clearButton.setOnAction(e -> clearConsole());

        Button closeButton = new Button("CLOSE");
        closeButton.getStyleClass().addAll("button", "danger", "debug-console-button");
        closeButton.setOnAction(e -> hideConsole());

        // Create header with buttons
        HBox headerBox = new HBox(20);
        headerBox.getStyleClass().add("debug-console-header-box");
        headerBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        headerBox.getChildren().addAll(headerLabel, spacer, clearButton, closeButton);

        VBox root = new VBox();
        root.getStyleClass().add("debug-console-root");
        root.getChildren().addAll(headerBox, scrollPane);
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

        Scene scene = new Scene(root, 800, 500);

        // Load the dark theme CSS
        try {
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource(DARK_THEME_CSS)).toExternalForm()
            );

        } catch (Exception e) {
            System.err.println("Could not load dark-theme.css: " + e.getMessage());
        }

        consoleStage = new Stage();
        consoleStage.setTitle("GhostSecure - Debug Console");
        consoleStage.getIcons().add(
                new Image(Objects.requireNonNull(getClass().getResource(APP_ICON_PATH)).toExternalForm())
        );
        consoleStage.setScene(scene);
    }

    private void addColoredMessage(String level, String message, String timestamp) {
        // Create timestamp text
        Text timestampText = new Text("[" + timestamp + "] ");
        timestampText.getStyleClass().add("debug-console-timestamp");

        // Determine message type and create styled text
        Text messageText = new Text();
        messageText.getStyleClass().add("debug-console-message");

        String cleanMessage = message.trim();

        if (level.contains("DEBUG")) {
            messageText.setText("🔍 [DEBUG] " + cleanMessage + "\n");
            messageText.getStyleClass().add("debug-console-debug");
        } else if (level.contains("INFO")) {
            messageText.setText("ℹ [INFO] " + cleanMessage + "\n");
            messageText.getStyleClass().add("debug-console-info");
        } else if (level.contains("WARNING")) {
            messageText.setText("⚠ [WARNING] " + cleanMessage + "\n");
            messageText.getStyleClass().add("debug-console-warning");
        } else if (level.contains("ERROR")) {
            messageText.setText("❌ [ERROR] " + cleanMessage + "\n");
            messageText.getStyleClass().add("debug-console-error");
        } else {
            // Default message
            messageText.setText(cleanMessage + "\n");
            messageText.getStyleClass().add("debug-console-default");
        }

        // Add to console flow
        consoleFlow.getChildren().addAll(timestampText, messageText);

        // Limit number of messages to prevent memory issues
        if (consoleFlow.getChildren().size() > 2000) { // 1000 messages (2 Text nodes each)
            consoleFlow.getChildren().remove(0, 200); // Remove oldest 100 messages
        }
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            scrollPane.setVvalue(1.0);
        });
    }

    public void showConsole() {
        Platform.runLater(() -> {
            DebugConsole instance = getInstance();
            if (instance.consoleStage != null) {
                instance.consoleStage.show();
                instance.consoleStage.toFront();
            }
        });
    }

    public void hideConsole() {
        consoleStage.hide();
        consoleStage.close();
    }

    public void clearConsole() {
        Platform.runLater(() -> consoleFlow.getChildren().clear());
    }

    // Method to be called by the Logging class to add messages to the console
    public void addLogMessage(String level, String message, String timestamp) {
        if (!Main.DEBUG_MODE) {
            return;
        }

        Platform.runLater(() -> {
            addColoredMessage(level, message, timestamp);
            scrollToBottom();
        });
    }
}