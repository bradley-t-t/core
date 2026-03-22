package com.core.plugin.rank;

/**
 * Display configuration for a rank level, loaded from {@code ranks.yml}.
 * Contains only cosmetic data -- no permission logic.
 */
public final class Rank {

    private final RankLevel level;
    private final String displayPrefix;
    private final String chatColor;

    public Rank(RankLevel level, String displayPrefix, String chatColor) {
        this.level = level;
        this.displayPrefix = displayPrefix;
        this.chatColor = chatColor;
    }

    public RankLevel level() { return level; }

    public String displayPrefix() { return displayPrefix; }

    public String chatColor() { return chatColor; }

    public int weight() { return level.weight(); }

    public String name() { return level.name().toLowerCase(); }
}
