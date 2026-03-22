package com.core.plugin.listener;

import com.core.plugin.CorePlugin;
import com.core.plugin.service.PlayerStatsService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * Routes Bukkit events to {@link PlayerStatsService} stat increments.
 * Each handler is minimal — just maps the event to a stat key.
 */
public final class StatsListener implements Listener {

    private final PlayerStatsService statsService;

    public StatsListener(CorePlugin plugin) {
        this.statsService = plugin.services().get(PlayerStatsService.class);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        statsService.incrementStat(victim.getUniqueId(), "deaths");

        Player killer = victim.getKiller();
        if (killer != null) {
            statsService.incrementStat(killer.getUniqueId(), "kills");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        statsService.incrementStat(event.getPlayer().getUniqueId(), "blocks-broken");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        statsService.incrementStat(event.getPlayer().getUniqueId(), "blocks-placed");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event instanceof PlayerDeathEvent) return;

        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            statsService.incrementStat(killer.getUniqueId(), "mobs-killed");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            statsService.incrementStat(event.getPlayer().getUniqueId(), "fish-caught");
        }
    }
}
