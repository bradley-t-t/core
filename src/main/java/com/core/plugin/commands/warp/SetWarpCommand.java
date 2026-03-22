package com.core.plugin.commands.warp;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.WarpService;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.rank.RankLevel;

@CommandInfo(
        name = "setwarp",
        aliases = {"createwarp"},
        minRank = RankLevel.OPERATOR,
        description = "Create a warp at your current location",
        usage = "/setwarp <name>",
        playerOnly = true,
        minArgs = 1,
        icon = Material.END_PORTAL_FRAME
)
public final class SetWarpCommand extends BaseCommand {

    public SetWarpCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        String warpName = context.arg(0);
        service(WarpService.class).setWarp(warpName, player.getLocation());
        Lang.send(player, "warp.set", "name", warpName);
        SoundUtil.success(player);
    }
}
