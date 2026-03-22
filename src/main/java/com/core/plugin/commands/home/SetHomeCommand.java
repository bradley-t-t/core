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
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "sethome",
        aliases = {"createhome"},
        minRank = RankLevel.MODERATOR,
        description = "Set a home at your current location",
        usage = "/sethome [name]",
        playerOnly = true,
        icon = Material.RED_BED
)
public final class SetHomeCommand extends BaseCommand {

    private static final String DEFAULT_HOME_NAME = "home";

    public SetHomeCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        String homeName = context.hasArg(0) ? context.arg(0) : DEFAULT_HOME_NAME;
        service(HomeService.class).setHome(player.getUniqueId(), homeName, player.getLocation());
        Lang.send(player, "home.set", "name", homeName);
        SoundUtil.success(player);
    }
}
