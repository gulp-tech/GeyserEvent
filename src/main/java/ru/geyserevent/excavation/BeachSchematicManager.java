package ru.geyserevent.excavation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.geyserevent.GeyserEventPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Загрузчик и генератор большой схемы ивента «Пляжные Раскопки» (31x31, радиус 15 блоков):
 * - 8 тропических пальм с кокосами (SHROOMLIGHT) и неопадающей листвой
 * - 4 археологических сектора раскопок с бочками, фонарями и более чем 110 блоками подозрительного песка
 * - Центральный пьедестал для 2-метрового Жителя-Археолога
 * - Двухслойный песчаниковый фундамент (песок никогда не проваливается в воду или на склонах)
 * - Полный 100% откат ландшафта после окончания ивента.
 */
public class BeachSchematicManager {

    private record SavedBlock(World world, int x, int y, int z, BlockData originalData) {
    }

    private record SchematicEntry(int dx, int dy, int dz, Material material) {
    }

    private record BlockPosKey(String worldName, int x, int y, int z) {
    }

    private final GeyserEventPlugin plugin;
    private final List<SchematicEntry> entries = new ArrayList<>();
    private final List<SavedBlock> activePasteHistory = new ArrayList<>();
    private final Set<BlockPosKey> activeArenaBlocks = new HashSet<>();
    private final Set<BlockPosKey> excavatableBlocks = new HashSet<>();

