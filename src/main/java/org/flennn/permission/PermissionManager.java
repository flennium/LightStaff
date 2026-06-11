package org.flennn.permission;

import org.bukkit.command.CommandSender;
import org.flennn.LightStaff;

public class PermissionManager {
    private final LightStaff plugin;

    public PermissionManager(LightStaff plugin) {
        this.plugin = plugin;
    }

    public boolean canUseLightStaff(CommandSender sender) {
        return hasAnyOrDeny(sender, "lightstaff.use");
    }

    public boolean canReload(CommandSender sender) {
        return hasAnyOrDeny(sender, "lightstaff.reload");
    }

    public boolean canRecover(CommandSender sender) {
        return hasAnyOrDeny(sender, "lightstaff.recover");
    }

    public boolean canStatus(CommandSender sender) {
        return hasAnyOrDeny(sender, "lightstaff.status");
    }

    public boolean canVanish(CommandSender sender) {
        return hasAnyOrDeny(sender, "lightstaff.vanish");
    }

    public boolean canVanishOthers(CommandSender sender) {
        return hasAnyOrDeny(sender, "lightstaff.vanish.others");
    }

    public boolean canFreeze(CommandSender sender) {
        return hasAnyOrDeny(sender, "lightstaff.freeze");
    }

    public boolean canFreezeStaff(CommandSender sender) {
        return hasAny(sender, "lightstaff.freeze.staff");
    }

    public boolean canInspectStaff(CommandSender sender) {
        return hasAny(sender, "lightstaff.inspect.staff");
    }

    public boolean canUseCreativeBypass(CommandSender sender) {
        return hasPermission(sender, "lightstaff.creative");
    }

    public boolean canSeeVanished(CommandSender sender) {
        return hasPermission(sender, "lightstaff.see");
    }

    public boolean canReceiveAlerts(CommandSender sender) {
        return hasPermission(sender, "lightstaff.alerts");
    }

    public boolean isStaff(CommandSender sender) {
        return hasAny(sender, "lightstaff.use");
    }

    public boolean hasAnyOrDeny(CommandSender sender, String... permissions) {
        if (hasAny(sender, permissions)) return true;
        deny(sender, permissions.length == 0 ? "lightstaff.use" : permissions[0]);
        return false;
    }

    public boolean hasAny(CommandSender sender, String... permissions) {
        for (String permission : permissions) {
            if (hasPermission(sender, permission)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPermission(CommandSender sender, String permission) {
        if (sender == null || permission == null || permission.isBlank()) return false;
        if (sender.isOp()) return true;
        if (sender.hasPermission(permission)) return true;
        if (sender.hasPermission("lightstaff.admin") || sender.hasPermission("lightstaff.*")) return true;

        String[] parts = permission.split("\\.");
        for (int i = parts.length - 1; i > 0; i--) {
            StringBuilder wildcard = new StringBuilder();
            for (int j = 0; j < i; j++) {
                if (j > 0) wildcard.append('.');
                wildcard.append(parts[j]);
            }
            wildcard.append(".*");
            if (sender.hasPermission(wildcard.toString())) {
                return true;
            }
        }
        return false;
    }

    public void deny(CommandSender sender, String permission) {
        plugin.getMessageManager().send(sender, "commands.no_permission_with_node", "<red>You lack permission to do that.</red> <gray>({permission})</gray>",
                java.util.Map.of("permission", permission == null ? "" : permission));
    }

    public void validatePermissions() {
        org.flennn.util.Console.info("Permission system loaded:");
        org.flennn.util.Console.info("  - Core: lightstaff.use, lightstaff.*");
        org.flennn.util.Console.info("  - Admin: lightstaff.reload, lightstaff.recover, lightstaff.status");
        org.flennn.util.Console.info("  - Tools: lightstaff.vanish, lightstaff.freeze, lightstaff.inspect, lightstaff.fly, lightstaff.push");
        org.flennn.util.Console.info("  - Visibility: lightstaff.see, lightstaff.alerts");
    }
}
