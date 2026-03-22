package com.core.plugin.commands.teleport;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.service.TeleportService;
import com.core.plugin.util.PlayerUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "tpa",
        aliases = {"tprequest"},
        minRank = RankLevel.MODERATOR,
        description = "Request to teleport to a player",
        usage = "/tpa <player>",
        playerOnly = true,
        minArgs = 1,
        icon = Material.ENDER_PEARL
)
public final class TpaCommand extends BaseCommand {

    public TpaCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player sender = context.playerOrError();
        if (sender == null) return;

        Player target = context.targetPlayer(0);
        if (target == null || context.isSelf(target)) return;

        service(TeleportService.class).sendTpaRequest(sender, target);
    }

    @Override
    protected List<String> complete(CommandContext context) {
        if (context.argsLength() == 1 && context.isPlayer()) {
            return PlayerUtil.onlineNamesExcluding((Player) context.sender(), context.arg(0));
        }
        return List.of();
    }
}
