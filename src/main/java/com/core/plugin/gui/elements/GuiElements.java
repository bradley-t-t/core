package com.core.plugin.gui.elements;

import com.core.plugin.gui.GuiItem;
import com.core.plugin.lang.Lang;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.function.Consumer;

/**
 * Factory for common reusable GUI navigation and action items.
 * All items pull display names from the language file for consistency.
 */
public final class GuiElements {

    private GuiElements() {}

    /** Arrow-based back button with the standard "gui.back" label. */
    public static GuiItem backButton(Consumer<InventoryClickEvent> onClick) {
        return GuiItem.of(Material.ARROW)
                .name(Lang.get("gui.back"))
                .onClick(onClick);
    }

    /** Lime concrete confirm button with the standard "gui.confirm" label. */
    public static GuiItem confirmButton(Consumer<InventoryClickEvent> onClick) {
        return GuiItem.of(Material.LIME_CONCRETE)
                .name(Lang.get("gui.confirm"))
                .onClick(onClick);
    }

    /** Red concrete cancel button with the standard "gui.cancel" label. */
    public static GuiItem cancelButton(Consumer<InventoryClickEvent> onClick) {
        return GuiItem.of(Material.RED_CONCRETE)
                .name(Lang.get("gui.cancel"))
                .onClick(onClick);
    }

    /** Arrow-based previous page button for paginated GUIs. */
    public static GuiItem previousPageButton(Consumer<InventoryClickEvent> onClick) {
        return GuiItem.of(Material.ARROW)
                .name(Lang.get("gui.previous-page"))
                .onClick(onClick);
    }

    /** Arrow-based next page button for paginated GUIs. */
    public static GuiItem nextPageButton(Consumer<InventoryClickEvent> onClick) {
        return GuiItem.of(Material.ARROW)
                .name(Lang.get("gui.next-page"))
                .onClick(onClick);
    }

    /** Paper page indicator showing current/total page count. Amount reflects the current page number. */
    public static GuiItem pageIndicator(int currentPage, int totalPages) {
        return GuiItem.of(Material.PAPER)
                .name(Lang.get("gui.page-indicator", "current", currentPage, "total", totalPages))
                .amount(currentPage);
    }
}
