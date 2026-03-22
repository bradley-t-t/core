package com.core.plugin.listener;

import com.core.plugin.CorePlugin;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Drops player heads on PvP kills (guaranteed) and mob heads on mob kills (rare chance).
 * Head items are named using the plugin's lang system for a consistent look.
 */
public final class HeadDropListener implements Listener {

    private static final double MOB_HEAD_DROP_CHANCE = 0.05; // 5%

    private static final Map<EntityType, Material> MOB_HEAD_MAP = Map.ofEntries(
            Map.entry(EntityType.ZOMBIE, Material.ZOMBIE_HEAD),
            Map.entry(EntityType.SKELETON, Material.SKELETON_SKULL),
            Map.entry(EntityType.CREEPER, Material.CREEPER_HEAD),
            Map.entry(EntityType.WITHER_SKELETON, Material.WITHER_SKELETON_SKULL),
            Map.entry(EntityType.ENDER_DRAGON, Material.DRAGON_HEAD),
            Map.entry(EntityType.PIGLIN, Material.PIGLIN_HEAD)
    );

    private final CorePlugin plugin;
    private final Random random = new Random();

    public HeadDropListener(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(victim);
            meta.setDisplayName(MessageUtil.colorize(
                    Lang.get("heads.player", "player", victim.getName())));
            meta.setLore(List.of(MessageUtil.colorize(
                    Lang.get("heads.player-lore", "killer", killer.getName()))));
            head.setItemMeta(meta);
        }

        victim.getWorld().dropItemNaturally(victim.getLocation(), head);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (event instanceof PlayerDeathEvent) return;

        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        Material headMaterial = MOB_HEAD_MAP.get(entity.getType());
        if (headMaterial == null) return;

        // Dragon heads always drop, others are rare
        boolean isDragon = entity.getType() == EntityType.ENDER_DRAGON;
        if (!isDragon && random.nextDouble() >= MOB_HEAD_DROP_CHANCE) return;

        String mobName = formatMobName(entity.getType());

        ItemStack head = new ItemStack(headMaterial);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.colorize(
                    Lang.get("heads.mob", "mob", mobName)));
            meta.setLore(List.of(MessageUtil.colorize(
                    Lang.get("heads.mob-lore", "player", killer.getName()))));
            head.setItemMeta(meta);
        }

        entity.getWorld().dropItemNaturally(entity.getLocation(), head);
        Lang.send(killer, "heads.mob-dropped", "mob", mobName);
    }

    private String formatMobName(EntityType type) {
        String raw = type.name().toLowerCase().replace('_', ' ');
        StringBuilder result = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
