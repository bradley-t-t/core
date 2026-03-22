package com.core.plugin.commands.teleport;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.TeleportService;
import com.core.plugin.util.LocationUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "back",
        aliases = {"return", "ret"},
        minRank = RankLevel.MODERATOR,
        description = "Teleport to your last location",
        usage = "/back",
        playerOnly = true,
        icon = Material.COMPASS
)
public final class BackCommand extends BaseCommand {

    public BackCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        var teleportService = service(TeleportService.class);
        Location backLocation = teleportService.getBackLocation(player.getUniqueId());

        if (backLocation == null) {
            Lang.send(player, "back.no-location");
            SoundUtil.error(player);
            return;
        }

        teleportService.teleport(player, backLocation);
        Lang.send(player, "back.teleported", "location", LocationUtil.format(backLocation));
        Lang.title(player, null, "title.teleport-subtitle", "destination", LocationUtil.format(backLocation));
    }
}
