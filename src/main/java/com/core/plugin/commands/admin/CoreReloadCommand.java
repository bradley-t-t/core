package com.core.plugin.commands.admin;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import java.util.List;
import com.core.plugin.rank.RankLevel;

/**
 * Hot-reloads the Core plugin: disables, reloads the JAR from disk, and re-enables.
 * Also supports reloading just the language file with /core reload lang.
 */
@CommandInfo(
        name = "core",
        description = "Core plugin admin commands",
        usage = "/core <reload|reload lang>",
        minRank = RankLevel.OPERATOR,
        minArgs = 1,
        icon = Material.REDSTONE
)
public final class CoreReloadCommand extends BaseCommand {

    private static final List<String> SUBCOMMANDS = List.of("reload");
    private static final List<String> RELOAD_ARGS = List.of("lang");

    public CoreReloadCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        String sub = context.arg(0).toLowerCase();

        if (!sub.equals("reload")) {
            Lang.send(context.sender(), "generic.usage", "usage", "/core <reload|reload lang>");
            return;
        }

        if (context.hasArg(1) && context.arg(1).equalsIgnoreCase("lang")) {
            reloadLanguage(context);
            return;
        }

        reloadPlugin(context);
    }

    @Override
    protected List<String> complete(CommandContext context) {
        if (context.argsLength() == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(context.arg(0).toLowerCase()))
                    .toList();
        }
        if (context.argsLength() == 2 && context.arg(0).equalsIgnoreCase("reload")) {
            return RELOAD_ARGS.stream()
                    .filter(s -> s.startsWith(context.arg(1).toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    private void reloadLanguage(CommandContext context) {
        plugin.reloadLanguage();
        Lang.send(context.sender(), "core.lang-reloaded");
        if (context.isPlayer()) SoundUtil.success((Player) context.sender());
    }

    private void reloadPlugin(CommandContext context) {
        Lang.send(context.sender(), "core.reloading");

        PluginManager pm = Bukkit.getPluginManager();
        pm.disablePlugin(plugin);
        pm.enablePlugin(plugin);

        Lang.send(context.sender(), "core.reloaded");
        if (context.isPlayer()) SoundUtil.success((Player) context.sender());
    }
}
