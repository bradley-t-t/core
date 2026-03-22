package com.core.plugin.modules.claim;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.Module;

/**
 * Brings together the land claim subsystem: regions ({@link ClaimRegion}),
 * spatial indexing ({@link ClaimIndex}), wand selections ({@link ClaimSelection}),
 * persistence ({@link ClaimDataManager}), and visualization ({@link ClaimVisualizer}).
 * Claim logic and access control live in {@link com.core.plugin.service.ClaimService}.
 */
public final class ClaimModule implements Module {

    @Override
    public void enable(CorePlugin plugin) {
    }

    @Override
    public void disable() {
    }

    @Override
    public String getName() {
        return "Claim";
    }
}
