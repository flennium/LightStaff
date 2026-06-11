package org.flennn.tools;

import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.List;

public record ToolDefinition(
        String key,
        boolean enabled,
        int slot,
        String permission,
        Material material,
        long cooldownMs,
        String displayName,
        List<String> lore,
        Sound sound,
        float soundVolume,
        float soundPitch
) {
    public boolean hasPermissionNode() {
        return permission != null && !permission.isBlank();
    }
}
