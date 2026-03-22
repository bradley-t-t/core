package com.core.plugin.commands.inventory;

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
        name = "clearinventory",
        aliases = {"ci", "clear", "clearinv", "empty"},
        minRank = RankLevel.OPERATOR,
        description = "Clear a player's inventory",
        usage = "/ci [player]",
        icon = Material.TNT
)
public final class ClearInventoryCommand extends BaseCommand {

    public ClearInventoryCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player target = context.targetOrSelf(0, RankLevel.MODERATOR, service(RankService.class));
        if (target == null) return;

        target.getInventory().clear();
        target.getInventory().setArmorContents(null);

        Lang.actionBar(target, "actionbar.inventory-cleared");
        SoundUtil.success(target);
        Lang.send(target, "clearinventory.cleared");

        if (!target.equals(context.sender())) {
            Lang.send(context.sender(), "clearinventory.cleared-other", "player", target.getName());
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }
}
