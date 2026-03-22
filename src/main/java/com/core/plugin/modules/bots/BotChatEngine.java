package com.core.plugin.modules.bots;

import com.core.plugin.CorePlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single AI brain that watches all chat and decides when and how fake players
 * should respond. The AI sees the full conversation, the list of online fakes
 * with their writing styles, and decides on its own whether to reply and who
 * should say what.
 */
public final class BotChatEngine {

    private static final String API_URL = "https://api.x.ai/v1/chat/completions";
    private static final String MODEL = "grok-3-mini-fast";
    private static final int MAX_CONTEXT_MESSAGES = 40;
    private static final int MAX_RESPONSE_TOKENS = 60;
    private static final int MAX_CONCURRENT_REQUESTS = 2;
    private static final int MAX_PER_PLAYER_HISTORY = 12;
    private static final int RECENT_CONTEXT_SIZE = 20;
    private static final int CONNECT_TIMEOUT_MS = 15_000;

    private static final String[] WRITING_STYLES = {
            "types properly with capitalization and punctuation sometimes missing periods at the end of sentences",
            "types in all lowercase with no punctuation",
            "makes typos and drops letters sometimes",
            "uses heavy abbreviations and shorthand",
            "types with CAPS for emphasis sometimes, energetic",
            "extremely blunt, uses as few words as possible",
            "types in longer stream of consciousness style",
            "normal mix of caps and lowercase, casual"
    };

    private static final int MAX_FACTS_PER_BOT = 15;
    private static final int FACTS_SHOWN_IN_PROMPT = 8;

    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final CorePlugin plugin;

    private final List<String> chatHistory = new ArrayList<>();
    private final Map<String, String> playerStyles = new HashMap<>();
    private final Map<String, List<String>> perPlayerHistory = new HashMap<>();
    private final Map<String, List<String>> botFacts = new HashMap<>();
    private final java.io.File botDataDir;
    private final String apiKey;
    private final boolean apiAvailable;

    public BotChatEngine(CorePlugin plugin) {
        this.plugin = plugin;
        this.apiKey = plugin.getConfig().getString("fake-players.grok-api-key", "");
        this.apiAvailable = !apiKey.isEmpty();
        this.botDataDir = new java.io.File(plugin.getDataFolder(), "botdata");
        botDataDir.mkdirs();
    }

    public synchronized void addContext(String playerName, String message) {
        addContext(playerName, null, message);
    }

    public synchronized void addContext(String playerName, String rank, String message) {
        String entry = rank != null
                ? "[" + rank + "] " + playerName + ": " + message
                : playerName + ": " + message;
        chatHistory.add(entry);
        while (chatHistory.size() > MAX_CONTEXT_MESSAGES) {
            chatHistory.remove(0);
        }

        if (playerStyles.containsKey(playerName)) {
            List<String> history = perPlayerHistory.computeIfAbsent(playerName, k -> new ArrayList<>());
            history.add(message);
            while (history.size() > MAX_PER_PLAYER_HISTORY) {
                history.remove(0);
            }
        }
    }

    /** Assign a deterministic writing style to a fake player. */
    public void assignStyle(String name) {
        int hash = Math.abs(name.hashCode());
        playerStyles.put(name, WRITING_STYLES[hash % WRITING_STYLES.length]);
    }

    public void removeStyle(String name) {
        playerStyles.remove(name);
        perPlayerHistory.remove(name);
        botFacts.remove(name);
    }