    public BeachSchematicManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        loadSchematic();
    }

    public final void loadSchematic() {
        entries.clear();
        File schemDir = new File(plugin.getDataFolder(), "schematics");
        if (!schemDir.exists()) {
            schemDir.mkdirs();
        }

        File file = new File(schemDir, "beach_excavation.schem.yml");
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (yaml.getInt("schema-version", 1) >= 2) {
                parseYamlSchematic(yaml);
            }
        }

        // Если схема старая (маленькая) или отсутствует — генерируем большую 31x31 схему и сохраняем её в файл
        if (entries.size() < 400) {
            entries.clear();
            buildLarge31x31BeachSchematic();
            saveGeneratedSchematicToFile(file);
        }

        plugin.getLogger().info("Загружена большая схема Пляжных Раскопок 31x31 (" + entries.size() + " блоков).");
    }

    private void parseYamlSchematic(YamlConfiguration yaml) {
        Map<Character, Material> palette = new HashMap<>();
        ConfigurationSection paletteSec = yaml.getConfigurationSection("palette");
        if (paletteSec != null) {
            for (String key : paletteSec.getKeys(false)) {
                if (!key.isEmpty()) {
                    Material mat = resolveMaterial(paletteSec.getString(key, "AIR"));
                    if (mat != null) {
                        palette.put(key.charAt(0), mat);
                    }
                }
            }
        }

        Map<String, SchematicEntry> blockMap = new LinkedHashMap<>();

        ConfigurationSection layersSec = yaml.getConfigurationSection("layers");
        if (layersSec != null) {
            for (String yKey : layersSec.getKeys(false)) {
                int dy;
                try {
                    dy = Integer.parseInt(yKey);
                } catch (NumberFormatException ex) {
                    continue;
                }
                List<String> rows = layersSec.getStringList(yKey);
                int halfZ = rows.size() / 2;
                for (int r = 0; r < rows.size(); r++) {
                    String compact = rows.get(r).replace(" ", "");
                    int halfX = compact.length() / 2;
                    int dz = r - halfZ;
                    for (int c = 0; c < compact.length(); c++) {
                        char symbol = compact.charAt(c);
                        if (symbol == '.') {
                            continue;
                        }
                        Material mat = palette.get(symbol);
                        if (mat != null && !mat.isAir()) {
                            int dx = c - halfX;
                            blockMap.put(dx + ":" + dy + ":" + dz, new SchematicEntry(dx, dy, dz, mat));
                        }
                    }
                }
            }
        }

        List<Map<?, ?>> blockList = yaml.getMapList("blocks");
        for (Map<?, ?> raw : blockList) {
            int dx = raw.containsKey("x") ? toInt(raw.get("x")) : toInt(raw.get("dx"));
            int dy = raw.containsKey("y") ? toInt(raw.get("y")) : toInt(raw.get("dy"));
            int dz = raw.containsKey("z") ? toInt(raw.get("z")) : toInt(raw.get("dz"));
            String matStr = raw.containsKey("material")
                    ? String.valueOf(raw.get("material"))
                    : String.valueOf(raw.get("data"));
            Material mat = resolveMaterial(matStr);
            if (mat != null && !mat.isAir()) {
                blockMap.put(dx + ":" + dy + ":" + dz, new SchematicEntry(dx, dy, dz, mat));
            }
        }

        entries.addAll(blockMap.values());
    }

    /**
     * Генерирует большую детализированную схему пляжа 31x31 (радиус 15 блоков):
     * - Песчаный остров с гладкими дорожками, резным песчаником и более чем 110 блоками SUSPICIOUS_SAND
     * - Центральный археологический алтарь для 2-метрового Жителя
     * - 4 угловых лагеря раскопок с бочками, заборами и фонарями
     * - 8 высоких тропических пальм (4 внутренних и 4 внешних) с пышной кроной
     */
    private void buildLarge31x31BeachSchematic() {
        Map<String, SchematicEntry> map = new LinkedHashMap<>();
        Material suspSand = resolveMaterial("SUSPICIOUS_SAND");

        // 1. Основное плато пляжа 31x31 (радиус 15, y = 0)
        for (int dx = -15; dx <= 15; dx++) {
            for (int dz = -15; dz <= 15; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > 225) {
                    continue;
                }

                Material floorMat;
                int absX = Math.abs(dx);
                int absZ = Math.abs(dz);

                if (dx == 0 && dz == 0) {
                    // Центр под пьедесталом
                    floorMat = Material.GOLD_BLOCK;
                } else if (absX <= 1 && absZ <= 1) {
                    // Основание центрального подиума
                    floorMat = Material.CHISELED_SANDSTONE;
                } else if (absX <= 2 && absZ <= 2 && (absX + absZ < 4)) {
                    floorMat = Material.SMOOTH_SANDSTONE;
                } else if ((absX <= 1 && absZ <= 12) || (absZ <= 1 && absX <= 12)) {
                    // 4 главные крестообразные дорожки к центру пляжа
                    if ((absX == 0 || absZ == 0) && (absX + absZ) % 3 == 0) {
                        floorMat = Material.CHISELED_SANDSTONE;
                    } else if (absX == 0 || absZ == 0) {
                        floorMat = Material.SMOOTH_SANDSTONE;
                    } else {
                        floorMat = ((absX + absZ) % 2 == 0) ? suspSand : Material.CUT_SANDSTONE;
                    }
                } else if (distSq >= 190) {
                    // Красивая внешняя кромка пляжа
                    floorMat = ((absX + absZ) % 3 == 0) ? Material.SMOOTH_SANDSTONE : Material.SAND;
                } else {
                    // Основные песчаные зоны раскопок с большим количеством подозрительного песка
                    boolean isSuspicious = ((absX * 3 + absZ * 5) % 4 == 0)
                            || ((absX + absZ) % 3 == 0 && distSq <= 165);
                    floorMat = isSuspicious ? suspSand : Material.SAND;
                }

                putBlock(map, dx, 0, dz, floorMat);
            }
        }

        // 2. Центральный возвышенный пьедестал для 2-метрового Жителя-Археолога (y = 1)
        putBlock(map, 0, 1, 0, Material.CHISELED_SANDSTONE);
        putBlock(map, -1, 1, 0, Material.SANDSTONE_SLAB);
        putBlock(map, 1, 1, 0, Material.SANDSTONE_SLAB);
        putBlock(map, 0, 1, -1, Material.SANDSTONE_SLAB);
        putBlock(map, 0, 1, 1, Material.SANDSTONE_SLAB);

        // Декоративные колонны со светильниками вокруг центра (на радиусе 3)
        int[][] centerPillars = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] p : centerPillars) {
            putBlock(map, p[0], 0, p[1], Material.CHISELED_SANDSTONE);
            putBlock(map, p[0], 1, p[1], Material.CUT_SANDSTONE);
            putBlock(map, p[0], 2, p[1], Material.OAK_FENCE);
            putBlock(map, p[0], 3, p[1], Material.LANTERN);
        }

        // 3. Четыре археологических лагеря по сторонам света (бочки, резной песчаник, фонари и залежи SUSPICIOUS_SAND)
        int[][] camps = {{-8, 0}, {8, 0}, {0, -8}, {0, 8}};
        for (int[] camp : camps) {
            int cx = camp[0];
            int cz = camp[1];
            // По бокам от дорожки ставим бочки и стойки с фонарями
            if (cx != 0) {
                putBlock(map, cx, 1, -2, Material.BARREL);
                putBlock(map, cx, 2, -2, Material.LANTERN);
                putBlock(map, cx, 1, 2, Material.BARREL);
                putBlock(map, cx, 2, 2, Material.LANTERN);
            } else {
                putBlock(map, -2, 1, cz, Material.BARREL);
                putBlock(map, -2, 2, cz, Material.LANTERN);
                putBlock(map, 2, 1, cz, Material.BARREL);
                putBlock(map, 2, 2, cz, Material.LANTERN);
            }
        }

        // 4. Четыре диагональные археологические раскопочные ямы/насыпи с богатым подозрительным песком
        int[][] digSites = {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}};
        for (int[] site : digSites) {
            int sx = site[0];
            int sz = site[1];
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    if (Math.abs(ox) <= 1 && Math.abs(oz) <= 1) {
                        putBlock(map, sx + ox, 0, sz + oz, suspSand);
                    }
                }
            }
            putBlock(map, sx, 1, sz, suspSand);
        }

        // 5. Восемь больших тропических пальм:
        // 4 внутренние пальмы (высота 6 блоков) и 4 внешние пальмы (высота 7 блоков)
        buildPalmTree(map, -5, -9, 6, 1, 1);
        buildPalmTree(map, 5, -9, 6, -1, 1);
        buildPalmTree(map, -5, 9, 6, 1, -1);
        buildPalmTree(map, 5, 9, 6, -1, -1);

        buildPalmTree(map, -10, -5, 7, 1, 1);
        buildPalmTree(map, 10, -5, 7, -1, 1);
        buildPalmTree(map, -10, 5, 7, 1, -1);
        buildPalmTree(map, 10, 5, 7, -1, -1);

        // 6. Сторожевые факелы/фонари по периметру пляжа (радиус 13)
        int[][] outerLanterns = {
                {-13, 0}, {13, 0}, {0, -13}, {0, 13},
                {-9, -9}, {9, -9}, {-9, 9}, {9, 9}
        };
        for (int[] l : outerLanterns) {
            putBlock(map, l[0], 0, l[1], Material.SMOOTH_SANDSTONE);
            putBlock(map, l[0], 1, l[1], Material.CHISELED_SANDSTONE);
            putBlock(map, l[0], 2, l[1], Material.OAK_FENCE);
            putBlock(map, l[0], 3, l[1], Material.LANTERN);
        }

        entries.addAll(map.values());
    }

    /**
     * Строит реалистичную изогнутую тропическую пальму с кокосами (SHROOMLIGHT) и раскидистой кроной.
     */
    private void buildPalmTree(Map<String, SchematicEntry> map, int baseX, int baseZ, int height, int bendX, int bendZ) {
        putBlock(map, baseX, 0, baseZ, Material.MOSSY_COBBLESTONE);
        int curX = baseX;
        int curZ = baseZ;

        for (int y = 1; y <= height; y++) {
            if (y == 3 || y == 5) {
                curX += bendX;
                curZ += bendZ;
            }
            putBlock(map, curX, y, curZ, Material.JUNGLE_WOOD);
        }

        int topY = height + 1;
        // Верхушка кроны
        putBlock(map, curX, topY, curZ, Material.JUNGLE_LEAVES);
        putBlock(map, curX + 1, topY, curZ, Material.JUNGLE_LEAVES);
        putBlock(map, curX - 1, topY, curZ, Material.JUNGLE_LEAVES);
        putBlock(map, curX, topY, curZ + 1, Material.JUNGLE_LEAVES);
        putBlock(map, curX, topY, curZ - 1, Material.JUNGLE_LEAVES);

        // Светящиеся кокосы под кроной
        putBlock(map, curX + 1, height - 1, curZ, Material.SHROOMLIGHT);
        putBlock(map, curX - 1, height - 1, curZ, Material.SHROOMLIGHT);

        // Длинные пальмовые ветви на 4 стороны и по диагоналям
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int[] d : dirs) {
            int dx = d[0];
            int dz = d[1];
            boolean diagonal = (dx != 0 && dz != 0);
            int maxLen = diagonal ? 2 : 3;
            for (int step = 1; step <= maxLen; step++) {
                int ly = (step == maxLen) ? height - 1 : height;
                putBlock(map, curX + dx * step, ly, curZ + dz * step, Material.JUNGLE_LEAVES);
            }
        }
    }

    private void putBlock(Map<String, SchematicEntry> map, int dx, int dy, int dz, Material mat) {
        if (mat != null && !mat.isAir()) {
            map.put(dx + ":" + dy + ":" + dz, new SchematicEntry(dx, dy, dz, mat));
        }
    }

    private void saveGeneratedSchematicToFile(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("name", "beach_excavation_31x31");
        List<Map<String, Object>> blockList = new ArrayList<>();
        for (SchematicEntry entry : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("x", entry.dx());
            m.put("y", entry.dy());
            m.put("z", entry.dz());
            m.put("material", entry.material().name());
            blockList.add(m);
        }
        yaml.set("blocks", blockList);
        try {
            yaml.save(file);
        } catch (IOException ignored) {
        }
    }

    private Material resolveMaterial(String name) {
        if (name == null) {
            return Material.SAND;
        }
        String clean = name.replace("minecraft:", "").trim().toUpperCase();
        Material mat = Material.matchMaterial(clean);
        if (mat != null && mat.isBlock()) {
            return mat;
        }
        if ("SUSPICIOUS_SAND".equals(clean)) {
            return Material.SAND;
        }
        return null;
    }

    /**
     * Вставляет большую схему пляжа 31x31 с центром в origin и возвращает точную локацию спавна 2-метрового Жителя.
     */
    public Location pasteSchematic(Location origin) {
        restoreActiveSchematic();

        World world = origin.getWorld();
        if (world == null) {
            return origin;
        }

        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        // Опускаем уровень до твёрдой поверхности, если команда вызвана в воздухе над блоком
        Block originBlock = world.getBlockAt(ox, oy, oz);
        if (originBlock.getType().isAir() && !world.getBlockAt(ox, oy - 1, oz).getType().isAir()) {
            oy = oy - 1;
        }

        // 1. Очищаем пространство над пляжем 31x31 (радиус 15, высота -2..10) и сохраняем все исходные блоки для отката
        for (int dx = -15; dx <= 15; dx++) {
            for (int dz = -15; dz <= 15; dz++) {
                if (dx * dx + dz * dz > 225) {
                    continue;
                }
                for (int dy = -2; dy <= 10; dy++) {
                    Block b = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                    recordBlockIfNotSaved(b);
                    if (dy >= 1 && !b.getType().isAir()) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }

        List<SchematicEntry> deferredDecorations = new ArrayList<>();

        // 2. Укладываем прочную двухслойную песчаниковую подушку (dy = -1 и dy = -2) под всем пляжем,
        // чтобы песок и подозрительный песок никогда не проваливались вниз и пляж красиво смотрелся на любом рельефе!
        for (SchematicEntry entry : entries) {
            if (entry.dy() == 0) {
                Block under1 = world.getBlockAt(ox + entry.dx(), oy - 1, oz + entry.dz());
                recordBlockIfNotSaved(under1);
                under1.setType(Material.SANDSTONE, false);

                Block under2 = world.getBlockAt(ox + entry.dx(), oy - 2, oz + entry.dz());
                recordBlockIfNotSaved(under2);
                under2.setType(Material.SANDSTONE, false);
            }
        }

        // 3. Строим основные блоки пляжа, лагерей и пальм
        for (SchematicEntry entry : entries) {
            Material mat = entry.material();
            if (mat == Material.LANTERN || mat == Material.SOUL_LANTERN || mat == Material.TORCH) {
                deferredDecorations.add(entry);
                continue;
            }
            placeSchematicBlock(world, ox + entry.dx(), oy + entry.dy(), oz + entry.dz(), mat);
        }

        // 4. Ставим фонари поверх стоек и бочек
        for (SchematicEntry entry : deferredDecorations) {
            placeSchematicBlock(world, ox + entry.dx(), oy + entry.dy(), oz + entry.dz(), entry.material());
        }

        // Центральный пьедестал находится на (0, +1, 0), поэтому 2-метровый Житель стоит ровно на нём на высоте oy + 2.0
        return new Location(world, ox + 0.5, oy + 2.0, oz + 0.5, 0.0f, 0.0f);
    }

    private void placeSchematicBlock(World world, int bx, int by, int bz, Material mat) {
        Block block = world.getBlockAt(bx, by, bz);
        recordBlockIfNotSaved(block);
        block.setType(mat, false);

        if (block.getBlockData() instanceof Leaves leaves) {
            leaves.setPersistent(true);
            block.setBlockData(leaves, false);
        }

        if (mat == Material.SUSPICIOUS_SAND || mat == Material.SAND) {
            excavatableBlocks.add(new BlockPosKey(world.getName(), bx, by, bz));
        }
    }

    private void recordBlockIfNotSaved(Block block) {
        BlockPosKey key = new BlockPosKey(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()
        );
        if (activeArenaBlocks.add(key)) {
            activePasteHistory.add(new SavedBlock(
                    block.getWorld(),
                    block.getX(),
                    block.getY(),
                    block.getZ(),
                    block.getBlockData().clone()
            ));
        }
    }

    public boolean isProtectedArenaBlock(Block block) {
        if (block == null || activeArenaBlocks.isEmpty()) {
            return false;
        }
        return activeArenaBlocks.contains(new BlockPosKey(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()
        ));
    }

    public boolean isExcavatableBeachSand(Block block) {
        if (block == null || excavatableBlocks.isEmpty()) {
            return false;
        }
        Material mat = block.getType();
        if (mat != Material.SUSPICIOUS_SAND && mat != Material.SAND) {
            return false;
        }
        return excavatableBlocks.contains(new BlockPosKey(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()
        ));
    }

    /**
     * Временно превращает раскопанный блок песка в гладкий песчаник и восстанавливает подозрительный песок через 10 секунд.
     */
    public void markBlockExcavatedAndScheduleRespawn(Block block) {
        if (block == null) {
            return;
        }
        World world = block.getWorld();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        BlockPosKey key = new BlockPosKey(world.getName(), x, y, z);

        block.setType(Material.SMOOTH_SANDSTONE, false);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!excavatableBlocks.contains(key)) {
                return;
            }
            Block target = world.getBlockAt(x, y, z);
            Material suspSand = Material.matchMaterial("SUSPICIOUS_SAND");
            target.setType(suspSand != null ? suspSand : Material.SAND, false);
        }, 20L * 10L);
    }

    /**
     * Полностью возвращает все блоки пляжа в исходное состояние.
     */
    public void restoreActiveSchematic() {
        if (activePasteHistory.isEmpty()) {
            activeArenaBlocks.clear();
            excavatableBlocks.clear();
            return;
        }

        for (int i = activePasteHistory.size() - 1; i >= 0; i--) {
            SavedBlock saved = activePasteHistory.get(i);
            Block block = saved.world().getBlockAt(saved.x(), saved.y(), saved.z());
            block.setBlockData(saved.originalData(), false);
        }
        activePasteHistory.clear();
        activeArenaBlocks.clear();
        excavatableBlocks.clear();
    }

    private int toInt(Object val) {
        if (val instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
