package com.core.plugin.commands.inventory;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.modules.rank.RankLevel;

import java.util.List;

@CommandInfo(
        name = "enderchest",
        aliases = {"ec", "echest"},
        minRank = RankLevel.MODERATOR,
        description = "Open your or another player's ender chest",
        usage = "/ec [player]",
        playerOnly = true,
        icon = Material.ENDER_CHEST
)
public final class EnderchestCommand extends BaseCommand {

    public EnderchestCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player sender = context.playerOrError();
        if (sender == null) return;

        if (context.hasArg(0)) {
            if (!hasMinRank(sender, RankLevel.MODERATOR)) {
                Lang.send(sender, "generic.no-permission-others");
                return;
            }
            Player target = context.targetPlayer(0);
            if (target == null) return;

            sender.openInventory(target.getEnderChest());
            SoundUtil.openGui(sender);
            Lang.send(sender, "enderchest.opened-other", "player", target.getName());
        } else {
            sender.openInventory(sender.getEnderChest());
            SoundUtil.openGui(sender);
            Lang.send(sender, "enderchest.opened-self");
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
