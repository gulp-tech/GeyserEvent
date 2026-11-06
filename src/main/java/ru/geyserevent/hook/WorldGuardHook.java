package ru.geyserevent.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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
    public static final String EVENT_REGION_ID = "geyserevent_event";
    public static final String ALIEN_REGION_ID = "geyserevent_alien";
    private static final int REGION_PRIORITY = 100;
    private static final int EVENT_REGION_PRIORITY = 95;

    private final Object worldGuardPlugin;
    private final boolean available;

    // Закэшированные отражённые члены (заполняются один раз)
    private static volatile boolean reflected;
    private static Object regionContainerTarget;
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
    private static Field tntFlagField;
    private static Field otherExplosionFlagField;
    private static Field creeperExplosionFlagField;
    private static Field fireFlagField;
    private static Method managerGetApplicableRegions;
    private static Method regionGetFlag;
    private static Object allowState;
    private static Object denyState;

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
            tntFlagField = optionalFlag(flagsClass, "TNT");
            otherExplosionFlagField = optionalFlag(flagsClass, "OTHER_EXPLOSION");
            creeperExplosionFlagField = optionalFlag(flagsClass, "CREEPER_EXPLOSION");
            fireFlagField = optionalFlag(flagsClass, "FIRE");

            Class<?> stateClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag$State");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class denyEnum = stateClass.asSubclass(Enum.class);
            allowState = Enum.valueOf(denyEnum, "ALLOW");
            denyState = Enum.valueOf(denyEnum, "DENY");

            try {
                managerGetApplicableRegions = managerClass.getMethod("getApplicableRegions",
                        Class.forName("com.sk89q.worldedit.math.BlockVector3"));
            } catch (NoSuchMethodException ignored) {
                managerGetApplicableRegions = null;
            }
            regionGetFlag = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion")
                    .getMethod("getFlag", Class.forName("com.sk89q.worldguard.protection.flags.Flag"));

            try {
                wgGetRegionContainer = worldGuardPlugin.getClass().getMethod("getRegionContainer");
                regionContainerTarget = worldGuardPlugin;
            } catch (NoSuchMethodException ignored) {
                Class<?> wgApi = Class.forName("com.sk89q.worldguard.WorldGuard");
                Object instance = wgApi.getMethod("getInstance").invoke(null);
                Object platform = instance.getClass().getMethod("getPlatform").invoke(instance);
                wgGetRegionContainer = platform.getClass().getMethod("getRegionContainer");
                regionContainerTarget = platform;
            }
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

            Object container = wgGetRegionContainer.invoke(regionContainerTarget);
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
            setFlag(region, tntFlagField, denyState);
            setFlag(region, otherExplosionFlagField, denyState);
            setFlag(region, creeperExplosionFlagField, denyState);
            setFlag(region, fireFlagField, denyState);

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

    private static Field optionalFlag(Class<?> flagsClass, String name) {
        try {
            return flagsClass.getField(name);
        } catch (Throwable ignored) {
            return null;
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
            Object container = wgGetRegionContainer.invoke(regionContainerTarget);
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

    public boolean removeRegion(World world, String regionId) {
        if (!available || world == null || regionId == null) {
            return false;
        }
        try {
            if (!resolveReflection()) {
                return false;
            }
            Object container = wgGetRegionContainer.invoke(regionContainerTarget);
            if (container == null) {
                return false;
            }
            Object weWorld = adapterAdapt.invoke(null, world);
            Object manager = containerGet.invoke(container, weWorld);
            if (manager == null) {
                return false;
            }
            if ((Boolean) managerHasRegion.invoke(manager, regionId)) {
                managerRemoveRegion.invoke(manager, regionId);
                if (managerSave != null) {
                    managerSave.invoke(manager);
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Приват ивента: TNT/взрывы DENY. BUILD не трогаем — раскопки/реагенты должны ломаться.
     */
    public boolean syncProtectionRegion(World world, String regionId, int centerX, int centerY, int centerZ, int radius) {
        if (!available || world == null || regionId == null) {
            return false;
        }
        try {
            if (!resolveReflection()) {
                return false;
            }
            Object container = wgGetRegionContainer.invoke(regionContainerTarget);
            if (container == null) {
                return false;
            }
            Object weWorld = adapterAdapt.invoke(null, world);
            Object manager = containerGet.invoke(container, weWorld);
            if (manager == null) {
                return false;
            }
            int minY = Math.max(world.getMinHeight(), centerY - 16);
            int maxY = Math.min(world.getMaxHeight() - 1, centerY + 24);
            Object min = blockVectorAt.invoke(null, centerX - radius, minY, centerZ - radius);
            Object max = blockVectorAt.invoke(null, centerX + radius, maxY, centerZ + radius);
            Object region = cuboidConstructor.newInstance(regionId, min, max);
            regionSetPriority.invoke(region, EVENT_REGION_PRIORITY);
            setFlag(region, tntFlagField, denyState);
            setFlag(region, otherExplosionFlagField, denyState);
            setFlag(region, creeperExplosionFlagField, denyState);
            setFlag(region, fireFlagField, denyState);
            if ((Boolean) managerHasRegion.invoke(manager, regionId)) {
                managerRemoveRegion.invoke(manager, regionId);
            }
            managerAddRegion.invoke(manager, region);
            if (managerSave != null) {
                managerSave.invoke(manager);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * false — в этой точке WorldGuard запрещает взрыв (приват игрока или TNT: deny).
     */
    public boolean allowsExplosion(Location loc) {
        if (!available || loc == null || loc.getWorld() == null) {
            return true;
        }
        try {
            if (!resolveReflection() || managerGetApplicableRegions == null || regionGetFlag == null) {
                return true;
            }
            Object container = wgGetRegionContainer.invoke(regionContainerTarget);
            if (container == null) {
                return true;
            }
            Object weWorld = adapterAdapt.invoke(null, loc.getWorld());
            Object manager = containerGet.invoke(container, weWorld);
            if (manager == null) {
                return true;
            }
            Object vector = blockVectorAt.invoke(null, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            Object set = managerGetApplicableRegions.invoke(manager, vector);
            if (!(set instanceof Iterable<?> regions)) {
                return true;
            }
            for (Object region : regions) {
                if (flagEquals(region, tntFlagField, denyState)
                        || flagEquals(region, otherExplosionFlagField, denyState)
                        || flagEquals(region, creeperExplosionFlagField, denyState)) {
                    return false;
                }
                if (flagEquals(region, buildFlagField, denyState)
                        && !flagEquals(region, tntFlagField, allowState)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void setFlag(Object region, Field flagField, Object state) {
        if (flagField == null || state == null) {
            return;
        }
        try {
            Object flag = flagField.get(null);
            if (flag != null) {
                regionSetFlag.invoke(region, flag, state);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean flagEquals(Object region, Field flagField, Object expected) {
        if (flagField == null || expected == null || regionGetFlag == null) {
            return false;
        }
        try {
            Object flag = flagField.get(null);
            if (flag == null) {
                return false;
            }
            return expected.equals(regionGetFlag.invoke(region, flag));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
