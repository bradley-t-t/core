package com.core.plugin.modules.bots;

import com.core.plugin.CorePlugin;
import com.core.plugin.lang.Lang;
import com.core.plugin.listener.BotTabListener;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.service.BotService;
import com.core.plugin.service.RankService;
import com.core.plugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all bot message broadcasting: fake chat, join/leave messages,
 * and tab list management. Operators see an asterisk prefix to distinguish
 * bots from real players.
 */
public final class BotBroadcaster {

    private final CorePlugin plugin;
    private final BotTraits traits;
    private final Map<String, Long> mutedBots = new ConcurrentHashMap<>();

    public BotBroadcaster(CorePlugin plugin, BotTraits traits) {
        this.plugin = plugin;
        this.traits = traits;
    }

    /** Send a fake chat message from a bot. Muted bots are silently skipped. */
    public void sendChat(String sender, String message) {
        if (sender == null || message == null) return;
        if (isMuted(sender)) return;

        BotTraits.RankDisplay display = traits.resolveRankDisplay(sender);
        String base = display.prefix() + " " + sender + " &8>> " + display.chatColor() + message;
        String opBase = display.prefix() + " &7*" + sender + " &8>> " + display.chatColor() + message;

        broadcastWithOpDistinction(
                MessageUtil.colorize(base),
                MessageUtil.colorize(opBase));
    }

    /** Broadcast a join message and add to tab list. */
    public void broadcastJoin(String name, BotDataSeeder seeder, BotService botService) {
        seeder.ensurePlayerData(name);
        if (plugin.getConfig().getBoolean("join-quit-messages", true)) {
            broadcastWithOpDistinction(
                    Lang.get("fakeplayers.join", "player", name),
                    Lang.get("fakeplayers.join", "player", "*" + name));
        }
        BotTabListener tab = findTabListener();
        if (tab != null) tab.addFake(name, traits.getDisplayName(name), botService);
        refreshTab();
    }

    /** Broadcast a leave message and remove from tab list. */
    public void broadcastLeave(String name) {
        var botId = com.core.plugin.util.BotUtil.fakeUuid(name);
        plugin.dataManager().setLastSeen(botId, System.currentTimeMillis());

        if (plugin.getConfig().getBoolean("join-quit-messages", true)) {
            broadcastWithOpDistinction(
                    Lang.get("fakeplayers.leave", "player", name),
                    Lang.get("fakeplayers.leave", "player", "*" + name));
        }
        BotTabListener tab = findTabListener();
        if (tab != null) tab.removeFake(name);
        refreshTab();
    }

    /** Broadcast a bot message (e.g. vote, death) with op distinction. */
    public void broadcastMessage(String normalMessage, String opMessage) {
        broadcastWithOpDistinction(
                MessageUtil.colorize(normalMessage),
                MessageUtil.colorize(opMessage));
    }

    /** Remove all fakes from tab and clear entries. */
    public void clearAllFromTab() {
        BotTabListener tab = findTabListener();
        if (tab != null) {
            tab.removeAllFakes();
            tab.clearAll();
        }
    }

    public void refreshTab() {
        BotTabListener tab = findTabListener();
        if (tab != null) tab.refreshAll();
    }

    // --- Mute management ---

    public void mute(String botName, long durationMillis) {
        long unmute = durationMillis == -1 ? -1 : System.currentTimeMillis() + durationMillis;
        mutedBots.put(botName, unmute);
    }

    public boolean isMuted(String botName) {
        Long unmute = mutedBots.get(botName);
        if (unmute == null) return false;
        if (unmute == -1) return true;
        if (System.currentTimeMillis() >= unmute) {
            mutedBots.remove(botName);
            return false;
        }
        return true;
    }

    // --- Internal ---

    private void broadcastWithOpDistinction(String normal, String op) {
        RankService rankService = plugin.services().get(RankService.class);
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean isOp = rankService != null
                    && rankService.getLevel(player.getUniqueId()).isAtLeast(RankLevel.OPERATOR);
            player.sendMessage(isOp ? op : normal);
        }
        Bukkit.getConsoleSender().sendMessage(op);
    }

    private BotTabListener findTabListener() {
        for (var listener : org.bukkit.event.HandlerList.getRegisteredListeners(plugin)) {
            if (listener.getListener() instanceof BotTabListener tab) return tab;
        }
        return null;
    }
}
