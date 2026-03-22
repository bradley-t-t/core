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
        name = "speed",
        aliases = {"walkspeed", "flyspeed"},
        minRank = RankLevel.MODERATOR,
        description = "Set walk and fly speed",
        usage = "/speed <1-10> [player]",
        minArgs = 1,
        icon = Material.SUGAR
)
public final class SpeedCommand extends BaseCommand {

    public SpeedCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        int raw = context.argInt(0, -1);
        if (raw == -1) return;

        if (raw < 1 || raw > 10) {
            Lang.send(context.sender(), "speed.out-of-range");
            return;
        }

        float speed = raw / 10f;

        Player target = context.targetOrSelf(1, RankLevel.MODERATOR, service(RankService.class));
        if (target == null) return;

        target.setWalkSpeed(speed);
        target.setFlySpeed(speed);

        SoundUtil.success(target);
        Lang.actionBar(target, "actionbar.speed-set", "speed", raw);
        Lang.send(target, "speed.set", "speed", raw);

        if (!target.equals(context.sender())) {
            Lang.send(context.sender(), "speed.set-other", "speed", raw, "player", target.getName());
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 2 ? PlayerUtil.onlineNames(context.arg(1)) : List.of();
    }
}
