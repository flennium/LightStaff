package org.flennn.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.flennn.LightStaff;

@CommandAlias("stafftp")
public class StaffTpCommand extends BaseCommand {
    @Default
    @CommandCompletion("@players")
    public void onStaffTp(Player sender, @Optional String targetName) {
        if (!LightStaff.getInstance().getPermissionManager().hasAnyOrDeny(sender, "lightstaff.stafftp")) return;
        if (targetName == null) {
            LightStaff.getInstance().getMessageManager().send(sender, "stafftp.usage", "<yellow>Usage: /stafftp <player></yellow>");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            LightStaff.getInstance().getMessageManager().send(sender, "stafftp.not_online", "<red>That player is not online.</red>");
            return;
        }
        if (target.equals(sender)) {
            LightStaff.getInstance().getMessageManager().send(sender, "stafftp.self", "<red>You cannot teleport to yourself.</red>");
            return;
        }
        sender.teleportAsync(target.getLocation()).thenAccept(success -> {
            if (!success) {
                LightStaff.getInstance().getSchedulerAdapter().runEntity(sender, () ->
                        LightStaff.getInstance().getMessageManager().send(sender, "stafftp.failed", "<red>Teleport failed. Try again.</red>"));
                return;
            }
            LightStaff.getInstance().getSchedulerAdapter().runEntity(sender, () -> {
                LightStaff.getInstance().getMessageManager().send(sender, "stafftp.success", "<green>Teleported to {player}.</green>",
                        java.util.Map.of("player", target.getName()));
                LightStaff.getInstance().audit("stafftp", sender.getName(), target.getName(), "world=" + target.getWorld().getName());
            });
        });
    }
}
