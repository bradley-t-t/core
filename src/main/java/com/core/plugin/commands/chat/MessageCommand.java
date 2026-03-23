package com.core.plugin.commands.chat;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.PlayerStateService;
import com.core.plugin.util.PlayerUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.modules.rank.RankLevel;

import java.util.List;

@CommandInfo(
        name = "msg",
        aliases = {"tell", "whisper", "pm", "message", "dm"},
        minRank = RankLevel.MEMBER,
        description = "Send a private message to a player",
        usage = "/msg <player> <message>",
        minArgs = 2,
        icon = Material.WRITABLE_BOOK
)
public final class MessageCommand extends BaseCommand {

    public MessageCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        String targetName = context.arg(0);
        String message = context.joinArgs(1);
        String senderName = context.sender().getName();

        if (PlayerUtil.isBot(targetName)) {
            // Bots don't reply to private messages — just show the sent confirmation
            Lang.sendRaw(context.sender(), "msg.sent", "target", targetName, "message", message);
            return;
        }

        Player target = context.targetPlayer(0);
        if (target == null) return;
        if (context.isSelf(target)) return;

        Lang.sendRaw(context.sender(), "msg.sent", "target", target.getName(), "message", message);
        Lang.sendRaw(target, "msg.received", "sender", senderName, "message", message);

        if (context.isPlayer()) {
            PlayerStateService stateService = service(PlayerStateService.class);
            Player sender = (Player) context.sender();
            stateService.setLastMessager(sender.getUniqueId(), target.getUniqueId());
            stateService.setLastMessager(target.getUniqueId(), sender.getUniqueId());
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
