package com.core.plugin.lang;

import com.core.plugin.util.MessageUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads all user-facing strings from {@code language.yml}, flattening the YAML
 * hierarchy into dot-separated keys. On load, any keys present in the bundled
 * default but missing from the server's copy are automatically patched in and
 * saved to disk — so plugin updates never leave gaps.
 */
public final class LanguageManager {

    private final JavaPlugin plugin;
    private final File languageFile;
    private final Map<String, String> messages = new HashMap<>();

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.languageFile = new File(plugin.getDataFolder(), "language.yml");
        if (!languageFile.exists()) {
            plugin.saveResource("language.yml", false);
        }
        reload();
    }

    /** Reload all messages from disk, patching any missing keys from defaults. */
    public void reload() {
        messages.clear();
        patchMissingKeys();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(languageFile);
        flatten("", config, messages);
    }

    /** Get a colorized message with placeholder replacement. */
    public String get(String key, Object... replacements) {
        String template = messages.getOrDefault(key, "&c[Missing: " + key + "]");
        return MessageUtil.colorize(MessageUtil.replacePlaceholders(template, replacements));
    }

    /** Get a prefixed, colorized message with placeholder replacement. */
    public String getPrefixed(String key, Object... replacements) {
        String prefix = messages.getOrDefault("prefix", "&8[&cCore&8] &7");
        String template = messages.getOrDefault(key, "&c[Missing: " + key + "]");
        return MessageUtil.colorize(prefix + MessageUtil.replacePlaceholders(template, replacements));
    }

    /**
     * Compares the server's language.yml against the bundled default.
     * Any keys present in the default but missing on disk are written
     * and the file is saved. Existing user customizations are preserved.
     */
    private void patchMissingKeys() {
        var resource = plugin.getResource("language.yml");
        if (resource == null) return;

        YamlConfiguration defaults;
        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read bundled language.yml defaults", e);
            return;
        }
        YamlConfiguration current = YamlConfiguration.loadConfiguration(languageFile);

        Map<String, Object> defaultValues = new HashMap<>();
        flattenValues("", defaults, defaultValues);

        boolean patched = false;
        for (var entry : defaultValues.entrySet()) {
            if (!current.contains(entry.getKey())) {
                current.set(entry.getKey(), entry.getValue());
                patched = true;
            }
        }

        if (patched) {
            try {
                current.save(languageFile);
                plugin.getLogger().info("Patched missing keys into language.yml.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save patched language.yml", e);
            }
        }
    }

    private void flatten(String parentPath, ConfigurationSection section, Map<String, String> output) {
        for (String key : section.getKeys(false)) {
            String fullPath = parentPath.isEmpty() ? key : parentPath + "." + key;
            if (section.isConfigurationSection(key)) {
                flatten(fullPath, section.getConfigurationSection(key), output);
            } else {
                output.put(fullPath, section.getString(key, ""));
            }
        }
    }

    private void flattenValues(String parentPath, ConfigurationSection section, Map<String, Object> output) {
        for (String key : section.getKeys(false)) {
            String fullPath = parentPath.isEmpty() ? key : parentPath + "." + key;
            if (section.isConfigurationSection(key)) {
                flattenValues(fullPath, section.getConfigurationSection(key), output);
            } else {
                output.put(fullPath, section.get(key));
            }
        }
    }
}
