package org.flennn.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.flennn.LightStaff;
import org.flennn.manager.LightStaffManager;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@CommandAlias("lightstaff|ls|staffmode|sm")
public class LightStaffCommand extends BaseCommand {
    @Default
    public void onDefault(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            LightStaff.getInstance().getMessageManager().send(sender, "commands.player_only", "<red>This command is for players only.</red>");
            return;
        }
        if (!LightStaff.getInstance().getPermissionManager().canUseLightStaff(player)) {
            return;
        }

        LightStaffManager manager = LightStaff.getInstance().getLightStaffManager();
        if (manager.isInLightStaff(player)) {
            if (manager.disableLightStaff(player)) {
                LightStaff.getInstance().getMessageManager().send(player, "lightstaff.disabled", "<green>You have exited Staff Mode.</green>");
            } else {
                LightStaff.getInstance().getMessageManager().send(player, "lightstaff.disable_failed", "<red>Could not exit Staff Mode. Please try again.</red>");
            }
            return;
        }

        String bypassPermission = LightStaff.getInstance().getPluginConfig().getString("combat.bypass_permission", "lightstaff.combat.bypass");
        if (isCombatEntryBlocked(player) && !LightStaff.getInstance().getPermissionManager().hasPermission(player, bypassPermission)) {
            LightStaff.getInstance().getMessageManager().send(player, "lightstaff.combat_blocked", "<red>You cannot enter Staff Mode while in combat!</red>");
            return;
        }

        if (manager.enableLightStaff(player)) {
            LightStaff.getInstance().getMessageManager().send(player, "lightstaff.enabled", "<green>You are now in Staff Mode.</green>");
            LightStaff.getInstance().getMessageManager().send(player, "lightstaff.enabled_hint", "<yellow>Use the tools in your hotbar to investigate players.</yellow>");
        } else {
            LightStaff.getInstance().getMessageManager().send(player, "lightstaff.enable_failed", "<red>Could not enter Staff Mode. Please try again.</red>");
        }
    }

    @Subcommand("status")
    @CommandCompletion("@players")
    public void onStatus(CommandSender sender, @Optional String targetName) {
        if (!LightStaff.getInstance().getPermissionManager().canStatus(sender)) return;
        Player target = resolveTarget(sender, targetName);
        if (target == null) {
            LightStaff.getInstance().getMessageManager().send(sender, "commands.player_not_online", "<red>Player is not online.</red>");
            return;
        }

        sender.sendMessage(LightStaff.getInstance().getLightStaffManager().buildStatus(target));
    }

    @Subcommand("recover")
    @CommandCompletion("@players")
    public void onRecover(CommandSender sender, String targetName) {
        if (!LightStaff.getInstance().getPermissionManager().canRecover(sender)) return;
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            LightStaff.getInstance().getMessageManager().send(sender, "commands.player_not_online", "<red>Player is not online.</red>");
            return;
        }

        LightStaff.getInstance().getLightStaffManager().recoverSession(target, sender);
    }

    @Subcommand("reload")
    public void onReload(CommandSender sender) {
        if (!LightStaff.getInstance().getPermissionManager().canReload(sender)) return;
        List<String> warnings = LightStaff.getInstance().reloadLightStaffConfig();
        LightStaff.getInstance().getMessageManager().send(sender, "commands.reload", "<green>LightStaff config reloaded with <yellow>{warnings}</yellow> warning(s).</green>",
                Map.of("warnings", String.valueOf(warnings.size())));
        for (int i = 0; i < Math.min(5, warnings.size()); i++) {
            sender.sendMessage("- " + warnings.get(i));
        }
        if (warnings.size() > 5) {
            LightStaff.getInstance().getMessageManager().send(sender, "commands.reload_more_warnings", "<yellow>See console for {count} more warning(s).</yellow>",
                    Map.of("count", String.valueOf(warnings.size() - 5)));
        }
    }

    private Player resolveTarget(CommandSender sender, String targetName) {
        if (targetName != null && !targetName.isBlank()) {
            return Bukkit.getPlayerExact(targetName);
        }
        return sender instanceof Player player ? player : null;
    }

    private boolean isCombatEntryBlocked(Player player) {
        if (player == null) return false;
        if (!LightStaff.getInstance().getPluginConfig().getBoolean("combat.block_lightstaff_entry", true)) return false;

        if (isTaggedByMetadata(player)) return true;
        return isTaggedByPlaceholderApi(player);
    }

    private boolean isTaggedByMetadata(Player player) {
        for (String key : configuredList("combat.metadata_keys", Arrays.asList("combatlogx_in_combat", "in_combat", "inCombat", "combatTagged", "tagged"))) {
            if (key == null || key.isBlank() || !player.hasMetadata(key)) continue;
            for (MetadataValue value : player.getMetadata(key)) {
                if (isCombatValue(String.valueOf(value.value()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTaggedByPlaceholderApi(Player player) {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return false;
            if (!LightStaff.getInstance().getPluginConfig().getBoolean("combat.placeholderapi.enabled", true)) return false;

            Class<?> placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method setPlaceholders = placeholderApiClass.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            for (String placeholder : configuredList("combat.placeholderapi.placeholders", Arrays.asList(
                    "%combatlogx_in_combat%",
                    "%combatlog_in_combat%",
                    "%pvpmanager_in_combat%",
                    "%deluxecombat_in_combat%",
                    "%combatplus_in_combat%"
            ))) {
                if (placeholder == null || placeholder.isBlank()) continue;
                String combatStatus = String.valueOf(setPlaceholders.invoke(null, player, placeholder));
                String cleanStatus = ChatColor.stripColor(combatStatus);
                if (isCombatValue(cleanStatus)) return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private boolean isCombatValue(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) return false;
        for (String configured : configuredList("combat.placeholderapi.combat_values", Arrays.asList("yes", "true", "1", "tagged", "combat", "in_combat"))) {
            if (configured != null && normalized.equals(configured.trim().toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return normalized.contains("yes") || normalized.contains("true") || normalized.equals("1");
    }

    private List<String> configuredList(String path, List<String> fallback) {
        List<String> configured = LightStaff.getInstance().getPluginConfig().getStringList(path);
        return configured.isEmpty() ? fallback : configured;
    }
}
