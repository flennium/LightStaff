package org.flennn.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.flennn.LightStaff;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final LightStaff plugin;
    private FileConfiguration settings;
    private FileConfiguration tools;
    private FileConfiguration messages;

    public ConfigManager(LightStaff plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        saveResourceIfMissing("tools.yml");
        saveResourceIfMissing("messages.yml");
        plugin.reloadConfig();
        settings = plugin.getConfig();
        settings.options().copyDefaults(true);
        plugin.saveConfig();
        tools = loadYaml("tools.yml");
        messages = loadYaml("messages.yml");
    }

    public FileConfiguration settings() {
        return settings;
    }

    public FileConfiguration tools() {
        return tools;
    }

    public FileConfiguration messages() {
        return messages;
    }

    public void saveSettings() {
        try {
            settings.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (IOException e) {
            org.flennn.util.Console.error("Could not save config.yml: " + e.getMessage());
        }
    }

    private FileConfiguration loadYaml(String name) {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));
    }

    private void saveResourceIfMissing(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }
}
