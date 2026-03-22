package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.util.MessageUtil;
import com.core.plugin.service.VoteService;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

@CommandInfo(
        name = "vote",
        minRank = RankLevel.MEMBER,
        description = "Vote for the server",
        usage = "/vote",
        playerOnly = true,
        icon = Material.EMERALD
)
public final class VoteCommand extends BaseCommand {

    public VoteCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        VoteService voteService = service(VoteService.class);
        int voteCount = voteService != null ? voteService.getVoteCount(player.getUniqueId()) : 0;
        Lang.send(player, "vote.count", "count", voteCount);

        // Try new multi-link format first
        List<?> links = plugin.getConfig().getList("voting.vote-links");
        if (links != null && !links.isEmpty()) {
            Lang.sendRaw(player, "vote.header");

            for (Object entry : links) {
                if (!(entry instanceof Map<?, ?> map)) continue;
                Object nameObj = map.get("name");
                Object urlObj = map.get("url");
                String name = nameObj != null ? nameObj.toString() : "Vote";
                String url = urlObj != null ? urlObj.toString() : "";
                if (url.isEmpty()) continue;

                String linkText = Lang.get("vote.link", "name", name, "url", url);
                String hoverText = Lang.get("vote.link-hover", "name", name);

                TextComponent clickable = new TextComponent(
                        TextComponent.fromLegacy(MessageUtil.colorize(linkText)));
                clickable.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new Text(TextComponent.fromLegacy(MessageUtil.colorize(hoverText)))));

                player.spigot().sendMessage(clickable);
            }
            return;
        }

        // Fallback: legacy single vote-url
        String voteUrl = plugin.getConfig().getString("voting.vote-url", "");
        if (voteUrl.isEmpty()) return;

        String urlMessage = Lang.get("vote.url", "url", voteUrl);
        String hoverText = Lang.get("vote.url-click");

        TextComponent clickable = new TextComponent(
                TextComponent.fromLegacy(MessageUtil.colorize(urlMessage)));
        clickable.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, voteUrl));
        clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(TextComponent.fromLegacy(MessageUtil.colorize(hoverText)))));

        player.spigot().sendMessage(clickable);
    }
}
