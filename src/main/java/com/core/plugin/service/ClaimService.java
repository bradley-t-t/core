package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.claim.*;
import com.core.plugin.modules.rank.RankLevel;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central service for land claim creation, validation, and access control.
 * Claims are persisted via {@link ClaimDataManager}, spatially indexed by {@link ClaimIndex},
 * and visualized through {@link ClaimVisualizer}.
 */
public final class ClaimService implements Service {

    private final CorePlugin plugin;
    private final ClaimDataManager dataManager;
    private final ClaimIndex index;
    private final ClaimVisualizer visualizer;
    private final Map<UUID, ClaimSelection> activeSelections = new ConcurrentHashMap<>();
    private final Map<UUID, List<ClaimRegion>> claimsByOwner = new HashMap<>();
    private final List<ClaimRegion> allClaims = new ArrayList<>();

    public ClaimService(CorePlugin plugin) {
        this.plugin = plugin;
        this.dataManager = new ClaimDataManager(plugin);
        this.index = new ClaimIndex();
        this.visualizer = new ClaimVisualizer(plugin);
    }

    @Override
    public void enable() {
        List<ClaimRegion> loaded = dataManager.loadAll();
        for (ClaimRegion claim : loaded) {
            allClaims.add(claim);
            index.add(claim);
            claimsByOwner.computeIfAbsent(claim.ownerId(), k -> new ArrayList<>()).add(claim);
        }
        plugin.getLogger().info("Loaded " + allClaims.size() + " land claims.");
    }

    @Override
    public void disable() {
        dataManager.saveAll(allClaims);
        activeSelections.clear();
        allClaims.clear();
        claimsByOwner.clear();
        index.clear();
    }

