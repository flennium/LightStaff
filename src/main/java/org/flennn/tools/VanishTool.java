package org.flennn.tools;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import java.util.Arrays;
import java.util.List;

public class VanishTool extends StaffTool {
    private static final List<String> LORE = Arrays.asList(
            "<gray>Click to go invisible",
            "<gray>from regular players.",
            "",
            "<yellow>Left-click: <white>Toggle vanish"
    );

    public VanishTool(org.flennn.manager.LightStaffManager manager) {
        super(manager, "vanish", manager.resolveToolMaterial("vanish", Material.ENDER_EYE), "<aqua><bold>Vanish Tool</bold></aqua>", LORE, Sound.BLOCK_GLASS_BREAK);
    }

    @Override
    public boolean handleInteraction(Player player) {
        boolean vanished = manager.isVanished(player);
        manager.setVanished(player, !vanished);
        manager.setCooldown(player);
        playSound(player);
        sendMessage(player, vanished ? "tools.vanish_disabled" : "tools.vanish_enabled",
                vanished ? "<green>You are now visible.</green>" : "<aqua>You are now vanished.</aqua>");
        return true;
    }
}
