package com.core.plugin.commands.claim;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.claim.ClaimRegion;
import com.core.plugin.modules.claim.ClaimSelection;
import com.core.plugin.service.ClaimService;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.modules.gui.GuiBuilder;
import com.core.plugin.modules.gui.GuiItem;
import com.core.plugin.modules.gui.GlassPane;
import com.core.plugin.modules.gui.PaginatedGui;
import com.core.plugin.modules.gui.elements.GuiElements;
import com.core.plugin.lang.Lang;
import com.core.plugin.modules.rank.RankLevel;
import com.core.plugin.util.PlayerUtil;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Primary command for the land claim system. Routes to subcommands for creating,
 * listing, viewing, inspecting, deleting claims, and obtaining the wand.
 */
@CommandInfo(
        name = "claim",
        aliases = {"claims"},
        minRank = RankLevel.MEMBER,
        playerOnly = true,
        description = "Create or manage land claims",
        usage = "/claim [create|list|view|info|delete|wand] <name>",
        icon = Material.GOLDEN_SHOVEL
)
public final class ClaimCommand extends BaseCommand {

    private static final List<String> SUBCOMMANDS = List.of("create", "list", "view", "info", "delete", "wand");

    public ClaimCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        String defaultAction = context.label().equalsIgnoreCase("claims") ? "list" : "create";
        String firstArg = context.hasArg(0) ? context.arg(0).toLowerCase() : defaultAction;

        // If first arg is a known subcommand, route to it
        if (SUBCOMMANDS.contains(firstArg)) {
            switch (firstArg) {
                case "create" -> handleCreate(player, context.hasArg(1) ? context.arg(1) : null);
                case "list" -> handleList(player);
                case "view" -> handleView(player, context.hasArg(1) ? context.arg(1) : null);
                case "info" -> handleInfo(player, context.hasArg(1) ? context.arg(1) : null);
                case "delete" -> handleDelete(player, context.hasArg(1) ? context.arg(1) : null);
                case "wand" -> handleWand(player);
            }
            return;
        }

        // If the first arg is not a subcommand, treat it as a claim name for creation
        // e.g. /claim MyBase
        if (context.hasArg(0)) {
            handleCreate(player, context.arg(0));
            return;
        }

