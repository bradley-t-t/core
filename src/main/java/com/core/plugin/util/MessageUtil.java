package com.core.plugin.util;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Centralized message formatting: color codes, prefix, and placeholder replacement.
 */
public final class MessageUtil {

    private static final String PREFIX = "&8[&cCore&8] &7";

    private MessageUtil() {}

    /** Send a prefixed, colorized message with placeholder pairs: key1, val1, key2, val2, ... */
    public static void send(CommandSender sender, String message, Object... replacements) {
        sender.sendMessage(colorize(PREFIX + replacePlaceholders(message, replacements)));
    }

    /** Send a colorized message without prefix. */
    public static void sendRaw(CommandSender sender, String message, Object... replacements) {
        sender.sendMessage(colorize(replacePlaceholders(message, replacements)));
    }

    /** Translate &-color codes to Bukkit color codes. */
    public static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Replace placeholder pairs in a message.
     * Example: replacePlaceholders("Hello {player}", "player", "Steve") -> "Hello Steve"
     */
    public static String replacePlaceholders(String message, Object... replacements) {
        if (replacements.length < 2) return message;
        String result = message;
        for (int i = 0; i < replacements.length - 1; i += 2) {
            result = result.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        }
        return result;
    }
}
