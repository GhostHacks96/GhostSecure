module me.ghosthacks96.applocker {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;
    requires org.kordamp.bootstrapfx.core;

    // Export the base package for other modules (not reflection specific)
    exports me.ghosthacks96.applocker;

    // Open the utils package to javafx.base for reflection purposes
    opens me.ghosthacks96.applocker.utils to javafx.base;

    // Open other packages if reflection is required
    opens me.ghosthacks96.applocker to javafx.fxml;
}