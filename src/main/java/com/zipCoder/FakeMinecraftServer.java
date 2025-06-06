package com.zipCoder;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

import static com.zipCoder.PacketUtils.*;

/**
 * New-NetIPAddress -IPAddress 192.168.1.100 -PrefixLength 24 -DefaultGateway 192.168.1.1 -InterfaceIndex 4
 * <p>
 * (Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.IPAddress -ne $null}).IPAddress
 */
public class FakeMinecraftServer {

    static Config config;
    public final static Logger LOGGER = Logger.getLogger(FakeMinecraftServer.class.getName());


    public static final String VERSION = "watcher v1.9.0";
    public final static int DEFAULT_PROTOCOL_VERSION = 763;
    public static final HashMap<Server, Thread> threads = new HashMap<>();

    static {
        try {
            Files.deleteIfExists(Paths.get("latest.log"));

            FileHandler fileHandler = new FileHandler("latest.log", false);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);
            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.ALL);

            // Custom ConsoleHandler for fine/finer logs with no date
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.ALL); // Handle FINE and FINER only
            consoleHandler.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    if (record.getLevel().intValue() <= Level.INFO.intValue()) {
                        return record.getMessage() + System.lineSeparator();
                    }
                    return record.getLevel() + ": " + record.getMessage() + System.lineSeparator();
                }
            });
            LOGGER.addHandler(consoleHandler);
            LOGGER.setLevel(Level.ALL);
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


    private static void packetLog(Server server, String ke, Object value) {
        String message = String.format("\t%s: %s", ke, value);
        serverLog(server, message);
    }

    private static void serverLog(Server server, String message) {
        String serverMessage = String.format("[%s]: %s", server.name, message);
        LOGGER.log(Level.FINEST, serverMessage);
    }

    public static void main(String[] args) {
        System.out.println(VERSION);

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                    MemoryUtils.handleMemory();
                    final StringBuilder runningThreads = new StringBuilder().append("Threads: ");
                    threads.forEach((server, thread) -> runningThreads.append(server.name).append(": ").append(thread.isAlive()).append(",\t"));
                    LOGGER.log(Level.FINEST, runningThreads.toString());
                }
                , 10, 120, TimeUnit.SECONDS);

        for (Server server : config.servers) {

            Thread thread = (new Thread(() -> {
                try {
                    TCP_server(server);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error running server loop. Port: " + server.port, e);
                } finally {
                    System.exit(1);
                }
            }));

            threads.put(server, thread);
            thread.start();
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

    public static void TCP_server(Server server) throws IOException {
        int packetLength;
        int packetId;
        int protocolVersion;
        int serverPort;
        String serverAddress;
        int nextState;

        try (ServerSocket serverSocket = new ServerSocket(server.port)) {
            System.out.println("Fake server (" + server.name + ") running on port " + server.port);
            while (true) {
                packetLength = 0;
                packetId = 0;
                protocolVersion = DEFAULT_PROTOCOL_VERSION;
                serverPort = 0;
                serverAddress = null;
                nextState = 1;

                try (Socket socket = serverSocket.accept()) {
                    serverLog(server, "Connection from " + socket.getInetAddress());

                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                    try {
                        // === Handshake Packet ===
                        packetLength = readVarInt(in);
                        serverLog(server, "\tPacket length: " + packetLength + "\tAvailable: " + in.available());

                        if (in.available() == 0) {//TODO: Understand why this happens
                            //Sometimes A client sends a STATUS request with 254 bytes, 0 available data
                            LOGGER.log(Level.WARNING, "Packet length is less than available data. Sending status response.");
                            respondStatus(server, protocolVersion, out);
                            continue;
                        }

                        // Read packet data
                        byte[] packetData = new byte[packetLength];
                        in.readFully(packetData); // Read exactly packetLength bytes
                        ByteArrayInputStream byteStream = new ByteArrayInputStream(packetData);
                        DataInputStream packet = new DataInputStream(byteStream);

                        // Parse packet
                        if (packet.available() > 0) {
                            packetId = readVarInt(packet);  // Should be 0 (Handshake)
                            packetLog(server, "packetId", packetId);
                        }
                        if (packet.available() > 0) protocolVersion = readVarInt(packet);  // Protocol version
                        if (packet.available() > 0) serverAddress = readString(packet);
                        if (packet.available() > 0) serverPort = packet.readUnsignedShort();
                        if (packet.available() > 0) {
                            nextState = readVarInt(packet);  // 1 = status, 2 = login
                            packetLog(server, "nextState", nextState);
                        }

                        if (nextState == 1 && config.handleStatusRequests) {
                            serverLog(server, "\tResponding to status request");
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
                                        serverLog(server, "\tResponding to ping");
                                        respondPong(in, out, payload);
                                    }
                                }
                            }
                        } else if (nextState == 2) {
                            serverLog(server, "\tResponding to login request");
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

                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Error handling packet (Sending status response)", e);
                        //We frequently get EOF exceptions here, we need to be ready for them
                        //We cant know what the request was, so we just send all responses
                        respondStatus(server, protocolVersion, out);
//                        respondLoginDisconnect(out, e.getMessage());
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error handling packet", e);
                } finally {
                    serverLog(server, "\tDone.\n");
                    MemoryUtils.handleMemory();
                }
            }
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
