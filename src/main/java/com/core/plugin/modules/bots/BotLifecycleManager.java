package com.core.plugin.modules.bots;

import com.core.plugin.CorePlugin;
import com.core.plugin.service.BotService;
import org.bukkit.Bukkit;

import java.util.*;

/**
 * Manages bot join/leave cycling, session scheduling, initial seeding,
 * pool rotation, and drain logic. Delegates actual broadcast/tab
 * operations to {@link BotBroadcaster}.
 */
public final class BotLifecycleManager {

    private final CorePlugin plugin;
    private final BotPool pool;
    private final BotTraits traits;
    private final BotBroadcaster broadcaster;
    private final BotDataSeeder seeder;
    private final BotChatEngine chatEngine;
    private final Set<String> onlineBots;
    private final Random random = new Random();

    private int minOnline;
    private int maxOnline;
    private int minSessionMinutes;
    private int maxSessionMinutes;
    private int cycleTaskId = -1;
    private int rotationTaskId = -1;

    public BotLifecycleManager(CorePlugin plugin, BotPool pool, BotTraits traits,
                                BotBroadcaster broadcaster, BotDataSeeder seeder,
                                BotChatEngine chatEngine, Set<String> onlineBots) {
        this.plugin = plugin;
        this.pool = pool;
        this.traits = traits;
        this.broadcaster = broadcaster;
        this.seeder = seeder;
        this.chatEngine = chatEngine;
        this.onlineBots = onlineBots;
    }

    public void configure(int minOnline, int maxOnline, int minSessionMinutes, int maxSessionMinutes) {
        this.minOnline = minOnline;
        this.maxOnline = maxOnline;
        this.minSessionMinutes = minSessionMinutes;
        this.maxSessionMinutes = maxSessionMinutes;
    }

    public int getMinOnline() { return minOnline; }
    public int getMaxOnline() { return maxOnline; }

    public void setMinOnline(int min) {
        this.minOnline = min;
        drainIfOverMax();
    }

    public void setMaxOnline(int max) {
        this.maxOnline = max;
        drainIfOverMax();
    }

