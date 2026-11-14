package ru.geyserevent.hud;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Мост кастомных сущностей Geyser. Ивент пришельцев удалён — хуки не регистрируем,
 * ванильные husk/ghast/поборник не подменяются.
 */
public final class GeyserEntityBridge {

    private GeyserEntityBridge() {
    }

    public static void subscribe(JavaPlugin plugin) {
        // no-op: пришелец и НЛО убраны
    }
}
