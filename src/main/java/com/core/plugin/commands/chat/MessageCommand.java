package com.core.plugin.commands.chat;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.PlayerStateService;
import com.core.plugin.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.rank.RankLevel;

import java.util.List;
import java.util.Random;

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

    private static final String[] FAKE_REPLIES = {
            "hey", "whats up", "yeah?", "hm?", "one sec",
            "busy rn", "oh hey", "sup", "lol what", "?",
    };

    private static final Random RANDOM = new Random();

    public MessageCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        String targetName = context.arg(0);
        String message = context.joinArgs(1);
        String senderName = context.sender().getName();

        if (PlayerUtil.isBot(targetName)) {
            Lang.sendRaw(context.sender(), "msg.sent", "target", targetName, "message", message);

            if (context.isPlayer()) {
                Player sender = (Player) context.sender();
                PlayerStateService stateService = service(PlayerStateService.class);
                stateService.setLastMessager(sender.getUniqueId(), sender.getUniqueId());

                long delay = 40 + RANDOM.nextInt(80);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!sender.isOnline()) return;
                    String reply = FAKE_REPLIES[RANDOM.nextInt(FAKE_REPLIES.length)];
                    Lang.sendRaw(sender, "msg.received", "sender", targetName, "message", reply);
                }, delay);
            }
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
