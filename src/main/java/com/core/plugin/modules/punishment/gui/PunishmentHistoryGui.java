package com.core.plugin.modules.punishment.gui;

import com.core.plugin.modules.gui.GuiItem;
import com.core.plugin.listener.GuiListener;
import com.core.plugin.modules.gui.PaginatedGui;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.punishment.PunishmentRecord;
import com.core.plugin.modules.punishment.PunishmentRegistry;
import com.core.plugin.modules.punishment.PunishmentSession;
import com.core.plugin.modules.punishment.PunishmentType;
import com.core.plugin.service.PunishmentService;
import com.core.plugin.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Paginated GUI showing a player's full punishment history.
 * Active punishments glow; expired ones are dimmed.
 */
public final class PunishmentHistoryGui {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm");

    private final JavaPlugin plugin;
    private final PunishmentService punishmentService;
    private final PunishmentRegistry registry;
    private final GuiListener guiListener;

    public PunishmentHistoryGui(JavaPlugin plugin, PunishmentService punishmentService, GuiListener guiListener) {
        this.plugin = plugin;
        this.punishmentService = punishmentService;
        this.registry = punishmentService.registry();
        this.guiListener = guiListener;
    }

    /** Open the history GUI. If fromSession is non-null, history was opened from the punish flow. */
    public void open(Player viewer, UUID targetId, String targetName, PunishmentSession fromSession) {
        List<PunishmentRecord> records = punishmentService.getHistory(targetId);
        List<GuiItem> items = new ArrayList<>();

        if (records.isEmpty()) {
            items.add(GuiItem.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Lang.get("punish.no-history")));
        } else {
            for (int i = records.size() - 1; i >= 0; i--) {
                items.add(buildRecordItem(records.get(i)));
            }
        }

        PaginatedGui gui = new PaginatedGui(
                Lang.get("punish.history-title", "player", targetName),
                items, guiListener);

        if (fromSession != null) {
            gui.onBack(() -> new PunishmentGui(plugin, punishmentService, guiListener)
                    .openTypeSelection(viewer, fromSession));
        }

        gui.open(viewer);
    }

    private GuiItem buildRecordItem(PunishmentRecord record) {
        PunishmentType type = registry.getTypeByKey(record.typeKey());
        String typeDisplay = type != null ? type.displayName() : record.typeKey();
        Material icon = type != null ? type.icon() : Material.STONE;
        boolean skipsSeverity = type != null && type.skipsSeverity();

        String dateDisplay = DATE_FORMAT.format(new Date(record.issuedAt()));
        String durationDisplay = record.durationMillis() == -1
                ? "Permanent"
                : TimeUtil.formatDuration(record.durationMillis());
        boolean isActive = record.active()
                && (record.expiresAt() == -1 || record.expiresAt() > System.currentTimeMillis());
        String activeDisplay = isActive
                ? Lang.get("punish.history-entry-active")
                : Lang.get("punish.history-entry-expired");

        String nameDisplay = skipsSeverity
                ? Lang.get("punish.history-entry-name-warn", "type", typeDisplay)
                : Lang.get("punish.history-entry-name", "type", typeDisplay, "severity", record.severity());

        List<String> loreLines = new ArrayList<>();
        loreLines.add(Lang.get("punish.history-entry-reason", "reason", record.reason()));
        loreLines.add(Lang.get("punish.history-entry-moderator", "moderator", record.moderatorName()));
        loreLines.add(Lang.get("punish.history-entry-date", "date", dateDisplay));
        if (!skipsSeverity) {
            loreLines.add(Lang.get("punish.history-entry-duration", "duration", durationDisplay));
        }
        loreLines.add(activeDisplay);

        GuiItem item = GuiItem.of(icon)
                .name(nameDisplay)
                .lore(loreLines.toArray(String[]::new));

        if (isActive) item.glow();
        return item;
    }
}
