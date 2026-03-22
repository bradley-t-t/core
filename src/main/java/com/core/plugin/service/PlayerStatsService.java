package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.data.DataManager;
import com.core.plugin.lang.Lang;
import com.core.plugin.stats.Achievement;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks player statistics in memory for online players, flushing to YAML
 * on quit. Handles achievement detection on stat increments and periodic
 * auto-save to guard against crashes.
 */
public final class PlayerStatsService implements Service {

    private static final long TICKS_PER_MINUTE = 1_200L;

    private final CorePlugin plugin;
    private final DataManager dataManager;
    private final Map<UUID, Map<String, Long>> statCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStartMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> unlockedCache = new ConcurrentHashMap<>();
    private int autoSaveTaskId = -1;

    public PlayerStatsService(CorePlugin plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.dataManager();
    }

    @Override
    public void enable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player.getUniqueId());
        }

        int intervalMinutes = plugin.getConfig().getInt("auto-save-interval-minutes", 5);
        if (intervalMinutes > 0) {
            long intervalTicks = intervalMinutes * TICKS_PER_MINUTE;
            autoSaveTaskId = Bukkit.getScheduler()
                    .runTaskTimerAsynchronously(plugin, this::saveAllCached, intervalTicks, intervalTicks)
                    .getTaskId();
        }
    }

    @Override
    public void disable() {
        if (autoSaveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autoSaveTaskId);
        }
        saveAllCached();
        statCache.clear();
        sessionStartMillis.clear();
        unlockedCache.clear();
    }

    /** Load stats from disk into memory and start the session timer. */
    public void loadPlayer(UUID playerId) {
        StatRegistry statRegistry = statRegistry();
        Set<String> keys = statRegistry != null ? statRegistry.getKeys() : Set.of();

        Map<String, Long> stats = new LinkedHashMap<>();
        for (String key : keys) {
            stats.put(key, dataManager.getStat(playerId, key));
        }
        statCache.put(playerId, stats);
        sessionStartMillis.put(playerId, System.currentTimeMillis());

        Set<String> unlocked = new HashSet<>(dataManager.getUnlockedAchievements(playerId));
        unlockedCache.put(playerId, unlocked);
    }

    /** Flush play time delta + all stats to disk, then remove from cache. */
    public void unloadPlayer(UUID playerId) {
        flushPlayTime(playerId);
        Map<String, Long> stats = statCache.remove(playerId);
        if (stats != null) {
            dataManager.setAllStats(playerId, stats);
        }
        sessionStartMillis.remove(playerId);
        unlockedCache.remove(playerId);
    }

    /** Increment a stat by the given amount. */
    public void incrementStat(UUID playerId, String statKey, long amount) {
        Map<String, Long> stats = statCache.get(playerId);
        if (stats == null) return;

        long newValue = stats.merge(statKey, amount, Long::sum);
        checkAchievements(playerId, statKey, newValue);
    }

    /** Increment a stat by 1. */
    public void incrementStat(UUID playerId, String statKey) {
        incrementStat(playerId, statKey, 1);
    }

    /** Get a single stat value. Falls through to disk for offline players. */
    public long getStat(UUID playerId, String statKey) {
        Map<String, Long> cached = statCache.get(playerId);
        if (cached != null) {
            if (StatRegistry.PLAY_TIME_KEY.equals(statKey)) {
                return cached.getOrDefault(statKey, 0L) + liveSessionMinutes(playerId);
            }
            return cached.getOrDefault(statKey, 0L);
        }
        return dataManager.getStat(playerId, statKey);
    }

    /** Get all stats. Includes live session play time for online players. */
    public Map<String, Long> getAllStats(UUID playerId) {
        Map<String, Long> cached = statCache.get(playerId);
        if (cached != null) {
            Map<String, Long> snapshot = new LinkedHashMap<>(cached);
            snapshot.merge(StatRegistry.PLAY_TIME_KEY, liveSessionMinutes(playerId), Long::sum);
            return snapshot;
        }
        StatRegistry statRegistry = statRegistry();
        Set<String> keys = statRegistry != null ? statRegistry.getKeys() : Set.of();
        return dataManager.getAllStats(playerId, keys);
    }

    /** Get first join timestamp. */
    public long getFirstJoin(UUID playerId) {
        return dataManager.getFirstJoin(playerId);
    }

    /** Record first join if not already set. */
    public void recordFirstJoinIfNew(UUID playerId) {
        if (dataManager.getFirstJoin(playerId) == 0) {
            dataManager.setFirstJoin(playerId, System.currentTimeMillis());
        }
    }

    /** Get unlocked achievement keys. Falls through to disk for offline players. */
    public Set<String> getUnlockedAchievements(UUID playerId) {
        Set<String> cached = unlockedCache.get(playerId);
        if (cached != null) return Set.copyOf(cached);
        return Set.copyOf(dataManager.getUnlockedAchievements(playerId));
    }

    /** Check if new achievements are earned and award them. */
    private void checkAchievements(UUID playerId, String statKey, long currentValue) {
        Set<String> unlocked = unlockedCache.get(playerId);
        if (unlocked == null) return;

        AchievementRegistry registry = achievementRegistry();
        if (registry == null) return;

        for (Achievement achievement : registry.getForStat(statKey)) {
            if (unlocked.contains(achievement.key())) continue;
            if (currentValue < achievement.threshold()) continue;

            unlocked.add(achievement.key());
            dataManager.addUnlockedAchievement(playerId, achievement.key());

            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                Lang.send(player, "stats.achievement-unlocked", "name", achievement.displayName());
                Lang.sendRaw(player, "stats.achievement-desc", "description", achievement.description());
            }
        }
    }

    /** Compute live session minutes for the current session. */
    private long liveSessionMinutes(UUID playerId) {
        Long start = sessionStartMillis.get(playerId);
        if (start == null) return 0;
        return (System.currentTimeMillis() - start) / 60_000;
    }

    /** Flush current session time into the cached play-time stat without ending the session. */
    private void flushPlayTime(UUID playerId) {
        long sessionMinutes = liveSessionMinutes(playerId);
        if (sessionMinutes <= 0) return;

        Map<String, Long> stats = statCache.get(playerId);
        if (stats != null) {
            stats.merge(StatRegistry.PLAY_TIME_KEY, sessionMinutes, Long::sum);
        }
        // Reset session start so we don't double-count
        sessionStartMillis.put(playerId, System.currentTimeMillis());
    }

    /** Save all cached players to disk (auto-save / shutdown). */
    private void saveAllCached() {
        for (UUID playerId : statCache.keySet()) {
            flushPlayTime(playerId);
            Map<String, Long> stats = statCache.get(playerId);
            if (stats != null) {
                dataManager.setAllStats(playerId, stats);
            }
        }
    }

    /** Lazily resolve StatRegistry to avoid ordering issues with reflection-based auto-discovery. */
    private StatRegistry statRegistry() {
        return plugin.services().get(StatRegistry.class);
    }

    /** Lazily resolve AchievementRegistry to avoid ordering issues with reflection-based auto-discovery. */
    private AchievementRegistry achievementRegistry() {
        return plugin.services().get(AchievementRegistry.class);
    }
}
