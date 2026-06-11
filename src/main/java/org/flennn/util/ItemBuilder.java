package org.flennn.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ItemBuilder {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material == null ? Material.STONE : material, Math.max(1, amount));
        this.meta = item.getItemMeta();
    }

    public ItemBuilder setName(String name) {
        if (meta == null || name == null || name.isBlank()) return this;
        meta.displayName(component(name));
        return this;
    }

    public ItemBuilder setLore(List<String> lore) {
        if (meta == null || lore == null || lore.isEmpty()) return this;
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(component(line == null ? "" : line));
        }
        meta.lore(loreComponents);
        return this;
    }

    public ItemBuilder addEnchant(Enchantment enchantment, int level) {
        if (meta == null || enchantment == null || level <= 0) return this;
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    public ItemBuilder addFlags(ItemFlag... flags) {
        if (meta == null || flags == null) return this;
        meta.addItemFlags(flags);
        return this;
    }

    public ItemBuilder addFlag(ItemFlag flag) {
        return addFlags(flag);
    }

    public ItemBuilder unbreakable(boolean unbreakable) {
        if (meta != null) meta.setUnbreakable(unbreakable);
        return this;
    }

    public ItemBuilder customModelData(Integer data) {
        if (meta != null && data != null) meta.setCustomModelData(data);
        return this;
    }

    public <T, Z> ItemBuilder data(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        if (meta != null && key != null && type != null && value != null) {
            meta.getPersistentDataContainer().set(key, type, value);
        }
        return this;
    }

    public ItemBuilder editMeta(Consumer<ItemMeta> editor) {
        if (meta != null && editor != null) editor.accept(meta);
        return this;
    }

    public ItemStack toItemStack() {
        if (meta != null) item.setItemMeta(meta);
        return item;
    }

    private static Component component(String value) {
        return MINI_MESSAGE.deserialize(value).decoration(TextDecoration.ITALIC, false);
    }
}
