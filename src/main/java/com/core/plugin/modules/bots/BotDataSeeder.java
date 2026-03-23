package com.core.plugin.modules.bots;

import com.core.plugin.CorePlugin;
import com.core.plugin.util.BotUtil;

import java.util.Map;
import java.util.Random;
import java.util.UUID;


/**
 * Creates and manages fake player data files so that /user, /seen, and
 * other profile commands work on bot players.
 *
 * <p>New bots start at zero (or near-zero) stats. If a bot has a
 * {@code first-join} date in the past, a one-time backfill simulates
 * the gradual growth that <em>would have</em> occurred since that date,
 * using personality-based rates so no bot appears with implausible
 * numbers on its first sync.</p>
 */
public final class BotDataSeeder {

    private static final String[][] ACHIEVEMENT_THRESHOLDS = {
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

    private final CorePlugin plugin;
    private final BotTraits traits;

    public BotDataSeeder(CorePlugin plugin, BotTraits traits) {
        this.plugin = plugin;
        this.traits = traits;
    }

    /** Create a player data file for a bot so /user, /seen, etc. work. */
    public void ensurePlayerData(String name) {
        UUID botId = BotUtil.fakeUuid(name);
        var dm = plugin.dataManager();

        if (dm.getFirstJoin(botId) > 0) {
            dm.setLastSeen(botId, System.currentTimeMillis());

            // One-time backfill: if the bot has a first-join but zero stats,
            // simulate growth proportional to its age.
            if (dm.getStat(botId, "kills") == 0 && dm.getStat(botId, "blocks-broken") == 0) {
                backfillStats(name, botId);
            }
            return;
        }

        // --- Brand-new bot: start at zero stats ---
        int hash = Math.abs(name.hashCode());
        long now = System.currentTimeMillis();

        long daysAgo = 1 + (hash % 90);
        dm.setFirstJoin(botId, now - (daysAgo * 86_400_000L));
        dm.setLastSeen(botId, now);
        dm.setRank(botId, traits.getRank(name));

        // Initialize all stats to zero
        dm.setStat(botId, "kills", 0);
        dm.setStat(botId, "deaths", 0);
        dm.setStat(botId, "blocks-broken", 0);
        dm.setStat(botId, "blocks-placed", 0);
        dm.setStat(botId, "mobs-killed", 0);
        dm.setStat(botId, "fish-caught", 0);
        dm.setStat(botId, "play-time", 0);

        // Backfill proportionally for the simulated age
        backfillStats(name, botId);
    }

    /**
     * Simulate organic growth from first-join to now. Calculates how many
     * 10-minute growth ticks the bot "would have" experienced (assuming
     * ~6 hours online per day on average) and applies personality-scaled
     * random increments for each simulated tick.
     */
    private void backfillStats(String name, UUID botId) {
        var dm = plugin.dataManager();
        long firstJoin = dm.getFirstJoin(botId);
        if (firstJoin <= 0) return;

        long ageMillis = System.currentTimeMillis() - firstJoin;
        long ageDays = Math.max(1, ageMillis / 86_400_000L);

        // Assume ~6 hours of online time per day, with growth ticks every 10 min
        // That's 36 ticks per day
        long simulatedTicks = ageDays * 36;

        double mult = growthMultiplier(name);
        int hash = Math.abs(name.hashCode());
        Random rng = new Random(hash); // deterministic per bot name

        long kills = 0, deaths = 0, blocksBroken = 0, blocksPlaced = 0;
        long mobsKilled = 0, fishCaught = 0, playTime = 0;

        for (long t = 0; t < simulatedTicks; t++) {
            kills += rollGain(rng, BotConfig.KILLS_PER_TICK, mult);
            deaths += rollGain(rng, BotConfig.DEATHS_PER_TICK, mult);
            blocksBroken += rollGain(rng, BotConfig.BLOCKS_BROKEN_PER_TICK, mult);
            blocksPlaced += rollGain(rng, BotConfig.BLOCKS_PLACED_PER_TICK, mult);
            mobsKilled += rollGain(rng, BotConfig.MOBS_KILLED_PER_TICK, mult);
            fishCaught += rollGain(rng, BotConfig.FISH_CAUGHT_PER_TICK, mult);
            playTime += 10; // 10 minutes per tick
        }

        // Apply caps
        kills = Math.min(kills, BotConfig.CAP_KILLS);
        deaths = Math.min(deaths, BotConfig.CAP_DEATHS);
        blocksBroken = Math.min(blocksBroken, BotConfig.CAP_BLOCKS_BROKEN);
        blocksPlaced = Math.min(blocksPlaced, BotConfig.CAP_BLOCKS_PLACED);
        mobsKilled = Math.min(mobsKilled, BotConfig.CAP_MOBS_KILLED);
        fishCaught = Math.min(fishCaught, BotConfig.CAP_FISH_CAUGHT);
        playTime = Math.min(playTime, BotConfig.CAP_PLAY_TIME);

        dm.setStat(botId, "kills", kills);
        dm.setStat(botId, "deaths", deaths);
        dm.setStat(botId, "blocks-broken", blocksBroken);
        dm.setStat(botId, "blocks-placed", blocksPlaced);
        dm.setStat(botId, "mobs-killed", mobsKilled);
        dm.setStat(botId, "fish-caught", fishCaught);
        dm.setStat(botId, "play-time", playTime);

        // Unlock achievements matching backfilled values
        Map<String, Long> statMap = Map.of(
                "kills", kills, "deaths", deaths, "blocks-broken", blocksBroken,
                "blocks-placed", blocksPlaced, "mobs-killed", mobsKilled,
                "fish-caught", fishCaught, "play-time", playTime);

        for (String[] entry : ACHIEVEMENT_THRESHOLDS) {
            long statValue = statMap.getOrDefault(entry[1], 0L);
            long threshold = Long.parseLong(entry[2]);
            if (statValue >= threshold) {
                dm.addUnlockedAchievement(botId, entry[0]);
            }
        }
    }

    /**
     * Roll a stat gain for a single tick, accounting for the personality multiplier
     * and the chance of producing a zero-gain tick.
     */
    static long rollGain(Random rng, int[] range, double mult) {
        if (rng.nextInt(100) < BotConfig.ZERO_GAIN_CHANCE) return 0;
        int base = range[0] + (range[1] > range[0] ? rng.nextInt(range[1] - range[0] + 1) : 0);
        return Math.max(0, Math.round(base * mult));
    }

    /** Resolve the growth multiplier for a bot based on its personality. */
    double growthMultiplier(String name) {
        BotTraits.Personality personality = traits.getPersonality(name);
        return switch (personality) {
            case QUIET -> BotConfig.GROWTH_MULT_QUIET;
            case CASUAL -> BotConfig.GROWTH_MULT_CASUAL;
            case SOCIAL -> BotConfig.GROWTH_MULT_SOCIAL;
            case TRYHARD -> BotConfig.GROWTH_MULT_TRYHARD;
            case CHILL -> BotConfig.GROWTH_MULT_CHILL;
        };
    }
}
