package com.core.plugin.modules.claim;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable representation of a rectangular land claim in a specific world.
 * Coordinates are block-level and inclusive on both min and max bounds.
 */
public record ClaimRegion(
        UUID claimId,
        UUID ownerId,
        String name,
        String worldName,
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        Set<UUID> trustedPlayerIds,
        long createdAt
) {

    /**
     * Creates a new claim region, normalizing coordinates so min/max are correct.
     * Generates a fresh claim ID, empty trusted set, and current timestamp.
     */
    public static ClaimRegion of(UUID owner, String name, String world, int x1, int z1, int x2, int z2) {
        return new ClaimRegion(
                UUID.randomUUID(),
                owner,
                name,
                world,
                Math.min(x1, x2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(z1, z2),
                new HashSet<>(),
                System.currentTimeMillis()
        );
    }

    /** True if the given block coordinate falls within this claim's 2D bounds in the same world. */
    public boolean contains(String world, int x, int z) {
        return worldName.equals(world)
                && x >= minX && x <= maxX
                && z >= minZ && z <= maxZ;
    }

    /** True if this claim's AABB overlaps with another claim in the same world. */
    public boolean overlaps(ClaimRegion other) {
        if (!worldName.equals(other.worldName)) return false;
        return minX <= other.maxX && maxX >= other.minX
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    /** Total block area of the claim (inclusive). */
    public int area() {
        return width() * length();
    }

    /** East-west span in blocks (inclusive). */
    public int width() {
        return maxX - minX + 1;
    }

    /** North-south span in blocks (inclusive). */
    public int length() {
        return maxZ - minZ + 1;
    }

    /** True if the player is the owner or is in the trusted set. */
    public boolean canInteract(UUID playerId) {
        return ownerId.equals(playerId) || trustedPlayerIds.contains(playerId);
    }
}
