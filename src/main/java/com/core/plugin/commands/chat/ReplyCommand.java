package com.core.plugin.commands.chat;

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

import java.util.UUID;

@CommandInfo(
        name = "reply",
        aliases = {"r", "re"},
        minRank = RankLevel.MEMBER,
        description = "Reply to the last player who messaged you",
        usage = "/reply <message>",
        playerOnly = true,
        minArgs = 1,
        icon = Material.WRITABLE_BOOK
)
public final class ReplyCommand extends BaseCommand {

    public ReplyCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player sender = context.playerOrError();
        if (sender == null) return;

        PlayerStateService stateService = service(PlayerStateService.class);
        UUID lastId = stateService.getLastMessager(sender.getUniqueId());

        if (lastId == null) {
            Lang.send(sender, "msg.no-reply-target");
            return;
        }

        Player target = Bukkit.getPlayer(lastId);
        if (target == null) {
            Lang.send(sender, "msg.reply-offline");
            return;
        }

        String message = context.joinArgs(0);

        Lang.sendRaw(sender, "msg.sent", "target", target.getName(), "message", message);
        Lang.sendRaw(target, "msg.received", "sender", sender.getName(), "message", message);

        stateService.setLastMessager(sender.getUniqueId(), target.getUniqueId());
        stateService.setLastMessager(target.getUniqueId(), sender.getUniqueId());
    }
}
