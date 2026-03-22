package com.core.plugin.modules.bots;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.Module;

/**
 * Brings together the fake player subsystem: name generation ({@link BotNameGenerator}),
 * pool management ({@link BotPool}), trait assignment ({@link BotTraits}),
 * and AI chat ({@link BotChatEngine}). Orchestration lives in
 * {@link com.core.plugin.service.BotService}.
 */
public final class BotsModule implements Module {

    @Override
    public void enable(CorePlugin plugin) {
    }

    @Override
    public void disable() {
    }

    @Override
    public String getName() {
        return "Bots";
    }
}
