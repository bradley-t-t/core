package com.core.plugin.commands.inventory;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.List;
import com.core.plugin.rank.RankLevel;

@CommandInfo(
        name = "repair",
        aliases = {"fix"},
        minRank = RankLevel.MODERATOR,
        description = "Repair items in your inventory",
        usage = "/repair [all]",
        playerOnly = true,
        icon = Material.ANVIL
)
public final class RepairCommand extends BaseCommand {

    public RepairCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        if (context.hasArg(0) && context.arg(0).equalsIgnoreCase("all")) {
            if (!hasMinRank(player, RankLevel.DIAMOND)) {
                Lang.send(player, "repair.no-permission-all");
                return;
            }
            int repaired = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (repairItem(item)) repaired++;
            }
            SoundUtil.success(player);
            Lang.actionBar(player, "actionbar.repair-all", "count", String.valueOf(repaired));
            Lang.send(player, "repair.repaired-all", "count", String.valueOf(repaired));
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!repairItem(hand)) {
            SoundUtil.error(player);
            Lang.send(player, "repair.not-repairable");
            return;
        }

        SoundUtil.success(player);
        Lang.actionBar(player, "actionbar.repair");
        Lang.send(player, "repair.repaired-hand");
    }

    private boolean repairItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !(item.getItemMeta() instanceof Damageable damageable)) return false;
        if (damageable.getDamage() == 0) return false;
        damageable.setDamage(0);
        item.setItemMeta(damageable);
        return true;
    }

    @Override
    protected List<String> complete(CommandContext context) {
        if (context.argsLength() == 1 && "all".startsWith(context.arg(0).toLowerCase())) {
            return List.of("all");
        }
        return List.of();
    }
}