        // Bare /claim with no args: if selection is complete, tell them to provide a name
        ClaimService claimService = service(ClaimService.class);
        ClaimSelection selection = claimService.getOrCreateSelection(player.getUniqueId());
        if (selection.isComplete()) {
            Lang.send(player, "claim.name-required");
        } else {
            Lang.send(player, "claim.incomplete");
        }
    }

    @Override
    protected List<String> complete(CommandContext context) {
        ClaimService claimService = service(ClaimService.class);
        Player player = context.isPlayer() ? (Player) context.sender() : null;

        if (context.argsLength() == 1) {
            String prefix = context.arg(0).toLowerCase();
            List<String> suggestions = new ArrayList<>(SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList());

            // Also suggest claim names for bare /claim <name>
            if (player != null) {
                claimService.getClaimNames(player.getUniqueId()).stream()
                        .filter(n -> n.toLowerCase().startsWith(prefix))
                        .forEach(suggestions::add);
            }
            return suggestions;
        }

        if (context.argsLength() == 2 && player != null) {
            String sub = context.arg(0).toLowerCase();
            // Subcommands that accept a claim name as second arg
            if (sub.equals("view") || sub.equals("info") || sub.equals("delete")) {
                String prefix = context.arg(1).toLowerCase();
                return claimService.getClaimNames(player.getUniqueId()).stream()
                        .filter(n -> n.toLowerCase().startsWith(prefix))
                        .toList();
            }
        }

        return List.of();
    }

    private void handleCreate(Player player, String name) {
        if (name == null || name.isBlank()) {
            Lang.send(player, "claim.name-required");
            return;
        }

        ClaimService claimService = service(ClaimService.class);
        ClaimSelection selection = claimService.getOrCreateSelection(player.getUniqueId());
        ClaimService.CreateResult result = claimService.createClaim(player.getUniqueId(), selection, name);

        RankLevel rank = service(com.core.plugin.service.RankService.class).getLevel(player.getUniqueId());

        switch (result) {
            case SUCCESS -> {
                ClaimRegion created = claimService.getClaimByName(player.getUniqueId(), name);
                int area = created != null ? created.area() : 0;
                Lang.send(player, "claim.created", "area", area, "name", name);
                if (created != null) claimService.visualizer().flashClaim(player, created);
                SoundUtil.success(player);
            }
            case NAME_TAKEN -> Lang.send(player, "claim.name-taken", "name", name);
            case TOO_LARGE -> Lang.send(player, "claim.too-large", "max", claimService.getMaxArea(rank));
            case TOO_SMALL -> Lang.send(player, "claim.too-small",
                    "min", plugin.getConfig().getInt("claims.min-claim-size", 5));
            case TOO_MANY_CLAIMS -> Lang.send(player, "claim.too-many", "max", claimService.getMaxClaims(rank));
            case OVERLAPS -> Lang.send(player, "claim.overlaps");
            case DIFFERENT_WORLDS -> Lang.send(player, "claim.different-worlds");
            case INCOMPLETE -> Lang.send(player, "claim.incomplete");
            case TOTAL_AREA_EXCEEDED -> Lang.send(player, "claim.total-area-exceeded",
                    "max", claimService.getMaxTotalArea(rank));
        }
    }

    private void handleList(Player player) {
        ClaimService claimService = service(ClaimService.class);
        List<ClaimRegion> claims = claimService.getPlayerClaims(player.getUniqueId());

        if (claims.isEmpty()) {
            Lang.send(player, "claim.no-claims");
            return;
        }

        List<GuiItem> items = new ArrayList<>();
        for (ClaimRegion claim : claims) {
            String ownerName = resolvePlayerName(claim.ownerId());

            items.add(GuiItem.of(Material.GREEN_WOOL)
                    .name("&e" + claim.name())
                    .lore(
                            "&7Owner: &f" + ownerName,
                            "&7Location: &f" + claim.worldName() + " (" + claim.minX() + ", " + claim.minZ() + ")",
                            "&7Size: &f" + claim.width() + "x" + claim.length() + " (" + claim.area() + " blocks)",
                            "&7Trusted: &f" + claim.trustedPlayerIds().size() + " player(s)",
                            "",
                            "&7Click to manage"
                    )
                    .onClick(event -> {
                        player.closeInventory();
                        openManageGui(player, claim);
                    })
            );
        }

        PaginatedGui gui = new PaginatedGui(Lang.get("claim.list-title"), items, guiListener());
        gui.open(player);
        SoundUtil.openGui(player);
    }

    private void openManageGui(Player player, ClaimRegion claim) {
        ClaimService claimService = service(ClaimService.class);

        GuiBuilder builder = GuiBuilder.create(Lang.get("claim.manage-title", "name", claim.name()), 3)
                .fill(GlassPane.gray());

        // Slot 10: Teleport (mod+) or Pathfind (member)
        RankLevel viewerRank = service(com.core.plugin.service.RankService.class).getLevel(player.getUniqueId());
        if (viewerRank.isAtLeast(RankLevel.MODERATOR)) {
            builder.item(10, GuiItem.of(Material.ENDER_PEARL)
                    .name("&eTeleport")
                    .lore("&7Teleport to this claim")
                    .onClick(event -> {
                        player.closeInventory();
                        var world = Bukkit.getWorld(claim.worldName());
                        if (world == null) return;
                        int centerX = (claim.minX() + claim.maxX()) / 2;
                        int centerZ = (claim.minZ() + claim.maxZ()) / 2;
                        int centerY = world.getHighestBlockYAt(centerX, centerZ) + 1;
                        player.teleport(new Location(world, centerX + 0.5, centerY, centerZ + 0.5));
                        SoundUtil.teleport(player);
                    }));
        } else {
            builder.item(10, GuiItem.of(Material.COMPASS)
                    .name("&ePathfind")
                    .lore("&7Draw a particle trail to this claim",
                            "&7Follow the blue particles")
                    .onClick(event -> {
                        player.closeInventory();
                        claimService.visualizer().pathfindToClaim(player, claim);
                        Lang.send(player, "claim.pathfind-started", "name", claim.name());
                        SoundUtil.success(player);
                    }));
        }

        // Slot 12: Manage Trusted
        builder.item(12, GuiItem.of(Material.PLAYER_HEAD)
                .name(Lang.get("claim.trusted-item"))
                .lore("&7" + claim.trustedPlayerIds().size() + " trusted player(s)",
                        "",
                        "&7Click to manage")
                .onClick(event -> {
                    player.closeInventory();
                    openTrustedGui(player, claim);
                }));

        // Slot 14: View Borders
        builder.item(14, GuiItem.of(Material.COMPASS)
                .name(Lang.get("claim.view-item"))
                .lore("&7Show claim border particles")
                .onClick(event -> {
                    player.closeInventory();
                    claimService.visualizer().flashClaim(player, claim);
                    Lang.send(player, "claim.viewing", "name", claim.name());
                    SoundUtil.success(player);
                }));

        // Slot 16: Delete Claim
        builder.item(16, GuiItem.of(Material.BARRIER)
                .name(Lang.get("claim.delete-item"))
                .lore("&7Permanently delete this claim",
                        "",
                        "&cThis cannot be undone!")
                .onClick(event -> {
                    player.closeInventory();
                    claimService.deleteClaim(player.getUniqueId(), claim.claimId());
                    claimService.visualizer().cancelAll(player.getUniqueId());
                    Lang.send(player, "claim.deleted", "name", claim.name());
                    SoundUtil.success(player);
                }));

        // Slot 22 (bottom center): Back
        builder.item(22, GuiElements.backButton(event -> {
            player.closeInventory();
            handleList(player);
        }));

        guiListener().open(player, builder.build(guiListener()));
    }

    private void openTrustedGui(Player player, ClaimRegion claim) {
        List<GuiItem> items = new ArrayList<>();

        for (UUID trustedId : claim.trustedPlayerIds()) {
            String trustedName = resolvePlayerName(trustedId);
            items.add(GuiItem.of(Material.PLAYER_HEAD)
                    .name("&e" + trustedName)
                    .lore("&7Click to remove")
                    .onClick(event -> {
                        player.closeInventory();
                        ClaimService claimService = service(ClaimService.class);
                        claimService.removeTrusted(claim.claimId(), trustedId);
                        Lang.send(player, "claim.untrusted", "player", trustedName);
                        SoundUtil.success(player);
                        openTrustedGui(player, claim);
                    })
            );
        }

        PaginatedGui gui = new PaginatedGui("&8Trusted Players", items, guiListener());
        gui.onBack(() -> {
            player.closeInventory();
            openManageGui(player, claim);
        });
        gui.open(player);
    }

    private void handleView(Player player, String claimName) {
        ClaimService claimService = service(ClaimService.class);
        ClaimRegion claim;

        if (claimName != null && !claimName.isBlank()) {
            claim = claimService.getClaimByName(player.getUniqueId(), claimName);
            if (claim == null) {
                Lang.send(player, "claim.not-found", "name", claimName);
                return;
            }
        } else {
            claim = claimService.getClaimAt(player.getLocation());
            if (claim == null) {
                Lang.send(player, "claim.not-in-claim");
                return;
            }
        }

        claimService.visualizer().flashClaim(player, claim);
        Lang.send(player, "claim.viewing", "name", claim.name());
        SoundUtil.success(player);
    }

    private void handleInfo(Player player, String claimName) {
        ClaimService claimService = service(ClaimService.class);
        ClaimRegion claim;

        if (claimName != null && !claimName.isBlank()) {
            claim = claimService.getClaimByName(player.getUniqueId(), claimName);
            if (claim == null) {
                Lang.send(player, "claim.not-found", "name", claimName);
                return;
            }
        } else {
            claim = claimService.getClaimAt(player.getLocation());
            if (claim == null) {
                Lang.send(player, "claim.not-in-claim");
                return;
            }
        }

        String ownerName = resolvePlayerName(claim.ownerId());
        String trustedNames = claim.trustedPlayerIds().isEmpty()
                ? "none"
                : String.join(", ", claim.trustedPlayerIds().stream().map(this::resolvePlayerName).toList());

        Lang.sendRaw(player, "claim.info-header");
        Lang.sendRaw(player, "claim.info-name", "name", claim.name());
        Lang.sendRaw(player, "claim.info-owner", "player", ownerName);
        Lang.sendRaw(player, "claim.info-area",
                "width", claim.width(),
                "length", claim.length(),
                "area", claim.area());
        Lang.sendRaw(player, "claim.info-trusted", "players", trustedNames);
    }

    private void handleDelete(Player player, String claimName) {
        ClaimService claimService = service(ClaimService.class);
        ClaimRegion claim;

        if (claimName != null && !claimName.isBlank()) {
            claim = claimService.getClaimByName(player.getUniqueId(), claimName);
            if (claim == null) {
                Lang.send(player, "claim.not-found", "name", claimName);
                return;
            }
        } else {
            claim = claimService.getClaimAt(player.getLocation());
            if (claim == null) {
                Lang.send(player, "claim.not-in-claim");
                return;
            }
        }

        if (!claim.ownerId().equals(player.getUniqueId())
                && !hasMinRank(player, RankLevel.OPERATOR)) {
            Lang.send(player, "claim.not-your-claim");
            return;
        }

        claimService.deleteClaim(player.getUniqueId(), claim.claimId());
        claimService.visualizer().cancelAll(player.getUniqueId());
        Lang.send(player, "claim.deleted", "name", claim.name());
        SoundUtil.success(player);
    }

    private void handleWand(Player player) {
        ItemStack wand = new ItemStack(Material.GOLDEN_SHOVEL);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Lang.get("claim.wand-name"));
            meta.setLore(List.of(
                    Lang.get("claim.wand-lore-1"),
                    Lang.get("claim.wand-lore-2")
            ));
            wand.setItemMeta(meta);
        }

        player.getInventory().addItem(wand);
        Lang.send(player, "claim.wand-given");
        SoundUtil.success(player);
    }

    private String resolvePlayerName(UUID playerId) {
        var offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        String name = offlinePlayer.getName();
        return name != null ? name : playerId.toString().substring(0, 8);
    }
}
