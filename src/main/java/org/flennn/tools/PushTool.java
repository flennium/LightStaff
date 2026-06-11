package org.flennn.tools;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.flennn.LightStaff;
import java.util.Arrays;
import java.util.List;

public class PushTool extends StaffTool {
    private static final List<String> LORE = Arrays.asList(
            "<gray>Right-click a player to push them",
            "<gray>in the direction you're facing.",
            "",
            "<red>Right-click: <white>Push player"
    );

    public PushTool(org.flennn.manager.LightStaffManager manager) {
        super(manager, "push", manager.resolveToolMaterial("push", Material.SLIME_BALL), "<gold><bold>Push Tool</bold></gold>", LORE, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK);
    }

    @Override
    public boolean handleInteraction(Player player) {
        sendMessage(player, "tools.push_hint", "<gray>Right-click a player to push them.</gray>");
        playSound(player);
        manager.setCooldown(player);
        return true;
    }

    @Override
    public boolean handlePlayerInteraction(Player staff, Player target) {
        if (staff == null || target == null) return false;
        Vector direction = staff.getLocation().getDirection().normalize();
        double pushStrength = manager.getToolDouble("push", "strength", 2.0);
        Vector push = direction.multiply(pushStrength).setY(0.5);
        target.setVelocity(push);
        sendMessage(staff, "tools.pushed", "<green>Pushed <white>{player}</white>!</green>", java.util.Map.of("player", target.getName()));
        playSound(staff);
        manager.setCooldown(staff);
        LightStaff.getInstance().audit("push_player", staff.getName(), target.getName(), "strength=" + pushStrength);
        return true;
    }
}
