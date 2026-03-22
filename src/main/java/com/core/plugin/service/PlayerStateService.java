package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.data.DataManager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks transient player states (god, vanish, fly, afk, freeze) and persistent
 * states (mute, nickname, last-seen) via {@link DataManager}.
 */
public final class PlayerStateService implements Service {

    private final CorePlugin plugin;
    private final Set<UUID> godMode = new HashSet<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> frozen = new HashSet<>();
    private final Set<UUID> afk = new HashSet<>();
    private final Map<UUID, UUID> lastMessager = new HashMap<>();

    public PlayerStateService(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override public void enable() {}

    @Override
    public void disable() {
        // Flush last-seen for all online players before shutdown
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateLastSeen(player.getUniqueId());
        }
        godMode.clear();
        vanished.clear();
        frozen.clear();
        afk.clear();
        lastMessager.clear();
    }

    // --- God Mode ---
    public boolean toggleGod(UUID playerId) { return toggle(godMode, playerId); }
    public boolean isGod(UUID playerId) { return godMode.contains(playerId); }

    // --- Vanish ---
    public boolean toggleVanish(UUID playerId) { return toggle(vanished, playerId); }
    public boolean isVanished(UUID playerId) { return vanished.contains(playerId); }
    public Set<UUID> getVanished() { return Set.copyOf(vanished); }

    // --- Freeze ---
    public boolean toggleFreeze(UUID playerId) { return toggle(frozen, playerId); }
    public boolean isFrozen(UUID playerId) { return frozen.contains(playerId); }

    // --- AFK ---
    public boolean toggleAfk(UUID playerId) { return toggle(afk, playerId); }
    public boolean isAfk(UUID playerId) { return afk.contains(playerId); }

    // --- Mute (persistent) ---
    public void setMuted(UUID playerId, boolean muted) { plugin.dataManager().setMuted(playerId, muted); }
    public boolean isMuted(UUID playerId) { return plugin.dataManager().isMuted(playerId); }

    // --- Nickname (persistent) ---
    public void setNickname(UUID playerId, String nickname) { plugin.dataManager().setNickname(playerId, nickname); }
    public String getNickname(UUID playerId) { return plugin.dataManager().getNickname(playerId); }

    // --- Last Seen (persistent) ---
    public void updateLastSeen(UUID playerId) { plugin.dataManager().setLastSeen(playerId, System.currentTimeMillis()); }
    public long getLastSeen(UUID playerId) { return plugin.dataManager().getLastSeen(playerId); }

    // --- Reply Tracking ---
    public void setLastMessager(UUID playerId, UUID messager) { lastMessager.put(playerId, messager); }
    public UUID getLastMessager(UUID playerId) { return lastMessager.get(playerId); }

    private boolean toggle(Set<UUID> set, UUID playerId) {
        if (set.contains(playerId)) {
            set.remove(playerId);
            return false;
        }
        set.add(playerId);
        return true;
    }
}
