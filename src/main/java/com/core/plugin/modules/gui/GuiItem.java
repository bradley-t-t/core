package com.core.plugin.modules.gui;

import com.core.plugin.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * A clickable GUI item with fluent builder API. Wraps an {@link ItemStack}
 * and an optional click handler.
 */
public final class GuiItem {

    private final ItemStack itemStack;
    private Consumer<InventoryClickEvent> clickHandler;

    private GuiItem(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public static GuiItem of(Material material) {
        return new GuiItem(new ItemStack(material));
    }

    public static GuiItem of(ItemStack itemStack) {
        return new GuiItem(itemStack.clone());
    }

    public GuiItem name(String name) {
        editMeta(meta -> meta.setDisplayName(MessageUtil.colorize(name)));
        return this;
    }

    public GuiItem lore(String... lines) {
        editMeta(meta -> meta.setLore(
                Arrays.stream(lines).map(MessageUtil::colorize).toList()
        ));
        return this;
    }

    public GuiItem amount(int amount) {
        itemStack.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    public GuiItem glow() {
        editMeta(meta -> {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
        return this;
    }

    public GuiItem hideFlags() {
        editMeta(meta -> meta.addItemFlags(ItemFlag.values()));
        return this;
    }

    public GuiItem onClick(Consumer<InventoryClickEvent> handler) {
        this.clickHandler = handler;
        return this;
    }

    public ItemStack itemStack() { return itemStack; }

    public Consumer<InventoryClickEvent> clickHandler() { return clickHandler; }

    public boolean hasClickHandler() { return clickHandler != null; }

    private void editMeta(Consumer<ItemMeta> editor) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            editor.accept(meta);
            itemStack.setItemMeta(meta);
        }
    }
}
