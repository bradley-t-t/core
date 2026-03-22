package com.core.plugin.listener;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.claim.ClaimRegion;
import com.core.plugin.modules.claim.ClaimSelection;
import com.core.plugin.service.ClaimService;
import com.core.plugin.modules.claim.ClaimVisualizer;
import com.core.plugin.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles golden shovel interactions for the claim system:
 * - Left-click: set corner 1
 * - Right-click: set corner 2
 * - Shift + right-click: show claim info at location
 * - Holding shovel: renders nearby claim borders
 * - Switching away: cancels particle rendering
 */
public final class ClaimWandListener implements Listener {

    private static final long CHECK_INTERVAL_TICKS = 10L;

    private final CorePlugin plugin;
    private final Map<UUID, Integer> shovelCheckTasks = new ConcurrentHashMap<>();

    public ClaimWandListener(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getItem().getType() != Material.GOLDEN_SHOVEL) return;
        if (event.getClickedBlock() == null) return;

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;

        ClaimService claimService = plugin.services().get(ClaimService.class);
        if (claimService == null) return;

        Player player = event.getPlayer();
        Location blockLocation = event.getClickedBlock().getLocation();

        event.setCancelled(true);

        // Shift + right-click: show claim info
        if (action == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            showClaimInfo(player, blockLocation, claimService);
            return;
        }

        ClaimSelection selection = claimService.getOrCreateSelection(player.getUniqueId());

        if (action == Action.LEFT_CLICK_BLOCK) {
            selection.setCorner1(blockLocation);
            Lang.send(player, "claim.corner-1-set",
                    "x", blockLocation.getBlockX(), "z", blockLocation.getBlockZ());
        } else {
            selection.setCorner2(blockLocation);
            Lang.send(player, "claim.corner-2-set",
                    "x", blockLocation.getBlockX(), "z", blockLocation.getBlockZ());
        }

        if (selection.isComplete()) {
            claimService.visualizer().showSelection(player, selection);

            Location c1 = selection.getCorner1();
            Location c2 = selection.getCorner2();
            int width = Math.abs(c1.getBlockX() - c2.getBlockX()) + 1;
            int length = Math.abs(c1.getBlockZ() - c2.getBlockZ()) + 1;

            Lang.actionBar(player, "claim.selection-area",
                    "width", width, "length", length, "area", width * length);

            // If selection overlaps the player's own claim, hint to use /claim resize
            ClaimRegion atCorner1 = claimService.getClaimAt(c1);
            ClaimRegion atCorner2 = claimService.getClaimAt(c2);
            ClaimRegion ownOverlap = null;
            if (atCorner1 != null && atCorner1.ownerId().equals(player.getUniqueId())) {
                ownOverlap = atCorner1;
            } else if (atCorner2 != null && atCorner2.ownerId().equals(player.getUniqueId())) {
                ownOverlap = atCorner2;
            }
            if (ownOverlap != null) {
                Lang.send(player, "claim.resize-hint", "name", ownOverlap.name());
            }
        }

        startShovelCheck(player, claimService);
    }

    /** When a player switches held item, cancel visualizations if no longer holding shovel. */
    @EventHandler
    public void onItemSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        var newItem = player.getInventory().getItem(event.getNewSlot());

        if (newItem == null || newItem.getType() != Material.GOLDEN_SHOVEL) {
            ClaimService claimService = plugin.services().get(ClaimService.class);
            if (claimService != null) {
                claimService.visualizer().cancelAll(player.getUniqueId());
            }
            stopShovelCheck(player.getUniqueId());
        } else {
            ClaimService claimService = plugin.services().get(ClaimService.class);
            if (claimService != null) {
                // Immediately show claim at current location + start the repeating check
                ClaimRegion claim = claimService.getClaimAt(player.getLocation());
                if (claim != null) {
                    claimService.visualizer().showClaim(player, claim);
                }
                startShovelCheck(player, claimService);
            }
        }
    }

    private void showClaimInfo(Player player, Location location, ClaimService claimService) {
        ClaimRegion claim = claimService.getClaimAt(location);
        if (claim == null) {
            Lang.send(player, "claim.not-in-claim");
            return;
        }

        String ownerName = Bukkit.getOfflinePlayer(claim.ownerId()).getName();
        if (ownerName == null) ownerName = claim.ownerId().toString();

        String trustedNames = claim.trustedPlayerIds().isEmpty() ? "none"
                : claim.trustedPlayerIds().stream()
                .map(uuid -> {
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    return name != null ? name : uuid.toString().substring(0, 8);
                })
                .reduce((a, b) -> a + ", " + b).orElse("none");

        Lang.send(player, "claim.info-header");
        Lang.send(player, "claim.info-name", "name", claim.name());
        Lang.send(player, "claim.info-owner", "player", ownerName);
        Lang.send(player, "claim.info-area",
                "width", claim.width(), "length", claim.length(), "area", claim.area());
        Lang.send(player, "claim.info-trusted", "players", trustedNames);
    }

    /** Start a repeating task that shows nearby claim borders while the shovel is held. */
    private void startShovelCheck(Player player, ClaimService claimService) {
        UUID playerId = player.getUniqueId();
        if (shovelCheckTasks.containsKey(playerId)) return;

        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) { stopShovelCheck(playerId); return; }

            var mainHand = player.getInventory().getItemInMainHand();
            if (mainHand.getType() != Material.GOLDEN_SHOVEL) {
                claimService.visualizer().cancelAll(playerId);
                stopShovelCheck(playerId);
                return;
            }

            // Show the claim the player is standing in (if any)
            ClaimRegion claim = claimService.getClaimAt(player.getLocation());
            if (claim != null) {
                claimService.visualizer().showClaim(player, claim);
            }
        }, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS).getTaskId();

        shovelCheckTasks.put(playerId, taskId);
    }

    private void stopShovelCheck(UUID playerId) {
        Integer taskId = shovelCheckTasks.remove(playerId);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }
}
