package com.core.plugin.commands.claim;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.claim.ClaimRegion;
import com.core.plugin.service.ClaimService;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Shortcut command to delete a claim by name or location.
 * Equivalent to {@code /claim delete}.
 */
@CommandInfo(
        name = "unclaim",
        minRank = RankLevel.MEMBER,
        playerOnly = true,
        description = "Remove your claim",
        usage = "/unclaim [claimname]",
        icon = Material.TNT
)
public final class UnclaimCommand extends BaseCommand {

    public UnclaimCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        ClaimService claimService = service(ClaimService.class);
        ClaimRegion claim;

        if (context.hasArg(0)) {
            String claimName = context.arg(0);
            claim = claimService.getClaimByName(player.getUniqueId(), claimName);
            if (claim == null) {
                Lang.send(player, "claim.not-found", "name", claimName);
                return;
            }
        } else {
            claim = claimService.getClaimAt(player.getLocation());
            if (claim == null) {
                Lang.send(player, "claim.not-in-claim");
                return;
            }
        }

        if (!claim.ownerId().equals(player.getUniqueId())
                && !hasMinRank(player, RankLevel.OPERATOR)) {
            Lang.send(player, "claim.not-your-claim");
            return;
        }

        claimService.deleteClaim(player.getUniqueId(), claim.claimId());
        claimService.visualizer().cancelAll(player.getUniqueId());
        Lang.send(player, "claim.deleted", "name", claim.name());
        SoundUtil.success(player);
    }

    @Override
    protected List<String> complete(CommandContext context) {
        if (context.argsLength() == 1 && context.isPlayer()) {
            Player player = (Player) context.sender();
            ClaimService claimService = service(ClaimService.class);
            String prefix = context.arg(0).toLowerCase();
            return claimService.getClaimNames(player.getUniqueId()).stream()
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
