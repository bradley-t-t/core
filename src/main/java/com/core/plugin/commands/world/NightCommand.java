package com.core.plugin.commands.world;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.rank.RankLevel;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@CommandInfo(
        name = "night",
        aliases = {"evening"},
        minRank = RankLevel.OPERATOR,
        description = "Set the time to night",
        playerOnly = true,
        icon = Material.CLOCK
)
public final class NightCommand extends BaseCommand {

    private static final long NIGHT_TICKS = 13000L;

    public NightCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        player.getWorld().setTime(NIGHT_TICKS);
        SoundUtil.success(player);
        Lang.send(player, "time.set-preset",
                "preset", "night",
                "ticks", String.valueOf(NIGHT_TICKS),
                "world", player.getWorld().getName());
    }
}
