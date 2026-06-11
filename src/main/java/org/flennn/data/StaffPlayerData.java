package org.flennn.data;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

public class StaffPlayerData {
    private final ItemStack[] savedInventory;
    private final ItemStack[] savedArmor;
    private final ItemStack savedOffhand;
    private final float savedExp;
    private final int savedLevel;
    private final boolean savedFlying;
    private final boolean savedAllowFlight;
    private final GameMode savedGameMode;

    public StaffPlayerData(ItemStack[] savedInventory, ItemStack[] savedArmor, ItemStack savedOffhand, float savedExp, int savedLevel, boolean savedFlying, boolean savedAllowFlight, GameMode savedGameMode) {
        this.savedInventory = savedInventory != null ? savedInventory : new ItemStack[36];
        this.savedArmor = savedArmor != null ? savedArmor : new ItemStack[4];
        this.savedOffhand = savedOffhand;
        this.savedExp = savedExp;
        this.savedLevel = savedLevel;
        this.savedFlying = savedFlying;
        this.savedAllowFlight = savedAllowFlight;
        this.savedGameMode = savedGameMode != null ? savedGameMode : GameMode.SURVIVAL;
    }

    public StaffPlayerData(Player player) {
        PlayerInventory inv = player.getInventory();
        this.savedInventory = inv.getStorageContents().clone();
        this.savedArmor = inv.getArmorContents().clone();
        this.savedOffhand = inv.getItemInOffHand().clone();
        this.savedExp = player.getExp();
        this.savedLevel = player.getLevel();
        this.savedFlying = player.isFlying();
        this.savedAllowFlight = player.getAllowFlight();
        this.savedGameMode = player.getGameMode();
    }

    public void restorePlayer(Player player) {
        if (player == null) return;
        PlayerInventory inventory = player.getInventory();
        if (savedInventory != null) inventory.setStorageContents(normalize(savedInventory, 36));
        if (savedArmor != null) inventory.setArmorContents(normalize(savedArmor, 4));
        inventory.setItemInOffHand(savedOffhand);
        player.setExp(savedExp);
        player.setLevel(savedLevel);
        player.setAllowFlight(savedAllowFlight);
        player.setFlying(savedFlying);
        if (savedGameMode != null) player.setGameMode(savedGameMode);
    }

    public static String serializeItemsBase64(ItemStack[] items) {
        if (items == null) return "";
        YamlConfiguration config = new YamlConfiguration();
        config.set("items", items);
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(config.saveToString().getBytes());
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    public static ItemStack[] deserializeItemsBase64(String data) {
        if (data == null || data.isEmpty()) return new ItemStack[0];
        try {
            String yaml = new String(Base64.getDecoder().decode(data));
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(yaml);
            List<ItemStack> list = (List<ItemStack>) config.getList("items");
            return list != null ? list.toArray(new ItemStack[0]) : new ItemStack[0];
        } catch (Exception e) {
            return new ItemStack[0];
        }
    }

    public static String serializeItemBase64(ItemStack item) {
        return serializeItemsBase64(new ItemStack[]{item});
    }

    public static ItemStack deserializeItemBase64(String data) {
        ItemStack[] items = deserializeItemsBase64(data);
        return items.length > 0 ? items[0] : null;
    }

    private static ItemStack[] normalize(ItemStack[] items, int expectedLength) {
        ItemStack[] normalized = new ItemStack[expectedLength];
        if (items == null) return normalized;
        System.arraycopy(items, 0, normalized, 0, Math.min(items.length, normalized.length));
        return normalized;
    }

    // Getters for all fields
    public ItemStack[] getSavedInventory() { return savedInventory; }
    public ItemStack[] getSavedArmor() { return savedArmor; }
    public ItemStack getSavedOffhand() { return savedOffhand; }
    public float getSavedExp() { return savedExp; }
    public int getSavedLevel() { return savedLevel; }
    public boolean isSavedFlying() { return savedFlying; }
    public boolean isSavedAllowFlight() { return savedAllowFlight; }
    public GameMode getSavedGameMode() { return savedGameMode; }
}
