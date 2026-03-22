package com.core.plugin.modules.vote;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.Module;

/**
 * Brings together the vote subsystem: reward handling ({@link VoteRewardHandler}).
 * The Votifier protocol server lives in {@link com.core.plugin.service.VoteService},
 * and pending reward delivery on join in {@link com.core.plugin.listener.VoteListener}.
 */
public final class VoteModule implements Module {

    @Override
    public void enable(CorePlugin plugin) {
    }

    @Override
    public void disable() {
    }

    @Override
    public String getName() {
        return "Vote";
    }
}
