package com.core.plugin.modules.punishment;

import java.util.UUID;

/**
 * Mutable state tracking an in-progress punishment flow for a single moderator.
 */
public final class PunishmentSession {

    private final UUID moderatorId;
    private final UUID targetId;
    private final String targetName;

    private PunishmentType type;
    private PunishmentSeverity severity;
    private String reason;
    private PunishmentFlowState state = PunishmentFlowState.SELECTING_TYPE;

    public PunishmentSession(UUID moderatorId, UUID targetId, String targetName) {
        this.moderatorId = moderatorId;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    public UUID moderatorId() { return moderatorId; }

    public UUID targetId() { return targetId; }

    public String targetName() { return targetName; }

    public PunishmentType type() { return type; }

    public void setType(PunishmentType type) { this.type = type; }

    public PunishmentSeverity severity() { return severity; }

    public void setSeverity(PunishmentSeverity severity) { this.severity = severity; }

    public String reason() { return reason; }

    public void setReason(String reason) { this.reason = reason; }

    public PunishmentFlowState state() { return state; }

    public void setState(PunishmentFlowState state) { this.state = state; }

    public boolean isComplete() {
        return type != null && severity != null && reason != null;
    }
}
