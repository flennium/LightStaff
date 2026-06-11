package org.flennn.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.flennn.LightStaff;
import org.flennn.manager.LightStaffManager;
import org.flennn.tools.InspectInventoryHolder;
import org.flennn.util.Console;

public class LightStaffListener implements Listener {
    private final LightStaffManager manager;

    public LightStaffListener(LightStaffManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInLightStaff(player)) return;

        ItemStack clickedItem = event.getItem();
        if (!manager.isLightStaffItem(clickedItem)) return;

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK ||
                action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (!manager.hasToolPermission(player, clickedItem)) {
                LightStaff.getInstance().getMessageManager().actionBar(player, "tools.no_permission", "<red>You lack permission for this tool.</red>");
                event.setCancelled(true);
                return;
            }

            if (manager.handleToolInteraction(player, clickedItem)) {
                event.setCancelled(true);
                return;
            }
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInLightStaff(player)) return;

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (!manager.isLightStaffItem(heldItem)) return;

        if (!manager.hasToolPermission(player, heldItem)) {
            LightStaff.getInstance().getMessageManager().actionBar(player, "tools.no_permission", "<red>You lack permission for this tool.</red>");
            event.setCancelled(true);
            return;
        }

        if (event.getRightClicked() instanceof Player target &&
                manager.handlePlayerInteraction(player, target, heldItem)) {
            Console.info(String.format("[STAFF] %s used %s on %s",
                    player.getName(), manager.getToolDisplayName(heldItem), target.getName()));
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (manager.isInLightStaff(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (manager.isInLightStaff(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player &&
                (manager.isInLightStaff(player) || manager.isFrozen(player))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player damager && manager.isInLightStaff(damager)) {
            event.setCancelled(true);
        }
        if (event.getEntity() instanceof Player target &&
                (manager.isInLightStaff(target) || manager.isFrozen(target))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!manager.isFrozen(event.getPlayer())) return;
        if (event.getTo() != null &&
                event.getFrom().getWorld().equals(event.getTo().getWorld()) &&
                event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        event.setTo(event.getFrom());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof InspectInventoryHolder) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (manager.isFrozen(player)) {
            event.setCancelled(true);
            return;
        }
        if (!manager.isInLightStaff(player)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (manager.isLightStaffItem(current) || manager.isLightStaffItem(cursor) || !LightStaff.getInstance().getPermissionManager().canUseCreativeBypass(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof InspectInventoryHolder) {
            event.setCancelled(true);
            return;
        }

        if (event.getWhoClicked() instanceof Player player &&
                (manager.isFrozen(player) || manager.isInLightStaff(player)) &&
                !LightStaff.getInstance().getPermissionManager().canUseCreativeBypass(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        manager.loadLightStaffDataAsync(event.getPlayer());
        broadcastStaffAlert(event.getPlayer(), true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        broadcastStaffAlert(event.getPlayer(), false);
        manager.handleFrozenQuit(event.getPlayer());
        if (manager.shouldRestoreOnQuit()) {
            manager.disableLightStaff(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (manager.isFrozen(player) || (manager.isInLightStaff(player) && !LightStaff.getInstance().getPermissionManager().canUseCreativeBypass(player))) {
            event.setCancelled(true);
        }
        if (manager.isLightStaffItem(dropped)) {
            event.setCancelled(true);
            LightStaff.getInstance().getMessageManager().actionBar(player, "tools.cannot_drop", "<red>You cannot drop staff tools!</red>");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (manager.isFrozen(player) || (manager.isInLightStaff(player) && !LightStaff.getInstance().getPermissionManager().canUseCreativeBypass(player)) || manager.isLightStaffItem(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!manager.isInLightStaff(player)) return;
        event.getDrops().removeIf(manager::isLightStaffItem);
    }

    @EventHandler
    public void onHungerDrain(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && (manager.isInLightStaff(player) || manager.isFrozen(player))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFrozenTeleport(PlayerTeleportEvent event) {
        if (manager.isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFrozenCommand(PlayerCommandPreprocessEvent event) {
        if (!manager.isFrozen(event.getPlayer())) return;
        String command = event.getMessage().split(" ", 2)[0].toLowerCase();
        if (manager.isFrozenCommandAllowed(command)) return;
        event.setCancelled(true);
        LightStaff.getInstance().getMessageManager().send(event.getPlayer(), "freeze.blocked_command", "<red>You cannot use commands while frozen.</red>");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFrozenChat(AsyncChatEvent event) {
        if (manager.isFrozen(event.getPlayer())) {
            event.setCancelled(true);
            LightStaff.getInstance().getSchedulerAdapter().runEntity(event.getPlayer(), () ->
                    LightStaff.getInstance().getMessageManager().send(event.getPlayer(), "freeze.blocked_chat", "<red>You cannot chat while frozen.</red>"));
        }
    }

    private void broadcastStaffAlert(Player player, boolean joined) {
        if (!manager.getConfig().getBoolean("staff_alerts_enabled", false)) return;
        if (!isStaffUser(player)) return;

        for (Player online : LightStaff.getInstance().getServer().getOnlinePlayers()) {
            if (LightStaff.getInstance().getPermissionManager().canReceiveAlerts(online)) {
                LightStaff.getInstance().getMessageManager().send(online,
                        joined ? "alerts.staff_join" : "alerts.staff_quit",
                        joined ? "<gray>Staff <yellow>{player}</yellow> joined.</gray>" : "<gray>Staff <yellow>{player}</yellow> left.</gray>",
                        java.util.Map.of("player", player.getName()));
            }
        }
    }

    private boolean isStaffUser(Player player) {
        return LightStaff.getInstance().getPermissionManager().isStaff(player);
    }
}
