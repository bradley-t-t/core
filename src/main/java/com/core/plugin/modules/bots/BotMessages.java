package com.core.plugin.modules.bots;

import java.util.Random;

/**
 * Single source of truth for all bot response pools. Deduplicates messages
 * that were previously scattered across BotService, BotTraits, and MessageCommand.
 */
public final class BotMessages {

    private static final Random RANDOM = new Random();

    private BotMessages() {}

    // --- Welcome cluster ---

    public static final String[] WELCOME = {
            "Welcome!", "welcome!", "Welcome!!", "welcome!!", "wb!", "welcomee",
            "hey welcome!", "ayy welcome", "Welcome :)", "hii welcome",
            "yoo welcome!", "heyo welcome", "hey!", "welcoome", "welcome dude",
    };

    // --- Fallback DM replies (when AI is unavailable) ---

    public static final String[] FALLBACK_DM = {
            "hey", "whats up", "yeah?", "hm?", "one sec",
            "busy rn", "oh hey", "sup", "lol what", "?",
            "cant talk rn", "who is this", "hey whats up",
    };

    // --- Writing styles for AI personality ---

    public static final String[] WRITING_STYLES = {
            "types in all lowercase, no punctuation, short sentences",
            "uses proper grammar and capitalization but keeps messages brief",
            "types fast with occasional typos and abbreviations like 'rn' 'tbh' 'ngl'",
            "uses lots of slang and internet speak, says 'lol' 'lmao' 'fr' 'bro'",
            "writes in fragments, one thought per message, very casual",
            "uses emotes like :) :D xD and is generally upbeat",
            "dry humor, deadpan responses, says things matter-of-factly",
            "enthusiastic about the game, uses exclamation marks, talks about builds and progress",
    };

    // --- Bot accusation keywords ---

    public static final String[] ACCUSATION_KEYWORDS = {
            "bot", "npc", "fake player", "fake account", "not real",
    };

    // --- Group address keywords ---

    public static final String[] GROUP_ADDRESS_KEYWORDS = {
            "everyone", "every one", "hey all", "anybody",
            "anyone", "you guys", "yall", "y'all",
    };

    // --- Question starters ---

    public static final String[] QUESTION_STARTERS = {
            "how", "what", "where", "why", "who",
            "can ", "does ", "is there", "do you",
    };

    // --- AFK announce/return ---

    public static final String[] AFK_START = {
            "brb", "one sec", "afk", "brb real quick", "gotta go for a sec",
            "hold on", "brb eating", "afk for a bit",
    };

    public static final String[] AFK_RETURN = {
            "back", "im back", "ok back", "back!", "sorry im back",
            "ok im here", "hey im back",
    };

    // --- Self-initiated (things bots say about what they're "doing") ---

    public static final String[] SELF_INITIATE = {
            "just found a cave with a ton of iron", "my base is finally coming together",
            "does anyone have extra wood", "this biome is beautiful honestly",
            "just made a full set of diamond tools", "i need to find a village",
            "anyone know where there's a desert nearby", "my farm is producing so much wheat",
            "just died to a creeper lol", "finally got mending on my pickaxe",
            "building a bridge to that island", "how do you guys organize your storage",
            "i keep getting lost in caves", "found a spawner near my base",
            "just tamed a wolf!", "need more coal", "the nether is terrifying",
            "making a mob grinder", "this server is pretty fun ngl",
            "anyone want to trade", "where do you guys find diamonds",
            "just enchanted my armor", "my house is so ugly lmao",
            "should i build in the plains or the forest", "exploring is so fun",
    };

    /** Pick a random message from the given pool. */
    public static String random(String[] pool) {
        return pool[RANDOM.nextInt(pool.length)];
    }
}
