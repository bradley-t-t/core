package com.core.plugin.gui.punishment;

import com.core.plugin.gui.ActiveGui;
import com.core.plugin.gui.GlassPane;
import com.core.plugin.gui.GuiBuilder;
import com.core.plugin.gui.GuiItem;
import com.core.plugin.gui.GuiListener;
import com.core.plugin.gui.elements.GuiElements;
import com.core.plugin.lang.Lang;
import com.core.plugin.punishment.*;
import com.core.plugin.service.PunishmentService;
import com.core.plugin.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Multi-screen GUI flow for the punishment system. Each screen corresponds to a
 * step in the flow: type selection, severity selection, reason prompt, and preview.
 * Types flagged with {@code skipsSeverity} skip the severity screen and record as permanent.
 */
public final class PunishmentGui {

    private final JavaPlugin plugin;
    private final PunishmentService punishmentService;
    private final PunishmentRegistry registry;
    private final GuiListener guiListener;

    public PunishmentGui(JavaPlugin plugin, PunishmentService punishmentService, GuiListener guiListener) {
        this.plugin = plugin;
        this.punishmentService = punishmentService;
        this.registry = punishmentService.registry();
        this.guiListener = guiListener;
    }

    /** Screen 1: Select punishment type + History link. */
    public void openTypeSelection(Player moderator, PunishmentSession session) {
        session.setState(PunishmentFlowState.SELECTING_TYPE);

        GuiBuilder builder = GuiBuilder.create(
                Lang.get("punish.title", "player", session.targetName()), 3)
                .fill(GlassPane.gray());

        int index = 0;
        for (PunishmentType type : registry.getTypes()) {
            int slot = 10 + (index * 2);

            GuiItem item = GuiItem.of(type.icon())
                    .name(Lang.get("punish." + type.key() + "-item"))
                    .lore(Lang.get("punish." + type.key() + "-lore"));
            if (session.type() == type) item.glow();

            item.onClick(event -> {
                session.setType(type);
                if (type.skipsSeverity()) {
                    // Highest severity tier for types that skip severity selection
                    PunishmentSeverity permanent = findHighestSeverity();
                    session.setSeverity(permanent);
                    openReasonPrompt(moderator, session);
                } else {
                    openSeveritySelection(moderator, session);
                }
            });
            builder.item(slot, item);
            index++;
        }

        // History
        builder.item(16, GuiItem.of(Material.BOOK)
                .name(Lang.get("punish.history-item"))
                .lore(Lang.get("punish.history-lore"))
                .onClick(event -> {
                    event.getWhoClicked().closeInventory();
                    new PunishmentHistoryGui(plugin, punishmentService, guiListener)
                            .open(moderator, session.targetId(), session.targetName(), session);
                }));

        ActiveGui gui = builder.build(guiListener);
        guiListener.open(moderator, gui);
    }

    /** Screen 2: Select severity tier. Only for types where {@code skipsSeverity} is false. */
    public void openSeveritySelection(Player moderator, PunishmentSession session) {
        session.setState(PunishmentFlowState.SELECTING_SEVERITY);

        String typeDisplay = session.type().displayName();
        GuiBuilder builder = GuiBuilder.create(
                Lang.get("punish.severity-title", "type", typeDisplay), 3)
                .fill(GlassPane.gray());

        int index = 0;
        for (PunishmentSeverity sev : registry.getSeverities()) {
            int slot = 10 + (index * 2);

            GuiItem item = GuiItem.of(sev.icon())
                    .name(Lang.get("punish.tier-" + sev.tier()))
                    .lore(Lang.get("punish.tier-lore", "type", typeDisplay, "duration", sev.displayDuration()));
            if (session.severity() == sev) item.glow();
            item.onClick(event -> {
                session.setSeverity(sev);
                openReasonPrompt(moderator, session);
            });
            builder.item(slot, item);
            index++;
        }

        // Back button
        builder.item(16, GuiElements.backButton(event -> openTypeSelection(moderator, session)));

        ActiveGui gui = builder.build(guiListener);
        guiListener.open(moderator, gui);
    }

