package com.core.plugin.commands.moderation;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.PlayerStateService;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "freeze",
        aliases = {"ss", "screenshare"},
        minRank = RankLevel.MODERATOR,
        description = "Toggle freeze on a player",
        usage = "/freeze <player>",
        minArgs = 1,
        icon = Material.ICE
)
public final class FreezeCommand extends BaseCommand {

    public FreezeCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player target = context.targetPlayer(0);
        if (target == null) return;
        if (context.isSelf(target)) return;

        PlayerStateService stateService = service(PlayerStateService.class);
        boolean nowFrozen = stateService.toggleFreeze(target.getUniqueId());

        if (nowFrozen) {
            Lang.send(context.sender(), "freeze.frozen", "player", target.getName());
            Lang.send(target, "freeze.frozen-target");
            SoundUtil.toggleOn(target);
        } else {
            Lang.send(context.sender(), "freeze.unfrozen", "player", target.getName());
            Lang.send(target, "freeze.unfrozen-target");
            SoundUtil.toggleOff(target);
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
