package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.rank.RankService;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.PlayerUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.rank.RankLevel;

import java.util.List;

@CommandInfo(
        name = "ping",
        aliases = {"ms", "latency"},
        minRank = RankLevel.MEMBER,
        description = "Show a player's ping",
        usage = "/ping [player]",
        icon = Material.CLOCK
)
public final class PingCommand extends BaseCommand {

    public PingCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player target = context.targetOrSelf(0, RankLevel.MODERATOR, service(RankService.class));
        if (target == null) return;

        int ping = target.getPing();

        if (target.equals(context.sender())) {
            Lang.send(context.sender(), "ping.self", "ping", ping);
        } else {
            Lang.send(context.sender(), "ping.other", "player", target.getName(), "ping", ping);
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
