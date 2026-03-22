package com.core.plugin.commands.world;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "storm",
        aliases = {"thunder"},
        minRank = RankLevel.OPERATOR,
        description = "Set the weather to storm",
        playerOnly = true,
        icon = Material.SUNFLOWER
)
public final class StormCommand extends BaseCommand {

    public StormCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        World world = player.getWorld();
        world.setStorm(true);
        world.setThundering(true);

        SoundUtil.success(player);
        Lang.send(player, "weather.set", "type", "storm", "world", world.getName());
    }
}
