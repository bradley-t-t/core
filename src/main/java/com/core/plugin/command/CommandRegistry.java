package com.core.plugin.command;

import com.core.plugin.CorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;

import java.lang.reflect.Field;

/**
 * Registers {@link BaseCommand} instances into Bukkit's command map via reflection,
 * enabling dynamic command registration without plugin.yml entries.
 */
public final class CommandRegistry {

    private final CorePlugin plugin;
    private final CommandMap commandMap;

    public CommandRegistry(CorePlugin plugin) {
        this.plugin = plugin;
        this.commandMap = extractCommandMap();
    }

    /** Register a single command. */
    public void register(BaseCommand command) {
        commandMap.register(plugin.getName(), new CoreCommand(command));
    }

    /** Register multiple commands at once. */
    public void registerAll(BaseCommand... commands) {
        for (BaseCommand command : commands) {
            register(command);
        }
    }

    private CommandMap extractCommandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access Bukkit command map via reflection", e);
        }
    }
}
