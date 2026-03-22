package com.core.plugin.vote;

import com.core.plugin.CorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Delivers pending vote rewards when a player joins.
 */
public final class VoteListener implements Listener {

    private final CorePlugin plugin;

    public VoteListener(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        VoteService voteService = plugin.services().get(VoteService.class);
        if (voteService == null) return;

        // Delay 20 ticks so the player is fully loaded and other join logic completes first
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> voteService.rewardHandler().deliverPendingRewards(event.getPlayer()), 20L);
    }
}
