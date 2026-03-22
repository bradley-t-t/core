package com.core.plugin.commands.home;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.modules.gui.GuiItem;
import com.core.plugin.modules.gui.PaginatedGui;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.HomeService;
import com.core.plugin.service.TeleportService;
import com.core.plugin.util.LocationUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "homes",
        aliases = {"listhomes"},
        minRank = RankLevel.OPERATOR,
        description = "List all your homes",
        usage = "/homes",
        icon = Material.RED_BED
)
public final class HomesCommand extends BaseCommand {

    public HomesCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        if (context.isPlayer()) {
            openGui((Player) context.sender());
        } else {
            showTextList(context);
        }
    }

    private void openGui(Player player) {
        Map<String, Location> homes = service(HomeService.class).getHomes(player.getUniqueId());

        if (homes.isEmpty()) {
            Lang.send(player, "home.no-homes");
            return;
        }

        List<GuiItem> items = new ArrayList<>();
        var teleportService = service(TeleportService.class);

        homes.forEach((name, location) -> items.add(
                GuiItem.of(Material.RED_BED)
                        .name(Lang.get("gui.home-name", "name", name))
                        .lore(Lang.get("gui.home-lore"),
                              Lang.get("gui.home-location",
                                "world", location.getWorld().getName(),
                                "x", String.format("%.1f", location.getX()),
                                "y", String.format("%.1f", location.getY()),
                                "z", String.format("%.1f", location.getZ())))
                        .onClick(event -> {
                            event.getWhoClicked().closeInventory();
                            teleportService.teleport(player, location);
                            Lang.send(player, "home.teleported", "name", name);
                            Lang.title(player, null, "title.teleport-subtitle", "destination", name);
                        })
        ));

        SoundUtil.openGui(player);
        new PaginatedGui(Lang.get("gui.homes-title"), items, guiListener()).open(player);
    }

    private void showTextList(CommandContext context) {
        Map<String, Location> homes;

        if (context.isPlayer()) {
            homes = service(HomeService.class).getHomes(((Player) context.sender()).getUniqueId());
        } else {
            Lang.send(context.sender(), "generic.player-only");
            return;
        }

        if (homes.isEmpty()) {
            Lang.send(context.sender(), "home.no-homes");
            return;
        }

        Lang.send(context.sender(), "home.list-header", "count", homes.size());
        homes.forEach((name, location) ->
                Lang.sendRaw(context.sender(), "home.list-entry",
                        "name", name, "location", LocationUtil.format(location)));
    }
}
