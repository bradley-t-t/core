package com.core.plugin.commands.claim;

import com.core.plugin.CorePlugin;
import com.core.plugin.claim.ClaimRegion;
import com.core.plugin.claim.ClaimService;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.rank.RankLevel;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Removes a player from the trusted set of a claim.
 * Supports both location-based and name-based claim lookup.
 */
@CommandInfo(
        name = "untrust",
        minRank = RankLevel.MEMBER,
        playerOnly = true,
        minArgs = 1,
        description = "Remove a player from your claim",
        usage = "/untrust <player> [claimname]",
        icon = Material.BARRIER
)
public final class UntrustCommand extends BaseCommand {

    public UntrustCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        ClaimService claimService = service(ClaimService.class);
        ClaimRegion claim = resolveClaim(player, claimService, context);
        if (claim == null) return;

        if (!claim.ownerId().equals(player.getUniqueId())) {
            Lang.send(player, "claim.not-your-claim");
            return;
        }

        Player target = context.targetPlayer(0);
        if (target == null) return;

        if (!claim.trustedPlayerIds().contains(target.getUniqueId())) {
            Lang.send(player, "claim.not-trusted", "player", target.getName());
            return;
        }

        claimService.removeTrusted(claim.claimId(), target.getUniqueId());
        Lang.send(player, "claim.untrusted", "player", target.getName());
        SoundUtil.success(player);
    }

    @Override
    protected List<String> complete(CommandContext context) {
        if (context.argsLength() == 1) {
            return PlayerUtil.onlineNames(context.arg(0));
        }
        if (context.argsLength() == 2 && context.isPlayer()) {
            Player player = (Player) context.sender();
            ClaimService claimService = service(ClaimService.class);
            String prefix = context.arg(1).toLowerCase();
            return claimService.getClaimNames(player.getUniqueId()).stream()
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private ClaimRegion resolveClaim(Player player, ClaimService claimService, CommandContext context) {
        if (context.hasArg(1)) {
            String claimName = context.arg(1);
            ClaimRegion claim = claimService.getClaimByName(player.getUniqueId(), claimName);
            if (claim == null) {
                Lang.send(player, "claim.not-found", "name", claimName);
            }
            return claim;
        }

        ClaimRegion claim = claimService.getClaimAt(player.getLocation());
        if (claim == null) {
            Lang.send(player, "claim.not-in-claim");
        }
        return claim;
    }
}
