package com.core.plugin.commands.world;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@CommandInfo(
        name = "day",
        aliases = {"morning"},
        minRank = RankLevel.OPERATOR,
        description = "Set the time to day",
        playerOnly = true,
        icon = Material.CLOCK
)
public final class DayCommand extends BaseCommand {

    private static final long DAY_TICKS = 1000L;

    public DayCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        player.getWorld().setTime(DAY_TICKS);
        SoundUtil.success(player);
        Lang.send(player, "time.set-preset",
                "preset", "day",
                "ticks", String.valueOf(DAY_TICKS),
                "world", player.getWorld().getName());
    }
}
