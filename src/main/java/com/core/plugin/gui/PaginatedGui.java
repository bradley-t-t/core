package com.core.plugin.gui;

import com.core.plugin.gui.elements.GuiElements;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Paginated GUI for displaying lists of items with navigation controls.
 * Content fills the inner area (rows 1 through 4), with glass pane borders
 * and navigation arrows in the bottom row. Supports an optional back button.
 */
public final class PaginatedGui {

    private static final int NAV_PREVIOUS_SLOT = 45;
    private static final int NAV_BACK_SLOT = 47;
    private static final int NAV_PAGE_INDICATOR_SLOT = 49;
    private static final int NAV_NEXT_SLOT = 53;
    private static final int CONTENT_START_ROW = 1;
    private static final int CONTENT_END_ROW = 4;
    private static final int CONTENT_COLS_START = 1;
    private static final int CONTENT_COLS_END = 7;
    private static final int ITEMS_PER_PAGE = (CONTENT_END_ROW - CONTENT_START_ROW + 1)
            * (CONTENT_COLS_END - CONTENT_COLS_START + 1);

    private final String title;
    private final List<GuiItem> contentItems;
    private final GuiListener guiListener;
    private Runnable onBack;

    public PaginatedGui(String title, List<GuiItem> contentItems, GuiListener guiListener) {
        this.title = title;
        this.contentItems = contentItems;
        this.guiListener = guiListener;
    }

    /** Set a back button action. When set, a back arrow appears in the bottom nav row. */
    public PaginatedGui onBack(Runnable onBack) {
        this.onBack = onBack;
        return this;
    }

    /** Open a specific page for a player. Page is 0-indexed. */
    public void open(Player player, int page) {
        int totalPages = Math.max(1, (int) Math.ceil((double) contentItems.size() / ITEMS_PER_PAGE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        GuiBuilder builder = GuiBuilder.create(title, 6)
                .border(GlassPane.gray())
                .fillRow(5, GlassPane.gray());

        // Place content items in the inner grid
        int startIndex = safePage * ITEMS_PER_PAGE;
        int contentSlot = 0;
        for (int row = CONTENT_START_ROW; row <= CONTENT_END_ROW; row++) {
            for (int col = CONTENT_COLS_START; col <= CONTENT_COLS_END; col++) {
                int itemIndex = startIndex + contentSlot;
                if (itemIndex < contentItems.size()) {
                    builder.item(row * 9 + col, contentItems.get(itemIndex));
                }
                contentSlot++;
            }
        }

        // Navigation — only shown when multiple pages exist
        if (totalPages > 1) {
            if (safePage > 0) {
                builder.item(NAV_PREVIOUS_SLOT, GuiElements.previousPageButton(event -> {
                    event.getWhoClicked().closeInventory();
                    open(player, safePage - 1);
                }));
            }

            builder.item(NAV_PAGE_INDICATOR_SLOT, GuiElements.pageIndicator(safePage + 1, totalPages));

            if (safePage < totalPages - 1) {
                builder.item(NAV_NEXT_SLOT, GuiElements.nextPageButton(event -> {
                    event.getWhoClicked().closeInventory();
                    open(player, safePage + 1);
                }));
            }
        }

        // Back button
        if (onBack != null) {
            builder.item(NAV_BACK_SLOT, GuiElements.backButton(event -> onBack.run()));
        }

        ActiveGui gui = builder.build(guiListener);
        guiListener.open(player, gui);
    }

    /** Open the first page. */
    public void open(Player player) {
        open(player, 0);
    }

    public int totalPages() {
        return Math.max(1, (int) Math.ceil((double) contentItems.size() / ITEMS_PER_PAGE));
    }
}
