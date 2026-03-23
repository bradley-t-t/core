package com.core.plugin.modules.bots;

import com.core.plugin.CorePlugin;
import com.core.plugin.util.BotUtil;

import java.util.Random;
import java.util.UUID;


/**
 * Creates fake player data files so that /user, /seen, etc. work on bots.
 * All stats start at zero and grow live via {@link BotStatGrowthTask}.
 */
public final class BotDataSeeder {

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
            return;
        }

        // --- Brand-new bot: start at zero stats ---
        int hash = Math.abs(name.hashCode());
        long now = System.currentTimeMillis();

        long daysAgo = 1 + (hash % 90);
        dm.setFirstJoin(botId, now - (daysAgo * 86_400_000L));
        dm.setLastSeen(botId, now);
        dm.setRank(botId, traits.getRank(name));

        // Initialize all stats to zero — growth happens live via BotStatGrowthTask
        dm.setStat(botId, "kills", 0);
        dm.setStat(botId, "deaths", 0);
        dm.setStat(botId, "blocks-broken", 0);
        dm.setStat(botId, "blocks-placed", 0);
        dm.setStat(botId, "mobs-killed", 0);
        dm.setStat(botId, "fish-caught", 0);
        dm.setStat(botId, "play-time", 0);
    }

    /**
     * Roll a stat gain for a single tick, accounting for the personality multiplier
     * and the chance of producing a zero-gain tick. Used by {@link BotStatGrowthTask}.
     */
    static long rollGain(Random rng, int[] range, double mult) {
        if (rng.nextInt(100) < BotConfig.ZERO_GAIN_CHANCE) return 0;
        int base = range[0] + (range[1] > range[0] ? rng.nextInt(range[1] - range[0] + 1) : 0);
        return Math.max(0, Math.round(base * mult));
    }
}
