package com.core.plugin.modules.treefell;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles the animated felling of a detected tree. Breaks logs layer by layer
 * from the chop point upward, then clears leaves in batches.
 * Consumes tool durability per log, respecting the Unbreaking enchantment.
 */
public final class TreeAnimator {

    private static final int LEAVES_PER_TICK = 8;

    private TreeAnimator() {}

    /**
     * Animates felling the given tree (logs + leaves).
     * Logs break bottom-to-top by Y-layer, then leaves break in batches.
     *
     * @param plugin        Plugin instance for scheduling
     * @param player        The player who chopped the tree
     * @param logs          Set of log blocks to fell
     * @param leaves        Set of leaf blocks to clear
     * @param ticksPerLayer Ticks between each layer breaking
     */
    public static void animate(Plugin plugin, Player player, Set<Block> logs, Set<Block> leaves, int ticksPerLayer) {
        // Group logs by Y-level, sorted ascending (bottom-to-top)
        TreeMap<Integer, List<Block>> logLayers = new TreeMap<>();
        for (Block log : logs) {
            logLayers.computeIfAbsent(log.getY(), k -> new ArrayList<>()).add(log);
        }

        // Group leaves by Y-level, sorted descending (top-to-bottom, like they're falling)
        TreeMap<Integer, List<Block>> leafLayers = new TreeMap<>(Comparator.reverseOrder());
        for (Block leaf : leaves) {
            leafLayers.computeIfAbsent(leaf.getY(), k -> new ArrayList<>()).add(leaf);
        }

        // Flatten leaf layers into a single ordered list for batch processing
        List<Block> orderedLeaves = new ArrayList<>();
        for (List<Block> layer : leafLayers.values()) {
            orderedLeaves.addAll(layer);
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        UUID playerId = player.getUniqueId();

        new BukkitRunnable() {
            private final Iterator<Map.Entry<Integer, List<Block>>> logIt = logLayers.entrySet().iterator();
            private boolean logsFinished = false;
            private int leafIndex = 0;

            @Override
            public void run() {
                Player p = plugin.getServer().getPlayer(playerId);
                if (p == null) {
                    cancel();
                    return;
                }

                // Phase 1: Break logs layer by layer
                if (!logsFinished) {
                    if (!logIt.hasNext()) {
                        logsFinished = true;
                    } else {
                        breakLogLayer(logIt.next().getValue(), p, tool);
                        return;
                    }
                }

                // Phase 2: Break leaves in batches
                if (leafIndex >= orderedLeaves.size()) {
                    cancel();
                    return;
                }

                int end = Math.min(leafIndex + LEAVES_PER_TICK, orderedLeaves.size());
                for (int i = leafIndex; i < end; i++) {
                    Block leaf = orderedLeaves.get(i);
                    if (!TreeScanner.isLeaf(leaf.getType())) continue;

                    Location center = leaf.getLocation().add(0.5, 0.5, 0.5);

                    // Leaf particles
                    leaf.getWorld().spawnParticle(
                            Particle.BLOCK,
                            center, 8,
                            0.3, 0.3, 0.3, 0.05,
                            leaf.getType().createBlockData()
                    );

                    leaf.getWorld().playSound(center, Sound.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 0.4f, randomPitch());
                    leaf.breakNaturally(tool);
                }
                leafIndex = end;
            }
        }.runTaskTimer(plugin, 1L, ticksPerLayer);
    }

    private static void breakLogLayer(List<Block> layer, Player player, ItemStack tool) {
        for (Block block : layer) {
            if (!TreeScanner.isLog(block.getType())) continue;

            Material logType = block.getType();
            Location center = block.getLocation().add(0.5, 0.5, 0.5);

            // Wood chip particles
            block.getWorld().spawnParticle(
                    Particle.BLOCK,
                    center, 20,
                    0.3, 0.3, 0.3, 0.1,
                    logType.createBlockData()
            );

            // Falling dust
            block.getWorld().spawnParticle(
                    Particle.FALLING_DUST,
                    center.clone().add(0, -0.3, 0), 5,
                    0.2, 0.0, 0.2, 0.02,
                    logType.createBlockData()
            );

            block.getWorld().playSound(center, Sound.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 0.8f, randomPitch());
            block.breakNaturally(tool);
            applyDurability(player, tool);
        }

        // Timber creak sound
        if (!layer.isEmpty()) {
            Location layerCenter = layer.get(0).getLocation().add(0.5, 0.5, 0.5);
            layer.get(0).getWorld().playSound(layerCenter, Sound.BLOCK_WOOD_FALL, SoundCategory.BLOCKS, 1.0f, 0.6f);
        }
    }

    /** Applies durability damage to the tool, respecting Unbreaking enchantment. */
    private static void applyDurability(Player player, ItemStack tool) {
        if (tool == null || tool.getType().isAir()) return;
        if (!(tool.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable)) return;

        int unbreaking = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0) {
            double chance = 1.0 / (unbreaking + 1);
            if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        }

        org.bukkit.inventory.meta.Damageable meta = (org.bukkit.inventory.meta.Damageable) tool.getItemMeta();
        meta.setDamage(meta.getDamage() + 1);
        tool.setItemMeta(meta);

        if (meta.getDamage() >= tool.getType().getMaxDurability()) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        }
    }

    private static float randomPitch() {
        return 0.8f + ThreadLocalRandom.current().nextFloat() * 0.4f;
    }
}
