package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.rank.Rank;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.util.MessageUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Manages rank display data from {@code ranks.yml} and player rank assignments
 * persisted in player data files. Permission logic is NOT here -- it lives in
 * command annotations ({@code minRank}) and inline rank checks in commands.
 */
public final class RankService implements Service {

    private final CorePlugin plugin;
    private final File ranksFile;
    private final EnumMap<RankLevel, Rank> ranks = new EnumMap<>(RankLevel.class);

    public RankService(CorePlugin plugin) {
        this.plugin = plugin;
        this.ranksFile = new File(plugin.getDataFolder(), "ranks.yml");
    }

    @Override
    public void enable() {
        if (!ranksFile.exists()) plugin.saveResource("ranks.yml", false);
        reload();
    }

    @Override
    public void disable() {
        ranks.clear();
    }

    /** Reload display configuration from disk. */
    public void reload() {
        ranks.clear();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(ranksFile);
        ConfigurationSection ranksSection = config.getConfigurationSection("ranks");
        if (ranksSection == null) return;

        for (String key : ranksSection.getKeys(false)) {
            ConfigurationSection section = ranksSection.getConfigurationSection(key);
            if (section == null) continue;

            RankLevel level = RankLevel.fromString(key);
            if (level == null) continue;

            String prefix = section.getString("prefix", "&7[" + key + "]");
            String chatColor = section.getString("chat-color", "&f");

            ranks.put(level, new Rank(level, prefix, chatColor));
        }

        plugin.getLogger().info("Loaded " + ranks.size() + " rank display configs.");
    }

    // --- Player Rank Assignment ---

    public RankLevel getLevel(UUID playerId) {
        String stored = plugin.dataManager().getRank(playerId);
        RankLevel level = RankLevel.fromString(stored);
        return level != null ? level : RankLevel.MEMBER;
    }

    public Rank getRank(UUID playerId) {
        return ranks.get(getLevel(playerId));
    }

    public void setRank(UUID playerId, RankLevel level) {
        plugin.dataManager().setRank(playerId, level.name().toLowerCase());
    }

    // --- Display ---

    public String getPrefix(UUID playerId) {
        Rank rank = getRank(playerId);
        return MessageUtil.colorize(rank.displayPrefix());
    }

    public String getChatColor(UUID playerId) {
        Rank rank = getRank(playerId);
        return rank.chatColor();
    }

    // --- Queries ---

    public Rank getDisplayConfig(RankLevel level) {
        Rank rank = ranks.get(level);
        return rank != null ? rank : ranks.values().iterator().next();
    }

    public List<Rank> getAllRanks() {
        return ranks.values().stream()
                .sorted(Comparator.comparingInt(Rank::weight))
                .toList();
    }
}
