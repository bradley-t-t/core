package com.core.plugin.modules.rank;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.Module;

/**
 * Brings together rank data models ({@link Rank}, {@link RankLevel}).
 * Rank persistence and player assignment are handled by
 * {@link com.core.plugin.service.RankService}.
 */
public final class RankModule implements Module {

    @Override
    public void enable(CorePlugin plugin) {
    }

    @Override
    public void disable() {
    }

    @Override
    public String getName() {
        return "Rank";
    }
}
