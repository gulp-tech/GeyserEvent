package ru.geyserevent.schematic;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.geyserevent.GeyserEventPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Менеджер схематики «Адский Разлом».
 * Загружает и строит портал Разлома без физики распада портала,
 * защищает игроков от телепортации в Ад и полностью откатывает ландшафт после ивента.
 */
public class RiftSchematicManager {

    private final GeyserEventPlugin plugin;

    /**
     * Сохранённые состояния исходных блоков мира для полного отката после ивента.
     */
    private final List<SavedBlock> savedBlocks = new ArrayList<>();

    /**
     * Координаты всех блоков активной схематики и блоков портала (ключ: "world:x:y:z").
     */
    private final Set<String> schematicBlockKeys = new HashSet<>();
    private final Set<String> portalBlockKeys = new HashSet<>();

    private Location activeCenter;
    private Location bossSpawnOffset = new Location(null, 0.5, 1.0, 2.5, 0.0f, 0.0f);

    public record RelativeBlock(int dx, int dy, int dz, Material material) {
    }

    private record SavedBlock(World world, int x, int y, int z, BlockData blockData) {
    }

    private final List<RelativeBlock> loadedBlocks = new ArrayList<>();

    public RiftSchematicManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        loadSchematicFile();
    }

    /**
     * Загружает файл схематики из plugins/GeyserEvent/schematics/infernal_rift.schem.yml.
     */
    public final void loadSchematicFile() {
        loadedBlocks.clear();
        File schemDir = new File(plugin.getDataFolder(), "schematics");
        if (!schemDir.exists()) {
            schemDir.mkdirs();
        }

        String fileName = plugin.getPluginConfig().getSchematicFile();
        File schemFile = new File(schemDir, fileName);
        if (!schemFile.exists()) {
            try {
                plugin.saveResource("schematics/" + fileName, false);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (schemFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(schemFile);
            parseYamlSchematic(yaml);
        }

        if (loadedBlocks.isEmpty()) {
            buildBuiltInFallbackSchematic();
        }

        plugin.getLogger().info("Загружена схематика Адского Разлома (" + loadedBlocks.size() + " блоков).");
    }

    private void parseYamlSchematic(YamlConfiguration yaml) {
        double offX = yaml.getDouble("boss-spawn-offset.x", 0.5);
        double offY = yaml.getDouble("boss-spawn-offset.y", 1.0);
        double offZ = yaml.getDouble("boss-spawn-offset.z", 2.5);
        float yaw = (float) yaml.getDouble("boss-spawn-offset.yaw", 0.0);
        float pitch = (float) yaml.getDouble("boss-spawn-offset.pitch", 0.0);
        this.bossSpawnOffset = new Location(null, offX, offY, offZ, yaw, pitch);

        Map<Character, Material> palette = new HashMap<>();
        ConfigurationSection paletteSec = yaml.getConfigurationSection("palette");
        if (paletteSec != null) {
            for (String key : paletteSec.getKeys(false)) {
                if (!key.isEmpty()) {
                    Material mat = Material.matchMaterial(paletteSec.getString(key, "AIR"));
                    if (mat != null) {
                        palette.put(key.charAt(0), mat);
                    }
                }
            }
        }

        Map<String, RelativeBlock> blockMap = new LinkedHashMap<>();

        // 1. Читаем послойную матрицу (layers)
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
                        if (mat != null && mat != Material.AIR) {
                            int dx = c - halfX;
                            blockMap.put(dx + ":" + dy + ":" + dz, new RelativeBlock(dx, dy, dz, mat));
                        }
                    }
                }
            }
        }

        // 2. Читаем точечные блоки (blocks)
        List<Map<?, ?>> blockList = yaml.getMapList("blocks");
        for (Map<?, ?> entry : blockList) {
            int dx = toInt(entry.get("x"), 0);
            int dy = toInt(entry.get("y"), 0);
            int dz = toInt(entry.get("z"), 0);
            String matName = String.valueOf(entry.get("material"));
            Material mat = Material.matchMaterial(matName);
            if (mat != null) {
                blockMap.put(dx + ":" + dy + ":" + dz, new RelativeBlock(dx, dy, dz, mat));
            }
        }

        loadedBlocks.addAll(blockMap.values());
    }

    private int toInt(Object obj, int def) {
        if (obj instanceof Number num) {
            return num.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception ex) {
            return def;
        }
    }

    /**
     * Встроенная процедурная схематика на случай, если файл схематики был повреждён.
     */
    private void buildBuiltInFallbackSchematic() {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz <= 20) {
                    Material floor = (dx == 0 && dz == 2) ? Material.LODESTONE
                            : ((Math.abs(dx) + Math.abs(dz)) % 3 == 0 ? Material.CRYING_OBSIDIAN
                            : ((Math.abs(dx) + Math.abs(dz)) % 2 == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.MAGMA_BLOCK));
                    loadedBlocks.add(new RelativeBlock(dx, 0, dz, floor));
                }
            }
        }
        // Рамка портала и полотно NETHER_PORTAL
        for (int dx = -2; dx <= 2; dx++) {
            loadedBlocks.add(new RelativeBlock(dx, 1, -2, Material.OBSIDIAN));
            loadedBlocks.add(new RelativeBlock(dx, 6, -2, Material.CRYING_OBSIDIAN));
        }
        for (int dy = 2; dy <= 5; dy++) {
            loadedBlocks.add(new RelativeBlock(-2, dy, -2, Material.CRYING_OBSIDIAN));
            loadedBlocks.add(new RelativeBlock(2, dy, -2, Material.CRYING_OBSIDIAN));
            for (int dx = -1; dx <= 1; dx++) {
                loadedBlocks.add(new RelativeBlock(dx, dy, -2, Material.NETHER_PORTAL));
            }
        }
    }

    /**
     * Размещает схематику Адского Разлома в мире и сохраняет затронутые блоки для последующего отката.
     */
    public Location pasteSchematic(Location center) {
        restoreSchematic();

        World world = center.getWorld();
        if (world == null) {
            return center;
        }

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        this.activeCenter = new Location(world, cx + 0.5, cy, cz + 0.5);

        Set<String> capturedCoords = new HashSet<>();

        // Сначала очищаем пространство арены (радиус 5, высота 1..8) и запоминаем блоки
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz > 28) {
                    continue;
                }
                for (int dy = 0; dy <= 8; dy++) {
                    int bx = cx + dx;
                    int by = cy + dy;
                    int bz = cz + dz;
                    String key = blockKey(world.getName(), bx, by, bz);
                    Block block = world.getBlockAt(bx, by, bz);
                    if (capturedCoords.add(key)) {
                        savedBlocks.add(new SavedBlock(world, bx, by, bz, block.getBlockData().clone()));
                    }
                    schematicBlockKeys.add(key);
                    if (dy >= 1 && !block.getType().isAir()) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }

        // Размещаем все блоки схематики (сначала твёрдые блоки, затем огонь и портал без физики)
        List<RelativeBlock> deferredPortalAndFire = new ArrayList<>();

        for (RelativeBlock rel : loadedBlocks) {
            if (rel.material() == Material.NETHER_PORTAL
                    || rel.material() == Material.SOUL_FIRE
                    || rel.material() == Material.FIRE
                    || rel.material() == Material.SOUL_LANTERN) {
                deferredPortalAndFire.add(rel);
                continue;
            }
            placeSingleBlock(world, cx + rel.dx(), cy + rel.dy(), cz + rel.dz(), rel.material(), capturedCoords);
        }

        for (RelativeBlock rel : deferredPortalAndFire) {
            placeSingleBlock(world, cx + rel.dx(), cy + rel.dy(), cz + rel.dz(), rel.material(), capturedCoords);
        }

        return getBossSpawnLocation();
    }

    private void placeSingleBlock(World world, int bx, int by, int bz, Material material, Set<String> capturedCoords) {
        String key = blockKey(world.getName(), bx, by, bz);
        Block block = world.getBlockAt(bx, by, bz);
        if (capturedCoords.add(key)) {
            savedBlocks.add(new SavedBlock(world, bx, by, bz, block.getBlockData().clone()));
        }
        schematicBlockKeys.add(key);

        if (material == Material.SOUL_FIRE && block.getRelative(0, -1, 0).getType().isAir()) {
            return;
        }

        // Важно: applyPhysics = false предотвращает разрушение блоков NETHER_PORTAL
        block.setType(material, false);

        if (material == Material.NETHER_PORTAL) {
            portalBlockKeys.add(key);
            BlockData data = block.getBlockData();
            if (data instanceof Orientable orientable) {
                orientable.setAxis(Axis.X);
                block.setBlockData(orientable, false);
            }
        }
    }

    /**
     * Полностью восстанавливает исходные блоки мира после окончания ивента.
     */
    public void restoreSchematic() {
        if (!savedBlocks.isEmpty() && plugin.getPluginConfig().isRestoreBlocksOnEnd()) {
            // Сначала убираем порталы и огонь, чтобы они не вызвали обновление соседних блоков
            for (SavedBlock saved : savedBlocks) {
                Block b = saved.world().getBlockAt(saved.x(), saved.y(), saved.z());
                if (b.getType() == Material.NETHER_PORTAL
                        || b.getType() == Material.SOUL_FIRE
                        || b.getType() == Material.FIRE) {
                    b.setType(Material.AIR, false);
                }
            }
            // Восстанавливаем в обратном порядке
            List<SavedBlock> reverse = new ArrayList<>(savedBlocks);
            Collections.reverse(reverse);
            for (SavedBlock saved : reverse) {
                Block b = saved.world().getBlockAt(saved.x(), saved.y(), saved.z());
                b.setBlockData(saved.blockData(), false);
            }
        }
        savedBlocks.clear();
        schematicBlockKeys.clear();
        portalBlockKeys.clear();
        activeCenter = null;
    }

    /**
     * Возвращает точку спавна Босса перед вратами Адского Разлома.
     */
    public Location getBossSpawnLocation() {
        if (activeCenter == null) {
            return null;
        }
        return activeCenter.clone().add(
                bossSpawnOffset.getX(),
                bossSpawnOffset.getY(),
                bossSpawnOffset.getZ()
        );
    }

    /**
     * Проверяет, принадлежит ли блок или локация порталу/зоне Адского Разлома
     * (чтобы заблокировать телепортацию игроков и мобов через портал).
     */
    public boolean isRiftPortalOrZone(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        String key = blockKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (portalBlockKeys.contains(key) || schematicBlockKeys.contains(key)) {
            return true;
        }
        if (activeCenter != null && activeCenter.getWorld().equals(loc.getWorld())) {
            int radius = plugin.getPluginConfig().getProtectionRadius() + 4;
            return activeCenter.distanceSquared(loc) <= (double) (radius * radius);
        }
        return false;
    }

    /**
     * Проверяет, находится ли блок под защитой схематики Разлома от разрушения/строительства.
     */
    public boolean isProtectedBlock(Location loc) {
        if (!plugin.getPluginConfig().isProtectRegion() || loc == null || loc.getWorld() == null) {
            return false;
        }
        String key = blockKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (schematicBlockKeys.contains(key)) {
            return true;
        }
        if (activeCenter != null && activeCenter.getWorld().equals(loc.getWorld())) {
            int r = plugin.getPluginConfig().getProtectionRadius();
            double dx = Math.abs(loc.getX() - activeCenter.getX());
            double dy = Math.abs(loc.getY() - activeCenter.getY());
            double dz = Math.abs(loc.getZ() - activeCenter.getZ());
            return dx <= r && dz <= r && dy <= 12;
        }
        return false;
    }

    /**
     * Сохраняет постройку вокруг администратора в файл схематики infernal_rift.schem.yml.
     */
    public int saveCustomSchematicFromWorld(Location center, int radius) throws IOException {
        World world = center.getWorld();
        if (world == null) {
            return 0;
        }
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        List<Map<String, Object>> serializedBlocks = new ArrayList<>();
        loadedBlocks.clear();

        for (int dy = -1; dy <= radius + 3; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    Material type = block.getType();
                    if (type.isAir()) {
                        continue;
                    }
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("x", dx);
                    entry.put("y", dy);
                    entry.put("z", dz);
                    entry.put("material", type.name());
                    serializedBlocks.add(entry);
                    loadedBlocks.add(new RelativeBlock(dx, dy, dz, type));
                }
            }
        }

        File schemDir = new File(plugin.getDataFolder(), "schematics");
        if (!schemDir.exists()) {
            schemDir.mkdirs();
        }
        File schemFile = new File(schemDir, plugin.getPluginConfig().getSchematicFile());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", "infernal_rift_custom");
        yaml.set("boss-spawn-offset.x", 0.5);
        yaml.set("boss-spawn-offset.y", 1.0);
        yaml.set("boss-spawn-offset.z", 2.5);
        yaml.set("blocks", serializedBlocks);
        yaml.save(schemFile);

        return serializedBlocks.size();
    }

    public Location getActiveCenter() {
        return activeCenter;
    }

    private String blockKey(String worldName, int x, int y, int z) {
        return worldName + ":" + x + ":" + y + ":" + z;
    }
}
