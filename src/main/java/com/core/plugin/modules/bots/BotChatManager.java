package com.core.plugin.modules.bots;

import com.core.plugin.CorePlugin;
import com.core.plugin.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Orchestrates all bot chat behavior with realistic human patterns:
 * thinking delays, conversation fatigue, personality-aware response chances,
 * typing delay, AFK windows, welcome clusters, death simulation, and AI chat.
 */
public final class BotChatManager {

    private final CorePlugin plugin;
    private final BotChatEngine chatEngine;
    private final BotBroadcaster broadcaster;
    private final BotTraits traits;
    private final BotPool pool;
    private final Set<String> onlineBots;
    private final Set<String> botAccusers = ConcurrentHashMap.newKeySet();

    /** Bots currently in a silence/AFK window. */
    private final Set<String> silentBots = ConcurrentHashMap.newKeySet();
    /** Recent bot messages for dedup — prevents near-identical back-to-back messages. */
    private final Deque<String> recentMessages = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static final int DEDUP_BUFFER_SIZE = 10;

    /** Conversation fatigue: tracks recent message timestamps per bot. */
    private final Map<String, Deque<Long>> botMessageTimestamps = new ConcurrentHashMap<>();

    /** Tracks when the last real player message was received (millis). */
    private volatile long lastRealPlayerChatTime = 0;

    /** Tracks bot-to-bot chain depth to prevent infinite loops. */
    private int botToBotChainDepth = 0;

    private final Random random = new Random();
    private int activityTaskId = -1;

    public BotChatManager(CorePlugin plugin, BotChatEngine chatEngine,
                           BotBroadcaster broadcaster, BotTraits traits,
                           BotPool pool, Set<String> onlineBots) {
        this.plugin = plugin;
        this.chatEngine = chatEngine;
        this.broadcaster = broadcaster;
        this.traits = traits;
        this.pool = pool;
        this.onlineBots = onlineBots;
    }

    public Set<String> getAccusers() {
        return Set.copyOf(botAccusers);
    }

    // --- Ambient activity scheduler ---

