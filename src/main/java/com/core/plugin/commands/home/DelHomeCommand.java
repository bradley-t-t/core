package com.core.plugin.commands.home;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.HomeService;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.rank.RankLevel;

import java.util.List;

@CommandInfo(
        name = "delhome",
        aliases = {"removehome", "rmhome"},
        minRank = RankLevel.MODERATOR,
        description = "Delete a home",
        usage = "/delhome <name>",
        playerOnly = true,
        minArgs = 1,
        icon = Material.RED_BED
)
public final class DelHomeCommand extends BaseCommand {

    public DelHomeCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        String homeName = context.arg(0);
        var homeService = service(HomeService.class);

        if (!homeService.homeExists(player.getUniqueId(), homeName)) {
            Lang.send(player, "home.not-found", "name", homeName);
            return;
        }

        homeService.deleteHome(player.getUniqueId(), homeName);
        Lang.send(player, "home.deleted", "name", homeName);
        SoundUtil.success(player);
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
