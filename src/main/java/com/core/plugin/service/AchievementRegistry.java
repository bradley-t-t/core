package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.stats.Achievement;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Loads achievement definitions from {@code achievements.yml} and provides
 * lookup methods for the rest of the plugin. Placed in the service package
 * so the reflection-based auto-scanner discovers it automatically.
 */
public final class AchievementRegistry implements Service {

    private final CorePlugin plugin;
    private final Map<String, Achievement> achievementsByKey = new LinkedHashMap<>();
    private final Map<String, List<Achievement>> achievementsByStat = new HashMap<>();

    public AchievementRegistry(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        plugin.saveResource("achievements.yml", false);

        File file = new File(plugin.getDataFolder(), "achievements.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("achievements");
        if (root == null) {
            plugin.getLogger().warning("No 'achievements' section found in achievements.yml");
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;

            String statKey = section.getString("stat");
            long threshold = section.getLong("threshold");
            String displayName = section.getString("display-name", key);
            String description = section.getString("description", "");
            Material icon = parseMaterial(section.getString("icon", "PAPER"), key);

            Achievement achievement = new Achievement(key, statKey, threshold, displayName, description, icon);
            achievementsByKey.put(key, achievement);
            achievementsByStat.computeIfAbsent(statKey, k -> new ArrayList<>()).add(achievement);
        }

        plugin.getLogger().info("Loaded " + achievementsByKey.size() + " achievements from config.");
    }

    @Override
    public void disable() {
        achievementsByKey.clear();
        achievementsByStat.clear();
    }

    /** All registered achievements in config-defined order. */
    public Collection<Achievement> getAll() {
        return Collections.unmodifiableCollection(achievementsByKey.values());
    }

    /** Lookup a single achievement by its config key. Returns null if not found. */
    public Achievement getByKey(String key) {
        if (key == null) return null;
        Achievement result = achievementsByKey.get(key);
        if (result != null) return result;
        // Case-insensitive fallback for backward compatibility with old uppercase enum names
        return achievementsByKey.get(key.toLowerCase(Locale.ROOT));
    }

    /** Total number of registered achievements. */
    public int size() {
        return achievementsByKey.size();
    }

    /** All achievements tied to a given stat key, for efficient threshold checking. */
    public List<Achievement> getForStat(String statKey) {
        return achievementsByStat.getOrDefault(statKey, List.of());
    }

    private Material parseMaterial(String name, String achievementKey) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Invalid icon material '" + name + "' for achievement '" + achievementKey + "', defaulting to PAPER");
            return Material.PAPER;
        }
    }
}
