package com.core.plugin.commands.world;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import com.core.plugin.rank.RankLevel;

@CommandInfo(
        name = "weather",
        minRank = RankLevel.OPERATOR,
        description = "Change the weather in a world",
        usage = "/weather <clear|rain|storm> [world]",
        minArgs = 1,
        icon = Material.SUNFLOWER
)
public final class WeatherCommand extends BaseCommand {

    private static final List<String> WEATHER_TYPES = List.of("clear", "rain", "storm");

    public WeatherCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        World world;
        if (context.hasArg(1)) {
            world = Bukkit.getWorld(context.arg(1));
            if (world == null) {
                Lang.send(context.sender(), "weather.world-not-found", "world", context.arg(1));
                return;
            }
        } else if (context.isPlayer()) {
            world = ((Player) context.sender()).getWorld();
        } else {
            Lang.send(context.sender(), "weather.specify-world");
            return;
        }

        String type = context.arg(0).toLowerCase();
        switch (type) {
            case "clear" -> {
                world.setStorm(false);
                world.setThundering(false);
            }
            case "rain" -> {
                world.setStorm(true);
                world.setThundering(false);
            }
            case "storm", "thunder" -> {
                world.setStorm(true);
                world.setThundering(true);
            }
            default -> {
                Lang.send(context.sender(), "weather.invalid-type");
                return;
            }
        }

        if (context.sender() instanceof Player player) SoundUtil.success(player);
        Lang.send(context.sender(), "weather.set", "type", type, "world", world.getName());
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return switch (context.argsLength()) {
            case 1 -> WEATHER_TYPES.stream()
                    .filter(t -> t.startsWith(context.arg(0).toLowerCase()))
                    .toList();
            case 2 -> Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(name -> name.toLowerCase().startsWith(context.arg(1).toLowerCase()))
                    .toList();
            default -> List.of();
        };
    }
}
