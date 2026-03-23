package com.core.plugin.modules.bots;

import com.core.plugin.CorePlugin;
import com.core.plugin.util.BotUtil;

import java.util.Map;
import java.util.UUID;

/**
 * Creates and manages fake player data files so that /user, /seen, and
 * other profile commands work on bot players. Generates deterministic
 * stats and achievements based on the bot's name hash.
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
            return;
        }

        int hash = Math.abs(name.hashCode());
        long now = System.currentTimeMillis();

        long daysAgo = 1 + (hash % 90);
        dm.setFirstJoin(botId, now - (daysAgo * 86_400_000L));
        dm.setLastSeen(botId, now);
        dm.setRank(botId, traits.getRank(name));

        int kills = hash % 50;
        int deaths = (hash / 50) % 80;
        int blocksBroken = 500 + (hash % 5000);
        int blocksPlaced = 200 + ((hash / 7) % 3000);
        int mobsKilled = 50 + (hash % 500);
        int fishCaught = (hash / 3) % 100;
        int playTime = (int) (daysAgo * 60 + (hash % 500));

        dm.setStat(botId, "kills", kills);
        dm.setStat(botId, "deaths", deaths);
        dm.setStat(botId, "blocks-broken", blocksBroken);
        dm.setStat(botId, "blocks-placed", blocksPlaced);
        dm.setStat(botId, "mobs-killed", mobsKilled);
        dm.setStat(botId, "fish-caught", fishCaught);
        dm.setStat(botId, "play-time", playTime);

        Map<String, Integer> statMap = Map.of(
                "kills", kills, "deaths", deaths, "blocks-broken", blocksBroken,
                "blocks-placed", blocksPlaced, "mobs-killed", mobsKilled,
                "fish-caught", fishCaught, "play-time", playTime);

        for (String[] entry : ACHIEVEMENT_THRESHOLDS) {
            int statValue = statMap.getOrDefault(entry[1], 0);
            int threshold = Integer.parseInt(entry[2]);
            if (statValue >= threshold) {
                dm.addUnlockedAchievement(botId, entry[0]);
            }
        }
    }
}
