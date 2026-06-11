package org.flennn.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;

public class MessageManager {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration messages;

    public MessageManager(FileConfiguration messages) {
        this.messages = messages;
    }

    public void reload(FileConfiguration messages) {
        this.messages = messages;
    }

    public String prefix() {
        return raw("prefix", "<gray>[LightStaff]</gray> ");
    }

    public String raw(String path, String fallback) {
        if (messages == null) return fallback;
        return messages.getString(path, fallback);
    }

    public String format(String path, String fallback, Map<String, String> placeholders) {
        String message = raw(path, fallback);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return message;
    }

    public Component component(String path, String fallback) {
        return miniMessage.deserialize(prefix() + raw(path, fallback));
    }

    public Component component(String path, String fallback, Map<String, String> placeholders) {
        return miniMessage.deserialize(prefix() + format(path, fallback, placeholders));
    }

    public Component bareComponent(String path, String fallback) {
        return miniMessage.deserialize(raw(path, fallback));
    }

    public Component bareComponent(String path, String fallback, Map<String, String> placeholders) {
        return miniMessage.deserialize(format(path, fallback, placeholders));
    }

    public void send(CommandSender sender, String path, String fallback) {
        if (sender != null) {
            sender.sendMessage(component(path, fallback));
        }
    }

    public void send(CommandSender sender, String path, String fallback, Map<String, String> placeholders) {
        if (sender != null) {
            sender.sendMessage(component(path, fallback, placeholders));
        }
    }

    public void actionBar(Player player, String path, String fallback) {
        if (player != null) {
            player.sendActionBar(bareComponent(path, fallback));
        }
    }
}
