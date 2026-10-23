package ru.geyserevent.mine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.excavation.BrushLootManager;
import ru.geyserevent.excavation.BrushTier;
import ru.geyserevent.mine.MinerBackpackManager.MineOreType;
import ru.geyserevent.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Менеджер постоянного ивента «Шахта Древних (Жила Жеод)» на координатах 0 ~ 0.
 * - Масштабная схема 45x45 (радиус 22, зона 24.5 блоков, schema-version: 3) с многоуровневыми
 *   террасами карьера, деревянными крепями, рельсовыми путями, 4 буровыми башнями, 4 вратами
 *   и величественной Кузницей Древних в центре.
 * - Без БоссБара: оповещения об обновлении жилы пишутся напрямую в чат.
 * - При вскапывании руды или жеоды предмет выпадает НА ПОЛ (world.dropItemNaturally).
 * - Игроки собирают руду с пола в соответствии с вместимостью их 3D-Рюкзака Шахтёра на спине,
 *   а при клике по 2-метровому Древнему Кузнецу открывают меню переплавки, раскола жеод и прокачки рюкзака.
 */
public class AncientMineManager {

    private static final int CURRENT_SCHEMA_VERSION = 3;
    public static final int MINE_RADIUS = 22;
    public static final double MINE_ZONE_LIMIT = 24.5;

    public record SchematicBlock(int dx, int dy, int dz, String blockDataString) {
    }

    private final GeyserEventPlugin plugin;
    private final File schematicFile;
    private final File activeBackupFile;
    private final List<SchematicBlock> blocks = new ArrayList<>();
    private final List<Block> pastedBlocks = new ArrayList<>();

    private Location centerLocation;
    private final List<Location> veinLocations = new ArrayList<>();
    private final Map<String, BrushTier> activeGeodeBlocks = new HashMap<>();

    private UUID blacksmithNpcUuid;
    private final List<UUID> hologramEntityUuids = new ArrayList<>();

    private BukkitTask tickTask;
    private int secondsUntilFullRegen = 180;
    private int secondsUntilNextGeodeSpawn = 18;
    private int regenCycleCount = 0;

