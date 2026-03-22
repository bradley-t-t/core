package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Finds safe random locations within the world border and teleports players there.
 * Uses a tick-based search that loads one chunk per tick to avoid blocking.
 */
public final class WildTeleportService implements Service {

    private static final Set<Material> UNSAFE_BLOCKS = Set.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
            Material.CACTUS, Material.SWEET_BERRY_BUSH,
            Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.POWDER_SNOW, Material.POINTED_DRIPSTONE
    );

    private static final Set<Material> LIQUID_BLOCKS = Set.of(
            Material.WATER, Material.LAVA
    );

    private final CorePlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private int maxAttempts;
    private int minDistance;
    private int cooldownSeconds;

    public WildTeleportService(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        maxAttempts = plugin.getConfig().getInt("wild-teleport.max-attempts", 20);
        minDistance = plugin.getConfig().getInt("wild-teleport.min-distance", 200);
        cooldownSeconds = plugin.getConfig().getInt("wild-teleport.cooldown-seconds", 300);
    }

    @Override
    public void disable() {
        cooldowns.clear();
    }

    /** Standard wild teleport with failure message if no safe spot found. */
    public void wildTeleport(Player player) {
        Lang.send(player, "wild.searching");
        searchAndTeleport(player, 0, maxAttempts, false);
    }

    /** Guaranteed wild teleport for first join — never gives up. */
    public void wildTeleportGuaranteed(Player player) {
        Lang.send(player, "wild.searching");
        searchAndTeleport(player, 0, 100, true);
    }

    public int getRemainingCooldown(UUID playerId) {
        if (cooldownSeconds <= 0) return 0;
        Long lastUse = cooldowns.get(playerId);
        if (lastUse == null) return 0;
        long elapsed = (System.currentTimeMillis() - lastUse) / 1000;
        return Math.max(0, (int) (cooldownSeconds - elapsed));
    }

    public void markCooldown(UUID playerId) {
        cooldowns.put(playerId, System.currentTimeMillis());
    }

    /**
     * Tick-based recursive search. Each tick loads one chunk and checks safety.
     * Runs entirely on the main thread — no async deadlock risk.
     */
    private void searchAndTeleport(Player player, int attempt, int maxTries, boolean guaranteed) {
        if (!player.isOnline()) return;

        if (attempt >= maxTries) {
            if (guaranteed) {
                // Fallback: force-place somewhere, can't fail
                Location fallback = forceFindLocation(player.getWorld());
                completeWildTeleport(player, fallback, guaranteed);
            } else {
                Lang.send(player, "wild.failed");
            }
            return;
        }

        World world = player.getWorld();
        int radius = wildRadius();
        int x = randomCoord(radius);
        int z = randomCoord(radius);

        // Skip coords too close to center
        if (Math.abs(x) < minDistance && Math.abs(z) < minDistance) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> searchAndTeleport(player, attempt + 1, maxTries, guaranteed), 1L);
            return;
        }

        // Load chunk
        world.getChunkAt(x >> 4, z >> 4).load(true);

        int highestY = world.getHighestBlockYAt(x, z);
        if (highestY <= world.getMinHeight()) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> searchAndTeleport(player, attempt + 1, maxTries, guaranteed), 1L);
            return;
        }

        Location candidate = new Location(world, x + 0.5, highestY + 1, z + 0.5);
        if (isSafe(candidate)) {
            completeWildTeleport(player, candidate, guaranteed);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> searchAndTeleport(player, attempt + 1, maxTries, guaranteed), 1L);
        }
    }

    private void completeWildTeleport(Player player, Location location, boolean isFirstJoin) {
        TeleportService teleportService = plugin.services().get(TeleportService.class);
        if (teleportService != null) {
            teleportService.teleport(player, location);
        } else {
            player.teleport(location);
        }

        player.setNoDamageTicks(200);
        player.setFallDistance(0f);

        Lang.send(player, "wild.teleported");
        Lang.title(player, "title.wild", "title.wild-subtitle");

        if (isFirstJoin) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                Lang.send(player, "wild.welcome-1");
                Lang.send(player, "wild.welcome-2");
                Lang.send(player, "wild.welcome-3");
                Lang.title(player, "title.welcome", "title.welcome-subtitle",
                        20, 80, 20, new Object[0]);
            }, 40L);
        }
    }

    private int wildRadius() {
        WorldBorderService borderService = plugin.services().get(WorldBorderService.class);
        return borderService != null ? borderService.wildRadius() : 5000;
    }

    /**
     * Last-resort synchronous fallback. Picks random coords until we find ANY
     * solid block that isn't lava. Cannot fail — worst case uses world spawn offset.
     */
    private Location forceFindLocation(World world) {
        int radius = wildRadius();

        for (int attempt = 0; attempt < 50; attempt++) {
            int x = randomCoord(radius);
            int z = randomCoord(radius);

            world.getChunkAt(x >> 4, z >> 4).load(true);
            int highestY = world.getHighestBlockYAt(x, z);

            if (highestY > world.getMinHeight()) {
                Location loc = new Location(world, x + 0.5, highestY + 1, z + 0.5);
                Block below = loc.getBlock().getRelative(0, -1, 0);
                if (below.getType() != Material.LAVA) return loc;
            }
        }

        Location spawn = world.getSpawnLocation().clone();
        spawn.add(random.nextInt(200) - 100, 0, random.nextInt(200) - 100);
        spawn.setY(world.getHighestBlockYAt(spawn.getBlockX(), spawn.getBlockZ()) + 1);
        return spawn;
    }

    private boolean isSafe(Location location) {
        Block feet = location.getBlock();
        Block below = feet.getRelative(0, -1, 0);
        Block head = feet.getRelative(0, 1, 0);

        if (!below.getType().isSolid()) return false;
        if (UNSAFE_BLOCKS.contains(below.getType())) return false;
        return isPassable(feet) && isPassable(head);
    }

    private boolean isPassable(Block block) {
        Material type = block.getType();
        if (UNSAFE_BLOCKS.contains(type)) return false;
        if (LIQUID_BLOCKS.contains(type)) return false;
        return !type.isSolid();
    }

    private int randomCoord(int radius) {
        return random.nextInt(radius * 2) - radius;
    }
}
