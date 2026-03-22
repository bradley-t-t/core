package com.core.plugin.modules.bots;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Validates bot names against the Mojang API and caches their real UUIDs.
 * Names that don't correspond to real Minecraft accounts are flagged for replacement.
 * Operates asynchronously to avoid blocking the main thread.
 */
public final class BotNameValidator {

    private static final String MOJANG_API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    /** Mojang rate limit: ~600 requests per 10 minutes. Stay well under. */
    private static final long DELAY_BETWEEN_REQUESTS_MS = 1200;

    private final Plugin plugin;
    private final Logger logger;

    /** Cache of validated name -> Mojang UUID. */
    private final Map<String, UUID> uuidCache = new ConcurrentHashMap<>();
    /** Names confirmed invalid (no Mojang account). */
    private final Set<String> invalidNames = ConcurrentHashMap.newKeySet();

    public BotNameValidator(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Returns the cached Mojang UUID for a name, or null if not yet validated.
     */
    public UUID getCachedUuid(String name) {
        return uuidCache.get(name);
    }

    /**
     * Returns true if this name has been checked and found to be invalid.
     */
    public boolean isInvalid(String name) {
        return invalidNames.contains(name);
    }

    /**
     * Returns all names that have been confirmed invalid.
     */
    public Set<String> getInvalidNames() {
        return Set.copyOf(invalidNames);
    }

    /**
     * Validates a list of names against the Mojang API asynchronously.
     * Results are cached. Calls the callback on the main thread when done.
     *
     * @param names    names to validate
     * @param callback called on main thread with the set of invalid names
     */
    public void validateAsync(List<String> names, java.util.function.Consumer<Set<String>> callback) {
        // Filter out already-validated names
        List<String> toCheck = new ArrayList<>();
        for (String name : names) {
            if (!uuidCache.containsKey(name) && !invalidNames.contains(name)) {
                toCheck.add(name);
            }
        }

        if (toCheck.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(Set.copyOf(invalidNames)));
            return;
        }

        logger.info("Validating " + toCheck.size() + " bot names against Mojang API...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int valid = 0;
            int invalid = 0;

            for (String name : toCheck) {
                try {
                    UUID uuid = lookupMojangUuid(name);
                    if (uuid != null) {
                        uuidCache.put(name, uuid);
                        valid++;
                    } else {
                        invalidNames.add(name);
                        invalid++;
                    }
                    Thread.sleep(DELAY_BETWEEN_REQUESTS_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // On error (rate limit, network), skip — don't mark as invalid
                    logger.warning("Mojang API error for '" + name + "': " + e.getMessage());
                }
            }

            int finalValid = valid;
            int finalInvalid = invalid;
            Bukkit.getScheduler().runTask(plugin, () -> {
                logger.info("Bot name validation complete: " + finalValid + " valid, "
                        + finalInvalid + " invalid.");
                callback.accept(Set.copyOf(invalidNames));
            });
        });
    }

    /**
     * Looks up a single username against the Mojang API.
     * Returns the UUID if the account exists, null otherwise.
     */
    private UUID lookupMojangUuid(String name) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(MOJANG_API + name)
                .toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);

        int code = conn.getResponseCode();
        if (code == 204 || code == 404) {
            return null; // No such account
        }
        if (code == 429) {
            throw new IOException("Rate limited");
        }
        if (code != 200) {
            throw new IOException("HTTP " + code);
        }

        String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return extractUuid(body);
    }

    /**
     * Extracts the UUID from a Mojang API JSON response.
     * Response format: {"id":"hexstring","name":"..."}
     */
    private UUID extractUuid(String json) {
        int idIdx = json.indexOf("\"id\"");
        if (idIdx == -1) return null;

        int colonIdx = json.indexOf(":", idIdx);
        if (colonIdx == -1) return null;

        int start = json.indexOf("\"", colonIdx + 1);
        if (start == -1) return null;
        start++;

        int end = json.indexOf("\"", start);
        if (end == -1) return null;

        String hex = json.substring(start, end);
        if (hex.length() != 32) return null;

        // Insert dashes: 8-4-4-4-12
        String formatted = hex.substring(0, 8) + "-"
                + hex.substring(8, 12) + "-"
                + hex.substring(12, 16) + "-"
                + hex.substring(16, 20) + "-"
                + hex.substring(20);

        try {
            return UUID.fromString(formatted);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
