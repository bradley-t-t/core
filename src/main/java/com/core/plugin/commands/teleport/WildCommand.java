package com.core.plugin.commands.teleport;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.service.WildTeleportService;
import com.core.plugin.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@CommandInfo(
        name = "wild",
        aliases = {"rtp", "randomtp"},
        minRank = RankLevel.MEMBER,
        playerOnly = true,
        description = "Teleport to a random location in the wild",
        usage = "/wild",
        icon = Material.GRASS_BLOCK
)
public final class WildCommand extends BaseCommand {

    public WildCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        WildTeleportService wildService = service(WildTeleportService.class);

        int remaining = wildService.getRemainingCooldown(player.getUniqueId());
        if (remaining > 0) {
            Lang.send(player, "wild.cooldown", "time", TimeUtil.formatDuration(remaining * 1000L));
            return;
        }

        wildService.markCooldown(player.getUniqueId());
        wildService.wildTeleport(player);
    }
}
