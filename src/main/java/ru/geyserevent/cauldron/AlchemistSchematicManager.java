package ru.geyserevent.cauldron;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.excavation.BrushTier;

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
 * Генератор и менеджер улучшенной большой схемы 31x31 для ивента «Котёл Алхимика» (schema-version: 2):
 * - Центральная Готическая Ротонда (высота 8 блоков) с куполом, арками, цепями и Гигантским Бурлящим Котлом
 * - 4 Входных Готических Портала/Арки по сторонам света и декоративная ограда по границе зоны ивента (радиус 15)
 * - 4 Алхимических Павильона (крытые лавки с книжными полками, зельеварками, бочками и кристаллами)
 * - 4 светящихся фиолетовых алхимических ручья с жабосветами под стеклом
 * - 4 гигантских волшебных гриба и 4 плакучие мангровые ивы
 * - 16 резных алхимических алтарей-пьедесталов, на которых вырастают Реагенты 3 редкостей
 * - Оптимизированные частицы без лагов на слабых телефонах и полный 100% откат ландшафта.
 */
public class AlchemistSchematicManager {

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

    /**
     * Точки 16 алхимических пьедесталов (координата dy = 1 над плачущим обсидианом),
     * где во время ивента вырастают нестабильные реагенты.
     */
    private final List<Location> reagentPedestalLocations = new ArrayList<>();

    /**
     * Активные выросшие реагенты на пьедесталах: ключ позиции (dy = 1) -> редкость реагента.
     */
    private final Map<BlockPosKey, BrushTier> activeReagentNodes = new HashMap<>();

    /**
     * Центральные котлы вокруг Алхимика, по которым тоже можно кликнуть для сдачи реагента.
     */
    private final Set<BlockPosKey> centralCauldronBlocks = new HashSet<>();

    private Location activeCenterOrigin;

    private static final int[][] PEDESTAL_OFFSETS = {
            {-5, -5}, {5, -5}, {-5, 5}, {5, 5},
            {-8, -3}, {8, -3}, {-8, 3}, {8, 3},
            {-3, -8}, {3, -8}, {-3, 8}, {3, 8},
            {-11, -2}, {11, -2}, {-2, -11}, {2, 11}
    };

