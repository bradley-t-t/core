package com.core.plugin.bots;

import com.core.plugin.CorePlugin;
import com.core.plugin.rank.Rank;
import com.core.plugin.rank.RankLevel;
import com.core.plugin.rank.RankService;
import com.core.plugin.util.MessageUtil;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assigns and stores deterministic traits for fake players:
 * rank, personality, play schedule, and writing style.
 * All assignments are derived from the name's hash for consistency across restarts.
 */
public final class BotTraits {

    private static final double DIAMOND_CHANCE = 0.15;

    /** Personality distribution weights (quiet, casual, social, tryhard, chill). Must sum to ~100. */
    private static final int[] PERSONALITY_WEIGHTS = {15, 35, 20, 15, 15};

    /**
     * Personality types that determine how a fake player behaves in chat.
     * Each has a chat frequency weight, preferred message pools, and reaction likelihood.
     */
    enum Personality {
        QUIET(5, 10, 8,
                new String[]{".", "k", "ok", "mhm", "ye"},
                new String[]{"rip", "F", "oof"},
                new String[]{"hey"},
                new String[]{"k", "ye", "mhm"}),

        CASUAL(20, 30, 15,
                new String[]{"lol", "gg", "nice", "brb", "back", "true", "oh"},
                new String[]{"rip", "lol", "oof", "that sucks", "nooo"},
                new String[]{"hey", "yo", "hi", "wb"},
                new String[]{"yeah", "true", "lol", "nice", "idk", "same"}),

        SOCIAL(35, 50, 25,
                new String[]{"my base is looking good", "anyone wanna explore together",
                        "just got back from mining", "this server is pretty chill",
                        "anyone else getting wrecked by mobs", "i love this area",
                        "how long has this server been up", "need to find a good spot for a base"},
                new String[]{"rip lmao", "how did that happen", "unlucky bro", "nooo haha",
                        "thats rough", "what killed you"},
                new String[]{"hey welcome back", "yooo whats up", "hi how are you"},
                new String[]{"oh ok", "wait really", "thats crazy", "no way",
                        "fr", "agreed", "haha yeah"}),

        TRYHARD(30, 25, 20,
                new String[]{"anyone wanna fight", "got full diamond", "gg ez",
                        "whos the best pvper here", "1v1?", "just enchanted my sword",
                        "heading to the nether for blaze rods", "need xp"},
                new String[]{"L", "gg", "how", "get good", "lmao"},
                new String[]{"yo", "sup"},
                new String[]{"facts", "true", "nah", "bet", "cap"}),

        CHILL(25, 20, 20,
                new String[]{"just vibing at my base", "this is peaceful",
                        "need to fix my farm", "my base keeps flooding lol",
                        "villagers are so dumb", "forgot where i died",
                        "almost done with my storage room", "enchanting is so rng",
                        "need more bookshelves", "why are phantoms a thing"},
                new String[]{"oh no", "rip dude", "happens to the best of us", "unlucky"},
                new String[]{"hey", "hello", "yo whats good"},
                new String[]{"oh ok", "thx", "ty", "oh i see", "makes sense"});

        final int ambientWeight;
        final int deathReactWeight;
        final int chatReactWeight;
        final String[] ambientMessages;
        final String[] deathReactions;
        final String[] joinReactions;
        final String[] chatReactions;

        Personality(int ambientWeight, int deathReactWeight, int chatReactWeight,
                    String[] ambientMessages, String[] deathReactions,
                    String[] joinReactions, String[] chatReactions) {
            this.ambientWeight = ambientWeight;
            this.deathReactWeight = deathReactWeight;
            this.chatReactWeight = chatReactWeight;
            this.ambientMessages = ambientMessages;
            this.deathReactions = deathReactions;
            this.joinReactions = joinReactions;
            this.chatReactions = chatReactions;
        }
    }

