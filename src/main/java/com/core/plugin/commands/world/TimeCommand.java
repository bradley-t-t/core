package com.core.plugin.commands.world;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

@CommandInfo(
        name = "time",
        minRank = RankLevel.OPERATOR,
        description = "Set the time in the current world",
        usage = "/time <day|night|noon|midnight|set <ticks>|add <ticks>>",
        minArgs = 1,
        icon = Material.CLOCK
)
public final class TimeCommand extends BaseCommand {

    private static final Map<String, Long> PRESETS = Map.of(
            "day", 1000L,
            "night", 13000L,
            "noon", 6000L,
            "midnight", 18000L
    );

    private static final List<String> COMPLETIONS = List.of("day", "night", "noon", "midnight", "set", "add");

    public TimeCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        World world = context.isPlayer()
                ? ((Player) context.sender()).getWorld()
                : Bukkit.getWorlds().getFirst();

        String action = context.arg(0).toLowerCase();

        if (PRESETS.containsKey(action)) {
            long ticks = PRESETS.get(action);
            world.setTime(ticks);
            if (context.sender() instanceof Player player) SoundUtil.success(player);
            Lang.send(context.sender(), "time.set-preset",
                    "preset", action,
                    "ticks", String.valueOf(ticks),
                    "world", world.getName());
            return;
        }

        if (!context.hasArg(1)) {
            Lang.send(context.sender(), "generic.usage", "usage", info().usage());
            return;
        }

        int ticks = context.argInt(1, -1);
        if (ticks < 0) return;

        switch (action) {
            case "set" -> {
                world.setTime(ticks);
                if (context.sender() instanceof Player player) SoundUtil.success(player);
                Lang.send(context.sender(), "time.set-ticks",
                        "ticks", String.valueOf(ticks),
                        "world", world.getName());
            }
            case "add" -> {
                world.setTime(world.getTime() + ticks);
                if (context.sender() instanceof Player player) SoundUtil.success(player);
                Lang.send(context.sender(), "time.added-ticks",
                        "ticks", String.valueOf(ticks),
                        "world", world.getName());
            }
            default -> Lang.send(context.sender(), "generic.usage", "usage", info().usage());
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        if (context.argsLength() == 1) {
            return COMPLETIONS.stream()
                    .filter(c -> c.startsWith(context.arg(0).toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