    public AlchemistSchematicManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        loadSchematic();
    }

    public final void loadSchematic() {
        entries.clear();
        File schemDir = new File(plugin.getDataFolder(), "schematics");
        if (!schemDir.exists()) {
            schemDir.mkdirs();
        }

        File file = new File(schemDir, "alchemist_cauldron.schem.yml");
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (yaml.getInt("schema-version", 1) >= 2) {
                parseYamlSchematic(yaml);
            }
        }

        if (entries.size() < 600) {
            entries.clear();
            buildLarge31x31AlchemistSwampSchematic();
            saveGeneratedSchematicToFile(file);
        }

        plugin.getLogger().info("Загружена улучшенная схема Котла Алхимика 31x31 (" + entries.size() + " блоков).");
    }

    private void parseYamlSchematic(YamlConfiguration yaml) {
        Map<String, SchematicEntry> blockMap = new LinkedHashMap<>();
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
     * Генерирует улучшенную архитектурную схему 31x31 «Котёл Алхимика» (schema-version: 2):
     * - Центральная Готическая Ротонда с купольным шпилем и бурлящим котлом
     * - 4 крытых Алхимических Павильона с книжными шкафами, кафедрами, бочками и зельеварками
     * - 4 Входные Готические Арки на границе зоны (N, S, E, W) + ограда по периметру
     * - 4 диагональных магических ручья из пурпурного стекла с жабосветами внутри
     * - 4 гигантских светящихся гриба и 4 плакучие мангровые ивы
     * - 16 пьедесталов из плачущего обсидиана и золочёного чернита для роста реагентов
     */
    private void buildLarge31x31AlchemistSwampSchematic() {
        Map<String, SchematicEntry> map = new LinkedHashMap<>();

        Material mud = resolveMaterial("MUD", Material.SOUL_SOIL);
        Material packedMud = resolveMaterial("PACKED_MUD", Material.COARSE_DIRT);
        Material mudBricks = resolveMaterial("MUD_BRICKS", Material.STONE_BRICKS);
        Material mossBlock = resolveMaterial("MOSS_BLOCK", Material.GRASS_BLOCK);
        Material deepslateBricks = resolveMaterial("DEEPSLATE_BRICKS", Material.NETHER_BRICKS);
        Material deepslateTiles = resolveMaterial("DEEPSLATE_TILES", Material.POLISHED_BLACKSTONE_BRICKS);
        Material chiseledDeepslate = resolveMaterial("CHISELED_DEEPSLATE", Material.CHISELED_POLISHED_BLACKSTONE);
        Material deepslateTileSlab = resolveMaterial("DEEPSLATE_TILE_SLAB", Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        Material deepslateBrickWall = resolveMaterial("DEEPSLATE_BRICK_WALL", Material.POLISHED_BLACKSTONE_WALL);
        Material verdantFroglight = resolveMaterial("VERDANT_FROGLIGHT", Material.SEA_LANTERN);
        Material pearlescentFroglight = resolveMaterial("PEARLESCENT_FROGLIGHT", Material.SHROOMLIGHT);
        Material ochreFroglight = resolveMaterial("OCHRE_FROGLIGHT", Material.GLOWSTONE);
        Material amethystCluster = resolveMaterial("AMETHYST_CLUSTER", Material.SEA_LANTERN);
        Material buddingAmethyst = resolveMaterial("BUDDING_AMETHYST", Material.PURPUR_BLOCK);

        // 1. Основное плато мистического болота 31x31 (радиус 15, y = 0)
        for (int dx = -15; dx <= 15; dx++) {
            for (int dz = -15; dz <= 15; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > 225) {
                    continue;
                }

                int absX = Math.abs(dx);
                int absZ = Math.abs(dz);
                Material floorMat;

                if (dx == 0 && dz == 0) {
                    floorMat = pearlescentFroglight;
                } else if (absX <= 1 && absZ <= 1) {
                    // Светящееся варево внутри центрального котла под стеклом
                    floorMat = Material.CRYING_OBSIDIAN;
                } else if (absX <= 3 && absZ <= 3 && distSq <= 14) {
                    // Основание Центральной Ротонды
                    floorMat = ((absX + absZ) % 2 == 0)
                            ? Material.POLISHED_BLACKSTONE_BRICKS
                            : Material.GILDED_BLACKSTONE;
                } else if ((absX <= 1 && absZ <= 14) || (absZ <= 1 && absX <= 14)) {
                    // 4 главные мощёные дорожки к центру
                    if ((absX == 0 || absZ == 0) && (absX + absZ) % 3 == 0) {
                        floorMat = chiseledDeepslate;
                    } else if (absX == 0 || absZ == 0) {
                        floorMat = Material.POLISHED_BLACKSTONE_BRICKS;
                    } else {
                        floorMat = ((absX + absZ) % 2 == 0) ? deepslateBricks : mudBricks;
                    }
                } else if (Math.abs(absX - absZ) <= 1 && absX >= 3 && absX <= 11) {
                    // 4 диагональных светящихся алхимических ручья
                    floorMat = ((absX + absZ) % 2 == 0)
                            ? Material.PURPLE_STAINED_GLASS
                            : Material.MAGENTA_STAINED_GLASS;
                } else if (distSq >= 190) {
                    // Внешнее готическое кольцо болота (чёткая граница зоны ивента!)
                    floorMat = ((absX + absZ) % 2 == 0) ? deepslateTiles : Material.MOSSY_STONE_BRICKS;
                } else {
                    // Живописная болотная почва (мох, мицелий, подзол, грязь)
                    int hash = (absX * 7 + absZ * 13) % 5;
                    floorMat = switch (hash) {
                        case 0 -> mossBlock;
                        case 1 -> Material.MYCELIUM;
                        case 2 -> Material.PODZOL;
                        case 3 -> packedMud;
                        default -> mud;
                    };
                }

                putBlock(map, dx, 0, dz, floorMat);
            }
        }

        // 2. Центральная Готическая Ротонда и Гигантский Бурлящий Котёл (y = 1..8)
        // Центральный пьедестал для 2-метрового Алхимика (0, 1, 0) в самом сердце котла
        putBlock(map, 0, 1, 0, Material.CHISELED_POLISHED_BLACKSTONE);

        // Фиолетовое «кипящее зелье» по углам вокруг Алхимика
        int[][] innerBrew = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int[] ib : innerBrew) {
            putBlock(map, ib[0], 1, ib[1], Material.CHISELED_POLISHED_BLACKSTONE);
            putBlock(map, ib[0], 2, ib[1], Material.BREWING_STAND);
        }

        // 4 интерактивных Котла вокруг Алхимика по сторонам света
        putBlock(map, -1, 1, 0, Material.CAULDRON);
        putBlock(map, 1, 1, 0, Material.CAULDRON);
        putBlock(map, 0, 1, -1, Material.CAULDRON);
        putBlock(map, 0, 1, 1, Material.CAULDRON);

        // Пологие ступени из полированного чернита со всех сторон центрального помоста
        for (int i = -1; i <= 1; i++) {
            putBlock(map, -2, 1, i, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
            putBlock(map, 2, 1, i, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
            putBlock(map, i, 1, -2, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
            putBlock(map, i, 1, 2, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        }

        // 4 угловые колонны Готической Ротонды (на координатах +-3, +-3) и купольный свод над Алхимиком
        int[][] rotundaPillars = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] p : rotundaPillars) {
            int px = p[0];
            int pz = p[1];
            putBlock(map, px, 0, pz, Material.GILDED_BLACKSTONE);
            putBlock(map, px, 1, pz, chiseledDeepslate);
            putBlock(map, px, 2, pz, deepslateTiles);
            putBlock(map, px, 3, pz, Material.POLISHED_BLACKSTONE_WALL);
            putBlock(map, px, 4, pz, Material.POLISHED_BLACKSTONE_WALL);
            putBlock(map, px, 5, pz, chiseledDeepslate);
            putBlock(map, px, 6, pz, deepslateTileSlab);

            // Диагональные своды крыши ротонды к центру (на высоте y = 6)
            int stepX = px > 0 ? -1 : 1;
            int stepZ = pz > 0 ? -1 : 1;
            putBlock(map, px + stepX, 5, pz, Material.NETHER_BRICK_FENCE);
            putBlock(map, px, 5, pz + stepZ, Material.NETHER_BRICK_FENCE);
            putBlock(map, px + stepX, 4, pz, Material.SOUL_LANTERN);
            putBlock(map, px + stepX, 6, pz + stepZ, deepslateTiles);
        }

        // Высокий готический купол и шпиль над центром ротонды (y = 6..8) — не мешает 2-метровому Алхимику (y = 2..4)
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                if (ox == 0 && oz == 0) {
                    putBlock(map, 0, 6, 0, pearlescentFroglight);
                    putBlock(map, 0, 7, 0, chiseledDeepslate);
                    putBlock(map, 0, 8, 0, amethystCluster);
                } else if (ox == 0 || oz == 0) {
                    putBlock(map, ox, 6, oz, deepslateTiles);
                    putBlock(map, ox, 7, oz, deepslateTileSlab);
                } else {
                    putBlock(map, ox, 6, oz, deepslateTileSlab);
                }
            }
        }

        // 3. 16 резных алхимических пьедесталов для роста нестабильных Реагентов
        for (int[] ped : PEDESTAL_OFFSETS) {
            int px = ped[0];
            int pz = ped[1];
            putBlock(map, px, 0, pz, Material.CRYING_OBSIDIAN);
            int[][] adj = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] a : adj) {
                int ax = px + a[0];
                int az = pz + a[1];
                String key = ax + ":0:" + az;
                SchematicEntry existing = map.get(key);
                if (existing != null
                        && existing.material() != Material.POLISHED_BLACKSTONE_BRICKS
                        && existing.material() != chiseledDeepslate
                        && existing.material() != Material.PURPLE_STAINED_GLASS
                        && existing.material() != Material.MAGENTA_STAINED_GLASS) {
                    putBlock(map, ax, 0, az, Material.GILDED_BLACKSTONE);
                }
            }
        }

        // 4. Четыре крытых Алхимических Павильона / Лавки вдоль дорожек
        buildAlchemicalPavilion(map, -6, -2, true, buddingAmethyst, amethystCluster, deepslateTileSlab);
        buildAlchemicalPavilion(map, 6, 2, true, buddingAmethyst, amethystCluster, deepslateTileSlab);
        buildAlchemicalPavilion(map, -2, 6, false, buddingAmethyst, amethystCluster, deepslateTileSlab);
        buildAlchemicalPavilion(map, 2, -6, false, buddingAmethyst, amethystCluster, deepslateTileSlab);

        // 5. Четыре Входные Готические Арки по краям зоны ивента (N, S, E, W на радиусе 14)
        buildEntranceGateArch(map, 0, -14, true, deepslateTiles, chiseledDeepslate, deepslateBrickWall);
        buildEntranceGateArch(map, 0, 14, true, deepslateTiles, chiseledDeepslate, deepslateBrickWall);
        buildEntranceGateArch(map, -14, 0, false, deepslateTiles, chiseledDeepslate, deepslateBrickWall);
        buildEntranceGateArch(map, 14, 0, false, deepslateTiles, chiseledDeepslate, deepslateBrickWall);

        // Декоративные пограничные столбики по внешнему кольцу зоны ивента (радиус 13-14)
        int[][] borderPosts = {
                {-10, -10}, {10, -10}, {-10, 10}, {10, 10},
                {-13, -6}, {13, -6}, {-13, 6}, {13, 6},
                {-6, -13}, {6, -13}, {-6, 13}, {6, 13}
        };
        for (int[] bp : borderPosts) {
            putBlock(map, bp[0], 0, bp[1], chiseledDeepslate);
            putBlock(map, bp[0], 1, bp[1], deepslateBrickWall);
            putBlock(map, bp[0], 2, bp[1], Material.SOUL_LANTERN);
        }

        // 6. Четыре гигантских волшебных светящихся гриба
        buildGiantMagicMushroom(map, -5, -10, 5, Material.NETHER_WART_BLOCK, pearlescentFroglight);
        buildGiantMagicMushroom(map, 5, -10, 5, Material.WARPED_WART_BLOCK, verdantFroglight);
        buildGiantMagicMushroom(map, -5, 10, 5, Material.WARPED_WART_BLOCK, verdantFroglight);
        buildGiantMagicMushroom(map, 5, 10, 5, Material.NETHER_WART_BLOCK, ochreFroglight);

        // 7. Четыре древние плакучие болотные ивы с корнями и светящимися плодами
        buildSwampWillowTree(map, -10, -5, 6, 1, 1, verdantFroglight);
        buildSwampWillowTree(map, 10, -5, 6, -1, 1, ochreFroglight);
        buildSwampWillowTree(map, -10, 5, 6, 1, -1, ochreFroglight);
        buildSwampWillowTree(map, 10, 5, 6, -1, -1, verdantFroglight);

        entries.addAll(map.values());
    }

    /**
     * Строит компактную алхимическую лавку с книжным шкафом, бочкой, кристаллом аметиста и навесом.
     */
    private void buildAlchemicalPavilion(
            Map<String, SchematicEntry> map,
            int cx,
            int cz,
            boolean alongX,
            Material buddingAmethyst,
            Material amethystCluster,
            Material roofSlab
    ) {
        int dx = alongX ? 0 : (cx > 0 ? 1 : -1);
        int dz = alongX ? (cz > 0 ? 1 : -1) : 0;
        int bx = cx + dx;
        int bz = cz + dz;

        putBlock(map, bx, 1, bz, Material.BOOKSHELF);
        putBlock(map, bx, 2, bz, Material.BREWING_STAND);
        putBlock(map, bx, 3, bz, roofSlab);

        int sideX = alongX ? 1 : 0;
        int sideZ = alongX ? 0 : 1;
        putBlock(map, bx + sideX, 1, bz + sideZ, Material.BARREL);
        putBlock(map, bx + sideX, 2, bz + sideZ, Material.SOUL_LANTERN);
        putBlock(map, bx - sideX, 1, bz - sideZ, buddingAmethyst);
        putBlock(map, bx - sideX, 2, bz - sideZ, amethystCluster);
    }

    /**
     * Строит готическую входную арку на краю арены (N, S, E, W), чётко обозначающую границу зоны ивента.
     */
    private void buildEntranceGateArch(
            Map<String, SchematicEntry> map,
            int gx,
            int gz,
            boolean northSouth,
            Material tileMat,
            Material chiseledMat,
            Material wallMat
    ) {
        int sx = northSouth ? 2 : 0;
        int sz = northSouth ? 0 : 2;

        int[][] posts = {{gx - sx, gz - sz}, {gx + sx, gz + sz}};
        for (int[] p : posts) {
            putBlock(map, p[0], 0, p[1], chiseledMat);
            putBlock(map, p[0], 1, p[1], tileMat);
            putBlock(map, p[0], 2, p[1], wallMat);
            putBlock(map, p[0], 3, p[1], wallMat);
            putBlock(map, p[0], 4, p[1], tileMat);
        }

        // Верхняя перекладина арки с подвесным фонарём душ
        for (int i = -1; i <= 1; i++) {
            int ax = gx + (northSouth ? i : 0);
            int az = gz + (northSouth ? 0 : i);
            putBlock(map, ax, 4, az, tileMat);
        }
        putBlock(map, gx, 5, gz, chiseledMat);
        putBlock(map, gx, 3, gz, Material.SOUL_LANTERN);
    }

    /**
     * Строит гигантский волшебный гриб со светящейся шляпкой.
     */
    private void buildGiantMagicMushroom(
            Map<String, SchematicEntry> map,
            int baseX,
            int baseZ,
            int height,
            Material capMaterial,
            Material glowMaterial
    ) {
        putBlock(map, baseX, 0, baseZ, Material.MYCELIUM);
        for (int y = 1; y <= height; y++) {
            putBlock(map, baseX, y, baseZ, Material.MUSHROOM_STEM);
        }

        putBlock(map, baseX + 1, height, baseZ, glowMaterial);
        putBlock(map, baseX - 1, height, baseZ, glowMaterial);
        putBlock(map, baseX, height, baseZ + 1, glowMaterial);
        putBlock(map, baseX, height, baseZ - 1, glowMaterial);

        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                if (Math.abs(ox) == 2 && Math.abs(oz) == 2) {
                    continue;
                }
                if (Math.abs(ox) == 2 || Math.abs(oz) == 2) {
                    putBlock(map, baseX + ox, height, baseZ + oz, capMaterial);
                }
            }
        }

        int topY = height + 1;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                putBlock(map, baseX + ox, topY, baseZ + oz, capMaterial);
            }
        }
        putBlock(map, baseX, topY + 1, baseZ, Material.SHROOMLIGHT);
    }

    /**
     * Строит древнюю плакучую болотную иву из мангрового/тёмного дерева с свисающей листвой.
     */
    private void buildSwampWillowTree(
            Map<String, SchematicEntry> map,
            int baseX,
            int baseZ,
            int height,
            int bendX,
            int bendZ,
            Material glowFruit
    ) {
        Material trunkMat = resolveMaterial("MANGROVE_WOOD", Material.DARK_OAK_WOOD);
        Material rootMat = resolveMaterial("MUDDY_MANGROVE_ROOTS", Material.MOSSY_COBBLESTONE);
        Material leafMat = resolveMaterial("MANGROVE_LEAVES", Material.DARK_OAK_LEAVES);

        putBlock(map, baseX, 0, baseZ, rootMat);
        putBlock(map, baseX + 1, 1, baseZ, rootMat);
        putBlock(map, baseX - 1, 1, baseZ, rootMat);
        putBlock(map, baseX, 1, baseZ + 1, rootMat);
        putBlock(map, baseX, 1, baseZ - 1, rootMat);

        int curX = baseX;
        int curZ = baseZ;
        for (int y = 1; y <= height; y++) {
            if (y == 4) {
                curX += bendX;
                curZ += bendZ;
            }
            putBlock(map, curX, y, curZ, trunkMat);
        }

        int topY = height + 1;
        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                if (Math.abs(ox) + Math.abs(oz) <= 3) {
                    putBlock(map, curX + ox, height, curZ + oz, leafMat);
                }
                if (Math.abs(ox) <= 1 && Math.abs(oz) <= 1) {
                    putBlock(map, curX + ox, topY, curZ + oz, leafMat);
                }
            }
        }

        putBlock(map, curX + 1, height - 1, curZ, glowFruit);
        putBlock(map, curX - 1, height - 1, curZ, glowFruit);

        int[][] vines = {{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-2, -1}, {2, 1}, {-1, 2}, {1, -2}};
        for (int[] v : vines) {
            putBlock(map, curX + v[0], height - 1, curZ + v[1], leafMat);
            putBlock(map, curX + v[0], height - 2, curZ + v[1], leafMat);
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
        yaml.set("name", "alchemist_cauldron_31x31_v2");
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
        return resolveMaterial(name, Material.STONE);
    }

    private Material resolveMaterial(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        String clean = name.replace("minecraft:", "").trim().toUpperCase();
        Material mat = Material.matchMaterial(clean);
        if (mat != null && mat.isBlock()) {
            return mat;
        }
        return fallback;
    }

    /**
     * Вставляет большую схему 31x31 «Котёл Алхимика» и возвращает точку спавна 2-метрового Алхимика.
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

        Block originBlock = world.getBlockAt(ox, oy, oz);
        if (originBlock.getType().isAir() && !world.getBlockAt(ox, oy - 1, oz).getType().isAir()) {
            oy = oy - 1;
        }

        this.activeCenterOrigin = new Location(world, ox + 0.5, oy, oz + 0.5);

        // 1. Очищаем пространство над болотом 31x31 (радиус 15, высота -2..10) и сохраняем блоки для отката
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

        Material deepslateBricks = resolveMaterial("DEEPSLATE_BRICKS", Material.STONE_BRICKS);
        Material polishedDeepslate = resolveMaterial("POLISHED_DEEPSLATE", Material.COBBLESTONE);
        Material froglight = resolveMaterial("PEARLESCENT_FROGLIGHT", Material.SEA_LANTERN);

        // 2. Укладываем прочный двухслойный фундамент (dy = -1, -2), а под стеклянными ручьями — светящиеся жабосветы!
        for (SchematicEntry entry : entries) {
            if (entry.dy() == 0) {
                Block under1 = world.getBlockAt(ox + entry.dx(), oy - 1, oz + entry.dz());
                recordBlockIfNotSaved(under1);
                if (entry.material() == Material.PURPLE_STAINED_GLASS || entry.material() == Material.MAGENTA_STAINED_GLASS) {
                    under1.setType(froglight, false);
                } else {
                    under1.setType(deepslateBricks, false);
                }

                Block under2 = world.getBlockAt(ox + entry.dx(), oy - 2, oz + entry.dz());
                recordBlockIfNotSaved(under2);
                under2.setType(polishedDeepslate, false);
            }
        }

        // 3. Строим основные блоки болота, ротонды, павильонов, грибов и плакучих ив
        List<SchematicEntry> deferredDecorations = new ArrayList<>();
        for (SchematicEntry entry : entries) {
            Material mat = entry.material();
            if (mat == Material.LANTERN
                    || mat == Material.SOUL_LANTERN
                    || mat == Material.TORCH
                    || mat == Material.BREWING_STAND
                    || mat.name().contains("AMETHYST_CLUSTER")) {
                deferredDecorations.add(entry);
                continue;
            }
            placeSchematicBlock(world, ox + entry.dx(), oy + entry.dy(), oz + entry.dz(), mat);
        }

        for (SchematicEntry entry : deferredDecorations) {
            placeSchematicBlock(world, ox + entry.dx(), oy + entry.dy(), oz + entry.dz(), entry.material());
        }

        // 4. Регистрируем 16 точек роста реагентов и 4 центральных котла
        reagentPedestalLocations.clear();
        activeReagentNodes.clear();
        for (int[] ped : PEDESTAL_OFFSETS) {
            Location pedLoc = new Location(world, ox + ped[0], oy + 1, oz + ped[1]);
            reagentPedestalLocations.add(pedLoc);
        }

        centralCauldronBlocks.clear();
        int[][] cauldrons = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] c : cauldrons) {
            centralCauldronBlocks.add(new BlockPosKey(world.getName(), ox + c[0], oy + 1, oz + c[1]));
        }

        // 5. Сразу выращиваем 4 стартовых реагента на болоте
        for (int i = 0; i < 4; i++) {
            BrushTier tier = plugin.getBrushLootManager().rollRandomReagentTier();
            spawnRandomReagentNode(tier);
        }

        // Центральный пьедестал находится на (0, +1, 0), поэтому 2-метровый Алхимик стоит ровно на oy + 2.0
        return new Location(world, ox + 0.5, oy + 2.0, oz + 0.5, 0.0f, 0.0f);
    }

    /**
     * Выращивает новый реагент указанной редкости на случайном свободном пьедестале болота.
     */
    public boolean spawnRandomReagentNode(BrushTier tier) {
        if (reagentPedestalLocations.isEmpty() || activeReagentNodes.size() >= 8) {
            return false;
        }

        List<Location> shuffled = new ArrayList<>(reagentPedestalLocations);
        Collections.shuffle(shuffled);

        for (Location loc : shuffled) {
            World w = loc.getWorld();
            if (w == null) {
                continue;
            }
            BlockPosKey key = new BlockPosKey(w.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            if (activeReagentNodes.containsKey(key)) {
                continue;
            }

            Block block = w.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            recordBlockIfNotSaved(block);

            Material nodeMat = switch (tier) {
                case COMMON -> resolveMaterial("WARPED_FUNGUS", Material.FERN);
                case EPIC -> resolveMaterial("AMETHYST_CLUSTER", Material.SEA_LANTERN);
                case LEGENDARY -> resolveMaterial("CRIMSON_FUNGUS", Material.END_ROD);
            };
            block.setType(nodeMat, false);
            activeReagentNodes.put(key, tier);

            Location effectLoc = loc.clone().add(0.5, 0.5, 0.5);
            w.playSound(effectLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.2f);
            w.spawnParticle(Particle.WITCH, effectLoc, 6, 0.25, 0.3, 0.25, 0.03);
            return true;
        }
        return false;
    }

    /**
     * Проверяет, кликнул ли игрок по выросшему реагенту (или по его пьедесталу из плачущего обсидиана под ним).
     */
    public Block resolveReagentNodeBlock(Block clickedBlock) {
        if (clickedBlock == null || activeReagentNodes.isEmpty()) {
            return null;
        }
        World w = clickedBlock.getWorld();
        BlockPosKey directKey = new BlockPosKey(w.getName(), clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ());
        if (activeReagentNodes.containsKey(directKey)) {
            return clickedBlock;
        }
        BlockPosKey aboveKey = new BlockPosKey(w.getName(), clickedBlock.getX(), clickedBlock.getY() + 1, clickedBlock.getZ());
        if (activeReagentNodes.containsKey(aboveKey)) {
            return w.getBlockAt(clickedBlock.getX(), clickedBlock.getY() + 1, clickedBlock.getZ());
        }
        return null;
    }

    public BrushTier getReagentTierAt(Block nodeBlock) {
        if (nodeBlock == null) {
            return null;
        }
        return activeReagentNodes.get(new BlockPosKey(
                nodeBlock.getWorld().getName(),
                nodeBlock.getX(),
                nodeBlock.getY(),
                nodeBlock.getZ()
        ));
    }

    /**
     * Срывает выросший реагент с пьедестала и возвращает его редкость.
     */
    public BrushTier harvestReagentAt(Block nodeBlock) {
        if (nodeBlock == null) {
            return null;
        }
        BlockPosKey key = new BlockPosKey(
                nodeBlock.getWorld().getName(),
                nodeBlock.getX(),
                nodeBlock.getY(),
                nodeBlock.getZ()
        );
        BrushTier tier = activeReagentNodes.remove(key);
        if (tier != null) {
            nodeBlock.setType(Material.AIR, false);
        }
        return tier;
    }

    public int getActiveReagentCount() {
        return activeReagentNodes.size();
    }

    /**
     * Проверяет, является ли блок одним из центральных Котлов Алхимика.
     */
    public boolean isCentralCauldronBlock(Block block) {
        if (block == null || centralCauldronBlocks.isEmpty()) {
            return false;
        }
        return centralCauldronBlocks.contains(new BlockPosKey(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()
        ));
    }

    /**
     * Спавнит лёгкие оптимизированные частицы над центральным Котлом и над активными реагентами (без лагов на телефонах).
     */
    public void spawnAmbientEffects() {
        if (activeCenterOrigin == null || activeCenterOrigin.getWorld() == null) {
            return;
        }
        World w = activeCenterOrigin.getWorld();

        // 1. Лёгкое бурление над центром Котла (всего 4 частицы в секунду вместо 32)
        Location centerCauldron = activeCenterOrigin.clone().add(0, 1.8, 0);
        w.spawnParticle(Particle.WITCH, centerCauldron, 3, 0.5, 0.3, 0.5, 0.01);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, centerCauldron, 1, 0.4, 0.2, 0.4, 0.01);

        // 2. Аккуратная подсветка выросших реагентов (по 2-3 частицы на реагент — не нагружает FPS)
        for (Map.Entry<BlockPosKey, BrushTier> entry : activeReagentNodes.entrySet()) {
            BlockPosKey pos = entry.getKey();
            BrushTier tier = entry.getValue();
            Location pLoc = new Location(w, pos.x() + 0.5, pos.y() + 0.55, pos.z() + 0.5);

            switch (tier) {
                case COMMON -> w.spawnParticle(Particle.HAPPY_VILLAGER, pLoc, 2, 0.2, 0.25, 0.2, 0.01);
                case EPIC -> w.spawnParticle(Particle.WITCH, pLoc, 3, 0.2, 0.3, 0.2, 0.02);
                case LEGENDARY -> {
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc, 2, 0.2, 0.3, 0.2, 0.01);
                    w.spawnParticle(Particle.END_ROD, pLoc, 1, 0.15, 0.25, 0.15, 0.01);
                }
            }
        }
    }

    private void placeSchematicBlock(World world, int bx, int by, int bz, Material mat) {
        Block block = world.getBlockAt(bx, by, bz);
        recordBlockIfNotSaved(block);
        block.setType(mat, false);

        if (block.getBlockData() instanceof Leaves leaves) {
            leaves.setPersistent(true);
            block.setBlockData(leaves, false);
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

    /**
     * Полностью возвращает все блоки болота в исходное состояние после окончания ивента.
     */
    public void restoreActiveSchematic() {
        activeReagentNodes.clear();
        reagentPedestalLocations.clear();
        centralCauldronBlocks.clear();
        activeCenterOrigin = null;

        if (activePasteHistory.isEmpty()) {
            activeArenaBlocks.clear();
            return;
        }

        for (int i = activePasteHistory.size() - 1; i >= 0; i--) {
            SavedBlock saved = activePasteHistory.get(i);
            Block block = saved.world().getBlockAt(saved.x(), saved.y(), saved.z());
            block.setBlockData(saved.originalData(), false);
        }
        activePasteHistory.clear();
        activeArenaBlocks.clear();
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