    /** Seed initial bots on startup. Returns cumulative delay ticks for scheduling. */
    public long seedInitialPlayers(boolean quickRestart) {
        int initialCount = randomBetween(minOnline, Math.min(maxOnline, pool.size()));

        List<String> shuffled = pool.shuffledBySchedulePriority(
                Collections.emptyMap(), traits::isInWindow);

        long cumulativeDelay = quickRestart ? 20 : 100;
        int fastCount = quickRestart ? (int) (initialCount * 0.8) : 0;

        for (int i = 0; i < initialCount && i < shuffled.size(); i++) {
            String name = shuffled.get(i);
            long delay = cumulativeDelay;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!pool.isEnabled() || onlineBots.size() >= maxOnline) return;
                onlineBots.add(name);
                broadcaster.broadcastJoin(name, seeder, plugin.services().get(BotService.class));
            }, delay);

            cumulativeDelay += quickRestart && i < fastCount
                    ? randomBetween(20, 60)
                    : quickRestart ? randomBetween(200, 600) : randomBetween(100, 300);
        }

        return cumulativeDelay;
    }

    /** Start the periodic join/leave cycle. */
    public void startCycle(long initialDelay, int intervalSeconds) {
        if (cycleTaskId != -1) return;
        long intervalTicks = intervalSeconds * BotConfig.TICKS_PER_SECOND;
        cycleTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::cycle,
                initialDelay + intervalTicks, intervalTicks).getTaskId();
    }

    /** Start the periodic pool rotation. */
    public void startPoolRotation() {
        if (rotationTaskId != -1) return;
        long intervalTicks = randomBetween(BotConfig.ROTATION_MIN_INTERVAL, BotConfig.ROTATION_MAX_INTERVAL);
        rotationTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!pool.isEnabled()) return;
            int swapCount = randomBetween(1, 3);
            List<String> newNames = pool.rotate(onlineBots, swapCount);
            for (String name : newNames) {
                traits.assignSingle(name, chatEngine);
            }
            Set<String> poolSet = new HashSet<>(pool.getNames());
            for (String existing : new ArrayList<>(onlineBots)) {
                if (!poolSet.contains(existing)) {
                    traits.remove(existing);
                }
            }
        }, intervalTicks, intervalTicks).getTaskId();
    }

    public void stopAll() {
        cycleTaskId = cancelTask(cycleTaskId);
        rotationTaskId = cancelTask(rotationTaskId);
    }

    /** Activate: seed bots and start all schedulers. */
    public void activate(int intervalSeconds) {
        if (pool.isEmpty()) return;

        int initialCount = randomBetween(minOnline, Math.min(maxOnline, pool.size()));
        List<String> shuffled = new ArrayList<>(pool.getNames());
        Collections.shuffle(shuffled, random);

        for (int i = 0; i < initialCount && i < shuffled.size(); i++) {
            String name = shuffled.get(i);
            long delayTicks = (long) (i + 1) * randomBetween(100, 300);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!pool.isEnabled() || onlineBots.size() >= maxOnline) return;
                onlineBots.add(name);
                broadcaster.broadcastJoin(name, seeder, plugin.services().get(BotService.class));
            }, delayTicks);
        }

        long intervalTicks = intervalSeconds * BotConfig.TICKS_PER_SECOND;
        cycleTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::cycle,
                intervalTicks, intervalTicks).getTaskId();
    }

    /** Deactivate: gradually remove all bots. */
    public void deactivate() {
        stopAll();

        List<String> toRemove = new ArrayList<>(onlineBots);
        Collections.shuffle(toRemove, random);

        for (int i = 0; i < toRemove.size(); i++) {
            String name = toRemove.get(i);
            long delayTicks = (long) (i + 1) * randomBetween(60, 160);
            boolean isLast = (i == toRemove.size() - 1);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (onlineBots.remove(name)) broadcaster.broadcastLeave(name);
                if (isLast) broadcaster.clearAllFromTab();
            }, delayTicks);
        }
    }

    /** Gradually remove bots exceeding the max. */
    public void drainIfOverMax() {
        int excess = onlineBots.size() - maxOnline;
        if (excess <= 0) return;

        List<String> toRemove = new ArrayList<>(onlineBots);
        Collections.shuffle(toRemove, random);
        toRemove = toRemove.subList(0, excess);

        for (int i = 0; i < toRemove.size(); i++) {
            String name = toRemove.get(i);
            long delay = (long) (i + 1) * randomBetween(60, 200);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (onlineBots.remove(name)) broadcaster.broadcastLeave(name);
            }, delay);
        }
    }

    // --- Cycle logic ---

    private void cycle() {
        if (pool.isEmpty() || random.nextInt(BotConfig.CYCLE_SKIP_CHANCE) == 0) return;

        long inWindowCount = pool.getNames().stream()
                .filter(name -> !onlineBots.contains(name))
                .filter(traits::isInWindow)
                .count();

        int onlineCount = onlineBots.size();

        List<String> outOfWindow = onlineBots.stream()
                .filter(name -> !traits.isInWindow(name))
                .toList();

        if (!outOfWindow.isEmpty() && random.nextInt(100) < BotConfig.OUT_OF_WINDOW_LEAVE_CHANCE) {
            String leaving = outOfWindow.get(random.nextInt(outOfWindow.size()));
            onlineBots.remove(leaving);
            broadcaster.broadcastLeave(leaving);
            return;
        }

        if (decideShouldAdd(onlineCount, inWindowCount)) {
            joinRandom();
        } else {
            leaveRandom();
        }
    }

    private boolean decideShouldAdd(int onlineCount, long inWindowCount) {
        if (onlineCount <= minOnline) return true;
        if (onlineCount >= maxOnline || onlineCount >= pool.size()) return false;

        // Peak hour weighting — more likely to add during busy hours
        int hour = java.time.LocalTime.now().getHour();
        int peakWeight = BotConfig.PEAK_HOUR_WEIGHTS[hour];
        int targetCount = minOnline + (int) ((maxOnline - minOnline) * (peakWeight / 70.0));
        targetCount = Math.min(targetCount, maxOnline);

        if (onlineCount < targetCount) return random.nextInt(100) < 75;
        if (onlineCount > targetCount) return false;

        if (inWindowCount > 0) return random.nextInt(100) < BotConfig.IN_WINDOW_ADD_CHANCE;
        return random.nextInt(3) == 0;
    }

    private void joinRandom() {
        if (onlineBots.size() >= maxOnline) return;

        List<String> inWindow = pool.getNames().stream()
                .filter(name -> !onlineBots.contains(name))
                .filter(traits::isInWindow)
                .toList();

        List<String> allOffline = pool.getNames().stream()
                .filter(name -> !onlineBots.contains(name))
                .toList();

        if (allOffline.isEmpty()) return;

        String name = (!inWindow.isEmpty() && random.nextInt(100) < BotConfig.IN_WINDOW_JOIN_CHANCE)
                ? inWindow.get(random.nextInt(inWindow.size()))
                : allOffline.get(random.nextInt(allOffline.size()));

        onlineBots.add(name);
        broadcaster.broadcastJoin(name, seeder, plugin.services().get(BotService.class));
        scheduleLeave(name);
    }

    private void scheduleLeave(String name) {
        int sessionMinutes = random.nextInt(100) < BotConfig.MARATHON_CHANCE
                ? randomBetween(BotConfig.MARATHON_MIN_MINUTES, BotConfig.MARATHON_MAX_MINUTES)
                : randomBetween(minSessionMinutes, maxSessionMinutes);

        long sessionTicks = sessionMinutes * 60L * BotConfig.TICKS_PER_SECOND;
        long jitter = random.nextLong(Math.max(1, sessionTicks / 7)) - sessionTicks / 14;
        sessionTicks = Math.max(60 * BotConfig.TICKS_PER_SECOND, sessionTicks + jitter);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (onlineBots.remove(name)) broadcaster.broadcastLeave(name);
        }, sessionTicks);
    }

    private void leaveRandom() {
        if (onlineBots.isEmpty()) return;

        List<String> outOfWindow = onlineBots.stream()
                .filter(name -> !traits.isInWindow(name))
                .toList();

        String name;
        if (!outOfWindow.isEmpty() && random.nextInt(100) < BotConfig.OUT_OF_WINDOW_REMOVE_CHANCE) {
            name = outOfWindow.get(random.nextInt(outOfWindow.size()));
        } else {
            List<String> online = new ArrayList<>(onlineBots);
            name = online.get(random.nextInt(online.size()));
        }

        onlineBots.remove(name);
        broadcaster.broadcastLeave(name);
    }

    private int randomBetween(int min, int max) {
        if (min >= max) return min;
        return min + random.nextInt(max - min + 1);
    }

    private int cancelTask(int taskId) {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        return -1;
    }
}
