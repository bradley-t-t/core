package com.core.plugin.claim;

import com.core.plugin.CorePlugin;
import com.core.plugin.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders particle borders for claim selections and established claims.
 * Particles only render while the player holds a golden shovel.
 * Double-density particles with taller corner pillars for visibility.
 */
public final class ClaimVisualizer {

    private static final int MAX_PARTICLES_PER_RENDER = 2000;
    private static final int RENDER_DISTANCE = 64;
    private static final int REPEAT_INTERVAL_TICKS = 5;
    private static final int CORNER_PILLAR_HEIGHT = 6;
    private static final float PARTICLE_SIZE = 1.5f;
    private static final double PARTICLE_SPACING = 0.5;

    private static final Color SELECTION_COLOR = Color.fromRGB(200, 50, 50);
    private static final Color OWN_CLAIM_COLOR = Color.fromRGB(0, 200, 0);
    private static final Color OTHER_CLAIM_COLOR = Color.fromRGB(230, 200, 0);
    private static final Color PATH_COLOR = Color.fromRGB(100, 200, 255);     // Light blue path

    private static final int PATH_PARTICLE_SPACING = 2;
    private static final int PATH_DURATION_TICKS = 1200; // 60 seconds
    private static final float PATH_PARTICLE_SIZE = 1.2f;

    private final CorePlugin plugin;
    private final Map<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pathTasks = new ConcurrentHashMap<>();

