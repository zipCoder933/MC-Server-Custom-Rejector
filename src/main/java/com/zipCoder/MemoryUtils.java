package com.zipCoder;

import java.util.logging.Level;

import static com.zipCoder.Main.LOGGER;

public class MemoryUtils {

    private static long totalMemory, freeMemory, usedMemory, maxMemory;
    private static double memoryPercent;
    private static final Runtime runtime = Runtime.getRuntime();

    public static void handleMemory() {
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
        String message = String.format("Memory: %.1f%%", memoryPercent);
        LOGGER.log(Level.FINEST, message);
    }
}