    /**
     * Schedule window -- the hours of the day a fake player prefers to be online.
     * Each player gets a start hour and a duration. They are more likely to join
     * during their window and leave outside it.
     */
    record PlaySchedule(int startHour, int durationHours) {
        boolean isInWindow() {
            int currentHour = LocalTime.now().getHour();
            int endHour = (startHour + durationHours) % 24;
            if (startHour < endHour) {
                return currentHour >= startHour && currentHour < endHour;
            }
            return currentHour >= startHour || currentHour < endHour;
        }
    }

    private final CorePlugin plugin;
    private final Map<String, String> ranks = new ConcurrentHashMap<>();
    private final Map<String, Personality> personalities = new ConcurrentHashMap<>();
    private final Map<String, PlaySchedule> schedules = new ConcurrentHashMap<>();

    public BotTraits(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Assign traits to all names in the pool. Clears previous assignments. */
    public void assignAll(List<String> names, BotChatEngine chatEngine) {
        ranks.clear();
        personalities.clear();
        schedules.clear();

        for (String name : names) {
            assignSingle(name, chatEngine);
        }
    }

    /** Assign traits to a single name (used during pool rotation). */
    public void assignSingle(String name, BotChatEngine chatEngine) {
        int hash = Math.abs(name.hashCode());

        ranks.put(name, (hash % 100) < (DIAMOND_CHANCE * 100) ? "diamond" : "member");
        personalities.put(name, pickPersonality(hash));

        int startHour = (hash / 10000) % 24;
        int duration = 4 + ((hash / 240000) % 11);
        int dayVariance = (LocalDate.now().getDayOfYear() + hash) % 3 - 1;
        schedules.put(name, new PlaySchedule((startHour + dayVariance + 24) % 24, duration));

        if (chatEngine != null) {
            chatEngine.assignStyle(name);
            chatEngine.loadFacts(name);
        }
    }

    /** Remove all trait data for a retired name. */
    public void remove(String name) {
        ranks.remove(name);
        personalities.remove(name);
        schedules.remove(name);
    }

    public String getRank(String name) {
        return ranks.getOrDefault(name, "member");
    }

    public Personality getPersonality(String name) {
        return personalities.getOrDefault(name, Personality.CASUAL);
    }

    public PlaySchedule getSchedule(String name) {
        return schedules.get(name);
    }

    public boolean isInWindow(String name) {
        PlaySchedule schedule = schedules.get(name);
        return schedule != null && schedule.isInWindow();
    }

    /** Build the colorized display name (rank prefix + name) for tab/chat. */
    public String getDisplayName(String name) {
        RankService rankService = plugin.services().get(RankService.class);
        if (rankService == null) return name;

        RankLevel level = RankLevel.fromString(getRank(name));
        if (level == null) level = RankLevel.MEMBER;

        Rank rank = rankService.getDisplayConfig(level);
        if (rank == null) return name;

        return MessageUtil.colorize(rank.displayPrefix() + " " + name);
    }

    /** Resolve rank display prefix and chat color for a fake player. */
    public RankDisplay resolveRankDisplay(String name) {
        RankService rankService = plugin.services().get(RankService.class);
        if (rankService == null) return RankDisplay.DEFAULT;

        RankLevel level = RankLevel.fromString(getRank(name));
        if (level == null) return RankDisplay.DEFAULT;

        Rank rank = rankService.getDisplayConfig(level);
        if (rank == null) return RankDisplay.DEFAULT;

        return new RankDisplay(rank.displayPrefix(), rank.chatColor());
    }

    public record RankDisplay(String prefix, String chatColor) {
        public static final RankDisplay DEFAULT = new RankDisplay("", "&f");
    }

    private static Personality pickPersonality(int hash) {
        Personality[] types = Personality.values();
        int roll = (hash / 100) % 100;
        int cumulative = 0;
        for (int i = 0; i < types.length && i < PERSONALITY_WEIGHTS.length; i++) {
            cumulative += PERSONALITY_WEIGHTS[i];
            if (roll < cumulative) return types[i];
        }
        return Personality.CASUAL;
    }
}
