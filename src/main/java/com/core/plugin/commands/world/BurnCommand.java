package com.core.plugin.commands.world;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.PlayerUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import com.core.plugin.rank.RankLevel;

@CommandInfo(
        name = "burn",
        aliases = {"ignite", "fire"},
        minRank = RankLevel.OPERATOR,
        description = "Set a player on fire",
        usage = "/burn <player> [seconds]",
        minArgs = 1,
        icon = Material.FLINT_AND_STEEL
)
public final class BurnCommand extends BaseCommand {

    private static final int DEFAULT_SECONDS = 5;

    public BurnCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player target = context.targetPlayer(0);
        if (target == null) return;

        if (context.isSelf(target)) return;

        int seconds = context.argInt(1, DEFAULT_SECONDS);
        target.setFireTicks(seconds * 20);

        Lang.send(context.sender(), "burn.set-fire",
                "player", target.getName(),
                "seconds", String.valueOf(seconds));
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
