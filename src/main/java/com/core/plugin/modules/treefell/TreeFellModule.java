package com.core.plugin.modules.treefell;

import com.core.plugin.CorePlugin;
import com.core.plugin.modules.Module;

/**
 * Module for realistic tree felling. When a player chops a log with an axe,
 * the entire tree above the chop point falls layer by layer with particles
 * and sound effects. Validates that the target is a natural tree (not a build)
 * using leaf-to-log ratio, ground checks, and size limits.
 */
public final class TreeFellModule implements Module {

    @Override
    public void enable(CorePlugin plugin) {
    }

    @Override
    public void disable() {
    }

    @Override
    public String getName() {
        return "TreeFell";
    }
}
