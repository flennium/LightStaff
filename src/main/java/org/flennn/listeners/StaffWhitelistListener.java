package org.flennn.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.flennn.LightStaff;
import org.flennn.commands.StaffWhitelistCommand;

public class StaffWhitelistListener implements Listener {
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!StaffWhitelistCommand.isStaffBypassEnabled()) return;
        boolean isWhitelisted = event.getResult() != PlayerLoginEvent.Result.KICK_WHITELIST;
        boolean isStaffBypass = LightStaff.getInstance().getPermissionManager().hasPermission(event.getPlayer(), "lightstaff.whitelist.bypass");
        if (!isWhitelisted && isStaffBypass) {
            event.allow();
        }
    }
}
