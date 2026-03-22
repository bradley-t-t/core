package com.core.plugin.listener;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.treefell.TreeAnimator;
import com.core.plugin.modules.treefell.TreeScanner;
import com.core.plugin.service.ClaimService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for log breaks and triggers realistic tree felling.
 * Respects sneaking (disables felling), axe requirement, claim protection,
 * and creative mode. Prevents concurrent felling by the same player.
 */
public final class TreeFellListener implements Listener {

    private static final Set<Material> AXES = Set.of(
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    );

    private final CorePlugin plugin;
    private final Set<UUID> activeFellers = ConcurrentHashMap.newKeySet();

    public TreeFellListener(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("tree-felling.enabled", true)) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!TreeScanner.isLog(block.getType())) return;
        if (player.isSneaking()) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!AXES.contains(tool.getType())) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (!activeFellers.add(player.getUniqueId())) return;

        int maxLogs = plugin.getConfig().getInt("tree-felling.max-logs", 128);
        double minRatio = plugin.getConfig().getDouble("tree-felling.min-leaf-ratio", 1.0);
        int ticksPerLayer = plugin.getConfig().getInt("tree-felling.ticks-per-layer", 2);

        TreeScanner.ScanResult result = TreeScanner.scan(block, maxLogs, minRatio);

        if (!result.isValid()) {
            activeFellers.remove(player.getUniqueId());
            return;
        }

        // Check claim protection for all logs and leaves
        ClaimService claimService = plugin.services().get(ClaimService.class);
        if (claimService != null) {
            for (Block log : result.logs()) {
                if (!claimService.canInteract(player.getUniqueId(), log.getLocation())) {
                    activeFellers.remove(player.getUniqueId());
                    return;
                }
            }
            for (Block leaf : result.leaves()) {
                if (!claimService.canInteract(player.getUniqueId(), leaf.getLocation())) {
                    activeFellers.remove(player.getUniqueId());
                    return;
                }
            }
        }

        // Remove the origin block (already being broken by the event)
        Set<Block> logsToFell = result.logs();
        logsToFell.remove(block);
        Set<Block> leavesToFell = result.leaves();

        if (logsToFell.isEmpty() && leavesToFell.isEmpty()) {
            activeFellers.remove(player.getUniqueId());
            return;
        }

        TreeAnimator.animate(plugin, player, logsToFell, leavesToFell, ticksPerLayer);

        // Release lock after estimated completion
        int logLayers = (int) logsToFell.stream().mapToInt(Block::getY).distinct().count();
        int leafTicks = (leavesToFell.size() / 8) + 1;
        long releaseTicks = (long) (logLayers + leafTicks + 2) * ticksPerLayer;
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> activeFellers.remove(player.getUniqueId()), releaseTicks);
    }
}
