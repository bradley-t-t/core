package com.core.plugin.gui;

import com.core.plugin.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central listener that manages all active GUI sessions. Handles click routing,
 * drag prevention, and cleanup on close.
 */
public final class GuiListener implements Listener {

    private final Map<UUID, ActiveGui> activeGuis = new ConcurrentHashMap<>();

    /** Register a GUI as active for a player and open it. */
    public void open(Player player, ActiveGui gui) {
        activeGuis.put(player.getUniqueId(), gui);
        player.openInventory(gui.inventory());
        SoundUtil.openGui(player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ActiveGui gui = activeGuis.get(player.getUniqueId());
        if (gui == null) return;

        if (!gui.isEditable()) {
            event.setCancelled(true);
        }

        // Only handle clicks in the top inventory (the GUI itself)
        if (event.getClickedInventory() != gui.inventory()) return;

        GuiItem item = gui.itemAt(event.getSlot());
        if (item != null && item.hasClickHandler()) {
            SoundUtil.clickGui(player);
            item.clickHandler().accept(event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ActiveGui gui = activeGuis.get(player.getUniqueId());
        if (gui == null || gui.isEditable()) return;

        // Cancel drag if any slot is in the GUI inventory
        int guiSize = gui.inventory().getSize();
        boolean dragsIntoGui = event.getRawSlots().stream().anyMatch(slot -> slot < guiSize);
        if (dragsIntoGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            ActiveGui gui = activeGuis.get(player.getUniqueId());
            // Only remove if the closing inventory is the one we're tracking,
            // so opening a new GUI mid-flow doesn't wipe the new registration.
            if (gui != null && gui.inventory().equals(event.getInventory())) {
                activeGuis.remove(player.getUniqueId());
            }
        }
    }

    /** Check if a player has an active GUI open. */
    public boolean hasActiveGui(UUID playerId) {
        return activeGuis.containsKey(playerId);
    }
}
