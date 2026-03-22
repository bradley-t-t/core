package com.core.plugin.commands.teleport;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.TeleportService;
import com.core.plugin.util.PlayerUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.modules.rank.RankLevel;

import java.util.List;

@CommandInfo(
        name = "tphere",
        aliases = {"tph", "s2p", "bring"},
        minRank = RankLevel.OPERATOR,
        description = "Teleport a player to you",
        usage = "/tphere <player>",
        playerOnly = true,
        minArgs = 1,
        icon = Material.ENDER_PEARL
)
public final class TeleportHereCommand extends BaseCommand {

    public TeleportHereCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player sender = context.playerOrError();
        if (sender == null) return;

        Player target = context.targetPlayer(0);
        if (target == null || context.isSelf(target)) return;

        service(TeleportService.class).teleport(target, sender);
        Lang.send(sender, "tphere.teleported-to-you", "player", target.getName());
        Lang.send(target, "tphere.you-were-teleported", "player", sender.getName());
        Lang.title(target, null, "title.teleport-subtitle", "destination", sender.getName());
    }

    @Override
    protected List<String> complete(CommandContext context) {
        if (context.argsLength() == 1) {
            return PlayerUtil.onlineNamesExcluding((Player) context.sender(), context.arg(0));
        }
        return List.of();
    }
}
