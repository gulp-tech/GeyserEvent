package ru.geyserevent.excavation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.geyserevent.GeyserEventPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Загрузчик и менеджер схемы ивента «Пляжные Раскопки» (17x17, пальмы, подозрительный песок, пьедестал для Жителя).
 * Полностью откатывает ландшафт после завершения ивента.
 */
public class BeachSchematicManager {

    private record SavedBlock(World world, int x, int y, int z, BlockData originalData) {
    }

    private record SchematicEntry(int dx, int dy, int dz, BlockData blockData) {
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
        File file = new File(plugin.getDataFolder(), "schematics/beach_excavation.schem.yml");
        if (!file.exists()) {
            plugin.saveResource("schematics/beach_excavation.schem.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> blockList = yaml.getMapList("blocks");
        for (Map<?, ?> raw : blockList) {
            int dx = toInt(raw.get("dx"));
            int dy = toInt(raw.get("dy"));
            int dz = toInt(raw.get("dz"));
            String dataStr = String.valueOf(raw.get("data"));
            try {
                BlockData data = Bukkit.createBlockData(dataStr);
                entries.add(new SchematicEntry(dx, dy, dz, data));
            } catch (IllegalArgumentException ex) {
                Material fallback = Material.matchMaterial(dataStr.replace("minecraft:", "").toUpperCase());
                if (fallback != null && fallback.isBlock()) {
                    entries.add(new SchematicEntry(dx, dy, dz, fallback.createBlockData()));
                }
            }
        }
        plugin.getLogger().info("Загружена схема beach_excavation.schem.yml (" + entries.size() + " блоков).");
    }

    /**
     * Вставляет схему пляжа с центром в origin и запоминает блоки для раскопок и последующего отката.
     */
    public void pasteSchematic(Location origin) {
        restoreActiveSchematic();

        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        // Очищаем воздух над пляжем (радиус 8, высота 1..7)
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (dx * dx + dz * dz > 72) {
                    continue;
                }
                for (int dy = 1; dy <= 7; dy++) {
                    Block b = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                    if (!b.getType().isAir()) {
                        recordBlockIfNotSaved(b);
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }

        for (SchematicEntry entry : entries) {
            int bx = ox + entry.dx();
            int by = oy + entry.dy();
            int bz = oz + entry.dz();

            Block block = world.getBlockAt(bx, by, bz);
            recordBlockIfNotSaved(block);
            block.setBlockData(entry.blockData(), false);

            Material type = entry.blockData().getMaterial();
            if (type == Material.SUSPICIOUS_SAND || type == Material.SAND) {
                excavatableBlocks.add(new BlockPosKey(world.getName(), bx, by, bz));
            }
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
     * Временно превращает раскопанный блок песка в песчаник и восстанавливает подозрительный песок через 12 секунд.
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

        block.setType(Material.SANDSTONE, false);

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
