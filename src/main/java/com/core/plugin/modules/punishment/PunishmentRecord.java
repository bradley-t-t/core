package com.core.plugin.modules.punishment;

import java.util.UUID;

/**
 * Immutable snapshot of a punishment that has been issued.
 * An {@code expiresAt} value of {@code -1} indicates a permanent punishment.
 */
public record PunishmentRecord(
        UUID targetId,
        String targetName,
        UUID moderatorId,
        String moderatorName,
        String typeKey,
        int severity,
        long durationMillis,
        String reason,
        long issuedAt,
        long expiresAt,
        boolean active
) {}
