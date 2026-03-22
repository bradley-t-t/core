package com.core.plugin.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Serialize and deserialize {@link Location} objects to/from YAML config sections.
 */
public final class LocationUtil {

    private LocationUtil() {}

    /** Serialize a location into a config section. */
    public static void save(ConfigurationSection section, String path, Location location) {
        section.set(path + ".world", location.getWorld().getName());
        section.set(path + ".x", location.getX());
        section.set(path + ".y", location.getY());
        section.set(path + ".z", location.getZ());
        section.set(path + ".yaw", (double) location.getYaw());
        section.set(path + ".pitch", (double) location.getPitch());
    }

    /** Deserialize a location from a config section. Returns null if world doesn't exist. */
    public static Location load(ConfigurationSection section, String path) {
        String worldName = section.getString(path + ".world");
        if (worldName == null) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        return new Location(
                world,
                section.getDouble(path + ".x"),
                section.getDouble(path + ".y"),
                section.getDouble(path + ".z"),
                (float) section.getDouble(path + ".yaw"),
                (float) section.getDouble(path + ".pitch")
        );
    }

    /** Format a location into a readable string. */
    public static String format(Location location) {
        return String.format("&f%s &7(%.1f, %.1f, %.1f)",
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ());
    }
}
