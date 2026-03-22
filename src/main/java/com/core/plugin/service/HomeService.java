package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.data.DataManager;
import org.bukkit.Location;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Delegates home management to {@link DataManager} for persistence.
 */
public final class HomeService implements Service {

    private final DataManager dataManager;

    public HomeService(CorePlugin plugin) {
        this.dataManager = plugin.dataManager();
    }

    @Override public void enable() {}
    @Override public void disable() {}

    public void setHome(UUID playerId, String name, Location location) {
        dataManager.setHome(playerId, name, location);
    }

    public Location getHome(UUID playerId, String name) {
        return dataManager.getHome(playerId, name);
    }

    public void deleteHome(UUID playerId, String name) {
        dataManager.deleteHome(playerId, name);
    }

    public Map<String, Location> getHomes(UUID playerId) {
        return dataManager.getHomes(playerId);
    }

    public Set<String> getHomeNames(UUID playerId) {
        return dataManager.getHomeNames(playerId);
    }

    public boolean homeExists(UUID playerId, String name) {
        return dataManager.getHome(playerId, name) != null;
    }
}
