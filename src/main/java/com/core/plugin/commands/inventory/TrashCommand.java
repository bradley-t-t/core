package com.core.plugin.commands.inventory;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.modules.gui.ActiveGui;
import com.core.plugin.modules.gui.GlassPane;
import com.core.plugin.modules.gui.GuiBuilder;
import com.core.plugin.lang.Lang;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.core.plugin.modules.rank.RankLevel;

@CommandInfo(
        name = "trash",
        aliases = {"disposal", "bin", "garbage"},
        minRank = RankLevel.MODERATOR,
        description = "Open a disposal inventory",
        playerOnly = true,
        icon = Material.LAVA_BUCKET
)
public final class TrashCommand extends BaseCommand {

    public TrashCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        ActiveGui gui = GuiBuilder.create(Lang.get("trash.title"), 6)
                .border(GlassPane.gray())
                .editable(true)
                .build(guiListener());

        guiListener().open(player, gui);
        SoundUtil.openGui(player);
    }
}
