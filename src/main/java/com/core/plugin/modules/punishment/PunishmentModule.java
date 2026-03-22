package com.core.plugin.modules.punishment;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.Module;

/**
 * Brings together the punishment subsystem: types ({@link PunishmentType}),
 * severities ({@link PunishmentSeverity}), records ({@link PunishmentRecord}),
 * sessions ({@link PunishmentSession}), flow states ({@link PunishmentFlowState}),
 * the config registry ({@link PunishmentRegistry}),
 * and GUIs ({@link com.core.plugin.modules.punishment.gui.PunishmentGui},
 * {@link com.core.plugin.modules.punishment.gui.PunishmentHistoryGui}).
 * Session management and execution live in {@link com.core.plugin.service.PunishmentService}.
 */
public final class PunishmentModule implements Module {

    @Override
    public void enable(CorePlugin plugin) {
    }

    @Override
    public void disable() {
    }

    @Override
    public String getName() {
        return "Punishment";
    }
}
