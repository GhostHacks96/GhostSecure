package me.ghosthacks96.ghostsecure.utils.controllers;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class DebugConsole {
    private static DebugConsole instance;
    private TextFlow consoleFlow;
    private ScrollPane scrollPane;
    private Stage consoleStage;
    private VBox consoleContainer;

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
        headerLabel.setStyle("""
                    -fx-font-size: 18px;
                    -fx-font-weight: bold;
                    -fx-text-fill: #00d4ff;
                    -fx-effect: dropshadow(gaussian, #00d4ff4d, 3, 0, 0, 0);
                    -fx-padding: 15 20 10 20;
                """);

        // Create console flow for rich text support
        consoleFlow = new TextFlow();
        consoleFlow.setStyle("""
                    -fx-background-color: #0a0a0a;
                    -fx-padding: 10;
                """);

        // Create container for the console flow
        consoleContainer = new VBox();
        consoleContainer.getChildren().add(consoleFlow);
        consoleContainer.setStyle("""
                    -fx-background-color: #0a0a0a;
                    -fx-border-color: #00d4ff33;
                    -fx-border-width: 1;
                    -fx-border-radius: 8;
                    -fx-background-radius: 8;
                """);

        // Create scroll pane
        scrollPane = new ScrollPane(consoleContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPrefViewportHeight(400);
        scrollPane.setPrefViewportWidth(780);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("""
                    -fx-background: #0a0a0a;
                    -fx-background-color: #0a0a0a;
                    -fx-border-color: #00d4ff33;
                    -fx-border-width: 1;
                    -fx-border-radius: 8;
                    -fx-background-radius: 8;
                """);

        // Create control buttons
        Button clearButton = new Button("CLEAR");
        clearButton.getStyleClass().add("warning");
        clearButton.setOnAction(e -> clearConsole());
        clearButton.setStyle("""
                    -fx-background-color: linear-gradient(to bottom right, #ffa502 0%, #ff6348 100%);
                    -fx-text-fill: #000000;
                    -fx-background-radius: 8;
                    -fx-font-weight: bold;
                    -fx-cursor: hand;
                    -fx-effect: dropshadow(gaussian, #0000004d, 4, 0, 0, 2);
                    -fx-pref-width: 80;
                    -fx-pref-height: 32;
                """);

        Button closeButton = new Button("CLOSE");
        closeButton.getStyleClass().add("danger");
        closeButton.setOnAction(e -> hideConsole());
        closeButton.setStyle("""
                    -fx-background-color: linear-gradient(to bottom right, #ff4757 0%, #c44569 100%);
                    -fx-text-fill: #000000;
                    -fx-background-radius: 8;
                    -fx-font-weight: bold;
                    -fx-cursor: hand;
                    -fx-effect: dropshadow(gaussian, #0000004d, 4, 0, 0, 2);
                    -fx-pref-width: 80;
                    -fx-pref-height: 32;
                """);

        // Add hover effects
        clearButton.setOnMouseEntered(e -> clearButton.setStyle(clearButton.getStyle() +
                "-fx-scale-x: 1.02; -fx-scale-y: 1.02; -fx-effect: dropshadow(gaussian, #ffa50266, 8, 0, 0, 3);"));
        clearButton.setOnMouseExited(e -> clearButton.setStyle("""
                    -fx-background-color: linear-gradient(to bottom right, #ffa502 0%, #ff6348 100%);
                    -fx-text-fill: #000000;
                    -fx-background-radius: 8;
                    -fx-font-weight: bold;
                    -fx-cursor: hand;
                    -fx-effect: dropshadow(gaussian, #0000004d, 4, 0, 0, 2);
                    -fx-pref-width: 80;
                    -fx-pref-height: 32;
                """));

        closeButton.setOnMouseEntered(e -> closeButton.setStyle(closeButton.getStyle() +
                "-fx-scale-x: 1.02; -fx-scale-y: 1.02; -fx-effect: dropshadow(gaussian, #ff475766, 8, 0, 0, 3);"));
        closeButton.setOnMouseExited(e -> closeButton.setStyle("""
                    -fx-background-color: linear-gradient(to bottom right, #ff4757 0%, #c44569 100%);
                    -fx-text-fill: #000000;
                    -fx-background-radius: 8;
                    -fx-font-weight: bold;
                    -fx-cursor: hand;
                    -fx-effect: dropshadow(gaussian, #0000004d, 4, 0, 0, 2);
                    -fx-pref-width: 80;
                    -fx-pref-height: 32;
                """));

        // Create header with buttons
        HBox headerBox = new HBox(20);
        headerBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        headerBox.getChildren().addAll(headerLabel, spacer, clearButton, closeButton);
        headerBox.setStyle("""
                    -fx-background-color: #000000f2;
                    -fx-border-color: #00d4ff40;
                    -fx-border-width: 0 0 2 0;
                    -fx-padding: 5 15 5 15;
                """);

        VBox root = new VBox();
        root.getChildren().addAll(headerBox, scrollPane);
        root.setStyle("""
                    -fx-background-color: linear-gradient(to bottom right, #0a0a0a 0%, #1a1a1a 100%);
                    -fx-spacing: 5;
                """);
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

        Scene scene = new Scene(root, 800, 500);

        consoleStage = new Stage();
        consoleStage.setTitle("GhostSecure - Debug Console");
        consoleStage.setScene(scene);
    }

    private void addColoredMessage(String level, String message, String timestamp) {
        // Create timestamp text
        Text timestampText = new Text("[" + timestamp + "] ");
        timestampText.setStyle("""
                -fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
                -fx-font-size: 12px;
                -fx-fill: #888888;
                """);

        // Determine message type and create styled text
        Text messageText = new Text();
        messageText.setStyle("""
                -fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
                -fx-font-size: 12px;
                """);

        String cleanMessage = message.trim();

        if (level.contains("DEBUG")) {
            messageText.setText("🔍 [DEBUG] " + cleanMessage + "\n");
            messageText.setStyle(messageText.getStyle() + "-fx-fill: #00d4ff;"); // Cyan for debug
        } else if (level.contains("INFO")) {
            messageText.setText("ℹ [INFO] " + cleanMessage + "\n");
            messageText.setStyle(messageText.getStyle() + "-fx-fill: #00ff88;"); // Green for info
        } else if (level.contains("WARNING")) {
            messageText.setText("⚠ [WARNING] " + cleanMessage + "\n");
            messageText.setStyle(messageText.getStyle() + "-fx-fill: #ffaa00;"); // Orange for warning
        } else if (level.contains("ERROR")) {
            messageText.setText("❌ [ERROR] " + cleanMessage + "\n");
            messageText.setStyle(messageText.getStyle() + "-fx-fill: #ff4444;"); // Red for error
        } else {
            // Default message
            messageText.setText(cleanMessage + "\n");
            messageText.setStyle(messageText.getStyle() + "-fx-fill: #ffffff;"); // White for default
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