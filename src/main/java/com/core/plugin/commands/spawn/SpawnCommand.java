package com.core.plugin.commands.spawn;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.rank.RankLevel;
import org.bukkit.Material;

@CommandInfo(
        name = "spawn",
        aliases = {"hub", "lobby"},
        minRank = RankLevel.MEMBER,
        description = "There is no spawn",
        usage = "/spawn",
        icon = Material.NETHER_STAR
)
public final class SpawnCommand extends BaseCommand {

    public SpawnCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Lang.send(context.sender(), "spawn.no-spawn");
    }
}
