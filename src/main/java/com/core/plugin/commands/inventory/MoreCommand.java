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
import com.core.plugin.rank.RankLevel;

@CommandInfo(
        name = "more",
        aliases = {"stack"},
        minRank = RankLevel.MODERATOR,
        description = "Max out the stack size of the item in your hand",
        playerOnly = true,
        icon = Material.CHEST_MINECART
)
public final class MoreCommand extends BaseCommand {

    public MoreCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            SoundUtil.error(player);
            Lang.send(player, "more.not-holding");
            return;
        }

        hand.setAmount(hand.getMaxStackSize());
        SoundUtil.success(player);
        Lang.send(player, "more.maxed", "amount", String.valueOf(hand.getMaxStackSize()));
    }
}
