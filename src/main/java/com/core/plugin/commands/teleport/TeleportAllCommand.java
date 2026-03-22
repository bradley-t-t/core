package com.core.plugin.commands.teleport;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "tpall",
        minRank = RankLevel.OPERATOR,
        description = "Teleport all online players to you",
        usage = "/tpall",
        playerOnly = true,
        icon = Material.ENDER_PEARL
)
public final class TeleportAllCommand extends BaseCommand {

    public TeleportAllCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player sender = context.playerOrError();
        if (sender == null) return;

        var teleportService = service(TeleportService.class);
        int count = 0;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(sender)) continue;
            teleportService.teleport(online, sender);
            Lang.title(online, null, "title.teleport-subtitle", "destination", sender.getName());
            count++;
        }

        Lang.send(sender, "tpall.teleported", "count", count);
    }
}
