package com.core.plugin.commands.chat;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.BotService;
import com.core.plugin.service.PlayerStateService;
import com.core.plugin.util.BotUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.modules.rank.RankLevel;

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

        String message = context.joinArgs(0);

        // Check if replying to a bot
        BotService botService = service(BotService.class);
        if (botService != null) {
            String botName = botService.getOnlineFakes().stream()
                    .filter(name -> BotUtil.fakeUuid(name).equals(lastId))
                    .findFirst().orElse(null);

            if (botName != null) {
                // Bots don't reply to private messages
                Lang.sendRaw(sender, "msg.sent", "target", botName, "message", message);
                return;
            }
        }

        Player target = Bukkit.getPlayer(lastId);
        if (target == null) {
            Lang.send(sender, "msg.reply-offline");
            return;
        }

        Lang.sendRaw(sender, "msg.sent", "target", target.getName(), "message", message);
        Lang.sendRaw(target, "msg.received", "sender", sender.getName(), "message", message);

        stateService.setLastMessager(sender.getUniqueId(), target.getUniqueId());
        stateService.setLastMessager(target.getUniqueId(), sender.getUniqueId());
    }
}
