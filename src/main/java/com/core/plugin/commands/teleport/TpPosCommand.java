package com.core.plugin.commands.teleport;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.TeleportService;
import com.core.plugin.util.LocationUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "tppos",
        aliases = {"teleportpos", "tploc"},
        minRank = RankLevel.MODERATOR,
        description = "Teleport to specific coordinates",
        usage = "/tppos <x> <y> <z> [world]",
        playerOnly = true,
        minArgs = 3,
        icon = Material.ENDER_PEARL
)
public final class TpPosCommand extends BaseCommand {

    public TpPosCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        double x = context.argDouble(0, Double.NaN);
        double y = context.argDouble(1, Double.NaN);
        double z = context.argDouble(2, Double.NaN);
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) return;

        World world = player.getWorld();
        if (context.hasArg(3)) {
            world = Bukkit.getWorld(context.arg(3));
            if (world == null) {
                Lang.send(player, "generic.invalid-world", "world", context.arg(3));
                SoundUtil.error(player);
                return;
            }
        }

        Location destination = new Location(world, x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
        service(TeleportService.class).teleport(player, destination);
        Lang.send(player, "tppos.teleported", "location", LocationUtil.format(destination));
        Lang.title(player, null, "title.teleport-subtitle", "destination", LocationUtil.format(destination));
    }
}
