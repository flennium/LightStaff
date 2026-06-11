package org.flennn.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.flennn.LightStaff;

import java.util.Map;

@CommandAlias("togglestaffwhitelist|staffwhitelist")
public class StaffWhitelistCommand extends BaseCommand {
    @Getter
    private static boolean staffBypassEnabled = false;

    public static void loadState() {
        staffBypassEnabled = LightStaff.getInstance().getPluginConfig().getBoolean("staff_whitelist_enabled", false);
    }

    @Default
    public void onDefault(CommandSender sender) {
        if (!LightStaff.getInstance().getPermissionManager().hasAnyOrDeny(sender, "lightstaff.whitelist.toggle")) return;
        staffBypassEnabled = !staffBypassEnabled;
        LightStaff.getInstance().getPluginConfig().set("staff_whitelist_enabled", staffBypassEnabled);
        LightStaff.getInstance().getConfigManager().saveSettings();
        LightStaff.getInstance().audit("staff_whitelist_toggle", sender.getName(), "-", "enabled=" + staffBypassEnabled);
        String state = LightStaff.getInstance().getMessageManager().raw(staffBypassEnabled ? "whitelist.enabled_state" : "whitelist.disabled_state",
                staffBypassEnabled ? "<green>enabled</green>" : "<red>disabled</red>");
        LightStaff.getInstance().getMessageManager().send(sender, "whitelist.toggled", "<yellow>Staff whitelist bypass has been {state}</yellow>",
                Map.of("state", state));
    }

}
