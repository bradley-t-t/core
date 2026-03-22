package com.core.plugin.util;

import com.core.plugin.service.BotService;
import com.core.plugin.service.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Player lookup and filtering helpers used across commands and tab completers.
 * Includes fake players in results when the fake player system is active.
 */
public final class PlayerUtil {

    private static ServiceRegistry serviceRegistry;

    private PlayerUtil() {}

    /** Set the service registry for fake player lookup. Called once during plugin init. */
    public static void init(ServiceRegistry registry) {
        serviceRegistry = registry;
    }

    /** All online player names (real + fake), sorted for tab completion. */
    public static List<String> onlineNames() {
        return filteredOnlineNames(null, "");
    }

    /** Online player names (real + fake) filtered by prefix, for tab completion. */
    public static List<String> onlineNames(String prefix) {
        return filteredOnlineNames(null, prefix);
    }

    /** Online player names excluding the given player, filtered by prefix. */
    public static List<String> onlineNamesExcluding(Player excluded, String prefix) {
        return filteredOnlineNames(excluded, prefix);
    }

    /** Check if a name belongs to an online fake player. */
    public static boolean isBot(String name) {
        if (serviceRegistry == null) return false;
        BotService fakeService = serviceRegistry.get(BotService.class);
        return fakeService != null && fakeService.isFake(name);
    }

    private static List<String> filteredOnlineNames(Player excluded, String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        List<String> names = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(excluded)) continue;
            if (lowerPrefix.isEmpty() || player.getName().toLowerCase().startsWith(lowerPrefix)) {
                names.add(player.getName());
            }
        }

        for (String fake : getOnlineFakes()) {
            if (lowerPrefix.isEmpty() || fake.toLowerCase().startsWith(lowerPrefix)) {
                names.add(fake);
            }
        }

        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private static Set<String> getOnlineFakes() {
        if (serviceRegistry == null) return Set.of();
        BotService fakeService = serviceRegistry.get(BotService.class);
        if (fakeService == null || !fakeService.isEnabled()) return Set.of();
        return fakeService.getOnlineFakes();
    }
}
