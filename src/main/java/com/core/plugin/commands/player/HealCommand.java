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
import org.bukkit.potion.PotionEffect;
import com.core.plugin.modules.rank.RankLevel;

import java.util.List;

@CommandInfo(
        name = "heal",
        aliases = {"hp"},
        minRank = RankLevel.MODERATOR,
        description = "Heal a player to full health",
        usage = "/heal [player]",
        icon = Material.GOLDEN_APPLE
)
public final class HealCommand extends BaseCommand {

    public HealCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player target = context.targetOrSelf(0, RankLevel.MODERATOR, service(RankService.class));
        if (target == null) return;

        target.setHealth(target.getMaxHealth());
        target.setFireTicks(0);
        for (PotionEffect effect : target.getActivePotionEffects()) {
            target.removePotionEffect(effect.getType());
        }

        SoundUtil.heal(target);
        Lang.actionBar(target, "actionbar.healed");
        Lang.send(target, "heal.healed");

        if (!target.equals(context.sender())) {
            Lang.send(context.sender(), "heal.healed-other", "player", target.getName());
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
