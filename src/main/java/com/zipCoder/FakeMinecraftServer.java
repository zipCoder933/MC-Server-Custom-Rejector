package com.zipCoder;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FakeMinecraftServer {

    static Config config;
    public final static Logger LOGGER = Logger.getLogger(FakeMinecraftServer.class.getName());

    static {
        try {
            config = Config.loadConfig();
        } catch (Exception e) {
            System.err.println("Error loading settings: " + e.getMessage());
            config = new Config();
        }
    }

    static String version = "watcher v1.4.0";

    private static void packetLog(String message) {
        System.out.println(message);
    }

    public static void main(String[] args) {
        System.out.println(version);

        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                FakeMinecraftServer::handleMemory, 0, 5, java.util.concurrent.TimeUnit.MINUTES);

        for (Server server : config.servers) {
            (new Thread(() -> runServer(server))).start();
        }
    }

    public static void runServer(Server server) {
        try (ServerSocket serverSocket = new ServerSocket(server.port)) {
            System.out.println("Fake server running on port " + server.port);

            while (true) {
                handleMemory();

                int packetLength = 0;
                int packetId;
                int protocolVersion = 700;
                int serverPort;
                String serverAddress;
                int nextState = 1;

                try (Socket socket = serverSocket.accept()) {
                    System.out.println("Connection from " + socket.getInetAddress());

                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                    // === Handshake Packet ===
                    packetLength = readVarInt(in);
                    packetId = readVarInt(in);  // Should be 0 (Handshake)

//                    if (packetId > 1) return;     //We can ignore packets we dont care about to prevent errors

                    protocolVersion = readVarInt(in);  // Protocol version
                    serverAddress = readString(in);
                    serverPort = in.readUnsignedShort();
                    nextState = readVarInt(in);  // 1 = status, 2 = login


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

//                        if (in.available() <= 0) return;
                        try {
                            pingLength = readVarInt(in);
                            pingPacketId = readVarInt(in);
                        } finally {
                            if (pingPacketId == 0x01) {
                                long payload = in.readLong();
                                ByteArrayOutputStream pingBuffer = new ByteArrayOutputStream();
                                DataOutputStream pingPacket = new DataOutputStream(pingBuffer);
                                pingPacket.writeByte(0x01); // Pong Response ID
                                pingPacket.writeLong(payload);
                                sendPacket(out, pingBuffer.toByteArray());
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
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error responding to client " + server.port + ": " + e.getMessage());

                    respondStatus(server, protocolVersion, null);
                    respondLoginDisconnect(null, e.getMessage());

                } finally {
                    System.out.println("\tDone.");
                }
            }


        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error running server (restarting) " + server.port, e);

            try {
                String message = e.getMessage() == null ? "Unknown error" : e.getMessage();
                DiscordWebhook webhook = new DiscordWebhook(config.discordWebhook);
                webhook.setUsername("Watcher App");
                String content = "Error running server (restarting) " + server.port + ": " + message + "\n";
                try {
                    for (StackTraceElement element : e.getStackTrace()) content += element.toString() + "\n";
                } catch (Exception ignored) {
                }
                webhook.setContent(content);
                webhook.execute();
            } catch (Exception ignored) {
            }

            try {//wait
                Thread.sleep(30000);
            } catch (InterruptedException ex) {
            }

            runServer(server); //restart
        }
    }


    private static long totalMemory, freeMemory, usedMemory, maxMemory;
    private static double memoryPercent;
    private static final Runtime runtime = Runtime.getRuntime();

    private static void handleMemory() {
        System.gc();
        // Total memory currently in use by JVM (in bytes)
        totalMemory = runtime.totalMemory();
        // Free memory within the total memory (in bytes)
        freeMemory = runtime.freeMemory();
        // Used memory = totalMemory - freeMemory
        usedMemory = totalMemory - freeMemory;
        // Max memory the JVM will attempt to use (in bytes)
        maxMemory = runtime.maxMemory();
        memoryPercent = (double) usedMemory / maxMemory * 100.0;
        System.out.printf("Memory Used: %.2f%%\n", memoryPercent);
    }

    private static void respondLoginDisconnect(DataOutputStream out, String message) throws IOException {
        String json = "{\"text\":\"" + message + "\"}";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream packet = new DataOutputStream(buffer);
        packet.writeByte(0x00); // Login Disconnect packet ID
        writeString(packet, json);
        sendPacket(out, buffer.toByteArray());
    }

    private static void respondStatus(Server server, int protocolVersion, DataOutputStream out) throws IOException {
        // Build response JSON
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
    }


    //=============================================================================================================================
    //=============================================================================================================================
    //=============================================================================================================================
    //=============================================================================================================================
    //=============================================================================================================================
    //=============================================================================================================================


    // Helper methods
    static void sendPacket(DataOutputStream out, byte[] data) throws IOException {
        writeVarInt(out, data.length);
        out.write(data);
    }

    static int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0, result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = (read & 0b01111111);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) throw new IOException("VarInt too big");
        } while ((read & 0b10000000) != 0);
        return result;
    }

    static void writeVarInt(DataOutputStream out, int value) throws IOException {
        do {
            byte temp = (byte) (value & 0b01111111);
            value >>>= 7;
            if (value != 0) temp |= 0b10000000;
            out.writeByte(temp);
        } while (value != 0);
    }

    static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void writeString(DataOutputStream out, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
}
