module me.ghosthacks96.ghostsecure {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;
    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;

    opens me.ghosthacks96.ghostsecure to javafx.fxml, com.google.gson;
    exports me.ghosthacks96.ghostsecure;
    exports me.ghosthacks96.ghostsecure.utils;
}
