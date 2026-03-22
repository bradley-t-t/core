package com.core.plugin.commands.teleport;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.TeleportService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

@CommandInfo(
        name = "top",
        minRank = RankLevel.OPERATOR,
        description = "Teleport to the highest block at your position",
        usage = "/top",
        playerOnly = true,
        icon = Material.FEATHER
)
public final class TopCommand extends BaseCommand {

    public TopCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        Location current = player.getLocation();
        World world = current.getWorld();
        int highestY = world.getHighestBlockYAt(current.getBlockX(), current.getBlockZ()) + 1;

        Location top = new Location(world, current.getX(), highestY, current.getZ(),
                current.getYaw(), current.getPitch());

        service(TeleportService.class).teleport(player, top);
        Lang.send(player, "top.teleported", "y", highestY);
        Lang.title(player, null, "title.teleport-subtitle", "destination", "Y: " + highestY);
    }
}
