package com.core.plugin.listener;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.claim.ClaimRegion;
import com.core.plugin.service.ClaimService;
import com.core.plugin.lang.Lang;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive grief protection for claimed land. Cancels block modification,
 * container access, entity interaction, and environmental damage from untrusted
 * sources inside claim boundaries.
 */
public final class ClaimProtectionListener implements Listener {

    private static final long MESSAGE_COOLDOWN_MS = 2000;

    private static final Set<Material> PROTECTED_CONTAINERS = Set.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.HOPPER, Material.DROPPER, Material.DISPENSER,
            Material.BREWING_STAND, Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX, Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX, Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX,
            Material.LECTERN, Material.CHISELED_BOOKSHELF,
            Material.DECORATED_POT, Material.CRAFTER
    );

    private static final Set<Material> PROTECTED_REDSTONE = Set.of(
            Material.LEVER,
            Material.STONE_BUTTON, Material.OAK_BUTTON, Material.SPRUCE_BUTTON,
            Material.BIRCH_BUTTON, Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON,
            Material.DARK_OAK_BUTTON, Material.CHERRY_BUTTON, Material.BAMBOO_BUTTON,
            Material.MANGROVE_BUTTON, Material.CRIMSON_BUTTON, Material.WARPED_BUTTON,
            Material.POLISHED_BLACKSTONE_BUTTON,
            Material.REPEATER, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR,
            Material.NOTE_BLOCK
    );

    private static final Set<Material> PROTECTED_DOORS = Set.of(
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR,
            Material.JUNGLE_DOOR, Material.ACACIA_DOOR, Material.DARK_OAK_DOOR,
            Material.CHERRY_DOOR, Material.BAMBOO_DOOR, Material.MANGROVE_DOOR,
            Material.CRIMSON_DOOR, Material.WARPED_DOOR,
            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR,
            Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR,
            Material.CHERRY_TRAPDOOR, Material.BAMBOO_TRAPDOOR, Material.MANGROVE_TRAPDOOR,
            Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR,
            Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE,
            Material.JUNGLE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE,
            Material.CHERRY_FENCE_GATE, Material.BAMBOO_FENCE_GATE, Material.MANGROVE_FENCE_GATE,
            Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE
    );

    private final CorePlugin plugin;
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerCurrentClaim = new ConcurrentHashMap<>();

    public ClaimProtectionListener(CorePlugin plugin) {
        this.plugin = plugin;
    }

    // --- Block Modification ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (shouldDeny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            sendDenied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (shouldDeny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            sendDenied(event.getPlayer());
        }
    }

    // --- Explosions ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isInsideClaim(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isInsideClaim(block.getLocation()));
    }

    // --- Fire & Liquid ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isInsideClaim(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getNewState().getType() == Material.FIRE
                && isInsideClaim(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (event.getBlock().isLiquid() && isInsideClaim(event.getToBlock().getLocation())) {
            ClaimRegion sourceClaim = getClaimService().getClaimAt(event.getBlock().getLocation());
            ClaimRegion targetClaim = getClaimService().getClaimAt(event.getToBlock().getLocation());
            // Only cancel if the liquid is flowing INTO a different claim (or into a claim from unclaimed)
            if (targetClaim != null && (sourceClaim == null || !sourceClaim.claimId().equals(targetClaim.claimId()))) {
                event.setCancelled(true);
            }
        }
    }

    // --- Pistons ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            Location destination = block.getRelative(event.getDirection()).getLocation();
            if (isInsideNonSourceClaim(event.getBlock().getLocation(), destination)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            Location destination = block.getRelative(event.getDirection()).getLocation();
            if (isInsideNonSourceClaim(event.getBlock().getLocation(), destination)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // --- Player Interaction ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.PHYSICAL) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Material type = clicked.getType();
        boolean isProtected = PROTECTED_CONTAINERS.contains(type)
                || PROTECTED_REDSTONE.contains(type)
                || PROTECTED_DOORS.contains(type);

        if (isProtected && shouldDeny(event.getPlayer(), clicked.getLocation())) {
            event.setCancelled(true);
            sendDenied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        boolean isProtectedEntity = entity instanceof Villager
                || entity instanceof ArmorStand
                || entity instanceof ItemFrame;

        if (isProtectedEntity && shouldDeny(event.getPlayer(), entity.getLocation())) {
            event.setCancelled(true);
            sendDenied(event.getPlayer());
        }
    }

    // --- Buckets ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (shouldDeny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            sendDenied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (shouldDeny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            sendDenied(event.getPlayer());
        }
    }

    // --- Entity Grief ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        boolean isMobGrief = entity instanceof Enderman || entity instanceof Wither;
        if (isMobGrief && isInsideClaim(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Entity remover = event.getRemover();
        if (remover instanceof Player player) {
            if (shouldDeny(player, event.getEntity().getLocation())) {
                event.setCancelled(true);
                sendDenied(player);
            }
        } else if (isInsideClaim(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        boolean isProtectedEntity = victim instanceof Animals
                || victim instanceof ArmorStand;
        if (!isProtectedEntity) return;

        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker != null && shouldDeny(attacker, victim.getLocation())) {
            event.setCancelled(true);
            sendDenied(attacker);
        }
    }

    // --- Vehicles ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (event.getAttacker() instanceof Player player) {
            if (shouldDeny(player, event.getVehicle().getLocation())) {
                event.setCancelled(true);
                sendDenied(player);
            }
        }
    }

    // --- Hanging entities (placement) ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() != null && shouldDeny(event.getPlayer(), event.getEntity().getLocation())) {
            event.setCancelled(true);
            sendDenied(event.getPlayer());
        }
    }

    // --- Armor stand manipulation ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (shouldDeny(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
            sendDenied(event.getPlayer());
        }
    }

    // --- Crop trampling (farmland) ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFarmlandTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.FARMLAND) {
            if (shouldDeny(event.getPlayer(), event.getClickedBlock().getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    // --- Claim enter/exit titles ---

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Only check when crossing a block boundary
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        ClaimService cs = getClaimService();
        if (cs == null) return;

        ClaimRegion nowIn = cs.getClaimAt(event.getTo());
        UUID nowClaimId = nowIn != null ? nowIn.claimId() : null;
        UUID prevClaimId = playerCurrentClaim.get(player.getUniqueId());

        // No change
        if (java.util.Objects.equals(nowClaimId, prevClaimId)) return;

        // Left a claim
        if (prevClaimId != null && nowClaimId == null) {
            playerCurrentClaim.remove(player.getUniqueId());
            Lang.actionBar(player, "claim.exit-claim");
        }
        // Entered a claim
        else if (nowClaimId != null && !nowClaimId.equals(prevClaimId)) {
            playerCurrentClaim.put(player.getUniqueId(), nowClaimId);
            String ownerName = org.bukkit.Bukkit.getOfflinePlayer(nowIn.ownerId()).getName();
            if (ownerName == null) ownerName = "Unknown";

            if (nowIn.ownerId().equals(player.getUniqueId())) {
                Lang.actionBar(player, "claim.enter-own-claim", "name", nowIn.name());
            } else {
                Lang.actionBar(player, "claim.enter-claim", "owner", ownerName, "name", nowIn.name());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerCurrentClaim.remove(playerId);
        lastMessageTime.remove(playerId);
    }

    // --- Helpers ---

    private boolean shouldDeny(Player player, Location location) {
        ClaimService claimService = getClaimService();
        return claimService != null && !claimService.canInteract(player.getUniqueId(), location);
    }

    private boolean isInsideClaim(Location location) {
        ClaimService claimService = getClaimService();
        return claimService != null && claimService.getClaimAt(location) != null;
    }

    /**
     * Returns true if the destination block is inside a claim that differs from
     * the claim at the source location (prevents cross-claim piston manipulation).
     */
    private boolean isInsideNonSourceClaim(Location source, Location destination) {
        ClaimService claimService = getClaimService();
        if (claimService == null) return false;

        ClaimRegion destClaim = claimService.getClaimAt(destination);
        if (destClaim == null) return false;

        ClaimRegion sourceClaim = claimService.getClaimAt(source);
        return sourceClaim == null || !sourceClaim.claimId().equals(destClaim.claimId());
    }

    /** Resolves the actual player behind a damage event, unwrapping projectile owners. */
    private Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    /** Sends the claim-protected action bar with a cooldown to prevent spam. */
    private void sendDenied(Player player) {
        long now = System.currentTimeMillis();
        Long lastSent = lastMessageTime.get(player.getUniqueId());
        if (lastSent != null && now - lastSent < MESSAGE_COOLDOWN_MS) return;

        lastMessageTime.put(player.getUniqueId(), now);
        Lang.actionBar(player, "claim.protected");
    }

    private ClaimService getClaimService() {
        return plugin.services().get(ClaimService.class);
    }
}
