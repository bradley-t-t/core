package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.util.MessageUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@CommandInfo(
        name = "discord",
        aliases = {"dc"},
        minRank = RankLevel.MEMBER,
        description = "Get the Discord invite link",
        playerOnly = true,
        icon = Material.MUSIC_DISC_CHIRP
)
public final class DiscordCommand extends BaseCommand {

    public DiscordCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        String url = plugin.getConfig().getString("discord-url", "https://discord.gg/g5AkS6Gpm4");
        String prefix = Lang.get("prefix");
        String message = Lang.get("discord.link", "url", url);
        String hover = Lang.get("discord.hover");

        TextComponent prefixComponent = new TextComponent(
                TextComponent.fromLegacy(MessageUtil.colorize(prefix)));

        TextComponent clickable = new TextComponent(
                TextComponent.fromLegacy(MessageUtil.colorize(message)));
        clickable.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(TextComponent.fromLegacy(MessageUtil.colorize(hover)))));

        prefixComponent.addExtra(clickable);
        player.spigot().sendMessage(prefixComponent);
    }
}
