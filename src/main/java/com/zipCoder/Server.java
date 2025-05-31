package com.zipCoder;

public class Server {
    public String name;
    public int port;
    public String version = "1.20.1";
    public String title = "A Minecraft Server";
    public int maxPlayers = 100;

    public Server(String name, int port, String version, String title, int maxPlayers) {
        this.name = name;
        this.port = port;
        this.version = version;
        this.title = title;
        this.maxPlayers = maxPlayers;
    }

    public Server() {
    }

    public String toString() {
        return "Server{" +
                "name='" + name + '\'' +
                ", port=" + port +
                '}';
    }
}
