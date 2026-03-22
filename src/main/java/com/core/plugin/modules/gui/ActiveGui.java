package com.core.plugin.modules.gui;

import org.bukkit.inventory.Inventory;

import java.util.Map;

/**
 * Represents a built GUI instance with its inventory, item mappings, and state.
 * Managed by {@link GuiListener} during the player's viewing session.
 */
public final class ActiveGui {

    private final Inventory inventory;
    private final Map<Integer, GuiItem> items;
    private final boolean editable;

    ActiveGui(Inventory inventory, Map<Integer, GuiItem> items, boolean editable) {
        this.inventory = inventory;
        this.items = items;
        this.editable = editable;
    }

    public Inventory inventory() { return inventory; }

    public GuiItem itemAt(int slot) { return items.get(slot); }

    public boolean isEditable() { return editable; }
}
