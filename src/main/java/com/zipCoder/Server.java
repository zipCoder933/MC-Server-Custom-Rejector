package com.zipCoder;

public class Server {
    public String name;
    public int port;

    public Server(String name, int port) {
        this.name = name;
        this.port = port;
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
