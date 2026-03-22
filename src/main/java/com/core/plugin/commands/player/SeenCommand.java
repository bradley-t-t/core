package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.PlayerStateService;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "seen",
        aliases = {"lastseen", "lastonline"},
        minRank = RankLevel.MEMBER,
        description = "Check when a player was last online",
        usage = "/seen <player>",
        minArgs = 1,
        icon = Material.PLAYER_HEAD
)
public final class SeenCommand extends BaseCommand {

    public SeenCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void execute(CommandContext context) {
        String name = context.arg(0);
        Player online = Bukkit.getPlayerExact(name);

        if (online != null) {
            Lang.send(context.sender(), "seen.online", "player", online.getName());
            return;
        }

        // Fake players show as online
        if (com.core.plugin.util.PlayerUtil.isBot(name)) {
            Lang.send(context.sender(), "seen.online", "player", name);
            return;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        long lastSeen = service(PlayerStateService.class).getLastSeen(offline.getUniqueId());

        if (lastSeen <= 0) {
            Lang.send(context.sender(), "seen.never-joined", "player", name);
            return;
        }

        Lang.send(context.sender(), "seen.last-seen",
                "player", name, "time", TimeUtil.formatRelative(lastSeen));
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
