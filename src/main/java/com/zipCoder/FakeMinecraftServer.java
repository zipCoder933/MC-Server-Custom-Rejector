package com.zipCoder;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class FakeMinecraftServer {

    static Config config = Config.loadConfig();

    public static void main(String[] args) throws IOException {
        System.out.println(config + "\n");
        for (Server server : config.servers) {
            (new Thread(() -> runServer(server))).start();
        }
    }

    public static void runServer(Server server) {
        try {
            ServerSocket serverSocket = new ServerSocket(server.port);
            System.out.println("Fake server running on port " + server.port);
            while (true) {
                try (Socket socket = serverSocket.accept()) {
                    System.out.println("Connection from " + socket.getInetAddress());

                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                    // === Handshake Packet ===
                    int packetLength = readVarInt(in);
                    int packetId = readVarInt(in);  // Should be 0 (Handshake)
                    int protocolVersion = readVarInt(in);  // Protocol version
                    String serverAddress = readString(in);
                    int serverPort = in.readUnsignedShort();
                    int nextState = readVarInt(in);  // 1 = status, 2 = login


                    if (nextState == 1) { // status request
                        if (config.handleStatusRequests) respondWithStatus(server, in, out, protocolVersion);
                    } else if (nextState == 2) { //Login request

                        // === Login Start Packet ===
                        packetLength = readVarInt(in);
                        packetId = readVarInt(in);  // Should be 0 (Login Start)
                        String username = readString(in);
                        System.out.println("User: " + username);

                        // === Send Login Disconnect ===
                        try {
                            String json = "{\"text\":\"" + config.rejectMessage + "\"}";
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            DataOutputStream packet = new DataOutputStream(buffer);
                            packet.writeByte(0x00); // Login Disconnect packet ID
                            writeString(packet, json);
                            sendPacket(out, buffer.toByteArray());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        // === Send discord webhook ===
                        try {
                            DiscordWebhook webhook = new DiscordWebhook(config.discordWebhook);
                            webhook.setUsername("Watcher App");
                            webhook.setContent("Player **" + username + "** wants to join server **" + server.name + "**");
                            webhook.execute();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            try {//Print/send error message
                System.err.println("Error running server (restarting) " + server.port + ": " + e.getMessage());
                DiscordWebhook webhook = new DiscordWebhook(config.discordWebhook);
                webhook.setUsername("Watcher App");
                String content = "Error running server (restarting) " + server.port + ": " + e.getMessage() + "\n";
                for (StackTraceElement element : e.getStackTrace()) content += element.toString() + "\n";
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

    private static void respondWithStatus(Server server, DataInputStream in, DataOutputStream out, int protocolVersion) throws IOException {
        // Wait for Status Request packet (ID 0x00)
        int statusLength = readVarInt(in);
        int statusPacketId = readVarInt(in);
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
        int pingLength = readVarInt(in);
        int pingPacketId = readVarInt(in);
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
