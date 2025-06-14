package com.zipCoder.closeAndWakeUpServer;

import com.zipCoder.Main;
import com.zipCoder.PlatformUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class WakeUpHandler {

    public static final int NOFITY_SOCKET = 8080;
    public static final int NOTIFY_WAIT_TIME = 5 * 60 * 1000;
    public static final String INTERFACE_NAME = "eth0";
    public static final String MAC_ADDRESS = "34:5a:60:5d:96:bb";
    public Thread wakeUpThread;
    public int failedToExecWakeCommand = 0;
    public int failedToGetServerConnection = 0;

    public void start() {
        if (wakeUpThread == null || !wakeUpThread.isAlive()) {
            wakeUpThread = new Thread(this::wakeServerAndShutdown);
            wakeUpThread.start();
        } else {
            System.out.println("Wake up thread is already running.");
        }
    }

    private void wakeServerAndShutdown() {
        for (int i = 0; i < 10; i++) {
            boolean success = wakeServer();
            if (!success) failedToExecWakeCommand++;

            success = startSocketAndWait();
            if (success) {
                System.out.println("Closing watcher program. Success: " + success);
                System.exit(0);
            } else {
                Main.sendSimpleWebhook("Error waiting for client connection.");
                failedToGetServerConnection++;
            }
        }
    }

    private boolean startSocketAndWait() {
        Main.sendSimpleWebhook("Starting wake up server.");
        try (ServerSocket serverSocket = new ServerSocket(NOFITY_SOCKET)) {
            System.out.println("Wake up server started. Waiting for connection...");
            serverSocket.setSoTimeout(NOTIFY_WAIT_TIME);
            Socket client = serverSocket.accept();
            //Send a response
            client.getOutputStream().write(0);
            client.getOutputStream().write("Hello World".getBytes());
            System.out.println("Client connected. Closing socket...");
            client.close();
            serverSocket.close();
            return true;
        } catch (IOException e) {
            System.out.println("Error waiting for client connection: " + e.getMessage());
            return false;
        }
    }


    private boolean wakeServer() {
        if (!PlatformUtils.isLinux()) {
            System.out.println("Not on Linux, skipping wake up.");
            return true;
        }

        for (int i = 0; i < 7; i++) {
            try {
                Process process = Runtime.getRuntime().exec("sudo etherwake -i " + INTERFACE_NAME + " " + MAC_ADDRESS);

                // Read the output
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                System.out.println("Output:");
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }

                // Read the error output (if any)
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                System.out.println("\nError Output:");
                while ((line = errorReader.readLine()) != null) {
                    System.err.println(line);
                }

                // Wait for the command to finish and get the exit code
                int exitCode = process.waitFor();

                if (exitCode == 0) return true;
                else Thread.sleep(1500);

            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
