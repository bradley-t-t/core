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
        name = "invsee",
        aliases = {"inventory", "openinv"},
        minRank = RankLevel.MODERATOR,
        description = "View another player's inventory",
        usage = "/invsee <player>",
        playerOnly = true,
        minArgs = 1,
        icon = Material.CHEST
)
public final class InvseeCommand extends BaseCommand {

    public InvseeCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player sender = context.playerOrError();
        if (sender == null) return;

        Player target = context.targetPlayer(0);
        if (target == null) return;

        if (context.isSelf(target)) return;

        sender.openInventory(target.getInventory());
        SoundUtil.openGui(sender);
        Lang.send(sender, "invsee.opened", "player", target.getName());
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