    public AncientMineManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "schematics");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.schematicFile = new File(dir, "ancient_mine.schem.yml");
        this.activeBackupFile = new File(dir, "ancient_mine_active_backup.yml");
        loadSchematic();
    }

    public void loadSchematic() {
        blocks.clear();
        boolean needsRegen = !schematicFile.exists();
        if (!needsRegen) {
            YamlConfiguration check = YamlConfiguration.loadConfiguration(schematicFile);
            if (check.getInt("schema-version", 1) < CURRENT_SCHEMA_VERSION) {
                needsRegen = true;
            }
        }
        if (needsRegen) {
            saveDefaultAncientMineSchematic();
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(schematicFile);
        List<Map<?, ?>> list = yaml.getMapList("blocks");
        for (Map<?, ?> entry : list) {
            int dx = toInt(entry.get("dx"));
            int dy = toInt(entry.get("dy"));
            int dz = toInt(entry.get("dz"));
            String data = String.valueOf(entry.get("data"));
            blocks.add(new SchematicBlock(dx, dy, dz, data));
        }
        plugin.getLogger().info("Загружена улучшенная схема «Шахта Древних» 45x45 (блоков: " + blocks.size() + ").");
    }

    /**
     * Генерирует масштабную схему 45x45 (радиус 22, schema-version: 3):
     * - 4-слойное основание с глубокими террасными карьерами (dy = -4..0)
     * - 4 главных моста с рельсами и шахтёрскими крепями
     * - Величественная Ротонда Кузницы Древних в центре (радиус 5, высота до dy = 10)
     * - 4 входных бастионных врат (Север, Юг, Восток, Запад)
     * - 4 угловых буровых крана с подвешенными кристальными кластерами
     * - Более 260 блоков рудной жилы на 3 высотных уровнях в 4 секторах карьера
     */
    private void saveDefaultAncientMineSchematic() {
        Map<String, SchematicBlock> map = new LinkedHashMap<>();
        int r = MINE_RADIUS;

        // 1. Массивное 4-слойное основание и террасные карьеры (dy = -4..0)
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist > r + 0.5) {
                    continue;
                }
                putBlock(map, dx, -4, dz, "minecraft:deepslate_bricks");
                putBlock(map, dx, -3, dz, "minecraft:deepslate_tiles");

                boolean isQuarrySector = Math.abs(dx) >= 5 && Math.abs(dx) <= 18
                        && Math.abs(dz) >= 5 && Math.abs(dz) <= 18
                        && dist <= 19.5;
                boolean isDeepPit = Math.abs(dx) >= 7 && Math.abs(dx) <= 15
                        && Math.abs(dz) >= 7 && Math.abs(dz) <= 15
                        && dist <= 17.5;

                if (isDeepPit) {
                    putBlock(map, dx, -2, dz, "minecraft:cobbled_deepslate");
                    // На уровне dy = -1 в глубоком карьере размещаем нижний пласт рудной жилы
                    if ((dx + dz) % 2 == 0) {
                        putBlock(map, dx, -1, dz, "minecraft:deepslate_gold_ore");
                    } else {
                        putBlock(map, dx, -1, dz, "minecraft:air");
                    }
                } else {
                    putBlock(map, dx, -2, dz, "minecraft:polished_deepslate");
                    putBlock(map, dx, -1, dz, isQuarrySector ? "minecraft:cobbled_deepslate" : "minecraft:polished_blackstone_bricks");
                }

                // Расчистка воздуха над карьером (dy = 0..12)
                for (int dy = 0; dy <= 12; dy++) {
                    putBlock(map, dx, dy, dz, "minecraft:air");
                }

                // Поверхность dy = 0:
                if (dist >= r - 1.2) {
                    putBlock(map, dx, 0, dz, "minecraft:deepslate_bricks");
                } else if (Math.abs(dx) <= 3 || Math.abs(dz) <= 3) {
                    // Крестообразные мосты от 4 врат к центральной Кузнице
                    if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
                        putBlock(map, dx, 0, dz, "minecraft:polished_blackstone_bricks");
                    } else if ((dx == 0 && Math.abs(dz) >= 6 && Math.abs(dz) <= 18)
                            || (dz == 0 && Math.abs(dx) >= 6 && Math.abs(dx) <= 18)) {
                        putBlock(map, dx, 0, dz, "minecraft:tuff_bricks".equals("") ? "minecraft:polished_ANDESITE".toLowerCase() : "minecraft:polished_andesite");
                    } else {
                        putBlock(map, dx, 0, dz, "minecraft:deepslate_tile_slab[type=double]");
                    }
                } else if (isDeepPit) {
                    // В глубоком карьере оставляем проход и отдельные рудные глыбы на dy = 0
                    if ((Math.abs(dx) + Math.abs(dz)) % 3 == 0) {
                        putBlock(map, dx, 0, dz, "minecraft:deepslate_diamond_ore");
                    }
                } else if (isQuarrySector) {
                    // Терраса карьера dy = 0 — богатая рудная жила
                    putBlock(map, dx, 0, dz, "minecraft:deepslate_iron_ore");
                } else {
                    putBlock(map, dx, 0, dz, "minecraft:basalt[axis=y]");
                }
            }
        }

        // 2. Многоуровневые рудные скалы и шахтёрские деревянные крепи в 4 секторах карьера (dy = 1..3)
        int[][] sectorCenters = {{-11, -11}, {11, -11}, {-11, 11}, {11, 11}};
        for (int[] sc : sectorCenters) {
            int sx = sc[0];
            int sz = sc[1];
            for (int ox = -4; ox <= 4; ox++) {
                for (int oz = -4; oz <= 4; oz++) {
                    int distManh = Math.abs(ox) + Math.abs(oz);
                    if (distManh >= 3 && distManh <= 6) {
                        putBlock(map, sx + ox, 1, sz + oz, "minecraft:deepslate_iron_ore");
                        if (distManh >= 5) {
                            putBlock(map, sx + ox, 2, sz + oz, "minecraft:deepslate_gold_ore");
                        }
                    }
                }
            }
            // Центральный гео-кристалл в каждом из 4 карьеров
            putBlock(map, sx, 0, sz, "minecraft:cobbled_deepslate");
            putBlock(map, sx, 1, sz, "minecraft:amethyst_block");
            putBlock(map, sx, 2, sz, "minecraft:crying_obsidian");

            // Шахтёрские деревянные арки-крепи на входе в каждый карьер
            int signX = sx < 0 ? -1 : 1;
            int signZ = sz < 0 ? -1 : 1;
            int archX = sx - signX * 4;
            int archZ = sz - signZ * 4;
            for (int y = 1; y <= 4; y++) {
                putBlock(map, archX, y, sz - signZ * 2, "minecraft:spruce_log[axis=y]");
                putBlock(map, sx - signX * 2, y, archZ, "minecraft:spruce_log[axis=y]");
            }
            putBlock(map, archX, 4, sz - signZ * 2, "minecraft:spruce_planks");
            putBlock(map, sx - signX * 2, 4, archZ, "minecraft:spruce_planks");
            putBlock(map, archX, 3, sz - signZ * 2, "minecraft:lantern[hanging=true]");
        }

        // 3. Величественная Центральная Ротонда «Кузница Древних» (радиус 5, dy = 1..10)
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= 4.4) {
                    putBlock(map, dx, 1, dz, "minecraft:polished_blackstone_bricks");
                } else if (d <= 5.4 && (Math.abs(dx) <= 2 || Math.abs(dz) <= 2)) {
                    putBlock(map, dx, 1, dz, "minecraft:polished_blackstone_brick_slab[type=bottom]");
                }
            }
        }
        // Лавовые каналы горна под оранжевым стеклом
        int[][] lavaPits = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}, {-1, -2}, {1, -2}, {-1, 2}, {1, 2}};
        for (int[] lp : lavaPits) {
            putBlock(map, lp[0], 0, lp[1], "minecraft:magma_block");
            putBlock(map, lp[0], 1, lp[1], "minecraft:orange_stained_glass");
        }
        // Пьедестал Кузнеца в самом центре и рабочие станции Кузницы вокруг
        putBlock(map, 0, 1, 0, "minecraft:lodestone");
        putBlock(map, -3, 2, 0, "minecraft:anvil[facing=north]");
        putBlock(map, 3, 2, 0, "minecraft:anvil[facing=south]");
        putBlock(map, 0, 2, -3, "minecraft:smithing_table");
        putBlock(map, 0, 2, 3, "minecraft:smithing_table");
        putBlock(map, -2, 2, -2, "minecraft:blast_furnace[facing=south,lit=true]");
        putBlock(map, 2, 2, -2, "minecraft:blast_furnace[facing=south,lit=true]");
        putBlock(map, -2, 2, 2, "minecraft:blast_furnace[facing=north,lit=true]");
        putBlock(map, 2, 2, 2, "minecraft:blast_furnace[facing=north,lit=true]");

        // 4 мощные колонны Ротонды и высокий купол (dy = 2..10)
        int[][] forgePillars = {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}};
        for (int[] fp : forgePillars) {
            int px = fp[0];
            int pz = fp[1];
            putBlock(map, px, 2, pz, "minecraft:chiseled_polished_blackstone");
            putBlock(map, px, 3, pz, "minecraft:deepslate_bricks");
            putBlock(map, px, 4, pz, "minecraft:deepslate_brick_wall");
            putBlock(map, px, 5, pz, "minecraft:deepslate_brick_wall");
            putBlock(map, px, 6, pz, "minecraft:chiseled_polished_blackstone");
            putBlock(map, px, 7, pz, "minecraft:shroomlight");
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= 4.8 && d >= 3.2) {
                    putBlock(map, dx, 7, dz, "minecraft:deepslate_brick_slab[type=top]");
                } else if (d < 3.2 && d >= 1.5) {
                    putBlock(map, dx, 8, dz, "minecraft:deepslate_tile_slab[type=bottom]");
                } else if (d < 1.5) {
                    putBlock(map, dx, 9, dz, "minecraft:chiseled_deepslate");
                }
            }
        }
        putBlock(map, 0, 10, 0, "minecraft:soul_lantern[hanging=false]");
        putBlock(map, 0, 8, 0, "minecraft:chain[axis=y]");
        putBlock(map, 0, 7, 0, "minecraft:chain[axis=y]");
        putBlock(map, 0, 6, 0, "minecraft:soul_lantern[hanging=true]");

        // 4. Четыре Входных Бастионных Врат на севере, юге, востоке и западе (радиус 21)
        buildGrandMineGate(map, 0, -21, true);
        buildGrandMineGate(map, 0, 21, true);
        buildGrandMineGate(map, -21, 0, false);
        buildGrandMineGate(map, 21, 0, false);

        // 5. Четыре Угловые Буровые Вышки / Крана с подвешенными кристаллами на диагоналях
        int[][] cranes = {{-16, -16}, {16, -16}, {-16, 16}, {16, 16}};
        for (int[] c : cranes) {
            int cx = c[0];
            int cz = c[1];
            for (int y = 1; y <= 8; y++) {
                putBlock(map, cx, y, cz, "minecraft:spruce_log[axis=y]");
            }
            int dirX = cx < 0 ? 1 : -1;
            int dirZ = cz < 0 ? 1 : -1;
            putBlock(map, cx + dirX, 8, cz, "minecraft:spruce_slab[type=top]");
            putBlock(map, cx, 8, cz + dirZ, "minecraft:spruce_slab[type=top]");
            putBlock(map, cx + dirX, 8, cz + dirZ, "minecraft:chiseled_deepslate");
            putBlock(map, cx + dirX * 2, 8, cz + dirZ * 2, "minecraft:spruce_trapdoor[half=top,open=false]");
            putBlock(map, cx + dirX * 2, 7, cz + dirZ * 2, "minecraft:chain[axis=y]");
            putBlock(map, cx + dirX * 2, 6, cz + dirZ * 2, "minecraft:chain[axis=y]");
            putBlock(map, cx + dirX * 2, 5, cz + dirZ * 2, "minecraft:shroomlight");
            putBlock(map, cx, 9, cz, "minecraft:soul_lantern[hanging=false]");
        }

        // 6. Внешняя крепостная стена карьера по кольцу r = 21 с колоннами и фонарями
        for (int angleDeg = 0; angleDeg < 360; angleDeg += 10) {
            if (angleDeg % 90 == 0 || Math.abs((angleDeg % 90) - 90) <= 10 || (angleDeg % 90) <= 10) {
                continue;
            }
            double rad = Math.toRadians(angleDeg);
            int bx = (int) Math.round(Math.cos(rad) * 21.0);
            int bz = (int) Math.round(Math.sin(rad) * 21.0);
            putBlock(map, bx, 1, bz, "minecraft:deepslate_bricks");
            putBlock(map, bx, 2, bz, "minecraft:deepslate_brick_wall");
            if (angleDeg % 20 == 0) {
                putBlock(map, bx, 2, bz, "minecraft:chiseled_deepslate");
                putBlock(map, bx, 3, bz, "minecraft:deepslate_brick_wall");
                putBlock(map, bx, 4, bz, "minecraft:lantern[hanging=false]");
            }
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", CURRENT_SCHEMA_VERSION);
        yaml.set("name", "ancient_mine_45x45_v3");
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (SchematicBlock b : map.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dx", b.dx());
            m.put("dy", b.dy());
            m.put("dz", b.dz());
            m.put("data", b.blockDataString());
            serialized.add(m);
        }
        yaml.set("blocks", serialized);
        try {
            yaml.save(schematicFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить ancient_mine.schem.yml: " + ex.getMessage());
        }
    }

    private void buildGrandMineGate(Map<String, SchematicBlock> map, int gx, int gz, boolean northSouth) {
        for (int offset = -3; offset <= 3; offset++) {
            int x = northSouth ? gx + offset : gx;
            int z = northSouth ? gz : gz + offset;
            if (Math.abs(offset) == 3) {
                for (int y = 1; y <= 6; y++) {
                    putBlock(map, x, y, z, "minecraft:deepslate_bricks");
                }
                putBlock(map, x, 7, z, "minecraft:chiseled_polished_blackstone");
                putBlock(map, x, 8, z, "minecraft:soul_campfire[lit=true]");
            } else if (Math.abs(offset) == 2) {
                putBlock(map, x, 5, z, "minecraft:deepslate_brick_slab[type=top]");
                putBlock(map, x, 6, z, "minecraft:deepslate_bricks");
            } else {
                putBlock(map, x, 6, z, "minecraft:deepslate_brick_slab[type=top]");
                putBlock(map, x, 7, z, "minecraft:polished_blackstone_brick_slab[type=bottom]");
                if (offset == 0) {
                    putBlock(map, x, 6, z, "minecraft:shroomlight");
                    putBlock(map, x, 5, z, "minecraft:soul_lantern[hanging=true]");
                }
            }
        }
    }

    private void putBlock(Map<String, SchematicBlock> map, int dx, int dy, int dz, String data) {
        String key = dx + ":" + dy + ":" + dz;
        map.put(key, new SchematicBlock(dx, dy, dz, data));
    }

    public void initializePermanentMine() {
        restoreOrStartMine();
    }

    private void restoreOrStartMine() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("mine");
        boolean enabled = section == null || section.getBoolean("enabled", true);
        if (!enabled) {
            return;
        }

        if (activeBackupFile.exists()) {
            restoreActiveSchematic();
        }

        String worldName = section != null
                ? section.getString("world", plugin.getPluginConfig().getWorldName())
                : plugin.getPluginConfig().getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return;
        }

        int x = section != null ? section.getInt("x", 0) : 0;
        int z = section != null ? section.getInt("z", 0) : 0;
        int configuredY = section != null ? section.getInt("y", -999) : -999;

        world.getChunkAt(x >> 4, z >> 4).load(true);
        int y = (configuredY > world.getMinHeight() && configuredY < world.getMaxHeight())
                ? configuredY
                : world.getHighestBlockYAt(x, z);

        Location target = new Location(world, x + 0.5, y, z + 0.5);
        spawnMineAt(target, false);
    }

    public void spawnMineAt(Location center, boolean saveCoordsToConfig) {
        shutdownMine(true);

        World world = center.getWorld();
        if (world == null) {
            return;
        }

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        this.centerLocation = new Location(world, cx + 0.5, cy, cz + 0.5);

        if (saveCoordsToConfig) {
            plugin.getConfig().set("mine.enabled", true);
            plugin.getConfig().set("mine.world", world.getName());
            plugin.getConfig().set("mine.x", cx);
            plugin.getConfig().set("mine.y", cy);
            plugin.getConfig().set("mine.z", cz);
            plugin.saveConfig();
        }

        for (int dx = -MINE_RADIUS - 2; dx <= MINE_RADIUS + 2; dx += 16) {
            for (int dz = -MINE_RADIUS - 2; dz <= MINE_RADIUS + 2; dz += 16) {
                world.getChunkAt((cx + dx) >> 4, (cz + dz) >> 4).load(true);
            }
        }

        YamlConfiguration backup = new YamlConfiguration();
        backup.set("world", world.getName());
        List<Map<String, Object>> backupList = new ArrayList<>();
        pastedBlocks.clear();
        veinLocations.clear();
        activeGeodeBlocks.clear();

        for (SchematicBlock sb : blocks) {
            Block block = world.getBlockAt(cx + sb.dx(), cy + sb.dy(), cz + sb.dz());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("x", block.getX());
            entry.put("y", block.getY());
            entry.put("z", block.getZ());
            entry.put("data", block.getBlockData().getAsString());
            backupList.add(entry);

            try {
                BlockData bd = Bukkit.createBlockData(sb.blockDataString());
                block.setBlockData(bd, false);
            } catch (IllegalArgumentException ex) {
                block.setType(Material.AIR, false);
            }
            pastedBlocks.add(block);

            // Запоминаем слоты рудной жилы в 4 карьерах (уровни dy = -1..2)
            double dist = Math.hypot(sb.dx(), sb.dz());
            boolean isQuarryVein = (sb.dy() >= -1 && sb.dy() <= 2)
                    && Math.abs(sb.dx()) >= 5 && Math.abs(sb.dx()) <= 18
                    && Math.abs(sb.dz()) >= 5 && Math.abs(sb.dz()) <= 18
                    && dist <= 19.5
                    && !block.getType().isAir()
                    && !block.getType().name().contains("SPRUCE")
                    && !block.getType().name().contains("LANTERN");
            if (isQuarryVein) {
                veinLocations.add(block.getLocation());
            }
        }

        backup.set("blocks", backupList);
        try {
            backup.save(activeBackupFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить бэкап Шахты Древних: " + ex.getMessage());
        }

        regenerateAllOresAndGeodes(false);
        spawnBlacksmithNpc();
        startMineTicker();

        plugin.getLogger().info("Постоянный ивент «Шахта Древних» (45x45) активен на координатах: "
                + world.getName() + " " + cx + " " + cy + " " + cz);
    }

    public void regenerateAllOresAndGeodes() {
        regenerateAllOresAndGeodes(true);
    }

    /**
     * Полностью обновляет все рудные блоки в жиле и размещает Древние жеоды.
     * Пишет в чат о регенерации (БЕЗ БоссБара!).
     */
    @SuppressWarnings("deprecation")
    public void regenerateAllOresAndGeodes(boolean announceInChat) {
        if (centerLocation == null || centerLocation.getWorld() == null) {
            return;
        }
        activeGeodeBlocks.clear();
        regenCycleCount++;
        boolean richVeinCycle = (regenCycleCount % 3 == 0);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        List<Location> shuffled = new ArrayList<>(veinLocations);
        Collections.shuffle(shuffled, rnd);

        int initialGeodes = richVeinCycle
                ? Math.min(28, Math.max(16, shuffled.size() / 7))
                : Math.min(18, Math.max(10, shuffled.size() / 10));

        for (int i = 0; i < shuffled.size(); i++) {
            Location loc = shuffled.get(i);
            Block b = loc.getBlock();
            if (i < initialGeodes) {
                BrushTier tier = plugin.getBrushLootManager().rollRandomGeodeTier();
                placeGeodeBlock(b, tier);
            } else {
                b.setType(randomOreMaterial(rnd, richVeinCycle), false);
            }
        }

        this.secondsUntilFullRegen = getConfiguredRegenIntervalSeconds();
        this.secondsUntilNextGeodeSpawn = 18;

        if (announceInChat) {
            if (richVeinCycle) {
                Bukkit.broadcastMessage(ColorUtil.color(
                        "&e┠ &#FFB81CШ&#FFAE18а&#FFA414х&#FF9A10т&#FF900Cа-&#FF8608Д&#FF7C04р&#FF7200е&#FF6800в&#FF5E00н&#FF5400и&#FF4A00х &8(&#E0C9120 ~ 0&8): &6Богатая жила и легендарные жеоды обновились!"
                ));
            } else {
                Bukkit.broadcastMessage(ColorUtil.color(
                        "&e┠ &#FFB81CШ&#FFAE18а&#FFA414х&#FF9A10т&#FF900Cа-&#FF8608Д&#FF7C04р&#FF7200е&#FF6800в&#FF5E00н&#FF5400и&#FF4A00х &8(&#E0C9120 ~ 0&8): &fРудная жила и древние жеоды обновились! &7(Следующее через " + ColorUtil.formatDurationVerbose(secondsUntilFullRegen) + ")"
                ));
            }
            for (Player p : centerLocation.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(centerLocation) <= 55.0 * 55.0) {
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.15f);
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
                }
            }
        }
    }

    private Material randomOreMaterial(ThreadLocalRandom rnd, boolean richVein) {
        int roll = rnd.nextInt(100);
        if (richVein) {
            if (roll < 15) return Material.DEEPSLATE_COPPER_ORE;
            if (roll < 35) return Material.DEEPSLATE_IRON_ORE;
            if (roll < 58) return Material.DEEPSLATE_GOLD_ORE;
            if (roll < 78) return Material.DEEPSLATE_EMERALD_ORE;
            if (roll < 92) return Material.DEEPSLATE_DIAMOND_ORE;
            return Material.ANCIENT_DEBRIS;
        }
        if (roll < 24) return Material.DEEPSLATE_COPPER_ORE;
        if (roll < 50) return Material.DEEPSLATE_IRON_ORE;
        if (roll < 72) return Material.DEEPSLATE_GOLD_ORE;
        if (roll < 86) return Material.DEEPSLATE_EMERALD_ORE;
        if (roll < 96) return Material.DEEPSLATE_DIAMOND_ORE;
        return Material.ANCIENT_DEBRIS;
    }

    private void placeGeodeBlock(Block block, BrushTier tier) {
        Material mat = switch (tier) {
            case COMMON -> Material.AMETHYST_BLOCK;
            case EPIC -> Material.CRYING_OBSIDIAN;
            case LEGENDARY -> Material.GILDED_BLACKSTONE;
        };
        block.setType(mat, false);
        activeGeodeBlocks.put(blockKey(block.getLocation()), tier);
    }

    public boolean spawnSingleRandomGeode() {
        if (veinLocations.isEmpty() || activeGeodeBlocks.size() >= 30) {
            return false;
        }
        List<Location> candidates = new ArrayList<>();
        for (Location loc : veinLocations) {
            if (!activeGeodeBlocks.containsKey(blockKey(loc))) {
                candidates.add(loc);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        Location chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        BrushTier tier = plugin.getBrushLootManager().rollRandomGeodeTier();
        placeGeodeBlock(chosen.getBlock(), tier);
        if (chosen.getWorld() != null) {
            chosen.getWorld().playSound(chosen, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.2f);
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private void spawnBlacksmithNpc() {
        removeBlacksmithEntities();
        if (centerLocation == null || centerLocation.getWorld() == null) {
            return;
        }
        World world = centerLocation.getWorld();
        Location npcLoc = centerLocation.clone().add(0.0, 2.0, 0.0);

        Villager villager = world.spawn(npcLoc, Villager.class, v -> {
            v.setProfession(Villager.Profession.WEAPONSMITH);
            v.setVillagerType(Villager.Type.TAIGA);
            v.setVillagerLevel(5);
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setCollidable(false);
            v.setRemoveWhenFarAway(false);
            v.setPersistent(true);
            v.setCustomNameVisible(false);

            Attribute scaleAttr = resolveScaleAttribute();
            if (scaleAttr != null) {
                AttributeInstance inst = v.getAttribute(scaleAttr);
                if (inst != null) {
                    inst.setBaseValue(2.0);
                }
            }
        });
        this.blacksmithNpcUuid = villager.getUniqueId();

        Location holoLoc = npcLoc.clone().add(0.0, 3.85, 0.0);
        try {
            TextDisplay td = world.spawn(holoLoc, TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setSeeThrough(true);
                d.setShadowed(true);
                d.setPersistent(true);
                d.setText(buildBlacksmithHologramText());
            });
            hologramEntityUuids.add(td.getUniqueId());
        } catch (Throwable t) {
            ArmorStand as = world.spawn(holoLoc, ArmorStand.class, a -> {
                a.setVisible(false);
                a.setMarker(true);
                a.setGravity(false);
                a.setCustomNameVisible(true);
                a.setCustomName(ColorUtil.color("&#FFB81C&lДревний Кузнец &7(Кузница и Рюкзак)"));
            });
            hologramEntityUuids.add(as.getUniqueId());
        }
    }

    private String buildBlacksmithHologramText() {
        return ColorUtil.color(
                "&#FFB81C&lДревний Кузнец\n"
                        + "&fЖеод в жиле: &#34D711" + activeGeodeBlocks.size() + " шт. &8| &fОбновление: &#E0C912" + ColorUtil.formatDuration(secondsUntilFullRegen) + "\n"
                        + "&6Нажмите: &fПереплавка руды, Раскол жеод и &#34D711Прокачка Рюкзака"
        );
    }

    @SuppressWarnings("deprecation")
    private void updateBlacksmithHologram() {
        if (centerLocation == null || centerLocation.getWorld() == null) {
            return;
        }
        if (!centerLocation.getWorld().isChunkLoaded(centerLocation.getBlockX() >> 4, centerLocation.getBlockZ() >> 4)) {
            return;
        }
        Entity npc = blacksmithNpcUuid != null ? Bukkit.getEntity(blacksmithNpcUuid) : null;
        if (npc == null || !npc.isValid()) {
            spawnBlacksmithNpc();
            return;
        }
        String text = buildBlacksmithHologramText();
        for (UUID uuid : hologramEntityUuids) {
            Entity e = Bukkit.getEntity(uuid);
            if (e instanceof TextDisplay td) {
                td.setText(text);
            }
        }
    }

    private void startMineTicker() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        this.secondsUntilFullRegen = getConfiguredRegenIntervalSeconds();
        this.secondsUntilNextGeodeSpawn = 18;

        this.tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (centerLocation == null || centerLocation.getWorld() == null) {
                    return;
                }
                secondsUntilFullRegen--;
                secondsUntilNextGeodeSpawn--;

                // Напоминание в чат игрокам рядом с шахтой за 30 секунд до регенерации
                if (secondsUntilFullRegen == 30) {
                    for (Player p : centerLocation.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(centerLocation) <= 55.0 * 55.0) {
                            p.sendMessage(ColorUtil.color(
                                    "&e┠ &#FFB81CШахта Древних: &fРудная жила и древние жеоды обновятся через &#E0C91230 сек.&f!"
                            ));
                        }
                    }
                }

                if (secondsUntilFullRegen <= 0) {
                    regenerateAllOresAndGeodes(true);
                } else if (secondsUntilNextGeodeSpawn <= 0) {
                    spawnSingleRandomGeode();
                    secondsUntilNextGeodeSpawn = 18;
                }

                updateBlacksmithHologram();
                spawnLightweightGeodeParticles();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void spawnLightweightGeodeParticles() {
        if (centerLocation == null || centerLocation.getWorld() == null || activeGeodeBlocks.isEmpty()) {
            return;
        }
        World world = centerLocation.getWorld();
        boolean playerNearby = false;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(centerLocation) <= 45.0 * 45.0) {
                playerNearby = true;
                break;
            }
        }
        if (!playerNearby) {
            return;
        }

        for (Map.Entry<String, BrushTier> entry : activeGeodeBlocks.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts.length != 3) {
                continue;
            }
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            Location pLoc = new Location(world, x + 0.5, y + 1.05, z + 0.5);
            Particle particle = switch (entry.getValue()) {
                case COMMON -> Particle.WAX_OFF;
                case EPIC -> Particle.PORTAL;
                case LEGENDARY -> Particle.FLAME;
            };
            world.spawnParticle(particle, pLoc, 2, 0.2, 0.15, 0.2, 0.01);
        }
    }

    public boolean isInsideMineTerritory(Location loc) {
        if (centerLocation == null || loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!loc.getWorld().equals(centerLocation.getWorld())) {
            return false;
        }
        int dx = Math.abs(loc.getBlockX() - centerLocation.getBlockX());
        int dz = Math.abs(loc.getBlockZ() - centerLocation.getBlockZ());
        int dy = loc.getBlockY() - centerLocation.getBlockY();
        return dx <= MINE_RADIUS + 1 && dz <= MINE_RADIUS + 1 && dy >= -5 && dy <= 15;
    }

    public boolean isPlayerInsideMineZone(Location loc) {
        if (centerLocation == null || loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!loc.getWorld().equals(centerLocation.getWorld())) {
            return false;
        }
        double dx = Math.abs(loc.getX() - centerLocation.getX());
        double dz = Math.abs(loc.getZ() - centerLocation.getZ());
        double dy = Math.abs(loc.getY() - centerLocation.getY());
        return dx <= MINE_ZONE_LIMIT && dz <= MINE_ZONE_LIMIT && dy <= 16.0;
    }

    /**
     * Обрабатывает ломание блока в «Шахте Древних».
     * Руда и Древние жеоды НЕ попадают сразу в инвентарь, а выпадают НА ПОЛ!
     */
    public boolean handleMineBlockBreak(Player player, Block block) {
        if (!isInsideMineTerritory(block.getLocation())) {
            return false;
        }

        String key = blockKey(block.getLocation());
        BrushLootManager lootManager = plugin.getBrushLootManager();
        MinerBackpackManager backpackMgr = plugin.getMinerBackpackManager();
        Location dropLoc = block.getLocation().add(0.5, 0.65, 0.5);

        // 1. Проверяем, не является ли этот блок «Древней жеодой»
        BrushTier geodeTier = activeGeodeBlocks.remove(key);
        if (geodeTier != null) {
            block.setType(Material.COBBLED_DEEPSLATE, false);

            ItemStack geodeItem = lootManager.createGeodeItem(geodeTier);
            Item dropped = block.getWorld().dropItemNaturally(dropLoc, geodeItem);
            dropped.setVelocity(new Vector(0.0, 0.22, 0.0));
            dropped.setPickupDelay(6);

            player.playSound(dropLoc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.2f);
            block.getWorld().spawnParticle(Particle.WAX_OFF, dropLoc, 6, 0.2, 0.2, 0.2, 0.02);
            updateBlacksmithHologram();
            return true;
        }

        // 2. Проверяем, не является ли этот блок рудой в жиле
        boolean isVeinBlock = false;
        for (Location vLoc : veinLocations) {
            if (vLoc.getBlockX() == block.getX() && vLoc.getBlockY() == block.getY() && vLoc.getBlockZ() == block.getZ()) {
                isVeinBlock = true;
                break;
            }
        }

        if (isVeinBlock && block.getType() != Material.COBBLED_DEEPSLATE) {
            Material brokenType = block.getType();
            block.setType(Material.COBBLED_DEEPSLATE, false);

            MineOreType oreType = switch (brokenType) {
                case DEEPSLATE_COPPER_ORE, COPPER_ORE -> MineOreType.COPPER;
                case DEEPSLATE_IRON_ORE, IRON_ORE -> MineOreType.IRON;
                case DEEPSLATE_GOLD_ORE, GOLD_ORE -> MineOreType.GOLD;
                case DEEPSLATE_EMERALD_ORE, EMERALD_ORE -> MineOreType.EMERALD;
                case DEEPSLATE_DIAMOND_ORE, DIAMOND_ORE -> MineOreType.DIAMOND;
                case ANCIENT_DEBRIS -> MineOreType.ANCIENT_DEBRIS;
                default -> MineOreType.IRON;
            };

            // Выбрасываем руду НА ПОЛ (а не сразу в инвентарь!)
            ItemStack oreDrop = backpackMgr.createMineOreItem(oreType, 1);
            Item dropped = block.getWorld().dropItemNaturally(dropLoc, oreDrop);
            dropped.setVelocity(new Vector(0.0, 0.20, 0.0));
            dropped.setPickupDelay(6);

            player.playSound(dropLoc, Sound.BLOCK_DEEPSLATE_BREAK, 0.9f, 1.1f);

            // Шанс вскрыть под рудой новую Древнюю жеоду (увеличивается от уровня прокачки Рюкзака!)
            int baseChance = 12 + backpackMgr.getGeodeBonusChancePercent(player);
            if (ThreadLocalRandom.current().nextInt(100) < baseChance) {
                BrushTier bonusTier = lootManager.rollRandomGeodeTier();
                placeGeodeBlock(block, bonusTier);
                player.playSound(dropLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);
            }
            return true;
        }

        // Остальные блоки структуры защищены
        return true;
    }

    public boolean isBlacksmithNpc(Entity entity) {
        return entity != null && blacksmithNpcUuid != null && blacksmithNpcUuid.equals(entity.getUniqueId());
    }

    /**
     * При клике по Древнему Кузнецу или наковальне/горну в центре открывает меню
     * «Кузница Древних и Прокачка Рюкзака»!
     */
    public boolean handleBlacksmithInteract(Player player) {
        if (plugin.getMinerBackpackManager() != null) {
            plugin.getMinerBackpackManager().openForgeAndBackpackMenu(player);
        }
        return true;
    }

    public void shutdownMine(boolean restoreBlocks) {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        removeBlacksmithEntities();
        activeGeodeBlocks.clear();
        veinLocations.clear();
        if (restoreBlocks) {
            restoreActiveSchematic();
        }
    }

    private void removeBlacksmithEntities() {
        if (blacksmithNpcUuid != null) {
            Entity e = Bukkit.getEntity(blacksmithNpcUuid);
            if (e != null) {
                e.remove();
            }
            blacksmithNpcUuid = null;
        }
        for (UUID id : hologramEntityUuids) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        hologramEntityUuids.clear();
    }

    public void restoreActiveSchematic() {
        if (!activeBackupFile.exists()) {
            return;
        }
        YamlConfiguration backup = YamlConfiguration.loadConfiguration(activeBackupFile);
        String worldName = backup.getString("world", "world2");
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            List<Map<?, ?>> list = backup.getMapList("blocks");
            for (int i = list.size() - 1; i >= 0; i--) {
                Map<?, ?> entry = list.get(i);
                int x = toInt(entry.get("x"));
                int y = toInt(entry.get("y"));
                int z = toInt(entry.get("z"));
                String data = String.valueOf(entry.get("data"));
                Block block = world.getBlockAt(x, y, z);
                try {
                    block.setBlockData(Bukkit.createBlockData(data), false);
                } catch (IllegalArgumentException ex) {
                    block.setType(Material.AIR, false);
                }
            }
        }
        pastedBlocks.clear();
        activeBackupFile.delete();
    }

    public Location getCenterLocation() {
        return centerLocation;
    }

    public boolean isMineActive() {
        return centerLocation != null && centerLocation.getWorld() == null ? false : centerLocation != null;
    }

    public int getActiveGeodeCount() {
        return activeGeodeBlocks.size();
    }

    public int getSecondsUntilFullRegen() {
        return Math.max(0, secondsUntilFullRegen);
    }

    private int getConfiguredRegenIntervalSeconds() {
        return Math.max(30, plugin.getConfig().getInt("mine.regen-interval-seconds", 180));
    }

    private String blockKey(Location loc) {
        return loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private Attribute resolveScaleAttribute() {
        for (String name : new String[]{"SCALE", "GENERIC_SCALE"}) {
            try {
                return Attribute.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private int toInt(Object obj) {
        if (obj instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
