package org.flennn.tools;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.flennn.LightStaff;
import java.util.Arrays;
import java.util.List;

public class FlyToggleTool extends StaffTool {
    private static final List<String> LORE = Arrays.asList(
            "<gray>Click to toggle flight mode.",
            "<gray>Makes investigation easier.",
            "",
            "<yellow>Left-click: <white>Toggle flight"
    );

    public FlyToggleTool(org.flennn.manager.LightStaffManager manager) {
        super(manager, "fly", manager.resolveToolMaterial("fly", Material.FEATHER), "<yellow><bold>Fly Toggle</bold></yellow>", LORE, Sound.ENTITY_BAT_TAKEOFF);
    }

    @Override
    public boolean handleInteraction(Player player) {
        if (player == null) return false;
        boolean isFlying = player.isFlying();
        if (isFlying) {
            player.setFlying(false);
            sendMessage(player, "tools.fly_disabled", "<red>Flight mode disabled.</red>");
        } else {
            // Ensure player can fly before setting flying to true
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
            player.setFlying(true);
            sendMessage(player, "tools.fly_enabled", "<green>Flight mode enabled.</green>");
        }
        playSound(player);
        manager.setCooldown(player);
        LightStaff.getInstance().audit("fly_toggle", player.getName(), player.getName(), "flying=" + player.isFlying());
        return true;
    }
}
