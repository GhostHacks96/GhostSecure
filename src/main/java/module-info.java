module ghostsecure {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;
    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;
    requires java.net.http;

    opens me.ghosthacks96.ghostsecure to javafx.fxml, com.google.gson;
    exports me.ghosthacks96.ghostsecure;
    exports me.ghosthacks96.ghostsecure.itemTypes;
    exports me.ghosthacks96.ghostsecure.utils.services;
    exports me.ghosthacks96.ghostsecure.gui;
    opens me.ghosthacks96.ghostsecure.gui to com.google.gson, javafx.fxml;
    exports me.ghosthacks96.ghostsecure.utils;
    exports me.ghosthacks96.ghostsecure.utils.debug;
    exports me.ghosthacks96.ghostsecure.utils.file_handlers;
    exports me.ghosthacks96.ghostsecure.utils.api_handlers;
    exports me.ghosthacks96.ghostsecure.utils.encryption;
}
