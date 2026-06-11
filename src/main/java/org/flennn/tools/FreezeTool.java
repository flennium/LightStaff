package org.flennn.tools;

import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public class FreezeTool extends StaffTool {
    private static final List<String> LORE = Arrays.asList(
            "<gray>Right-click a player to freeze them.",
            "<gray>Frozen players can't move or chat normally.",
            "<gray>Disconnecting while frozen = ban.",
            "",
            "<yellow>Right-click: <white>Freeze/Unfreeze player"
    );

    public FreezeTool(org.flennn.manager.LightStaffManager manager) {
        super(manager, "freeze", manager.resolveToolMaterial("freeze", Material.PACKED_ICE), "<red><bold>Freeze Tool</bold></red>", LORE, Sound.BLOCK_GLASS_BREAK);
    }

    @Override
    public boolean handleInteraction(Player player) {
        sendMessage(player, "freeze.tool_hint", "<yellow>Right-click a player to freeze/unfreeze them.</yellow>");
        playSound(player);
        manager.setCooldown(player);
        return true;
    }

    @Override
    public boolean handlePlayerInteraction(Player staff, Player target) {
        if (staff == null || target == null) return false;
        if (org.flennn.LightStaff.getInstance().getPermissionManager().isStaff(target) &&
                !org.flennn.LightStaff.getInstance().getPermissionManager().canFreezeStaff(staff)) {
            sendMessage(staff, "tools.freeze_staff_denied", "<red>You can't freeze other staff members.</red>");
            return true;
        }
        if (isFrozen(target)) {
            unfreezePlayer(target, staff);
            sendMessage(staff, "tools.freeze_staff_unfroze", "<green>You unfroze <yellow>{player}</yellow>.</green>", java.util.Map.of("player", target.getName()));
            sendMessage(target, "tools.freeze_target_unfrozen", "<green>You've been unfrozen by staff.</green>");
        } else {
            freezePlayer(target, staff);
            sendMessage(staff, "tools.freeze_staff_froze", "<red>You froze <yellow>{player}</yellow>.</red>", java.util.Map.of("player", target.getName()));
            sendMessage(target, "tools.freeze_target_frozen", "<red>You've been frozen by staff. Don't move or disconnect!</red>");
        }
        playSound(staff);
        manager.setCooldown(staff);
        return true;
    }

    public void freezePlayer(Player player) {
        freezePlayer(player, null);
    }

    public void freezePlayer(Player player, Player staff) {
        if (player == null) return;
        manager.setFrozen(player, true, null, staff != null ? staff.getName() : "-");

        player.showTitle(Title.title(
                org.flennn.LightStaff.getInstance().getMessageManager().bareComponent("freeze.title", "<red><bold>FROZEN</bold></red>"),
                org.flennn.LightStaff.getInstance().getMessageManager().bareComponent("freeze.subtitle", "<gray>Don't move or disconnect!</gray>"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000))
        ));
    }

    public void unfreezePlayer(Player player) {
        unfreezePlayer(player, null);
    }

    public void unfreezePlayer(Player player, Player staff) {
        if (player == null) return;
        manager.setFrozen(player, false, null, staff != null ? staff.getName() : "-");
        player.clearTitle();
    }

    public boolean isFrozen(Player player) {
        return player != null && manager.isFrozen(player);
    }
}
