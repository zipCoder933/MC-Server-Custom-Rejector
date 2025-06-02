package com.zipCoder;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static com.zipCoder.PacketUtils.*;

public class FakeMinecraftServer {

    static Config config;
    public final static Logger LOGGER = Logger.getLogger(FakeMinecraftServer.class.getName());
    public final static int DEFAULT_PROTOCOL_VERSION = 763;

    static {
        try {
            FileHandler fileHandler = new FileHandler("latest.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error setting up file handler", e);
        }
    }

    static {
        try {
            config = Config.loadConfig();
        } catch (Exception e) {
            System.err.println("Error loading settings: " + e.getMessage());
            config = new Config();
        }
    }

    static String version = "watcher v1.5.0";

    private static void packetLog(String ke, Object value) {
        String message = String.format("\t%s: %s", ke, value);
        System.out.println(message);
    }

    public static void main(String[] args) {
        System.out.println(version);

        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                MemoryUtils::handleMemory, 0, 1, java.util.concurrent.TimeUnit.MINUTES);

        for (Server server : config.servers) {
            (new Thread(() -> runServer(server))).start();
        }
    }

    public static void runServer(Server server) {
        int packetLength = 0;
        int packetId = 0;
        int protocolVersion = DEFAULT_PROTOCOL_VERSION;
        int serverPort;
        String serverAddress;
        int nextState = 1;

        try (ServerSocket serverSocket = new ServerSocket(server.port)) {
            System.out.println("Fake server running on port " + server.port);
            while (true) {
                packetLength = 0;
                packetId = 0;
                protocolVersion = DEFAULT_PROTOCOL_VERSION;
                serverPort = 0;
                serverAddress = null;
                nextState = 1;

                try (Socket socket = serverSocket.accept()) {
                    System.out.println("Connection from " + socket.getInetAddress());

                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                    try {
                        // === Handshake Packet ===
                        packetLength = readVarInt(in);
                        packetLog("packetLength", packetLength);
                        packetId = readVarInt(in);  // Should be 0 (Handshake)
                        packetLog("packetId", packetId);
                        protocolVersion = readVarInt(in);  // Protocol version
                        serverAddress = readString(in);
                        serverPort = in.readUnsignedShort();
                        nextState = readVarInt(in);  // 1 = status, 2 = login
                        packetLog("nextState", nextState);


                        if (nextState == 1 && config.handleStatusRequests) {
                            /**
                             * Status Request
                             *
                             *
                             */
                            int statusLength;
                            int statusPacketId = 0x00;
                            int pingLength;
                            int pingPacketId = 0x01;

                            try {
                                statusLength = readVarInt(in);
                                statusPacketId = readVarInt(in);
                            } finally {
                                if (statusPacketId == 0x00) {
                                    respondStatus(server, protocolVersion, out);
                                }
                            }

                            if (in.available() > 0) {
                                try {
                                    pingLength = readVarInt(in);
                                    pingPacketId = readVarInt(in);
                                } finally {//Send a pong
                                    if (pingPacketId == 0x01) {
                                        respondPong(in, out);
                                    }
                                }
                            }

                        } else if (nextState == 2) {
                            /**
                             * Login request
                             *
                             *
                             */
                            String username = "Unknown";
                            try {
                                // === Send Login Disconnect ===
                                respondLoginDisconnect(out, config.rejectMessage);

                                // === Login Start Packet ===
                                //We dont need to read the packet before we respond
                                packetLength = readVarInt(in);
                                packetId = readVarInt(in);  // Should be 0 (Login Start)
                                username = readString(in);
                            } catch (Exception e) {
                                LOGGER.log(Level.SEVERE, "Error responding to login request" + e.getMessage());
                            } finally {
                                try { // === Send discord webhook ===
                                    DiscordWebhook webhook = new DiscordWebhook(config.discordWebhook);
                                    webhook.setUsername("Watcher App");
                                    webhook.setContent("Player **" + username + "** wants to join server **" + server.name + "**");
                                    webhook.execute();
                                } catch (Exception e) {
                                    LOGGER.log(Level.SEVERE, "Error sending webhook" + e.getMessage());
                                }
                            }
                        }
                    } catch (EOFException e) {
                        LOGGER.log(Level.SEVERE, "Error reading packet (Sending status response)", e);
                        //We frequently get EOF exceptions here, we need to be ready for them
                        //We cant know what the request was, so we just send all responses
//                        respondLoginDisconnect(out, e.getMessage());
                        respondStatus(server, protocolVersion, out);
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Error handling packet", e);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error handling packet", e);
                } finally {
                    System.out.println("\tDone.\n");
                }
            }


        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error running server loop. Port: " + server.port, e);

            try {
                DiscordWebhook webhook = new DiscordWebhook(config.discordWebhook);
                webhook.setUsername("Watcher App");
                webhook.setContent("Watcher app has encountered an error on port **" + server.port + "**");
                webhook.execute();
            } catch (Exception ignored) {
                LOGGER.log(Level.SEVERE, "Error sending webhook" + e.getMessage());
            }
        }
    }

    private static void respondPong(DataInputStream in, DataOutputStream out) {
        try {
            long payload = 0;
            if (in.available() > 0) {
                payload = in.readLong();
            }
            ByteArrayOutputStream pingBuffer = new ByteArrayOutputStream();
            DataOutputStream pingPacket = new DataOutputStream(pingBuffer);
            pingPacket.writeByte(0x01); // Pong Response ID
            pingPacket.writeLong(payload);
            sendPacket(out, pingBuffer.toByteArray());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error responding with pong" + e.getMessage());
        }
    }


    private static void respondLoginDisconnect(DataOutputStream out, String message) {
        try {
            String json = "{\"text\":\"" + message + "\"}";
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream packet = new DataOutputStream(buffer);


            packet.writeByte(0x00); // Login Disconnect packet ID
            writeString(packet, json);
            sendPacket(out, buffer.toByteArray());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error responding with disconnect" + e.getMessage());
        }
    }

    private static void respondStatus(Server server, int protocolVersion, DataOutputStream out) {
        try {
            String responseJson = "{"
                    + "\"version\":{\"name\":\"" + server.version + "\",\"protocol\":" + protocolVersion + "},"
                    + "\"players\":{\"max\":" + server.maxPlayers + ",\"online\":0,\"sample\":[]},"
                    + "\"description\":{\"text\":\"" + server.title + "\"}"
                    + "}";
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream packet = new DataOutputStream(buffer);


            packet.writeByte(0x00); // Status Response ID
            writeString(packet, responseJson);
            sendPacket(out, buffer.toByteArray());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error responding with status" + e.getMessage());
        }
    }
}
