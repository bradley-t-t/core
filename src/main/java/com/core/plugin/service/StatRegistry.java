package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.stats.StatType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Loads stat definitions from {@code stats.yml} and provides lookup access.
 * Registered as a service so it participates in the standard lifecycle.
 */
public final class StatRegistry implements Service {

    public static final String PLAY_TIME_KEY = "play-time";

    private final CorePlugin plugin;
    private final Map<String, StatType> statTypes = new LinkedHashMap<>();

    public StatRegistry(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        File statsFile = new File(plugin.getDataFolder(), "stats.yml");
        if (!statsFile.exists()) plugin.saveResource("stats.yml", false);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(statsFile);
        ConfigurationSection section = config.getConfigurationSection("stats");

        if (section == null) {
            plugin.getLogger().log(Level.WARNING, "stats.yml missing 'stats' section");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;

            String displayName = entry.getString("display-name", key);
            Material icon = Material.matchMaterial(entry.getString("icon", "PAPER"));
            if (icon == null) icon = Material.PAPER;

            statTypes.put(key, new StatType(key, displayName, icon));
        }

        plugin.getLogger().info("Loaded " + statTypes.size() + " stat types from stats.yml");
    }

    @Override
    public void disable() {
        statTypes.clear();
    }

    /** Look up a stat definition by its YAML key. Returns {@code null} if not registered. */
    public StatType getByKey(String key) {
        return statTypes.get(key);
    }

    /** All registered stat definitions (insertion-ordered). */
    public Collection<StatType> getAll() {
        return Collections.unmodifiableCollection(statTypes.values());
    }

    /** All registered stat keys. */
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(statTypes.keySet());
    }
}
