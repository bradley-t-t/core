package com.core.plugin.commands.home;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.HomeService;
import com.core.plugin.service.TeleportService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "home",
        aliases = {"h"},
        minRank = RankLevel.MODERATOR,
        description = "Teleport to a home",
        usage = "/home [name]",
        playerOnly = true,
        icon = Material.RED_BED
)
public final class HomeCommand extends BaseCommand {

    private static final String DEFAULT_HOME_NAME = "home";

    public HomeCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        String homeName = context.hasArg(0) ? context.arg(0) : DEFAULT_HOME_NAME;
        Location location = service(HomeService.class).getHome(player.getUniqueId(), homeName);

        if (location == null) {
            Lang.send(player, "home.not-found", "name", homeName);
            return;
        }

        service(TeleportService.class).teleport(player, location);
        Lang.send(player, "home.teleported", "name", homeName);
        Lang.title(player, null, "title.teleport-subtitle", "destination", homeName);
    }

    @Override
    protected List<String> complete(CommandContext context) {
        if (context.argsLength() != 1 || !context.isPlayer()) return List.of();

        Player player = (Player) context.sender();
        String prefix = context.arg(0).toLowerCase();
        return service(HomeService.class).getHomeNames(player.getUniqueId()).stream()
                .filter(name -> name.startsWith(prefix))
                .toList();
    }
}
