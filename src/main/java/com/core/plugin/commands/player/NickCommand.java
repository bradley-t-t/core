package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.rank.RankService;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.PlayerStateService;
import com.core.plugin.util.MessageUtil;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import com.core.plugin.rank.RankLevel;

@CommandInfo(
        name = "nick",
        aliases = {"nickname", "name"},
        minRank = RankLevel.MODERATOR,
        description = "Set or clear a player's nickname",
        usage = "/nick <name|off> [player]",
        minArgs = 1,
        icon = Material.NAME_TAG
)
public final class NickCommand extends BaseCommand {

    public NickCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player target = context.targetOrSelf(1, RankLevel.MODERATOR, service(RankService.class));
        if (target == null) return;

        var stateService = service(PlayerStateService.class);
        String input = context.arg(0);

        if (input.equalsIgnoreCase("off")) {
            stateService.setNickname(target.getUniqueId(), null);
            target.setDisplayName(target.getName());

            SoundUtil.success(target);
            Lang.send(target, "nick.cleared");

            if (!target.equals(context.sender())) {
                Lang.send(context.sender(), "nick.cleared-other", "player", target.getName());
            }
            return;
        }

        String colorized = MessageUtil.colorize(input);
        stateService.setNickname(target.getUniqueId(), input);
        target.setDisplayName(colorized);

        SoundUtil.success(target);
        Lang.send(target, "nick.set", "nick", colorized);

        if (!target.equals(context.sender())) {
            Lang.send(context.sender(), "nick.set-other", "player", target.getName(), "nick", colorized);
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 2 ? PlayerUtil.onlineNames(context.arg(1)) : List.of();
    }
}
