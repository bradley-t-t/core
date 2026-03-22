package com.core.plugin.service;

import com.core.plugin.CorePlugin;
import com.core.plugin.data.DataManager;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.punishment.*;
import com.core.plugin.util.SoundUtil;
import com.core.plugin.util.TimeUtil;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-progress punishment sessions and executes finalized punishments.
 * Sessions track the moderator's GUI flow state; execution applies the actual
 * game effect (warn, mute, ban) and logs the record to disk.
 */
public final class PunishmentService implements Service {

    private final CorePlugin plugin;
    private final DataManager dataManager;
    private final Map<UUID, PunishmentSession> activeSessions = new ConcurrentHashMap<>();

    public PunishmentService(CorePlugin plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.dataManager();
    }

    @Override public void enable() {}

    @Override
    public void disable() {
        activeSessions.clear();
    }

    public PunishmentRegistry registry() {
        return plugin.services().get(PunishmentRegistry.class);
    }

    public PunishmentSession startSession(UUID moderatorId, UUID targetId, String targetName) {
        PunishmentSession session = new PunishmentSession(moderatorId, targetId, targetName);
        activeSessions.put(moderatorId, session);
        return session;
    }

    public PunishmentSession getSession(UUID moderatorId) {
        return activeSessions.get(moderatorId);
    }

    public void clearSession(UUID moderatorId) {
        activeSessions.remove(moderatorId);
    }

    public boolean hasActiveSession(UUID moderatorId) {
        return activeSessions.containsKey(moderatorId);
    }

    /** Execute the finalized punishment, apply game effects, and log the record. */
    public void executePunishment(PunishmentSession session) {
        PunishmentSeverity severity = session.severity();
        long durationMillis = severity.durationMillis();
        long now = System.currentTimeMillis();
        long expiresAt = durationMillis == -1 ? -1 : now + durationMillis;

        Player moderator = Bukkit.getPlayer(session.moderatorId());
        Player target = Bukkit.getPlayer(session.targetId());
        String moderatorName = moderator != null ? moderator.getName() : "Console";

        PunishmentRecord record = new PunishmentRecord(
                session.targetId(), session.targetName(),
                session.moderatorId(), moderatorName,
                session.type().key(), severity.tier(),
                durationMillis, session.reason(),
                now, expiresAt, true
        );

        dataManager.addPunishment(session.targetId(), record);
        applyPunishment(record, target, moderator);
        clearSession(session.moderatorId());
    }

    public List<PunishmentRecord> getHistory(UUID targetId) {
        return dataManager.getPunishments(targetId);
    }

    private void applyPunishment(PunishmentRecord record, Player target, Player moderator) {
        String durationDisplay = record.durationMillis() == -1
                ? "Permanent"
                : TimeUtil.formatDuration(record.durationMillis());

        switch (record.typeKey()) {
            case "warn" -> applyWarn(record, target, moderator);
            case "mute" -> applyMute(record, target, moderator, durationDisplay);
            case "ban" -> applyBan(record, target, moderator, durationDisplay);
        }
    }

    private void applyWarn(PunishmentRecord record, Player target, Player moderator) {
        if (target != null) {
            Lang.send(target, "punish.warn-target", "reason", record.reason());
            SoundUtil.error(target);
        }
        if (moderator != null) {
            Lang.send(moderator, "punish.executed-warn",
                    "player", record.targetName(), "reason", record.reason());
        }
    }

    private void applyMute(PunishmentRecord record, Player target, Player moderator, String durationDisplay) {
        PlayerStateService stateService = plugin.services().get(PlayerStateService.class);
        stateService.setMuted(record.targetId(), true);
        dataManager.setMutedUntil(record.targetId(), record.expiresAt());

        if (target != null) {
            Lang.send(target, "mute.muted-target");
            SoundUtil.toggleOn(target);
        }
        if (moderator != null) {
            Lang.send(moderator, "punish.executed",
                    "player", record.targetName(), "type", "Mute",
                    "severity", record.severity(), "reason", record.reason());
        }
    }

    private void applyBan(PunishmentRecord record, Player target, Player moderator, String durationDisplay) {
        Date expiry = record.expiresAt() == -1 ? null : new Date(record.expiresAt());
        Bukkit.getBanList(BanList.Type.NAME)
                .addBan(record.targetName(), record.reason(), expiry, record.moderatorName());

        if (target != null) {
            String screen = record.expiresAt() == -1
                    ? Lang.get("ban.screen", "reason", record.reason())
                    : Lang.get("tempban.screen", "reason", record.reason(), "duration", durationDisplay);
            SoundUtil.ban(target);
            target.kickPlayer(screen);
        }
        if (moderator != null) {
            Lang.send(moderator, "punish.executed",
                    "player", record.targetName(), "type", "Ban",
                    "severity", record.severity(), "reason", record.reason());
        }
    }
}
