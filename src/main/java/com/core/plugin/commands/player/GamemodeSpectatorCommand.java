package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.rank.RankService;
import com.core.plugin.util.PlayerUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.rank.RankLevel;

import java.util.List;

@CommandInfo(
        name = "gmsp",
        aliases = {"spectator"},
        minRank = RankLevel.OPERATOR,
        description = "Switch to spectator mode",
        usage = "/gmsp [player]",
        icon = Material.GRASS_BLOCK
)
public final class GamemodeSpectatorCommand extends BaseCommand {

    public GamemodeSpectatorCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player target = context.targetOrSelf(0, RankLevel.MODERATOR, service(RankService.class));
        if (target == null) return;
        GamemodeCommand.applyGamemode(context, target, GameMode.SPECTATOR);
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
