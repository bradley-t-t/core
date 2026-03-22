package com.core.plugin.modules.bots;

import com.core.plugin.CorePlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages the persistent pool of fake player names.
 * Handles loading/saving from fakeplayers.yml, auto-sizing based on config,
 * and periodic rotation of names to simulate new players discovering the server.
 */
public final class BotPool {

    /** Pool should be this multiple of maxOnline to allow cycling variety. */
    private static final double POOL_MULTIPLIER = 1.5;
    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 16;
    private static final int MAX_GENERATION_ATTEMPTS = 50;

    private final CorePlugin plugin;
    private final File dataFile;
    private final List<String> names = new ArrayList<>();
    private final Random random = new Random();
    private BotNameValidator validator;
    private boolean enabled;

    public BotPool(CorePlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "fakeplayers.yml");
    }

    public void setValidator(BotNameValidator validator) {
        this.validator = validator;
    }

    public BotNameValidator getValidator() {
        return validator;
    }

    /** Load the pool from disk and auto-size if needed. */
    public void load(int maxOnline) {
        names.clear();

        if (dataFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            names.addAll(config.getStringList("players"));
            enabled = config.getBoolean("enabled",
                    plugin.getConfig().getBoolean("fake-players.enabled", false));
        } else {
            enabled = plugin.getConfig().getBoolean("fake-players.enabled", false);
        }

        ensurePoolSize(maxOnline);
        save();
    }

    /** Read the last-shutdown timestamp for quick restart detection. */
    public long getLastShutdownTime() {
        if (!dataFile.exists()) return 0;
        return YamlConfiguration.loadConfiguration(dataFile).getLong("last-shutdown", 0);
    }

    /** Persist the shutdown timestamp for quick restart detection. */
    public void saveShutdownTime() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        config.set("last-shutdown", System.currentTimeMillis());
        try { config.save(dataFile); } catch (IOException ignored) {}
    }

    public List<String> getNames() {
        return List.copyOf(names);
    }

    public boolean isEmpty() {
        return names.isEmpty();
    }

    public int size() {
        return names.size();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    /** Add a name to the pool if not already present. */
    public void add(String name) {
        if (!names.contains(name)) {
            names.add(name);
            save();
        }
    }

    /** Remove a name from the pool. Returns true if it was present. */
    public boolean remove(String name) {
        boolean removed = names.remove(name);
        if (removed) save();
        return removed;
    }

    /**
     * Rotate: retire random offline names and generate fresh replacements.
     *
     * @param onlineNames names currently online (excluded from retirement)
     * @param count       how many names to swap out
     * @return newly generated names (caller should assign traits)
     */
    public List<String> rotate(Set<String> onlineNames, int count) {
        if (names.size() < 10) return List.of();

        BotNameGenerator generator = new BotNameGenerator();
        Set<String> existing = new HashSet<>(names);
        List<String> newNames = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            List<String> offlinePool = names.stream()
                    .filter(name -> !onlineNames.contains(name))
                    .toList();
            if (offlinePool.isEmpty()) break;

            String retiring = offlinePool.get(random.nextInt(offlinePool.size()));
            names.remove(retiring);
            existing.remove(retiring);

            String replacement = generateUniqueName(generator, existing);
            if (replacement != null) {
                names.add(replacement);
                existing.add(replacement);
                newNames.add(replacement);
            }
        }

        save();
        return newNames;
    }

    /** Build a shuffled list prioritizing names in their schedule window. */
    public List<String> shuffledBySchedulePriority(Map<String, ?> scheduleMap,
                                             java.util.function.Predicate<String> inWindowTest) {
        List<String> inWindow = names.stream().filter(inWindowTest)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(inWindow, random);

        List<String> outWindow = names.stream().filter(n -> !inWindow.contains(n))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(outWindow, random);

        List<String> result = new ArrayList<>(inWindow);
        result.addAll(outWindow);
        return result;
    }

    private void ensurePoolSize(int maxOnline) {
        int targetSize = (int) (maxOnline * POOL_MULTIPLIER);
        if (names.size() >= targetSize) return;

        BotNameGenerator generator = new BotNameGenerator();
        Set<String> existing = new HashSet<>(names);
        int generated = 0;

        while (names.size() < targetSize) {
            String name = generateUniqueName(generator, existing);
            if (name == null) break;
            names.add(name);
            existing.add(name);
            generated++;
        }

        if (generated > 0) {
            plugin.getLogger().info("Generated " + generated
                    + " fake player names (pool now " + names.size() + ").");
        }
    }

    private String generateUniqueName(BotNameGenerator generator, Set<String> existing) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String name = generator.generate();
            if (!existing.contains(name)
                    && name.length() >= MIN_NAME_LENGTH
                    && name.length() <= MAX_NAME_LENGTH) {
                return name;
            }
        }
        return null;
    }

    /**
     * Replaces any names in the pool that the validator has flagged as invalid
     * (no real Mojang account). Invalid names that are currently online are skipped
     * and will be replaced next cycle.
     *
     * @param onlineNames names currently online (won't be replaced mid-session)
     * @return newly added replacement names (caller should assign traits)
     */
    public List<String> replaceInvalidNames(Set<String> onlineNames) {
        if (validator == null) return List.of();

        Set<String> invalid = validator.getInvalidNames();
        if (invalid.isEmpty()) return List.of();

        List<String> toReplace = names.stream()
                .filter(invalid::contains)
                .filter(name -> !onlineNames.contains(name))
                .toList();

        if (toReplace.isEmpty()) return List.of();

        BotNameGenerator generator = new BotNameGenerator();
        Set<String> existing = new HashSet<>(names);
        List<String> newNames = new ArrayList<>();

        for (String retiring : toReplace) {
            String replacement = generateUniqueName(generator, existing);
            if (replacement == null) break;

            names.remove(retiring);
            existing.remove(retiring);
            names.add(replacement);
            existing.add(replacement);
            newNames.add(replacement);
        }

        if (!newNames.isEmpty()) {
            save();
            plugin.getLogger().info("Replaced " + newNames.size()
                    + " invalid bot names with real Minecraft accounts.");
        }

        return newNames;
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", enabled);
        config.set("players", names);
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save fakeplayers.yml", e);
        }
    }
}
