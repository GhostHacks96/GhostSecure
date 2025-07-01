package me.ghosthacks96.ghostsecure.utils.controllers;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import me.ghosthacks96.ghostsecure.Main;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class DebugConsole {
    private static DebugConsole instance;
    private TextArea consoleArea;
    private Stage consoleStage;

    private DebugConsole() {
        setupConsole();
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

        // Create console area with dark theme styling and emoji-supporting font
        consoleArea = new TextArea();
        consoleArea.setEditable(false);
        consoleArea.setPrefRowCount(25);
        consoleArea.setPrefColumnCount(100);
        consoleArea.setWrapText(true);

        // Updated styling with fallback fonts that support emojis
        consoleArea.setStyle("""
                    -fx-background-color: #0a0a0a;
                    -fx-text-fill: #ffffff;
                    -fx-font-family: 'Segoe UI Emoji', 'Noto Color Emoji', 'Apple Color Emoji', 'Consolas', 'Monaco', 'Courier New', monospace;
                    -fx-font-size: 12px;
                    -fx-border-color: #00d4ff33;
                    -fx-border-width: 1;
                    -fx-border-radius: 8;
                    -fx-background-radius: 8;
                    -fx-control-inner-background: #1e1e1e;
                    -fx-padding: 10;
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
        root.getChildren().addAll(headerBox, consoleArea);
        root.setStyle("""
                    -fx-background-color: linear-gradient(to bottom right, #0a0a0a 0%, #1a1a1a 100%);
                    -fx-spacing: 0;
                """);
        VBox.setVgrow(consoleArea, javafx.scene.layout.Priority.ALWAYS);

        Scene scene = new Scene(root, 800, 500);

        consoleStage = new Stage();
        consoleStage.setTitle("GhostSecure - Debug Console");
        consoleStage.setScene(scene);

        // Redirect System.out to console with colored output and emoji filtering
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(new OutputStream() {
            private StringBuilder buffer = new StringBuilder();

            @Override
            public void write(int b) throws IOException {
                if (Main.DEBUG_MODE) {
                    if (b == '\n') {
                        String line = buffer.toString();
                        String timestamp = java.time.LocalTime.now().format(
                                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                        );
                        // Convert console format back to emoji format for debug console
                        String emojiLine = convertConsoleToEmojiFormat(line);
                        String formattedLine = String.format("[%s] %s\n", timestamp, emojiLine);

                        Platform.runLater(() -> {
                            consoleArea.appendText(formattedLine);
                            consoleArea.setScrollTop(Double.MAX_VALUE);
                        });
                        buffer.setLength(0);
                    } else {
                        buffer.append((char) b);
                    }
                }
                originalOut.write(b); // Also write to original console
            }
        }));
    }

    public void showConsole() {
        if (!consoleStage.isShowing()) {
            consoleStage.show();
        }
        consoleStage.toFront();
    }

    public void hideConsole() {
        consoleStage.hide();
        consoleStage.close();
    }

    public void clearConsole() {
        Platform.runLater(() -> consoleArea.clear());
    }

    // Helper method to convert console format back to emoji format for debug console
    private String convertConsoleToEmojiFormat(String consoleLine) {
        return consoleLine
                .replace("[DEBUG]", "🔍 [DEBUG]")
                .replace("[INFO]", "ℹ️ [INFO]")
                .replace("[WARNING]", "⚠️ [WARNING]")
                .replace("[ERROR]", "❌ [ERROR]");
    }

}