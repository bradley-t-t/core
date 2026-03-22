package com.core.plugin.modules;

import com.core.plugin.CorePlugin;

/**
 * Lifecycle contract for plugin modules. Modules group feature-specific logic
 * (GUIs, data models, registries) that isn't a service or listener.
 */
public interface Module {

    void enable(CorePlugin plugin);

    void disable();

    String getName();
}
