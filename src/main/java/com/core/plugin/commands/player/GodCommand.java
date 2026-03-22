package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.service.RankService;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.PlayerStateService;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.modules.rank.RankLevel;

import java.util.List;

@CommandInfo(
        name = "god",
        aliases = {"godmode", "invincible"},
        minRank = RankLevel.OPERATOR,
        description = "Toggle god mode for a player",
        usage = "/god [player]",
        icon = Material.TOTEM_OF_UNDYING
)
public final class GodCommand extends BaseCommand {

    public GodCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player target = context.targetOrSelf(0, RankLevel.MODERATOR, service(RankService.class));
        if (target == null) return;

        boolean enabled = service(PlayerStateService.class).toggleGod(target.getUniqueId());

        if (enabled) {
            SoundUtil.toggleOn(target);
            Lang.actionBar(target, "actionbar.god-enabled");
            Lang.send(target, "god.enabled");
        } else {
            SoundUtil.toggleOff(target);
            Lang.actionBar(target, "actionbar.god-disabled");
            Lang.send(target, "god.disabled");
        }

        if (!target.equals(context.sender())) {
            Lang.send(context.sender(), enabled ? "god.enabled-other" : "god.disabled-other",
                    "player", target.getName());
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
