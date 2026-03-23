package com.core.plugin.commands.diamond;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.service.DiamondService;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@CommandInfo(
        name = "joinmessage",
        aliases = {"joinmsg", "jm"},
        minRank = RankLevel.DIAMOND,
        description = "Set or clear your custom join message",
        usage = "/joinmessage [message|off]",
        playerOnly = true,
        icon = Material.OAK_SIGN
)
public final class JoinMessageCommand extends BaseCommand {

    private static final int MAX_LENGTH = 64;

    public JoinMessageCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        DiamondService diamondService = service(DiamondService.class);

        // No args: show current join message
        if (!context.hasArg(0)) {
            String current = diamondService.getJoinMessage(player.getUniqueId());
            if (current == null) {
                Lang.send(player, "diamond.joinmsg-none");
            } else {
                Lang.send(player, "diamond.joinmsg-current", "message", current);
            }
            return;
        }

        String input = context.joinArgs(0);

        if (input.equalsIgnoreCase("off") || input.equalsIgnoreCase("clear")) {
            diamondService.clearJoinMessage(player.getUniqueId());
            SoundUtil.toggleOff(player);
            Lang.send(player, "diamond.joinmsg-cleared");
            return;
        }

        if (input.length() > MAX_LENGTH) {
            Lang.send(player, "diamond.joinmsg-too-long", "max", String.valueOf(MAX_LENGTH));
            return;
        }

        diamondService.setJoinMessage(player.getUniqueId(), input);
        SoundUtil.toggleOn(player);
        Lang.send(player, "diamond.joinmsg-set", "message", input);
    }
}
