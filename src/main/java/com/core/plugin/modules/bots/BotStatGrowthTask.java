package com.core.plugin.modules.bots;

import com.core.plugin.CorePlugin;
import com.core.plugin.data.DataManager;
import com.core.plugin.util.BotUtil;
import org.bukkit.Bukkit;

import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Periodic task that nudges each online bot's stats upward by small,
 * personality-scaled random amounts. Runs on the async scheduler so
 * YAML writes don't block the main thread.
 *
 * <p>Stats are written directly to the bot's YAML via {@link DataManager},
 * which means the existing {@link com.core.plugin.service.StatsSyncService}
 * picks them up on its next 2-minute sync cycle.</p>
 */
public final class BotStatGrowthTask {

    private final CorePlugin plugin;
    private final BotTraits traits;
    private final Set<String> onlineBots;
    private final Random random = new Random();
    private int taskId = -1;

    public BotStatGrowthTask(CorePlugin plugin, BotTraits traits, Set<String> onlineBots) {
        this.plugin = plugin;
        this.traits = traits;
        this.onlineBots = onlineBots;
    }

    /** Start the repeating async task. */
    public void start() {
        if (taskId != -1) return;
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::tick,
                BotConfig.STAT_GROWTH_INTERVAL_TICKS,
                BotConfig.STAT_GROWTH_INTERVAL_TICKS
        ).getTaskId();
    }

    /** Stop the repeating task. */
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    /** One growth tick: iterate all online bots and bump their stats. */
    private void tick() {
        DataManager dm = plugin.dataManager();

        for (String name : onlineBots) {
            UUID botId = BotUtil.fakeUuid(name);
            double mult = growthMultiplier(name);

            increment(dm, botId, "kills", BotConfig.KILLS_PER_TICK, mult, BotConfig.CAP_KILLS);
            increment(dm, botId, "deaths", BotConfig.DEATHS_PER_TICK, mult, BotConfig.CAP_DEATHS);
            increment(dm, botId, "blocks-broken", BotConfig.BLOCKS_BROKEN_PER_TICK, mult, BotConfig.CAP_BLOCKS_BROKEN);
            increment(dm, botId, "blocks-placed", BotConfig.BLOCKS_PLACED_PER_TICK, mult, BotConfig.CAP_BLOCKS_PLACED);
            increment(dm, botId, "mobs-killed", BotConfig.MOBS_KILLED_PER_TICK, mult, BotConfig.CAP_MOBS_KILLED);
            increment(dm, botId, "fish-caught", BotConfig.FISH_CAUGHT_PER_TICK, mult, BotConfig.CAP_FISH_CAUGHT);

            // Play time: always add 10 minutes per tick (matches the 10-minute interval)
            long currentPlayTime = dm.getStat(botId, "play-time");
            if (currentPlayTime < BotConfig.CAP_PLAY_TIME) {
                dm.setStat(botId, "play-time", Math.min(currentPlayTime + 10, BotConfig.CAP_PLAY_TIME));
            }

            // Update last-seen so /seen reflects recent activity
            dm.setLastSeen(botId, System.currentTimeMillis());
        }
    }

    /** Increment a single stat by a random, personality-scaled amount, respecting the cap. */
    private void increment(DataManager dm, UUID botId, String statKey,
                           int[] range, double mult, long cap) {
        long gain = BotDataSeeder.rollGain(random, range, mult);
        if (gain <= 0) return;

        long current = dm.getStat(botId, statKey);
        if (current >= cap) return;

        dm.setStat(botId, statKey, Math.min(current + gain, cap));
    }

    /** Resolve the growth multiplier for a bot based on its personality. */
    private double growthMultiplier(String name) {
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
