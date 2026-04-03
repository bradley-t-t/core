package com.core.plugin.data;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.punishment.PunishmentRecord;
import com.core.plugin.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Handles all YAML-based persistence: player homes, warps, spawn, and player metadata.
 * Each player gets their own YAML file; server-wide data uses shared files.
 */
public final class DataManager {

    private final CorePlugin plugin;
    private final File playerDataDir;
    private final File warpsFile;
    private final File spawnFile;

    private YamlConfiguration warpsConfig;
    private YamlConfiguration spawnConfig;

    public DataManager(CorePlugin plugin) {
        this.plugin = plugin;
        this.playerDataDir = new File(plugin.getDataFolder(), "playerdata");
        this.warpsFile = new File(plugin.getDataFolder(), "warps.yml");
        this.spawnFile = new File(plugin.getDataFolder(), "spawn.yml");

        playerDataDir.mkdirs();
        warpsConfig = YamlConfiguration.loadConfiguration(warpsFile);
        spawnConfig = YamlConfiguration.loadConfiguration(spawnFile);
    }

    // --- Player Homes ---

    public void setHome(UUID playerId, String name, Location location) {
        var config = loadPlayerConfig(playerId);
        LocationUtil.save(config, "homes." + name.toLowerCase(), location);
        savePlayerConfig(playerId, config);
    }

    public Location getHome(UUID playerId, String name) {
        var config = loadPlayerConfig(playerId);
        return LocationUtil.load(config, "homes." + name.toLowerCase());
    }

    public void deleteHome(UUID playerId, String name) {
        var config = loadPlayerConfig(playerId);
        config.set("homes." + name.toLowerCase(), null);
        savePlayerConfig(playerId, config);
    }

    public Map<String, Location> getHomes(UUID playerId) {
        var config = loadPlayerConfig(playerId);
        var section = config.getConfigurationSection("homes");
        if (section == null) return Map.of();

        Map<String, Location> homes = new HashMap<>();
        for (String key : section.getKeys(false)) {
            Location loc = LocationUtil.load(config, "homes." + key);
            if (loc != null) homes.put(key, loc);
        }
        return homes;
    }

    public Set<String> getHomeNames(UUID playerId) {
        var config = loadPlayerConfig(playerId);
        var section = config.getConfigurationSection("homes");
        return section != null ? section.getKeys(false) : Set.of();
    }

    // --- Warps ---

    public void setWarp(String name, Location location) {
        LocationUtil.save(warpsConfig, "warps." + name.toLowerCase(), location);
        saveConfig(warpsConfig, warpsFile);
    }

    public Location getWarp(String name) {
        return LocationUtil.load(warpsConfig, "warps." + name.toLowerCase());
    }

    public void deleteWarp(String name) {
        warpsConfig.set("warps." + name.toLowerCase(), null);
        saveConfig(warpsConfig, warpsFile);
    }

    public Set<String> getWarpNames() {
        var section = warpsConfig.getConfigurationSection("warps");
        return section != null ? section.getKeys(false) : Set.of();
    }

    // --- Spawn ---

    public void setSpawn(Location location) {
        LocationUtil.save(spawnConfig, "spawn", location);
        saveConfig(spawnConfig, spawnFile);
    }

    public Location getSpawn() {
        return LocationUtil.load(spawnConfig, "spawn");
    }

    // --- Player Metadata ---

    public void setLastSeen(UUID playerId, long timestamp) {
        var config = loadPlayerConfig(playerId);
        config.set("last-seen", timestamp);
        savePlayerConfig(playerId, config);
    }

    public long getLastSeen(UUID playerId) {
        return loadPlayerConfig(playerId).getLong("last-seen", 0);
    }

    public void setNickname(UUID playerId, String nickname) {
        var config = loadPlayerConfig(playerId);
        config.set("nickname", nickname);
        savePlayerConfig(playerId, config);
    }

    public String getNickname(UUID playerId) {
        return loadPlayerConfig(playerId).getString("nickname");
    }

    public void setMuted(UUID playerId, boolean muted) {
        var config = loadPlayerConfig(playerId);
        config.set("muted", muted);
        savePlayerConfig(playerId, config);
    }

    public boolean isMuted(UUID playerId) {
        var config = loadPlayerConfig(playerId);
        boolean muted = config.getBoolean("muted", false);
        if (!muted) return false;

        long expiresAt = config.getLong("mute-expires", 0);
        if (expiresAt > 0 && expiresAt < System.currentTimeMillis()) {
            config.set("muted", false);
            config.set("mute-expires", null);
            savePlayerConfig(playerId, config);
            return false;
        }
        return true;
    }

    // --- First Join ---

    public void setFirstJoin(UUID playerId, long timestamp) {
        var config = loadPlayerConfig(playerId);
        config.set("first-join", timestamp);
        savePlayerConfig(playerId, config);
    }

    public long getFirstJoin(UUID playerId) {
        return loadPlayerConfig(playerId).getLong("first-join", 0);
    }

    // --- Statistics ---

    public void setStat(UUID playerId, String statKey, long value) {
        var config = loadPlayerConfig(playerId);
        config.set("stats." + statKey, value);
        savePlayerConfig(playerId, config);
    }

    public long getStat(UUID playerId, String statKey) {
        return loadPlayerConfig(playerId).getLong("stats." + statKey, 0);
    }

    public Map<String, Long> getAllStats(UUID playerId, Set<String> statKeys) {
        var config = loadPlayerConfig(playerId);
        Map<String, Long> stats = new LinkedHashMap<>();
        for (String key : statKeys) {
            stats.put(key, config.getLong("stats." + key, 0));
        }
        return stats;
    }

