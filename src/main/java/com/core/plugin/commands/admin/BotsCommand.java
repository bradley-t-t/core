package com.core.plugin.commands.admin;

import com.core.plugin.CorePlugin;
import com.core.plugin.service.BotService;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.modules.gui.GuiItem;
import com.core.plugin.modules.gui.PaginatedGui;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.rank.RankLevel;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Admin command for managing the fake player pool and viewing status.
 * /fakeplayers -- opens management GUI
 * /fakeplayers on|off -- toggle system
 * /fakeplayers add|remove <name> -- manage pool
 * /fakeplayers status -- show current state
 */
@CommandInfo(
        name = "fakeplayers",
        aliases = {"fp"},
        minRank = RankLevel.OPERATOR,
        description = "Manage fake player system",
        usage = "/fakeplayers [add|remove <name>]",
        icon = Material.PLAYER_HEAD,
        hidden = true
)
public final class BotsCommand extends BaseCommand {

    public BotsCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        BotService botService = service(BotService.class);

        if (context.hasArg(0)) {
            String action = context.arg(0).toLowerCase();

            switch (action) {
                case "on", "enable" -> {
                    if (botService.isEnabled()) {
                        Lang.send(context.sender(), "fakeplayers.already-on");
                    } else {
                        botService.activate();
                        Lang.send(context.sender(), "fakeplayers.enabled");
                    }
                }
                case "off", "disable" -> {
                    if (!botService.isEnabled()) {
                        Lang.send(context.sender(), "fakeplayers.already-off");
                    } else {
                        botService.deactivate();
                        Lang.send(context.sender(), "fakeplayers.disabled");
                    }
                }
                case "add" -> {
                    if (!context.hasArg(1)) break;
                    String name = context.arg(1);
                    botService.addToPool(name);
                    Lang.send(context.sender(), "fakeplayers.added", "player", name);
                }
                case "remove" -> {
                    if (!context.hasArg(1)) break;
                    String name = context.arg(1);
                    botService.removeFromPool(name);
                    Lang.send(context.sender(), "fakeplayers.removed", "player", name);
                }
                case "min" -> {
                    if (!context.hasArg(1)) {
                        Lang.send(context.sender(), "fakeplayers.range",
                                "min", botService.getMinOnline(), "max", botService.getMaxOnline());
                        return;
                    }
                    int min = context.argInt(1, -1);
                    if (min < 0) { Lang.send(context.sender(), "generic.invalid-number"); return; }
                    botService.setMinOnline(min);
                    Lang.send(context.sender(), "fakeplayers.min-set", "value", min);
                }
                case "max" -> {
                    if (!context.hasArg(1)) {
                        Lang.send(context.sender(), "fakeplayers.range",
                                "min", botService.getMinOnline(), "max", botService.getMaxOnline());
                        return;
                    }
                    int max = context.argInt(1, -1);
                    if (max < 0) { Lang.send(context.sender(), "generic.invalid-number"); return; }
                    botService.setMaxOnline(max);
                    Lang.send(context.sender(), "fakeplayers.max-set", "value", max);
                }
                case "range" -> {
                    if (!context.hasArg(2)) {
                        Lang.send(context.sender(), "fakeplayers.range",
                                "min", botService.getMinOnline(), "max", botService.getMaxOnline());
                        return;
                    }
                    int min = context.argInt(1, -1);
                    int max = context.argInt(2, -1);
                    if (min < 0 || max < 0 || min > max) {
                        Lang.send(context.sender(), "generic.invalid-number");
                        return;
                    }
                    botService.setMinOnline(min);
                    botService.setMaxOnline(max);
                    Lang.send(context.sender(), "fakeplayers.range-set", "min", min, "max", max);
                }
                case "status" -> Lang.send(context.sender(), "fakeplayers.status",
                        "state", botService.isEnabled() ? "enabled" : "disabled",
                        "online", botService.getOnlineFakes().size(),
                        "pool", botService.getPlayerPool().size(),
                        "min", botService.getMinOnline(),
                        "max", botService.getMaxOnline());
                default -> openGuiIfPlayer(context, botService);
            }

            // Only fall through to GUI for known commands that consumed the arg
            if (Set.of("on", "enable", "off", "disable", "add", "remove", "status", "min", "max", "range").contains(action)) {
                return;
            }
        }

        openGuiIfPlayer(context, botService);
    }

    private void openGuiIfPlayer(CommandContext context, BotService botService) {
        Player player = context.playerOrError();
        if (player == null) return;
        openGui(player, botService);
    }

    private void openGui(Player viewer, BotService botService) {
        List<String> pool = botService.getPlayerPool();
        Set<String> online = botService.getOnlineFakes();

        List<GuiItem> items = new ArrayList<>();

        for (String name : pool) {
            boolean isOnline = online.contains(name);

            GuiItem item = GuiItem.of(isOnline ? Material.LIME_WOOL : Material.GRAY_WOOL)
                    .name(Lang.get("fakeplayers.gui-name", "player", name))
                    .lore(
                            isOnline
                                    ? Lang.get("fakeplayers.gui-online")
                                    : Lang.get("fakeplayers.gui-offline"),
                            Lang.get("fakeplayers.gui-click-remove")
                    )
                    .onClick(event -> {
                        botService.removeFromPool(name);
                        Lang.send(viewer, "fakeplayers.removed", "player", name);
                        event.getWhoClicked().closeInventory();
                        openGui(viewer, botService);
                    });

            if (isOnline) item.glow();
            items.add(item);
        }

        new PaginatedGui(
                Lang.get("fakeplayers.gui-title"), items, guiListener()
        ).open(viewer);
    }

    @Override
    protected List<String> complete(CommandContext context) {
        if (context.argsLength() == 1) {
            return List.of("on", "off", "status", "add", "remove", "min", "max", "range");
        }
        if (context.argsLength() == 2 && "remove".equalsIgnoreCase(context.arg(0))) {
            BotService botService = service(BotService.class);
            String prefix = context.arg(1).toLowerCase();
            return botService.getPlayerPool().stream()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
