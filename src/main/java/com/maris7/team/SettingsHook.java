package com.maris7.team;

import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class SettingsHook {
    private final JavaPlugin plugin;
    private volatile boolean unavailable;
    private Object apiInstance;
    private Method isEnabledMethod;
    private Method setMethod;

    public SettingsHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        api();
    }

    public boolean isAvailable() {
        return api() != null;
    }

    public boolean isEnabled(UUID uuid, String feature, boolean defaultValue) {
        Object api = api();
        if (api == null) {
            return defaultValue;
        }
        try {
            if (isEnabledMethod == null) {
                isEnabledMethod = api.getClass().getMethod("isEnabled", UUID.class, String.class, boolean.class);
            }
            Object result = isEnabledMethod.invoke(api, uuid, feature, defaultValue);
            return result instanceof Boolean value ? value : defaultValue;
        } catch (Throwable ignored) {
            clear();
            return defaultValue;
        }
    }

    public Boolean set(UUID uuid, String feature, boolean enabled) {
        Object api = api();
        if (api == null) {
            return null;
        }
        try {
            if (setMethod == null) {
                setMethod = api.getClass().getMethod("set", UUID.class, String.class, boolean.class);
            }
            Object result = setMethod.invoke(api, uuid, feature, enabled);
            return result instanceof Boolean value ? value : enabled;
        } catch (Throwable ignored) {
            clear();
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object api() {
        if (apiInstance != null) {
            return apiInstance;
        }
        if (unavailable) {
            return null;
        }
        try {
            if (!plugin.getServer().getPluginManager().isPluginEnabled("MarisSettings")) {
                unavailable = true;
                return null;
            }
            Class<?> apiClass = Class.forName("com.maris7.settings.api.MarisSettingsApi");
            Object api = plugin.getServer().getServicesManager().load((Class) apiClass);
            if (api == null) {
                unavailable = true;
                return null;
            }
            apiInstance = api;
            return api;
        } catch (Throwable ignored) {
            clear();
            unavailable = true;
            return null;
        }
    }

    private void clear() {
        apiInstance = null;
        isEnabledMethod = null;
        setMethod = null;
    }
}