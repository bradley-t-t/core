package com.core.plugin.listener;

import com.core.plugin.CorePlugin;
import com.core.plugin.service.BotService;
import com.core.plugin.util.BotUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.lang.reflect.*;
import java.util.*;

/**
 * Injects fake player entries into the tab list using direct NMS packet construction.
 * Builds ClientboundPlayerInfoUpdatePacket manually without creating ServerPlayer instances.
 */
public final class BotTabListener implements Listener {

    private final CorePlugin plugin;
    private final Set<UUID> injectedUuids = new HashSet<>();
    private final Random random = new Random();
    private final NmsReflection nms = new NmsReflection();

    public BotTabListener(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sendAllFakesToPlayer(event.getPlayer());
            refreshAll();
        }, 10L);
    }

    public void addFake(String name, String displayName) {
        UUID uuid = BotUtil.fakeUuid(name);
        injectedUuids.add(uuid);
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendAdd(player, uuid, name, displayName);
        }
    }

    public void removeFake(String name) {
        UUID uuid = BotUtil.fakeUuid(name);
        injectedUuids.remove(uuid);
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendRemove(player, List.of(uuid));
        }
    }

    public void removeAllFakes() {
        if (injectedUuids.isEmpty()) return;
        List<UUID> uuids = List.copyOf(injectedUuids);
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendRemove(player, uuids);
        }
        injectedUuids.clear();
    }

    public void refreshAll() {
        BotService botService = plugin.services().get(BotService.class);
        if (botService == null || !botService.isEnabled()) return;

        int totalOnline = botService.getTotalOnlineCount();
        Component header = Component.text("Core Survival", NamedTextColor.YELLOW);
        Component footer = Component.text("Online: ", NamedTextColor.GRAY)
                .append(Component.text(totalOnline, NamedTextColor.WHITE));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeaderAndFooter(header, footer);
        }
    }

    public void clearAll() {
        removeAllFakes();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
    }

    private void sendAllFakesToPlayer(Player player) {
        BotService botService = plugin.services().get(BotService.class);
        if (botService == null || !botService.isEnabled() || !player.isOnline()) return;

        for (String fakeName : botService.getOnlineFakes()) {
            UUID uuid = BotUtil.fakeUuid(fakeName);
            injectedUuids.add(uuid);
            sendAdd(player, uuid, fakeName, botService.getDisplayName(fakeName));
        }
    }

    // --- NMS Packet Construction ---

    @SuppressWarnings("unchecked")
    private void sendAdd(Player player, UUID uuid, String name, String displayName) {
        nms.resolve(player);
        if (!nms.available || !player.isOnline()) return;

        try {
            Object conn = nms.getConnection(player);
            Object profile = nms.gameProfileConstructor.newInstance(uuid, name);
            Object nmsDisplayName = resolveDisplayComponent(displayName);

            Object entry = nms.createEntry(uuid, profile, nmsDisplayName, random.nextInt(100) + 20);
            if (entry == null) {
                plugin.getLogger().warning("Tab entry: no 9-param Entry(UUID,...) constructor found.");
                return;
            }

            Object packet = nms.createUpdatePacket(entry);
            if (packet == null) {
                plugin.getLogger().warning("No matching packet constructor found.");
                return;
            }

            nms.sendMethod.invoke(conn, packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Tab add error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void sendRemove(Player player, List<UUID> uuids) {
        nms.resolve(player);
        if (!nms.available || !player.isOnline()) return;

        try {
            Object conn = nms.getConnection(player);
            Object packet = nms.removePacketConstructor.newInstance(uuids);
            nms.sendMethod.invoke(conn, packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Tab remove error: " + e.getMessage());
        }
    }

    private Object resolveDisplayComponent(String displayName) {
        if (displayName == null) return null;
        try {
            Class<?> craftChatMessage = Class.forName("org.bukkit.craftbukkit.util.CraftChatMessage");
            Method fromStringOrNull = craftChatMessage.getMethod("fromStringOrNull", String.class);
            return fromStringOrNull.invoke(null, displayName);
        } catch (Exception e) {
            plugin.getLogger().warning("Tab display name failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Caches all NMS reflection lookups needed for tab list packet construction.
     * Resolved lazily on first use to avoid reflection at construction time.
     */
    private class NmsReflection {
        boolean resolved;
        boolean available;

        Method getHandleMethod;
        Field connectionField;
        Method sendMethod;
        Constructor<?> gameProfileConstructor;
        Constructor<?> removePacketConstructor;
        Class<?> updatePacketClass;
        Class<?> entryRecordClass;
        Object addPlayerAction;
        Object updateListedAction;
        Object updateLatencyAction;
        Object updateDisplayNameAction;
        Object updateGameModeAction;
        Object survivalGameType;

        void resolve(Player sample) {
            if (resolved) return;
            resolved = true;

            try {
                getHandleMethod = sample.getClass().getMethod("getHandle");
                Object nmsPlayer = getHandleMethod.invoke(sample);

                connectionField = findConnectionField(nmsPlayer);
                connectionField.setAccessible(true);

                Object conn = connectionField.get(nmsPlayer);
                sendMethod = findSendMethod(conn);

                gameProfileConstructor = Class.forName("com.mojang.authlib.GameProfile")
                        .getConstructor(UUID.class, String.class);

                removePacketConstructor = Class.forName(
                        "net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket")
                        .getConstructor(List.class);

                updatePacketClass = Class.forName(
                        "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");

                resolveInnerClasses();
                resolveSurvivalGameType();

                available = sendMethod != null && entryRecordClass != null
                        && addPlayerAction != null && updateListedAction != null;

                if (available) {
                    plugin.getLogger().info("Tab list injection: NMS reflection resolved successfully.");
                } else {
                    plugin.getLogger().warning("Tab list injection: missing components.");
                }
            } catch (Exception e) {
                available = false;
                plugin.getLogger().warning("Tab list injection failed: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        Object getConnection(Player player) throws Exception {
            Object nmsPlayer = getHandleMethod.invoke(player);
            return connectionField.get(nmsPlayer);
        }

        Object createEntry(UUID uuid, Object profile, Object nmsDisplayName, int latency) throws Exception {
            for (Constructor<?> c : entryRecordClass.getDeclaredConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 9 && params[0] == UUID.class) {
                    c.setAccessible(true);
                    return c.newInstance(uuid, profile, true, latency,
                            survivalGameType, nmsDisplayName, false, 0, null);
                }
            }
            return null;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        Object createUpdatePacket(Object entry) throws Exception {
            EnumSet actions = EnumSet.of((Enum) addPlayerAction, (Enum) updateListedAction);
            if (updateLatencyAction != null) actions.add((Enum) updateLatencyAction);
            if (updateDisplayNameAction != null) actions.add((Enum) updateDisplayNameAction);
            if (updateGameModeAction != null) actions.add((Enum) updateGameModeAction);

            for (Constructor<?> c : updatePacketClass.getDeclaredConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 2 && params[0] == EnumSet.class) {
                    c.setAccessible(true);
                    return c.newInstance(actions, List.of(entry));
                }
            }
            return null;
        }

        private Field findConnectionField(Object nmsPlayer) throws Exception {
            for (Field f : nmsPlayer.getClass().getFields()) {
                if (f.getType().getSimpleName().contains("ServerGamePacketListener")
                        || f.getType().getSimpleName().contains("ServerCommonPacketListener")
                        || f.getName().equals("connection")) {
                    return f;
                }
            }
            for (Field f : nmsPlayer.getClass().getSuperclass().getFields()) {
                if (f.getName().equals("connection")) return f;
            }
            throw new NoSuchFieldException("connection");
        }

        private Method findSendMethod(Object conn) {
            for (Method m : conn.getClass().getMethods()) {
                if ("send".equals(m.getName()) && m.getParameterCount() == 1) return m;
            }
            return null;
        }

        private void resolveInnerClasses() {
            Class<?> actionEnum = null;
            for (Class<?> inner : updatePacketClass.getDeclaredClasses()) {
                if (inner.getSimpleName().equals("Entry")) entryRecordClass = inner;
                if (inner.isEnum() && inner.getSimpleName().equals("Action")) actionEnum = inner;
            }
            if (actionEnum == null) return;
            for (Object constant : actionEnum.getEnumConstants()) {
                switch (((Enum<?>) constant).name()) {
                    case "ADD_PLAYER" -> addPlayerAction = constant;
                    case "UPDATE_LISTED" -> updateListedAction = constant;
                    case "UPDATE_LATENCY" -> updateLatencyAction = constant;
                    case "UPDATE_DISPLAY_NAME" -> updateDisplayNameAction = constant;
                    case "UPDATE_GAME_MODE" -> updateGameModeAction = constant;
                }
            }
        }

        private void resolveSurvivalGameType() throws ClassNotFoundException {
            Class<?> gameTypeClass = Class.forName("net.minecraft.world.level.GameType");
            for (Object gt : gameTypeClass.getEnumConstants()) {
                if ("SURVIVAL".equals(((Enum<?>) gt).name())) {
                    survivalGameType = gt;
                    return;
                }
            }
        }
    }
}
