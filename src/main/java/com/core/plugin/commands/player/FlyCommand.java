package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.service.RankService;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "fly",
        aliases = {"flight"},
        minRank = RankLevel.OPERATOR,
        description = "Toggle flight for a player",
        usage = "/fly [player]",
        icon = Material.ELYTRA
)
public final class FlyCommand extends BaseCommand {

    public FlyCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player target = context.targetOrSelf(0, RankLevel.MODERATOR, service(RankService.class));
        if (target == null) return;

        boolean enabled = !target.getAllowFlight();
        target.setAllowFlight(enabled);
        if (!enabled) target.setFlying(false);

        if (enabled) {
            SoundUtil.toggleOn(target);
            Lang.actionBar(target, "actionbar.fly-enabled");
            Lang.send(target, "fly.enabled");
        } else {
            SoundUtil.toggleOff(target);
            Lang.actionBar(target, "actionbar.fly-disabled");
            Lang.send(target, "fly.disabled");
        }

        if (!target.equals(context.sender())) {
            Lang.send(context.sender(), enabled ? "fly.enabled-other" : "fly.disabled-other",
                    "player", target.getName());
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
