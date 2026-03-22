package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.bots.BotChatEngine;
import com.core.plugin.modules.bots.BotPool;
import com.core.plugin.listener.BotTabListener;
import com.core.plugin.modules.bots.BotTraits;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the bot system: cycling players online/offline,
 * broadcasting join/leave/chat/death messages, and delegating to AI chat.
 * Pool management lives in {@link BotPool}, trait assignment in
 * {@link BotTraits}, and AI communication in {@link BotChatEngine}.
 */
public final class BotService implements Service {

    private static final long TICKS_PER_SECOND = 20L;
    private static final long QUICK_RESTART_THRESHOLD_MS = 600_000;

    private static final String[][] DEATH_SCENARIOS = {
            {"death.fall", "false"},
            {"death.drowning", "false"},
            {"death.fire", "false"},
            {"death.lava", "false"},
            {"death.starvation", "false"},
            {"death.explosion", "false"},
            {"death.freeze", "false"},
            {"death.mob", "true"},
    };

    private static final String[] MOB_NAMES = {
            "Zombie", "Skeleton", "Creeper", "Spider", "Enderman",
            "Witch", "Drowned", "Pillager", "Vindicator", "Warden"
    };

    private final CorePlugin plugin;
    private final BotPool pool;
    private final BotTraits traits;
    private final Set<String> currentlyOnline = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();

    private BotChatEngine chatEngine;
    private int minOnline;
    private int maxOnline;
    private int minSessionMinutes;
    private int maxSessionMinutes;
    private int joinLeaveIntervalSeconds;
    private int schedulerTaskId = -1;
    private int activityTaskId = -1;
    private int rotationTaskId = -1;

    public BotService(CorePlugin plugin) {
        this.plugin = plugin;
        this.pool = new BotPool(plugin);
        this.traits = new BotTraits(plugin);
    }

    // --- Service lifecycle ---

    @Override
    public void enable() {
        loadConfig();
        pool.load(maxOnline);
        chatEngine = new BotChatEngine(plugin);

        if (!chatEngine.isAvailable()) {
            plugin.getLogger().warning(
                    "Fake players: No Grok API key set -- fake player chat is disabled. "
                            + "Set fake-players.grok-api-key in config.yml");
        }

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
        long cumulativeDelay = seedInitialPlayers(quickRestart);

        if (quickRestart) {
            plugin.getLogger().info("Quick restart detected -- fake players rejoining fast.");
        }

        long intervalTicks = joinLeaveIntervalSeconds * TICKS_PER_SECOND;
        schedulerTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::cycle,
                cumulativeDelay + intervalTicks, intervalTicks).getTaskId();

        startActivityScheduler();
        startPoolRotation();

