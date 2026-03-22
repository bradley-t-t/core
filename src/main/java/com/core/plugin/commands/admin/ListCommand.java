package com.core.plugin.commands.admin;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.rank.RankLevel;
import com.core.plugin.service.BotService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Overrides vanilla /list to include fake players seamlessly.
 * Real and fake players are shuffled together so they can't be distinguished.
 */
@CommandInfo(
        name = "list",
        aliases = {"online", "who", "players"},
        minRank = RankLevel.MEMBER,
        description = "View online players",
        usage = "/list",
        icon = Material.PLAYER_HEAD
)
public final class ListCommand extends BaseCommand {

    public ListCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        List<String> allNames = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            allNames.add(player.getName());
        }

        BotService fakeService = service(BotService.class);
        if (fakeService != null && fakeService.isEnabled()) {
            allNames.addAll(fakeService.getOnlineFakes());
        }

        Collections.sort(allNames, String.CASE_INSENSITIVE_ORDER);

        int count = allNames.size();
        String nameList = String.join("&f, &e", allNames);

        Lang.send(context.sender(), "list.header", "count", count);
        Lang.sendRaw(context.sender(), "list.players", "players", nameList);
    }
}
