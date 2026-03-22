package com.core.plugin.commands.teleport;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.TeleportService;
import com.core.plugin.util.LocationUtil;
import com.core.plugin.util.PlayerUtil;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import com.core.plugin.rank.RankLevel;

import java.util.List;

@CommandInfo(
        name = "tp",
        aliases = {"teleport"},
        minRank = RankLevel.MODERATOR,
        description = "Teleport to a player or coordinates",
        usage = "/tp <player> [target] or /tp <player> <x> <y> <z>",
        minArgs = 1,
        icon = Material.ENDER_PEARL
)
public final class TeleportCommand extends BaseCommand {

    public TeleportCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        var teleportService = service(TeleportService.class);

        switch (context.argsLength()) {
            case 1 -> teleportSenderToTarget(context, teleportService);
            case 2 -> teleportPlayerToPlayer(context, teleportService);
            case 4 -> teleportPlayerToCoordinates(context, teleportService);
            default -> Lang.send(context.sender(), "generic.usage", "usage", info().usage());
        }
    }

    private void teleportSenderToTarget(CommandContext context, TeleportService teleportService) {
        Player sender = context.playerOrError();
        if (sender == null) return;

        Player target = context.targetPlayer(0);
        if (target == null || context.isSelf(target)) return;

        teleportService.teleport(sender, target);
        Lang.send(sender, "tp.teleported-to", "player", target.getName());
        Lang.title(sender, null, "title.teleport-subtitle", "destination", target.getName());
    }

    private void teleportPlayerToPlayer(CommandContext context, TeleportService teleportService) {
        if (!hasMinRank((Player) context.sender(), RankLevel.MODERATOR)) {
            Lang.send(context.sender(), "generic.no-permission-others");
            return;
        }

        Player player = context.targetPlayer(0);
        if (player == null) return;

        Player target = context.targetPlayer(1);
        if (target == null || context.isSelf(target)) return;

        teleportService.teleport(player, target);
        Lang.send(context.sender(), "tp.teleported-other", "player", player.getName(), "target", target.getName());
        Lang.title(player, null, "title.teleport-subtitle", "destination", target.getName());
    }

    private void teleportPlayerToCoordinates(CommandContext context, TeleportService teleportService) {
        Player player = context.targetPlayer(0);
        if (player == null) return;

        double x = context.argDouble(1, Double.NaN);
        double y = context.argDouble(2, Double.NaN);
        double z = context.argDouble(3, Double.NaN);
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) return;

        Location destination = new Location(player.getWorld(), x, y, z);
        teleportService.teleport(player, destination);
        Lang.send(context.sender(), "tp.teleported-to-coords", "player", player.getName(), "location", LocationUtil.format(destination));
        Lang.title(player, null, "title.teleport-subtitle", "destination", LocationUtil.format(destination));
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return PlayerUtil.onlineNames(context.arg(context.argsLength() - 1));
    }
}
