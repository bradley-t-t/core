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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * Generates a one-time 6-character code that the player enters on the
 * website account page to link their Minecraft account. The code is
 * stored in the Supabase {@code link_codes} table and expires after
 * 10 minutes.
 */
@CommandInfo(
        name = "link",
        aliases = {"linkaccount", "weblink"},
        minRank = RankLevel.MEMBER,
        description = "Link your Minecraft account to the website",
        playerOnly = true,
        icon = Material.CHAIN
)
public final class LinkCommand extends BaseCommand {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SITE_URL = "https://coreminecraft.com";

    private final HttpClient httpClient;

    public LinkCommand(CorePlugin plugin) {
        super(plugin);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        String code = generateCode();
        String uuid = player.getUniqueId().toString();
        String username = player.getName();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean stored = storeCode(code, uuid, username);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (stored) {
                    String linkUrl = SITE_URL + "/account?code=" + code;
                    String prefix = Lang.get("prefix");

                    // Line 1: code display
                    Lang.send(player, "link.code-generated", "code", code);

                    // Line 2: clickable link
                    TextComponent prefixComp = new TextComponent(
                            TextComponent.fromLegacy(MessageUtil.colorize(prefix)));

                    TextComponent clickable = new TextComponent(
                            TextComponent.fromLegacy(MessageUtil.colorize(
                                    Lang.get("link.click-to-link"))));
                    clickable.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, linkUrl));
                    clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new Text(TextComponent.fromLegacy(MessageUtil.colorize(
                                    Lang.get("link.click-hover"))))));

                    prefixComp.addExtra(clickable);
                    player.spigot().sendMessage(prefixComp);

                    // Line 3: expiry note
                    Lang.send(player, "link.expires");
                } else {
                    Lang.send(player, "link.error");
                }
            });
        });
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private boolean storeCode(String code, String playerUuid, String playerUsername) {
        String supabaseUrl = plugin.getConfig().getString("supabase-url", "");
        String supabaseKey = plugin.getConfig().getString("supabase-anon-key", "");

        if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) {
            plugin.getLogger().warning("Supabase credentials not configured — cannot store link code");
            return false;
        }

        String json = String.format(
                "{\"code\":\"%s\",\"player_uuid\":\"%s\",\"player_username\":\"%s\"}",
                code, playerUuid, playerUsername
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(supabaseUrl + "/rest/v1/link_codes"))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to store link code", e);
            return false;
        }
    }
}
