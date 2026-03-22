package com.core.plugin.stats;

import org.bukkit.Material;

/**
 * Trackable player statistic loaded from {@code stats.yml}.
 * Each instance defines its persistence key, display name, and GUI icon material.
 */
public record StatType(String yamlKey, String displayName, Material icon) {}
