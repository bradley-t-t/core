package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.bots.*;
import com.core.plugin.listener.BotTabListener;
import com.core.plugin.util.BotUtil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Thin orchestrator for the bot system. Owns the shared {@code currentlyOnline}
 * set and delegates all behavior to focused managers:
 * <ul>
 *   <li>{@link BotLifecycleManager} — join/leave cycling and session scheduling</li>
 *   <li>{@link BotChatManager} — chat responses, welcome clusters, death simulation</li>
 *   <li>{@link BotVoteManager} — vote simulation</li>
 *   <li>{@link BotBroadcaster} — message formatting and tab list</li>
 *   <li>{@link BotDataSeeder} — fake player data for /user and /seen</li>
 * </ul>
 */
public final class BotService implements Service {

    private final CorePlugin plugin;
    private final BotPool pool;
    private final BotTraits traits;
    private final Set<String> currentlyOnline = ConcurrentHashMap.newKeySet();

    private BotChatEngine chatEngine;
    private BotBroadcaster broadcaster;
    private BotDataSeeder seeder;
    private BotLifecycleManager lifecycle;
    private BotChatManager chatManager;
    private BotVoteManager voteManager;
    private BotStatGrowthTask growthTask;

    private int joinLeaveIntervalSeconds;
    private int voteLinkCount;

    public BotService(CorePlugin plugin) {
        this.plugin = plugin;
        this.pool = new BotPool(plugin);
        this.traits = new BotTraits(plugin);
    }

    // --- Service lifecycle ---

    @Override
    public void enable() {
        loadConfig();

        BotNameValidator validator = new BotNameValidator(plugin);
        pool.setValidator(validator);
        BotUtil.setValidator(validator);

        pool.load(lifecycle != null ? lifecycle.getMaxOnline() : 30);
        chatEngine = new BotChatEngine(plugin);

        if (!chatEngine.isAvailable()) {
            plugin.getLogger().warning(
                    "Fake players: No Grok API key set -- fake player chat is disabled. "
                            + "Set fake-players.grok-api-key in config.yml");
        }

        // Initialize managers
        broadcaster = new BotBroadcaster(plugin, traits);
        seeder = new BotDataSeeder(plugin, traits);
        lifecycle = new BotLifecycleManager(plugin, pool, traits, broadcaster, seeder, chatEngine, currentlyOnline);
        chatManager = new BotChatManager(plugin, chatEngine, broadcaster, traits, pool, currentlyOnline);
        voteManager = new BotVoteManager(plugin, currentlyOnline, broadcaster);
        growthTask = new BotStatGrowthTask(plugin, traits, currentlyOnline);

        int minOnline = plugin.getConfig().getInt("fake-players.min-online", 10);
        int maxOnline = plugin.getConfig().getInt("fake-players.max-online", 30);
        int minSession = plugin.getConfig().getInt("fake-players.min-session-minutes", 15);
        int maxSession = plugin.getConfig().getInt("fake-players.max-session-minutes", 180);
        lifecycle.configure(minOnline, maxOnline, minSession, maxSession);

        pool.load(maxOnline);
        traits.assignAll(pool.getNames(), chatEngine);

        if (!pool.isEnabled()) {
            plugin.getLogger().info("Fake player system is disabled.");
            return;
        }
        if (pool.isEmpty()) {
            plugin.getLogger().warning("Fake player pool is empty -- nothing to simulate.");
            return;
        }

        boolean quickRestart = isQuickRestart();
        long cumulativeDelay = lifecycle.seedInitialPlayers(quickRestart);

        if (quickRestart) {
            plugin.getLogger().info("Quick restart detected -- fake players rejoining fast.");
        }

        lifecycle.startCycle(cumulativeDelay, joinLeaveIntervalSeconds);
        lifecycle.startPoolRotation();
        chatManager.startActivityScheduler();
        voteManager.start(voteLinkCount);
        growthTask.start();

        // Validate bot names in background
        validator.validateAsync(pool.getNames(), invalidNames -> {
            if (!invalidNames.isEmpty()) {
                List<String> newNames = pool.replaceInvalidNames(currentlyOnline);
                for (String name : newNames) {
                    traits.assignSingle(name, chatEngine);
                }
                if (!newNames.isEmpty()) {
                    validator.validateAsync(newNames, ignored -> {});
                }
            }
        });

        plugin.getLogger().info("Fake player system active: pool of " + pool.size() + ".");
    }

