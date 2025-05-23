module me.ghosthacks96.applocker {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;
    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;

    opens me.ghosthacks96.applocker to javafx.fxml, com.google.gson;
    exports me.ghosthacks96.applocker;
    exports me.ghosthacks96.applocker.utils;
}
