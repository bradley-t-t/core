package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.rank.RankService;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import com.core.plugin.rank.RankLevel;

@CommandInfo(
        name = "feed",
        aliases = {"eat", "satiate"},
        minRank = RankLevel.MODERATOR,
        description = "Restore a player's hunger",
        usage = "/feed [player]",
        icon = Material.COOKED_BEEF
)
public final class FeedCommand extends BaseCommand {

    public FeedCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player target = context.targetOrSelf(0, RankLevel.MODERATOR, service(RankService.class));
        if (target == null) return;

        target.setFoodLevel(20);
        target.setSaturation(20f);

        SoundUtil.feed(target);
        Lang.actionBar(target, "actionbar.fed");
        Lang.send(target, "feed.fed");

        if (!target.equals(context.sender())) {
            Lang.send(context.sender(), "feed.fed-other", "player", target.getName());
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
