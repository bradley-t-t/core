package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.rank.RankLevel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;

/**
 * Periodically syncs player stats (including bots) to the Supabase
 * {@code player_stats} table so the website can display leaderboards
 * and player profiles. Runs every 2 minutes.
 */
public final class StatsSyncService implements Service {

    private static final long SYNC_INTERVAL_TICKS = 20L * 60 * 2; // 2 minutes
    private static final long STARTUP_DELAY_TICKS = 200L; // 10 seconds

    private final CorePlugin plugin;
    private final HttpClient httpClient;
    private BukkitTask syncTask;

    public StatsSyncService(CorePlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void enable() {
        if (!isConfigured()) {
            plugin.getLogger().warning("Stats sync disabled — missing supabase-url or supabase-anon-key in config.yml");
            return;
        }

        // Bulk sync offline players first, then sync online players after a delay
        // so online status isn't overwritten by the slower bulk sync
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::syncAllPlayerData, STARTUP_DELAY_TICKS);
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::syncAll, STARTUP_DELAY_TICKS + 600L); // 30s later

        syncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::syncAll, SYNC_INTERVAL_TICKS, SYNC_INTERVAL_TICKS);

        plugin.getLogger().info("Stats sync enabled (2-minute interval).");
    }

    @Override
    public void disable() {
        if (syncTask != null) syncTask.cancel();
        // Final sync on shutdown — mark everyone offline
        markAllOffline();
    }

    private void syncAll() {
        // Mark all bots offline first, then re-mark currently online ones
        markBotsOffline();
        syncRealPlayers();
        syncBotPlayers();
    }

    /**
     * Sync a player's final stats on quit, marking them offline.
     * Called from PlayerListener on quit before stats are unloaded.
     */
    public void syncPlayerQuit(Player player) {
        if (!isConfigured()) return;

        UUID playerId = player.getUniqueId();
        String username = player.getName();

        PlayerStatsService statsService = plugin.services().get(PlayerStatsService.class);
        RankService rankService = plugin.services().get(RankService.class);
        if (statsService == null || rankService == null) return;

        Map<String, Long> stats = statsService.getAllStats(playerId);
        RankLevel rank = rankService.getLevel(playerId);
        long firstJoin = statsService.getFirstJoin(playerId);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                upsertPlayer(username, playerId.toString(), rank.name().toLowerCase(),
                        stats.getOrDefault("kills", 0L),
                        stats.getOrDefault("deaths", 0L),
                        stats.getOrDefault("blocks-broken", 0L),
                        stats.getOrDefault("blocks-placed", 0L),
                        stats.getOrDefault("mobs-killed", 0L),
                        stats.getOrDefault("fish-caught", 0L),
                        stats.getOrDefault("play-time", 0L),
                        firstJoin > 0 ? Instant.ofEpochMilli(firstJoin).toString() : null,
                        false, false));
    }

    /**
     * Bulk sync all offline players from playerdata files on startup.
     * Ensures every player who has ever joined appears in the stats table.
     */
    private void syncAllPlayerData() {
        File playerDataDir = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataDir.exists()) return;

        File[] files = playerDataDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) return;

        PlayerStatsService statsService = plugin.services().get(PlayerStatsService.class);
        RankService rankService = plugin.services().get(RankService.class);
        if (statsService == null || rankService == null) return;

        Set<String> onlineNames = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlineNames.add(player.getUniqueId().toString());
        }

        int synced = 0;
        for (File file : files) {
            String uuidStr = file.getName().replace(".yml", "");
            UUID playerId;
            try {
                playerId = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }

            // Skip online players — they'll be synced by the regular cycle
            if (onlineNames.contains(uuidStr)) continue;

            var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            String username = Bukkit.getOfflinePlayer(playerId).getName();
            if (username == null) continue;

            String rankStr = config.getString("rank", "member");
            RankLevel rank = com.core.plugin.modules.rank.RankLevel.fromString(rankStr);
            if (rank == null) rank = RankLevel.MEMBER;

            long kills = config.getLong("stats.kills", 0);
            long deaths = config.getLong("stats.deaths", 0);
            long blocksBroken = config.getLong("stats.blocks-broken", 0);
            long blocksPlaced = config.getLong("stats.blocks-placed", 0);
            long mobsKilled = config.getLong("stats.mobs-killed", 0);
            long fishCaught = config.getLong("stats.fish-caught", 0);
            long playTime = config.getLong("stats.play-time", 0);
            long firstJoin = config.getLong("first-join", 0);

            upsertPlayer(username, uuidStr, rank.name().toLowerCase(),
                    kills, deaths, blocksBroken, blocksPlaced, mobsKilled, fishCaught, playTime,
                    firstJoin > 0 ? Instant.ofEpochMilli(firstJoin).toString() : null,
                    false, false);
            synced++;
        }

        plugin.getLogger().info("[StatsSync] Bulk synced " + synced + " offline players to Supabase.");
    }

    private void syncRealPlayers() {
        PlayerStatsService statsService = plugin.services().get(PlayerStatsService.class);
        RankService rankService = plugin.services().get(RankService.class);
        if (statsService == null || rankService == null) return;

        Set<String> onlineNames = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            String username = player.getName();
            onlineNames.add(username);

            Map<String, Long> stats = statsService.getAllStats(playerId);
            RankLevel rank = rankService.getLevel(playerId);
            long firstJoin = statsService.getFirstJoin(playerId);

            upsertPlayer(
                    username,
                    playerId.toString(),
                    rank.name().toLowerCase(),
                    stats.getOrDefault("kills", 0L),
                    stats.getOrDefault("deaths", 0L),
                    stats.getOrDefault("blocks-broken", 0L),
                    stats.getOrDefault("blocks-placed", 0L),
                    stats.getOrDefault("mobs-killed", 0L),
                    stats.getOrDefault("fish-caught", 0L),
                    stats.getOrDefault("play-time", 0L),
                    firstJoin > 0 ? Instant.ofEpochMilli(firstJoin).toString() : null,
                    true,
                    false
            );
        }
    }

    private void syncBotPlayers() {
        BotService botService = plugin.services().get(BotService.class);
        if (botService == null) return;

        Set<String> onlineBots = botService.getOnlineFakes();
        if (onlineBots.isEmpty()) return;

        for (String botName : onlineBots) {
            // Deterministic join date derived from name hash — 1 to 365 days ago
            long daysAgo = Math.abs(botName.hashCode() % 365) + 1;
            String botFirstJoin = Instant.now().minus(Duration.ofDays(daysAgo)).toString();

            upsertPlayer(
                    botName,
                    null,
                    "member",
                    randomBotStat(50, 500),
                    randomBotStat(20, 200),
                    randomBotStat(5000, 80000),
                    randomBotStat(3000, 50000),
                    randomBotStat(100, 3000),
                    randomBotStat(10, 300),
                    randomBotStat(60, 6000),
                    botFirstJoin,
                    true,
                    true
            );
        }
    }

    private long randomBotStat(long min, long max) {
        return min + (long) (Math.random() * (max - min));
    }

    private void upsertPlayer(String username, String uuid, String rank,
                               long kills, long deaths, long blocksBroken,
                               long blocksPlaced, long mobsKilled, long fishCaught,
                               long playTimeMinutes, String firstJoin,
                               boolean isOnline, boolean isBot) {

        String supabaseUrl = plugin.getConfig().getString("supabase-url", "");
        String anonKey = plugin.getConfig().getString("supabase-anon-key", "");

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"username\":\"").append(escapeJson(username)).append("\",");
        if (uuid != null) json.append("\"uuid\":\"").append(uuid).append("\",");
        json.append("\"rank\":\"").append(rank).append("\",");
        json.append("\"kills\":").append(kills).append(",");
        json.append("\"deaths\":").append(deaths).append(",");
        json.append("\"blocks_broken\":").append(blocksBroken).append(",");
        json.append("\"blocks_placed\":").append(blocksPlaced).append(",");
        json.append("\"mobs_killed\":").append(mobsKilled).append(",");
        json.append("\"fish_caught\":").append(fishCaught).append(",");
        json.append("\"play_time_minutes\":").append(playTimeMinutes).append(",");
        if (firstJoin != null) json.append("\"first_join\":\"").append(firstJoin).append("\",");
        json.append("\"last_seen\":\"").append(Instant.now().toString()).append("\",");
        json.append("\"is_online\":").append(isOnline).append(",");
        json.append("\"is_bot\":").append(isBot).append(",");
        json.append("\"updated_at\":\"").append(Instant.now().toString()).append("\"");
        json.append("}");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(supabaseUrl + "/rest/v1/player_stats?on_conflict=username"))
                .header("apikey", anonKey)
                .header("Authorization", "Bearer " + anonKey)
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .timeout(Duration.ofSeconds(5))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                plugin.getLogger().warning("[StatsSync] Failed to upsert " + username
                        + ": HTTP " + response.statusCode() + " " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            plugin.getLogger().log(Level.WARNING, "[StatsSync] Failed to sync " + username, e);
        }
    }

    private void markBotsOffline() {
        if (!isConfigured()) return;

        String supabaseUrl = plugin.getConfig().getString("supabase-url", "");
        String anonKey = plugin.getConfig().getString("supabase-anon-key", "");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(supabaseUrl + "/rest/v1/player_stats?is_bot=eq.true&is_online=eq.true"))
                .header("apikey", anonKey)
                .header("Authorization", "Bearer " + anonKey)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(
                        "{\"is_online\":false,\"updated_at\":\"" + Instant.now() + "\"}"))
                .timeout(Duration.ofSeconds(5))
                .build();

        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            plugin.getLogger().log(Level.WARNING, "[StatsSync] Failed to mark bots offline", e);
        }
    }

    private void markAllOffline() {
        if (!isConfigured()) return;

        String supabaseUrl = plugin.getConfig().getString("supabase-url", "");
        String anonKey = plugin.getConfig().getString("supabase-anon-key", "");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(supabaseUrl + "/rest/v1/player_stats?is_online=eq.true"))
                .header("apikey", anonKey)
                .header("Authorization", "Bearer " + anonKey)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(
                        "{\"is_online\":false,\"updated_at\":\"" + Instant.now() + "\"}"))
                .timeout(Duration.ofSeconds(5))
                .build();

        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            plugin.getLogger().log(Level.WARNING, "[StatsSync] Failed to mark players offline", e);
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isConfigured() {
        String url = plugin.getConfig().getString("supabase-url", "");
        String key = plugin.getConfig().getString("supabase-anon-key", "");
        return !url.isEmpty() && !key.isEmpty();
    }
}
