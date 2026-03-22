package com.core.plugin.commands.teleport;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.service.TeleportService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.rank.RankLevel;

@CommandInfo(
        name = "tpdeny",
        aliases = {"tpno", "tprefuse"},
        minRank = RankLevel.MODERATOR,
        description = "Deny a pending TPA request",
        usage = "/tpdeny",
        playerOnly = true,
        icon = Material.ENDER_PEARL
)
public final class TpDenyCommand extends BaseCommand {

    public TpDenyCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        service(TeleportService.class).denyTpa(player);
    }
}
