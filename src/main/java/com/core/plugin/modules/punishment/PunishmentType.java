package com.core.plugin.modules.punishment;

import org.bukkit.Material;

/**
 * Config-driven punishment type definition.
 *
 * @param key            unique identifier used for persistence and config lookup
 * @param displayName    human-readable label shown in GUIs and messages
 * @param icon           material used as the GUI icon
 * @param skipsSeverity  when true, severity selection is skipped (e.g. warnings)
 */
public record PunishmentType(String key, String displayName, Material icon, boolean skipsSeverity) {}
