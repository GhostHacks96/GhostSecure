package me.ghosthacks96.ghostsecure.gui.extras;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

/**
 * Controller for the splash screen
 */
public class SplashGUI {

    @FXML private ImageView appIcon;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;

    private Stage stage;

    @FXML
    public void initialize() {
        // Initialization code if needed
    }

    /**
     * Sets the stage reference for this controller
     * @param stage The stage containing this controller's view
     */
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /**
     * Updates the status message displayed on the splash screen
     * @param message The message to display
     */
    public void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    /**
     * Updates the progress indicator value
     * @param progress The progress value (0.0 to 1.0), or -1 for indeterminate
     */
    public void updateProgress(double progress) {
        Platform.runLater(() -> progressIndicator.setProgress(progress));
    }

    /**
     * Closes the splash screen
     */
    public void close() {
        if (stage != null) {
            Platform.runLater(() -> stage.close());
        }
    }
}