package org.flennn.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LightStaffConfigValidator {
    private static final List<String> VALID_ENTRY_GAMEMODES = Arrays.asList(
            "permission", "keep", "creative", "survival", "adventure", "spectator"
    );
    private static final List<String> VALID_STORAGE_TYPES = Arrays.asList("sqlite", "json", "mysql", "mariadb");

    private LightStaffConfigValidator() {
    }

    public static List<String> validate(FileConfiguration config, FileConfiguration toolsConfig, FileConfiguration messagesConfig) {
        List<String> warnings = new ArrayList<>();
        if (config == null) {
            warnings.add("Config is not loaded.");
            return warnings;
        }
        if (toolsConfig == null) {
            warnings.add("Tools config is not loaded.");
            return warnings;
        }
        if (messagesConfig == null) {
            warnings.add("Messages config is not loaded.");
            return warnings;
        }

        if (config.getLong("cooldown", 1000L) < 0L) {
            warnings.add("Global cooldown cannot be negative.");
        }
        if (config.getLong("actionbar_interval", 2L) < 1L) {
            warnings.add("Actionbar interval must be at least 1 tick.");
        }
        if (config.getInt("freeze_ban_days", 30) < 0) {
            warnings.add("Freeze ban days cannot be negative.");
        }
        String entryGameMode = config.getString("gamemode_on_enter", "permission");
        String normalizedGameMode = entryGameMode == null ? "" : entryGameMode.trim().toLowerCase();
        if (!VALID_ENTRY_GAMEMODES.contains(normalizedGameMode)) {
            warnings.add("gamemode_on_enter must be one of: permission, keep, creative, survival, adventure, spectator.");
        }
        if (!config.getBoolean("allow_flight_on_enter", true) && config.getBoolean("start_flying_on_enter", true)) {
            warnings.add("start_flying_on_enter has no effect when allow_flight_on_enter is false.");
        }
        validateCombat(config, warnings);
        validateStorage(config, warnings);
        if (config.getBoolean("staff_alerts_enabled", false)) {
            if (messagesConfig.getString("alerts.staff_join", "").isBlank()) {
                warnings.add("alerts.staff_join cannot be blank when staff alerts are enabled.");
            }
            if (messagesConfig.getString("alerts.staff_quit", "").isBlank()) {
                warnings.add("alerts.staff_quit cannot be blank when staff alerts are enabled.");
            }
        }

        if (messagesConfig.getString("prefix", "").isBlank()) {
            warnings.add("messages.yml prefix cannot be blank.");
        }

        ConfigurationSection tools = toolsConfig.getConfigurationSection("tools");
        if (tools == null) {
            warnings.add("Missing tools section in tools.yml; default tools will be used where possible.");
            return warnings;
        }

        Map<String, Integer> enabledSlots = new LinkedHashMap<>();
        for (String key : tools.getKeys(false)) {
            String path = "tools." + key + ".";
            boolean enabled = toolsConfig.getBoolean(path + "enabled", true);
            int slot = toolsConfig.getInt(path + "slot", -1);
            if (enabled) {
                enabledSlots.put(key, slot);
            }

            if (toolsConfig.getLong(path + "cooldown", 0L) < 0L) {
                warnings.add("Tool '" + key + "' cooldown cannot be negative.");
            }

            String materialList = toolsConfig.getString(path + "material", "");
            if (enabled && !hasAvailableItemMaterial(materialList)) {
                warnings.add("Tool '" + key + "' has no valid item material in '" + materialList + "'.");
            }
            if (enabled && toolsConfig.getString(path + "display_name", "").isBlank()) {
                warnings.add("Tool '" + key + "' display_name cannot be blank.");
            }
            String sound = toolsConfig.getString(path + "sound", "");
            if (!sound.isBlank() && !hasSound(sound)) {
                warnings.add("Tool '" + key + "' has invalid sound '" + sound + "'.");
            }
        }

        warnings.addAll(ToolSlotValidator.validate(enabledSlots));
        return warnings;
    }

    private static void validateStorage(FileConfiguration config, List<String> warnings) {
        String type = config.getString("storage.type", "sqlite");
        String normalized = type == null ? "" : type.trim().toLowerCase(java.util.Locale.ROOT);
        if (!VALID_STORAGE_TYPES.contains(normalized)) {
            warnings.add("storage.type must be one of: sqlite, json, mysql, mariadb.");
            return;
        }
        if ("sqlite".equals(normalized) || "json".equals(normalized)) return;

        String root = "storage." + normalized + ".";
        if (config.getString(root + "host", "").isBlank()) {
            warnings.add(root + "host cannot be blank.");
        }
        if (config.getInt(root + "port", 0) <= 0) {
            warnings.add(root + "port must be greater than 0.");
        }
        if (config.getString(root + "database", "").isBlank()) {
            warnings.add(root + "database cannot be blank.");
        }
        if (config.getString(root + "username", "").isBlank()) {
            warnings.add(root + "username cannot be blank.");
        }
    }

    private static void validateCombat(FileConfiguration config, List<String> warnings) {
        if (!config.getBoolean("combat.block_lightstaff_entry", true)) return;
        if (config.getString("combat.bypass_permission", "lightstaff.combat.bypass").isBlank()) {
            warnings.add("combat.bypass_permission cannot be blank when combat blocking is enabled.");
        }
        boolean hasConfiguredMetadataKeys = config.isList("combat.metadata_keys") && !config.getStringList("combat.metadata_keys").isEmpty();
        boolean hasConfiguredPlaceholders = config.isList("combat.placeholderapi.placeholders") && !config.getStringList("combat.placeholderapi.placeholders").isEmpty();
        if (!hasConfiguredMetadataKeys
                && (!config.getBoolean("combat.placeholderapi.enabled", true)
                || !hasConfiguredPlaceholders)
                && config.contains("combat")) {
            warnings.add("combat checks are enabled but no metadata keys or PlaceholderAPI placeholders are configured.");
        }
    }

    private static boolean hasSound(String configured) {
        try {
            org.bukkit.Sound.valueOf(configured.trim().toUpperCase(java.util.Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean hasAvailableItemMaterial(String configured) {
        if (configured == null || configured.isBlank()) {
            return false;
        }

        for (String candidate : configured.split(",")) {
            Material material = Material.matchMaterial(candidate.trim());
            if (material != null && material.isItem()) {
                return true;
            }
        }
        return false;
    }
}
