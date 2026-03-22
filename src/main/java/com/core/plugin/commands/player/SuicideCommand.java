package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.rank.RankLevel;

@CommandInfo(
        name = "suicide",
        aliases = {"kms"},
        minRank = RankLevel.MEMBER,
        playerOnly = true,
        description = "Strike yourself with lightning and die",
        usage = "/suicide",
        icon = Material.SKELETON_SKULL
)
public final class SuicideCommand extends BaseCommand {

    public SuicideCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = (Player) context.sender();
        player.getWorld().strikeLightning(player.getLocation());
        player.setHealth(0);
        Lang.send(player, "suicide.death");
    }
}
