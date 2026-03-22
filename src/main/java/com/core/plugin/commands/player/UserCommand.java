package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.modules.gui.ActiveGui;
import com.core.plugin.modules.gui.GlassPane;
import com.core.plugin.modules.gui.GuiBuilder;
import com.core.plugin.modules.gui.GuiItem;
import com.core.plugin.modules.gui.PaginatedGui;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.rank.Rank;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.service.RankService;
import com.core.plugin.service.AchievementRegistry;
import com.core.plugin.service.PlayerStateService;
import com.core.plugin.service.PlayerStatsService;
import com.core.plugin.service.PunishmentService;
import com.core.plugin.service.StatRegistry;
import com.core.plugin.stats.Achievement;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@CommandInfo(
        name = "user",
        aliases = {"whois", "playerinfo"},
        minRank = RankLevel.MEMBER,
        description = "View information about a player",
        usage = "/user <player>",
        minArgs = 1,
        icon = Material.PLAYER_HEAD
)
public final class UserCommand extends BaseCommand {

    public UserCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void execute(CommandContext context) {
        Player viewer = context.playerOrError();
        if (viewer == null) return;

        String targetName = context.arg(0);
        Player onlineTarget = Bukkit.getPlayerExact(targetName);

        boolean isBot = com.core.plugin.util.PlayerUtil.isBot(targetName);
        UUID targetId;
        OfflinePlayer offlineTarget;
        if (isBot) {
            targetId = com.core.plugin.util.BotUtil.fakeUuid(targetName);
            offlineTarget = Bukkit.getOfflinePlayer(targetId);
        } else {
            offlineTarget = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayer(targetName);
            targetId = offlineTarget.getUniqueId();
        }

        RankService rankService = service(RankService.class);
        PlayerStateService stateService = service(PlayerStateService.class);
        PlayerStatsService statsService = service(PlayerStatsService.class);

        boolean isOnline = onlineTarget != null || isBot;
        String resolvedName = onlineTarget != null ? onlineTarget.getName() : targetName;
        Rank rank = rankService.getRank(targetId);
        RankLevel viewerRank = rankService.getLevel(viewer.getUniqueId());

        // 6-row GUI: head top center, info row, stats row, mod actions bottom
        GuiBuilder builder = GuiBuilder.create("&8" + resolvedName, 6)
                .fill(GlassPane.gray());

        // === Row 0: Player head with summary ===
        builder.item(4, buildSkullItem(isBot ? null : offlineTarget, resolvedName, isOnline, rank));

        // === Row 1: Core info ===
        // Rank
        builder.item(10, GuiItem.of(Material.NAME_TAG)
                .name("&e" + rank.displayPrefix())
                .lore("&7Rank: &f" + rank.level().name().toLowerCase(),
                        "&7Weight: &f" + rank.weight()));

        // First joined
        long firstJoin = statsService.getFirstJoin(targetId);
        builder.item(12, GuiItem.of(Material.CLOCK)
                .name("&eFirst Joined")
                .lore("&f" + (firstJoin > 0 ? TimeUtil.formatRelative(firstJoin) : "Never")));

        // Last seen
        long lastSeen = stateService.getLastSeen(targetId);
        String lastSeenText = isOnline ? "&eOnline now"
                : lastSeen > 0 ? "&f" + TimeUtil.formatRelative(lastSeen) : "&7Never";
        builder.item(14, GuiItem.of(isOnline ? Material.LIME_DYE : Material.GRAY_DYE)
                .name("&eStatus")
                .lore(lastSeenText));

        // Location (real online players + mod only)
        if (onlineTarget != null && viewerRank.isAtLeast(RankLevel.MODERATOR)) {
            var loc = onlineTarget.getLocation();
            builder.item(16, GuiItem.of(Material.COMPASS)
                    .name("&eLocation")
                    .lore("&f" + loc.getWorld().getName(),
                            "&f" + String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ())));
        }

        // === Row 2: Statistics ===
        Map<String, Long> stats = statsService.getAllStats(targetId);
        long kills = stats.getOrDefault("kills", 0L);
        long deaths = stats.getOrDefault("deaths", 0L);
        String kd = deaths == 0 ? String.valueOf(kills) : String.format("%.2f", (double) kills / deaths);
        long playMins = stats.getOrDefault(StatRegistry.PLAY_TIME_KEY, 0L);

        builder.item(20, GuiItem.of(Material.DIAMOND_SWORD)
                .name("&eCombat")
                .lore("&7Kills: &f" + kills,
                        "&7Deaths: &f" + deaths,
                        "&7K/D: &f" + kd)
                .hideFlags());

