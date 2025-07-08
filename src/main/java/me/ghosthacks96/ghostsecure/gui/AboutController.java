package me.ghosthacks96.ghostsecure.gui;

import javafx.fxml.FXML;
import me.ghosthacks96.ghostsecure.Main;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static me.ghosthacks96.ghostsecure.Main.logger;

public class AboutController {




    @SuppressWarnings("ResultOfMethodCallIgnored")
    @FXML
    private void openAppDataFolder() {
        try {
            File appDataDir = new File(Main.APP_DATA_PATH);
            if (!appDataDir.exists()) {
                appDataDir.mkdirs();
            }

            openDirectory(appDataDir);
        } catch (IOException e) {
            logger.logError("Failed to open AppData folder: " + e.getMessage());
        }
    }

    @FXML
    private void openDiscordInvite() {
        openUrl("https://discord.gg/Pn5U4whfnd", "Discord");
    }

    @FXML
    private void openGitHubRepo() {
        openUrl("https://github.com/ghosthacks96/ghostsecure", "GitHub");
    }

    private void openDirectory(File directory) throws IOException {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(directory);
        } else {
            new ProcessBuilder("explorer.exe", directory.getAbsolutePath()).start();
        }
    }

    private void openUrl(String url, String serviceName) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                new ProcessBuilder("cmd", "/c", "start", url).start();
            }
        } catch (IOException | URISyntaxException e) {
            logger.logError("Failed to open " + serviceName + " link: " + e.getMessage());
        }
    }


}
