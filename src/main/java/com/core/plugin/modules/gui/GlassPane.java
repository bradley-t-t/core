package com.core.plugin.modules.gui;

import org.bukkit.Material;

/**
 * Factory for blank glass pane {@link GuiItem}s used as GUI backgrounds and borders.
 * All panes have an empty display name and no lore.
 */
public final class GlassPane {

    private GlassPane() {}

    public static GuiItem gray()      { return pane(Material.GRAY_STAINED_GLASS_PANE); }
    public static GuiItem black()     { return pane(Material.BLACK_STAINED_GLASS_PANE); }
    public static GuiItem white()     { return pane(Material.WHITE_STAINED_GLASS_PANE); }
    public static GuiItem red()       { return pane(Material.RED_STAINED_GLASS_PANE); }
    public static GuiItem blue()      { return pane(Material.BLUE_STAINED_GLASS_PANE); }
    public static GuiItem green()     { return pane(Material.GREEN_STAINED_GLASS_PANE); }
    public static GuiItem yellow()    { return pane(Material.YELLOW_STAINED_GLASS_PANE); }
    public static GuiItem purple()    { return pane(Material.PURPLE_STAINED_GLASS_PANE); }
    public static GuiItem cyan()      { return pane(Material.CYAN_STAINED_GLASS_PANE); }
    public static GuiItem lightGray() { return pane(Material.LIGHT_GRAY_STAINED_GLASS_PANE); }
    public static GuiItem orange()    { return pane(Material.ORANGE_STAINED_GLASS_PANE); }

    /** Create a pane of any glass material. */
    public static GuiItem pane(Material material) {
        return GuiItem.of(material).name(" ").hideFlags();
    }
}
