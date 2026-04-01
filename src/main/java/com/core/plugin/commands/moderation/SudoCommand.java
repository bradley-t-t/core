package com.core.plugin.commands.moderation;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.RankService;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.modules.rank.RankLevel;

import java.util.List;

@CommandInfo(
        name = "sudo",
        aliases = {"forcecmd"},
        minRank = RankLevel.OPERATOR,
        description = "Force a player to execute a command or send a chat message",
        usage = "/sudo <player> <command...>",
        minArgs = 2,
        icon = Material.COMMAND_BLOCK
)
public final class SudoCommand extends BaseCommand {

    public SudoCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player target = context.targetPlayer(0);
        if (target == null) return;
        if (context.isSelf(target)) return;

        RankService rankService = plugin.services().get(RankService.class);
        if (context.isPlayer()) {
            Player senderPlayer = (Player) context.sender();
            RankLevel senderRank = rankService.getLevel(senderPlayer.getUniqueId());
            RankLevel targetRank = rankService.getLevel(target.getUniqueId());
            if (targetRank.weight() >= senderRank.weight()) {
                context.sender().sendMessage(ChatColor.RED + "You cannot sudo a player of equal or higher rank.");
                return;
            }
        }

        String input = context.joinArgs(1);

        if (input.startsWith("/")) {
            target.performCommand(input.substring(1));
        } else {
            target.chat(input);
        }

        Lang.send(context.sender(), "sudo.forced", "player", target.getName(), "input", input);
        if (context.isPlayer()) SoundUtil.success((Player) context.sender());
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
