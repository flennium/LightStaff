package org.flennn.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.flennn.LightStaff;
import org.flennn.manager.LightStaffManager;

import java.util.Map;

@CommandAlias("vanish")
public class VanishCommand extends BaseCommand {
    @Default
    @CommandCompletion("@players")
    public void onVanish(CommandSender sender, @Optional String targetName) {
        if (!LightStaff.getInstance().getPermissionManager().canVanish(sender)) return;
        LightStaffManager manager = LightStaff.getInstance().getLightStaffManager();
        if ((targetName == null || targetName.isBlank()) && !(sender instanceof Player)) {
            LightStaff.getInstance().getMessageManager().send(sender, "vanish.console_usage", "<yellow>Usage: /vanish <player></yellow>");
            return;
        }

        Player target = targetName == null || targetName.isBlank() ? (Player) sender : Bukkit.getPlayerExact(targetName);
        if (target == null) {
            LightStaff.getInstance().getMessageManager().send(sender, "commands.player_not_online", "<red>Player is not online.</red>");
            return;
        }

        if (!target.equals(sender) && !LightStaff.getInstance().getPermissionManager().canVanishOthers(sender)) {
            LightStaff.getInstance().getMessageManager().send(sender, "vanish.no_others_permission", "<red>You lack permission to toggle vanish for other players.</red>");
            return;
        }

        boolean vanished = manager.isVanished(target);
        String actor = sender instanceof Player player ? player.getName() : sender.getName();
        LightStaff.getInstance().getSchedulerAdapter().runEntity(target, () -> manager.setVanished(target, !vanished, actor));
        if (target.equals(sender)) {
            LightStaff.getInstance().getMessageManager().send(sender, vanished ? "vanish.self_disabled" : "vanish.self_enabled",
                    vanished ? "<green>You are now visible to other players.</green>" : "<aqua>You are now vanished and invisible to regular players.</aqua>");
            return;
        }

        LightStaff.getInstance().getMessageManager().send(sender, vanished ? "vanish.target_disabled_sender" : "vanish.target_enabled_sender",
                vanished ? "<green>{player} is now visible to other players.</green>" : "<aqua>{player} is now vanished from regular players.</aqua>",
                Map.of("player", target.getName()));
        LightStaff.getInstance().getMessageManager().send(target, vanished ? "vanish.target_disabled_target" : "vanish.target_enabled_target",
                vanished ? "<green>You are now visible to other players.</green>" : "<aqua>You were vanished by {staff}.</aqua>",
                Map.of("staff", actor));
    }
}
