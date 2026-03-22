package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.data.DataManager;
import org.bukkit.Location;

import java.util.Set;

/**
 * Delegates warp management to {@link DataManager} for persistence.
 */
public final class WarpService implements Service {

    private final DataManager dataManager;

    public WarpService(CorePlugin plugin) {
        this.dataManager = plugin.dataManager();
    }

    @Override public void enable() {}
    @Override public void disable() {}

    public void setWarp(String name, Location location) {
        dataManager.setWarp(name, location);
    }

    public Location getWarp(String name) {
        return dataManager.getWarp(name);
    }

    public void deleteWarp(String name) {
        dataManager.deleteWarp(name);
    }

    public Set<String> getWarpNames() {
        return dataManager.getWarpNames();
    }

    public boolean warpExists(String name) {
        return dataManager.getWarp(name) != null;
    }
}
