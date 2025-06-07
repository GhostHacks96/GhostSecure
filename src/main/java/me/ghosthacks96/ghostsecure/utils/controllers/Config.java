package me.ghosthacks96.ghostsecure.utils.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.itemTypes.LockedItem;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Config {

    Main main;

    public static String appDataPath = System.getenv("APPDATA") + "/ghosthacks96/GhostSecure/";
    public static JsonObject config;
    public static String PASSWORD_HASH = "";
    public static Gson gson = new Gson();
    public static Logging logger;

    public static List<LockedItem> lockedItems = new ArrayList<>();

    public Config(Main main) {
        this.main = main;
        logger = main.logger;
    }

    public JsonObject getJsonConfig() {
        return config;
    }

    public File getConfigFile() {
        return new File(appDataPath + "config.json");
    }
    public void setDefaultConfig()  throws Exception{
        config = new JsonObject();
        config.addProperty("mode", "unlock");
        config.add("programs", new JsonArray());
        config.add("folders", new JsonArray());
        config.addProperty("password", "");
        lockedItems.clear();
        saveConfig();
        logger.logInfo("Default config created successfully.");

        if(!new File(appDataPath).exists()) {
            if(!new File(appDataPath).mkdirs()) {
                throw new IOException("Failed to create AppData directory");
            }
            if(!new File(appDataPath + "config.json").createNewFile()) {
                throw new IOException("Failed to create config file");
            }
            logger.logInfo("Default config file created successfully.");
        }
    }

    public static void loadConfig(boolean service) {
        try {
            config = gson.fromJson(new JsonReader(new FileReader(appDataPath + "config.json")), JsonObject.class);

            if (config.has("password")) {
                PASSWORD_HASH = config.get("password").getAsString();
            }

            if (!config.has("mode")) {
                config.addProperty("mode", "unlock");
            }
            loadLockedItemsFromConfig(service);

            if(!service)logger.logInfo("Config loaded successfully.");
        } catch (Exception e) {
            if(!service)logger.logWarning("No config file found. A new setup will be required.");
            e.printStackTrace();
        }
    }

    private static void loadLockedItemsFromConfig(boolean service) {
        try {
            if(!lockedItems.isEmpty()) lockedItems.clear();
            if (config.has("programs")) {
                JsonArray programs = config.getAsJsonArray("programs");
                for (int i = 0; i < programs.size(); i++) {
                    String programEntry = programs.get(i).getAsString();
                    lockedItems.add(parseLockedItem(programEntry));
                }
            }

            if (config.has("folders")) {
                JsonArray folders = config.getAsJsonArray("folders");
                for (int i = 0; i < folders.size(); i++) {
                    String folderEntry = folders.get(i).getAsString();
                    lockedItems.add(parseLockedItem(folderEntry));
                }
            }
            if(!service)logger.logInfo("Locked items loaded successfully from config.");
        } catch (Exception e) {
            logger.logError("Error loading locked items from config: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public static void printConfig() {
        if (config != null) {
            // Convert the JsonObject to a formatted JSON string
            String configJson = gson.toJson(config);

            // Print the JSON string
            System.out.println("Current Config Data:");
            System.out.println(configJson);

            // Optional: Log it using your logger
            logger.logInfo("Current Config Data: " + configJson);
        } else {
            System.out.println("Config object is null. No data to display.");
            logger.logWarning("Config object is null. No data to display.");
        }
    }

    private static boolean containsItem(JsonArray jsonArray, String path) {
        for (int i = 0; i < jsonArray.size(); i++) {
            String existingEntry = jsonArray.get(i).getAsString();
            // Compare only the raw path part (before `[::]`)
            if (existingEntry.startsWith(path + "[::]")) {
                return true;
            }
        }
        return false;
    }


    public static void saveConfig() {
        try {
            JsonArray programsArray = config.getAsJsonArray("programs");
            JsonArray foldersArray = config.getAsJsonArray("folders");

            // Ensure "programs" and "folders" arrays exist in the config
            if (programsArray == null) {
                programsArray = new JsonArray();
                config.add("programs", programsArray);
            }
            if (foldersArray == null) {
                foldersArray = new JsonArray();
                config.add("folders", foldersArray);
            }
            for (LockedItem item : lockedItems) {

                // Determine the formatted string for the item
                String formattedPath = item.getPath() + "[::]" + (item.isLocked() ? "locked" : "unlocked");

                if (item.getName().contains(".exe")) {
                    // If the item is a program, check if it's already in "programs" and add only if it's not
                    if (!containsItem(programsArray, item.getPath())) {
                        programsArray.add(formattedPath);
                    }
                } else {
                    // If the item is a folder, check if it's already in "folders" and add only if it's not
                    if (!containsItem(foldersArray, item.getPath())) {
                        foldersArray.add(formattedPath);
                    }
                }
            }

            File appDataDir = new File(appDataPath);
            if (!appDataDir.exists() && !appDataDir.mkdirs()) {
                throw new IOException("Failed to create AppData directory");
            }

            File configFile = new File(appDataDir, "config.json");
            printConfig();

            try (FileWriter writer = new FileWriter(configFile)) {

                writer.write(gson.toJson(config));
                logger.logInfo("Config saved successfully.");
            }
        } catch (Exception e) {
            logger.logError("Error saving config file: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static LockedItem parseLockedItem(String input) {
        try {
            String[] parts = input.split("\\[::\\]");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid format for locked item: " + input);
            }
            String path = parts[0];
            String lockStatus = parts[1];
            String name = path.substring(path.lastIndexOf("\\") + 1);
            return new LockedItem(path, name, lockStatus.equalsIgnoreCase("locked"));
        } catch (IllegalArgumentException e) {
            logger.logError("Error parsing locked item: " + input + " - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

}
