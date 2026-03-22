package com.core.plugin.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Categorized sound effects for common plugin actions.
 */
public final class SoundUtil {

    private SoundUtil() {}

    public static void teleport(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
    }

    public static void heal(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    public static void feed(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
    }

    public static void toggleOn(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
    }

    public static void toggleOff(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
    }

    public static void gamemode(Player player) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.2f);
    }

    public static void vanish(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
    }

    public static void error(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    public static void success(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    public static void ban(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1f);
    }

    public static void openGui(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    public static void clickGui(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
    }
}
