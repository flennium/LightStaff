package org.flennn.logging;

import org.bukkit.plugin.Plugin;
import org.flennn.util.Console;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class AuditLogger {
    private final Plugin plugin;
    private final Path logFile;
    private volatile boolean closed;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "LightStaff-Audit");
        thread.setDaemon(true);
        return thread;
    });

    public AuditLogger(Plugin plugin) {
        this.plugin = plugin;
        this.logFile = plugin.getDataFolder().toPath().resolve("logs").resolve("audit.log");
    }

    public void log(String action, String actor, String target, String details) {
        String line = String.join(" | ",
                Instant.now().toString(),
                sanitize(action),
                "actor=" + sanitize(actor),
                "target=" + sanitize(target),
                sanitize(details)
        ) + System.lineSeparator();

        if (closed) {
            writeLine(line);
            return;
        }
        try {
            executor.execute(() -> writeLine(line));
        } catch (RejectedExecutionException ignored) {
            writeLine(line);
        }
    }

    public void close() {
        closed = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private void writeLine(String line) {
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Console.warn("Could not write LightStaff audit log: " + e.getMessage());
        }
    }

    static String sanitize(String value) {
        if (value == null || value.isBlank()) return "-";
        return value.replace('\n', ' ').replace('\r', ' ').replace('|', '/');
    }
}