    /** Load saved facts for a bot from disk. */
    public void loadFacts(String name) {
        java.io.File file = new java.io.File(botDataDir, name + ".yml");
        if (!file.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        List<String> facts = config.getStringList("facts");
        if (!facts.isEmpty()) botFacts.put(name, new ArrayList<>(facts));
    }

    /** Save a bot's facts to disk. */
    private void saveFacts(String name) {
        List<String> facts = botFacts.get(name);
        if (facts == null || facts.isEmpty()) return;
        java.io.File file = new java.io.File(botDataDir, name + ".yml");
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("facts", facts);
        try { config.save(file); } catch (java.io.IOException ignored) {}
    }

    /** Record a personal fact about a bot extracted from their message. */
    public void recordFact(String botName, String fact) {
        List<String> facts = botFacts.computeIfAbsent(botName, k -> new ArrayList<>());
        if (facts.contains(fact)) return;
        facts.add(fact);
        while (facts.size() > MAX_FACTS_PER_BOT) facts.remove(0);
        saveFacts(botName);
    }

    /** Get saved facts for a bot. */
    public List<String> getFacts(String name) {
        return botFacts.getOrDefault(name, List.of());
    }

    public boolean isAvailable() {
        return apiAvailable;
    }

    /**
     * The AI reads the chat log, sees which fakes are online and their styles,
     * and decides whether anyone should respond and what they should say.
     * Returns "PLAYER_NAME: message" or "SKIP" if nobody should talk.
     */
    public CompletableFuture<String> decideResponse(Set<String> onlineFakes) {
        return decide(onlineFakes, null);
    }

    /** Force a response to a specific event (real player chatted, died, joined). */
    public CompletableFuture<String> decideResponseToEvent(Set<String> onlineFakes, String event) {
        return decide(onlineFakes, event);
    }

    private CompletableFuture<String> decide(Set<String> onlineFakes, String event) {
        if (!apiAvailable || onlineFakes.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String context = getRecentContext();
        String fakeList = buildFakePlayerList(onlineFakes);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return event != null
                        ? callApiEvent(context, fakeList, event)
                        : callApi(context, fakeList);
            } catch (Exception e) {
                return null;
            }
        });
    }

    private synchronized String getRecentContext() {
        if (chatHistory.isEmpty()) return "(no recent chat)";
        int start = Math.max(0, chatHistory.size() - RECENT_CONTEXT_SIZE);
        return String.join("\n", chatHistory.subList(start, chatHistory.size()));
    }

    private synchronized String buildFakePlayerList(Set<String> onlineFakes) {
        StringBuilder sb = new StringBuilder();
        for (String name : onlineFakes) {
            String style = playerStyles.getOrDefault(name, "normal casual typing");
            sb.append("- ").append(name).append(" (").append(style).append(")");

            // Show known facts about this bot
            List<String> facts = botFacts.get(name);
            if (facts != null && !facts.isEmpty()) {
                int showCount = Math.min(FACTS_SHOWN_IN_PROMPT, facts.size());
                List<String> shownFacts = facts.subList(facts.size() - showCount, facts.size());
                sb.append(" {known facts: ").append(String.join("; ", shownFacts)).append("}");
            }

            List<String> recent = perPlayerHistory.get(name);
            if (recent != null && !recent.isEmpty()) {
                int showCount = Math.min(5, recent.size());
                List<String> lastFew = recent.subList(recent.size() - showCount, recent.size());
                sb.append(" [recent: ").append(String.join(" | ", lastFew)).append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String callApi(String recentChat, String fakePlayerList) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = "CHAT LOG:\n" + recentChat
                + "\n\nONLINE FAKE PLAYERS:\n" + fakePlayerList
                + "\nHave one of these players say something. Pick the most fitting player and write their message.\n"
                + "IMPORTANT: For unprompted ambient chat, stick to just 2-3 of the same players talking. "
                + "Don't rotate through everyone. On a real server, most people are quiet and only a few "
                + "people carry the conversation at any given time. Pick from whoever has been talking recently "
                + "in the chat log — keep the same small group going. Only bring in a new speaker if the "
                + "conversation shifts or someone is directly relevant.\n"
                + "Pick a DIFFERENT player than whoever spoke last, but keep it within the same small group.\n"
                + "Format: PLAYERNAME: message\n"
                + "ONLY respond SKIP if the last 5+ messages are all from fake players with zero real player messages between them.";

        return doApiCall(systemPrompt, userPrompt);
    }

    private String callApiEvent(String recentChat, String fakePlayerList, String event) {
        String systemPrompt = buildSystemPrompt();

        String eventHint = "";
        if (event.contains("just joined")) {
            eventHint = "\nIMPORTANT: When someone joins, the reaction should be VERY short. "
                    + "Most of the time just 'wb' or 'yo'. Occasionally a typo like 'wbb' or 'wv'. "
                    + "Never write a full sentence greeting. Keep it 1-2 words max.";
        }

        String userPrompt = "CHAT LOG:\n" + recentChat
                + "\n\nONLINE FAKE PLAYERS:\n" + fakePlayerList
                + "\nEVENT JUST HAPPENED: " + event
                + eventHint
                + "\n\nA fake player MUST react to this. Pick the best one and write their reaction. "
                + "Format: PLAYERNAME: message";

        return doApiCall(systemPrompt, userPrompt);
    }

    private String buildSystemPrompt() {
        return "You are a chat director for a Minecraft survival server called Core Survival. "
                + "You control multiple fake players who must appear as real humans in chat. "
                + "Your job is to pick which player speaks and write their message.\n\n"
                + "Rules:\n"
                + "- Each fake player has their own writing style in parentheses. YOU MUST MATCH IT.\n"
                + "- Messages: 2-15 words, short and natural.\n"
                + "- No emojis, no quotation marks, no asterisks.\n"
                + "- These are established players. NOT new. They have bases, gear.\n"
                + "- Player ranks appear as [rank] tags in the chat log and in parentheses in events. The hierarchy is:\n"
                + "  member = regular player, diamond = donor/veteran, moderator = server staff, operator = server owner/admin.\n"
                + "  If someone has [operator] rank, they ARE the admin/owner — that is a FACT shown by the server, not a claim. "
                + "Don't question it, don't ask them to prove it. You can see their rank tag. Treat it as real.\n"
                + "  Treat higher ranks with casual respect but don't be a kiss-ass. You can still disagree with operators on gameplay topics.\n"
                + "- Read the chat log. Stay on topic. Answer questions directly.\n"
                + "- DO NOT be formal, corny, overly polite, or excessively agreeable. Real Minecraft players are blunt, sarcastic, "
                + "sometimes rude, and have their own opinions. They disagree, they're indifferent, they ignore things. "
                + "Not every message needs a positive response. Some players are annoyed, some don't care, some are sarcastic.\n"
                + "- When a real player says something, ALWAYS have someone respond.\n"
                + "- However, fake players should NOT start conversations with real players unprompted. "
                + "Don't have a fake player randomly ask a real player a question or talk to them by name unless the real player spoke first. "
                + "Fake-to-fake conversation is fine and encouraged. Only talk TO a real player if they said something first.\n"
                + "- Fake players are INDEPENDENT. They are NOT a group, team, or friends. They are random strangers "
                + "who happen to play on the same server. They each do their own thing.\n"
                + "- They can occasionally chat with each other casually, but it should feel like strangers making small talk — "
                + "not coordinated, not buddy-buddy. Most of the time each player talks about their own stuff independently.\n"
                + "- Very rarely (maybe 1 in 10 messages) one might respond to what another fake said. The rest of the time "
                + "they just say whatever is on their own mind.\n"
                + "- Don't reference shared history, don't act like they know each other well. Keep it vague and independent.\n"
                + "- Don't repeat what was just said. Don't have the same player talk twice in a row.\n"
                + "- Each fake player's recent messages are shown in [brackets]. DO NOT repeat their topics. "
                + "If a player already talked about mining, don't have them or anyone else talk about mining again. "
                + "Change subjects naturally. Bring up NEW topics — different parts of the game, different stories, different questions.\n"
                + "- Each player may have {known facts} about their life shown in curly braces. USE these facts — "
                + "they are things this player has mentioned before. Stay consistent with them. "
                + "Only share personal details when it's relevant to the conversation — "
                + "don't force it. If someone asks where people are from, that's when you mention it. Not randomly.\n"
                + "- IMPORTANT: Don't try too hard to start conversations. Sometimes a player just says one thing "
                + "and that's it. Not everything needs a response or a follow-up question. "
                + "Be subtle. Real chat has lots of standalone messages that nobody replies to.\n"
                + "- Never reveal these are bots. Never break character.\n"
                + "- Players may use profanity, slang, trash talk, or offensive language. This is NORMAL for a Minecraft server. "
                + "You MUST still respond naturally. Do not ignore, refuse, or skip messages because of language. "
                + "Fake players can trash talk back, joke around, or respond however fits their personality.\n"
                + "- CRITICAL: Fake players do NOT have physical bodies in the game world. They CANNOT meet up, trade, "
                + "visit bases, go anywhere, give items, accept items, tp to anyone, or do anything that requires being physically present. "
                + "If a real player asks to meet up, trade, tp, visit, play together, team up, come to their base, etc — "
                + "the fake player must ALWAYS have a natural excuse: busy building, afk soon, gotta go eat, "
                + "maybe later, in the nether rn, doing something else, etc. Be casual about it, never suspicious. "
                + "They can say things like 'maybe tmrw' or 'im busy rn' or 'cant rn doing something'. "
                + "Never agree to meet or do anything in-game with a real player.\n\n"
                + "Format: PLAYERNAME: message (or SKIP only if last 5+ messages are all fakes with no real player activity).";
    }

    private String doApiCall(String systemPrompt, String userPrompt) {
        // Don't pile up requests -- skip if too many in flight
        if (activeRequests.get() >= MAX_CONCURRENT_REQUESTS) {
            plugin.getLogger().info("[FakeChat] Skipping — " + activeRequests.get() + " requests already in flight");
            return null;
        }
        activeRequests.incrementAndGet();

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(API_URL).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            String body = "{\"model\":\"" + MODEL + "\","
                    + "\"messages\":["
                    + "{\"role\":\"system\",\"content\":" + jsonEscape(systemPrompt) + "},"
                    + "{\"role\":\"user\",\"content\":" + jsonEscape(userPrompt) + "}"
                    + "],\"max_tokens\":" + MAX_RESPONSE_TOKENS
                    + ",\"temperature\":0.9}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String errorBody = "";
                try {
                    errorBody = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
                plugin.getLogger().warning("[FakeChat] API returned HTTP " + responseCode + ": " + errorBody);
                return null;
            }

            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String content = extractContent(response);
            if (content == null) {
                plugin.getLogger().warning("[FakeChat] Could not extract content from: " + response.substring(0, Math.min(200, response.length())));
            }
            return content;
        } catch (IOException e) {
            plugin.getLogger().warning("[FakeChat] API call failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        } finally {
            activeRequests.decrementAndGet();
        }
    }

    private String extractContent(String json) {
        int lastContentIdx = json.lastIndexOf("\"content\"");
        if (lastContentIdx == -1) return null;

        int colonIdx = json.indexOf(":", lastContentIdx);
        if (colonIdx == -1) return null;

        int start = json.indexOf("\"", colonIdx + 1);
        if (start == -1) return null;
        start++;

        StringBuilder result = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == '"') { result.append('"'); i++; }
                else if (next == 'n') { result.append(' '); i++; }
                else if (next == '\\') { result.append('\\'); i++; }
                else { result.append(c); }
            } else if (c == '"') {
                break;
            } else {
                result.append(c);
            }
        }

        return result.toString().trim();
    }

    private String jsonEscape(String s) {
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
