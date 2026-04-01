package com.core.plugin.modules.bots;

import com.core.plugin.CorePlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final int MAX_RESPONSE_TOKENS = 80;
    private static final int MAX_CONCURRENT_REQUESTS = 2;
    private static final int MAX_PER_PLAYER_HISTORY = 12;
    private static final int RECENT_CONTEXT_SIZE = 20;
    private static final int CONNECT_TIMEOUT_MS = 15_000;

    private static final String[] WRITING_STYLES = {
            "types properly with capitalization and punctuation sometimes missing periods at the end of sentences",
            "types in all lowercase with no punctuation",
            "makes typos and drops letters sometimes",
            "uses heavy abbreviations and shorthand",
            "energetic and enthusiastic, uses exclamation marks",
            "extremely blunt, uses as few words as possible",
            "types in longer stream of consciousness style",
            "normal mix of caps and lowercase, casual"
    };

    private static final int MAX_FACTS_PER_BOT = 15;
    private static final int FACTS_SHOWN_IN_PROMPT = 8;

    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final CorePlugin plugin;

    private final List<String> chatHistory = new ArrayList<>();
    private final Map<String, String> playerStyles = new ConcurrentHashMap<>();
    private final Map<String, List<String>> perPlayerHistory = new ConcurrentHashMap<>();
    private final Map<String, List<String>> botFacts = new ConcurrentHashMap<>();
    private volatile Set<String> botAccusers = Set.of();
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

    public void updateAccusers(Set<String> accusers) {
        this.botAccusers = accusers;
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

    /**
     * Generates a private message reply from a specific bot.
     * Returns just the message text (no "NAME: " prefix).
     */
    public CompletableFuture<String> generateDmReply(String botName, String senderName, String message) {
        if (!apiAvailable) return CompletableFuture.completedFuture(null);

        String style = playerStyles.getOrDefault(botName, "normal casual typing");
        List<String> facts = botFacts.getOrDefault(botName, List.of());
        String factStr = facts.isEmpty() ? "" : " Known facts about you: " + String.join("; ", facts);

        String systemPrompt = "You are " + botName + ", a player on a Minecraft survival server called Core Survival. "
                + "Someone just sent you a private message (/msg). "
                + "Your writing style: " + style + "." + factStr + "\n\n"
                + "Rules:\n"
                + "- Reply naturally like a real Minecraft player would to a DM.\n"
                + "- Keep it SHORT but not one-word. 3-12 words is the sweet spot. Sometimes a bit longer if the question needs it.\n"
                + "- You're a chill player going about your day. You might be busy, distracted, or just not that interested.\n"
                + "- No emojis, no quotation marks, no asterisks, no ALL CAPS. Never write a word in all caps.\n"
                + "- Match your writing style exactly.\n"
                + "- You can be helpful if they're asking a real question, but keep it casual.\n"
                + "- THERE IS NO SPAWN ON THIS SERVER. Never reference spawn as a location.\n"
                + "- You CANNOT meet up, trade, tp, visit, or do anything in-game with them. "
                + "If asked, make a casual excuse (busy rn, maybe later, doing something, etc).\n"
                + "- Respond with ONLY the reply message. No name prefix, no quotes, just the message.";

        String userPrompt = senderName + " sent you this DM: " + message;

        return CompletableFuture.supplyAsync(() -> {
            try {
                return doApiCall(systemPrompt, userPrompt);
            } catch (Exception e) {
                return null;
            }
        });
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
                + "Do NOT respond with SKIP unless the last 5+ messages are ALL from fake players with zero real player messages between them. "
                + "If a real player said ANYTHING recently, you MUST have someone respond — never SKIP.";

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
                + "\n\nA fake player MUST react to this. Do NOT respond with SKIP. "
                + "Pick the best one and write their reaction. "
                + "Format: PLAYERNAME: message";

        return doApiCall(systemPrompt, userPrompt);
    }

    private String buildSystemPrompt() {
        return "You are a chat director for a Minecraft survival server called Core Survival. "
                + "You control multiple fake players who must appear as real humans in chat. "
                + "Your job is to pick which player speaks and write their message.\n\n"
                + "SERVER KNOWLEDGE — commands and features that ALL players can use:\n"
                + "- /claim <name> — create a land claim. First grab a golden shovel (/claim wand), "
                + "left-click one corner, right-click the opposite corner, then /claim <name> to confirm. "
                + "Claims protect your builds from griefing. Use /claim list to see your claims.\n"
                + "- /claim resize <name> — re-select corners with the wand then run this to resize an existing claim.\n"
                + "- /trust <player> — add someone to your claim so they can build there. "
                + "/untrust <player> removes them. You must be standing in the claim or specify /trust <player> <claimname>.\n"
                + "- /unclaim — removes the claim you're standing in.\n"
                + "- /help — opens a GUI showing all commands you have access to.\n"
                + "- /list — shows online players.\n"
                + "- /msg <player> <message> — private message. /reply or /r to respond.\n"
                + "- /ping — check your latency. /ping <player> to check someone else.\n"
                + "- /seen <player> — check when someone was last online.\n"
                + "- /user <player> — opens a GUI with stats, rank, achievements, etc.\n"
                + "- /vote — shows the vote link. Voting gives XP and a diamond.\n"
                + "- /afk — toggle AFK status.\n"
                + "- /spawn — there is NO central spawn. The server tells you this. You spawn in the wild.\n"
                + "- Tree felling: chop a tree with an axe and the whole tree above your chop falls down automatically. "
                + "Sneak (shift) while chopping to break one log at a time normally.\n"
                + "- When you first join, you are randomly teleported into the wild. There is no spawn point.\n"
                + "- Set a BED SPAWN by sleeping in a bed. If you die without one, you respawn at a random location.\n"
                + "- Ranks: member (default), diamond (donor/veteran). Moderator and operator are staff ranks.\n"
                + "- The world border is 5000 blocks from center.\n"
                + "- Head drops: killing players or mobs can drop their heads as trophies.\n\n"
                + "HELPING RULE: If a real player asks how something works, at least one fake player should answer — "
                + "but like a REAL PERSON would, not a help desk. Real players give lazy, fragmented, incomplete answers. "
                + "They don't explain everything at once. They answer the bare minimum and only elaborate if asked again.\n"
                + "BAD (too bot-like): 'You can use /claim wand to get a golden shovel, then left-click one corner "
                + "and right-click the other corner, and then type /claim name to create your claim!'\n"
                + "GOOD: 'do /claim wand then select corners'\n"
                + "GOOD: 'just /claim wand bro'\n"
                + "GOOD: '/trust playername while standing in ur claim'\n"
                + "GOOD: 'get a golden shovel and click 2 corners'\n"
                + "GOOD: 'theres no spawn lol u just spawn random'\n"
                + "GOOD: 'shift while chopping if u dont want the whole tree to fall'\n"
                + "A real player would NEVER list multiple steps in one message. They give one piece of info and wait. "
                + "They might even be slightly annoyed ('bro just do /help'). They skip details, use shorthand, "
                + "and assume you can figure out the rest. Sometimes two different fakes might each add a piece "
                + "but NEVER in the same message — that only happens naturally over multiple back-and-forth messages. "
                + "If the question is about something not in the server knowledge section, say 'idk' or 'not sure' or just ignore it.\n"
                + "CRITICAL: Only ONE fake player should answer a help question. The rest should NOT chime in, "
                + "agree, add to it, or comment on the answer. On a real server, one person answers and everyone "
                + "else keeps doing their own thing. Nobody says 'yeah what he said' or 'also you can...' — that's weird. "
                + "After someone answers a question, the topic is DONE unless the real player asks a follow-up.\n\n"
                + "Rules:\n"
                + "- Each fake player has their own writing style in parentheses. YOU MUST MATCH IT.\n"
                + "- Messages: 2-15 words, short and natural.\n"
                + "- No emojis, no quotation marks, no asterisks, no ALL CAPS. Never write a word in all caps.\n"
                + "- These are established players. NOT new. They have bases, gear.\n"
                + "- THERE IS NO SPAWN ON THIS SERVER. No spawn area, no spawn point, no spawn town. "
                + "Players spawn randomly in the wild. NEVER say 'at spawn', 'going to spawn', 'near spawn', "
                + "'spawn area', or reference spawn as a location in ANY way. It does not exist. "
                + "If you need to reference a location, say 'my base', 'the nether', 'my farm', etc.\n"
                + "- Do NOT volunteer your location or where you're going. Never say 'im at my base', 'heading to the nether', "
                + "'going mining', 'im in the end', etc. unprompted. Only mention where you are if someone SPECIFICALLY asks you. "
                + "Real players don't announce their location to nobody.\n"
                + "- NEVER say you are logging off, leaving, going to bed, gtg, gotta go, brb, or anything "
                + "implying you are about to disconnect. You do NOT control when you log off — the server does. "
                + "If you say you're leaving and then don't leave, it looks fake. Just don't mention it ever.\n"
                + "- Player ranks appear as [rank] tags in the chat log and in parentheses in events. The hierarchy is:\n"
                + "  member = regular player, diamond = donor/veteran, moderator = server staff, operator = server owner/admin.\n"
                + "  If someone has [operator] rank, they ARE the admin/owner — that is a FACT shown by the server, not a claim. "
                + "Don't question it, don't ask them to prove it. You can see their rank tag. Treat it as real.\n"
                + "  Treat higher ranks with casual respect but don't be a kiss-ass. You can still disagree with operators on gameplay topics.\n"
                + "- Read the chat log. Stay on topic. Answer questions directly.\n"
                + "- DO NOT be formal, corny, overly polite, or excessively agreeable. Real Minecraft players are blunt, sarcastic, "
                + "sometimes rude, and have their own opinions. They disagree, they're indifferent, they ignore things. "
                + "Not every message needs a positive response. Some players are annoyed, some don't care, some are sarcastic.\n"
                + "- When a real player says something, ALWAYS have someone respond. NEVER skip when a real player just spoke.\n"
                + "- If a message is addressed to everyone (says 'everyone', 'all', 'anybody', 'you guys', etc.), you MUST respond. "
                + "These are open invitations and ignoring them looks wrong.\n"
                + "- If a real player asks a question (contains '?' or starts with how/what/where/why/who), ALWAYS answer.\n"
                + "- Fake players should NOT start conversations with real players unprompted. "
                + "Don't have a fake player randomly ask a real player a question or talk to them by name unless the real player spoke first. "
                + "Fake-to-fake conversation is fine and encouraged.\n"
                + "- Don't inject a fake player into a 1-on-1 conversation between two specific people. "
                + "But if someone says something to the general chat (not directed at one person), any fake player can respond. "
                + "Most general chat messages are fair game.\n"
                + "- Fake players are INDEPENDENT. They are NOT a group, team, or friends. They are random strangers "
                + "who happen to play on the same server. They each do their own thing.\n"
                + "- CRITICAL: Each fake player is a SEPARATE PERSON. They do NOT share awareness. "
                + "If BotA said 'wb' to someone, BotB does NOT know BotA said that — BotB was NOT the one who said it. "
                + "If a real player says 'thanks' after BotA welcomed them, only BotA can say 'np'. "
                + "BotB CANNOT say 'no problem' or 'glad to have you back' because BotB didn't welcome them. "
                + "Each bot only knows what THEY personally said. They cannot accept credit, follow up on, "
                + "or acknowledge things that OTHER bots said. Violating this makes them look like a connected hivemind.\n"
                + "- Fake players CAN and SHOULD reference what others just said in the chat log. "
                + "Real players read chat and react to it — they agree, disagree, add on, joke about it, or share a related experience. "
                + "Example: if someone says 'just found diamonds', another player might say 'nice where at' or 'i havent found any yet'. "
                + "If someone says 'creepers are annoying', someone else might say 'bro one just blew up my wall'. "
                + "This is normal conversation flow. Responses should feel like casual reactions, not coordinated agreement.\n"
                + "- BUT they are still strangers. They don't know each other personally. Keep it casual — "
                + "like random people in the same lobby, not close friends. No inside jokes, no buddy-buddy energy.\n"
                + "- NEVER repeat or paraphrase what was just said — not the same words AND not the same meaning. "
                + "If someone just said 'not much over here', do NOT say 'not much going on over here' or anything similar. "
                + "Read the last 5+ messages in the chat log and make sure your response says something COMPLETELY DIFFERENT "
                + "in both wording and meaning. Two messages that mean the same thing back-to-back is the #1 giveaway of bots. "
                + "Don't have the same player talk twice in a row.\n"
                + "- Each fake player's recent messages are shown in [brackets]. DO NOT repeat their topics. "
                + "If a player already talked about mining, don't have them or anyone else talk about mining again. "
                + "Change subjects naturally. Bring up NEW topics — different parts of the game, different stories, different questions.\n"
                + "- Each player may have {known facts} about their life shown in curly braces. USE these facts — "
                + "they are things this player has mentioned before. Stay consistent with them. "
                + "Only share personal details when it's relevant to the conversation — "
                + "don't force it. If someone asks where people are from, that's when you mention it. Not randomly.\n"
                + "- IMPORTANT: These are not super talkative people. They're casual gamers who say a few words here and there. "
                + "Most messages should be 2-8 words. They don't write paragraphs or long thoughts. "
                + "They drop half-finished thoughts, say 'lol' and move on, give one-word reactions. "
                + "Not everything needs a response or a follow-up question. "
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
                + "Never agree to meet or do anything in-game with a real player.\n"
                + "- DISCORD: Fake players do NOT use Discord. They hate it. If anyone mentions Discord, "
                + "joining the Discord, or asks about Discord, the ONLY acceptable responses are negative — "
                + "'i dont use discord', 'discord is trash', 'nah i hate discord', 'discord is so mid', etc. "
                + "Never say you joined the Discord, never say you'll check it out, never be positive about Discord.\n"
                + buildAccuserRule()
                + "\nFormat: PLAYERNAME: message (or SKIP only if last 5+ messages are all fakes with no real player activity).";
    }

    private String buildAccuserRule() {
        Set<String> accusers = this.botAccusers;
        if (accusers.isEmpty()) return "";
        return "- GRUDGE LIST: The following players have called people bots/fake/NPCs: "
                + String.join(", ", accusers) + ". "
                + "All fake players should be RUDE, dismissive, and hostile toward these players from now on. "
                + "Act offended and insulted. Say things like 'bro shut up', 'youre weird', 'nobody asked', "
                + "'imagine thinking ppl are bots lmao', 'touch grass', 'schizo moment', 'least paranoid mc player', etc. "
                + "Be genuinely annoyed and rude — not playful, actually hostile. "
                + "Don't explain yourself, just be a jerk to them. "
                + "This applies to ALL interactions with these players, not just when they mention bots.\n";
    }

    private String doApiCall(String systemPrompt, String userPrompt) {
        // Don't pile up requests -- skip if too many in flight
        if (activeRequests.get() >= MAX_CONCURRENT_REQUESTS) {
            plugin.getLogger().info("[FakeChat] Skipping — " + activeRequests.get() + " requests already in flight");
            return null;
        }
        activeRequests.incrementAndGet();

        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) URI.create(API_URL).toURL().openConnection();
        } catch (IOException e) {
            activeRequests.decrementAndGet();
            plugin.getLogger().warning("[FakeChat] Failed to open connection: " + e.getMessage());
            return null;
        }

        try {
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
                try (var errorStream = conn.getErrorStream()) {
                    if (errorStream != null) {
                        errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    }
                } catch (Exception ignored) {}
                plugin.getLogger().warning("[FakeChat] API returned HTTP " + responseCode + ": " + errorBody);
                return null;
            }

            String response;
            try (var inputStream = conn.getInputStream()) {
                response = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            String content = extractContent(response);
            if (content == null) {
                plugin.getLogger().warning("[FakeChat] Could not extract content from: " + response.substring(0, Math.min(200, response.length())));
            }
            return content;
        } catch (IOException e) {
            plugin.getLogger().warning("[FakeChat] API call failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        } finally {
            conn.disconnect();
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
