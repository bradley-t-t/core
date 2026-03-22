package com.core.plugin.modules.bots;

import java.util.*;

/**
 * Provides real Minecraft usernames for the bot system.
 * Uses a large pool of known valid account names so that external services
 * (PMC, server lists, etc.) recognize them as real players.
 * Names are shuffled and drawn without replacement per generator instance.
 */
public final class BotNameGenerator {

    /**
     * Pool of real Minecraft usernames. These are common/popular names
     * that correspond to actual Mojang accounts.
     */
    private static final String[] REAL_NAMES = {
            // Classic / well-known style names
            "Technoblade", "Quackity", "Purpled", "Foolish", "Punz",
            "Antfrost", "Awesamdude", "Ponk", "Skeppy", "BadBoyHalo",
            "CaptainSparklez", "Vikkstar123", "Lachlan", "Bajan", "JeromeASF",
            "AntVenom", "Graser10", "HBomb94", "Wisp", "Hannahxxrose",

            // Typical MC player names
            "xNestorio", "Stimpy", "Stimpay", "ApacheBlitz", "Danteh",
            "Huahwi", "Tylarzz", "Verzide", "Cxlvxn", "Breezily",
            "Tenebrous", "Palikka", "Kiingtong", "Grapeapplesauce", "Shubble",
            "Smajor1995", "fWhip", "GeminiTay", "Smallishbeans", "InTheLittleWood",

            // Normal-sounding player names
            "SilverGhost", "IronWarden", "NetherKing", "FrostByte",
            "DarkElixer", "SkywardFox", "CobaltBear", "RedstoneWiz",
            "MoonlitWolf", "CrimsonFang", "StormBreaker", "IcyVenom",
            "GoldenArch", "ObsidianStar", "EnderPearled", "DiamondRex",
            "MysticFlare", "BlazeRunner", "CreeperSlyr", "ZombieHuntr",

            // Real typical usernames found on servers
            "xXDarkKnightXx", "PvPMaster2009", "MineCraftLord", "BlockBuilder99",
            "TheDiamondKing", "CoolGamer2010", "ProBuilder123", "EpicMiner42",
            "DragonSlayer99", "NinjaWarrior7", "ShadowNinja22", "IceWolf2011",
            "FireDragon55", "StealthMode11", "DarkShadow999", "CoolKid2012",
            "GameMaster55", "ProGamer2008", "EpicGamer2011", "MasterBuilder7",

            // Short/clean names
            "Zelk", "Krinios", "Krtzyy", "Sapnap", "Punzo",
            "Ranboo", "Tubbo", "Eret", "Nihachu", "Sylvee",
            "Seapeekay", "Mefs", "Krinios", "Illumina", "Fruitberries",
            "Petezahhutt", "TapL", "Grian", "Scar", "Mumbo",

            // Number-tagged real names
            "Luke7744", "Jake2205", "Ryan9912", "Alex8833", "Sam1470",
            "Emma2234", "Noah3321", "Owen5518", "Max8876", "Ben4409",
            "Kai1155", "Leo7723", "Cole3398", "Zoe6612", "Mia2207",
            "Dan8891", "Finn4456", "Ruby1123", "Theo5567", "Nina3342",

            // More authentic-feeling names
            "Cxnnor", "Rxse", "Txny", "Jxck", "Mxtt",
            "ItzLuke", "NotAlex", "ItsRyan", "DaMax", "MrFinn",
            "xShadow", "xStorm", "xBlaze", "xFrost", "xDrift",
            "LilKai", "LilMia", "LilSam", "LilZoe", "LilDan",

            // Underscore style
            "Dark_Viper", "Ice_Phoenix", "Storm_Rider", "Ghost_Wolf", "Fire_Born",
            "Night_Shade", "Stone_Cold", "Iron_Fist", "Steel_Edge", "Moon_Rise",
            "Star_Fall", "Wind_Walker", "Rain_Maker", "Sun_Strike", "Sky_Diver",
            "Cave_Dweller", "Tree_Climber", "Lake_Fisher", "Rock_Miner", "Sand_Surfer",

            // OG-style short names
            "Jeb", "Dinnerbone", "Notch", "Herobrine", "Steve",
            "Grumm", "Searge", "Marc", "TheMogMiner", "Celest",

            // More common real player patterns
            "xxSniperxx", "xXPr0Xx", "MLGSteve", "NoobMaster69", "Xx_Dark_xX",
            "iTzVortex", "iiMystical", "oJacko", "iiBreezy", "oVortex",
            "ThatGuyMike", "JustAPlayer", "SomeGuy2010", "RandomDude22", "SimplySam",
            "JustJake", "OnlyNoah", "UrBoiLiam", "ItsMeAlex", "DatBoi99",
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