        builder.item(22, GuiItem.of(Material.IRON_PICKAXE)
                .name("&eActivity")
                .lore("&7Blocks Mined: &f" + stats.getOrDefault("blocks-broken", 0L),
                        "&7Blocks Placed: &f" + stats.getOrDefault("blocks-placed", 0L),
                        "&7Mobs Killed: &f" + stats.getOrDefault("mobs-killed", 0L),
                        "&7Fish Caught: &f" + stats.getOrDefault("fish-caught", 0L))
                .hideFlags());

        builder.item(24, GuiItem.of(Material.EXPERIENCE_BOTTLE)
                .name("&ePlay Time")
                .lore("&f" + TimeUtil.formatDuration(playMins * 60_000)));

        // === Row 3: Achievements ===
        AchievementRegistry achievementRegistry = service(AchievementRegistry.class);
        Set<String> unlocked = statsService.getUnlockedAchievements(targetId);
        int totalAch = achievementRegistry.size();

        builder.item(31, GuiItem.of(Material.GOLDEN_APPLE)
                .name("&eAchievements")
                .lore("&f" + unlocked.size() + "&7/&f" + totalAch + " &7unlocked",
                        "",
                        "&7Click to view all")
                .glow()
                .onClick(event -> {
                    event.getWhoClicked().closeInventory();
                    openAchievementsGui(viewer, resolvedName, targetId, stats, unlocked);
                }));

        // === Row 4: Moderator actions ===
        if (viewerRank.isAtLeast(RankLevel.MODERATOR)) {
            // Status flags
            List<String> statusLines = buildStatusLines(targetId, stateService, isOnline);
            builder.item(38, GuiItem.of(Material.REDSTONE_TORCH)
                    .name("&eStatus Flags")
                    .lore(statusLines.toArray(String[]::new)));

            // Punish
            PunishmentService punishmentService = service(PunishmentService.class);
            builder.item(40, GuiItem.of(Material.ANVIL)
                    .name("&ePunish")
                    .lore("&7Open punishment GUI")
                    .onClick(event -> {
                        event.getWhoClicked().closeInventory();
                        var session = punishmentService.startSession(
                                viewer.getUniqueId(), targetId, resolvedName);
                        new com.core.plugin.modules.punishment.gui.PunishmentGui(
                                plugin, punishmentService, guiListener())
                                .openTypeSelection(viewer, session);
                    }));
        }

        guiListener().open(viewer, builder.build(guiListener()));
    }

    @Override
    protected List<String> complete(CommandContext context) {
        return context.argsLength() == 1 ? PlayerUtil.onlineNames(context.arg(0)) : List.of();
    }

    private void openAchievementsGui(Player viewer, String targetName, UUID targetId,
                                     Map<String, Long> stats, Set<String> unlocked) {
        AchievementRegistry achievementRegistry = service(AchievementRegistry.class);
        List<GuiItem> items = new ArrayList<>();

        for (Achievement achievement : achievementRegistry.getAll()) {
            boolean isUnlocked = unlocked.contains(achievement.key());
            long currentValue = stats.getOrDefault(achievement.statKey(), 0L);

            if (isUnlocked) {
                items.add(GuiItem.of(achievement.icon())
                        .name("&e" + achievement.displayName())
                        .lore("&7" + achievement.description(),
                                "",
                                "&7> &eUnlocked")
                        .glow());
            } else {
                items.add(GuiItem.of(Material.BARRIER)
                        .name("&7" + achievement.displayName())
                        .lore("&7" + achievement.description(),
                                "",
                                "&7Progress: &f" + currentValue + "&7/&f" + achievement.threshold()));
            }
        }

        new PaginatedGui("&8Achievements: " + targetName, items, guiListener())
                .onBack(() -> execute(new com.core.plugin.command.CommandContext(
                        viewer, "user", new String[]{targetName})))
                .open(viewer);
    }

    private GuiItem buildSkullItem(OfflinePlayer target, String name, boolean isOnline, Rank rank) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (target != null) {
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                skull.setItemMeta(meta);
            }
        }

        return GuiItem.of(skull)
                .name("&f" + name)
                .lore(isOnline ? "&eOnline" : "&7Offline",
                        "&7" + rank.displayPrefix());
    }

    private List<String> buildStatusLines(UUID targetId, PlayerStateService stateService, boolean isOnline) {
        List<String> lines = new ArrayList<>();
        if (isOnline) {
            lines.add(flagLine("God", stateService.isGod(targetId)));
            lines.add(flagLine("Vanished", stateService.isVanished(targetId)));
            lines.add(flagLine("AFK", stateService.isAfk(targetId)));
            lines.add(flagLine("Frozen", stateService.isFrozen(targetId)));
        }
        lines.add(flagLine("Muted", stateService.isMuted(targetId)));

        String nickname = stateService.getNickname(targetId);
        if (nickname != null) {
            lines.add("&7Nick: &f" + nickname);
        }
        return lines;
    }

    private String flagLine(String label, boolean active) {
        return active ? "&7> &e" + label : "&7> &7" + label;
    }
}
