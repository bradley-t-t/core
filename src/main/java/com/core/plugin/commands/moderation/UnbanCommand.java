package com.core.plugin.commands.moderation;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.List;
import com.core.plugin.rank.RankLevel;

@CommandInfo(
        name = "unban",
        aliases = {"pardon"},
        minRank = RankLevel.OPERATOR,
        description = "Unban a player",
        usage = "/unban <player>",
        minArgs = 1,
        icon = Material.BARRIER
)
public final class UnbanCommand extends BaseCommand {

    public UnbanCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        String targetName = context.arg(0);
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);

        if (!banList.isBanned(targetName)) {
            Lang.send(context.sender(), "unban.not-banned", "player", targetName);
            return;
        }

        banList.pardon(targetName);
        Lang.send(context.sender(), "unban.unbanned", "player", targetName);
    }

    @Override
    protected List<String> complete(CommandContext context) {
        if (context.argsLength() != 1) return List.of();
        String prefix = context.arg(0).toLowerCase();
        return Bukkit.getBanList(BanList.Type.NAME).getBanEntries().stream()
                .map(entry -> entry.getTarget())
                .filter(name -> name != null && name.toLowerCase().startsWith(prefix))
                .toList();
    }
}
