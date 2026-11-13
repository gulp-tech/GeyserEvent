package ru.geyserevent.hud;

import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geyser 2.9+: кастомные сущности geyserevent:alien / geyserevent:nave
 * только для UUID ивента. Ванильный husk/ghast/поборник не подменяются.
 */
public final class GeyserEntityBridge {

    public static final String ALIEN_ID = "geyserevent:alien";
    public static final String NAVE_ID = "geyserevent:nave";

    private static final Set<UUID> ALIEN_UUIDS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> NAVE_UUIDS = ConcurrentHashMap.newKeySet();

    private static Object alienDef;
    private static Object naveDef;
    private static boolean hooked;

    private GeyserEntityBridge() {
    }

    public static void markAlien(UUID uuid) {
        if (uuid != null) {
            ALIEN_UUIDS.add(uuid);
        }
    }

    public static void markNave(UUID uuid) {
        if (uuid != null) {
            NAVE_UUIDS.add(uuid);
        }
    }

    public static void unmark(UUID uuid) {
        if (uuid != null) {
            ALIEN_UUIDS.remove(uuid);
            NAVE_UUIDS.remove(uuid);
        }
    }

    public static void clear() {
        ALIEN_UUIDS.clear();
        NAVE_UUIDS.clear();
    }

    public static synchronized void subscribe(JavaPlugin plugin) {
        if (plugin == null || hooked || !GeyserPackBridge.isGeyserApiPresent()) {
            return;
        }
        try {
            Object api = geyserApi();
            if (api == null) {
                return;
            }
            Object bus = api.getClass().getMethod("eventBus").invoke(api);
            if (bus == null) {
                return;
            }
            Object registrar = null;
            try {
                Class<?> registrarClass = Class.forName("org.geysermc.geyser.api.event.EventRegistrar");
                registrar = registrarClass.getMethod("of", Object.class).invoke(null, plugin);
            } catch (Throwable ignored) {
            }

            boolean ok = false;
            ok |= subscribe(bus, registrar, "org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent",
                    consumer(plugin, GeyserEntityBridge::onDefineEntities));
            ok |= subscribe(bus, registrar, "org.geysermc.geyser.api.event.entity.ServerSpawnEntityEvent",
                    consumer(plugin, GeyserEntityBridge::onSpawn));
            if (!ok) {
                ok |= subscribe(bus, registrar, "org.geysermc.geyser.api.event.world.ServerSpawnEntityEvent",
                        consumer(plugin, GeyserEntityBridge::onSpawn));
            }
            hooked = ok;
            if (ok) {
                plugin.getLogger().info("Geyser: кастомные сущности geyserevent:alien / nave по UUID ивента.");
            } else {
                plugin.getLogger().warning("Geyser: нет API кастомных сущностей — пришелец на Bedrock будет husk.");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Geyser entity API: " + t.getMessage());
        }
    }

    private static void onDefineEntities(Object event) {
        alienDef = registerDefinition(event, ALIEN_ID);
        naveDef = registerDefinition(event, NAVE_ID);
    }

    private static Object registerDefinition(Object event, String id) {
        try {
            Class<?> identClass = identifierClass();
            Object ident = identClass.getMethod("of", String.class).invoke(null, id);
            Class<?> defClass = Class.forName("org.geysermc.geyser.api.entity.CustomEntityDefinition");
            Object def = defClass.getMethod("of", identClass).invoke(null, ident);
            event.getClass().getMethod("register", defClass).invoke(event, def);
            return def;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void onSpawn(Object event) {
        UUID uuid = readUuid(event);
        if (uuid == null) {
            return;
        }
        Object def = null;
        if (ALIEN_UUIDS.contains(uuid)) {
            def = alienDef;
        } else if (NAVE_UUIDS.contains(uuid)) {
            def = naveDef;
        }
        if (def == null) {
            return;
        }
        try {
            for (Method m : event.getClass().getMethods()) {
                if ("definition".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(event, def);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static UUID readUuid(Object event) {
        for (String name : new String[]{"uuid", "javaUuid", "entityUuid", "getUuid"}) {
            try {
                Method m = event.getClass().getMethod(name);
                Object v = m.invoke(event);
                if (v instanceof UUID u) {
                    return u;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Object entity = event.getClass().getMethod("entity").invoke(event);
            if (entity != null) {
                Object v = entity.getClass().getMethod("uuid").invoke(entity);
                if (v instanceof UUID u) {
                    return u;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Class<?> identifierClass() throws ClassNotFoundException {
        try {
            return Class.forName("org.geysermc.geyser.api.util.Identifier");
        } catch (ClassNotFoundException ex) {
            return Class.forName("net.kyori.adventure.key.Key");
        }
    }

    private static Object geyserApi() {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            try {
                Object api = apiClass.getMethod("api").invoke(null);
                if (api != null) {
                    return api;
                }
            } catch (NoSuchMethodException ignored) {
            }
            return apiClass.getMethod("instance").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object consumer(JavaPlugin plugin, java.util.function.Consumer<Object> handler) {
        InvocationHandler ih = (proxy, method, args) -> {
            if ("accept".equals(method.getName()) && args != null && args.length == 1) {
                try {
                    handler.accept(args[0]);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Geyser entity event: " + t.getMessage());
                }
                return null;
            }
            if ("toString".equals(method.getName())) {
                return "GeyserEventEntityHandler";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == (args != null && args.length == 1 ? args[0] : null);
            }
            return null;
        };
        return Proxy.newProxyInstance(GeyserEntityBridge.class.getClassLoader(),
                new Class<?>[]{java.util.function.Consumer.class}, ih);
    }

    private static boolean subscribe(Object bus, Object registrar, String eventClassName, Object consumer) {
        try {
            Class<?> eventClass = Class.forName(eventClassName);
            Class<?> consumerClass = java.util.function.Consumer.class;
            Method method = null;
            if (registrar != null) {
                try {
                    Class<?> registrarClass = Class.forName("org.geysermc.geyser.api.event.EventRegistrar");
                    method = bus.getClass().getMethod("subscribe", registrarClass, Class.class, consumerClass);
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (method == null) {
                method = bus.getClass().getMethod("subscribe", Class.class, consumerClass);
            }
            if (method.getParameterCount() == 3) {
                method.invoke(bus, registrar, eventClass, consumer);
            } else {
                method.invoke(bus, eventClass, consumer);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
