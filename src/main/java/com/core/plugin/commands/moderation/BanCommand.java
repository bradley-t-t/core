package com.core.plugin.commands.moderation;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.gui.punishment.PunishmentGui;
import com.core.plugin.punishment.PunishmentRegistry;
import com.core.plugin.punishment.PunishmentSession;
import com.core.plugin.service.PunishmentService;
import com.core.plugin.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import com.core.plugin.rank.RankLevel;

import java.util.List;

@CommandInfo(
        name = "ban",
        minRank = RankLevel.MODERATOR,
        description = "Open the punishment GUI to ban a player",
        usage = "/ban <player>",
        minArgs = 1,
        icon = Material.BARRIER
)
public final class BanCommand extends BaseCommand {

    public BanCommand(CorePlugin plugin) {
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
        PunishmentRegistry registry = service(PunishmentRegistry.class);
        PunishmentSession session = punishmentService.startSession(
                moderator.getUniqueId(), offlineTarget.getUniqueId(),
                onlineTarget != null ? onlineTarget.getName() : targetName);
        session.setType(registry.getTypeByKey("ban"));

        new PunishmentGui(plugin, punishmentService, guiListener())
                .openSeveritySelection(moderator, session);
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
