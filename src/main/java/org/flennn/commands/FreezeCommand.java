package org.flennn.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.flennn.LightStaff;
import org.flennn.manager.LightStaffManager;

import java.util.Arrays;
import java.util.stream.Collectors;

@CommandAlias("freeze")
public class FreezeCommand extends BaseCommand {
    @Default
    @CommandCompletion("@players")
    public void onFreeze(CommandSender sender, String targetName, @Optional String[] reasonWords) {
        if (!LightStaff.getInstance().getPermissionManager().canFreeze(sender)) return;
        LightStaffManager manager = LightStaff.getInstance().getLightStaffManager();
        Player target = resolveTarget(sender, targetName);
        if (target == null || !canFreezeStaff(sender, target)) return;

        if (manager.isFrozen(target)) {
            unfreeze(sender, target, manager);
            return;
        }

        freeze(sender, target, manager, joinReason(reasonWords));
    }

    @Subcommand("on|add|set")
    @CommandCompletion("@players")
    public void onFreezeExplicit(CommandSender sender, String targetName, @Optional String[] reasonWords) {
        if (!LightStaff.getInstance().getPermissionManager().canFreeze(sender)) return;
        LightStaffManager manager = LightStaff.getInstance().getLightStaffManager();
        Player target = resolveTarget(sender, targetName);
        if (target == null || !canFreezeStaff(sender, target)) return;
        freeze(sender, target, manager, joinReason(reasonWords));
    }

    @Subcommand("off|clear|remove")
    @CommandCompletion("@players")
    public void onUnfreeze(CommandSender sender, String targetName) {
        if (!LightStaff.getInstance().getPermissionManager().canFreeze(sender)) return;
        Player target = resolveTarget(sender, targetName);
        if (target == null) return;

        unfreeze(sender, target, LightStaff.getInstance().getLightStaffManager());
    }

    @CommandAlias("unfreeze")
    @CommandCompletion("@players")
    public void onUnfreezeAlias(CommandSender sender, String targetName) {
        if (!LightStaff.getInstance().getPermissionManager().canFreeze(sender)) return;
        Player target = resolveTarget(sender, targetName);
        if (target == null) return;

        unfreeze(sender, target, LightStaff.getInstance().getLightStaffManager());
    }

    private Player resolveTarget(CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            LightStaff.getInstance().getMessageManager().send(sender, "commands.player_not_found", "<red>Player not found.</red>");
        }
        return target;
    }

    private boolean canFreezeStaff(CommandSender sender, Player target) {
        if (sender instanceof Player player && player.equals(target)) {
            LightStaff.getInstance().getMessageManager().send(sender, "freeze.no_self", "<red>You cannot freeze yourself.</red>");
            return false;
        }
        boolean targetIsStaff = LightStaff.getInstance().getPermissionManager().isStaff(target);
        if (targetIsStaff && !LightStaff.getInstance().getPermissionManager().canFreezeStaff(sender)) {
            LightStaff.getInstance().getMessageManager().send(sender, "freeze.no_staff_permission", "<red>You can't freeze other staff members.</red>");
            return false;
        }
        return true;
    }

    private void freeze(CommandSender sender, Player target, LightStaffManager manager, String reason) {
        LightStaff.getInstance().getSchedulerAdapter().runEntity(target, () -> {
            manager.setFrozen(target, true, sanitizeReason(reason), sender.getName());
            LightStaff.getInstance().getMessageManager().send(target, "freeze.frozen", "<red>You have been frozen by staff. Do not disconnect.</red>");
        });
        LightStaff.getInstance().getMessageManager().send(sender, "freeze.froze_sender", "<red>Froze {player}.</red>", java.util.Map.of("player", target.getName()));
    }

    private void unfreeze(CommandSender sender, Player target, LightStaffManager manager) {
        if (!manager.isFrozen(target)) {
            LightStaff.getInstance().getMessageManager().send(sender, "freeze.not_frozen", "<yellow>{player} is not frozen.</yellow>", java.util.Map.of("player", target.getName()));
            return;
        }

        LightStaff.getInstance().getSchedulerAdapter().runEntity(target, () -> {
            manager.setFrozen(target, false, null, sender.getName());
            LightStaff.getInstance().getMessageManager().send(target, "freeze.unfrozen", "<green>You have been unfrozen.</green>");
        });
        LightStaff.getInstance().getMessageManager().send(sender, "freeze.unfroze_sender", "<green>Unfroze {player}.</green>", java.util.Map.of("player", target.getName()));
    }

    private String joinReason(String[] reasonWords) {
        if (reasonWords == null || reasonWords.length == 0) {
            return null;
        }
        return Arrays.stream(reasonWords)
                .filter(word -> word != null && !word.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) return null;
        String cleaned = reason
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .trim();
        return cleaned.length() > 160 ? cleaned.substring(0, 160) : cleaned;
    }
}