    @Override
    public void disable() {
        if (lifecycle != null) lifecycle.stopAll();
        if (chatManager != null) chatManager.stopActivityScheduler();
        if (voteManager != null) voteManager.stop();
        if (growthTask != null) growthTask.stop();
        currentlyOnline.clear();
        pool.saveShutdownTime();
    }

    // --- Public API ---

    public Set<String> getOnlineFakes() {
        return Set.copyOf(currentlyOnline);
    }

    public int getTotalOnlineCount() {
        return Bukkit.getOnlinePlayers().size() + currentlyOnline.size();
    }

    public List<String> getPlayerPool() {
        return pool.getNames();
    }

    /**
     * Delete all bot player data files from disk and clear bot rows from Supabase.
     * Bots will regenerate fresh data (with organic backfill) on their next join.
     *
     * @param callback called on the main thread with the count of files deleted
     *                 and the Supabase row count (or -1 on failure)
     */
    public void resetBotData(java.util.function.BiConsumer<Integer, Integer> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            java.io.File playerDataDir = new java.io.File(plugin.getDataFolder(), "playerdata");
            int filesDeleted = 0;

            // Build a set of all bot UUIDs from the pool
            Set<String> botUuidStrings = new java.util.HashSet<>();
            for (String name : pool.getNames()) {
                botUuidStrings.add(BotUtil.fakeUuid(name).toString());
            }

            // Delete matching YAML files
            if (playerDataDir.exists()) {
                java.io.File[] files = playerDataDir.listFiles((dir, fn) -> fn.endsWith(".yml"));
                if (files != null) {
                    for (java.io.File file : files) {
                        String uuidStr = file.getName().replace(".yml", "");
                        if (botUuidStrings.contains(uuidStr)) {
                            if (file.delete()) filesDeleted++;
                        }
                    }
                }
            }

            // Clear from Supabase
            StatsSyncService syncService = plugin.services().get(StatsSyncService.class);
            int supabaseDeleted = syncService != null ? syncService.deleteAllBotStats() : -1;

            int finalFilesDeleted = filesDeleted;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalFilesDeleted, supabaseDeleted));
        });
    }

    public void addToPool(String name) {
        pool.add(name);
    }

    public void removeFromPool(String name) {
        boolean wasOnline = currentlyOnline.remove(name);
        pool.remove(name);
        if (wasOnline && broadcaster != null) {
            broadcaster.broadcastLeave(name);
        }
    }

    public boolean isFake(String name) {
        return currentlyOnline.contains(name) || pool.getNames().contains(name);
    }

    public boolean isOnlineFake(String name) {
        return currentlyOnline.contains(name);
    }

    public String getDisplayName(String name) {
        return traits.getDisplayName(name);
    }

    public boolean isEnabled() {
        return pool.isEnabled();
    }

    public Set<String> getBotAccusers() {
        return chatManager != null ? chatManager.getAccusers() : Set.of();
    }

    public int getMinOnline() {
        return lifecycle != null ? lifecycle.getMinOnline() : 0;
    }

    public int getMaxOnline() {
        return lifecycle != null ? lifecycle.getMaxOnline() : 0;
    }

    public void setMinOnline(int min) {
        if (lifecycle != null) lifecycle.setMinOnline(min);
        plugin.getConfig().set("fake-players.min-online", min);
        plugin.saveConfig();
    }

    public void setMaxOnline(int max) {
        if (lifecycle != null) lifecycle.setMaxOnline(max);
        plugin.getConfig().set("fake-players.max-online", max);
        plugin.saveConfig();
        pool.load(max);
        traits.assignAll(pool.getNames(), chatEngine);
    }

    // --- Event hooks ---

    public void onRealPlayerDeath(String victimName) {
        if (!pool.isEnabled() || chatManager == null) return;
        chatManager.onRealPlayerDeath(victimName, resolveRankName(victimName));
    }

    public void onRealPlayerJoin(String playerName, boolean firstJoin) {
        if (!pool.isEnabled() || chatManager == null) return;
        chatManager.onRealPlayerJoin(playerName, firstJoin, resolveRankName(playerName));
    }

    public void onRealPlayerChat(String playerName, String message) {
        if (!pool.isEnabled() || chatManager == null) return;
        chatManager.onRealPlayerChat(playerName, resolveRankName(playerName), message);
    }

    public void generatePrivateReply(String botName, String senderName, String incomingMessage,
                                      Consumer<String> callback) {
        if (chatManager != null) {
            chatManager.generatePrivateReply(botName, senderName, incomingMessage, callback);
        }
    }

    // --- Punishment handling ---

    public void onBotPunished(String botName, String typeKey, long durationMillis) {
        switch (typeKey) {
            case "ban" -> {
                if (currentlyOnline.remove(botName) && broadcaster != null) {
                    broadcaster.broadcastLeave(botName);
                }
                pool.remove(botName);
                java.util.Date expiry = durationMillis == -1 ? null
                        : new java.util.Date(System.currentTimeMillis() + durationMillis);
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                        .addBan(botName, "Banned", expiry, "Server");
            }
            case "mute" -> {
                if (broadcaster != null) broadcaster.mute(botName, durationMillis);
            }
            case "warn" -> {
                if (currentlyOnline.remove(botName) && broadcaster != null) {
                    broadcaster.broadcastLeave(botName);
                    long rejoinTicks = 6000 + new Random().nextInt(12000);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (pool.isEnabled() && !currentlyOnline.contains(botName)
                                && pool.getNames().contains(botName)) {
                            currentlyOnline.add(botName);
                            broadcaster.broadcastJoin(botName, seeder, this);
                        }
                    }, rejoinTicks);
                }
            }
        }
    }

    // --- Activate/Deactivate ---

    public void activate() {
        if (pool.isEnabled()) return;
        pool.setEnabled(true);
        pool.load(lifecycle != null ? lifecycle.getMaxOnline() : 30);
        traits.assignAll(pool.getNames(), chatEngine);

        if (lifecycle != null) {
            lifecycle.activate(joinLeaveIntervalSeconds);
            lifecycle.startPoolRotation();
        }
        if (chatManager != null) chatManager.startActivityScheduler();
        if (voteManager != null) voteManager.start(voteLinkCount);
        if (growthTask != null) growthTask.start();
    }

    public void deactivate() {
        if (!pool.isEnabled()) return;
        pool.setEnabled(false);

        if (lifecycle != null) lifecycle.deactivate();
        if (chatManager != null) chatManager.stopActivityScheduler();
        if (voteManager != null) voteManager.stop();
        if (growthTask != null) growthTask.stop();
    }

    // --- Internal ---

    private String resolveRankName(String playerName) {
        var player = Bukkit.getPlayerExact(playerName);
        if (player == null) return "player";
        var rankService = plugin.services().get(RankService.class);
        if (rankService == null) return "player";
        return rankService.getLevel(player.getUniqueId()).name().toLowerCase();
    }

    private void loadConfig() {
        joinLeaveIntervalSeconds = plugin.getConfig().getInt("fake-players.cycle-interval-seconds", 60);
        List<?> voteLinks = plugin.getConfig().getList("voting.vote-links");
        voteLinkCount = (voteLinks != null && !voteLinks.isEmpty())
                ? voteLinks.size()
                : plugin.getConfig().getInt("fake-players.vote-link-count", 1);
    }

    private boolean isQuickRestart() {
        long lastShutdown = pool.getLastShutdownTime();
        return lastShutdown > 0
                && (System.currentTimeMillis() - lastShutdown) < BotConfig.QUICK_RESTART_THRESHOLD_MS;
    }
}
