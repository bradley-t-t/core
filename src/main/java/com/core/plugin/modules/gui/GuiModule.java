package com.core.plugin.modules.gui;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.Module;

/**
 * Brings together the GUI framework: builders ({@link GuiBuilder}),
 * active sessions ({@link ActiveGui}), items ({@link GuiItem}),
 * pagination ({@link PaginatedGui}), glass panes ({@link GlassPane}),
 * and reusable elements ({@link com.core.plugin.modules.gui.elements.GuiElements}).
 * Click/close handling lives in {@link com.core.plugin.listener.GuiListener}.
 */
public final class GuiModule implements Module {

    @Override
    public void enable(CorePlugin plugin) {
    }

    @Override
    public void disable() {
    }

    @Override
    public String getName() {
        return "Gui";
    }
}
