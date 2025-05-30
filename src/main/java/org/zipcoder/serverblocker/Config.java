package org.zipcoder.serverblocker;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {

    public static class Common {
        public final ForgeConfigSpec.ConfigValue<String> rejectionMessage;
        public final ForgeConfigSpec.ConfigValue<String> discordWebhookUrl;
        public final ForgeConfigSpec.ConfigValue<String> serverName;

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment("General server config").push("server");

            rejectionMessage = builder
                    .comment("Message shown to rejected players")
                    .define("rejectionMessage", "Hang tight! The server is starting up!");

            discordWebhookUrl = builder
                    .comment("Discord Webhook URL for join notifications")
                    .define("discordWebhookUrl",
                            "https://discordapp.com/api/webhooks/1377479606822506656/F8vv6GxZrJ8VAs8sO7ZKAlRTKDHOaqRP2LJrjZgDPY3iw4WLf24SUFJc8aFL1UYg3Uen");

            serverName = builder
                    .comment("Name of the server")
                    .define("serverName",
                            "Vanilla");



            builder.pop();
        }
    }

    public static final ForgeConfigSpec SERVER_CONFIG;
    public static final Common SERVER;

    static {
        final Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        SERVER_CONFIG = specPair.getRight();
        SERVER = specPair.getLeft();
    }
}
