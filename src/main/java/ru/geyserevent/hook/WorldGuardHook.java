package ru.geyserevent.hook;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Мягкая (опциональная) интеграция с WorldGuard через рефлексию.
 *
 * <p>Задача: сделать так, чтобы регионы WorldGuard <b>не мешали</b> ломать руды
 * и Древние жеоды постоянного ивента «Шахта Древних (0 ~ 0)», а также открывать
 * меню Древнего Кузнеца. Для этого вокруг шахты создаётся (или обновляется)
 * собственный регион {@code geyserevent_mine} с высоким приоритетом и флагами
 * {@code build: allow} / {@code interact: allow}.</p>
 *
 * <p>WorldGuard может быть не установлен вообще — тогда все методы безопасно
 * возвращают {@code false} и плагин работает без него.</p>
 */
public final class WorldGuardHook {

    public static final String REGION_ID = "geyserevent_mine";

    private final Object worldGuardPlugin;
    private final boolean available;

    public WorldGuardHook() {
        this.worldGuardPlugin = resolveWorldGuardPlugin();
        this.available = this.worldGuardPlugin != null;
    }

    private static Object resolveWorldGuardPlugin() {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
            if (plugin == null || !plugin.isEnabled()) {
                return null;
            }
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            return wgClass.isInstance(plugin) ? wgClass.cast(plugin) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Создаёт/обновляет регион {@value #REGION_ID}, покрывающий шахту.
     *
     * @param world   мир шахты
     * @param centerX X центра
     * @param centerY Y центра
     * @param centerZ Z центра
     * @param radius  горизонтальный радиус региона (в блоках)
     * @param minY    минимальная высота региона
     * @param maxY    максимальная высота региона
     * @return true, если регион успешно применён
     */
    public boolean syncMineRegion(World world, int centerX, int centerY, int centerZ,
                                  int radius, int minY, int maxY) {
        if (!available || world == null) {
            return false;
        }
        try {
            Object container = worldGuardPlugin.getClass().getMethod("getRegionContainer").invoke(worldGuardPlugin);
            if (container == null) {
                return false;
            }

            // WorldEdit-мир, необходимый для RegionContainer.get(...)
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object weWorld = adapterClass.getMethod("adapt", World.class).invoke(null, world);
            Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");
            Object manager = container.getClass().getMethod("get", weWorldClass).invoke(container, weWorld);
            if (manager == null) {
                return false;
            }
            Class<?> managerClass = manager.getClass();

            Class<?> blockVectorClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Method at = blockVectorClass.getMethod("at", int.class, int.class, int.class);
            Object min = at.invoke(null, centerX - radius, minY, centerZ - radius);
            Object max = at.invoke(null, centerX + radius, maxY, centerZ + radius);

            Class<?> cuboidClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion");
            Constructor<?> ctor = cuboidClass.getConstructor(String.class, blockVectorClass, blockVectorClass);
            Object region = ctor.newInstance(REGION_ID, min, max);

            // Высокий приоритет: наш регион важнее регионов спавна/протекта сервера
            cuboidClass.getMethod("setPriority", int.class).invoke(region, 100);

            Class<?> flagClass = Class.forName("com.sk89q.worldguard.protection.flags.Flag");
            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Class<?> stateClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag$State");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object allow = Enum.valueOf((Class) stateClass.asSubclass(Enum.class), "ALLOW");

            Method setFlag = cuboidClass.getMethod("setFlag", flagClass, Object.class);
            setFlag.invoke(region, flagsClass.getField("BUILD").get(null), allow);
            setFlag.invoke(region, flagsClass.getField("INTERACT").get(null), allow);
            // На всякий случай разрешаем ещё и проход/использование предметов,
            // чтобы WorldGuard никак не трогал territory шахты.
            trySetFlag(setFlag, flagsClass, region, "PASSTHROUGH", allow);
            trySetFlag(setFlag, flagsClass, region, "USE", allow);

            // Пересоздаём регион, чтобы флаги гарантированно применились
            if ((Boolean) managerClass.getMethod("hasRegion", String.class).invoke(manager, REGION_ID)) {
                managerClass.getMethod("removeRegion", String.class).invoke(manager, REGION_ID);
            }
            managerClass.getMethod("addRegion", Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion"))
                    .invoke(manager, region);
            try {
                managerClass.getMethod("save").invoke(manager);
            } catch (NoSuchMethodException ignored) {
                // В некоторых сборках сохранение происходит автоматически
            }
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static void trySetFlag(Method setFlag, Class<?> flagsClass,
                                   Object region, String flagName, Object allow) {
        try {
            Object flag = flagsClass.getField(flagName).get(null);
            if (flag != null) {
                setFlag.invoke(region, flag, allow);
            }
        } catch (Throwable ignored) {
            // Флаг отсутствует в данной версии WorldGuard — просто пропускаем
        }
    }

    /**
     * Удаляет регион шахты (используется при полном выключении ивента).
     */
    public boolean removeMineRegion(World world) {
        if (!available || world == null) {
            return false;
        }
        try {
            Object container = worldGuardPlugin.getClass().getMethod("getRegionContainer").invoke(worldGuardPlugin);
            if (container == null) {
                return false;
            }
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object weWorld = adapterClass.getMethod("adapt", World.class).invoke(null, world);
            Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");
            Object manager = container.getClass().getMethod("get", weWorldClass).invoke(container, weWorld);
            if (manager == null) {
                return false;
            }
            Class<?> managerClass = manager.getClass();
            if ((Boolean) managerClass.getMethod("hasRegion", String.class).invoke(manager, REGION_ID)) {
                managerClass.getMethod("removeRegion", String.class).invoke(manager, REGION_ID);
                try {
                    managerClass.getMethod("save").invoke(manager);
                } catch (NoSuchMethodException ignored) {
                    // ничего не делаем
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
