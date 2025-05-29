package org.zipcoder.serverBlocker;

import com.google.gson.Gson;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;

public final class ServerBlocker extends JavaPlugin implements Listener {

    private PluginConfig config;

    @Override
    public void onEnable() {
        config = loadOrCreateConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Plugin enabled. Server name: " + config.server_name);
    }

    private PluginConfig loadOrCreateConfig() {
        getLogger().info("Loading config.json");
        File file = new File("config.json");
        Gson gson = new Gson();

        if (!file.exists()) {
            try (FileWriter out = new FileWriter(file)) {
                gson.toJson(new PluginConfig(), out);
                getLogger().info("Created default config.json");
            } catch (Exception e) {
                getLogger().severe("Failed to create config.json: " + e.getMessage());
            }
        }

        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, PluginConfig.class);
        } catch (Exception e) {
            getLogger().severe("Failed to load config.json: " + e.getMessage());
            return new PluginConfig();
        }
    }


    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, config.custom_message);

        //send webhook
        DiscordWebhook webhook = new DiscordWebhook(config.discord_webhook_url);
        webhook.setUsername("Server Entrance Bot");
        webhook.setContent("**" + event.getPlayer().getName() + "** has attempted to join server **" + config.server_name + "**.");
        try {
            webhook.execute();
        } catch (IOException e) {
            System.out.println("Failed to send webhook: " + e.getMessage());
        }

    }
}
