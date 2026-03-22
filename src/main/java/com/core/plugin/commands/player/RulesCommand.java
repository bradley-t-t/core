package com.core.plugin.commands.player;

import com.core.plugin.CorePlugin;
import com.core.plugin.command.BaseCommand;
import com.core.plugin.command.CommandContext;
import com.core.plugin.command.CommandInfo;
import com.core.plugin.modules.gui.ActiveGui;
import com.core.plugin.modules.gui.GlassPane;
import com.core.plugin.modules.gui.GuiBuilder;
import com.core.plugin.modules.gui.GuiItem;
import com.core.plugin.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Opens an interactive GUI showing the server rules organized by category.
 * The main menu shows categories; clicking a category opens a detail page.
 */
@CommandInfo(
        name = "rules",
        description = "View the server rules",
        playerOnly = true,
        icon = Material.WRITABLE_BOOK
)
public final class RulesCommand extends BaseCommand {

    public RulesCommand(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    protected void execute(CommandContext context) {
        Player player = context.playerOrError();
        if (player == null) return;

        openMainMenu(player);
        SoundUtil.openGui(player);
    }

    // ── Main Menu ──────────────────────────────────────────────────────────

    private void openMainMenu(Player player) {
        ActiveGui gui = GuiBuilder.create("&8Server Rules", 5)
                .border(GlassPane.gray())
                .item(11, categoryItem(Material.SHIELD,
                        "&c&lGriefing & Stealing",
                        "&7Rules about player property,",
                        "&7builds, and items.",
                        "",
                        "&eClick to view"))
                .item(13, categoryItem(Material.PAPER,
                        "&6&lChat & Communication",
                        "&7Rules about chat behavior,",
                        "&7profanity, and topics.",
                        "",
                        "&eClick to view"))
                .item(15, categoryItem(Material.DIAMOND_SWORD,
                        "&b&lPvP & Fair Play",
                        "&7Rules about combat, exploits,",
                        "&7and fair gameplay.",
                        "",
                        "&eClick to view"))
                .item(29, categoryItem(Material.COMMAND_BLOCK,
                        "&d&lExploits & Cheating",
                        "&7Rules about hacks, mods,",
                        "&7and exploiting bugs.",
                        "",
                        "&eClick to view"))
                .item(31, categoryItem(Material.OAK_SIGN,
                        "&a&lGeneral Conduct",
                        "&7Rules about behavior, respect,",
                        "&7and common sense.",
                        "",
                        "&eClick to view"))
                .item(33, categoryItem(Material.LAVA_BUCKET,
                        "&e&lBuilding & Environment",
                        "&7Rules about builds, the world,",
                        "&7and shared spaces.",
                        "",
                        "&eClick to view"))
                .fill(GlassPane.black())
                .build(guiListener());

        // Wire click handlers
        setClick(gui, 11, e -> openCategory(player, griefingPage()));
        setClick(gui, 13, e -> openCategory(player, chatPage()));
        setClick(gui, 15, e -> openCategory(player, pvpPage()));
        setClick(gui, 29, e -> openCategory(player, exploitsPage()));
        setClick(gui, 31, e -> openCategory(player, conductPage()));
        setClick(gui, 33, e -> openCategory(player, buildingPage()));

        guiListener().open(player, gui);
    }

    // ── Category Pages ─────────────────────────────────────────────────────

    private CategoryDef griefingPage() {
        return new CategoryDef("&8Rules: &cGriefing & Stealing", Material.SHIELD, new RuleEntry[]{
                rule(Material.GRASS_BLOCK, "&cNo Griefing",
                        "&7Do not destroy, deface, or modify",
                        "&7another player's builds or land,",
                        "&7whether claimed or unclaimed."),
                rule(Material.CHEST, "&cNo Stealing",
                        "&7Do not take items from other players'",
                        "&7chests, containers, or builds without",
                        "&7explicit permission from the owner."),
                rule(Material.TNT, "&cNo Raiding",
                        "&7Do not use TNT, lava, fire, or any",
                        "&7other methods to destroy or damage",
                        "&7another player's property."),
                rule(Material.WATER_BUCKET, "&cNo Flooding or Lava Griefing",
                        "&7Do not place water, lava, or other",
                        "&7destructive blocks near other players'",
                        "&7builds or common areas."),
                rule(Material.WHEAT, "&cNo Crop Griefing",
                        "&7Do not harvest or trample other players'",
                        "&7farms without replanting and permission."),
        });
    }

    private CategoryDef chatPage() {
        return new CategoryDef("&8Rules: &6Chat & Communication", Material.PAPER, new RuleEntry[]{
                rule(Material.PAPER, "&6No Excessive Profanity",
                        "&7Occasional swearing is fine — this is",
                        "&7the internet. But don't spam slurs,",
                        "&7be excessively vulgar, or target others."),
                rule(Material.RED_BANNER, "&6No Politics or Religion",
                        "&7Keep political and religious debates",
                        "&7off the server. This is a game, not",
                        "&7a forum. Nobody wants that here."),
                rule(Material.GOAT_HORN, "&6No Spam or Chat Flooding",
                        "&7Do not repeatedly send the same message,",
                        "&7flood chat with random characters, or",
                        "&7abuse caps lock excessively."),
                rule(Material.WITHER_SKELETON_SKULL, "&6No Harassment",
                        "&7Do not target, bully, threaten, or",
                        "&7harass other players in chat, messages,",
                        "&7or through any other means."),
                rule(Material.NAME_TAG, "&6No Advertising",
                        "&7Do not advertise other servers, websites,",
                        "&7or services in chat or private messages."),
                rule(Material.SCULK_SHRIEKER, "&6No Doxxing or Personal Info",
                        "&7Do not share or threaten to share anyone's",
                        "&7personal information. This is an instant",
                        "&7and permanent ban — zero tolerance."),
        });
    }

    private CategoryDef pvpPage() {
        return new CategoryDef("&8Rules: &bPvP & Fair Play", Material.DIAMOND_SWORD, new RuleEntry[]{
                rule(Material.DIAMOND_SWORD, "&bPvP is Allowed",
                        "&7PvP is enabled. You can fight other",
                        "&7players in the open world. Protect",
                        "&7yourself — claim your land and gear up."),
                rule(Material.IRON_DOOR, "&bNo Spawn Killing",
                        "&7Do not camp or repeatedly kill players",
                        "&7at their bed spawn or death point."),
                rule(Material.FISHING_ROD, "&bNo Combat Logging",
                        "&7Do not log out during PvP to avoid dying.",
                        "&7If you engage in a fight, see it through."),
                rule(Material.ENDER_PEARL, "&bNo Teleport Trapping",
                        "&7Do not use /msg, /tpa, or other commands",
                        "&7to lure players into traps or ambushes."),
        });
    }

    private CategoryDef exploitsPage() {
        return new CategoryDef("&8Rules: &dExploits & Cheating", Material.COMMAND_BLOCK, new RuleEntry[]{
                rule(Material.COMMAND_BLOCK, "&dNo Hacked Clients",
                        "&7Do not use hacked clients, kill aura,",
                        "&7fly hacks, x-ray, speed hacks, or any",
                        "&7form of cheating software."),
                rule(Material.SPYGLASS, "&dNo X-Ray",
                        "&7X-ray texture packs, mods, or glitches",
                        "&7are not allowed. This includes any method",
                        "&7of seeing through blocks to find ores."),
                rule(Material.REDSTONE, "&dNo Exploiting Bugs",
                        "&7If you find a bug or exploit, report it",
                        "&7to staff. Do not abuse it. Using duplication",
                        "&7glitches is a bannable offense."),
                rule(Material.HOPPER, "&dNo Lag Machines",
                        "&7Do not build redstone contraptions, entity",
                        "&7farms, or anything designed to cause lag",
                        "&7or crash the server."),
                rule(Material.PISTON, "&dAutomation Limits",
                        "&7Automatic farms are fine, but keep them",
                        "&7reasonable. If it causes TPS drops, staff",
                        "&7may ask you to downsize or disable it."),
        });
    }

    private CategoryDef conductPage() {
        return new CategoryDef("&8Rules: &aGeneral Conduct", Material.OAK_SIGN, new RuleEntry[]{
                rule(Material.OAK_SIGN, "&aUse Common Sense",
                        "&7If you think something might not be",
                        "&7allowed, it probably isn't. When in",
                        "&7doubt, ask a staff member."),
                rule(Material.PLAYER_HEAD, "&aRespect Staff Decisions",
                        "&7Staff have the final say. If you disagree",
                        "&7with a ruling, appeal it calmly — do not",
                        "&7argue in public or harass staff."),
                rule(Material.BOOKSHELF, "&aNo Impersonation",
                        "&7Do not impersonate staff members, other",
                        "&7players, or pretend to have permissions",
                        "&7or roles you don't have."),
                rule(Material.EXPERIENCE_BOTTLE, "&aNo Alt Abuse",
                        "&7Do not use alternate accounts to evade",
                        "&7bans, bypass claim limits, or gain unfair",
                        "&7advantages over other players."),
                rule(Material.CLOCK, "&aAFK Rules",
                        "&7AFK farming is allowed in moderation.",
                        "&7Do not use AFK machines to bypass the",
                        "&7AFK kick or hog mob spawners indefinitely."),
        });
    }

    private CategoryDef buildingPage() {
        return new CategoryDef("&8Rules: &eBuilding & Environment", Material.LAVA_BUCKET, new RuleEntry[]{
                rule(Material.LAVA_BUCKET, "&eNo Inappropriate Builds",
                        "&7Do not build anything offensive, vulgar,",
                        "&7or hateful. This includes pixel art, signs,",
                        "&7and map art. Keep it appropriate."),
                rule(Material.OAK_SAPLING, "&eClean Up After Yourself",
                        "&7Don't leave floating trees, 1x1 towers,",
                        "&7or random cobblestone pillars in the world.",
                        "&7If you break it, clean it up."),
                rule(Material.RAIL, "&eNo Claiming Public Areas",
                        "&7Do not claim land around server structures,",
                        "&7portals, or common paths to block access",
                        "&7for other players."),
                rule(Material.CAMPFIRE, "&eClaim Your Builds",
                        "&7Use &f/claim wand &7to protect your builds.",
                        "&7Unclaimed builds are at your own risk —",
                        "&7staff cannot restore unclaimed property."),
        });
    }

    // ── Category Detail View ───────────────────────────────────────────────

    private void openCategory(Player player, CategoryDef category) {
        int ruleCount = category.rules.length;
        int rows = Math.max(4, (ruleCount / 7) + 4); // enough rows for rules + border + back button
        rows = Math.min(6, rows);

        GuiBuilder builder = GuiBuilder.create(category.title, rows)
                .border(GlassPane.gray());

        // Place rules in the center area
        int[] centerSlots = getCenterSlots(rows, ruleCount);
        for (int i = 0; i < ruleCount && i < centerSlots.length; i++) {
            RuleEntry rule = category.rules[i];
            builder.item(centerSlots[i], GuiItem.of(rule.icon)
                    .name(rule.name)
                    .lore(rule.lore)
                    .hideFlags());
        }

        // Back button
        int backSlot = (rows - 1) * 9 + 4; // bottom center
        builder.item(backSlot, GuiItem.of(Material.ARROW)
                .name("&7Back to Categories")
                .onClick(e -> openMainMenu(player)));

        builder.fill(GlassPane.black());
        ActiveGui gui = builder.build(guiListener());

        guiListener().open(player, gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private GuiItem categoryItem(Material icon, String name, String... lore) {
        return GuiItem.of(icon).name(name).lore(lore).hideFlags().glow();
    }

    private void setClick(ActiveGui gui, int slot, java.util.function.Consumer<InventoryClickEvent> handler) {
        GuiItem existing = gui.itemAt(slot);
        if (existing != null) {
            existing.onClick(handler);
        }
    }

    private static int[] getCenterSlots(int rows, int count) {
        // Available center slots (excluding border) for different row counts
        int[] all;
        if (rows == 4) {
            all = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        } else if (rows == 5) {
            all = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        } else {
            all = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        }
        return java.util.Arrays.copyOf(all, Math.min(count, all.length));
    }

    private static RuleEntry rule(Material icon, String name, String... lore) {
        return new RuleEntry(icon, name, lore);
    }

    private record RuleEntry(Material icon, String name, String[] lore) {}
    private record CategoryDef(String title, Material icon, RuleEntry[] rules) {}
}
