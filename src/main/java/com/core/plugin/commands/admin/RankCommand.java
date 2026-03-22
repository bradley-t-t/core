package com.core.plugin.commands.admin;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.rank.Rank;
import com.core.plugin.rank.RankLevel;
import com.core.plugin.rank.RankService;
import com.core.plugin.util.MessageUtil;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

@CommandInfo(
        name = "rank",
        description = "Manage player ranks",
        usage = "/rank <set|info|list|reload>",
        minRank = RankLevel.OPERATOR,
        minArgs = 1,
        icon = Material.GOLDEN_HELMET
)
public final class RankCommand extends BaseCommand {

    private static final List<String> SUBCOMMANDS = List.of("set", "info", "list", "reload");

    public RankCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        RankService rankService = service(RankService.class);

        switch (context.arg(0).toLowerCase()) {
            case "set" -> handleSet(context, rankService);
            case "info" -> handleInfo(context, rankService);
            case "list" -> handleList(context, rankService);
            case "reload" -> handleReload(context, rankService);
            default -> Lang.send(context.sender(), "generic.usage", "usage", "/rank <set|info|list|reload>");
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        RankService rankService = service(RankService.class);

        return switch (context.argsLength()) {
            case 1 -> SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(context.arg(0).toLowerCase()))
                    .toList();
            case 2 -> switch (context.arg(0).toLowerCase()) {
                case "set", "info" -> PlayerUtil.onlineNames(context.arg(1));
                default -> List.of();
            };
            case 3 -> context.arg(0).equalsIgnoreCase("set")
                    ? java.util.Arrays.stream(RankLevel.values())
                    .map(r -> r.name().toLowerCase())
                    .filter(r -> r.startsWith(context.arg(2).toLowerCase()))
                    .toList()
                    : List.of();
            default -> List.of();
        };
    }

    private void handleSet(CommandContext context, RankService rankService) {
        if (context.argsLength() < 3) {
            Lang.send(context.sender(), "generic.usage", "usage", "/rank set <player> <rank>");
            return;
        }

        if (context.isPlayer() && !hasMinRank((Player) context.sender(), RankLevel.OPERATOR)) {
            Lang.send(context.sender(), "rank.no-permission");
            return;
        }

        Player target = context.targetPlayer(1);
        if (target == null) return;

        RankLevel level = RankLevel.fromString(context.arg(2));
        if (level == null) {
            Lang.send(context.sender(), "rank.not-found", "rank", context.arg(2));
            return;
        }

        rankService.setRank(target.getUniqueId(), level);
        Rank rank = rankService.getDisplayConfig(level);

        target.setPlayerListName(MessageUtil.colorize(rank.displayPrefix() + " " + target.getName()));

        Lang.send(context.sender(), "rank.set", "player", target.getName(), "rank", level.name().toLowerCase());
        Lang.send(target, "rank.your-rank-set", "rank", level.name().toLowerCase(), "prefix", rank.displayPrefix());
        if (context.isPlayer()) SoundUtil.success((Player) context.sender());
        SoundUtil.success(target);
    }

    private void handleInfo(CommandContext context, RankService rankService) {
        Player target;
        if (context.hasArg(1)) {
            target = context.targetPlayer(1);
        } else {
            target = context.playerOrError();
        }
        if (target == null) return;

        Rank rank = rankService.getRank(target.getUniqueId());
        Lang.send(context.sender(), "rank.info-header", "player", target.getName());
        Lang.send(context.sender(), "rank.info-rank",
                "prefix", rank.displayPrefix(), "rank", rank.name());
        Lang.send(context.sender(), "rank.info-weight", "weight", rank.weight());
    }

    private void handleList(CommandContext context, RankService rankService) {
        Lang.send(context.sender(), "rank.list-header");
        for (Rank rank : rankService.getAllRanks()) {
            Lang.send(context.sender(), "rank.list-entry",
                    "prefix", rank.displayPrefix(),
                    "rank", rank.name(),
                    "weight", rank.weight());
        }
    }

    private void handleReload(CommandContext context, RankService rankService) {
        if (context.isPlayer() && !hasMinRank((Player) context.sender(), RankLevel.OPERATOR)) {
            Lang.send(context.sender(), "rank.no-permission");
            return;
        }

        rankService.reload();
        Lang.send(context.sender(), "rank.reloaded", "count", rankService.getAllRanks().size());
        if (context.isPlayer()) SoundUtil.success((Player) context.sender());
    }
}
