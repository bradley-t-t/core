package com.core.plugin.util;

import java.util.UUID;

/**
 * Shared utilities for the bot system.
 * Centralizes UUID generation and other logic duplicated across multiple classes.
 */
public final class BotUtil {

    private static final String UUID_NAMESPACE = "FakePlayer:";

    private BotUtil() {}

    /** Deterministic UUID for a fake player name, consistent across tab list, ping, and service. */
    public static UUID fakeUuid(String name) {
        return UUID.nameUUIDFromBytes((UUID_NAMESPACE + name).getBytes());
    }
}
