package com.core.plugin.vote;

import com.core.plugin.CorePlugin;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gives vote rewards to players. If the player is offline when the vote arrives,
 * pending rewards are stored and delivered on next login.
 */
public final class VoteRewardHandler {

    private final CorePlugin plugin;

    VoteRewardHandler(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Gives all configured vote rewards to an online player immediately. */
    public void giveRewards(Player player) {
        int xpLevels = plugin.getConfig().getInt("voting.rewards.xp-levels", 5);
        if (xpLevels > 0) {
            player.giveExpLevels(xpLevels);
        }

        List<Map<?, ?>> itemConfigs = plugin.getConfig().getMapList("voting.rewards.items");
        for (Map<?, ?> entry : itemConfigs) {
            String materialName = String.valueOf(entry.get("material"));
            int amount = entry.containsKey("amount") ? ((Number) entry.get("amount")).intValue() : 1;

            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                plugin.getLogger().warning("Invalid vote reward material: " + materialName);
                continue;
            }

            ItemStack item = new ItemStack(material, amount);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            overflow.values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    /** Increments pending vote rewards for an offline player. */
    public void addPendingReward(UUID playerId) {
        int pending = plugin.dataManager().getPendingVoteRewards(playerId);
        plugin.dataManager().setPendingVoteRewards(playerId, pending + 1);
    }

    /** Checks for and delivers any pending vote rewards on join. */
    public void deliverPendingRewards(Player player) {
        UUID playerId = player.getUniqueId();
        int pending = plugin.dataManager().getPendingVoteRewards(playerId);
        if (pending <= 0) return;

        for (int i = 0; i < pending; i++) {
            giveRewards(player);
        }

        plugin.dataManager().setPendingVoteRewards(playerId, 0);
        Lang.send(player, "vote.pending", "count", pending);
        SoundUtil.success(player);
    }
}
