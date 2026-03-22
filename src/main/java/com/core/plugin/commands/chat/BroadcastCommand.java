package com.core.plugin.commands.chat;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;

@CommandInfo(
        name = "broadcast",
        aliases = {"bc", "announce", "alert"},
        minRank = RankLevel.OPERATOR,
        description = "Broadcast a message to all players",
        usage = "/broadcast <message>",
        minArgs = 1,
        icon = Material.BELL
)
public final class BroadcastCommand extends BaseCommand {

    public BroadcastCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        String message = MessageUtil.colorize(context.joinArgs(0));
        Bukkit.broadcastMessage(Lang.get("broadcast.format", "message", message));
    }
}