    public void setAllStats(UUID playerId, Map<String, Long> stats) {
        var config = loadPlayerConfig(playerId);
        for (var entry : stats.entrySet()) {
            config.set("stats." + entry.getKey(), entry.getValue());
        }
        savePlayerConfig(playerId, config);
    }

    // --- Achievements ---

    /** Returns lowercase achievement keys. Normalizes legacy uppercase enum names to lowercase. */
    public Set<String> getUnlockedAchievements(UUID playerId) {
        var config = loadPlayerConfig(playerId);
        List<String> names = config.getStringList("achievements");
        Set<String> unlocked = new HashSet<>();
        for (String name : names) {
            unlocked.add(name.toLowerCase(java.util.Locale.ROOT));
        }
        return unlocked;
    }

    public void addUnlockedAchievement(UUID playerId, String achievementKey) {
        var config = loadPlayerConfig(playerId);
        List<String> names = new ArrayList<>(config.getStringList("achievements"));
        if (!names.contains(achievementKey)) {
            names.add(achievementKey);
            config.set("achievements", names);
            savePlayerConfig(playerId, config);
        }
    }

    // --- Punishments ---

    public void addPunishment(UUID playerId, PunishmentRecord record) {
        var config = loadPlayerConfig(playerId);
        List<Map<?, ?>> existing = config.getMapList("punishments");
        List<Map<String, Object>> punishments = new ArrayList<>();
        for (Map<?, ?> raw : existing) {
            Map<String, Object> entry = new LinkedHashMap<>();
            raw.forEach((k, v) -> entry.put(String.valueOf(k), v));
            punishments.add(entry);
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", record.typeKey());
        entry.put("severity", record.severity());
        entry.put("reason", record.reason());
        entry.put("moderator", record.moderatorId().toString());
        entry.put("moderator-name", record.moderatorName());
        entry.put("target-name", record.targetName());
        entry.put("duration", record.durationMillis());
        entry.put("issued-at", record.issuedAt());
        entry.put("expires-at", record.expiresAt());
        entry.put("active", record.active());
        punishments.add(entry);

        config.set("punishments", punishments);
        savePlayerConfig(playerId, config);
    }

    public List<PunishmentRecord> getPunishments(UUID playerId) {
        var config = loadPlayerConfig(playerId);
        List<Map<?, ?>> rawList = config.getMapList("punishments");
        if (rawList.isEmpty()) return List.of();

        List<PunishmentRecord> records = new ArrayList<>();
        for (Map<?, ?> raw : rawList) {
            String typeKey = String.valueOf(raw.get("type"));
            int severity = ((Number) raw.get("severity")).intValue();
            String reason = String.valueOf(raw.get("reason"));
            UUID moderator = UUID.fromString(String.valueOf(raw.get("moderator")));
            String moderatorName = String.valueOf(raw.get("moderator-name"));
            String targetName = String.valueOf(raw.get("target-name"));
            long duration = ((Number) raw.get("duration")).longValue();
            long issuedAt = ((Number) raw.get("issued-at")).longValue();
            long expiresAt = ((Number) raw.get("expires-at")).longValue();
            Object activeRaw = raw.get("active");
            boolean active = activeRaw instanceof Boolean
                    ? (Boolean) activeRaw
                    : Boolean.parseBoolean(String.valueOf(activeRaw));

            records.add(new PunishmentRecord(
                    playerId, targetName, moderator, moderatorName,
                    typeKey, severity, duration, reason,
                    issuedAt, expiresAt, active
            ));
        }
        return records;
    }

    public void setMutedUntil(UUID playerId, long expiresAt) {
        var config = loadPlayerConfig(playerId);
        config.set("muted", true);
        config.set("mute-expires", expiresAt);
        savePlayerConfig(playerId, config);
    }

    public long getMuteExpiry(UUID playerId) {
        return loadPlayerConfig(playerId).getLong("mute-expires", 0);
    }

    // --- Voting ---

    public int getVoteCount(UUID playerId) {
        return loadPlayerConfig(playerId).getInt("vote-count", 0);
    }

    public void setVoteCount(UUID playerId, int count) {
        var config = loadPlayerConfig(playerId);
        config.set("vote-count", count);
        savePlayerConfig(playerId, config);
    }

    public int getPendingVoteRewards(UUID playerId) {
        return loadPlayerConfig(playerId).getInt("pending-vote-rewards", 0);
    }

    public void setPendingVoteRewards(UUID playerId, int count) {
        var config = loadPlayerConfig(playerId);
        config.set("pending-vote-rewards", count);
        savePlayerConfig(playerId, config);
    }

    // --- Rank ---

    public void setRank(UUID playerId, String rankName) {
        var config = loadPlayerConfig(playerId);
        config.set("rank", rankName);
        savePlayerConfig(playerId, config);
    }

    public String getRank(UUID playerId) {
        return loadPlayerConfig(playerId).getString("rank");
    }

    // --- Internal ---

    private File playerFile(UUID playerId) {
        return new File(playerDataDir, playerId + ".yml");
    }

    private YamlConfiguration loadPlayerConfig(UUID playerId) {
        return YamlConfiguration.loadConfiguration(playerFile(playerId));
    }

    private void savePlayerConfig(UUID playerId, YamlConfiguration config) {
        saveConfig(config, playerFile(playerId));
    }

    private void saveConfig(YamlConfiguration config, File file) {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + file.getName(), e);
        }
    }
}
