package org.flennn.tools;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import java.util.Arrays;
import java.util.List;

public class ExitLightStaffTool extends StaffTool {
    private static final List<String> LORE = Arrays.asList(
            "<gray>Click to exit Staff Mode and",
            "<gray>get your stuff back.",
            "",
            "<red>Left-click: <white>Exit Staff Mode"
    );

    public ExitLightStaffTool(org.flennn.manager.LightStaffManager manager) {
        super(manager, "exit", manager.resolveToolMaterial("exit", Material.FIRE_CHARGE), "<dark_red><bold>Exit Staff Mode</bold></dark_red>", LORE, Sound.BLOCK_NOTE_BLOCK_PLING);
    }

    @Override
    public boolean handleInteraction(Player player) {
        if (player == null) return false;
        if (manager.disableLightStaff(player)) {
            sendMessage(player, "tools.exit_success", "<green>You've exited Staff Mode.</green>");
        } else {
            sendMessage(player, "tools.exit_not_in_lightstaff", "<red>You're not in Staff Mode.</red>");
        }
        playSound(player);
        manager.setCooldown(player);
        return true;
    }
}
