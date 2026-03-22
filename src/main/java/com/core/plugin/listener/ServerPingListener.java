package com.core.plugin.listener;

import com.core.plugin.CorePlugin;
import com.core.plugin.service.BotService;
import com.core.plugin.util.BotUtil;
import com.core.plugin.util.MessageUtil;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Overrides the server list MOTD and injects fake players into the ping response.
 * Uses Paper's extended ping event to modify the player sample (hover list)
 * and player count shown in the multiplayer menu.
 */
public final class ServerPingListener implements Listener {

    private final CorePlugin plugin;

    public ServerPingListener(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        String motd = plugin.getConfig().getString("motd", "");
        if (!motd.isEmpty()) {
            event.setMotd(MessageUtil.colorize(motd.replace("\\n", "\n")));
        }

        BotService fakeService = plugin.services().get(BotService.class);
        if (fakeService == null || !fakeService.isEnabled()) return;

        // Add fake players to the sample list (shown on hover in multiplayer menu)
        for (String fakeName : fakeService.getOnlineFakes()) {
            PlayerProfile profile = plugin.getServer().createProfile(
                    BotUtil.fakeUuid(fakeName), fakeName);
            event.getPlayerSample().add(profile);
        }

        // Override the displayed player count
        int totalOnline = fakeService.getTotalOnlineCount();
        event.setNumPlayers(totalOnline);

        // Max players stays slightly above total for a natural "filling up" look
        int maxPlayers = Math.max(event.getMaxPlayers(), totalOnline + 5 + (totalOnline / 5));
        event.setMaxPlayers(maxPlayers);
    }
}
