package com.core.plugin.gui;

import com.core.plugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Fluent builder for creating GUI inventories. Supports item placement, background
 * fills, border patterns, and editable mode for admin views.
 *
 * <pre>
 * GuiBuilder.create("&8My GUI", 3)
 *     .border(GlassPane.gray())
 *     .item(13, someItem)
 *     .build(guiListener);
 * </pre>
 */
public final class GuiBuilder {

    private final String title;
    private final int rows;
    private final int size;
    private final Map<Integer, GuiItem> items = new HashMap<>();
    private boolean editable;

    private GuiBuilder(String title, int rows) {
        this.title = MessageUtil.colorize(title);
        this.rows = rows;
        this.size = rows * 9;
    }

    public static GuiBuilder create(String title, int rows) {
        return new GuiBuilder(title, Math.max(1, Math.min(6, rows)));
    }

    /** Place an item at a specific slot. */
    public GuiBuilder item(int slot, GuiItem item) {
        if (slot >= 0 && slot < size) {
            items.put(slot, item);
        }
        return this;
    }

    /** Fill all empty slots with the given item (typically a glass pane). */
    public GuiBuilder fill(GuiItem filler) {
        for (int i = 0; i < size; i++) {
            items.putIfAbsent(i, filler);
        }
        return this;
    }

    /** Fill only the border slots (top, bottom, left, right edges). */
    public GuiBuilder border(GuiItem filler) {
        for (int i = 0; i < size; i++) {
            if (isBorderSlot(i)) {
                items.putIfAbsent(i, filler);
            }
        }
        return this;
    }

    /** Fill a specific row (0-indexed) with the given item. */
    public GuiBuilder fillRow(int row, GuiItem filler) {
        int start = row * 9;
        for (int i = start; i < start + 9 && i < size; i++) {
            items.putIfAbsent(i, filler);
        }
        return this;
    }

    /** Mark this GUI as editable -- clicks won't be cancelled. Used for invsee/trash. */
    public GuiBuilder editable(boolean editable) {
        this.editable = editable;
        return this;
    }

    /** Build the inventory and register it with the GUI listener. */
    public ActiveGui build(GuiListener guiListener) {
        Inventory inventory = Bukkit.createInventory(null, size, title);
        items.forEach((slot, item) -> inventory.setItem(slot, item.itemStack()));

        return new ActiveGui(inventory, Map.copyOf(items), editable);
    }

    private boolean isBorderSlot(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        return row == 0 || row == rows - 1 || col == 0 || col == 8;
    }
}
