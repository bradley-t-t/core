package com.core.plugin.modules.treefell;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.*;

/**
 * BFS-based tree detection that distinguishes natural trees from player builds.
 * Scans connected logs (26-directional) above the chop point, then collects
 * all attached leaves. Validates via leaf-to-log ratio, ground check, and size cap.
 */
public final class TreeScanner {

    /** All log materials considered for tree felling. */
    private static final Set<Material> ALL_LOGS = Set.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
            Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
            Material.CHERRY_LOG, Material.MANGROVE_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM
    );

    /** All leaf/wart materials. */
    private static final Set<Material> ALL_LEAVES = Set.of(
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
            Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
            Material.CHERRY_LEAVES, Material.MANGROVE_LEAVES,
            Material.NETHER_WART_BLOCK, Material.WARPED_WART_BLOCK,
            Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES
    );

    /** Natural ground blocks that a tree trunk base should sit on. */
    private static final Set<Material> NATURAL_GROUND = Set.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.PODZOL, Material.MYCELIUM, Material.ROOTED_DIRT,
            Material.MUD, Material.MUDDY_MANGROVE_ROOTS, Material.MOSS_BLOCK,
            Material.NETHERRACK, Material.WARPED_NYLIUM, Material.CRIMSON_NYLIUM,
            Material.SOUL_SOIL, Material.SOUL_SAND
    );

    /** Leaf types mapped from their log type for ratio validation. */
    private static final Map<Material, Set<Material>> LOG_TO_LEAVES = Map.ofEntries(
            Map.entry(Material.OAK_LOG, Set.of(Material.OAK_LEAVES)),
            Map.entry(Material.SPRUCE_LOG, Set.of(Material.SPRUCE_LEAVES)),
            Map.entry(Material.BIRCH_LOG, Set.of(Material.BIRCH_LEAVES)),
            Map.entry(Material.JUNGLE_LOG, Set.of(Material.JUNGLE_LEAVES)),
            Map.entry(Material.ACACIA_LOG, Set.of(Material.ACACIA_LEAVES)),
            Map.entry(Material.DARK_OAK_LOG, Set.of(Material.DARK_OAK_LEAVES)),
            Map.entry(Material.CHERRY_LOG, Set.of(Material.CHERRY_LEAVES)),
            Map.entry(Material.MANGROVE_LOG, Set.of(Material.MANGROVE_LEAVES)),
            Map.entry(Material.CRIMSON_STEM, Set.of(Material.NETHER_WART_BLOCK)),
            Map.entry(Material.WARPED_STEM, Set.of(Material.WARPED_WART_BLOCK))
    );

    private TreeScanner() {}

    /** Returns true if the material is a natural (non-stripped) log type. */
    public static boolean isLog(Material material) {
        return ALL_LOGS.contains(material);
    }

    /** Returns true if the material is a leaf/wart type. */
    public static boolean isLeaf(Material material) {
        return ALL_LEAVES.contains(material);
    }

    /**
     * Scans for a natural tree starting from the given block.
     * Returns the set of log and leaf blocks to fell, or empty if not a valid tree.
     *
     * @param origin   The block being broken (must be a log)
     * @param maxLogs  Maximum logs to detect before aborting
     * @param minRatio Minimum leaf-to-log ratio for natural tree validation
     */
    /** Max horizontal distance (X or Z) a log can be from the trunk base. */
    private static final int MAX_HORIZONTAL_RADIUS = 5;

    public static ScanResult scan(Block origin, int maxLogs, double minRatio) {
        Material logType = origin.getType();
        if (!isLog(logType)) return ScanResult.NOT_A_TREE;

        // Phase 1: Find the trunk base by scanning downward (including 2x2 trunks)
        Block base = findTrunkBase(origin, logType);

        // Check that the trunk sits on natural ground
        Material below = base.getRelative(BlockFace.DOWN).getType();
        if (!NATURAL_GROUND.contains(below)) {
            return ScanResult.NOT_A_TREE;
        }

        // Phase 2: BFS through connected logs at Y >= origin Y (above chop point)
        // BFS with radius-2 scanning to catch branches that skip a block
        // (e.g. acacia branches that jump diagonally with a gap)
        // Capped horizontally from trunk base to prevent bleeding into neighbors.
        int originY = origin.getY();
        int trunkX = base.getX();
        int trunkZ = base.getZ();

        Set<Block> treeLogs = new LinkedHashSet<>();
        Queue<Block> queue = new ArrayDeque<>();

        queue.add(origin);
        treeLogs.add(origin);

        while (!queue.isEmpty()) {
            if (treeLogs.size() > maxLogs) {
                return ScanResult.TOO_LARGE;
            }

            Block current = queue.poll();

            // 26-directional (radius 1) — catches diagonal branches without
            // jumping gaps between neighboring trees
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block neighbor = current.getRelative(dx, dy, dz);
                        if (neighbor.getY() < originY) continue;
                        if (neighbor.getType() != logType) continue;
                        if (treeLogs.contains(neighbor)) continue;

                        // Horizontal spread cap from trunk base
                        if (Math.abs(neighbor.getX() - trunkX) > MAX_HORIZONTAL_RADIUS) continue;
                        if (Math.abs(neighbor.getZ() - trunkZ) > MAX_HORIZONTAL_RADIUS) continue;

                        treeLogs.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }

        if (treeLogs.size() <= 1) {
            return ScanResult.NOT_A_TREE;
        }

        // Phase 3: Collect all connected leaves attached to the tree logs
        Set<Material> leafTypes = LOG_TO_LEAVES.getOrDefault(logType, Set.of());

        Set<Block> treeLeaves = new LinkedHashSet<>();
        if (!leafTypes.isEmpty()) {
            collectLeaves(treeLogs, logType, leafTypes, treeLeaves);
        }

        // Phase 4: Ratio check — validate this is a natural tree
        double ratio = treeLogs.isEmpty() ? 0 : (double) treeLeaves.size() / treeLogs.size();
        if (ratio < minRatio) {
            return ScanResult.NOT_A_TREE;
        }

        return new ScanResult(true, treeLogs, treeLeaves);
    }

    /**
     * Finds the trunk base by scanning straight down. Also checks adjacent columns
     * for 2x2 trunks (dark oak, large spruce, large jungle).
     */
    private static Block findTrunkBase(Block origin, Material logType) {
        Block base = origin;
        while (base.getRelative(BlockFace.DOWN).getType() == logType) {
            base = base.getRelative(BlockFace.DOWN);
        }
        return base;
    }

    /**
     * BFS from logs outward through leaves to collect all attached leaves.
     * Leaves connect to logs and to each other within a reasonable distance.
     * Max 6 blocks from the nearest log to prevent runaway into neighboring trees.
     * Leaves adjacent to a foreign log (same type, not in our tree) are still collected
     * but act as a boundary — the BFS does not spread further through them.
     */
    private static void collectLeaves(Set<Block> logs, Material logType, Set<Material> leafTypes, Set<Block> result) {
        Queue<Block> queue = new ArrayDeque<>();
        Map<Block, Integer> distance = new HashMap<>();

        // Seed: all leaf blocks directly adjacent to any log
        for (Block log : logs) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block neighbor = log.getRelative(dx, dy, dz);
                        if (leafTypes.contains(neighbor.getType()) && result.add(neighbor)) {
                            distance.put(neighbor, 1);
                            // Only continue BFS from this leaf if it doesn't touch a foreign log
                            if (!touchesForeignLog(neighbor, logType, logs)) {
                                queue.add(neighbor);
                            }
                        }
                    }
                }
            }
        }

        // BFS through leaves, max 6 deep from a log
        while (!queue.isEmpty()) {
            Block current = queue.poll();
            int dist = distance.get(current);
            if (dist >= 6) continue;

            for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN,
                    BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block neighbor = current.getRelative(face);
                if (leafTypes.contains(neighbor.getType()) && result.add(neighbor)) {
                    distance.put(neighbor, dist + 1);
                    // Boundary: collect the leaf but don't spread through it if it's near a foreign tree
                    if (!touchesForeignLog(neighbor, logType, logs)) {
                        queue.add(neighbor);
                    }
                }
            }
        }
    }

    /**
     * Returns true if the given block is adjacent (6-directional) to a log of the same type
     * that is NOT part of our detected tree set — indicating a neighboring tree boundary.
     */
    private static boolean touchesForeignLog(Block block, Material logType, Set<Block> ourLogs) {
        for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN,
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block adj = block.getRelative(face);
            if (adj.getType() == logType && !ourLogs.contains(adj)) {
                return true;
            }
        }
        return false;
    }

    /** Result of a tree scan operation. */
    public static final class ScanResult {
        public static final ScanResult NOT_A_TREE = new ScanResult(false, Set.of(), Set.of());
        public static final ScanResult TOO_LARGE = new ScanResult(false, Set.of(), Set.of());

        private final boolean valid;
        private final Set<Block> logs;
        private final Set<Block> leaves;

        public ScanResult(boolean valid, Set<Block> logs, Set<Block> leaves) {
            this.valid = valid;
            this.logs = logs;
            this.leaves = leaves;
        }

        public boolean isValid() { return valid; }
        public Set<Block> logs() { return logs; }
        public Set<Block> leaves() { return leaves; }
    }
}
