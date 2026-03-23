package com.core.plugin.modules.bots;

/**
 * Centralized constants for the bot system. All magic numbers, thresholds,
 * and timing values that were previously scattered across BotService,
 * BotChatEngine, BotTraits, and BotPool live here.
 */
public final class BotConfig {

    private BotConfig() {}

    // --- Timing (ticks) ---

    public static final long TICKS_PER_SECOND = 20L;

    /** If server restarts within this window, bots rejoin quickly. */
    public static final long QUICK_RESTART_THRESHOLD_MS = 600_000;

    // --- Chat response chances (0-100) ---

    /** Chance bots react to ambient tick. */
    public static final int AMBIENT_CHAT_CHANCE = 8;
    /** Chance bots react to a regular chat message. */
    public static final int REGULAR_CHAT_CHANCE = 10;
    /** Chance bots react to a direct mention or question. */
    public static final int MENTION_CHAT_CHANCE = 20;
    /** Chance bots react to a player death. */
    public static final int DEATH_REACT_CHANCE = 8;
    /** Chance bots react to a returning player joining. */
    public static final int JOIN_REACT_CHANCE = 8;
    /** Chance of a simulated death when no real players are online. */
    public static final int IDLE_DEATH_CHANCE = 1;
    /** Chance to prefer in-window bots when joining. */
    public static final int IN_WINDOW_JOIN_CHANCE = 80;
    /** Chance out-of-window bots leave during a cycle. */
    public static final int OUT_OF_WINDOW_LEAVE_CHANCE = 40;
    /** Chance to prefer out-of-window bots when removing. */
    public static final int OUT_OF_WINDOW_REMOVE_CHANCE = 70;
    /** Chance to add a new bot when in-window bots are available. */
    public static final int IN_WINDOW_ADD_CHANCE = 65;

    // --- Welcome cluster ---

    public static final int WELCOME_MIN_BOTS = 0;
    public static final int WELCOME_MAX_BOTS = 2;
    /** Chance that nobody welcomes a joining player at all (0-100). */
    public static final int WELCOME_SKIP_CHANCE = 60;

    // --- Bot accusation ---

    public static final int ACCUSATION_MIN_RESPONDERS = 0;
    public static final int ACCUSATION_MAX_RESPONDERS = 1;
    /** Chance that bots completely ignore a bot accusation (0-100). */
    public static final int ACCUSATION_IGNORE_CHANCE = 70;

    // --- Group message ---

    public static final int GROUP_MIN_RESPONDERS = 1;
    public static final int GROUP_MAX_RESPONDERS = 2;

    // --- Session (minutes) ---

    /** 5% chance of a marathon session (8-12 hours). */
    public static final int MARATHON_CHANCE = 5;
    public static final int MARATHON_MIN_MINUTES = 480;
    public static final int MARATHON_MAX_MINUTES = 720;

    // --- Cycle skip ---

    /** 1 in 3 cycles are skipped for natural variation. */
    public static final int CYCLE_SKIP_CHANCE = 3;

    // --- Traits ---

    public static final double DIAMOND_CHANCE = 0.03;
    public static final int POOL_MULTIPLIER = 3;

    // --- Delays (ticks) ---

    public static final int AMBIENT_MIN_INTERVAL = 6000;
    public static final int AMBIENT_MAX_INTERVAL = 18000;
    public static final int ROTATION_MIN_INTERVAL = 144000;
    public static final int ROTATION_MAX_INTERVAL = 288000;
    public static final int VOTE_MIN_INTERVAL = 3600;
    public static final int VOTE_MAX_INTERVAL = 9600;
    public static final int VOTE_FIRST_MIN_DELAY = 6000;
    public static final int VOTE_FIRST_MAX_DELAY = 12000;

    // --- Vote ---

    public static final int VOTE_CLUSTER_MIN = 1;
    public static final int VOTE_CLUSTER_MAX = 3;
    public static final double VOTE_MAX_CHANCE_PER_CHECK = 0.5;
    public static final int VOTE_CHECKS_PER_HOUR = 10;

    // --- Thinking simulation (reading + processing before typing) ---

    /** Minimum "thinking" delay in ticks before a bot starts typing a response. */
    public static final int THINK_MIN_TICKS = 30;  // 1.5 seconds
    /** Maximum "thinking" delay in ticks. */
    public static final int THINK_MAX_TICKS = 100; // 5 seconds

    // --- Typing simulation ---

    /** Base delay in ticks per character of message length. */
    public static final double TICKS_PER_CHARACTER = 0.8;
    /** Minimum typing delay in ticks (even for "lol"). */
    public static final int MIN_TYPING_DELAY = 15;
    /** Maximum typing delay in ticks. */
    public static final int MAX_TYPING_DELAY = 160;
    /** Random jitter range added to typing delay (ticks). */
    public static final int TYPING_JITTER = 20;

    // --- Conversation fatigue ---

    /** Time window in millis to track recent bot messages for fatigue. */
    public static final long FATIGUE_WINDOW_MS = 120_000; // 2 minutes
    /** Max messages a bot can send within the fatigue window before going quiet. */
    public static final int FATIGUE_MAX_MESSAGES = 2;

    // --- Natural silence ---

    /** If no real player has chatted for this long (ms), bots mostly shut up. */
    public static final long SILENCE_THRESHOLD_MS = 180_000; // 3 minutes
    /** Tiny chance of ambient chat during silence periods (0-100). */
    public static final int SILENCE_AMBIENT_CHANCE = 5;
    /** Self-initiated messages only happen if a real player chatted within this window (ms). */
    public static final long SELF_INITIATE_RECENCY_MS = 120_000; // 2 minutes

