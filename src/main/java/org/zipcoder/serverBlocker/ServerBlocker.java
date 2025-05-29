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
        saveDefaultJsonConfig("config.json");
        loadJsonConfig();

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Plugin enabled. Server name: " + config.server_name);
    }

    private void saveDefaultJsonConfig(String filename) {
        File configFile = new File(getDataFolder(), filename);
        if (!configFile.exists()) {
            getDataFolder().mkdirs();
            try (Reader in = new InputStreamReader(getResource(filename));
                 FileWriter out = new FileWriter(configFile)) {
                in.transferTo(out);
            } catch (Exception e) {
                getLogger().severe("Failed to save default config.json: " + e.getMessage());
            }
        }
    }

    private void loadJsonConfig() {
        File configFile = new File(getDataFolder(), "config.json");
        try (FileReader reader = new FileReader(configFile)) {
            this.config = new Gson().fromJson(reader, PluginConfig.class);
        } catch (Exception e) {
            getLogger().severe("Failed to load config.json: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
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
            e.printStackTrace();
        }

    }
}
