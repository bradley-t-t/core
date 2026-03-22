package com.core.plugin.commands.inventory;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.rank.RankLevel;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

@CommandInfo(
        name = "give",
        aliases = {"i", "item"},
        minRank = RankLevel.OPERATOR,
        description = "Give a player an item",
        usage = "/give <player> <material> [amount]",
        minArgs = 2,
        icon = Material.HOPPER
)
public final class GiveCommand extends BaseCommand {

    public GiveCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player target = context.targetPlayer(0);
        if (target == null) return;

        Material material = Material.matchMaterial(context.arg(1));
        if (material == null) {
            Lang.send(context.sender(), "give.unknown-material", "material", context.arg(1));
            return;
        }

        int amount = Math.max(1, Math.min(context.argInt(2, 1), 64));
        String materialName = material.name().toLowerCase();

        target.getInventory().addItem(new ItemStack(material, amount));
        Lang.send(context.sender(), "give.given",
                "amount", String.valueOf(amount),
                "item", materialName,
                "player", target.getName());

        if (!target.equals(context.sender())) {
            SoundUtil.success(target);
            Lang.send(target, "give.received",
                    "amount", String.valueOf(amount),
                    "item", materialName);
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return switch (context.argsLength()) {
            case 1 -> PlayerUtil.onlineNames(context.arg(0));
            case 2 -> {
                String prefix = context.arg(1).toLowerCase();
                yield Arrays.stream(Material.values())
                        .filter(m -> !m.isLegacy())
                        .map(m -> m.name().toLowerCase())
                        .filter(name -> name.startsWith(prefix))
                        .limit(30)
                        .toList();
            }
            default -> List.of();
        };
    }
}
