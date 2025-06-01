package com.zipCoder;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class FakeMinecraftServer {

//    static Config config = Config.loadConfig();
    static String version = "watcher v1.3.0";

//    private static void packetLog(String message) {
//        System.out.println(message);
//    }

    public static void main(String[] args) {
        System.out.println(version);

//        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
//                FakeMinecraftServer::handleMemory, 0, 1, java.util.concurrent.TimeUnit.MINUTES);

//        for (Server server : config.servers) {
//            (new Thread(() -> runServer(server))).start();
//        }
    }

//    public static void runServer(Server server) {
//        try (ServerSocket serverSocket = new ServerSocket(server.port)) {
//            System.out.println("Fake server running on port " + server.port);
//
//            while (true) {
//                handleMemory();
//                try (Socket socket = serverSocket.accept()) {
//                    System.out.println("Connection from " + socket.getInetAddress());
//
//                    DataInputStream in = new DataInputStream(socket.getInputStream());
//                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
//
//                    // === Handshake Packet ===
//                    int packetLength = readVarInt(in);
//                    //packetLog("\tPacket Length: " + packetLength);
//                    int packetId = readVarInt(in);  // Should be 0 (Handshake)
//                    //packetLog("\tPacket ID: " + packetId);
//
//                    //We can ignore packets we dont care about to prevent errors
//                    if (packetId != 0 && packetId != 1) return;
//                    /**
//                     * Handshake (packet ID 0) — to get the protocol version and next state,
//                     * Status packets (e.g., status request ID 0, ping ID 1),
//                     * Player connection packets (usually Login Start packet with a specific ID),
//                     */
//
//                    int protocolVersion = readVarInt(in);  // Protocol version
//                    //packetLog("\tProtocol Version: " + protocolVersion);
//                    String serverAddress = readString(in);
//                    //packetLog("\tServer Address: " + serverAddress);
//                    int serverPort = in.readUnsignedShort();
//                    //packetLog("\tServer Port: " + serverPort);
//                    int nextState = readVarInt(in);  // 1 = status, 2 = login
//                    //packetLog("\tNext State: " + nextState);
//
//
//                    if (nextState == 1) { // status request
//                        if (config.handleStatusRequests) respondWithStatus(server, in, out, protocolVersion);
//                    } else if (nextState == 2) { //Login request
//
//                        // === Login Start Packet ===
//                        packetLength = readVarInt(in);
//                        packetId = readVarInt(in);  // Should be 0 (Login Start)
//                        String username = readString(in);
//                        //packetLog("\tUser: " + username);
//
//                        // === Send Login Disconnect ===
//                        try {
//                            String json = "{\"text\":\"" + config.rejectMessage + "\"}";
//                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
//                            DataOutputStream packet = new DataOutputStream(buffer);
//                            packet.writeByte(0x00); // Login Disconnect packet ID
//                            writeString(packet, json);
//                            sendPacket(out, buffer.toByteArray());
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//
//                        // === Send discord webhook ===
//                        try {
//                            DiscordWebhook webhook = new DiscordWebhook(config.discordWebhook);
//                            webhook.setUsername("Watcher App");
//                            webhook.setContent("Player **" + username + "** wants to join server **" + server.name + "**");
//                            webhook.execute();
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    }
//
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//
//
//        } catch (Exception e) {
//            try {//Print/send error message
//                String message = e.getMessage() == null ? "Unknown error" : e.getMessage();
//                DiscordWebhook webhook = new DiscordWebhook(config.discordWebhook);
//                webhook.setUsername("Watcher App");
//                String content = "Error running server (restarting) " + server.port + ": " + message + "\n";
//                try {
//                    for (StackTraceElement element : e.getStackTrace()) content += element.toString() + "\n";
//                } catch (Exception ignored) {
//                }
//                System.out.println(content);
//                webhook.setContent(content);
//                webhook.execute();
//            } catch (Exception ignored) {
//            }
//
//            try {//wait
//                Thread.sleep(30000);
//            } catch (InterruptedException ex) {
//            }
//
//            runServer(server); //restart
//        }
//    }

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

    private static void respondWithStatus(Server server, DataInputStream in, DataOutputStream out, int protocolVersion) throws IOException {
        // Wait for Status Request packet (ID 0x00)
        int statusLength = readVarInt(in);
        //packetLog("\tStatus Length: " + statusLength);
        int statusPacketId = readVarInt(in);
        //packetLog("\tStatus Packet ID: " + statusPacketId);
        if (statusPacketId == 0x00) {
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

        // Wait for Ping packet (ID 0x01) and respond with Pong
        if (in.available() <= 0) {
            return; // Wait until there's data
        }

        int pingLength = readVarInt(in);
        //packetLog("\tPing Length: " + pingLength);
        int pingPacketId = readVarInt(in);
        //packetLog("\tPing Packet ID: " + pingPacketId);
        if (pingPacketId == 0x01) {
            long payload = in.readLong();
            ByteArrayOutputStream pingBuffer = new ByteArrayOutputStream();
            DataOutputStream pingPacket = new DataOutputStream(pingBuffer);
            pingPacket.writeByte(0x01); // Pong Response ID
            pingPacket.writeLong(payload);
            sendPacket(out, pingBuffer.toByteArray());
        }
    }

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
