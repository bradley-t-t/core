package com.core.plugin.command;

import com.core.plugin.lang.Lang;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

/**
 * Wraps a command invocation's sender and arguments, providing typed accessors
 * and validation helpers so individual commands stay concise.
 */
public final class CommandContext {

    private final CommandSender sender;
    private final String[] args;
    private final String label;

    public CommandContext(CommandSender sender, String label, String[] args) {
        this.sender = sender;
        this.label = label;
        this.args = args;
    }

    public CommandSender sender() { return sender; }

    public String label() { return label; }

    public String[] args() { return args; }

    public int argsLength() { return args.length; }

    public boolean hasArg(int index) { return index >= 0 && index < args.length; }

    public String arg(int index) { return hasArg(index) ? args[index] : ""; }

    public String joinArgs(int fromIndex) {
        if (fromIndex >= args.length) return "";
        return String.join(" ", Arrays.copyOfRange(args, fromIndex, args.length));
    }

    public int argInt(int index, int defaultValue) {
        if (!hasArg(index)) return defaultValue;
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException ignored) {
            Lang.send(sender, "generic.invalid-number", "input", args[index]);
            return defaultValue;
        }
    }

    public double argDouble(int index, double defaultValue) {
        if (!hasArg(index)) return defaultValue;
        try {
            return Double.parseDouble(args[index]);
        } catch (NumberFormatException ignored) {
            Lang.send(sender, "generic.invalid-number", "input", args[index]);
            return defaultValue;
        }
    }

    public boolean isPlayer() { return sender instanceof Player; }

    /** Returns the sender as a Player, or null with an error message if console. */
    public Player playerOrError() {
        if (sender instanceof Player player) return player;
        Lang.send(sender, "generic.player-only");
        return null;
    }

    /** Resolves arg at index to an online player, sending an error if not found. Returns null on failure. */
    public Player targetPlayer(int argIndex) {
        String name = arg(argIndex);
        if (name.isEmpty()) return null;
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            Lang.send(sender, "generic.player-not-online", "player", name);
        }
        return target;
    }

    /** Resolves a target: if arg exists use it, otherwise fall back to sender (must be player). */
    public Player targetOrSelf(int argIndex) {
        if (hasArg(argIndex)) return targetPlayer(argIndex);
        return playerOrError();
    }

    /**
     * Resolves a target: if arg exists and sender's rank meets the requirement, use it;
     * otherwise fall back to self. Used for "target others" gating.
     */
    public Player targetOrSelf(int argIndex, com.core.plugin.rank.RankLevel requiredRank,
                                com.core.plugin.rank.RankService rankService) {
        if (hasArg(argIndex)) {
            if (sender instanceof Player player
                    && !rankService.getLevel(player.getUniqueId()).isAtLeast(requiredRank)) {
                Lang.send(sender, "rank.no-permission");
                return null;
            }
            return targetPlayer(argIndex);
        }
        return playerOrError();
    }

    /**
     * Check if the resolved target is the sender. If so, sends an error and returns true.
     * Use this to prevent self-targeting on commands where it's nonsensical.
     */
    public boolean isSelf(Player target) {
        if (target != null && sender instanceof Player player && player.equals(target)) {
            Lang.send(sender, "generic.cannot-target-self");
            if (sender instanceof Player p) SoundUtil.error(p);
            return true;
        }
        return false;
    }
}
