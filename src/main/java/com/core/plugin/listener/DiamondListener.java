package com.core.plugin.listener;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.service.RankService;
import com.core.plugin.service.DiamondService;
import com.core.plugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Handles Diamond rank perks at login: custom join messages and priority queue
 * bypassing the server's player cap.
 */
public final class DiamondListener implements Listener {

    private final CorePlugin plugin;

    public DiamondListener(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Priority queue: allow Diamond+ players to join even when the server is full.
     * Runs at HIGHEST priority so it fires after default checks.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.KICK_FULL) return;

        RankService rankService = plugin.services().get(RankService.class);
        if (rankService.getLevel(event.getPlayer().getUniqueId()).isAtLeast(RankLevel.DIAMOND)) {
            event.allow();
        }
    }

    /**
     * Custom join message for Diamond players. Fires one tick after join so the
     * player is fully loaded.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        DiamondService diamondService = plugin.services().get(DiamondService.class);
        RankService rankService = plugin.services().get(RankService.class);

        if (!rankService.getLevel(player.getUniqueId()).isAtLeast(RankLevel.DIAMOND)) return;

        String customMessage = diamondService.getJoinMessage(player.getUniqueId());
        if (customMessage == null || customMessage.isBlank()) return;

        String formatted = MessageUtil.colorize(
                "&b[Diamond] &f" + player.getName() + " &7> &b" + customMessage);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(formatted);
            }
        }, 1L);
    }
}
