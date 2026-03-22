package com.core.plugin.command;

import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.service.RankService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Bridge between Bukkit's {@link Command} system and our {@link BaseCommand} framework.
 * Registered directly into the server's command map via reflection. Access control is
 * handled by the rank system in {@link BaseCommand}, not Bukkit permissions.
 * Hidden commands are invisible to unauthorized players in tab completion.
 */
public final class CoreCommand extends Command {

    private final BaseCommand executor;

    public CoreCommand(BaseCommand executor) {
        super(executor.info().name());
        CommandInfo info = executor.info();
        setDescription(info.description());
        setUsage(info.usage());
        setAliases(List.of(info.aliases()));
        this.executor = executor;
    }

    /**
     * Controls whether the command appears in tab completion.
     * Hidden commands return false for unauthorized players so the
     * command name never shows in the chat bar.
     */
    @Override
    public boolean testPermissionSilent(CommandSender sender) {
        if (!executor.info().hidden()) return true;
        if (!(sender instanceof Player player)) return true;
        return executor.hasMinRank(player, executor.info().minRank());
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        return executor.onCommand(sender, this, label, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        List<String> result = executor.onTabComplete(sender, this, alias, args);
        return result != null ? result : List.of();
    }
}