    /** Screen 3: Confirm and enter reason prompt. */
    public void openReasonPrompt(Player moderator, PunishmentSession session) {
        String typeDisplay = session.type().displayName();
        String severityDisplay = session.type().skipsSeverity()
                ? "Permanent"
                : session.severity().displayDuration();

        GuiBuilder builder = GuiBuilder.create(
                Lang.get("punish.confirm-title"), 3)
                .fill(GlassPane.gray());

        // Summary items (display only)
        builder.item(11, GuiItem.of(session.type().icon())
                .name(Lang.get("punish.preview-type", "type", typeDisplay))
                .glow());

        builder.item(12, GuiItem.of(session.severity().icon())
                .name(Lang.get("punish.preview-severity", "severity", severityDisplay))
                .glow());

        // Confirm button
        builder.item(14, GuiElements.confirmButton(event -> {
                    event.getWhoClicked().closeInventory();
                    session.setState(PunishmentFlowState.AWAITING_REASON);
                    Lang.send(moderator, "punish.enter-reason-prompt");
                })
                .lore(Lang.get("punish.confirm-lore", "type", typeDisplay, "severity", severityDisplay)));

        // Back button -- types that skip severity go back to type selection
        builder.item(16, GuiElements.backButton(event -> {
            if (session.type().skipsSeverity()) {
                openTypeSelection(moderator, session);
            } else {
                openSeveritySelection(moderator, session);
            }
        }));

        ActiveGui gui = builder.build(guiListener);
        guiListener.open(moderator, gui);
    }

    /** Screen 4: Preview the full punishment before final execution. */
    public void openPreview(Player moderator, PunishmentSession session) {
        session.setState(PunishmentFlowState.PREVIEWING);

        String typeDisplay = session.type().displayName();
        String severityDisplay = session.type().skipsSeverity()
                ? "Permanent"
                : session.severity().displayDuration();
        String durationDisplay = session.type().skipsSeverity()
                ? "Permanent"
                : session.severity().durationMillis() == -1
                        ? "Permanent"
                        : TimeUtil.formatDuration(session.severity().durationMillis());

        GuiBuilder builder = GuiBuilder.create(
                Lang.get("punish.preview-title"), 4)
                .fill(GlassPane.gray());

        // Type
        builder.item(10, GuiItem.of(session.type().icon())
                .name(Lang.get("punish.preview-type", "type", typeDisplay))
                .glow());

        // Severity
        builder.item(12, GuiItem.of(session.severity().icon())
                .name(Lang.get("punish.preview-severity", "severity", severityDisplay))
                .lore(Lang.get("punish.preview-duration", "duration", durationDisplay))
                .glow());

        // Reason
        builder.item(14, GuiItem.of(Material.WRITABLE_BOOK)
                .name(Lang.get("punish.preview-reason", "reason", session.reason())));

        // Target head
        @SuppressWarnings("deprecation")
        var offlineTarget = Bukkit.getOfflinePlayer(session.targetName());
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(offlineTarget);
            skull.setItemMeta(meta);
        }
        builder.item(16, GuiItem.of(skull)
                .name(Lang.get("punish.preview-target", "player", session.targetName())));

        // Cancel
        builder.item(28, GuiElements.cancelButton(event -> {
            event.getWhoClicked().closeInventory();
            punishmentService.clearSession(moderator.getUniqueId());
            Lang.send(moderator, "punish.reason-cancelled");
        }));

        // Execute
        builder.item(34, GuiItem.of(Material.LIME_CONCRETE)
                .name(Lang.get("punish.execute-button"))
                .onClick(event -> {
                    event.getWhoClicked().closeInventory();
                    punishmentService.executePunishment(session);
                }));

        ActiveGui gui = builder.build(guiListener);
        guiListener.open(moderator, gui);
    }

    private PunishmentSeverity findHighestSeverity() {
        PunishmentSeverity highest = null;
        for (PunishmentSeverity sev : registry.getSeverities()) {
            if (highest == null || sev.tier() > highest.tier()) {
                highest = sev;
            }
        }
        return highest;
    }
}
