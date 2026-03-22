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
        name = "sun",
        minRank = RankLevel.OPERATOR,
        description = "Set the weather to clear",
        playerOnly = true,
        icon = Material.SUNFLOWER
)
public final class SunCommand extends BaseCommand {

    public SunCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        World world = player.getWorld();
        world.setStorm(false);
        world.setThundering(false);

        SoundUtil.success(player);
        Lang.send(player, "weather.set", "type", "clear", "world", world.getName());
    }
}
