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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Загрузчик и менеджер схемы ивента «Пляжные Раскопки» (17x17 Оазис Раскопок с 4 пальмами,
 * блоками подозрительного песка, археологическим лагерем и центральным пьедесталом для 2м Жителя).
 * Полностью откатывает ландшафт после завершения ивента.
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
        if (!file.exists()) {
            try {
                plugin.saveResource("schematics/beach_excavation.schem.yml", false);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            parseYamlSchematic(yaml);
        }

        if (entries.isEmpty()) {
            buildBuiltInBeachSchematic();
        }

        plugin.getLogger().info("Загружена схема Пляжных Раскопок (" + entries.size() + " блоков).");
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

        // 1. Читаем слои (layers)
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

        // 2. Читаем список детальных блоков (blocks) — поддерживаем x/y/z/material и dx/dy/dz/data
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
     * Встроенная генерация красивого пляжа 17x17 на случай отсутствия или повреждения файла.
     */
    private void buildBuiltInBeachSchematic() {
        Material suspSand = resolveMaterial("SUSPICIOUS_SAND");
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > 52) {
                    continue;
                }
                Material mat = Material.SAND;
                if (dx == 0 && dz == 0) {
                    mat = Material.CHISELED_SANDSTONE;
                } else if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    mat = Material.SMOOTH_SANDSTONE;
                } else if ((Math.abs(dx) + Math.abs(dz)) % 3 == 0 && distSq <= 36) {
                    mat = suspSand;
                } else if (distSq >= 42) {
                    mat = Material.SANDSTONE_SLAB;
                }
                entries.add(new SchematicEntry(dx, 0, dz, mat));
            }
        }
        int[][] palms = {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}};
        for (int[] p : palms) {
            int px = p[0];
            int pz = p[1];
            entries.add(new SchematicEntry(px, 1, pz, Material.JUNGLE_WOOD));
            entries.add(new SchematicEntry(px, 2, pz, Material.JUNGLE_WOOD));
            int tx = px > 0 ? px - 1 : px + 1;
            int tz = pz > 0 ? pz - 1 : pz + 1;
            entries.add(new SchematicEntry(tx, 3, tz, Material.JUNGLE_WOOD));
            entries.add(new SchematicEntry(tx, 4, tz, Material.JUNGLE_WOOD));
            entries.add(new SchematicEntry(tx, 5, tz, Material.JUNGLE_LEAVES));
            entries.add(new SchematicEntry(tx - 1, 5, tz, Material.JUNGLE_LEAVES));
            entries.add(new SchematicEntry(tx + 1, 5, tz, Material.JUNGLE_LEAVES));
            entries.add(new SchematicEntry(tx, 5, tz - 1, Material.JUNGLE_LEAVES));
            entries.add(new SchematicEntry(tx, 5, tz + 1, Material.JUNGLE_LEAVES));
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
     * Вставляет схему пляжа с центром в origin, возвращает точную локацию спавна 2-метрового Жителя.
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

        // Если ивент запущен через /rift start-here, ноги игрока находятся в блоке воздуха (на 1 блок выше земли) —
        // опускаем уровень пляжа (oy) до твёрдого блока земли, чтобы пляж лёг ровно в землю, а не висел в воздухе.
        Block originBlock = world.getBlockAt(ox, oy, oz);
        if (originBlock.getType().isAir() && !world.getBlockAt(ox, oy - 1, oz).getType().isAir()) {
            oy = oy - 1;
        }

        // 1. Очищаем пространство над пляжем (радиус 8, высота -1..7) и сохраняем исходные блоки для отката
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (dx * dx + dz * dz > 64) {
                    continue;
                }
                for (int dy = -1; dy <= 7; dy++) {
                    Block b = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                    recordBlockIfNotSaved(b);
                    if (dy >= 1 && !b.getType().isAir()) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }

        List<SchematicEntry> deferredDecorations = new ArrayList<>();

        // 2. Укладываем песчаниковую подушку (dy = -1) под блоками пола пляжа (dy = 0),
        // чтобы песок и подозрительный песок никогда не проваливались вниз на склонах или над водой!
        for (SchematicEntry entry : entries) {
            if (entry.dy() == 0) {
                Block under = world.getBlockAt(ox + entry.dx(), oy - 1, oz + entry.dz());
                recordBlockIfNotSaved(under);
                under.setType(Material.SANDSTONE, false);
            }
        }

        // 3. Строим основные блоки пляжа и пальм
        for (SchematicEntry entry : entries) {
            Material mat = entry.material();
            if (mat == Material.LANTERN || mat == Material.SOUL_LANTERN || mat == Material.TORCH) {
                deferredDecorations.add(entry);
                continue;
            }
            placeSchematicBlock(world, ox + entry.dx(), oy + entry.dy(), oz + entry.dz(), mat);
        }

        // 4. Ставим фонари и декор поверх построенных опор
        for (SchematicEntry entry : deferredDecorations) {
            placeSchematicBlock(world, ox + entry.dx(), oy + entry.dy(), oz + entry.dz(), entry.material());
        }

        // Возвращаем точку спавна 2-метрового Жителя прямо на центральном пьедестале (0, +1, 0)
        return new Location(world, ox + 0.5, oy + 1.0, oz + 0.5, 0.0f, 0.0f);
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
     * Временно превращает раскопанный блок песка в гладкий песчаник и восстанавливает подозрительный песок через 12 секунд.
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
        }, 20L * 12L);
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
