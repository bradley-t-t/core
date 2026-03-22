package com.core.plugin.modules.punishment;

import com.core.plugin.CorePlugin;
import com.core.plugin.service.Service;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Loads punishment types and severity tiers from {@code punishments.yml}.
 * Auto-discovered by the service registry via its {@code (CorePlugin)} constructor.
 */
public final class PunishmentRegistry implements Service {

    private final CorePlugin plugin;
    private final Map<String, PunishmentType> types = new LinkedHashMap<>();
    private final Map<Integer, PunishmentSeverity> severities = new LinkedHashMap<>();

    public PunishmentRegistry(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        plugin.saveResource("punishments.yml", false);

        File file = new File(plugin.getDataFolder(), "punishments.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        loadTypes(config);
        loadSeverities(config);

        plugin.getLogger().info("Loaded " + types.size() + " punishment types and " + severities.size() + " severity tiers.");
    }

    @Override
    public void disable() {
        types.clear();
        severities.clear();
    }

    public Collection<PunishmentType> getTypes() {
        return Collections.unmodifiableCollection(types.values());
    }

    public PunishmentType getTypeByKey(String key) {
        return types.get(key);
    }

    public Collection<PunishmentSeverity> getSeverities() {
        return Collections.unmodifiableCollection(severities.values());
    }

    public PunishmentSeverity getSeverityByTier(int tier) {
        return severities.get(tier);
    }

    private void loadTypes(YamlConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("types");
        if (section == null) {
            plugin.getLogger().log(Level.WARNING, "No 'types' section found in punishments.yml");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection typeSection = section.getConfigurationSection(key);
            if (typeSection == null) continue;

            String displayName = typeSection.getString("display-name", key);
            Material icon = Material.matchMaterial(typeSection.getString("icon", "STONE"));
            boolean skipsSeverity = typeSection.getBoolean("skips-severity", false);

            types.put(key, new PunishmentType(key, displayName, icon != null ? icon : Material.STONE, skipsSeverity));
        }
    }

    private void loadSeverities(YamlConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("severities");
        if (section == null) {
            plugin.getLogger().log(Level.WARNING, "No 'severities' section found in punishments.yml");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection sevSection = section.getConfigurationSection(key);
            if (sevSection == null) continue;

            int tier = Integer.parseInt(key);
            long duration = sevSection.getLong("duration", -1);
            String displayDuration = sevSection.getString("display-duration", "Unknown");
            Material icon = Material.matchMaterial(sevSection.getString("icon", "STONE"));

            severities.put(tier, new PunishmentSeverity(tier, duration, displayDuration, icon != null ? icon : Material.STONE));
        }
    }
}
