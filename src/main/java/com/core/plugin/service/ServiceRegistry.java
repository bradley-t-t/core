package com.core.plugin.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service locator that manages the lifecycle of all plugin services.
 * Services register by interface type and are retrieved by that type.
 */
public final class ServiceRegistry {

    private final Map<Class<? extends Service>, Service> services = new HashMap<>();

    /** Register and immediately enable a service. */
    public <T extends Service> void register(Class<T> type, T service) {
        services.put(type, service);
        service.enable();
    }

    /** Retrieve a registered service by its interface type. */
    @SuppressWarnings("unchecked")
    public <T extends Service> T get(Class<T> type) {
        return (T) services.get(type);
    }

    /** Disable all services and clear the registry. */
    public void disableAll() {
        services.values().forEach(Service::disable);
        services.clear();
    }
}
