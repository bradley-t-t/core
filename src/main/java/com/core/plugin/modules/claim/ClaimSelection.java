package com.core.plugin.modules.claim;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Mutable container for an in-progress wand selection.
 * Both corners must be set and in the same world before converting to a {@link ClaimRegion}.
 */
public final class ClaimSelection {

    private Location corner1;
    private Location corner2;

    public Location getCorner1() { return corner1; }

    public Location getCorner2() { return corner2; }

    public void setCorner1(Location loc) { this.corner1 = loc; }

    public void setCorner2(Location loc) { this.corner2 = loc; }

    /** Both corners are set and reside in the same world. */
    public boolean isComplete() {
        return corner1 != null
                && corner2 != null
                && corner1.getWorld() != null
                && corner1.getWorld().equals(corner2.getWorld());
    }

    /**
     * Converts this selection into a finalized {@link ClaimRegion}.
     *
     * @throws IllegalStateException if the selection is incomplete
     */
    public ClaimRegion toRegion(UUID ownerId, String name) {
        if (!isComplete()) {
            throw new IllegalStateException("Selection is incomplete — both corners must be in the same world");
        }
        return ClaimRegion.of(
                ownerId,
                name,
                corner1.getWorld().getName(),
                corner1.getBlockX(), corner1.getBlockZ(),
                corner2.getBlockX(), corner2.getBlockZ()
        );
    }
}
