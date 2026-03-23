package com.core.plugin.commands.diamond;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@CommandInfo(
        name = "craft",
        aliases = {"workbench", "wb"},
        minRank = RankLevel.DIAMOND,
        description = "Open a portable crafting table",
        playerOnly = true,
        icon = Material.CRAFTING_TABLE
)
public final class CraftCommand extends BaseCommand {

    public CraftCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        player.openWorkbench(null, true);
        SoundUtil.openGui(player);
    }
}
