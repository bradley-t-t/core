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
import com.core.plugin.rank.RankLevel;

@CommandInfo(
        name = "rain",
        minRank = RankLevel.OPERATOR,
        description = "Set the weather to rain",
        playerOnly = true,
        icon = Material.SUNFLOWER
)
public final class RainCommand extends BaseCommand {

    public RainCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        World world = player.getWorld();
        world.setStorm(true);
        world.setThundering(false);

        SoundUtil.success(player);
        Lang.send(player, "weather.set", "type", "rain", "world", world.getName());
    }
}
