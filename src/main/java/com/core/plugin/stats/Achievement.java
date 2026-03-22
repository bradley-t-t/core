package com.core.plugin.stats;

import org.bukkit.Material;

public record Achievement(String key, String statKey, long threshold, String displayName, String description, Material icon) {}
