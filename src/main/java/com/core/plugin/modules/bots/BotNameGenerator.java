package com.core.plugin.modules.bots;

import java.util.*;

/**
 * Provides real Minecraft usernames for the bot system.
 * Uses a large pool of generic, ordinary-sounding names that won't be
 * recognized as famous players, developers, or content creators.
 * Names are shuffled and drawn without replacement per generator instance.
 */
public final class BotNameGenerator {

    /**
     * Pool of ordinary Minecraft usernames. Avoids famous players,
     * Mojang staff, streamers, YouTubers, and any well-known accounts.
     */
    private static final String[] REAL_NAMES = {
            // Number-tagged names
            "Luke7744", "Jake2205", "Ryan9912", "Alex8833", "Sam1470",
            "Emma2234", "Noah3321", "Owen5518", "Max8876", "Ben4409",
            "Kai1155", "Leo7723", "Cole3398", "Zoe6612", "Mia2207",
            "Dan8891", "Finn4456", "Ruby1123", "Theo5567", "Nina3342",
            "Wyatt3019", "Jade7481", "Drew5530", "Lena4467", "Reid8210",
            "Tara2295", "Kyle6638", "Jess1047", "Mark7752", "Sara3316",
            "Sean4481", "Leah8825", "Troy5593", "Dana1172", "Chad9934",
            "Kira6657", "Beau2248", "Ally3309", "Dean7716", "Faye4453",

            // Typical MC player names
            "SilverGhost", "IronWarden", "NetherKing", "FrostByte",
            "DarkElixer", "SkywardFox", "CobaltBear", "RedstoneWiz",
            "MoonlitWolf", "CrimsonFang", "StormBreaker", "IcyVenom",
            "GoldenArch", "ObsidianStar", "EnderPearled", "DiamondRex",
            "MysticFlare", "BlazeRunner", "CreeperSlyr", "ZombieHuntr",
            "VoidWalker", "EmberFox", "PixelDrift", "QuartzHawk",
            "NovaBurst", "TidalForce", "GlitchWave", "ArcticMule",

            // Common real player patterns
            "xXDarkKnightXx", "PvPMaster2009", "BlockBuilder99",
            "TheDiamondKing", "CoolGamer2010", "ProBuilder123", "EpicMiner42",
            "DragonSlayer99", "NinjaWarrior7", "ShadowNinja22", "IceWolf2011",
            "FireDragon55", "StealthMode11", "DarkShadow999", "CoolKid2012",
            "GameMaster55", "ProGamer2008", "EpicGamer2011", "MasterBuilder7",

            // Lettered style
            "Cxnnor", "Rxse", "Txny", "Jxck", "Mxtt",
            "ItzLuke", "NotAlex", "ItsRyan", "DaMax", "MrFinn",
            "xShadow", "xStorm", "xBlaze", "xFrost", "xDrift",

            // Prefix style
            "iTzVortex", "iiMystical", "oJacko", "iiBreezy", "oVortex",
            "ThatGuyMike", "JustAPlayer", "SomeGuy2010", "RandomDude22", "SimplySam",
            "JustJake", "OnlyNoah", "UrBoiLiam", "ItsMeAlex", "DatBoi99",

            // Underscore style
            "Dark_Viper", "Ice_Phoenix", "Storm_Rider", "Ghost_Wolf", "Fire_Born",
            "Night_Shade", "Stone_Cold", "Iron_Fist", "Steel_Edge", "Moon_Rise",
            "Star_Fall", "Wind_Walker", "Rain_Maker", "Sun_Strike", "Sky_Diver",
            "Cave_Dweller", "Tree_Climber", "Lake_Fisher", "Rock_Miner", "Sand_Surfer",

            // Short clean names
            "Zelk", "Krinios", "Krtzyy", "Punzo", "Mefs",
            "Celest", "Volst", "Threx", "Bynox", "Crael",
            "Druvn", "Fleck", "Grynn", "Hyken", "Ixtel",
            "Jorvn", "Klept", "Lyndr", "Morex", "Nylth",

            // More number-tagged
            "Tyler3847", "Logan1952", "Mason6678", "Ethan2403", "Dylan5581",
            "Aaron8219", "Caleb4460", "Gavin7735", "Nolan1198", "Derek3352",
            "Bryce6684", "Grant2247", "Chase4413", "Blake9976", "Scott5531",
            "Paige1169", "Haley8824", "Megan3357", "Chloe6613", "Grace2248",

            // Gaming tag style
            "xxSniperxx", "xXPr0Xx", "MLGSteve", "NoobMaster69", "Xx_Dark_xX",
            "SilentShot44", "AcidRain77", "NeonPulse33", "VaporTrail88", "StaticHaze",
            "GhostRecon12", "PhantomAce99", "RogueOne42", "SteelTrap71", "IronSight55",
            "QuickScope21", "DeadEye2010", "SharpEdge16", "BluntForce29", "RawPower61",

            // Casual names
            "TheRealJosh", "ActuallyMatt", "TotallyKyle", "LiterallyDan", "HonestlyMax",
            "CasualFinn", "ChillBeau", "RelaxedReid", "MellowSean", "LazyTroy",
            "BusyBen", "HappyKai", "LuckyLeo", "SillyMia", "FunnyZoe",
    };

    private final List<String> available;
    private int index;

    public BotNameGenerator(long seed) {
        available = new ArrayList<>(Arrays.asList(REAL_NAMES));
        Collections.shuffle(available, new Random(seed));
        index = 0;
    }

    public BotNameGenerator() {
        this(System.nanoTime());
    }

    /**
     * Returns the next name from the shuffled pool.
     * Returns null if the pool is exhausted.
     */
    public String generate() {
        if (index >= available.size()) return null;
        return available.get(index++);
    }

    /** Total number of names available in the pool. */
    public static int poolSize() {
        return REAL_NAMES.length;
    }
}
