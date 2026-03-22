package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.rank.RankService;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.rank.RankLevel;

import java.util.List;
import java.util.Map;

@CommandInfo(
        name = "gamemode",
        aliases = {"gm"},
        minRank = RankLevel.OPERATOR,
        description = "Change a player's gamemode",
        usage = "/gamemode <mode> [player]",
        minArgs = 1,
        icon = Material.GRASS_BLOCK
)
public final class GamemodeCommand extends BaseCommand {

    private static final Map<String, GameMode> MODE_MAP = Map.ofEntries(
            Map.entry("survival", GameMode.SURVIVAL),
            Map.entry("s", GameMode.SURVIVAL),
            Map.entry("0", GameMode.SURVIVAL),
            Map.entry("creative", GameMode.CREATIVE),
            Map.entry("c", GameMode.CREATIVE),
            Map.entry("1", GameMode.CREATIVE),
            Map.entry("adventure", GameMode.ADVENTURE),
            Map.entry("a", GameMode.ADVENTURE),
            Map.entry("2", GameMode.ADVENTURE),
            Map.entry("spectator", GameMode.SPECTATOR),
            Map.entry("sp", GameMode.SPECTATOR),
            Map.entry("3", GameMode.SPECTATOR)
    );

    private static final List<String> MODE_NAMES = List.of(
            "survival", "creative", "adventure", "spectator"
    );

    public GamemodeCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        GameMode mode = MODE_MAP.get(context.arg(0).toLowerCase());
        if (mode == null) {
            Lang.send(context.sender(), "gamemode.unknown-mode", "mode", context.arg(0));
            return;
        }

        Player target = context.targetOrSelf(1, RankLevel.MODERATOR, service(RankService.class));
        if (target == null) return;

        applyGamemode(context, target, mode);
    }

    static void applyGamemode(CommandContext context, Player target, GameMode mode) {
        target.setGameMode(mode);
        String modeName = mode.name().toLowerCase();

        SoundUtil.gamemode(target);
        Lang.title(target, "title.gamemode", "title.gamemode-subtitle", "mode", modeName);
        Lang.send(target, "gamemode.set", "mode", modeName);

        if (!target.equals(context.sender())) {
            Lang.send(context.sender(), "gamemode.set-other", "player", target.getName(), "mode", modeName);
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return switch (context.argsLength()) {
            case 1 -> {
                String prefix = context.arg(0).toLowerCase();
                yield MODE_NAMES.stream()
                        .filter(name -> name.startsWith(prefix))
                        .toList();
            }
            case 2 -> PlayerUtil.onlineNames(context.arg(1));
            default -> List.of();
        };
    }
}
