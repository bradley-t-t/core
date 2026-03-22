package com.core.plugin.modules.claim;

import java.util.*;

/**
 * Chunk-based spatial index for O(1) claim lookups by block coordinate.
 * Claims are registered in every chunk they touch, enabling fast point and overlap queries.
 */
public final class ClaimIndex {

    private final Map<String, Map<Long, List<ClaimRegion>>> worldChunkMap = new HashMap<>();

    /** Registers a claim in every chunk it overlaps. */
    public void add(ClaimRegion claim) {
        var chunkMap = worldChunkMap.computeIfAbsent(claim.worldName(), w -> new HashMap<>());
        forEachChunk(claim, (chunkX, chunkZ) -> {
            long key = chunkKey(chunkX, chunkZ);
            chunkMap.computeIfAbsent(key, k -> new ArrayList<>()).add(claim);
        });
    }

    /** Removes a claim from every chunk it was registered in. */
    public void remove(ClaimRegion claim) {
        var chunkMap = worldChunkMap.get(claim.worldName());
        if (chunkMap == null) return;

        forEachChunk(claim, (chunkX, chunkZ) -> {
            long key = chunkKey(chunkX, chunkZ);
            var list = chunkMap.get(key);
            if (list != null) {
                list.removeIf(c -> c.claimId().equals(claim.claimId()));
                if (list.isEmpty()) chunkMap.remove(key);
            }
        });

        if (chunkMap.isEmpty()) worldChunkMap.remove(claim.worldName());
    }

    /** Returns the claim containing the given block position, or null if unclaimed. */
    public ClaimRegion getClaimAt(String world, int x, int z) {
        var chunkMap = worldChunkMap.get(world);
        if (chunkMap == null) return null;

        long key = chunkKey(x >> 4, z >> 4);
        var candidates = chunkMap.get(key);
        if (candidates == null) return null;

        for (ClaimRegion claim : candidates) {
            if (claim.contains(world, x, z)) return claim;
        }
        return null;
    }

    /** True if any existing claim overlaps the candidate region. Checks only relevant chunks. */
    public boolean hasOverlap(ClaimRegion candidate) {
        var chunkMap = worldChunkMap.get(candidate.worldName());
        if (chunkMap == null) return false;

        int minChunkX = candidate.minX() >> 4;
        int maxChunkX = candidate.maxX() >> 4;
        int minChunkZ = candidate.minZ() >> 4;
        int maxChunkZ = candidate.maxZ() >> 4;

        Set<UUID> checked = new HashSet<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                var candidates = chunkMap.get(chunkKey(cx, cz));
                if (candidates == null) continue;
                for (ClaimRegion existing : candidates) {
                    if (checked.add(existing.claimId()) && existing.overlaps(candidate)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** True if any existing claim (other than the excluded one) overlaps the candidate region. */
    public boolean hasOverlapExcluding(ClaimRegion candidate, UUID excludeClaimId) {
        var chunkMap = worldChunkMap.get(candidate.worldName());
        if (chunkMap == null) return false;

        int minChunkX = candidate.minX() >> 4;
        int maxChunkX = candidate.maxX() >> 4;
        int minChunkZ = candidate.minZ() >> 4;
        int maxChunkZ = candidate.maxZ() >> 4;

        Set<UUID> checked = new HashSet<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                var candidates = chunkMap.get(chunkKey(cx, cz));
                if (candidates == null) continue;
                for (ClaimRegion existing : candidates) {
                    if (existing.claimId().equals(excludeClaimId)) continue;
                    if (checked.add(existing.claimId()) && existing.overlaps(candidate)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Removes all claims from the index. */
    public void clear() {
        worldChunkMap.clear();
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private void forEachChunk(ClaimRegion claim, ChunkConsumer consumer) {
        int minChunkX = claim.minX() >> 4;
        int maxChunkX = claim.maxX() >> 4;
        int minChunkZ = claim.minZ() >> 4;
        int maxChunkZ = claim.maxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                consumer.accept(cx, cz);
            }
        }
    }

    @FunctionalInterface
    private interface ChunkConsumer {
        void accept(int chunkX, int chunkZ);
    }
}
