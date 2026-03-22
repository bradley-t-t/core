package com.core.plugin.util;

import com.core.plugin.modules.bots.BotNameValidator;

import java.util.UUID;

/**
 * Shared utilities for the bot system.
 * Centralizes UUID generation and other logic duplicated across multiple classes.
 */
public final class BotUtil {

    private static final String UUID_NAMESPACE = "FakePlayer:";

    /** Validator instance set by BotService on enable. */
    private static BotNameValidator validator;

    private BotUtil() {}

    /** Set the validator instance so fakeUuid can resolve real Mojang UUIDs. */
    public static void setValidator(BotNameValidator v) {
        validator = v;
    }

    /**
     * UUID for a fake player name. Returns the real Mojang UUID if the name
     * has been validated, otherwise falls back to a deterministic fake UUID.
     */
    public static UUID fakeUuid(String name) {
        if (validator != null) {
            UUID real = validator.getCachedUuid(name);
            if (real != null) return real;
        }
        return UUID.nameUUIDFromBytes((UUID_NAMESPACE + name).getBytes());
    }
}
