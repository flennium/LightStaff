package org.flennn.tools;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.flennn.manager.LightStaffManager;
import org.flennn.util.ItemBuilder;
import java.util.List;
import org.bukkit.persistence.PersistentDataType;
import org.flennn.LightStaff;

public abstract class StaffTool {
    protected final LightStaffManager manager;
    protected final String key;
    protected final Material material;
    protected final String displayName;
    protected final List<String> lore;
    protected final Sound interactionSound;
    protected final float soundVolume;
    protected final float soundPitch;
    private final ItemStack templateItem;

    public StaffTool(LightStaffManager manager, String key, Material material, String displayName, List<String> lore, Sound interactionSound) {
        this.manager = manager;
        this.key = key;
        ToolDefinition definition = manager.getToolDefinition(key);
        this.material = definition != null ? definition.material() : material;
        this.displayName = definition != null ? definition.displayName() : displayName;
        this.lore = definition != null ? definition.lore() : lore;
        this.interactionSound = definition != null ? definition.sound() : interactionSound;
        this.soundVolume = definition != null ? definition.soundVolume() : 0.5f;
        this.soundPitch = definition != null ? definition.soundPitch() : 0.5f;
        this.templateItem = buildTemplateItem();
    }

    private ItemStack buildTemplateItem() {
        return new ItemBuilder(material)
                .setName(displayName)
                .setLore(lore)
                .addFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
                .data(LightStaff.getInstance().getStaffToolKey(), PersistentDataType.BYTE, (byte) 1)
                .data(LightStaff.getInstance().getStaffToolIdKey(), PersistentDataType.STRING, key)
                .toItemStack();
    }

    public ItemStack createTool() {
        return templateItem.clone();
    }

    public abstract boolean handleInteraction(Player player);
    public boolean handlePlayerInteraction(Player staff, Player target) { return false; }
    public String getKey() { return key; }
    protected void playSound(Player player) {
        if (player != null && interactionSound != null) {
            player.playSound(player.getLocation(), interactionSound, soundVolume, soundPitch);
        }
    }
    protected void sendMessage(Player player, String path, String fallback, java.util.Map<String, String> placeholders) {
        LightStaff.getInstance().getMessageManager().send(player, path, fallback, placeholders);
    }

    protected void sendMessage(Player player, String path, String fallback) {
        LightStaff.getInstance().getMessageManager().send(player, path, fallback);
    }
}
