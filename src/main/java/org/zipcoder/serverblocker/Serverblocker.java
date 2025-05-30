package org.zipcoder.serverblocker;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

import java.io.IOException;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Serverblocker.MODID)
public class Serverblocker {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "serverblocker";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();


    public Serverblocker() {
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SERVER_CONFIG);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        LOGGER.info("HELLO from player logging in");
        ServerPlayer player = (ServerPlayer) event.getEntity();
        String name = player.getName().getString();

        DiscordWebhook webhook  =new DiscordWebhook(Config.SERVER.discordWebhookUrl.get());
        webhook.setUsername("Server Entrance Bot");
        webhook.setContent("**" + name
                + "** has attempted to join server **"
                + Config.SERVER.serverName.get() + "**.");
        try {
            webhook.execute();
        } catch (IOException e) {
            System.out.println("Failed to send webhook: " + e.getMessage());
        }
        player.connection.disconnect(
                Component.literal(Config.SERVER.rejectionMessage.get()));
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
