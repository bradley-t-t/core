package com.core.plugin.listener;

import com.core.plugin.CorePlugin;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.rank.Rank;
import com.core.plugin.service.RankService;
import com.core.plugin.service.BotService;
import com.core.plugin.service.PlayerStateService;
import com.core.plugin.util.MessageUtil;
import com.core.plugin.util.TimeUtil;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Formats chat with rank prefix, nickname support, and interactive player names.
 * Hovering a player's name shows a quick info preview; clicking runs /user.
 * Blocks muted players from chatting.
 */
public final class ChatListener implements Listener {

    private final CorePlugin plugin;

    public ChatListener(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        PlayerStateService stateService = plugin.services().get(PlayerStateService.class);
        RankService rankService = plugin.services().get(RankService.class);
        Player player = event.getPlayer();

        if (stateService.isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            Lang.send(player, "chat.muted");
            return;
        }

        event.setCancelled(true);

        Rank rank = rankService.getRank(player.getUniqueId());
        String prefix = rank != null ? rank.displayPrefix() : "";
        String chatColor = rank != null ? rank.chatColor() : "&f";

        String nickname = stateService.getNickname(player.getUniqueId());
        String displayName = nickname != null ? nickname : player.getName();

        // Build hover tooltip
        String hoverText = buildHoverPreview(player, rank, stateService);

        // Interactive player name component with hover + click
        BaseComponent[] nameComponents = TextComponent.fromLegacyText(
                MessageUtil.colorize(prefix + " " + displayName));
        for (BaseComponent component : nameComponents) {
            component.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT, new Text(hoverText)));
            component.setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND, "/user " + player.getName()));
        }

        // Strip & color/format codes from raw input to prevent player chat injection
        String rawMessage = event.getMessage().replaceAll("(?i)&[0-9a-fk-or]", "");

        // Separator and message
        BaseComponent[] separator = TextComponent.fromLegacyText(
                MessageUtil.colorize(" &8>> "));
        BaseComponent[] messageBody = TextComponent.fromLegacyText(
                MessageUtil.colorize(chatColor + rawMessage));

        // Combine into final message
        ComponentBuilder fullMessage = new ComponentBuilder();
        for (BaseComponent c : nameComponents) fullMessage.append(c);
        for (BaseComponent c : separator) fullMessage.append(c);
        for (BaseComponent c : messageBody) fullMessage.append(c);

        BaseComponent[] result = fullMessage.create();
        for (Player recipient : event.getRecipients()) {
            recipient.spigot().sendMessage(result);
        }

        // Fake players may react to chat
        BotService fakeService =
                plugin.services().get(BotService.class);
        if (fakeService != null) fakeService.onRealPlayerChat(player.getName(), rawMessage);
    }

    private String buildHoverPreview(Player player, Rank rank, PlayerStateService stateService) {
        String rankDisplay = rank != null ? rank.displayPrefix() + " &7(" + rank.level().name().toLowerCase() + ")" : "&7Unknown";
        long lastSeen = stateService.getLastSeen(player.getUniqueId());
        String playTime = lastSeen > 0 ? TimeUtil.formatRelative(lastSeen) : "N/A";

        StringBuilder hover = new StringBuilder();
        hover.append(MessageUtil.colorize(Lang.get("user.hover-name", "player", player.getName())));
        hover.append("\n");
        hover.append(MessageUtil.colorize(Lang.get("user.hover-rank", "rank", rankDisplay)));
        hover.append("\n");
        hover.append(MessageUtil.colorize(Lang.get("user.hover-health",
                "health", String.format("%.0f", player.getHealth()),
                "max", String.format("%.0f", player.getMaxHealth()))));
        hover.append("\n");
        hover.append(MessageUtil.colorize(Lang.get("user.hover-world", "world", player.getWorld().getName())));
        hover.append("\n");
        hover.append(MessageUtil.colorize(Lang.get("user.hover-click")));

        return hover.toString();
    }
}
