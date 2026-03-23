package com.core.plugin.modules.bots;

import com.core.plugin.CorePlugin;
import com.core.plugin.lang.Lang;
import com.core.plugin.service.VoteService;
import org.bukkit.Bukkit;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates bot voting behavior. Bots vote in clusters throughout the day,
 * scaling probability based on how many still need to vote and how much
 * of the day remains.
 */
public final class BotVoteManager {

    private final CorePlugin plugin;
    private final Set<String> onlineBots;
    private final BotBroadcaster broadcaster;
    private final Random random = new Random();

    private final Map<String, Integer> dailyVoteCounts = new ConcurrentHashMap<>();
    private int voteResetDay = -1;
    private int voteLinkCount;
    private int taskId = -1;

    public BotVoteManager(CorePlugin plugin, Set<String> onlineBots, BotBroadcaster broadcaster) {
        this.plugin = plugin;
        this.onlineBots = onlineBots;
        this.broadcaster = broadcaster;
    }

    public void start(int voteLinkCount) {
        this.voteLinkCount = voteLinkCount;
        if (taskId != -1 || voteLinkCount <= 0) return;

        long intervalTicks = randomBetween(BotConfig.VOTE_MIN_INTERVAL, BotConfig.VOTE_MAX_INTERVAL);
        long firstDelay = randomBetween(BotConfig.VOTE_FIRST_MIN_DELAY, BotConfig.VOTE_FIRST_MAX_DELAY);
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tryCluster,
                firstDelay, intervalTicks).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void tryCluster() {
        if (onlineBots.isEmpty()) return;

        resetDailyIfNeeded();

        List<String> eligible = onlineBots.stream()
                .filter(name -> dailyVoteCounts.getOrDefault(name, 0) < voteLinkCount)
                .toList();

        if (eligible.isEmpty()) return;

        int hour = LocalTime.now().getHour();
        int hoursLeft = Math.max(1, 24 - hour);
        double chance = Math.min(
                (double) eligible.size() / (hoursLeft * BotConfig.VOTE_CHECKS_PER_HOUR),
                BotConfig.VOTE_MAX_CHANCE_PER_CHECK);

        if (random.nextDouble() >= chance) return;

        List<String> shuffled = new ArrayList<>(eligible);
        Collections.shuffle(shuffled, random);
        int clusterSize = Math.min(shuffled.size(),
                randomBetween(BotConfig.VOTE_CLUSTER_MIN, BotConfig.VOTE_CLUSTER_MAX));

        simulateVote(shuffled.get(0));

        for (int i = 1; i < clusterSize; i++) {
            String follower = shuffled.get(i);
            long delayTicks = randomBetween(60, 600);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (onlineBots.contains(follower)) simulateVote(follower);
            }, delayTicks);
        }
    }

    private void simulateVote(String botName) {
        dailyVoteCounts.merge(botName, 1, Integer::sum);

        String site = pickVoteSiteName();
        VoteService voteService = plugin.services().get(VoteService.class);
        if (voteService != null) {
            voteService.processVote(botName, site, false);
        }

        broadcaster.broadcastMessage(
                Lang.get("vote.received", "player", botName),
                Lang.get("vote.received", "player", "*" + botName));
    }

    private String pickVoteSiteName() {
        List<?> links = plugin.getConfig().getList("voting.vote-links");
        if (links != null && !links.isEmpty()) {
            Object entry = links.get(random.nextInt(links.size()));
            if (entry instanceof Map<?, ?> map) {
                Object name = map.get("name");
                if (name != null) return name.toString();
            }
        }
        return "VoteSite";
    }

    private void resetDailyIfNeeded() {
        int today = LocalDate.now().getDayOfYear();
        if (today != voteResetDay) {
            dailyVoteCounts.clear();
            voteResetDay = today;
        }
    }

    private int randomBetween(int min, int max) {
        if (min >= max) return min;
        return min + random.nextInt(max - min + 1);
    }
}
