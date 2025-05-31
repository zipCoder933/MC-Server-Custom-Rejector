package com.zipCoder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Config {
    public String discordWebhook = "https://discordapp.com/api/webhooks/1377479606822506656/F8vv6GxZrJ8VAs8sO7ZKAlRTKDHOaqRP2LJrjZgDPY3iw4WLf24SUFJc8aFL1UYg3Uen";
    public String rejectMessage = "§cYou are not allowed to join this server.";
    public Server[] servers = new Server[]{
            new Server("Vanilla", 25565)
    };

    // === Internal stuff ===
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SETTINGS_PATH = Paths.get(System.getProperty("user.dir"), "config.json");

    private static void save(Config settings) {
        try (Writer writer = Files.newBufferedWriter(SETTINGS_PATH)) {
            GSON.toJson(settings, writer);
        } catch (IOException e) {
            System.err.println("Error saving settings: " + e.getMessage());
        }
    }

    public static Config loadConfig() {
        if (Files.exists(SETTINGS_PATH)) {
            try (Reader reader = Files.newBufferedReader(SETTINGS_PATH)) {
                return GSON.fromJson(reader, (Type) Config.class);

            } catch (IOException e) {
                System.err.println("Error loading settings: " + e.getMessage());
            }
        } else {
            save(new Config()); // Save defaults if no file
        }
        return new Config();
    }

}
