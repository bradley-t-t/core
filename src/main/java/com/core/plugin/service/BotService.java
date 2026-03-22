package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.bots.BotChatEngine;
import com.core.plugin.modules.bots.BotNameValidator;
import com.core.plugin.modules.bots.BotPool;
import com.core.plugin.listener.BotTabListener;
import com.core.plugin.modules.bots.BotTraits;
import com.core.plugin.util.BotUtil;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.rank.RankLevel;
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

    private static final String[] VOTE_SITE_NAMES = {
            "MinecraftServers.org", "PlanetMinecraft", "TopMinecraftServers",
            "MinecraftMP.com", "ServerList101"
    };

    private final CorePlugin plugin;
    private final BotPool pool;
    private final BotTraits traits;
    private final Set<String> currentlyOnline = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();

    /** Tracks how many times each bot has voted today, keyed by bot name. */
    private final Map<String, Integer> dailyVoteCounts = new ConcurrentHashMap<>();
    /** Bots that are currently muted (name -> unmute time in millis, -1 = permanent). */
    private final Map<String, Long> mutedBots = new ConcurrentHashMap<>();
    /** Players who have called bots "bots" — bots will be rude to them. */
    private final Set<String> botAccusers = ConcurrentHashMap.newKeySet();
    /** The day-of-year the vote counts were last reset. */
    private int voteResetDay = -1;

    private BotChatEngine chatEngine;
    private int minOnline;
    private int maxOnline;
    private int minSessionMinutes;
    private int maxSessionMinutes;
    private int joinLeaveIntervalSeconds;
    private int voteLinkCount;
    private int schedulerTaskId = -1;
    private int activityTaskId = -1;
    private int rotationTaskId = -1;
    private int voteTaskId = -1;

    public BotService(CorePlugin plugin) {
        this.plugin = plugin;
        this.pool = new BotPool(plugin);
        this.traits = new BotTraits(plugin);
    }

    // --- Service lifecycle ---

    @Override
    public void enable() {
        loadConfig();

        // Set up name validator before loading pool
        BotNameValidator validator = new BotNameValidator(plugin);
        pool.setValidator(validator);
        BotUtil.setValidator(validator);

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
        startVoteScheduler();

        // Validate bot names against Mojang API in the background.
        // Invalid names (no real MC account) get replaced so external sites show them correctly.
        validator.validateAsync(pool.getNames(), invalidNames -> {
            if (!invalidNames.isEmpty()) {
                List<String> newNames = pool.replaceInvalidNames(currentlyOnline);
                for (String name : newNames) {
                    traits.assignSingle(name, chatEngine);
                }
                // Re-validate the new names
                if (!newNames.isEmpty()) {
                    validator.validateAsync(newNames, ignored -> {});
                }
            }
        });

        plugin.getLogger().info("Fake player system active: pool of " + pool.size() + ".");
    }

    @Override
    public void disable() {
        cancelTask(schedulerTaskId);
        cancelTask(activityTaskId);
        cancelTask(rotationTaskId);
        cancelTask(voteTaskId);
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
        boolean wasOnline = currentlyOnline.remove(name);
        pool.remove(name);
        if (wasOnline) {
            broadcastLeave(name);
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
        return Set.copyOf(botAccusers);
    }

    public int getMinOnline() { return minOnline; }
    public int getMaxOnline() { return maxOnline; }

    public void setMinOnline(int min) {
        this.minOnline = min;
        plugin.getConfig().set("fake-players.min-online", min);
        plugin.saveConfig();
        drainIfOverMax();
    }

    public void setMaxOnline(int max) {
        this.maxOnline = max;
        plugin.getConfig().set("fake-players.max-online", max);
        plugin.saveConfig();
        pool.load(max); // resize pool if needed
        traits.assignAll(pool.getNames(), chatEngine);
        drainIfOverMax();
    }

    /** Gradually remove bots that exceed the current max. */
    private void drainIfOverMax() {
        int excess = currentlyOnline.size() - maxOnline;
        if (excess <= 0) return;

        List<String> toRemove = new ArrayList<>(currentlyOnline);
        Collections.shuffle(toRemove, random);
        toRemove = toRemove.subList(0, excess);

        for (int i = 0; i < toRemove.size(); i++) {
            String name = toRemove.get(i);
            long delay = (long) (i + 1) * randomBetween(60, 200); // staggered 3-10s apart
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (currentlyOnline.remove(name)) broadcastLeave(name);
            }, delay);
        }
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
                if (!pool.isEnabled() || currentlyOnline.size() >= maxOnline) return;
                currentlyOnline.add(name);
                broadcastJoin(name);
            }, delayTicks);
        }

        long intervalTicks = joinLeaveIntervalSeconds * TICKS_PER_SECOND;
        schedulerTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::cycle,
                intervalTicks, intervalTicks).getTaskId();

        startActivityScheduler();
        startPoolRotation();
        startVoteScheduler();
    }

    public void deactivate() {
        if (!pool.isEnabled()) return;
        pool.setEnabled(false);

        schedulerTaskId = cancelTask(schedulerTaskId);
        activityTaskId = cancelTask(activityTaskId);
        rotationTaskId = cancelTask(rotationTaskId);
        voteTaskId = cancelTask(voteTaskId);

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
        // 20% chance to react to a death
        if (random.nextInt(100) < 20) {
            askAiAndSend(victimName + " (" + resolveRankName(victimName) + ") just died");
        }
    }

    public void onRealPlayerJoin(String playerName, boolean firstJoin) {
        if (!pool.isEnabled() || currentlyOnline.isEmpty()) return;
        plugin.getLogger().info("[FakeChat] Join event: " + playerName + (firstJoin ? " (first join)" : ""));

        // 5-6 bots welcome first-time joiners
        if (firstJoin) {
            sendWelcomeCluster(playerName);
            return; // welcome cluster is enough, don't also ask AI
        }

        // 15% chance to react to a returning player joining
        if (random.nextInt(100) < 15) {
            askAiAndSend(playerName + " (" + resolveRankName(playerName) + ") just joined the server");
        }
    }

    public void onRealPlayerChat(String playerName, String message) {
        if (!pool.isEnabled() || currentlyOnline.isEmpty()) return;
        if (chatEngine != null) chatEngine.addContext(playerName, resolveRankName(playerName), message);
        plugin.getLogger().info("[FakeChat] Chat event: " + playerName + " >> " + message);

        String lower = message.toLowerCase();

        // Detect players calling bots "bots"
        boolean callsBots = lower.contains("bot") || lower.contains("npc") || lower.contains("fake player")
                || lower.contains("fake account") || lower.contains("not real");
        if (callsBots) {
            botAccusers.add(playerName);
            // Always respond to bot accusations — multiple bots get offended
            int responders = Math.min(currentlyOnline.size(), randomBetween(2, 4));
            for (int i = 0; i < responders; i++) {
                long delay = randomBetween(20, 60) + (long) i * randomBetween(15, 40);
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        askAiAndSend(playerName + " (" + resolveRankName(playerName)
                                + ") just accused people of being bots, saying: " + message),
                        delay);
            }
            return;
        }

        boolean addressesEveryone = lower.contains("everyone") || lower.contains("every one")
                || lower.contains("hey all") || lower.contains("anybody")
                || lower.contains("anyone") || lower.contains("you guys")
                || lower.contains("yall") || lower.contains("y'all");
        boolean mentionsBot = currentlyOnline.stream()
                .anyMatch(bot -> lower.contains(bot.toLowerCase()));
        boolean isQuestion = message.contains("?") || lower.startsWith("how")
                || lower.startsWith("what") || lower.startsWith("where")
                || lower.startsWith("why") || lower.startsWith("who")
                || lower.startsWith("can ") || lower.startsWith("does ")
                || lower.startsWith("is there") || lower.startsWith("do you");

        // Group-addressed messages: multiple bots respond
        if (addressesEveryone) {
            int responders = Math.min(currentlyOnline.size(), randomBetween(2, 4));
            for (int i = 0; i < responders; i++) {
                long delay = randomBetween(30, 80) + (long) i * randomBetween(20, 60);
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        askAiAndSend(playerName + " (" + resolveRankName(playerName) + ") said to everyone: " + message),
                        delay);
            }
            return;
        }

        // Direct mention or question: 80% chance
        if (mentionsBot || isQuestion) {
            if (random.nextInt(100) < 80) {
                askAiAndSend(playerName + " (" + resolveRankName(playerName) + ") said: " + message);
            }
            return;
        }

        // Regular chat: 40% chance
        if (random.nextInt(100) < 40) {
            askAiAndSend(playerName + " (" + resolveRankName(playerName) + ") said: " + message);
        }
    }

    private String resolveRankName(String playerName) {
        var player = org.bukkit.Bukkit.getPlayerExact(playerName);
        if (player == null) return "player";
        var rankService = plugin.services().get(com.core.plugin.service.RankService.class);
        if (rankService == null) return "player";
        return rankService.getLevel(player.getUniqueId()).name().toLowerCase();
    }

    // --- Punishment handling ---

    /**
     * Called when a bot receives a punishment.
     * Bots react naturally: banned bots leave, muted bots stop talking, kicked bots leave temporarily.
     */
    public void onBotPunished(String botName, String typeKey, long durationMillis) {
        switch (typeKey) {
            case "ban" -> {
                // Bot gets banned — remove from online, pool, and add to Bukkit ban list
                if (currentlyOnline.remove(botName)) {
                    broadcastLeave(botName);
                }
                pool.remove(botName);
                // Apply real Bukkit ban so the name is properly blocked
                Date expiry = durationMillis == -1 ? null : new Date(System.currentTimeMillis() + durationMillis);
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                        .addBan(botName, "Banned", expiry, "Server");
            }
            case "mute" -> {
                // Bot gets muted — track mute expiry, stop talking
                long unmute = durationMillis == -1 ? -1 : System.currentTimeMillis() + durationMillis;
                mutedBots.put(botName, unmute);
            }
            case "warn" -> {
                // Bot gets kicked/warned — leave for a while then maybe come back
                if (currentlyOnline.remove(botName)) {
                    broadcastLeave(botName);
                    // Rejoin after 5-15 minutes
                    long rejoinTicks = randomBetween(6000, 18000);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (pool.isEnabled() && !currentlyOnline.contains(botName)
                                && pool.getNames().contains(botName)) {
                            currentlyOnline.add(botName);
                            broadcastJoin(botName);
                        }
                    }, rejoinTicks);
                }
            }
        }
    }

    /** Returns true if a bot is currently muted. Also cleans up expired mutes. */
    private boolean isBotMuted(String botName) {
        Long unmute = mutedBots.get(botName);
        if (unmute == null) return false;
        if (unmute == -1) return true; // permanent
        if (System.currentTimeMillis() >= unmute) {
            mutedBots.remove(botName);
            return false;
        }
        return true;
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
        if (currentlyOnline.size() >= maxOnline) return;

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

    // --- Welcome messages ---

    private static final String[] WELCOME_MESSAGES = {
            "Welcome!", "welcome!", "Welcome!!", "welcome!!", "wb!", "welcomee",
            "hey welcome!", "ayy welcome", "Welcome :)", "hii welcome",
            "yoo welcome!", "heyo welcome", "hey!", "welcoome", "welcome dude"
    };

    private void sendWelcomeCluster(String playerName) {
        List<String> online = new ArrayList<>(currentlyOnline);
        if (online.isEmpty()) return;

        Collections.shuffle(online, random);
        int count = Math.min(online.size(), randomBetween(5, 6));

        for (int i = 0; i < count; i++) {
            String bot = online.get(i);
            String msg = WELCOME_MESSAGES[random.nextInt(WELCOME_MESSAGES.length)];
            long delayTicks = randomBetween(20, 60) + (long) i * randomBetween(15, 40);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (pool.isEnabled() && currentlyOnline.contains(bot)) {
                    sendFakeChat(bot, msg);
                    if (chatEngine != null) chatEngine.addContext(bot, msg);
                }
            }, delayTicks);
        }
    }

    // --- Private message replies ---

    /**
     * Generates an AI-driven private reply from a bot to a real player.
     * The callback is invoked on the main thread with the reply message.
     */
    public void generatePrivateReply(String botName, String senderName, String incomingMessage,
                                      java.util.function.Consumer<String> callback) {
        if (chatEngine == null || !chatEngine.isAvailable()) {
            // Fallback: canned reply after a delay
            long delay = 40 + random.nextInt(80);
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    callback.accept(FALLBACK_DM_REPLIES[random.nextInt(FALLBACK_DM_REPLIES.length)]), delay);
            return;
        }

        var future = chatEngine.generateDmReply(botName, senderName, incomingMessage);
        future.thenAccept(reply -> {
            long delay = 30 + random.nextInt(100); // 1.5-6.5 seconds
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (reply == null || reply.isBlank()) {
                    callback.accept(FALLBACK_DM_REPLIES[random.nextInt(FALLBACK_DM_REPLIES.length)]);
                } else {
                    callback.accept(reply);
                }
            }, delay);
        });
    }

    private static final String[] FALLBACK_DM_REPLIES = {
            "hey", "whats up", "yeah?", "hm?", "one sec",
            "busy rn", "oh hey", "sup", "lol what", "?",
            "cant talk rn", "who is this", "hey whats up",
    };

    // --- AI chat ---

    private void startActivityScheduler() {
        if (activityTaskId != -1) return;
        long intervalTicks = randomBetween(6000, 12000); // 5-10 minutes
        activityTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentlyOnline.isEmpty()) return;
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                if (random.nextInt(100) < 3) simulateDeath();
                return;
            }
            // 15% chance of ambient chatter
            if (random.nextInt(100) >= 15) return;
            plugin.getLogger().info("[FakeChat] Ambient tick -- asking AI...");
            askAiAndSend(null);
        }, intervalTicks, intervalTicks).getTaskId();
    }

    private void askAiAndSend(String event) {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        if (chatEngine == null || !chatEngine.isAvailable()) return;

        chatEngine.updateAccusers(getBotAccusers());
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

        String mob = MOB_NAMES[random.nextInt(MOB_NAMES.length)];
        String deathMsg = needsKiller
                ? Lang.get(scenario[0], "player", victim, "killer", mob)
                : Lang.get(scenario[0], "player", victim);
        String deathMsgOp = needsKiller
                ? Lang.get(scenario[0], "player", "*" + victim, "killer", mob)
                : Lang.get(scenario[0], "player", "*" + victim);

        broadcastBotMessage(deathMsg, deathMsgOp);

        if (chatEngine != null) chatEngine.addContext("SERVER", victim + " died");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pool.isEnabled()) askAiAndSend(victim + " (a fake player) just died");
        }, randomBetween(40, 120));
    }

    // --- Chat formatting ---

    private void sendFakeChat(String sender, String message) {
        if (sender == null || message == null) return;
        if (isBotMuted(sender)) return;

        BotTraits.RankDisplay display = traits.resolveRankDisplay(sender);
        String base = display.prefix() + " " + sender + " &8>> " + display.chatColor() + message;
        String opBase = display.prefix() + " &7*" + sender + " &8>> " + display.chatColor() + message;

        RankService rankService = plugin.services().get(RankService.class);
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean isOp = rankService != null
                    && rankService.getLevel(player.getUniqueId()).isAtLeast(RankLevel.OPERATOR);
            player.sendMessage(MessageUtil.colorize(isOp ? opBase : base));
        }
    }

    // --- Broadcast helpers ---

    private void broadcastJoin(String name) {
        ensureBotPlayerData(name);
        if (plugin.getConfig().getBoolean("join-quit-messages", true)) {
            broadcastBotMessage(
                    Lang.get("fakeplayers.join", "player", name),
                    Lang.get("fakeplayers.join", "player", "*" + name));
        }
        BotTabListener tab = getTabListener();
        if (tab != null) tab.addFake(name, traits.getDisplayName(name), this);
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

        if (plugin.getConfig().getBoolean("join-quit-messages", true)) {
            broadcastBotMessage(
                    Lang.get("fakeplayers.leave", "player", name),
                    Lang.get("fakeplayers.leave", "player", "*" + name));
        }
        BotTabListener tab = getTabListener();
        if (tab != null) tab.removeFake(name);
        refreshTab();
    }

    /** Sends a bot-related broadcast with operator-visible distinction. */
    private void broadcastBotMessage(String normalMessage, String opMessage) {
        String normal = MessageUtil.colorize(normalMessage);
        String op = MessageUtil.colorize(opMessage);
        RankService rankService = plugin.services().get(RankService.class);
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean isOp = rankService != null
                    && rankService.getLevel(player.getUniqueId()).isAtLeast(RankLevel.OPERATOR);
            player.sendMessage(isOp ? op : normal);
        }
        Bukkit.getConsoleSender().sendMessage(op);
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
        // Derive vote count from configured vote links, fall back to explicit setting
        java.util.List<?> voteLinks = plugin.getConfig().getList("voting.vote-links");
        voteLinkCount = (voteLinks != null && !voteLinks.isEmpty())
                ? voteLinks.size()
                : plugin.getConfig().getInt("fake-players.vote-link-count", 1);
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
                if (!pool.isEnabled() || currentlyOnline.size() >= maxOnline) return;
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

    // --- Vote simulation ---

    /**
     * Periodically triggers bot votes throughout the day.
     * Each bot votes {@code voteLinkCount} times per day.
     * Votes happen in clusters of 2-3 bots to mimic real voting behavior.
     */
    private void startVoteScheduler() {
        if (voteTaskId != -1) return;
        if (voteLinkCount <= 0) return;

        // Check every 3-8 minutes for a vote opportunity
        long intervalTicks = randomBetween(3600, 9600); // 3-8 min
        voteTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tryVoteCluster,
                randomBetween(6000, 12000), intervalTicks).getTaskId(); // first check after 5-10 min
    }

    private void tryVoteCluster() {
        if (!pool.isEnabled() || currentlyOnline.isEmpty()) return;

        resetDailyVotesIfNeeded();

        // Find online bots that haven't hit their daily vote limit
        List<String> eligible = currentlyOnline.stream()
                .filter(name -> dailyVoteCounts.getOrDefault(name, 0) < voteLinkCount)
                .toList();

        if (eligible.isEmpty()) return;

        // Spread votes throughout the day: probability scales with how many still need to vote
        // relative to how much of the day is left
        int totalEligible = eligible.size();
        int hour = java.time.LocalTime.now().getHour();
        // Higher chance later in the day if many bots still haven't voted
        int hoursLeft = Math.max(1, 24 - hour);
        // Aim to average out votes: if 20 eligible and 12 hours left, ~1-2 should vote now
        // Base chance per tick of this scheduler: roughly eligible / (hoursLeft * checksPerHour)
        // ~10 checks per hour at 6min interval
        double chance = (double) totalEligible / (hoursLeft * 10.0);
        chance = Math.min(chance, 0.5); // cap at 50% per check

        if (random.nextDouble() >= chance) return;

        // Pick a cluster of 1-3 bots
        List<String> shuffled = new ArrayList<>(eligible);
        Collections.shuffle(shuffled, random);
        int clusterSize = Math.min(shuffled.size(), randomBetween(1, 3));

        // First bot votes immediately
        simulateVote(shuffled.get(0));

        // Remaining cluster members vote after a short delay (3-30 seconds)
        for (int i = 1; i < clusterSize; i++) {
            String follower = shuffled.get(i);
            long delayTicks = randomBetween(60, 600); // 3-30 seconds
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (pool.isEnabled() && currentlyOnline.contains(follower)) {
                    simulateVote(follower);
                }
            }, delayTicks);
        }
    }

    private void simulateVote(String botName) {
        dailyVoteCounts.merge(botName, 1, Integer::sum);

        String site = pickVoteSiteName();
        VoteService voteService = plugin.services().get(VoteService.class);
        if (voteService != null) {
            voteService.processVote(botName, site, false);
        }

        // Broadcast with operator distinction
        broadcastBotMessage(
                Lang.get("vote.received", "player", botName),
                Lang.get("vote.received", "player", "*" + botName));
    }

    /** Picks a vote site name from config, falling back to the hardcoded list. */
    private String pickVoteSiteName() {
        java.util.List<?> links = plugin.getConfig().getList("voting.vote-links");
        if (links != null && !links.isEmpty()) {
            Object entry = links.get(random.nextInt(links.size()));
            if (entry instanceof java.util.Map<?, ?> map) {
                Object name = map.get("name");
                if (name != null) return name.toString();
            }
        }
        return VOTE_SITE_NAMES[random.nextInt(VOTE_SITE_NAMES.length)];
    }

    private void resetDailyVotesIfNeeded() {
        int today = java.time.LocalDate.now().getDayOfYear();
        if (today != voteResetDay) {
            dailyVoteCounts.clear();
            voteResetDay = today;
        }
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
