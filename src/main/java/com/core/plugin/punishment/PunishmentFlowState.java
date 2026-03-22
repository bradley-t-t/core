package com.core.plugin.punishment;

/**
 * Stages of the interactive punishment flow a moderator progresses through.
 */
public enum PunishmentFlowState {

    SELECTING_TYPE,
    SELECTING_SEVERITY,
    AWAITING_REASON,
    PREVIEWING
}
