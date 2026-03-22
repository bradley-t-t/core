package com.core.plugin.listener;

import com.core.plugin.CorePlugin;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.rank.Rank;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.service.RankService;
import com.core.plugin.service.BotService;
import com.core.plugin.service.PlayerStateService;
import com.core.plugin.service.PlayerStatsService;
import com.core.plugin.service.WildTeleportService;
import com.core.plugin.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Handles join/quit tracking, rank prefix in tab list, god mode damage cancellation,
 * freeze movement blocking, and vanish visibility on join.
 */
public final class PlayerListener implements Listener {

    private final CorePlugin plugin;

    public PlayerListener(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerStateService state = plugin.services().get(PlayerStateService.class);
        RankService rankService = plugin.services().get(RankService.class);
        PlayerStatsService statsService = plugin.services().get(PlayerStatsService.class);

        state.updateLastSeen(player.getUniqueId());
        statsService.recordFirstJoinIfNew(player.getUniqueId());
        statsService.loadPlayer(player.getUniqueId());

        // Set join message
        boolean isFirstJoin = plugin.dataManager().getRank(player.getUniqueId()) == null;
        if (isFirstJoin) {
            event.joinMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(MessageUtil.colorize(
                            Lang.get("join.first", "player", player.getName()))));
        } else {
            event.joinMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(MessageUtil.colorize(
                            Lang.get("join.normal", "player", player.getName()))));
        }

        // First join: assign default rank and wild teleport
        if (isFirstJoin) {
            rankService.setRank(player.getUniqueId(), RankLevel.MEMBER);

            // Guaranteed wild teleport — delay 5 ticks so the player is fully loaded
            WildTeleportService wildService = plugin.services().get(WildTeleportService.class);
            if (wildService != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> wildService.wildTeleportGuaranteed(player), 5L);
            }

            // Starter kit — delay 10 ticks so inventory is ready after teleport begins
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                var inventory = player.getInventory();
                inventory.setHelmet(new ItemStack(Material.IRON_HELMET));
                inventory.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                inventory.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                inventory.setBoots(new ItemStack(Material.IRON_BOOTS));
                // Claim wand (golden shovel with name/lore)
                ItemStack claimWand = new ItemStack(Material.GOLDEN_SHOVEL);
                ItemMeta wandMeta = claimWand.getItemMeta();
                if (wandMeta != null) {
                    wandMeta.setDisplayName(Lang.get("claim.wand-name"));
                    wandMeta.setLore(java.util.List.of(
                            Lang.get("claim.wand-lore-1"),
                            Lang.get("claim.wand-lore-2")
                    ));
                    claimWand.setItemMeta(wandMeta);
                }

                inventory.addItem(
                        new ItemStack(Material.IRON_SWORD),
                        new ItemStack(Material.IRON_PICKAXE),
                        new ItemStack(Material.IRON_SHOVEL),
                        new ItemStack(Material.IRON_AXE),
                        new ItemStack(Material.COOKED_BEEF, 16),
                        new ItemStack(Material.TORCH, 8),
                        new ItemStack(Material.MAP),
                        claimWand
                );
            }, 10L);
        }

        // Set tab list name with rank prefix
        Rank rank = rankService.getRank(player.getUniqueId());
        if (rank != null) {
            player.setPlayerListName(MessageUtil.colorize(rank.displayPrefix() + " " + player.getName()));
        }

        // Hide vanished players from the joining player
        for (var vanishedId : state.getVanished()) {
            Player vanished = plugin.getServer().getPlayer(vanishedId);
            if (vanished != null) {
                player.hidePlayer(plugin, vanished);
            }
        }

        // Fake players may greet
        BotService fakeService = plugin.services().get(BotService.class);
        if (fakeService != null) fakeService.onRealPlayerJoin(player.getName(), isFirstJoin);

        // Bed spawn warning — on join and every 15 minutes
        if (!isFirstJoin && player.getBedSpawnLocation() == null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && player.getBedSpawnLocation() == null) {
                    Lang.send(player, "bed.no-spawn-warning");
                }
            }, 100L); // 5 seconds after join
        }

        startBedWarningTask(player);
    }

    private void startBedWarningTask(Player player) {
        // Remind every 15 minutes (18000 ticks) if no bed spawn
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline()) {
                task.cancel();
                return;
            }
            if (player.getBedSpawnLocation() == null) {
                Lang.send(player, "bed.no-spawn-warning");
            }
        }, 18000L, 18000L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.services().get(PlayerStateService.class).updateLastSeen(player.getUniqueId());
        plugin.services().get(PlayerStatsService.class).unloadPlayer(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player victim = event.getEntity();
        event.setDeathMessage(null);

        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        EntityDamageEvent.DamageCause cause = lastDamage != null
                ? lastDamage.getCause() : EntityDamageEvent.DamageCause.CUSTOM;

        Player killer = victim.getKiller();
        if (killer != null) {
            String weapon = getWeaponName(killer);
            if (weapon != null) {
                broadcastDeath("death.pvp-weapon", victim.getName(), killer.getName(), weapon);
            } else {
                broadcastDeath("death.pvp", victim.getName(), killer.getName());
            }
            return;
        }

        String langKey = switch (cause) {
            case FALL -> "death.fall";
            case DROWNING -> "death.drowning";
            case FIRE, FIRE_TICK -> "death.fire";
            case LAVA -> "death.lava";
            case VOID -> "death.void";
            case STARVATION -> "death.starvation";
            case SUFFOCATION -> "death.suffocation";
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> "death.explosion";
            case LIGHTNING -> "death.lightning";
            case FREEZE -> "death.freeze";
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> {
                String mobName = lastDamage instanceof EntityDamageByEntityEvent entityEvent
                        ? entityEvent.getDamager().getName() : "a mob";
                broadcastDeath("death.mob", victim.getName(), mobName);
                yield null;
            }
            case PROJECTILE -> {
                String shooterName = lastDamage instanceof EntityDamageByEntityEvent entityEvent
                        ? entityEvent.getDamager().getName() : "something";
                broadcastDeath("death.projectile", victim.getName(), shooterName);
                yield null;
            }
            default -> "death.generic";
        };

        if (langKey != null) {
            broadcastDeath(langKey, victim.getName());
        }

        // Fake players may react to the death
        BotService fakeService = plugin.services().get(BotService.class);
        if (fakeService != null) fakeService.onRealPlayerDeath(victim.getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // If no bed spawn, wild teleport them after respawn
        if (player.getBedSpawnLocation() == null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                WildTeleportService wildService = plugin.services().get(WildTeleportService.class);
                if (wildService != null) {
                    wildService.wildTeleportGuaranteed(player);
                }
            }, 5L);
        }
    }

    private void broadcastDeath(String langKey, String player, Object... extra) {
        Object[] replacements = new Object[2 + extra.length * 2];
        replacements[0] = "player";
        replacements[1] = player;
        for (int i = 0; i < extra.length; i++) {
            String key = i == 0 ? "killer" : "weapon";
            replacements[2 + i * 2] = key;
            replacements[3 + i * 2] = extra[i];
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            Lang.send(online, langKey, replacements);
        }
    }

    private String getWeaponName(Player killer) {
        ItemStack hand = killer.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return null;
        ItemMeta meta = hand.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.services().get(PlayerStateService.class).isGod(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!hasActuallyMoved(event)) return;
        if (plugin.services().get(PlayerStateService.class).isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            Lang.send(event.getPlayer(), "freeze.cannot-move");
        }
    }

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        Player player = event.getPlayer();
        player.setRespawnLocation(event.getBed().getLocation(), true);
        Lang.send(player, "bed.spawn-set");
        event.setCancelled(true);
    }

    private boolean hasActuallyMoved(PlayerMoveEvent event) {
        var from = event.getFrom();
        var to = event.getTo();
        return to != null && (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ());
    }
}
