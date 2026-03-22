package com.core.plugin.commands.warp;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.WarpService;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.rank.RankLevel;

import java.util.List;

@CommandInfo(
        name = "delwarp",
        aliases = {"removewarp", "rmwarp"},
        minRank = RankLevel.OPERATOR,
        description = "Delete a warp",
        usage = "/delwarp <name>",
        playerOnly = true,
        minArgs = 1,
        icon = Material.END_PORTAL_FRAME
)
public final class DelWarpCommand extends BaseCommand {

    public DelWarpCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        String warpName = context.arg(0);
        var warpService = service(WarpService.class);

        if (!warpService.warpExists(warpName)) {
            Lang.send(player, "warp.not-found", "name", warpName);
            return;
        }

        warpService.deleteWarp(warpName);
        Lang.send(player, "warp.deleted", "name", warpName);
        SoundUtil.success(player);
    }

    @Override
    protected List<String> complete(CommandContext context) {
        if (context.argsLength() != 1) return List.of();

        String prefix = context.arg(0).toLowerCase();
        return service(WarpService.class).getWarpNames().stream()
                .filter(name -> name.startsWith(prefix))
                .toList();
    }
}
