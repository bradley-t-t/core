package com.core.plugin.commands.warp;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.TeleportService;
import com.core.plugin.service.WarpService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "warp",
        minRank = RankLevel.MODERATOR,
        description = "Teleport to a warp",
        usage = "/warp <name>",
        playerOnly = true,
        minArgs = 1,
        icon = Material.END_PORTAL_FRAME
)
public final class WarpCommand extends BaseCommand {

    public WarpCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        String warpName = context.arg(0);
        Location location = service(WarpService.class).getWarp(warpName);

        if (location == null) {
            Lang.send(player, "warp.not-found", "name", warpName);
            return;
        }

        service(TeleportService.class).teleport(player, location);
        Lang.send(player, "warp.teleported", "name", warpName);
        Lang.title(player, null, "title.teleport-subtitle", "destination", warpName);
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
