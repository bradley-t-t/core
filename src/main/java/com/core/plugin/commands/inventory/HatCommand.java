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
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "hat",
        aliases = {"head", "helmet"},
        minRank = RankLevel.MODERATOR,
        description = "Wear the item in your hand as a hat",
        playerOnly = true,
        icon = Material.DIAMOND_HELMET
)
public final class HatCommand extends BaseCommand {

    public HatCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            SoundUtil.error(player);
            Lang.send(player, "hat.not-holding");
            return;
        }

        ItemStack currentHelmet = player.getInventory().getHelmet();
        player.getInventory().setHelmet(hand);
        player.getInventory().setItemInMainHand(currentHelmet);

        SoundUtil.success(player);
        Lang.send(player, "hat.wearing", "item", hand.getType().name().toLowerCase());
    }
}
