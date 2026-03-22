package com.core.plugin.lang;

import com.core.plugin.util.MessageUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Static facade over {@link LanguageManager}. Initialized once during plugin startup
 * and used everywhere for message dispatch -- chat, action bar, and title.
 */
public final class Lang {

    private static LanguageManager manager;

    private Lang() {}

    public static void init(LanguageManager languageManager) {
        manager = languageManager;
    }

    /** Get a raw colorized message by key with placeholder replacement. */
    public static String get(String key, Object... replacements) {
        return manager.get(key, replacements);
    }

    /** Send a prefixed message by language key. */
    public static void send(CommandSender sender, String key, Object... replacements) {
        sender.sendMessage(manager.getPrefixed(key, replacements));
    }

    /** Send a non-prefixed message by language key. */
    public static void sendRaw(CommandSender sender, String key, Object... replacements) {
        sender.sendMessage(manager.get(key, replacements));
    }

    /** Send an action bar message by language key. */
    public static void actionBar(Player player, String key, Object... replacements) {
        String message = manager.get(key, replacements);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(message));
    }

    /** Send a title + subtitle by language keys. */
    public static void title(Player player, String titleKey, String subtitleKey, Object... replacements) {
        title(player, titleKey, subtitleKey, 10, 40, 10, replacements);
    }

    /** Send a title + subtitle with custom timing. */
    public static void title(Player player, String titleKey, String subtitleKey,
                             int fadeIn, int stay, int fadeOut, Object... replacements) {
        String title = titleKey != null ? manager.get(titleKey, replacements) : "";
        String subtitle = subtitleKey != null ? manager.get(subtitleKey, replacements) : "";
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    /** Send a raw string with prefix (not from language file). Fallback for dynamic content. */
    public static void sendDirect(CommandSender sender, String message) {
        String prefix = manager.get("prefix");
        sender.sendMessage(MessageUtil.colorize(prefix + message));
    }
}