    // --- AFK / Silence windows ---

    /** Chance per ambient tick that an online bot enters a silence window (0-100). */
    public static final int AFK_START_CHANCE = 5;
    /** Minimum silence duration in ticks. */
    public static final int AFK_MIN_DURATION = 3600; // 3 minutes
    /** Maximum silence duration in ticks. */
    public static final int AFK_MAX_DURATION = 18000; // 15 minutes
    /** Chance a bot says "brb" or similar before going silent. */
    public static final int AFK_ANNOUNCE_CHANCE = 10;
    /** Chance a bot says "back" or similar when returning. */
    public static final int AFK_RETURN_ANNOUNCE_CHANCE = 15;

    // --- Self-initiated messages ---

    /** Chance per ambient tick that a bot initiates its own message (0-100). */
    public static final int SELF_INITIATE_CHANCE = 5;

    // --- Bot-to-bot interaction ---

    /** Chance that a bot message triggers another bot to respond (0-100). */
    public static final int BOT_TO_BOT_CHANCE = 8;
    /** Max bot-to-bot exchanges per conversation to prevent loops. */
    public static final int BOT_TO_BOT_MAX_CHAIN = 2;

    // --- Peak hours ---

    /** Hour weights for join probability. Index = hour of day (0-23). Higher = more likely to join. */
    public static final int[] PEAK_HOUR_WEIGHTS = {
        // 00  01  02  03  04  05  06  07  08  09  10  11
            5,  3,  2,  2,  2,  3,  5, 10, 15, 20, 25, 30,
        // 12  13  14  15  16  17  18  19  20  21  22  23
           35, 35, 40, 50, 60, 65, 70, 70, 65, 55, 40, 20,
    };

    // --- Personality response multipliers ---

    /** Multiplier for response chance based on personality type.
     *  Applied to REGULAR_CHAT_CHANCE, MENTION_CHAT_CHANCE, etc. */
    public static final double PERSONALITY_QUIET_MULT = 0.25;
    public static final double PERSONALITY_CASUAL_MULT = 0.8;
    public static final double PERSONALITY_SOCIAL_MULT = 1.3;
    public static final double PERSONALITY_TRYHARD_MULT = 1.0;
    public static final double PERSONALITY_CHILL_MULT = 0.7;

    // --- Chat context ---

    public static final int MAX_CHAT_HISTORY = 40;
    public static final int MAX_PER_PLAYER_HISTORY = 12;
    public static final int MAX_FACTS_PER_BOT = 15;

    // --- Stat growth (per tick = every 10 minutes while online) ---

    /** Interval in ticks between stat growth ticks. 10 minutes = 12,000 ticks. */
    public static final long STAT_GROWTH_INTERVAL_TICKS = 12_000L;

    /**
     * Base stat gains per growth tick (min, max). Each personality applies a multiplier.
     * Values are intentionally small so growth is gradual and organic.
     */
    public static final int[] KILLS_PER_TICK = {0, 2};
    public static final int[] DEATHS_PER_TICK = {0, 2};
    public static final int[] BLOCKS_BROKEN_PER_TICK = {0, 25};
    public static final int[] BLOCKS_PLACED_PER_TICK = {0, 15};
    public static final int[] MOBS_KILLED_PER_TICK = {0, 8};
    public static final int[] FISH_CAUGHT_PER_TICK = {0, 3};

    /**
     * Personality multipliers for stat growth. Controls how fast each type accumulates stats.
     * QUIET bots barely grow; TRYHARD bots grow fast.
     */
    public static final double GROWTH_MULT_QUIET = 0.2;
    public static final double GROWTH_MULT_CASUAL = 0.6;
    public static final double GROWTH_MULT_SOCIAL = 0.5;
    public static final double GROWTH_MULT_TRYHARD = 1.0;
    public static final double GROWTH_MULT_CHILL = 0.4;

    /**
     * Chance (0-100) that a given stat produces zero gain on any tick,
     * even when the roll would otherwise be positive. Creates lumpy, human-like curves.
     */
    public static final int ZERO_GAIN_CHANCE = 30;

    /**
     * Absolute stat ceilings for bots. No bot can exceed these values regardless of age.
     * Set conservatively so bots never dominate leaderboards.
     */
    public static final long CAP_KILLS = 200;
    public static final long CAP_DEATHS = 300;
    public static final long CAP_BLOCKS_BROKEN = 40_000;
    public static final long CAP_BLOCKS_PLACED = 25_000;
    public static final long CAP_MOBS_KILLED = 2_000;
    public static final long CAP_FISH_CAUGHT = 500;
    public static final long CAP_PLAY_TIME = 15_000; // ~10 days

    // --- Death scenarios ---

    public static final String[][] DEATH_SCENARIOS = {
            {"death.fall", "false"},
            {"death.drowning", "false"},
            {"death.fire", "false"},
            {"death.lava", "false"},
            {"death.starvation", "false"},
            {"death.explosion", "false"},
            {"death.freeze", "false"},
            {"death.mob", "true"},
    };

    public static final String[] MOB_NAMES = {
            "Zombie", "Skeleton", "Creeper", "Spider", "Enderman",
            "Witch", "Drowned", "Pillager", "Vindicator", "Warden"
    };
}