    public ClaimVisualizer(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Show gold particles for the in-progress selection. Auto-cancels when shovel is unequipped. */
    public void showSelection(Player player, ClaimSelection selection) {
        if (!selection.isComplete()) return;

        Location c1 = selection.getCorner1();
        Location c2 = selection.getCorner2();
        int minX = Math.min(c1.getBlockX(), c2.getBlockX());
        int minZ = Math.min(c1.getBlockZ(), c2.getBlockZ());
        int maxX = Math.max(c1.getBlockX(), c2.getBlockX());
        int maxZ = Math.max(c1.getBlockZ(), c2.getBlockZ());

        startShovelTask(player, minX, minZ, maxX, maxZ, SELECTION_COLOR);
    }

    /** Show claim border — green if yours, yellow if someone else's. Auto-cancels when shovel is unequipped. */
    public void showClaim(Player player, ClaimRegion claim) {
        Color color = claim.ownerId().equals(player.getUniqueId()) ? OWN_CLAIM_COLOR : OTHER_CLAIM_COLOR;
        startShovelTask(player, claim.minX(), claim.minZ(), claim.maxX(), claim.maxZ(), color);
    }

    /** Flash green particles briefly on claim creation. Ignores shovel check. */
    public void flashClaim(Player player, ClaimRegion claim) {
        cancelAll(player.getUniqueId());
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) { cancelAll(player.getUniqueId()); return; }
            spawnBorder(player, claim.minX(), claim.minZ(), claim.maxX(), claim.maxZ(), OWN_CLAIM_COLOR);
        }, 0L, REPEAT_INTERVAL_TICKS);
        activeTasks.put(player.getUniqueId(), task);
        Bukkit.getScheduler().runTaskLater(plugin, () -> cancelAll(player.getUniqueId()), 100L);
    }

    /**
     * Start a repeating particle task that auto-cancels when:
     * - Player goes offline
     * - Player stops holding a golden shovel
     */
    private void startShovelTask(Player player, int minX, int minZ, int maxX, int maxZ, Color color) {
        cancelAll(player.getUniqueId());
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) { cancelAll(player.getUniqueId()); return; }
            if (!isHoldingShovel(player)) { cancelAll(player.getUniqueId()); return; }
            spawnBorder(player, minX, minZ, maxX, maxZ, color);
        }, 0L, REPEAT_INTERVAL_TICKS);
        activeTasks.put(player.getUniqueId(), task);
    }

    /**
     * Animated particle trail from player to claim. Features:
     * - Floating orbs that bob up and down in a sine wave
     * - Particles get denser near the player, sparser far away
     * - Animated sweep — particles shift forward over time like a flowing river
     * - Beacon column at the destination
     * - Stops when player enters the claim bounds (not just center)
     */
    public void pathfindToClaim(Player player, ClaimRegion claim) {
        cancelPath(player.getUniqueId());

        var world = Bukkit.getWorld(claim.worldName());
        if (world == null) return;

        int targetX = (claim.minX() + claim.maxX()) / 2;
        int targetZ = (claim.minZ() + claim.maxZ()) / 2;

        var trailDust = new DustOptions(PATH_COLOR, PATH_PARTICLE_SIZE);
        var beaconDust = new DustOptions(Color.fromRGB(255, 255, 255), 1.8f);
        final long[] tick = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) { cancelPath(player.getUniqueId()); return; }
            tick[0]++;

            // Check if player has entered the claim bounds
            int px = player.getLocation().getBlockX();
            int pz = player.getLocation().getBlockZ();
            if (claim.contains(claim.worldName(), px, pz)) {
                cancelPath(player.getUniqueId());
                Lang.send(player, "claim.pathfind-arrived", "name", claim.name());
                com.core.plugin.util.SoundUtil.success(player);
                return;
            }

            double startX = player.getLocation().getX();
            double startZ = player.getLocation().getZ();
            double dx = targetX + 0.5 - startX;
            double dz = targetZ + 0.5 - startZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double nx = dx / distance;
            double nz = dz / distance;

            double renderDist = Math.min(distance, 60);
            double animOffset = (tick[0] % 40) * 0.5; // Flowing animation

            // Trail: floating orbs with sine wave bob
            int count = 0;
            for (double d = 3; d < renderDist && count < 300; d += 1.5) {
                double t = d + animOffset;
                double orbX = startX + nx * d;
                double orbZ = startZ + nz * d;
                int groundY = findGroundY(world, (int) orbX, (int) orbZ) + 1;

                // Sine wave bob — each orb floats at a different height
                double bobHeight = 0.8 + Math.sin(t * 0.3) * 0.4;

                // Main trail particle
                player.spawnParticle(Particle.DUST,
                        new Location(world, orbX, groundY + bobHeight, orbZ),
                        1, 0, 0, 0, 0, trailDust);

                // Every 4th orb gets a second particle slightly offset for thickness
                if (count % 4 == 0) {
                    double perpX = -nz * 0.3;
                    double perpZ = nx * 0.3;
                    player.spawnParticle(Particle.DUST,
                            new Location(world, orbX + perpX, groundY + bobHeight - 0.2, orbZ + perpZ),
                            1, 0, 0, 0, 0, trailDust);
                    player.spawnParticle(Particle.DUST,
                            new Location(world, orbX - perpX, groundY + bobHeight - 0.2, orbZ - perpZ),
                            1, 0, 0, 0, 0, trailDust);
                }
                count++;
            }

            // Beacon at destination — tall column of white particles pulsing
            if (distance < 60) {
                int beaconY = findGroundY(world, targetX, targetZ) + 1;
                double pulseHeight = 3 + Math.sin(tick[0] * 0.15) * 2;
                for (double y = 0; y < pulseHeight; y += 0.5) {
                    // Slight spiral
                    double angle = y * 0.8 + tick[0] * 0.1;
                    double bx = targetX + 0.5 + Math.cos(angle) * 0.3;
                    double bz = targetZ + 0.5 + Math.sin(angle) * 0.3;
                    player.spawnParticle(Particle.DUST,
                            new Location(world, bx, beaconY + y, bz),
                            1, 0, 0, 0, 0, beaconDust);
                }
            }
        }, 0L, 4L); // Every 4 ticks

        pathTasks.put(player.getUniqueId(), task);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pathTasks.containsKey(player.getUniqueId())) {
                cancelPath(player.getUniqueId());
                if (player.isOnline()) {
                    Lang.send(player, "claim.pathfind-expired");
                }
            }
        }, PATH_DURATION_TICKS);
    }

    public void cancelPath(UUID playerId) {
        BukkitTask task = pathTasks.remove(playerId);
        if (task != null) task.cancel();
    }

    public void cancelAll(UUID playerId) {
        BukkitTask task = activeTasks.remove(playerId);
        if (task != null) task.cancel();
        cancelPath(playerId);
    }

    /**
     * Walks the perimeter spawning dust particles at half-block intervals.
     * Corners get tall pillars. Multiple Y levels for wall effect.
     */
    private void spawnBorder(Player player, int minX, int minZ, int maxX, int maxZ, Color color) {
        var dust = new DustOptions(color, PARTICLE_SIZE);
        var world = player.getWorld();
        double pX = player.getLocation().getX();
        double pZ = player.getLocation().getZ();
        double renderDistSq = (double) RENDER_DISTANCE * RENDER_DISTANCE;
        int count = 0;

        // Walk edges at 0.5 block spacing, Y = highest block at each position + 1
        for (double x = minX; x <= maxX + 0.01 && count < MAX_PARTICLES_PER_RENDER; x += PARTICLE_SPACING) {
            count += emitAtGround(player, world, x, minZ, pX, pZ, renderDistSq, dust);
            count += emitAtGround(player, world, x, maxZ + 1, pX, pZ, renderDistSq, dust);
        }
        for (double z = minZ + PARTICLE_SPACING; z < maxZ + 1 && count < MAX_PARTICLES_PER_RENDER; z += PARTICLE_SPACING) {
            count += emitAtGround(player, world, minX, z, pX, pZ, renderDistSq, dust);
            count += emitAtGround(player, world, maxX + 1, z, pX, pZ, renderDistSq, dust);
        }

        // Corner pillars at ground level
        int[][] corners = {{minX, minZ}, {minX, maxZ + 1}, {maxX + 1, minZ}, {maxX + 1, maxZ + 1}};
        for (int[] c : corners) {
            double groundY = findGroundY(world, c[0], c[1]) + 1;
            for (int dy = 0; dy <= CORNER_PILLAR_HEIGHT && count < MAX_PARTICLES_PER_RENDER; dy++) {
                count += emit(player, world, c[0], groundY + dy, c[1], pX, pZ, renderDistSq, dust);
                count += emit(player, world, c[0], groundY + dy + 0.5, c[1], pX, pZ, renderDistSq, dust);
            }
        }
    }

    private static final Set<Material> GROUND_BLOCKS = Set.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.DIRT_PATH, Material.COARSE_DIRT,
            Material.ROOTED_DIRT, Material.PODZOL, Material.MYCELIUM, Material.MUD,
            Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.CLAY,
            Material.STONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE,
            Material.DEEPSLATE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
            Material.SANDSTONE, Material.RED_SANDSTONE,
            Material.SNOW_BLOCK, Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE,
            Material.TERRACOTTA, Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA,
            Material.YELLOW_TERRACOTTA, Material.RED_TERRACOTTA, Material.BROWN_TERRACOTTA,
            Material.LIGHT_GRAY_TERRACOTTA,
            Material.NETHERRACK, Material.SOUL_SAND, Material.SOUL_SOIL,
            Material.BASALT, Material.BLACKSTONE, Material.END_STONE,
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
            Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS,
            Material.CHERRY_PLANKS, Material.BAMBOO_PLANKS, Material.MANGROVE_PLANKS,
            Material.CRIMSON_PLANKS, Material.WARPED_PLANKS,
            Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.BRICKS,
            Material.COBBLED_DEEPSLATE, Material.POLISHED_DEEPSLATE,
            Material.SMOOTH_STONE, Material.CUT_SANDSTONE,
            Material.BEDROCK, Material.OBSIDIAN
    );

    private int emitAtGround(Player player, org.bukkit.World world, double x, double z,
                              double pX, double pZ, double renderDistSq, DustOptions dust) {
        double dx = x - pX, dz = z - pZ;
        if (dx * dx + dz * dz > renderDistSq) return 0;

        double groundY = findGroundY(world, (int) x, (int) z) + 1.0;
        player.spawnParticle(Particle.DUST, new Location(world, x, groundY, z), 1, 0, 0, 0, 0, dust);
        player.spawnParticle(Particle.DUST, new Location(world, x, groundY + 0.5, z), 1, 0, 0, 0, 0, dust);
        return 2;
    }

    /** Scan downward from the highest block to find actual ground (not trees/leaves/flowers). */
    private int findGroundY(org.bukkit.World world, int x, int z) {
        int topY = world.getHighestBlockYAt(x, z);
        for (int y = topY; y > world.getMinHeight(); y--) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (GROUND_BLOCKS.contains(type)) return y;
            // Also accept any solid opaque block that isn't vegetation
            if (type.isSolid() && type.isOccluding()) return y;
        }
        return topY; // fallback
    }

    private int emit(Player player, org.bukkit.World world, double x, double y, double z,
                     double pX, double pZ, double renderDistSq, DustOptions dust) {
        double dx = x - pX, dz = z - pZ;
        if (dx * dx + dz * dz > renderDistSq) return 0;
        player.spawnParticle(Particle.DUST, new Location(world, x, y, z), 1, 0, 0, 0, 0, dust);
        return 1;
    }

    private boolean isHoldingShovel(Player player) {
        var main = player.getInventory().getItemInMainHand();
        var off = player.getInventory().getItemInOffHand();
        return main.getType() == Material.GOLDEN_SHOVEL || off.getType() == Material.GOLDEN_SHOVEL;
    }
}
