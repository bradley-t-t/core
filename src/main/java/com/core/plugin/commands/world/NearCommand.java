package com.core.plugin.commands.world;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.modules.rank.RankLevel;

import java.util.List;

@CommandInfo(
        name = "near",
        aliases = {"nearby"},
        minRank = RankLevel.MODERATOR,
        description = "List nearby players",
        usage = "/near [radius]",
        playerOnly = true,
        icon = Material.SPYGLASS
)
public final class NearCommand extends BaseCommand {

    private static final int DEFAULT_RADIUS = 200;
    private static final int MAX_RADIUS = 500;

    public NearCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        int radius = Math.min(context.argInt(0, DEFAULT_RADIUS), MAX_RADIUS);

        List<Player> nearby = player.getNearbyEntities(radius, radius, radius).stream()
                .filter(entity -> entity instanceof Player)
                .map(entity -> (Player) entity)
                .toList();

        if (nearby.isEmpty()) {
            Lang.send(player, "near.no-players", "radius", String.valueOf(radius));
            return;
        }

        Lang.sendRaw(player, "near.header",
                "count", String.valueOf(nearby.size()),
                "radius", String.valueOf(radius));

        for (Player nearbyPlayer : nearby) {
            double distance = player.getLocation().distance(nearbyPlayer.getLocation());
            Lang.sendRaw(player, "near.entry",
                    "player", nearbyPlayer.getName(),
                    "distance", String.format("%.1f", distance));
        }
    }
}
