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
import java.util.Arrays;

public class Config {
    public String discordWebhook = "https://discordapp.com/api/webhooks/1382226893104873613/HlmbOW03_fXYDL4FWKBaBhHwddT938srdyrmR6u6MVM6bGM1Js4cwcaj1iEaNeKZ8EXg";
    public String rejectMessage = "§aHang tight! The server should be up and running soon.";
    public Server[] servers = new Server[]{
            new Server("Vanilla", 25565, "1.20.1",  "A Minecraft Server", 20)
    };
    public boolean handleStatusRequests = true;


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
                Config config = GSON.fromJson(reader, (Type) Config.class);
                return config;
            } catch (IOException e) {
                System.out.println("Error loading settings: " + e.getMessage());
                return new Config();
            }
        } else {
            System.out.println("No settings file found, creating one...");
            save(new Config()); // Save defaults if no file
            return new Config();
        }
    }

    public String toString() {
        return "Config{" +
                "\ndiscordWebhook='" + discordWebhook +
                "\nrejectMessage='" + rejectMessage +
                "\nservers=" + Arrays.toString(servers) +
                "\nhandleStatusRequests=" + handleStatusRequests +
                '}';
    }

}
