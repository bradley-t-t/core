package com.core.plugin.commands.diamond;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.gui.ActiveGui;
import com.core.plugin.modules.gui.GlassPane;
import com.core.plugin.modules.gui.GuiBuilder;
import com.core.plugin.modules.gui.GuiItem;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.service.DiamondService;
import com.core.plugin.service.DiamondService.TrailType;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

@CommandInfo(
        name = "particles",
        aliases = {"trail", "trails", "cosmetics"},
        minRank = RankLevel.DIAMOND,
        description = "Choose a cosmetic particle trail",
        usage = "/particles [off]",
        playerOnly = true,
        icon = Material.BLAZE_POWDER
)
public final class ParticleCommand extends BaseCommand {

    private static final int GUI_ROWS = 4;
    private static final int[] TRAIL_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};

    public ParticleCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        DiamondService diamondService = service(DiamondService.class);

        if (context.hasArg(0) && isOffArg(context.arg(0))) {
            diamondService.clearTrail(player.getUniqueId());
            SoundUtil.toggleOff(player);
            Lang.send(player, "diamond.trail-cleared");
            return;
        }

        openTrailGui(player, diamondService);
    }

    private void openTrailGui(Player player, DiamondService diamondService) {
        TrailType currentTrail = diamondService.getTrail(player.getUniqueId());
        TrailType[] trails = TrailType.values();

        GuiBuilder builder = GuiBuilder.create("&8Particle Trails", GUI_ROWS)
                .fill(GlassPane.gray());

        for (int i = 0; i < trails.length && i < TRAIL_SLOTS.length; i++) {
            TrailType trail = trails[i];
            boolean isActive = trail == currentTrail;

            GuiItem item = GuiItem.of(trail.icon())
                    .name((isActive ? "&b> " : "&f") + trail.displayName() + (isActive ? " &7(active)" : ""))
                    .lore(trail.description(), "", isActive ? "&7Click to &cremove" : "&7Click to &bequip")
                    .onClick(event -> {
                        Player clicker = (Player) event.getWhoClicked();
                        if (trail == diamondService.getTrail(clicker.getUniqueId())) {
                            diamondService.clearTrail(clicker.getUniqueId());
                            SoundUtil.toggleOff(clicker);
                            Lang.send(clicker, "diamond.trail-cleared");
                        } else {
                            diamondService.setTrail(clicker.getUniqueId(), trail);
                            SoundUtil.toggleOn(clicker);
                            Lang.send(clicker, "diamond.trail-set", "trail", trail.displayName());
                        }
                        clicker.closeInventory();
                    });

            if (isActive) item.glow();

            builder.item(TRAIL_SLOTS[i], item);
        }

        // Disable button in bottom row
        builder.item(31, GuiItem.of(Material.BARRIER)
                .name("&cDisable Trail")
                .lore("&7Remove your current particle trail")
                .onClick(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    diamondService.clearTrail(clicker.getUniqueId());
                    SoundUtil.toggleOff(clicker);
                    Lang.send(clicker, "diamond.trail-cleared");
                    clicker.closeInventory();
                }));

        ActiveGui gui = builder.build(guiListener());
        guiListener().open(player, gui);
    }

    private boolean isOffArg(String arg) {
        return arg.equalsIgnoreCase("off")
                || arg.equalsIgnoreCase("none")
                || arg.equalsIgnoreCase("clear")
                || arg.equalsIgnoreCase("disable");
    }

    @Override
    protected List<String> complete(CommandContext context) {
        if (context.argsLength() != 1) return List.of();
        String prefix = context.arg(0).toLowerCase();
        return List.of("off").stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
