package com.core.plugin.punishment;

import org.bukkit.Material;

/**
 * Config-driven severity tier that determines punishment duration and GUI presentation.
 *
 * @param tier            numeric tier identifier (1, 2, 3, ...)
 * @param durationMillis  punishment duration in milliseconds; {@code -1} for permanent
 * @param displayDuration human-readable duration label shown in GUIs
 * @param icon            material used as the GUI icon
 */
public record PunishmentSeverity(int tier, long durationMillis, String displayDuration, Material icon) {}
