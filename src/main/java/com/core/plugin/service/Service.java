package com.core.plugin.service;

/**
 * Lifecycle contract for plugin services. Services are registered at startup
 * and torn down on disable via {@link ServiceRegistry}.
 */
public interface Service {

    /** Called when the service is registered. Load state, open resources. */
    void enable();

    /** Called on plugin shutdown. Save state, release resources. */
    void disable();
}
