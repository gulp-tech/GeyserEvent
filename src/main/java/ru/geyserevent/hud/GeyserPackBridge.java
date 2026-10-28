package ru.geyserevent.hud;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;

/**
 * Регистрация ресурспака ивентов прямо в движке Bedrock через публичный Geyser API.
 *
 * <p>Зачем: папку {@code packs} движок читает один раз при старте, и если плагин
 * скопировал пак позже (или ядро держит паки в другом месте), клиент пак не получит.
 * Поэтому плагин дополнительно подписывается на события загрузки паков Geyser и
 * сам добавляет свой пак в список — тогда он гарантированно уходит Bedrock-игрокам.</p>
 *
 * <p>Всё делается через рефлексию: Geyser API нет в classpath сборки, поэтому
 * любая ошибка просто пишется в лог, а плагин продолжает работать.</p>
 */
public final class GeyserPackBridge {

    private static final String API_CLASS = "org.geysermc.geyser.api.GeyserApi";

    private GeyserPackBridge() {
    }

    /** Есть ли в JVM Geyser API (ядро со встроенным движком Bedrock или плагин Geyser). */
    public static boolean isGeyserApiPresent() {
        return classExists(API_CLASS);
    }

    /** Реальная папка packs движка Bedrock — её сообщает само Geyser API. */
    public static File geyserPacksDirectory() {
        Object api = geyserApi();
        if (api == null) {
            return null;
        }
        try {
            Object dir = api.getClass().getMethod("packDirectory").invoke(api);
            if (dir instanceof Path path) {
                return path.toFile();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object geyserApi() {
        if (!classExists(API_CLASS)) {
            return null;
        }
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
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

    /**
     * Подписывается на события загрузки паков Geyser, чтобы пак ивентов всегда
     * попадал в список отправляемых Bedrock-игрокам.
     *
     * @return true, если подписка удалась
     */
    public static synchronized boolean subscribe(JavaPlugin plugin, File packFile) {
        if (plugin == null || packFile == null || !packFile.isFile()) {
            return false;
        }
        Object api = geyserApi();
        if (api == null) {
            return false;
        }
        try {
            Object bus = api.getClass().getMethod("eventBus").invoke(api);
            if (bus == null) {
                return false;
            }

            Object registrar = null;
            try {
                Class<?> registrarClass = Class.forName("org.geysermc.geyser.api.event.EventRegistrar");
                registrar = registrarClass.getMethod("of", Object.class).invoke(null, plugin);
            } catch (Throwable ignored) {
            }

            Class<?> consumerClass = java.util.function.Consumer.class;
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if ("accept".equals(name) && args != null && args.length == 1) {
                    try {
                        handleEvent(plugin, packFile, args[0]);
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Geyser: ресурспак ивентов не зарегистрирован: " + t);
                    }
                    return null;
                }
                if ("toString".equals(name)) {
                    return "GeyserEventPackHandler";
                }
                if ("hashCode".equals(name)) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(name)) {
                    return proxy == (args != null && args.length == 1 ? args[0] : null);
                }
                return null;
            };
            Object consumer = Proxy.newProxyInstance(GeyserPackBridge.class.getClassLoader(),
                    new Class<?>[]{consumerClass}, handler);

            boolean ok = false;
            // современное событие (2.6.2+): регистрируем готовый ResourcePack
            if (classExists("org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent")) {
                ok |= subscribe(bus, registrar,
                        "org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent", consumer);
            }
            // устаревшее событие (убрано только в 2.13.0): просто добавляем путь к файлу
            if (classExists("org.geysermc.geyser.api.event.lifecycle.GeyserLoadResourcePacksEvent")) {
                ok |= subscribe(bus, registrar,
                        "org.geysermc.geyser.api.event.lifecycle.GeyserLoadResourcePacksEvent", consumer);
            }

            if (ok) {
                plugin.getLogger().info("Ресурспак ивентов передан движку Bedrock через Geyser API"
                        + " — он будет отправляться Bedrock-игрокам.");
            }
            return ok;
        } catch (Throwable t) {
            plugin.getLogger().warning("Geyser API: не удалось подписаться на загрузку паков: " + t);
            return false;
        }
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
        } catch (Throwable t) {
            return false;
        }
    }

    private static void handleEvent(JavaPlugin plugin, File packFile, Object event) throws Exception {
        if (event == null) {
            return;
        }
        String name = event.getClass().getName();
        if (name.endsWith("GeyserDefineResourcePacksEvent")) {
            Path path = packFile.toPath();
            Class<?> codecClass = Class.forName("org.geysermc.geyser.api.pack.PackCodec");
            Object codec = codecClass.getMethod("path", Path.class).invoke(null, path);
            Class<?> packClass = Class.forName("org.geysermc.geyser.api.pack.ResourcePack");
            Object pack = packClass.getMethod("create", codecClass).invoke(null, codec);

            Class<?> optionClass = Class.forName("org.geysermc.geyser.api.pack.option.ResourcePackOption");
            Method register = event.getClass().getMethod("register", packClass, optionClass.arrayType());
            Object noOptions = Array.newInstance(optionClass, 0);
            try {
                register.invoke(event, pack, noOptions);
                plugin.getLogger().info("Ресурспак ивентов зарегистрирован в движке Bedrock"
                        + " (GeyserDefineResourcePacksEvent).");
            } catch (InvocationTargetException e) {
                // DUPLICATE — пак уже был загружен из папки packs, это нормально
                plugin.getLogger().info("Ресурспак ивентов уже есть в списке паков движка Bedrock ("
                        + e.getCause() + ").");
            }
            logPackList(plugin, event);
        } else if (name.endsWith("GeyserLoadResourcePacksEvent")) {
            Object raw = event.getClass().getMethod("resourcePacks").invoke(event);
            if (raw instanceof List<?> paths) {
                Path path = packFile.toPath();
                if (!paths.contains(path)) {
                    @SuppressWarnings("unchecked")
                    List<Path> mutable = (List<Path>) paths;
                    mutable.add(path);
                    plugin.getLogger().info("Ресурспак ивентов добавлен в список паков движка Bedrock"
                            + " (GeyserLoadResourcePacksEvent).");
                }
            }
        }
    }

    /** Пишет в консоль, какие паки движок Bedrock реально отправляет игрокам. */
    private static void logPackList(JavaPlugin plugin, Object event) {
        try {
            Object raw = event.getClass().getMethod("resourcePacks").invoke(event);
            if (!(raw instanceof List<?> packs)) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (Object pack : packs) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(packName(pack));
            }
            plugin.getLogger().info("Паки, которые движок Bedrock отправляет игрокам ("
                    + packs.size() + "): " + sb);
        } catch (Throwable ignored) {
        }
    }

    private static String packName(Object pack) {
        try {
            Object manifest = pack.getClass().getMethod("manifest").invoke(pack);
            Object header = manifest.getClass().getMethod("header").invoke(manifest);
            Object title = header.getClass().getMethod("name").invoke(header);
            Object version = header.getClass().getMethod("version").invoke(header);
            return title + " " + version;
        } catch (Throwable t) {
            return String.valueOf(pack);
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
