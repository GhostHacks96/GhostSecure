module ghostsecure {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;
    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;
    requires java.net.http;
    requires org.yaml.snakeyaml;

    opens me.ghosthacks96.ghostsecure to javafx.fxml, com.google.gson;
    exports me.ghosthacks96.ghostsecure;
    exports me.ghosthacks96.ghostsecure.itemTypes;
    exports me.ghosthacks96.ghostsecure.utils.services;
    exports me.ghosthacks96.ghostsecure.gui;
    opens me.ghosthacks96.ghostsecure.gui to com.google.gson, javafx.fxml;
    exports me.ghosthacks96.ghostsecure.utils.debug;
    exports me.ghosthacks96.ghostsecure.utils.file_handlers;
    exports me.ghosthacks96.ghostsecure.utils.api_handlers;
    exports me.ghosthacks96.ghostsecure.utils.encryption;
    exports me.ghosthacks96.ghostsecure.gui.tabs;
    opens me.ghosthacks96.ghostsecure.gui.tabs to com.google.gson, javafx.fxml;
    exports me.ghosthacks96.ghostsecure.gui.auth;
    opens me.ghosthacks96.ghostsecure.gui.auth to com.google.gson, javafx.fxml;
    exports me.ghosthacks96.ghostsecure.gui.extras;
    opens me.ghosthacks96.ghostsecure.gui.extras to com.google.gson, javafx.fxml;
    exports me.ghosthacks96.ghostsecure.utils.services.extras;
}
