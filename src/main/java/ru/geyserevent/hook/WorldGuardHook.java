package ru.geyserevent.hook;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
 *
 * <p><b>Оптимизация (v1.9.7):</b> все отражённые классы, конструкторы, методы и поля
 * резолвятся один раз и кэшируются в статических полях, поэтому повторные вызовы
 * не делают {@code Class.forName} и не аллоцируют объекты.</p>
 */
public final class WorldGuardHook {

    public static final String REGION_ID = "geyserevent_mine";
    private static final int REGION_PRIORITY = 100;

    private final Object worldGuardPlugin;
    private final boolean available;

    // Закэшированные отражённые члены (заполняются один раз)
    private static volatile boolean reflected;
    private static Method wgGetRegionContainer;
    private static Method adapterAdapt;
    private static Method containerGet;
    private static Method managerHasRegion;
    private static Method managerRemoveRegion;
    private static Method managerAddRegion;
    private static Method managerSave;
    private static Method regionSetPriority;
    private static Method regionSetFlag;
    private static Constructor<?> cuboidConstructor;
    private static Method blockVectorAt;
    private static Field buildFlagField;
    private static Field interactFlagField;
    private static Field passthroughFlagField;
    private static Field useFlagField;
    private static Object allowState;

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
     * Резолвит и кэширует все нужные члены WorldGuard/WorldEdit API.
     *
     * @return true, если интеграция готова к работе
     */
    private boolean resolveReflection() {
        if (reflected) {
            return true;
        }
        try {
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            adapterAdapt = adapterClass.getMethod("adapt", World.class);

            Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");
            Class<?> containerClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer");
            containerGet = containerClass.getMethod("get", weWorldClass);

            Class<?> managerClass = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager");
            managerHasRegion = managerClass.getMethod("hasRegion", String.class);
            managerRemoveRegion = managerClass.getMethod("removeRegion", String.class);
            managerAddRegion = managerClass.getMethod("addRegion",
                    Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion"));
            try {
                managerSave = managerClass.getMethod("save");
            } catch (NoSuchMethodException ignored) {
                managerSave = null;
            }

            Class<?> cuboidClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion");
            Class<?> blockVectorClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            blockVectorAt = blockVectorClass.getMethod("at", int.class, int.class, int.class);
            cuboidConstructor = cuboidClass.getConstructor(String.class, blockVectorClass, blockVectorClass);

            regionSetPriority = cuboidClass.getMethod("setPriority", int.class);
            regionSetFlag = cuboidClass.getMethod("setFlag",
                    Class.forName("com.sk89q.worldguard.protection.flags.Flag"), Object.class);

            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            buildFlagField = flagsClass.getField("BUILD");
            interactFlagField = flagsClass.getField("INTERACT");
            passthroughFlagField = flagsClass.getField("PASSTHROUGH");
            useFlagField = flagsClass.getField("USE");

            Class<?> stateClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag$State");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object state = Enum.valueOf((Class) stateClass.asSubclass(Enum.class), "ALLOW");
            allowState = state;

            wgGetRegionContainer = worldGuardPlugin.getClass().getMethod("getRegionContainer");
            reflected = true;
            return true;
        } catch (Throwable throwable) {
            reflected = false;
            return false;
        }
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
     * @return true, если регион применён (или уже был применён ранее)
     */
    public boolean syncMineRegion(World world, int centerX, int centerY, int centerZ,
                                  int radius, int minY, int maxY) {
        return applyRegion(world, centerX, centerY, centerZ, radius, minY, maxY, false);
    }

    /**
     * То же, что и {@link #syncMineRegion}, но дополнительно проверяет существование региона:
     * если регион уже создан и не тронут — ничего не пересоздаёт и не сохраняет (экономия диска).
     */
    public boolean ensureMineRegion(World world, int centerX, int centerY, int centerZ,
                                    int radius, int minY, int maxY) {
        return applyRegion(world, centerX, centerY, centerZ, radius, minY, maxY, true);
    }

    private boolean applyRegion(World world, int centerX, int centerY, int centerZ,
                                int radius, int minY, int maxY, boolean checkExisting) {
        if (!available || world == null) {
            return false;
        }
        try {
            if (!resolveReflection()) {
                return false;
            }

            Object container = wgGetRegionContainer.invoke(worldGuardPlugin);
            if (container == null) {
                return false;
            }
            Object weWorld = adapterAdapt.invoke(null, world);
            Object manager = containerGet.invoke(container, weWorld);
            if (manager == null) {
                return false;
            }

            if (checkExisting && (Boolean) managerHasRegion.invoke(manager, REGION_ID)) {
                // Регион уже есть — ничего не делаем (никакой лишней записи на диск)
                return true;
            }

            Object min = blockVectorAt.invoke(null, centerX - radius, minY, centerZ - radius);
            Object max = blockVectorAt.invoke(null, centerX + radius, maxY, centerZ + radius);
            Object region = cuboidConstructor.newInstance(REGION_ID, min, max);

            // Высокий приоритет: наш регион важнее регионов спавна/протекта сервера
            regionSetPriority.invoke(region, REGION_PRIORITY);
            regionSetFlag.invoke(region, buildFlagField.get(null), allowState);
            regionSetFlag.invoke(region, interactFlagField.get(null), allowState);
            applyOptionalFlag(region, passthroughFlagField);
            applyOptionalFlag(region, useFlagField);

            // Пересоздаём регион, чтобы флаги гарантированно применились
            if ((Boolean) managerHasRegion.invoke(manager, REGION_ID)) {
                managerRemoveRegion.invoke(manager, REGION_ID);
            }
            managerAddRegion.invoke(manager, region);
            if (managerSave != null) {
                managerSave.invoke(manager);
            }
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private void applyOptionalFlag(Object region, Field flagField) {
        try {
            Object flag = flagField.get(null);
            if (flag != null) {
                regionSetFlag.invoke(region, flag, allowState);
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
            if (!resolveReflection()) {
                return false;
            }
            Object container = wgGetRegionContainer.invoke(worldGuardPlugin);
            if (container == null) {
                return false;
            }
            Object weWorld = adapterAdapt.invoke(null, world);
            Object manager = containerGet.invoke(container, weWorld);
            if (manager == null) {
                return false;
            }
            if ((Boolean) managerHasRegion.invoke(manager, REGION_ID)) {
                managerRemoveRegion.invoke(manager, REGION_ID);
                if (managerSave != null) {
                    managerSave.invoke(manager);
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
