package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.PlayerStateService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.rank.RankLevel;

@CommandInfo(
        name = "afk",
        aliases = {"away"},
        minRank = RankLevel.MEMBER,
        description = "Toggle AFK status",
        playerOnly = true,
        icon = Material.MINECART
)
public final class AfkCommand extends BaseCommand {

    public AfkCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        boolean nowAfk = service(PlayerStateService.class).toggleAfk(player.getUniqueId());
        String key = nowAfk ? "afk.now-afk" : "afk.no-longer-afk";

        Bukkit.broadcastMessage(Lang.get(key, "player", player.getName()));
    }
}
