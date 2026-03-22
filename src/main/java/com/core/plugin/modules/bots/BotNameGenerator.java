package com.core.plugin.modules.bots;

import java.util.Random;

/**
 * Generates realistic Minecraft-style usernames with variety.
 * Produces a mix of clean names, gamer tags, stylized names, and name+number combos.
 */
public final class BotNameGenerator {

    private static final String[] CLEAN_PREFIXES = {
            "Shadow", "Frost", "Dark", "Storm", "Iron", "Silver", "Ash", "Ember",
            "Void", "Dusk", "Haze", "Cobalt", "Raven", "Onyx", "Jade", "Obsidian",
            "Crimson", "Phantom", "Drift", "Hollow", "Blaze", "Crystal", "Venom",
            "Thorn", "Rogue", "Wraith", "Ghost", "Eclipse", "Tempest", "Nova",
    };

    private static final String[] CLEAN_SUFFIXES = {
            "fall", "born", "strike", "blade", "wolf", "fire", "vex", "core",
            "byte", "dawn", "edge", "run", "fang", "claw", "flux", "mind",
            "drift", "vale", "rift", "bane", "keep", "forge", "stone", "helm",
    };

    private static final String[] SHORT_NAMES = {
            "Jinx", "Hex", "Vex", "Nyx", "Flux", "Dusk", "Rift", "Haze",
            "Veil", "Mist", "Pike", "Rook", "Wren", "Sage", "Cade", "Knox",
            "Lynx", "Blitz", "Gloom", "Kova", "Zyn", "Tarn", "Vale", "Reave",
    };

    private static final String[] FIRST_NAMES = {
            "Jake", "Mia", "Noah", "Emma", "Liam", "Ava", "Owen", "Lily",
            "Ryan", "Ella", "Alex", "Zoe", "Dan", "Jess", "Sam", "Ben",
            "Max", "Kai", "Leo", "Ivy", "Ash", "Finn", "Cole", "Aria",
            "Luke", "Maya", "Theo", "Nina", "Ethan", "Ruby",
    };

    private static final String[] TAG_SUFFIXES = {
            "_mc", "_pvp", "_hd", "_04", "_07", "_irl", "_plays", "_xo",
            "_2k", "_v2", "_99", "MC", "_vv", "_3x", "_09",
    };

    private static final String[] GAMER_PREFIXES = {
            "iTz", "Not", "Lil", "x", "Its", "Da", "Mr", "iix",
    };

    private static final String[] STYLIZED_PARTS = {
            "vxid", "nxght", "dxrk", "rxft", "blk", "cld", "frst", "wrth",
            "nvr", "fxde", "brkn", "sxl", "hxl", "lxst", "dxwn", "gld",
    };

    private static final String[] STYLIZED_SUFFIXES = {
            "less", "out", "rift", "fall", "ss", "nn", "ed", "_", "x",
    };

    private final Random random;

    public BotNameGenerator(long seed) {
        this.random = new Random(seed);
    }

    public BotNameGenerator() {
        this.random = new Random();
    }

    /** Generate a unique name. Style is picked randomly with natural distribution. */
    public String generate() {
        int style = random.nextInt(100);

        if (style < 25) {
            return generateClean();
        } else if (style < 45) {
            return generateGamerTag();
        } else if (style < 60) {
            return generateNameNumber();
        } else if (style < 75) {
            return generateStylized();
        } else if (style < 90) {
            return generateShort();
        } else {
            return generateFirstNameTag();
        }
    }

    private String generateClean() {
        return pick(CLEAN_PREFIXES) + pick(CLEAN_SUFFIXES);
    }

    private String generateGamerTag() {
        return pick(GAMER_PREFIXES) + pick(FIRST_NAMES);
    }

    private String generateNameNumber() {
        return pick(FIRST_NAMES) + pick(TAG_SUFFIXES);
    }

    private String generateStylized() {
        return pick(STYLIZED_PARTS) + pick(STYLIZED_SUFFIXES);
    }

    private String generateShort() {
        return pick(SHORT_NAMES);
    }

    private String generateFirstNameTag() {
        String name = pick(FIRST_NAMES).toLowerCase();
        return name + pick(TAG_SUFFIXES);
    }

    private String pick(String[] array) {
        return array[random.nextInt(array.length)];
    }
}
