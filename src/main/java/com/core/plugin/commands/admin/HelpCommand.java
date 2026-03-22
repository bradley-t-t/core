package com.core.plugin.commands.admin;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.gui.GuiItem;
import com.core.plugin.gui.PaginatedGui;
import com.core.plugin.lang.Lang;
import com.core.plugin.rank.Rank;
import com.core.plugin.rank.RankLevel;
import com.core.plugin.rank.RankService;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Interactive paginated help GUI showing all registered Core commands.
 * Filters by the player's rank. Moderator+ ranks see admin details like
 * the minimum rank required for each command.
 */
@CommandInfo(
        name = "help",
        aliases = {"commands", "?"},
        description = "View all available commands",
        usage = "/help",
        minRank = RankLevel.MEMBER,
        icon = Material.BOOK
)
public final class HelpCommand extends BaseCommand {

    private List<BaseCommand> registeredCommands = List.of();

    public HelpCommand(CorePlugin plugin) {
        super(plugin);
    }

    public void setRegisteredCommands(List<BaseCommand> commands) {
        this.registeredCommands = commands.stream()
                .sorted(Comparator.comparing(cmd -> cmd.info().name()))
                .toList();
    }

    @Override
    protected void execute(CommandContext context) {
        if (context.isPlayer()) {
            openGui((Player) context.sender());
        } else {
            sendTextHelp(context);
        }
    }

    private void openGui(Player player) {
        RankService rankService = service(RankService.class);
        RankLevel playerRank = rankService.getLevel(player.getUniqueId());
        boolean showAdminInfo = playerRank.isAtLeast(RankLevel.MODERATOR);

        List<GuiItem> items = new ArrayList<>();

        for (BaseCommand cmd : registeredCommands) {
            CommandInfo info = cmd.info();

            if (info.hidden() || !playerRank.isAtLeast(info.minRank())) {
                continue;
            }

            Material icon = info.icon();
            GuiItem item = GuiItem.of(icon)
                    .name("&a/" + info.name())
                    .lore(buildLore(info, showAdminInfo, rankService));

            items.add(item);
        }

        if (items.isEmpty()) {
            Lang.send(player, "help.no-commands");
            return;
        }

        new PaginatedGui(Lang.get("help.title"), items, guiListener()).open(player);
    }

    private String[] buildLore(CommandInfo info, boolean showAdminInfo, RankService rankService) {
        List<String> lore = new ArrayList<>();

        if (!info.description().isEmpty()) {
            lore.add("&7" + info.description());
        }

        lore.add("");

        if (!info.usage().isEmpty()) {
            lore.add("&fUsage: &7" + info.usage());
        }

        if (info.aliases().length > 0) {
            lore.add("&fAliases: &7" + String.join(", ", info.aliases()));
        }

        if (showAdminInfo) {
            lore.add("");
            lore.add("&8--- Staff Info ---");
            Rank rankDisplay = rankService.getDisplayConfig(info.minRank());
            lore.add("&fMin Rank: " + rankDisplay.displayPrefix());
            lore.add("&fPlayer Only: &e" + (info.playerOnly() ? "Yes" : "No"));
            if (info.minArgs() > 0) {
                lore.add("&fMin Args: &e" + info.minArgs());
            }
        }

        return lore.toArray(String[]::new);
    }

    private void sendTextHelp(CommandContext context) {
        Lang.send(context.sender(), "help.header");

        for (BaseCommand cmd : registeredCommands) {
            CommandInfo info = cmd.info();
            String entry = " &a/" + info.name() + " &7- " + info.description()
                    + " &8[" + info.minRank().name().toLowerCase() + "+]";
            Lang.sendDirect(context.sender(), entry);
        }
    }
}
