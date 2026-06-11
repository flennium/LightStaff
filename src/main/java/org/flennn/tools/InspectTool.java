package org.flennn.tools;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.flennn.util.ItemBuilder;
import org.flennn.LightStaff;
import java.util.Arrays;
import java.util.List;

public class InspectTool extends StaffTool {
    private static final List<String> LORE = Arrays.asList(
            "<gray>Right-click a player to check",
            "<gray>their inventory in read-only mode.",
            "",
            "<yellow>Right-click: <white>Inspect inventory"
    );

    public InspectTool(org.flennn.manager.LightStaffManager manager) {
        super(manager, "inspect", manager.resolveToolMaterial("inspect", Material.BOOK), "<gold><bold>Inspect Inventory</bold></gold>", LORE, Sound.BLOCK_CHEST_OPEN);
    }

    @Override
    public boolean handleInteraction(Player player) {
        sendMessage(player, "tools.inspect_hint", "<yellow>Right-click a player to inspect their inventory.</yellow>");
        playSound(player);
        manager.setCooldown(player);
        return true;
    }

    @Override
    public boolean handlePlayerInteraction(Player staff, Player target) {
        if (staff == null || target == null) return false;
        boolean targetIsStaff = LightStaff.getInstance().getPermissionManager().isStaff(target);
        if (targetIsStaff && !LightStaff.getInstance().getPermissionManager().canInspectStaff(staff)) {
            sendMessage(staff, "tools.inspect_staff_denied", "<red>You can't inspect other staff members' inventories.</red>");
            return true;
        }

        Component title = Component.text("Inventory of ", NamedTextColor.DARK_GRAY)
                .append(Component.text(target.getName(), NamedTextColor.AQUA));
        Inventory inventory = Bukkit.createInventory(new InspectInventoryHolder(target.getUniqueId()), 54, title);
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < Math.min(36, contents.length); i++) {
            if (contents[i] != null) {
                inventory.setItem(i, contents[i].clone());
            }
        }
        ItemStack[] armor = target.getInventory().getArmorContents();
        inventory.setItem(45, armor.length > 3 && armor[3] != null ? armor[3].clone() : null);
        inventory.setItem(46, armor.length > 2 && armor[2] != null ? armor[2].clone() : null);
        inventory.setItem(47, armor.length > 1 && armor[1] != null ? armor[1].clone() : null);
        inventory.setItem(48, armor.length > 0 && armor[0] != null ? armor[0].clone() : null);
        inventory.setItem(49, new ItemBuilder(Material.NAME_TAG)
                .setName("<yellow><bold>" + target.getName() + "'s Inventory</bold></yellow>")
                .setLore(Arrays.asList(
                        "<gray>This is a read-only view",
                        "<gray>of the player's inventory."
                ))
                .toItemStack());
        staff.openInventory(inventory);
        sendMessage(staff, "tools.inspect_opened", "<green>Opened inventory of <yellow>{player}</yellow>.</green>", java.util.Map.of("player", target.getName()));
        playSound(staff);
        manager.setCooldown(staff);
        LightStaff.getInstance().audit("inspect_inventory", staff.getName(), target.getName(), "readonly=true");
        return true;
    }
}
