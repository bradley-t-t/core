package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.rank.RankService;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.PlayerStateService;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import com.core.plugin.rank.RankLevel;

@CommandInfo(
        name = "vanish",
        aliases = {"v", "invisible", "vis"},
        minRank = RankLevel.MODERATOR,
        description = "Toggle vanish for a player",
        usage = "/vanish [player]",
        icon = Material.GLASS
)
public final class VanishCommand extends BaseCommand {

    public VanishCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player target = context.targetOrSelf(0, RankLevel.MODERATOR, service(RankService.class));
        if (target == null) return;

        boolean vanished = service(PlayerStateService.class).toggleVanish(target.getUniqueId());

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(target)) continue;
            if (vanished) {
                online.hidePlayer(plugin, target);
            } else {
                online.showPlayer(plugin, target);
            }
        }

        if (vanished) {
            SoundUtil.toggleOn(target);
            Lang.actionBar(target, "actionbar.vanish-on");
            Lang.send(target, "vanish.vanished");
        } else {
            SoundUtil.toggleOff(target);
            Lang.actionBar(target, "actionbar.vanish-off");
            Lang.send(target, "vanish.visible");
        }

        if (!target.equals(context.sender())) {
            Lang.send(context.sender(), vanished ? "vanish.vanished-other" : "vanish.visible-other",
                    "player", target.getName());
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
