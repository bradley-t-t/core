package com.core.plugin.commands.warp;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.modules.gui.GuiItem;
import com.core.plugin.modules.gui.PaginatedGui;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.TeleportService;
import com.core.plugin.service.WarpService;
import com.core.plugin.util.LocationUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "warps",
        aliases = {"listwarps"},
        minRank = RankLevel.MODERATOR,
        description = "List all warps",
        usage = "/warps",
        icon = Material.END_PORTAL_FRAME
)
public final class WarpsCommand extends BaseCommand {

    public WarpsCommand(CorePlugin plugin) {
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
        var warpService = service(WarpService.class);
        Set<String> warpNames = warpService.getWarpNames();

        if (warpNames.isEmpty()) {
            Lang.send(player, "warp.no-warps");
            return;
        }

        List<GuiItem> items = new ArrayList<>();
        var teleportService = service(TeleportService.class);

        for (String name : warpNames) {
            Location location = warpService.getWarp(name);
            if (location == null) continue;

            items.add(GuiItem.of(Material.ENDER_PEARL)
                    .name(Lang.get("gui.warp-name", "name", name))
                    .lore(Lang.get("gui.warp-lore"),
                          Lang.get("gui.warp-location",
                            "world", location.getWorld().getName(),
                            "x", String.format("%.1f", location.getX()),
                            "y", String.format("%.1f", location.getY()),
                            "z", String.format("%.1f", location.getZ())))
                    .onClick(event -> {
                        event.getWhoClicked().closeInventory();
                        teleportService.teleport(player, location);
                        Lang.send(player, "warp.teleported", "name", name);
                        Lang.title(player, null, "title.teleport-subtitle", "destination", name);
                    }));
        }

        SoundUtil.openGui(player);
        new PaginatedGui(Lang.get("gui.warps-title"), items, guiListener()).open(player);
    }

    private void showTextList(CommandContext context) {
        var warpService = service(WarpService.class);
        Set<String> warpNames = warpService.getWarpNames();

        if (warpNames.isEmpty()) {
            Lang.send(context.sender(), "warp.no-warps");
            return;
        }

        Lang.send(context.sender(), "warp.list-header", "count", warpNames.size());
        for (String name : warpNames) {
            Location location = warpService.getWarp(name);
            if (location != null) {
                Lang.sendRaw(context.sender(), "warp.list-entry",
                        "name", name, "location", LocationUtil.format(location));
            }
        }
    }
}
