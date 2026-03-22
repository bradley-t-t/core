package com.core.plugin.listener;

import com.core.plugin.CorePlugin;
import com.core.plugin.gui.punishment.PunishmentGui;
import com.core.plugin.lang.Lang;
import com.core.plugin.punishment.PunishmentFlowState;
import com.core.plugin.punishment.PunishmentSession;
import com.core.plugin.service.PunishmentService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Intercepts chat from moderators who are in the AWAITING_REASON punishment flow state.
 * The message is captured as the punishment reason (not broadcast) and the preview GUI opens.
 * Must run before {@link ChatListener} so it can cancel first.
 */
public final class PunishmentChatListener implements Listener {

    private final CorePlugin plugin;

    public PunishmentChatListener(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PunishmentService punishmentService = plugin.services().get(PunishmentService.class);
        PunishmentSession session = punishmentService.getSession(player.getUniqueId());

        if (session == null || session.state() != PunishmentFlowState.AWAITING_REASON) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("cancel")) {
            punishmentService.clearSession(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () ->
                    Lang.send(player, "punish.reason-cancelled"));
            return;
        }

        session.setReason(message);

        // Reopen preview GUI on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            PunishmentGui gui = new PunishmentGui(plugin, punishmentService, plugin.guiListener());
            gui.openPreview(player, session);
        });
    }
}
