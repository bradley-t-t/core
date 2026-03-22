package com.core.plugin.commands.moderation;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.gui.punishment.PunishmentGui;
import com.core.plugin.punishment.PunishmentSession;
import com.core.plugin.service.PunishmentService;
import com.core.plugin.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import com.core.plugin.rank.RankLevel;

@CommandInfo(
        name = "punish",
        aliases = {"pun"},
        minRank = RankLevel.MODERATOR,
        description = "Open the punishment GUI for a player",
        usage = "/punish <player>",
        minArgs = 1,
        icon = Material.BARRIER
)
public final class PunishCommand extends BaseCommand {

    public PunishCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void execute(CommandContext context) {
        Player moderator = context.playerOrError();
        if (moderator == null) return;

        String targetName = context.arg(0);
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        OfflinePlayer offlineTarget = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayer(targetName);

        if (onlineTarget != null && context.isSelf(onlineTarget)) return;

        PunishmentService punishmentService = service(PunishmentService.class);
        PunishmentSession session = punishmentService.startSession(
                moderator.getUniqueId(), offlineTarget.getUniqueId(),
                onlineTarget != null ? onlineTarget.getName() : targetName);

        PunishmentGui gui = new PunishmentGui(plugin, punishmentService, guiListener());
        gui.openTypeSelection(moderator, session);
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
