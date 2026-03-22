package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * Manages the overworld border on startup based on config.yml settings.
 * Provides the border radius and buffer for other systems (wild teleport).
 */
public final class WorldBorderService implements Service {

    private final CorePlugin plugin;
    private int radius;
    private int wildBuffer;

    public WorldBorderService(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        radius = plugin.getConfig().getInt("world-border.radius", 5000);
        wildBuffer = plugin.getConfig().getInt("world-border.wild-buffer", 200);
        boolean enabled = plugin.getConfig().getBoolean("world-border.enabled", true);

        if (!enabled) return;

        World overworld = Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElse(null);

        if (overworld == null) return;

        WorldBorder border = overworld.getWorldBorder();
        border.setCenter(overworld.getSpawnLocation());
        border.setSize(radius * 2.0);

        plugin.getLogger().info("World border set to " + radius + " block radius in " + overworld.getName() + ".");

        // Disable vanilla advancement announcements in all worlds
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        }
    }

    @Override
    public void disable() {}

    /** Maximum distance from center for wild teleports (radius minus buffer). */
    public int wildRadius() {
        return Math.max(100, radius - wildBuffer);
    }

    public int radius() { return radius; }
}