    /** Returns the claim containing the given location, or null if unclaimed. */
    public ClaimRegion getClaimAt(Location location) {
        if (location.getWorld() == null) return null;
        return index.getClaimAt(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
    }

    /**
     * Determines whether a player may interact at the given location.
     * Returns true if the location is unclaimed, the player is the owner/trusted,
     * or the player holds OPERATOR rank.
     */
    public boolean canInteract(UUID playerId, Location location) {
        ClaimRegion claim = getClaimAt(location);
        if (claim == null) return true;
        if (claim.canInteract(playerId)) return true;

        RankService rankService = plugin.services().get(RankService.class);
        return rankService != null && rankService.getLevel(playerId).isAtLeast(RankLevel.MODERATOR);
    }

    /** Attempts to create a claim from the player's active selection with a given name. */
    public CreateResult createClaim(UUID playerId, ClaimSelection selection, String name) {
        if (!selection.isComplete()) return CreateResult.INCOMPLETE;

        Location c1 = selection.getCorner1();
        Location c2 = selection.getCorner2();
        if (c1.getWorld() == null || !c1.getWorld().equals(c2.getWorld())) {
            return CreateResult.DIFFERENT_WORLDS;
        }

        if (getClaimByName(playerId, name) != null) {
            return CreateResult.NAME_TAKEN;
        }

        ClaimRegion candidate = selection.toRegion(playerId, name);
        int minSize = plugin.getConfig().getInt("claims.min-claim-size", 5);
        if (candidate.width() < minSize || candidate.length() < minSize) {
            return CreateResult.TOO_SMALL;
        }

        RankLevel rank = getPlayerRank(playerId);
        int maxArea = getMaxArea(rank);
        if (maxArea > 0 && candidate.area() > maxArea) {
            return CreateResult.TOO_LARGE;
        }

        int maxClaims = getMaxClaims(rank);
        if (maxClaims > 0 && getPlayerClaims(playerId).size() >= maxClaims) {
            return CreateResult.TOO_MANY_CLAIMS;
        }

        int maxTotalArea = getMaxTotalArea(rank);
        if (maxTotalArea > 0) {
            int currentTotal = getPlayerClaims(playerId).stream().mapToInt(ClaimRegion::area).sum();
            if (currentTotal + candidate.area() > maxTotalArea) {
                return CreateResult.TOTAL_AREA_EXCEEDED;
            }
        }

        if (index.hasOverlap(candidate)) {
            return CreateResult.OVERLAPS;
        }

        allClaims.add(candidate);
        index.add(candidate);
        claimsByOwner.computeIfAbsent(playerId, k -> new ArrayList<>()).add(candidate);
        dataManager.saveClaim(candidate);

        activeSelections.remove(playerId);
        return CreateResult.SUCCESS;
    }

    /** Deletes a claim. Only the owner or an OPERATOR may delete. */
    public boolean deleteClaim(UUID playerId, UUID claimId) {
        ClaimRegion target = allClaims.stream()
                .filter(c -> c.claimId().equals(claimId))
                .findFirst().orElse(null);
        if (target == null) return false;

        boolean isOwner = target.ownerId().equals(playerId);
        RankService rankService = plugin.services().get(RankService.class);
        boolean isOperator = rankService != null
                && rankService.getLevel(playerId).isAtLeast(RankLevel.OPERATOR);

        if (!isOwner && !isOperator) return false;

        allClaims.remove(target);
        index.remove(target);
        List<ClaimRegion> ownerClaims = claimsByOwner.get(target.ownerId());
        if (ownerClaims != null) {
            ownerClaims.removeIf(c -> c.claimId().equals(claimId));
            if (ownerClaims.isEmpty()) claimsByOwner.remove(target.ownerId());
        }
        dataManager.deleteClaim(claimId);
        return true;
    }

    /** Adds a player to the claim's trusted set and persists the change. */
    public void addTrusted(UUID claimId, UUID trustedId) {
        findClaim(claimId).ifPresent(claim -> {
            claim.trustedPlayerIds().add(trustedId);
            dataManager.saveClaim(claim);
        });
    }

    /** Removes a player from the claim's trusted set and persists the change. */
    public void removeTrusted(UUID claimId, UUID trustedId) {
        findClaim(claimId).ifPresent(claim -> {
            claim.trustedPlayerIds().remove(trustedId);
            dataManager.saveClaim(claim);
        });
    }

    /** Returns all claims owned by the given player. */
    public List<ClaimRegion> getPlayerClaims(UUID playerId) {
        return claimsByOwner.getOrDefault(playerId, List.of());
    }

    /** Returns a claim owned by the given player with the given name, or null if not found. */
    public ClaimRegion getClaimByName(UUID ownerId, String name) {
        return getPlayerClaims(ownerId).stream()
                .filter(c -> c.name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    /** Returns the names of all claims owned by the given player. */
    public List<String> getClaimNames(UUID ownerId) {
        return getPlayerClaims(ownerId).stream()
                .map(ClaimRegion::name)
                .toList();
    }

    /** Gets or creates a wand selection for the player. */
    public ClaimSelection getOrCreateSelection(UUID playerId) {
        return activeSelections.computeIfAbsent(playerId, k -> new ClaimSelection());
    }

    /** Reads the max area config value for the given rank. -1 means unlimited. */
    public int getMaxArea(RankLevel rank) {
        return plugin.getConfig().getInt("claims.max-area." + rank.name(), 2500);
    }

    /** Reads the max claims config value for the given rank. -1 means unlimited. */
    public int getMaxClaims(RankLevel rank) {
        return plugin.getConfig().getInt("claims.max-claims." + rank.name(), 2);
    }

    /** Reads the max total combined area across all claims for the given rank. -1 means unlimited. */
    public int getMaxTotalArea(RankLevel rank) {
        return plugin.getConfig().getInt("claims.max-total-area." + rank.name(), 1000000);
    }

    /** Exposes the visualizer for listeners and commands. */
    public ClaimVisualizer visualizer() {
        return visualizer;
    }

    private RankLevel getPlayerRank(UUID playerId) {
        RankService rankService = plugin.services().get(RankService.class);
        return rankService != null ? rankService.getLevel(playerId) : RankLevel.MEMBER;
    }

    private Optional<ClaimRegion> findClaim(UUID claimId) {
        return allClaims.stream().filter(c -> c.claimId().equals(claimId)).findFirst();
    }

    /** Result codes for {@link #createClaim}. */
    public enum CreateResult {
        SUCCESS,
        TOO_LARGE,
        TOO_SMALL,
        TOO_MANY_CLAIMS,
        OVERLAPS,
        DIFFERENT_WORLDS,
        INCOMPLETE,
        NAME_TAKEN,
        TOTAL_AREA_EXCEEDED
    }
}
