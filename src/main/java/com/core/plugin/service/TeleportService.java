package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages teleportation, TPA requests with expiry, and back-location tracking.
 */
public final class TeleportService implements Service {

    private static final long TPA_TIMEOUT_MS = 60_000;

    private final CorePlugin plugin;
    private final Map<UUID, UUID> tpaRequests = new HashMap<>();
    private final Map<UUID, Long> tpaTimestamps = new HashMap<>();
    private final Map<UUID, Location> backLocations = new HashMap<>();

    public TeleportService(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override public void enable() {}

    @Override
    public void disable() {
        tpaRequests.clear();
        tpaTimestamps.clear();
        backLocations.clear();
    }

    /** Teleport a player to a location, storing their previous location for /back. */
    public void teleport(Player player, Location destination) {
        backLocations.put(player.getUniqueId(), player.getLocation().clone());
        player.teleport(destination);
        SoundUtil.teleport(player);
    }

    /** Teleport a player to another player. */
    public void teleport(Player player, Player target) {
        teleport(player, target.getLocation());
    }

    /** Send a TPA request from requester to target. */
    public void sendTpaRequest(Player requester, Player target) {
        tpaRequests.put(target.getUniqueId(), requester.getUniqueId());
        tpaTimestamps.put(target.getUniqueId(), System.currentTimeMillis());

        Lang.send(requester, "tpa.request-sent", "player", target.getName());
        Lang.send(target, "tpa.request-received", "player", requester.getName());
        Lang.send(target, "tpa.request-hint");
        SoundUtil.toggleOn(target);
    }

    /** Accept a pending TPA request. Returns true if successful. */
    public boolean acceptTpa(Player target) {
        UUID requesterId = tpaRequests.remove(target.getUniqueId());
        Long timestamp = tpaTimestamps.remove(target.getUniqueId());

        if (requesterId == null || timestamp == null) {
            Lang.send(target, "tpa.no-pending");
            return false;
        }

        if (System.currentTimeMillis() - timestamp > TPA_TIMEOUT_MS) {
            Lang.send(target, "tpa.expired");
            return false;
        }

        Player requester = plugin.getServer().getPlayer(requesterId);
        if (requester == null || !requester.isOnline()) {
            Lang.send(target, "tpa.requester-offline");
            return false;
        }

        teleport(requester, target);
        Lang.send(requester, "tpa.accepted-by", "player", target.getName());
        Lang.send(target, "tpa.you-accepted", "player", requester.getName());
        return true;
    }

    /** Deny a pending TPA request. */
    public boolean denyTpa(Player target) {
        UUID requesterId = tpaRequests.remove(target.getUniqueId());
        tpaTimestamps.remove(target.getUniqueId());

        if (requesterId == null) {
            Lang.send(target, "tpa.no-pending");
            return false;
        }

        Player requester = plugin.getServer().getPlayer(requesterId);
        if (requester != null && requester.isOnline()) {
            Lang.send(requester, "tpa.denied-by", "player", target.getName());
        }
        Lang.send(target, "tpa.denied");
        return true;
    }

    public Location getBackLocation(UUID playerId) {
        return backLocations.get(playerId);
    }

    public boolean hasPendingRequest(UUID targetId) {
        return tpaRequests.containsKey(targetId);
    }
}