    public void startActivityScheduler() {
        if (activityTaskId != -1) return;
        long intervalTicks = randomBetween(BotConfig.AMBIENT_MIN_INTERVAL, BotConfig.AMBIENT_MAX_INTERVAL);
        activityTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::ambientTick,
                intervalTicks, intervalTicks).getTaskId();
    }

    public void stopActivityScheduler() {
        if (activityTaskId != -1) {
            Bukkit.getScheduler().cancelTask(activityTaskId);
            activityTaskId = -1;
        }
    }

    private void ambientTick() {
        if (onlineBots.isEmpty()) return;

        // Chance for a random bot to enter/exit AFK
        processAfkCycles();

        if (Bukkit.getOnlinePlayers().isEmpty()) {
            if (random.nextInt(100) < BotConfig.IDLE_DEATH_CHANCE) simulateDeath();
            return;
        }

        // Natural silence: if no real player has chatted recently, bots should be quiet too
        long silenceMs = System.currentTimeMillis() - lastRealPlayerChatTime;
        if (lastRealPlayerChatTime > 0 && silenceMs > BotConfig.SILENCE_THRESHOLD_MS) {
            // Very low chance of ambient chat when nobody real is talking
            if (random.nextInt(100) >= BotConfig.SILENCE_AMBIENT_CHANCE) return;
        }

        // Self-initiated messages — only when real players are actively chatting
        if (silenceMs < BotConfig.SELF_INITIATE_RECENCY_MS
                && random.nextInt(100) < BotConfig.SELF_INITIATE_CHANCE) {
            String bot = randomNonFatiguedBot();
            if (bot != null) {
                BotTraits.Personality personality = traits.getPersonality(bot);
                String msg = personality.ambientMessages.length > 0
                        ? personality.ambientMessages[random.nextInt(personality.ambientMessages.length)]
                        : BotMessages.random(BotMessages.SELF_INITIATE);
                sendWithThinkingAndTyping(bot, msg, false, () -> {
                    if (chatEngine != null) chatEngine.addContext(bot, msg);
                    maybeTriggerbotToBot(bot, msg);
                });
                return;
            }
        }

        // Regular ambient AI chatter
        if (random.nextInt(100) >= BotConfig.AMBIENT_CHAT_CHANCE) return;
        askAiAndSend(null);
    }

    // --- AFK / Silence windows ---

    private void processAfkCycles() {
        for (String bot : onlineBots) {
            if (silentBots.contains(bot)) continue;
            if (random.nextInt(100) >= BotConfig.AFK_START_CHANCE) continue;

            silentBots.add(bot);

            if (random.nextInt(100) < BotConfig.AFK_ANNOUNCE_CHANCE) {
                sendWithThinkingAndTyping(bot, BotMessages.random(BotMessages.AFK_START), false, null);
            }

            long afkDuration = randomBetween(BotConfig.AFK_MIN_DURATION, BotConfig.AFK_MAX_DURATION);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!onlineBots.contains(bot)) {
                    silentBots.remove(bot);
                    return;
                }
                silentBots.remove(bot);
                if (random.nextInt(100) < BotConfig.AFK_RETURN_ANNOUNCE_CHANCE) {
                    sendWithThinkingAndTyping(bot, BotMessages.random(BotMessages.AFK_RETURN), false, null);
                }
            }, afkDuration);
        }
    }

    private boolean isActive(String bot) {
        return onlineBots.contains(bot) && !silentBots.contains(bot);
    }

    private String randomActiveBotOrNull() {
        List<String> active = onlineBots.stream()
                .filter(b -> !silentBots.contains(b))
                .toList();
        if (active.isEmpty()) return null;
        return active.get(random.nextInt(active.size()));
    }

    /** Pick a random active bot that isn't fatigued (hasn't spoken too much recently). */
    private String randomNonFatiguedBot() {
        List<String> candidates = onlineBots.stream()
                .filter(b -> !silentBots.contains(b) && !isFatigued(b))
                .toList();
        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    // --- Conversation fatigue ---

    /** Returns true if a bot has spoken too many times in the recent window. */
    private boolean isFatigued(String bot) {
        Deque<Long> timestamps = botMessageTimestamps.get(bot);
        if (timestamps == null) return false;

        long cutoff = System.currentTimeMillis() - BotConfig.FATIGUE_WINDOW_MS;
        // Clean old entries
        while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
            timestamps.pollFirst();
        }

        return timestamps.size() >= BotConfig.FATIGUE_MAX_MESSAGES;
    }

    /** Record that a bot just spoke. */
    private void recordBotMessage(String bot) {
        botMessageTimestamps.computeIfAbsent(bot, k -> new java.util.concurrent.ConcurrentLinkedDeque<>())
                .addLast(System.currentTimeMillis());
    }

    // --- Thinking + Typing delay ---

    /**
     * Send a message with a realistic thinking delay (reading + processing)
     * followed by a typing delay. Quick reactions skip the thinking phase.
     *
     * @param needsThinking true for responses to questions/complex messages,
     *                      false for reflexive reactions ("lol", "wb")
     */
    private void sendWithThinkingAndTyping(String bot, String message, boolean needsThinking, Runnable afterSend) {
        long thinkDelay = needsThinking ? calculateThinkingDelay(message) : randomBetween(5, 20);
        long typeDelay = calculateTypingDelay(message);
        long totalDelay = thinkDelay + typeDelay;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isActive(bot)) return;
            if (isFatigued(bot)) return; // Double-check fatigue at send time
            broadcaster.sendChat(bot, message);
            recordBotMessage(bot);
            if (afterSend != null) afterSend.run();
        }, totalDelay);
    }

    /** Thinking delay — how long to "read and process" before typing starts. */
    private long calculateThinkingDelay(String context) {
        // Longer messages or questions need more thinking time
        int baseThink = randomBetween(BotConfig.THINK_MIN_TICKS, BotConfig.THINK_MAX_TICKS);
        // Add extra for longer context
        if (context != null && context.length() > 30) {
            baseThink += randomBetween(10, 40);
        }
        return baseThink;
    }

    private long calculateTypingDelay(String message) {
        double baseTicks = message.length() * BotConfig.TICKS_PER_CHARACTER;
        int jitter = random.nextInt(BotConfig.TYPING_JITTER * 2) - BotConfig.TYPING_JITTER;
        long total = Math.round(baseTicks) + jitter;
        return Math.max(BotConfig.MIN_TYPING_DELAY, Math.min(BotConfig.MAX_TYPING_DELAY, total));
    }

    // --- Personality-aware response chance ---

    private double personalityMultiplier(String botName) {
        BotTraits.Personality personality = traits.getPersonality(botName);
        return switch (personality) {
            case QUIET -> BotConfig.PERSONALITY_QUIET_MULT;
            case CASUAL -> BotConfig.PERSONALITY_CASUAL_MULT;
            case SOCIAL -> BotConfig.PERSONALITY_SOCIAL_MULT;
            case TRYHARD -> BotConfig.PERSONALITY_TRYHARD_MULT;
            case CHILL -> BotConfig.PERSONALITY_CHILL_MULT;
        };
    }

    /** Roll a personality-weighted chance, also factoring in fatigue. */
    private boolean rollPersonalityChance(int baseChance) {
        String bot = randomNonFatiguedBot();
        if (bot == null) return false;
        double adjustedChance = baseChance * personalityMultiplier(bot);
        return random.nextInt(100) < adjustedChance;
    }

    // --- Bot-to-bot interaction ---

    private void maybeTriggerbotToBot(String speakerBot, String message) {
        if (botToBotChainDepth >= BotConfig.BOT_TO_BOT_MAX_CHAIN) return;
        if (random.nextInt(100) >= BotConfig.BOT_TO_BOT_CHANCE) return;

        List<String> candidates = onlineBots.stream()
                .filter(b -> !b.equals(speakerBot) && !silentBots.contains(b) && !isFatigued(b))
                .toList();
        if (candidates.isEmpty()) return;

        String responder = candidates.get(random.nextInt(candidates.size()));
        BotTraits.Personality personality = traits.getPersonality(responder);

        double mult = personalityMultiplier(responder);
        if (random.nextDouble() >= mult) return;

        String reply = personality.chatReactions.length > 0
                ? personality.chatReactions[random.nextInt(personality.chatReactions.length)]
                : "lol";

        long extraDelay = randomBetween(40, 100);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isActive(responder) || isFatigued(responder)) return;
            botToBotChainDepth++;
            sendWithThinkingAndTyping(responder, reply, false, () -> {
                if (chatEngine != null) chatEngine.addContext(responder, reply);
                botToBotChainDepth = 0;
            });
        }, extraDelay);
    }

    // --- Event hooks ---

    public void onRealPlayerDeath(String victimName, String rankName) {
        if (onlineBots.isEmpty()) return;
        if (chatEngine != null) chatEngine.addContext("SERVER", null, victimName + " died");

        if (rollPersonalityChance(BotConfig.DEATH_REACT_CHANCE)) {
            askAiAndSend(victimName + " (" + rankName + ") just died");
        }
    }

    public void onRealPlayerJoin(String playerName, boolean firstJoin, String rankName) {
        if (onlineBots.isEmpty()) return;

        if (firstJoin) {
            sendWelcomeCluster(playerName);
            return;
        }

        if (rollPersonalityChance(BotConfig.JOIN_REACT_CHANCE)) {
            askAiAndSend(playerName + " (" + rankName + ") just joined the server");
        }
    }

    public void onRealPlayerChat(String playerName, String rankName, String message) {
        if (onlineBots.isEmpty()) return;
        lastRealPlayerChatTime = System.currentTimeMillis();
        if (chatEngine != null) chatEngine.addContext(playerName, rankName, message);

        String lower = message.toLowerCase();

        // Detect bot accusations — vary response count, sometimes ignore entirely
        if (containsAny(lower, BotMessages.ACCUSATION_KEYWORDS)) {
            botAccusers.add(playerName);
            int responders = random.nextInt(100) < BotConfig.ACCUSATION_IGNORE_CHANCE
                    ? 0
                    : Math.min(countActiveBots(),
                            randomBetween(BotConfig.ACCUSATION_MIN_RESPONDERS, BotConfig.ACCUSATION_MAX_RESPONDERS));
            for (int i = 0; i < responders; i++) {
                long delay = randomBetween(40, 120) + (long) i * randomBetween(30, 80);
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        askAiAndSend(playerName + " (" + rankName
                                + ") just accused people of being bots, saying: " + message), delay);
            }
            return;
        }

        // Discord mentions — mostly ignore, rarely respond negatively
        if (lower.contains("discord")) {
            if (random.nextInt(100) < 15) {
                askAiAndSend(playerName + " (" + rankName + ") mentioned Discord: " + message);
            }
            return;
        }

        // Group-addressed messages
        if (containsAny(lower, BotMessages.GROUP_ADDRESS_KEYWORDS)) {
            int responders = Math.min(countActiveBots(),
                    randomBetween(BotConfig.GROUP_MIN_RESPONDERS, BotConfig.GROUP_MAX_RESPONDERS));
            for (int i = 0; i < responders; i++) {
                long delay = randomBetween(30, 80) + (long) i * randomBetween(20, 60);
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        askAiAndSend(playerName + " (" + rankName + ") said to everyone: " + message), delay);
            }
            return;
        }

        // Direct mention or question
        boolean mentionsBot = onlineBots.stream()
                .anyMatch(bot -> lower.contains(bot.toLowerCase()));
        boolean isQuestion = message.contains("?") || startsWithAny(lower, BotMessages.QUESTION_STARTERS);

        if (mentionsBot || isQuestion) {
            if (rollPersonalityChance(BotConfig.MENTION_CHAT_CHANCE)) {
                askAiAndSend(playerName + " (" + rankName + ") said: " + message);
            }
            return;
        }

        // Regular chat
        if (rollPersonalityChance(BotConfig.REGULAR_CHAT_CHANCE)) {
            askAiAndSend(playerName + " (" + rankName + ") said: " + message);
        }
    }

    // --- DM replies ---

    public void generatePrivateReply(String botName, String senderName, String incomingMessage,
                                      Consumer<String> callback) {
        if (chatEngine == null || !chatEngine.isAvailable()) {
            BotTraits.Personality personality = traits.getPersonality(botName);
            String reply = personality.chatReactions.length > 0
                    ? personality.chatReactions[random.nextInt(personality.chatReactions.length)]
                    : BotMessages.random(BotMessages.FALLBACK_DM);
            long delay = calculateThinkingDelay(incomingMessage) + calculateTypingDelay(reply);
            Bukkit.getScheduler().runTaskLater(plugin, () -> callback.accept(reply), delay);
            return;
        }

        var future = chatEngine.generateDmReply(botName, senderName, incomingMessage);
        future.thenAccept(reply -> {
            String finalReply = (reply == null || reply.isBlank())
                    ? BotMessages.random(BotMessages.FALLBACK_DM) : reply;
            long delay = calculateThinkingDelay(incomingMessage) + calculateTypingDelay(finalReply);
            Bukkit.getScheduler().runTaskLater(plugin, () -> callback.accept(finalReply), delay);
        });
    }

    // --- AI integration ---

    private void askAiAndSend(String event) {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        if (chatEngine == null || !chatEngine.isAvailable()) {
            sendFallbackMessage();
            return;
        }

        chatEngine.updateAccusers(getAccusers());

        var future = (event != null)
                ? chatEngine.decideResponseToEvent(onlineBots, event)
                : chatEngine.decideResponse(onlineBots);

        future.thenAccept(response -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!pool.isEnabled() || response == null) return;
            handleAiResponse(response, event != null && (event.contains("said:") || event.contains("question")));
        }));
    }

    private void sendFallbackMessage() {
        String bot = randomNonFatiguedBot();
        if (bot == null) return;

        BotTraits.Personality personality = traits.getPersonality(bot);
        String[] msgPool = personality.chatReactions.length > 0
                ? personality.chatReactions : BotMessages.FALLBACK_DM;
        String msg = msgPool[random.nextInt(msgPool.length)];

        sendWithThinkingAndTyping(bot, msg, false, () -> {
            if (chatEngine != null) chatEngine.addContext(bot, msg);
        });
    }

    private void handleAiResponse(String response, boolean needsThinking) {
        String trimmed = response.trim();
        if (trimmed.equalsIgnoreCase("SKIP") || trimmed.equalsIgnoreCase("skip.")) return;

        int colonIdx = trimmed.indexOf(":");
        if (colonIdx <= 0) return;

        String name = trimmed.substring(0, colonIdx).trim();
        String message = trimmed.substring(colonIdx + 1).trim();

        if (message.isEmpty() || !onlineBots.contains(name)) return;
        if (silentBots.contains(name)) return;
        if (isFatigued(name)) return;

        // Dedup: reject messages too similar to recent bot messages
        if (isTooSimilar(message)) return;
        trackMessage(message);

        sendWithThinkingAndTyping(name, message, needsThinking, () -> {
            if (chatEngine != null) chatEngine.addContext(name, message);
            extractAndSaveFact(name, message);
            maybeTriggerbotToBot(name, message);
        });
    }

    /** Check if a message is too similar to any recent bot message. */
    private boolean isTooSimilar(String message) {
        String normalized = normalizeForDedup(message);
        for (String recent : recentMessages) {
            if (similarity(normalized, recent) > 0.55) return true;
        }
        return false;
    }

    private void trackMessage(String message) {
        recentMessages.addLast(normalizeForDedup(message));
        while (recentMessages.size() > DEDUP_BUFFER_SIZE) {
            recentMessages.pollFirst();
        }
    }

    private String normalizeForDedup(String msg) {
        return msg.toLowerCase().replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
    }

    private double similarity(String a, String b) {
        Set<String> wordsA = new HashSet<>(Arrays.asList(a.split(" ")));
        Set<String> wordsB = new HashSet<>(Arrays.asList(b.split(" ")));
        if (wordsA.isEmpty() && wordsB.isEmpty()) return 1.0;

        Set<String> intersection = new HashSet<>(wordsA);
        intersection.retainAll(wordsB);
        Set<String> union = new HashSet<>(wordsA);
        union.addAll(wordsB);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private void extractAndSaveFact(String botName, String message) {
        if (chatEngine == null) return;
        String lower = message.toLowerCase();
        String[] factTriggers = {"i live", "my base", "i built", "i found", "i have a",
                "im building", "i'm building", "i play", "i started"};
        for (String trigger : factTriggers) {
            if (lower.contains(trigger)) {
                chatEngine.recordFact(botName, message);
                break;
            }
        }
    }

    // --- Welcome cluster ---

    private void sendWelcomeCluster(String playerName) {
        List<String> active = onlineBots.stream()
                .filter(b -> !silentBots.contains(b))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (active.isEmpty()) return;

        Collections.shuffle(active, random);

        // 15% chance nobody says anything at all
        if (random.nextInt(100) < BotConfig.WELCOME_SKIP_CHANCE) return;

        int count = Math.min(active.size(),
                randomBetween(BotConfig.WELCOME_MIN_BOTS, BotConfig.WELCOME_MAX_BOTS));

        for (int i = 0; i < count; i++) {
            String bot = active.get(i);
            BotTraits.Personality personality = traits.getPersonality(bot);
            String msg = personality.joinReactions.length > 0
                    ? personality.joinReactions[random.nextInt(personality.joinReactions.length)]
                    : BotMessages.random(BotMessages.WELCOME);

            // More spread out: thinking + stagger
            long delayTicks = randomBetween(20, 60) + (long) i * randomBetween(30, 80);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (pool.isEnabled() && isActive(bot)) {
                    broadcaster.sendChat(bot, msg);
                    recordBotMessage(bot);
                    if (chatEngine != null) chatEngine.addContext(bot, msg);
                }
            }, delayTicks);
        }
    }

    // --- Death simulation ---

    private void simulateDeath() {
        String victim = randomActiveBotOrNull();
        if (victim == null) return;

        var scenario = BotConfig.DEATH_SCENARIOS[random.nextInt(BotConfig.DEATH_SCENARIOS.length)];
        boolean needsKiller = Boolean.parseBoolean(scenario[1]);
        String mob = needsKiller ? BotConfig.MOB_NAMES[random.nextInt(BotConfig.MOB_NAMES.length)] : null;

        String deathMsg = needsKiller
                ? Lang.get(scenario[0], "player", victim, "killer", mob)
                : Lang.get(scenario[0], "player", victim);
        String deathMsgOp = needsKiller
                ? Lang.get(scenario[0], "player", "*" + victim, "killer", mob)
                : Lang.get(scenario[0], "player", "*" + victim);

        broadcaster.broadcastMessage(deathMsg, deathMsgOp);

        if (chatEngine != null) chatEngine.addContext("SERVER", victim + " died");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pool.isEnabled()) askAiAndSend(victim + " (a fake player) just died");
        }, randomBetween(40, 120));
    }

    // --- Utilities ---

    private int countActiveBots() {
        return (int) onlineBots.stream().filter(b -> !silentBots.contains(b)).count();
    }

    private boolean containsAny(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private boolean startsWithAny(String text, String[] prefixes) {
        for (String prefix : prefixes) {
            if (text.startsWith(prefix)) return true;
        }
        return false;
    }

    private int randomBetween(int min, int max) {
        if (min >= max) return min;
        return min + random.nextInt(max - min + 1);
    }
}
