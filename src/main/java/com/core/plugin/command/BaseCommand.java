package com.core.plugin.command;

import com.core.plugin.CorePlugin;
import com.core.plugin.listener.GuiListener;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.service.RankService;
import com.core.plugin.service.Service;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Abstract base for all Core commands. Handles rank-based access control,
 * player-only validation, and minimum argument enforcement before delegating
 * to subclass logic.
 */
public abstract class BaseCommand implements CommandExecutor, TabCompleter {

    protected final CorePlugin plugin;
    private final CommandInfo info;

    protected BaseCommand(CorePlugin plugin) {
        this.plugin = plugin;
        this.info = getClass().getAnnotation(CommandInfo.class);
        if (this.info == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " missing @CommandInfo annotation");
        }
    }

    public CommandInfo info() { return info; }

    @Override
    public final boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !hasMinRank(player, info.minRank())) {
            if (info.hidden()) {
                sender.sendMessage("\u00a7cUnknown or incomplete command, see below for error");
                sender.sendMessage("/" + label + "\u00a7c\u00a7n<--[HERE]");
            } else {
                Lang.send(sender, "rank.no-permission", "rank", info.minRank().name());
            }
            return true;
        }

        if (info.playerOnly() && !(sender instanceof Player)) {
            Lang.send(sender, "generic.player-only");
            return true;
        }

        if (args.length < info.minArgs()) {
            Lang.send(sender, "generic.usage", "usage", info.usage());
            return true;
        }

        execute(new CommandContext(sender, label, args));
        return true;
    }

    @Override
    public final List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender instanceof Player player && !hasMinRank(player, info.minRank())) {
            return List.of();
        }
        return complete(new CommandContext(sender, alias, args));
    }

    protected abstract void execute(CommandContext context);

    protected List<String> complete(CommandContext context) {
        return List.of();
    }

    protected <T extends Service> T service(Class<T> type) {
        return plugin.services().get(type);
    }

    protected GuiListener guiListener() {
        return plugin.guiListener();
    }

    /** Check if a player meets a minimum rank requirement. */
    protected boolean hasMinRank(Player player, RankLevel required) {
        return service(RankService.class).getLevel(player.getUniqueId()).isAtLeast(required);
    }
}
