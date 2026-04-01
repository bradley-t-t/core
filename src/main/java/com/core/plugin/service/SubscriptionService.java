package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.rank.Rank;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syncs Diamond rank with the Supabase {@code diamond_subscriptions} table.
 * <p>
 * On player join and every 5 minutes, checks subscription status and
 * grants or revokes Diamond rank accordingly. Moderator+ ranks are never
 * touched — only Member ↔ Diamond transitions.
 * <p>
 * Maintains a persistent local queue ({@code pending-diamond.yml}) so that
 * rank grants survive server restarts even if the player is offline when
 * the webhook fires. The queue is processed on startup and on every join.
 */
public final class SubscriptionService implements Service {

    private static final long SYNC_INTERVAL_TICKS = 20L * 60 * 5; // 5 minutes
    private static final long STARTUP_DELAY_TICKS = 100L; // 5 seconds after enable
    private static final Pattern STATUS_PATTERN = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"");

    private final CorePlugin plugin;
    private final HttpClient httpClient;
    private final File pendingFile;
    private final Set<String> pendingGrants = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingRevokes = ConcurrentHashMap.newKeySet();
    private BukkitTask syncTask;

    public SubscriptionService(CorePlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.pendingFile = new File(plugin.getDataFolder(), "pending-diamond.yml");
    }

    @Override
    public void enable() {
        loadPendingQueue();

        if (!isConfigured()) {
            plugin.getLogger().warning("Subscription sync disabled — missing supabase-url or supabase-anon-key in config.yml");
            return;
        }

        // Sync online players shortly after startup
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            refreshPendingFromSupabase();
            Bukkit.getScheduler().runTask(plugin, this::processPendingQueue);
            syncAllOnlinePlayers();
        }, STARTUP_DELAY_TICKS);

        // Periodic sync every 5 minutes
        syncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, () -> {
                    refreshPendingFromSupabase();
                    Bukkit.getScheduler().runTask(plugin, this::processPendingQueue);
                    syncAllOnlinePlayers();
                }, SYNC_INTERVAL_TICKS, SYNC_INTERVAL_TICKS);

        plugin.getLogger().info("Subscription sync enabled (5-minute interval).");
    }

    @Override
    public void disable() {
        if (syncTask != null) syncTask.cancel();
        savePendingQueue();
    }

    /**
     * Check a player's subscription status and update their rank.
     * Also processes any pending queue entries for this player.
     */
    public void checkAndSync(Player player) {
        if (!isConfigured()) {
            // Even without Supabase, process the local queue
            processPendingForPlayer(player);
            return;
        }

        String username = player.getName();
        UUID playerId = player.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean isActive = fetchSubscriptionActive(username);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                applySubscriptionState(player, isActive);
                // Clear from pending since we've resolved it live
                pendingGrants.remove(username.toLowerCase());
                pendingRevokes.remove(username.toLowerCase());
                savePendingQueue();
            });
        });
    }

    // --- Pending Queue (persistent across restarts) ---

    /**
     * Queue a Diamond grant for a player who may be offline.
     * Persists to disk immediately.
     */
    public void queueGrant(String username) {
        String key = username.toLowerCase();
        pendingGrants.add(key);
        pendingRevokes.remove(key);
        savePendingQueue();
        plugin.getLogger().info("[Diamond] Queued Diamond grant for " + username);
    }

    /**
     * Queue a Diamond revoke for a player who may be offline.
     * Persists to disk immediately.
     */
    public void queueRevoke(String username) {
        String key = username.toLowerCase();
        pendingRevokes.add(key);
        pendingGrants.remove(key);
        savePendingQueue();
        plugin.getLogger().info("[Diamond] Queued Diamond revoke for " + username);
    }

    /** Process pending grants/revokes for all online players. */
    private void processPendingQueue() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            processPendingForPlayer(player);
        }
    }

    private void processPendingForPlayer(Player player) {
        String key = player.getName().toLowerCase();
        RankService rankService = plugin.services().get(RankService.class);
        RankLevel currentRank = rankService.getLevel(player.getUniqueId());

        if (pendingGrants.contains(key) && currentRank == RankLevel.MEMBER) {
            rankService.setRank(player.getUniqueId(), RankLevel.DIAMOND);
            updateTabName(player, rankService);
            pendingGrants.remove(key);
            savePendingQueue();
            plugin.getLogger().info("[Diamond] Applied queued grant for " + player.getName());
        } else if (pendingRevokes.contains(key) && currentRank == RankLevel.DIAMOND) {
            rankService.setRank(player.getUniqueId(), RankLevel.MEMBER);
            updateTabName(player, rankService);
            pendingRevokes.remove(key);
            savePendingQueue();
            plugin.getLogger().info("[Diamond] Applied queued revoke for " + player.getName());
        } else {
            // Clean up stale entries
            pendingGrants.remove(key);
            pendingRevokes.remove(key);
        }
    }

    /**
     * Fetch all active subscriptions from Supabase and populate the pending queue
     * for any players whose local rank doesn't match.
     */
    private void refreshPendingFromSupabase() {
        String supabaseUrl = plugin.getConfig().getString("supabase-url", "");
        String anonKey = plugin.getConfig().getString("supabase-anon-key", "");

        String url = supabaseUrl + "/rest/v1/diamond_subscriptions"
                + "?select=minecraft_username,status";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", anonKey)
                .header("Authorization", "Bearer " + anonKey)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                plugin.getLogger().warning("[Diamond] Failed to refresh pending queue: HTTP " + response.statusCode());
                return;
            }

            String body = response.body();
            // Simple JSON array parsing — each entry has minecraft_username and status
            Pattern entryPattern = Pattern.compile(
                    "\"minecraft_username\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"status\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = entryPattern.matcher(body);

            while (matcher.find()) {
                String username = matcher.group(1).toLowerCase();
                String status = matcher.group(2);

                if ("active".equals(status)) {
                    pendingGrants.add(username);
                    pendingRevokes.remove(username);
                } else {
                    pendingRevokes.add(username);
                    pendingGrants.remove(username);
                }
            }

            savePendingQueue();
        } catch (IOException | InterruptedException e) {
            plugin.getLogger().log(Level.WARNING, "[Diamond] Failed to refresh pending queue from Supabase", e);
        }
    }

    // --- Online Player Sync ---

    private void syncAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String username = player.getName();
            boolean isActive = fetchSubscriptionActive(username);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                applySubscriptionState(player, isActive);
            });
        }
    }

    private void applySubscriptionState(Player player, boolean isActive) {
        RankService rankService = plugin.services().get(RankService.class);
        RankLevel currentRank = rankService.getLevel(player.getUniqueId());
        String username = player.getName();

        if (isActive && currentRank == RankLevel.MEMBER) {
            rankService.setRank(player.getUniqueId(), RankLevel.DIAMOND);
            updateTabName(player, rankService);
            plugin.getLogger().info("[Diamond] Granted Diamond to " + username + " (active subscription)");
        } else if (!isActive && currentRank == RankLevel.DIAMOND) {
            rankService.setRank(player.getUniqueId(), RankLevel.MEMBER);
            updateTabName(player, rankService);
            plugin.getLogger().info("[Diamond] Revoked Diamond from " + username + " (subscription inactive)");
        }
    }

    // --- Supabase API ---

    private boolean fetchSubscriptionActive(String username) {
        String supabaseUrl = plugin.getConfig().getString("supabase-url", "");
        String anonKey = plugin.getConfig().getString("supabase-anon-key", "");

        String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String url = supabaseUrl + "/rest/v1/diamond_subscriptions"
                + "?minecraft_username=eq." + encodedUsername
                + "&status=eq.active"
                + "&select=status"
                + "&limit=1";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", anonKey)
                .header("Authorization", "Bearer " + anonKey)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("[Diamond] Supabase API returned " + response.statusCode()
                        + " for " + username);
                return false;
            }

            String body = response.body();
            Matcher matcher = STATUS_PATTERN.matcher(body);
            return matcher.find() && "active".equals(matcher.group(1));
        } catch (IOException | InterruptedException e) {
            plugin.getLogger().log(Level.WARNING, "[Diamond] Failed to check subscription for " + username, e);
            return false;
        }
    }

    // --- Persistence ---

    private void loadPendingQueue() {
        if (!pendingFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(pendingFile);
        pendingGrants.addAll(config.getStringList("grants"));
        pendingRevokes.addAll(config.getStringList("revokes"));

        if (!pendingGrants.isEmpty() || !pendingRevokes.isEmpty()) {
            plugin.getLogger().info("[Diamond] Loaded pending queue: "
                    + pendingGrants.size() + " grants, " + pendingRevokes.size() + " revokes");
        }
    }

    private void savePendingQueue() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("grants", new ArrayList<>(pendingGrants));
        config.set("revokes", new ArrayList<>(pendingRevokes));
        try {
            config.save(pendingFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save pending-diamond.yml", e);
        }
    }

    private void updateTabName(Player player, RankService rankService) {
        Rank rank = rankService.getRank(player.getUniqueId());
        if (rank != null) {
            player.setPlayerListName(MessageUtil.colorize(rank.displayPrefix() + " " + player.getName()));
        }
    }

    private boolean isConfigured() {
        String url = plugin.getConfig().getString("supabase-url", "");
        String key = plugin.getConfig().getString("supabase-anon-key", "");
        return !url.isEmpty() && !key.isEmpty();
    }
}
