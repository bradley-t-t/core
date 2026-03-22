package com.core.plugin.rank;

/**
 * Defines the rank hierarchy. Commands declare a {@code minRank} in their
 * annotation; inline checks use {@link #isAtLeast(RankLevel)} for sub-feature gating.
 * Display names and prefixes are configured in {@code ranks.yml}.
 */
public enum RankLevel {

    MEMBER(0),
    DIAMOND(100),
    MODERATOR(200),
    OPERATOR(300);

    private final int weight;

    RankLevel(int weight) {
        this.weight = weight;
    }

    public int weight() { return weight; }

    /** Check if this rank is at least as high as the given rank. */
    public boolean isAtLeast(RankLevel other) {
        return this.weight >= other.weight;
    }

    /** Parse a rank level from a string name (case-insensitive). Returns null if not found. */
    public static RankLevel fromString(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