        plugin.getLogger().info("Fake player system active: pool of " + pool.size() + ".");
    }

    @Override
    public void disable() {
        cancelTask(schedulerTaskId);
        cancelTask(activityTaskId);
        cancelTask(rotationTaskId);
        currentlyOnline.clear();
        pool.saveShutdownTime();
    }

    // --- Public API (unchanged contract) ---

    public Set<String> getOnlineFakes() {
        return Set.copyOf(currentlyOnline);
    }

    public int getTotalOnlineCount() {
        return Bukkit.getOnlinePlayers().size() + currentlyOnline.size();
    }

    public List<String> getPlayerPool() {
        return pool.getNames();
    }

    public void addToPool(String name) {
        pool.add(name);
    }

    public void removeFromPool(String name) {
        pool.remove(name);
        currentlyOnline.remove(name);
    }

    public boolean isFake(String name) {
        return currentlyOnline.contains(name);
    }

    public String getDisplayName(String name) {
        return traits.getDisplayName(name);
    }

    public boolean isEnabled() {
        return pool.isEnabled();
    }

    public void activate() {
        if (pool.isEnabled()) return;
        pool.setEnabled(true);
        pool.load(maxOnline);
        traits.assignAll(pool.getNames(), chatEngine);

        if (pool.isEmpty()) return;

        int initialCount = randomBetween(minOnline, Math.min(maxOnline, pool.size()));
        List<String> shuffled = new ArrayList<>(pool.getNames());
        Collections.shuffle(shuffled, random);

        for (int i = 0; i < initialCount && i < shuffled.size(); i++) {
            String name = shuffled.get(i);
            long delayTicks = (long) (i + 1) * randomBetween(100, 300);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!pool.isEnabled()) return;
                currentlyOnline.add(name);
                broadcastJoin(name);
            }, delayTicks);
        }

        long intervalTicks = joinLeaveIntervalSeconds * TICKS_PER_SECOND;
        schedulerTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::cycle,
                intervalTicks, intervalTicks).getTaskId();

        startActivityScheduler();
        startPoolRotation();
    }

    public void deactivate() {
        if (!pool.isEnabled()) return;
        pool.setEnabled(false);

        schedulerTaskId = cancelTask(schedulerTaskId);
        activityTaskId = cancelTask(activityTaskId);
        rotationTaskId = cancelTask(rotationTaskId);

        List<String> toRemove = new ArrayList<>(currentlyOnline);
        Collections.shuffle(toRemove, random);

        for (int i = 0; i < toRemove.size(); i++) {
            String name = toRemove.get(i);
            long delayTicks = (long) (i + 1) * randomBetween(60, 160);
            boolean isLast = (i == toRemove.size() - 1);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (currentlyOnline.remove(name)) broadcastLeave(name);
                if (isLast) {
                    BotTabListener tab = getTabListener();
                    if (tab != null) { tab.removeAllFakes(); tab.clearAll(); }
                }
            }, delayTicks);
        }
    }

    // --- Event hooks (called by external listeners) ---

    public void onRealPlayerDeath(String victimName) {
        if (!pool.isEnabled() || currentlyOnline.isEmpty()) return;
        if (chatEngine != null) chatEngine.addContext("SERVER", null, victimName + " died");
        plugin.getLogger().info("[FakeChat] Death event: " + victimName);
        askAiAndSend(victimName + " (" + resolveRankName(victimName) + ") just died");
    }

    public void onRealPlayerJoin(String playerName) {
        if (!pool.isEnabled() || currentlyOnline.isEmpty()) return;
        plugin.getLogger().info("[FakeChat] Join event: " + playerName);
        askAiAndSend(playerName + " (" + resolveRankName(playerName) + ") just joined the server");
    }

    public void onRealPlayerChat(String playerName, String message) {
        if (!pool.isEnabled() || currentlyOnline.isEmpty()) return;
        if (chatEngine != null) chatEngine.addContext(playerName, resolveRankName(playerName), message);
        plugin.getLogger().info("[FakeChat] Chat event: " + playerName + " >> " + message);
        askAiAndSend(playerName + " (" + resolveRankName(playerName) + ") said: " + message);
    }

    private String resolveRankName(String playerName) {
        var player = org.bukkit.Bukkit.getPlayerExact(playerName);
        if (player == null) return "player";
        var rankService = plugin.services().get(com.core.plugin.service.RankService.class);
        if (rankService == null) return "player";
        return rankService.getLevel(player.getUniqueId()).name().toLowerCase();
    }

    // --- Cycle logic ---

    private void cycle() {
        if (pool.isEmpty() || random.nextInt(3) == 0) return;

        long inWindowCount = pool.getNames().stream()
                .filter(name -> !currentlyOnline.contains(name))
                .filter(traits::isInWindow)
                .count();

        int onlineCount = currentlyOnline.size();

        List<String> outOfWindow = currentlyOnline.stream()
                .filter(name -> !traits.isInWindow(name))
                .toList();

        if (!outOfWindow.isEmpty() && random.nextInt(100) < 40) {
            String leaving = outOfWindow.get(random.nextInt(outOfWindow.size()));
            currentlyOnline.remove(leaving);
            broadcastLeave(leaving);
            return;
        }

        boolean shouldAdd = decideShouldAdd(onlineCount, inWindowCount);
        if (shouldAdd) {
            joinRandomPlayer();
        } else {
            leaveRandomPlayer();
        }
    }

    private boolean decideShouldAdd(int onlineCount, long inWindowCount) {
        if (onlineCount <= minOnline) return true;
        if (onlineCount >= maxOnline || onlineCount >= pool.size()) return false;
        if (inWindowCount > 0) return random.nextInt(100) < 65;
        int midpoint = (minOnline + maxOnline) / 2;
        return onlineCount < midpoint ? random.nextInt(3) != 0 : random.nextInt(3) == 0;
    }

    private void joinRandomPlayer() {
        List<String> inWindow = pool.getNames().stream()
                .filter(name -> !currentlyOnline.contains(name))
                .filter(traits::isInWindow)
                .toList();

        List<String> allOffline = pool.getNames().stream()
                .filter(name -> !currentlyOnline.contains(name))
                .toList();

        if (allOffline.isEmpty()) return;

        String name = (!inWindow.isEmpty() && random.nextInt(100) < 80)
                ? inWindow.get(random.nextInt(inWindow.size()))
                : allOffline.get(random.nextInt(allOffline.size()));

        currentlyOnline.add(name);
        broadcastJoin(name);
        scheduleLeave(name);
    }

    private void scheduleLeave(String name) {
        int sessionMinutes = random.nextInt(100) < 5
                ? randomBetween(480, 720)
                : randomBetween(minSessionMinutes, maxSessionMinutes);

        long sessionTicks = sessionMinutes * 60L * TICKS_PER_SECOND;
        long jitter = random.nextLong(Math.max(1, sessionTicks / 7)) - sessionTicks / 14;
        sessionTicks = Math.max(60 * TICKS_PER_SECOND, sessionTicks + jitter);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (currentlyOnline.remove(name)) broadcastLeave(name);
        }, sessionTicks);
    }

    private void leaveRandomPlayer() {
        if (currentlyOnline.isEmpty()) return;

        List<String> outOfWindow = currentlyOnline.stream()
                .filter(name -> !traits.isInWindow(name))
                .toList();

        String name;
        if (!outOfWindow.isEmpty() && random.nextInt(100) < 70) {
            name = outOfWindow.get(random.nextInt(outOfWindow.size()));
        } else {
            List<String> online = new ArrayList<>(currentlyOnline);
            name = online.get(random.nextInt(online.size()));
        }

        currentlyOnline.remove(name);
        broadcastLeave(name);
    }

    // --- AI chat ---

    private void startActivityScheduler() {
        if (activityTaskId != -1) return;
        long intervalTicks = randomBetween(600, 1200); // 30-60 seconds
        activityTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentlyOnline.isEmpty()) return;
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                if (random.nextInt(100) < 3) simulateDeath();
                return;
            }
            plugin.getLogger().info("[FakeChat] Ambient tick -- asking AI...");
            askAiAndSend(null);
        }, intervalTicks, intervalTicks).getTaskId();
    }

    private void askAiAndSend(String event) {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        if (chatEngine == null || !chatEngine.isAvailable()) return;

        plugin.getLogger().info("[FakeChat] API call -- event: " + (event != null ? event : "ambient"));

        var future = (event != null)
                ? chatEngine.decideResponseToEvent(getOnlineFakes(), event)
                : chatEngine.decideResponse(getOnlineFakes());

        future.thenAccept(response -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!pool.isEnabled() || response == null) return;
            plugin.getLogger().info("[FakeChat] AI response: " + response);
            handleAiResponse(response);
        }));
    }

    private void handleAiResponse(String response) {
        String trimmed = response.trim();
        if (trimmed.equalsIgnoreCase("SKIP") || trimmed.equalsIgnoreCase("skip.")) return;

        int colonIdx = trimmed.indexOf(":");
        if (colonIdx <= 0) return;

        String speaker = trimmed.substring(0, colonIdx).trim();
        String message = trimmed.substring(colonIdx + 1).trim();
        if (message.isEmpty() || message.equalsIgnoreCase("SKIP")) return;

        if (!currentlyOnline.contains(speaker)) {
            final String target = speaker;
            speaker = currentlyOnline.stream()
                    .filter(name -> name.equalsIgnoreCase(target))
                    .findFirst().orElse(null);
            if (speaker == null) return;
        }

        plugin.getLogger().info("[FakeChat] Sending: " + speaker + " >> " + message);
        sendFakeChat(speaker, message);
        chatEngine.addContext(speaker, message);
        extractAndSaveFact(speaker, message);
    }

    /** If a bot's message contains personal info, save it as a fact for future consistency. */
    private void extractAndSaveFact(String botName, String message) {
        if (chatEngine == null) return;
        String lower = message.toLowerCase();
        // Only save messages that sound like personal facts
        boolean isPersonal = lower.contains("i live") || lower.contains("im from") || lower.contains("i'm from")
                || lower.contains("my job") || lower.contains("i work") || lower.contains("i go to")
                || lower.contains("years old") || lower.contains("yr old") || lower.contains("my name")
                || lower.contains("i have a") || lower.contains("my dog") || lower.contains("my cat")
                || lower.contains("my timezone") || lower.contains("its like") && lower.contains("am")
                || lower.contains("its like") && lower.contains("pm")
                || lower.contains("i play on") || lower.contains("my pc") || lower.contains("my setup")
                || lower.contains("in school") || lower.contains("in college") || lower.contains("graduated")
                || lower.contains("my favorite") || lower.contains("i hate") || lower.contains("i love")
                || lower.contains("my base is") || lower.contains("i built") || lower.contains("my farm");
        if (isPersonal) {
            chatEngine.recordFact(botName, message);
        }
    }

    // --- Death simulation ---

    private void simulateDeath() {
        String victim = randomOnlineFake();
        if (victim == null) return;

        String[] scenario = DEATH_SCENARIOS[random.nextInt(DEATH_SCENARIOS.length)];
        boolean needsKiller = "true".equals(scenario[1]);

        String deathMsg = needsKiller
                ? Lang.get(scenario[0], "player", victim, "killer",
                        MOB_NAMES[random.nextInt(MOB_NAMES.length)])
                : Lang.get(scenario[0], "player", victim);

        String colorized = MessageUtil.colorize(deathMsg);
        for (Player player : Bukkit.getOnlinePlayers()) player.sendMessage(colorized);
        Bukkit.getConsoleSender().sendMessage(colorized);

        if (chatEngine != null) chatEngine.addContext("SERVER", victim + " died");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pool.isEnabled()) askAiAndSend(victim + " (a fake player) just died");
        }, randomBetween(40, 120));
    }

    // --- Chat formatting ---

    private void sendFakeChat(String sender, String message) {
        if (sender == null || message == null) return;

        BotTraits.RankDisplay display = traits.resolveRankDisplay(sender);
        String formatted = MessageUtil.colorize(
                display.prefix() + " " + sender + " &8>> " + display.chatColor() + message);

        for (Player player : Bukkit.getOnlinePlayers()) player.sendMessage(formatted);
    }

    // --- Broadcast helpers ---

    private void broadcastJoin(String name) {
        ensureBotPlayerData(name);
        broadcast(Lang.get("fakeplayers.join", "player", name));
        BotTabListener tab = getTabListener();
        if (tab != null) tab.addFake(name, traits.getDisplayName(name));
        refreshTab();
    }

    /** Create a player data file for a bot so /user, /seen, etc. work on them. */
    private void ensureBotPlayerData(String name) {
        java.util.UUID botId = com.core.plugin.util.BotUtil.fakeUuid(name);
        var dm = plugin.dataManager();

        // Only write if they don't already have data
        if (dm.getFirstJoin(botId) > 0) {
            dm.setLastSeen(botId, System.currentTimeMillis());
            return;
        }

        // Generate realistic data — deterministic from name hash
        int hash = Math.abs(name.hashCode());
        long now = System.currentTimeMillis();

        // First join: 1-90 days ago
        long daysAgo = 1 + (hash % 90);
        dm.setFirstJoin(botId, now - (daysAgo * 86_400_000L));
        dm.setLastSeen(botId, now);

        // Rank
        String rank = traits.getRank(name);
        dm.setRank(botId, rank);

        // Random stats — scale with how long they've "been playing"
        int kills = hash % 50;
        int deaths = (hash / 50) % 80;
        int blocksBroken = 500 + (hash % 5000);
        int blocksPlaced = 200 + ((hash / 7) % 3000);
        int mobsKilled = 50 + (hash % 500);
        int fishCaught = (hash / 3) % 100;
        int playTime = (int) (daysAgo * 60 + (hash % 500));

        dm.setStat(botId, "kills", kills);
        dm.setStat(botId, "deaths", deaths);
        dm.setStat(botId, "blocks-broken", blocksBroken);
        dm.setStat(botId, "blocks-placed", blocksPlaced);
        dm.setStat(botId, "mobs-killed", mobsKilled);
        dm.setStat(botId, "fish-caught", fishCaught);
        dm.setStat(botId, "play-time", playTime);

        // Grant achievements based on their stats
        String[][] achievementThresholds = {
                {"first_blood", "kills", "1"}, {"warrior", "kills", "50"},
                {"first_death", "deaths", "1"}, {"accident_prone", "deaths", "100"},
                {"miner", "blocks-broken", "100"}, {"excavator", "blocks-broken", "1000"},
                {"deep_digger", "blocks-broken", "10000"},
                {"builder", "blocks-placed", "100"}, {"architect", "blocks-placed", "1000"},
                {"hunter", "mobs-killed", "50"}, {"slayer", "mobs-killed", "500"},
                {"fisherman", "fish-caught", "25"}, {"angler", "fish-caught", "200"},
                {"newcomer", "play-time", "60"}, {"regular", "play-time", "1440"},
                {"veteran", "play-time", "10080"},
        };

        java.util.Map<String, Integer> statMap = java.util.Map.of(
                "kills", kills, "deaths", deaths, "blocks-broken", blocksBroken,
                "blocks-placed", blocksPlaced, "mobs-killed", mobsKilled,
                "fish-caught", fishCaught, "play-time", playTime);

        for (String[] entry : achievementThresholds) {
            int statValue = statMap.getOrDefault(entry[1], 0);
            int threshold = Integer.parseInt(entry[2]);
            if (statValue >= threshold) {
                dm.addUnlockedAchievement(botId, entry[0]);
            }
        }

        // Set a nickname for some bots (20% chance)
        if (hash % 5 == 0) {
            dm.setNickname(botId, null); // no nickname by default
        }
    }

    private void broadcastLeave(String name) {
        // Update last-seen time
        java.util.UUID botId = com.core.plugin.util.BotUtil.fakeUuid(name);
        plugin.dataManager().setLastSeen(botId, System.currentTimeMillis());

        broadcast(Lang.get("fakeplayers.leave", "player", name));
        BotTabListener tab = getTabListener();
        if (tab != null) tab.removeFake(name);
        refreshTab();
    }

    private void broadcast(String langMessage) {
        String colorized = MessageUtil.colorize(langMessage);
        for (Player player : Bukkit.getOnlinePlayers()) player.sendMessage(colorized);
        Bukkit.getConsoleSender().sendMessage(colorized);
    }

    private void refreshTab() {
        BotTabListener tab = getTabListener();
        if (tab != null) tab.refreshAll();
    }

    private BotTabListener getTabListener() {
        for (var listener : org.bukkit.event.HandlerList.getRegisteredListeners(plugin)) {
            if (listener.getListener() instanceof BotTabListener tab) return tab;
        }
        return null;
    }

    // --- Pool rotation ---

    private void startPoolRotation() {
        if (rotationTaskId != -1) return;
        long intervalTicks = randomBetween(144000, 288000);
        rotationTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!pool.isEnabled()) return;
            int swapCount = randomBetween(1, 3);
            List<String> newNames = pool.rotate(currentlyOnline, swapCount);
            for (String name : newNames) {
                traits.assignSingle(name, chatEngine);
            }
            // Clean up traits for retired names
            Set<String> poolSet = new HashSet<>(pool.getNames());
            for (String existing : new ArrayList<>(currentlyOnline)) {
                if (!poolSet.contains(existing)) {
                    traits.remove(existing);
                }
            }
        }, intervalTicks, intervalTicks).getTaskId();
    }

    // --- Initialization helpers ---

    private void loadConfig() {
        minOnline = plugin.getConfig().getInt("fake-players.min-online", 10);
        maxOnline = plugin.getConfig().getInt("fake-players.max-online", 30);
        minSessionMinutes = plugin.getConfig().getInt("fake-players.min-session-minutes", 15);
        maxSessionMinutes = plugin.getConfig().getInt("fake-players.max-session-minutes", 180);
        joinLeaveIntervalSeconds = plugin.getConfig().getInt("fake-players.cycle-interval-seconds", 60);
    }

    private boolean isQuickRestart() {
        long lastShutdown = pool.getLastShutdownTime();
        return lastShutdown > 0
                && (System.currentTimeMillis() - lastShutdown) < QUICK_RESTART_THRESHOLD_MS;
    }

    private long seedInitialPlayers(boolean quickRestart) {
        int initialCount = randomBetween(minOnline, Math.min(maxOnline, pool.size()));

        List<String> shuffled = pool.shuffledBySchedulePriority(
                Collections.emptyMap(),
                name -> traits.isInWindow(name));

        long cumulativeDelay = quickRestart ? 20 : 100;
        int fastCount = quickRestart ? (int) (initialCount * 0.8) : 0;

        for (int i = 0; i < initialCount && i < shuffled.size(); i++) {
            String name = shuffled.get(i);
            long delay = cumulativeDelay;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!pool.isEnabled()) return;
                currentlyOnline.add(name);
                broadcastJoin(name);
            }, delay);

            cumulativeDelay += quickRestart && i < fastCount
                    ? randomBetween(20, 60)
                    : quickRestart
                            ? randomBetween(200, 600)
                            : randomBetween(100, 300);
        }

        return cumulativeDelay;
    }

    // --- Utility ---

    private String randomOnlineFake() {
        if (currentlyOnline.isEmpty()) return null;
        List<String> online = new ArrayList<>(currentlyOnline);
        return online.get(random.nextInt(online.size()));
    }

    private int randomBetween(int min, int max) {
        if (min >= max) return min;
        return min + random.nextInt(max - min + 1);
    }

    private int cancelTask(int taskId) {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        return -1;
    }
}
