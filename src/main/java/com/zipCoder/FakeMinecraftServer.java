package com.zipCoder;

import java.io.*;
import java.net.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static com.zipCoder.PacketUtils.*;

/**
 * New-NetIPAddress -IPAddress 192.168.1.100 -PrefixLength 24 -DefaultGateway 192.168.0.1 -InterfaceIndex 4
 */
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

    static String version = "watcher v1.7.0";

    private static void packetLog(String ke, Object value) {
        String message = String.format("\t%s: %s", ke, value);
        System.out.println(message);
    }

    public static void main(String[] args) {
        System.out.println(version);

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                MemoryUtils::handleMemory, 0, 1, TimeUnit.MINUTES);

        for (Server server : config.servers) {
            (new Thread(() -> TCP_server(server))).start();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                DiscordWebhook webhook = new DiscordWebhook(config.discordWebhook);
                webhook.setUsername("Watcher App");
                webhook.setContent("Watcher app has terminated.");
                webhook.execute();
            } catch (Exception ignored) {
                LOGGER.log(Level.SEVERE, "Error sending webhook", ignored);
            }
        }));
    }

//    public static void UDP_server(Server server) {
//        try (DatagramSocket socket = new DatagramSocket(server.port)) {
//            byte[] buffer = new byte[1024];
//            System.out.println("UDP Server running on port " + server.port);
//
//            while (true) {
//                try {
//                    DatagramPacket request = new DatagramPacket(buffer, buffer.length);
//                    socket.receive(request); // Wait for packet
//
//                    String received = new String(request.getData(), 0, request.getLength());
//                    System.out.println("Received: " + received);
//
//                    String response = "Echo: " + received;
//                    byte[] responseData = response.getBytes();
//
//                    DatagramPacket responsePacket = new DatagramPacket(
//                            responseData,
//                            responseData.length,
//                            request.getAddress(),
//                            request.getPort()
//                    );
//                    socket.send(responsePacket); // Respond immediately
//                } catch (IOException e) {
//                    LOGGER.log(Level.SEVERE, "Error handling UDP packet", e);
//                }
//            }
//        } catch (SocketException e) {
//            throw new RuntimeException(e);
//        }
//    }

    public static void TCP_server(Server server) {
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

                        byte[] packetData = new byte[packetLength];
                        in.readFully(packetData); // Read exactly packetLength bytes
                        // === Now use a ByteArrayInputStream for efficient access ===
                        ByteArrayInputStream byteStream = new ByteArrayInputStream(packetData);
                        DataInputStream packet = new DataInputStream(byteStream);

                        if (packet.available() > 0) {
                            packetId = readVarInt(packet);  // Should be 0 (Handshake)
                            packetLog("packetId", packetId);
                        }
                        if (packet.available() > 0) protocolVersion = readVarInt(packet);  // Protocol version
                        if (packet.available() > 0) serverAddress = readString(packet);
                        if (packet.available() > 0) serverPort = packet.readUnsignedShort();
                        if (packet.available() > 0) {
                            nextState = readVarInt(packet);  // 1 = status, 2 = login
                            packetLog("nextState", nextState);
                        }


                        if (nextState == 1 && config.handleStatusRequests) {
                            /**
                             * Status packet
                             *
                             *
                             */
                            int statusLength;
                            int statusPacketId = 0x00;


                            try {
                                statusLength = readVarInt(in);
                                statusPacketId = readVarInt(in);
                            } finally {
                                if (statusPacketId == 0x00) {
                                    respondStatus(server, protocolVersion, out);
                                }
                            }

                            if (in.available() > 0) {
                                int pingLength;
                                int pingPacketId = 0x01;
                                long payload = 0;

                                try {
                                    pingLength = readVarInt(in);
                                    pingPacketId = readVarInt(in);
                                    payload = in.readLong();
                                } finally {//Send a pong
                                    if (pingPacketId == 0x01) {
                                        System.out.println("\tResponding to ping");
                                        respondPong(in, out, payload);
                                    }
                                }
                            }

                        } else if (nextState == 2) {
                            /**
                             * Login packet
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
            System.exit(1);
        }
    }

    private static void respondPong(DataInputStream in, DataOutputStream out, long payload) {
        try {
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
