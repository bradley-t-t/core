package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.rank.RankLevel;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages Diamond rank perks: cosmetic particle trails, custom join messages,
 * and priority queue status. Data is persisted in {@code diamond.yml}.
 */
public final class DiamondService implements Service {

    /** Available particle trail types Diamond players can choose from. */
    public enum TrailType {
        HEART(Particle.HEART, null, Material.RED_DYE, "Heart", "&7Floating hearts trail behind you"),
        FLAME(Particle.FLAME, null, Material.BLAZE_POWDER, "Flame", "&7Leave a fiery trail as you walk"),
        SOUL(Particle.SOUL_FIRE_FLAME, null, Material.SOUL_LANTERN, "Soul Flame", "&7Eerie blue flames follow you"),
        ENCHANT(Particle.ENCHANT, null, Material.ENCHANTING_TABLE, "Enchant", "&7Glittering enchantment sparks"),
        NOTE(Particle.NOTE, null, Material.NOTE_BLOCK, "Note", "&7Colorful music notes float up"),
        CHERRY(Particle.CHERRY_LEAVES, null, Material.CHERRY_LEAVES, "Cherry Blossom", "&7Pink petals drift behind you"),
        END(Particle.PORTAL, null, Material.ENDER_PEARL, "End Portal", "&7Mysterious portal particles"),
        SNOW(Particle.SNOWFLAKE, null, Material.SNOWBALL, "Snowflake", "&7Gentle snowflakes swirl around"),
        EMERALD(Particle.HAPPY_VILLAGER, null, Material.EMERALD, "Emerald Sparkle", "&7Green sparkles of good fortune"),
        DIAMOND(Particle.DUST, Color.fromRGB(79, 195, 247), Material.DIAMOND, "Diamond Dust", "&7Shimmering diamond-blue particles");

        private final Particle particle;
        private final Color dustColor;
        private final Material icon;
        private final String displayName;
        private final String description;

        TrailType(Particle particle, Color dustColor, Material icon, String displayName, String description) {
            this.particle = particle;
            this.dustColor = dustColor;
            this.icon = icon;
            this.displayName = displayName;
            this.description = description;
        }

        public Particle particle() { return particle; }
        public Color dustColor() { return dustColor; }
        public Material icon() { return icon; }
        public String displayName() { return displayName; }
        public String description() { return description; }

        public static TrailType fromString(String name) {
            if (name == null) return null;
            try {
                return valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static final int PARTICLE_INTERVAL_TICKS = 4;

    private final CorePlugin plugin;
    private final File dataFile;

    private final Map<UUID, TrailType> activeTrails = new HashMap<>();
    private final Map<UUID, String> joinMessages = new HashMap<>();
    private BukkitTask particleTask;

    public DiamondService(CorePlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "diamond.yml");
    }

    @Override
    public void enable() {
        loadData();
        startParticleTask();
    }

    @Override
    public void disable() {
        if (particleTask != null) particleTask.cancel();
        saveData();
        activeTrails.clear();
        joinMessages.clear();
    }

    // --- Particle Trails ---

    public void setTrail(UUID playerId, TrailType trail) {
        if (trail == null) {
            activeTrails.remove(playerId);
        } else {
            activeTrails.put(playerId, trail);
        }
        saveData();
    }

    public TrailType getTrail(UUID playerId) {
        return activeTrails.get(playerId);
    }

    public void clearTrail(UUID playerId) {
        activeTrails.remove(playerId);
        saveData();
    }

    // --- Join Messages ---

    public void setJoinMessage(UUID playerId, String message) {
        if (message == null || message.isBlank()) {
            joinMessages.remove(playerId);
        } else {
            joinMessages.put(playerId, message);
        }
        saveData();
    }

    public String getJoinMessage(UUID playerId) {
        return joinMessages.get(playerId);
    }

    public void clearJoinMessage(UUID playerId) {
        joinMessages.remove(playerId);
        saveData();
    }

    // --- Priority Queue ---

    /** Check if a player qualifies for priority login. */
    public boolean hasPriorityQueue(UUID playerId) {
        RankService rankService = plugin.services().get(RankService.class);
        return rankService.getLevel(playerId).isAtLeast(RankLevel.DIAMOND);
    }

    // --- Particle Tick ---

    private void startParticleTask() {
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            RankService rankService = plugin.services().get(RankService.class);

            for (var entry : activeTrails.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) continue;
                if (!rankService.getLevel(player.getUniqueId()).isAtLeast(RankLevel.DIAMOND)) continue;

                spawnTrailParticle(player, entry.getValue());
            }
        }, PARTICLE_INTERVAL_TICKS, PARTICLE_INTERVAL_TICKS);
    }

    private void spawnTrailParticle(Player player, TrailType trail) {
        Location loc = player.getLocation().add(0, 0.5, 0);

        if (trail.particle() == Particle.DUST && trail.dustColor() != null) {
            Particle.DustOptions dust = new Particle.DustOptions(trail.dustColor(), 1.0f);
            player.getWorld().spawnParticle(Particle.DUST, loc, 3, 0.2, 0.3, 0.2, 0, dust);
        } else {
            player.getWorld().spawnParticle(trail.particle(), loc, 2, 0.2, 0.3, 0.2, 0);
        }
    }

    // --- Persistence ---

    private void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        var trailsSection = config.getConfigurationSection("trails");
        if (trailsSection != null) {
            for (String key : trailsSection.getKeys(false)) {
                UUID playerId = UUID.fromString(key);
                TrailType trail = TrailType.fromString(trailsSection.getString(key));
                if (trail != null) activeTrails.put(playerId, trail);
            }
        }

        var messagesSection = config.getConfigurationSection("join-messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                UUID playerId = UUID.fromString(key);
                String message = messagesSection.getString(key);
                if (message != null) joinMessages.put(playerId, message);
            }
        }

        plugin.getLogger().info("Loaded " + activeTrails.size() + " Diamond trails, "
                + joinMessages.size() + " join messages.");
    }

    private void saveData() {
        YamlConfiguration config = new YamlConfiguration();

        for (var entry : activeTrails.entrySet()) {
            config.set("trails." + entry.getKey(), entry.getValue().name().toLowerCase());
        }

        for (var entry : joinMessages.entrySet()) {
            config.set("join-messages." + entry.getKey(), entry.getValue());
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save diamond.yml", e);
        }
    }
}
