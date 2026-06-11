package org.flennn.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

public final class Console {
    private static final String PREFIX = "&8[&eLightStaff&8] ";

    private Console() {
    }

    public static void info(String message) {
        send("&bINFO", message);
    }

    public static void success(String message) {
        send("&aOK", message);
    }

    public static void warn(String message) {
        send("&eWARN", message);
    }

    public static void error(String message) {
        send("&cERROR", message);
    }

    public static void startup(StartupReport report) {
        line("&8&m--------------------------------------------------");
        line("&8[&eLightStaff&8]");
        line("&8&m--------------------------------------------------");
        line("&eStatus        &8: " + (report.ready() ? "&aReady" : "&cStarted with warnings"));
        line("&eStorage       &8: &f" + report.storage());
        line("&eTools         &8: &f" + report.enabledTools() + "&7/&f" + report.totalTools() + " &7enabled");
        line("&eConfig        &8: " + (report.warningCount() == 0 ? "&aClean" : "&e" + report.warningCount() + " warning(s)"));
        line("&ePlatform      &8: &f" + report.platform());
        line("&eAPI Target    &8: &f1.19 &8| &eJava &8: &f" + report.javaVersion());
        line("&eMade by       &8: &fflennn &8| &eVersion &8: &f" + report.version());
        line("&8&m--------------------------------------------------");
        if (report.ready()) {
            success("LightStaff is ready.");
        } else {
            warn("LightStaff started with warnings. Run /lightstaff reload after fixing config.");
        }
    }

    private static void send(String level, String message) {
        Bukkit.getConsoleSender().sendMessage(color(PREFIX + level + " &7" + message));
    }

    private static void line(String message) {
        Bukkit.getConsoleSender().sendMessage(color(message));
    }

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message == null ? "" : message);
    }

    public record StartupReport(
            String version,
            boolean ready,
            String storage,
            int enabledTools,
            int totalTools,
            int warningCount,
            String platform,
            String javaVersion
    ) {
    }
}
