package com.core.plugin.commands.teleport;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.service.TeleportService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "tpaccept",
        aliases = {"tpyes", "tpallow"},
        minRank = RankLevel.MODERATOR,
        description = "Accept a pending TPA request",
        usage = "/tpaccept",
        playerOnly = true,
        icon = Material.ENDER_PEARL
)
public final class TpAcceptCommand extends BaseCommand {

    public TpAcceptCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        service(TeleportService.class).acceptTpa(player);
    }
}
