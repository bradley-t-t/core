package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;

/**
 * Logs the current player count (real + bots) to the Supabase
 * {@code player_count_history} table every 5 minutes so the admin
 * panel can display historical player activity charts.
 */
public final class PlayerCountService implements Service {

    private static final long LOG_INTERVAL_TICKS = 20L * 60; // 1 minute
    private static final long STARTUP_DELAY_TICKS = 400L; // 20 seconds

    private final CorePlugin plugin;
    private final HttpClient httpClient;
    private BukkitTask logTask;

    public PlayerCountService(CorePlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void enable() {
        if (!isConfigured()) {
            plugin.getLogger().warning("Player count logging disabled — missing Supabase config");
            return;
        }

        logTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::logCount, STARTUP_DELAY_TICKS, LOG_INTERVAL_TICKS);

        plugin.getLogger().info("Player count logging enabled (1-minute interval).");
    }

    @Override
    public void disable() {
        if (logTask != null) logTask.cancel();
    }

    private void logCount() {
        int realCount = Bukkit.getOnlinePlayers().size();

        BotService botService = plugin.services().get(BotService.class);
        int botCount = botService != null ? botService.getOnlineFakes().size() : 0;
        int totalOnline = realCount + botCount;

        String supabaseUrl = plugin.getConfig().getString("supabase-url", "");
        String anonKey = plugin.getConfig().getString("supabase-anon-key", "");

        String json = String.format(
                "{\"real_count\":%d,\"bot_count\":%d,\"total_count\":%d,\"recorded_at\":\"%s\"}",
                realCount, botCount, totalOnline, Instant.now().toString()
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(supabaseUrl + "/rest/v1/player_count_history"))
                .header("apikey", anonKey)
                .header("Authorization", "Bearer " + anonKey)
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(5))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                plugin.getLogger().warning("[PlayerCount] Failed to log count: HTTP "
                        + response.statusCode() + " " + response.body());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[PlayerCount] Failed to log count", e);
        }
    }

    private boolean isConfigured() {
        String url = plugin.getConfig().getString("supabase-url", "");
        String key = plugin.getConfig().getString("supabase-anon-key", "");
        return !url.isEmpty() && !key.isEmpty();
    }
}
