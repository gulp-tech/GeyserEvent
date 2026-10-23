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
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.excavation.BrushLootManager;
import ru.geyserevent.excavation.BrushTier;
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
 * - Постоянный карьер 31x31 (радиус 15) с Кузницей Древних в центре, 4 шахтёрскими вратами,
 *   4 буровыми башнями и 4 глубокими рудными жилами.
 * - Автоматически регенерирует руды и спавнит «Древние жеоды» 3 редкостей
 *   (Обычная — Аметистовый блок, Эпическая — Плачущий обсидиан, Легендарная — Золочёный чернит).
 * - Игроки добывают жеоды киркой (или срывают с жилы) и раскалывают их у 2-метрового
 *   NPC «Древний Кузнец» (или на наковальне в центре) ради настраиваемого лута (/minemenu и /rift menu).
 */
public class AncientMineManager {

    private static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int MINE_RADIUS = 15;
    public static final double MINE_ZONE_LIMIT = 16.5;

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
    private int secondsUntilNextGeodeSpawn = 20;

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
        plugin.getLogger().info("Загружена схема «Шахта Древних» 31x31 (блоков: " + blocks.size() + ").");
    }

    private void saveDefaultAncientMineSchematic() {
        Map<String, SchematicBlock> map = new LinkedHashMap<>();
        int r = MINE_RADIUS;

        // 1. Массивное 3-слойное основание карьера из глубинного сланца и базальта (dy = -3..0)
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist > r + 0.5) {
                    continue;
                }
                putBlock(map, dx, -3, dz, "minecraft:deepslate_bricks");
                putBlock(map, dx, -2, dz, "minecraft:deepslate_tiles");

                // В 4 секторах карьера (диагонали) делаем углубление dy = -1 для рудной жилы
                boolean isQuarryPit = (Math.abs(dx) >= 4 && Math.abs(dx) <= 11
                        && Math.abs(dz) >= 4 && Math.abs(dz) <= 11
                        && dist <= 13.0);

                if (isQuarryPit) {
                    putBlock(map, dx, -1, dz, "minecraft:cobbled_deepslate");
                } else {
                    putBlock(map, dx, -1, dz, "minecraft:polished_deepslate");
                }

                // Поверхность dy = 0:
                if (dist >= r - 0.8) {
                    putBlock(map, dx, 0, dz, "minecraft:deepslate_bricks");
                } else if (Math.abs(dx) <= 2 || Math.abs(dz) <= 2) {
                    // Крестообразные шахтёрские мосты и рельсовые пути к центру
                    if ((dx == 0 && Math.abs(dz) >= 5 && Math.abs(dz) <= 13)
                            || (dz == 0 && Math.abs(dx) >= 5 && Math.abs(dx) <= 13)) {
                        putBlock(map, dx, 0, dz, "minecraft:COARSE_DIRT".toLowerCase());
                    } else if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        putBlock(map, dx, 0, dz, "minecraft:polished_blackstone_bricks");
                    } else {
                        putBlock(map, dx, 0, dz, "minecraft:deepslate_tile_slab[type=double]");
                    }
                } else if (isQuarryPit) {
                    // Рудные блоки жилы в 4 карьерах
                    if ((dx + dz) % 3 == 0) {
                        putBlock(map, dx, 0, dz, "minecraft:deepslate_gold_ore");
                    } else if ((dx * dz) % 5 == 0) {
                        putBlock(map, dx, 0, dz, "minecraft:deepslate_diamond_ore");
                    } else if ((dx + dz) % 2 == 0) {
                        putBlock(map, dx, 0, dz, "minecraft:iron_ore");
                    } else {
                        putBlock(map, dx, 0, dz, "minecraft:cobbled_deepslate");
                    }
                } else {
                    putBlock(map, dx, 0, dz, "minecraft:basalt[axis=y]");
                }

                // Расчистка воздуха над карьером (dy = 1..11)
                for (int dy = 1; dy <= 11; dy++) {
                    putBlock(map, dx, dy, dz, "minecraft:air");
                }
            }
        }

        // 2. Дополнительные рудные выступы внутри 4 карьеров на высоте dy = 1
        int[][] pitCenters = {{-8, -8}, {8, -8}, {-8, 8}, {8, 8}};
        for (int[] pc : pitCenters) {
            int cx = pc[0];
            int cz = pc[1];
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    if (Math.abs(ox) + Math.abs(oz) <= 3) {
                        putBlock(map, cx + ox, 1, cz + oz, "minecraft:deepslate_iron_ore");
                    }
                }
            }
            putBlock(map, cx, 2, cz, "minecraft:budding_amethyst");
        }

        // 3. Центральная Кузница Древних (радиус 4, высота dy = 1..9)
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= 3.6) {
                    putBlock(map, dx, 1, dz, "minecraft:polished_blackstone_bricks");
                } else if (d <= 4.5 && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1)) {
                    // Ступени со всех 4 сторон
                    putBlock(map, dx, 1, dz, "minecraft:polished_blackstone_brick_slab[type=bottom]");
                }
            }
        }
        // Лавовый горн под стеклом вокруг центральной наковальни/пьедестала Кузнеца
        int[][] lavaSlots = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int[] ls : lavaSlots) {
            putBlock(map, ls[0], 0, ls[1], "minecraft:magma_block");
            putBlock(map, ls[0], 1, ls[1], "minecraft:orange_stained_glass");
        }
        // Центральный пьедестал для 2-метрового NPC «Древний Кузнец» (0, 1, 0) и наковальни
        putBlock(map, 0, 1, 0, "minecraft:lodestone");
        putBlock(map, -2, 2, 0, "minecraft:anvil[facing=north]");
        putBlock(map, 2, 2, 0, "minecraft:anvil[facing=south]");
        putBlock(map, 0, 2, -2, "minecraft:blast_furnace[facing=north,lit=true]");
        putBlock(map, 0, 2, 2, "minecraft:smithing_table");

        // 4 колонны Ротонды Кузницы и купол (dy = 2..8)
        int[][] forgePillars = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] fp : forgePillars) {
            int px = fp[0];
            int pz = fp[1];
            putBlock(map, px, 2, pz, "minecraft:chiseled_polished_blackstone");
            putBlock(map, px, 3, pz, "minecraft:deepslate_brick_wall");
            putBlock(map, px, 4, pz, "minecraft:deepslate_brick_wall");
            putBlock(map, px, 5, pz, "minecraft:chiseled_polished_blackstone");
            putBlock(map, px, 6, pz, "minecraft:shroomlight");
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= 3.8 && d >= 2.5) {
                    putBlock(map, dx, 6, dz, "minecraft:deepslate_brick_slab[type=top]");
                } else if (d < 2.5) {
                    putBlock(map, dx, 7, dz, "minecraft:deepslate_tile_slab[type=bottom]");
                }
            }
        }
        putBlock(map, 0, 7, 0, "minecraft:chiseled_deepslate");
        putBlock(map, 0, 8, 0, "minecraft:soul_lantern[hanging=false]");
        putBlock(map, 0, 6, 0, "minecraft:chain[axis=y]");
        putBlock(map, 0, 5, 0, "minecraft:soul_lantern[hanging=true]");

        // 4. Четыре Входных Шахтёрских Врат на севере, юге, востоке и западе (радиус 14)
        buildMineGate(map, 0, -14, true);
        buildMineGate(map, 0, 14, true);
        buildMineGate(map, -14, 0, false);
        buildMineGate(map, 14, 0, false);

        // 5. Четыре Угловые Буровые Башни / Крана на диагоналях
        int[][] cranes = {{-11, -11}, {11, -11}, {-11, 11}, {11, 11}};
        for (int[] c : cranes) {
            int cx = c[0];
            int cz = c[1];
            for (int y = 1; y <= 6; y++) {
                putBlock(map, cx, y, cz, "minecraft:spruce_log[axis=y]");
            }
            int dirX = cx < 0 ? 1 : -1;
            int dirZ = cz < 0 ? 1 : -1;
            putBlock(map, cx + dirX, 6, cz, "minecraft:spruce_slab[type=top]");
            putBlock(map, cx, 6, cz + dirZ, "minecraft:spruce_slab[type=top]");
            putBlock(map, cx + dirX, 6, cz + dirZ, "minecraft:chiseled_deepslate");
            putBlock(map, cx + dirX, 5, cz + dirZ, "minecraft:chain[axis=y]");
            putBlock(map, cx + dirX, 4, cz + dirZ, "minecraft:amethyst_block");
            putBlock(map, cx, 7, cz, "minecraft:lantern[hanging=false]");
        }

        // 6. Ограждение карьера по внешнему кольцу с фонарями
        for (int angleDeg = 0; angleDeg < 360; angleDeg += 15) {
            if (angleDeg % 90 == 0) {
                continue; // проходы врат
            }
            double rad = Math.toRadians(angleDeg);
            int bx = (int) Math.round(Math.cos(rad) * 14.0);
            int bz = (int) Math.round(Math.sin(rad) * 14.0);
            putBlock(map, bx, 1, bz, "minecraft:deepslate_brick_wall");
            if (angleDeg % 30 == 0) {
                putBlock(map, bx, 2, bz, "minecraft:deepslate_brick_wall");
                putBlock(map, bx, 3, bz, "minecraft:lantern[hanging=false]");
            }
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", CURRENT_SCHEMA_VERSION);
        yaml.set("name", "ancient_mine_31x31");
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

    private void buildMineGate(Map<String, SchematicBlock> map, int gx, int gz, boolean northSouth) {
        for (int offset = -2; offset <= 2; offset++) {
            int x = northSouth ? gx + offset : gx;
            int z = northSouth ? gz : gz + offset;
            if (Math.abs(offset) == 2) {
                for (int y = 1; y <= 5; y++) {
                    putBlock(map, x, y, z, "minecraft:deepslate_bricks");
                }
                putBlock(map, x, 6, z, "minecraft:chiseled_deepslate");
                putBlock(map, x, 7, z, "minecraft:soul_lantern[hanging=false]");
            } else {
                putBlock(map, x, 5, z, "minecraft:spruce_slab[type=top]");
                if (offset == 0) {
                    putBlock(map, x, 6, z, "minecraft:shroomlight");
                    putBlock(map, x, 4, z, "minecraft:lantern[hanging=true]");
                }
            }
        }
    }

    private void putBlock(Map<String, SchematicBlock> map, int dx, int dy, int dz, String data) {
        String key = dx + ":" + dy + ":" + dz;
        map.put(key, new SchematicBlock(dx, dy, dz, data));
    }

    /**
     * Запускает постоянный ивент «Шахта Древних» на координатах из конфига (по умолчанию X=0, Z=0 в world2).
     */
    public void initializePermanentMine() {
        restoreOrStartMine();
    }

    private void restoreOrStartMine() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("mine");
        boolean enabled = section == null || section.getBoolean("enabled", true);
        if (!enabled) {
            return;
        }

        // Если осталась резервная копия с прошлого запуска — сначала восстановим блоки, чтобы чисто поставить заново
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

        // Подгружаем чанк 0 ~ 0 перед определением высоты поверхности
        world.getChunkAt(x >> 4, z >> 4).load(true);
        int y = (configuredY > world.getMinHeight() && configuredY < world.getMaxHeight())
                ? configuredY
                : world.getHighestBlockYAt(x, z);

        Location target = new Location(world, x + 0.5, y, z + 0.5);
        spawnMineAt(target, false);
    }

    /**
     * Устанавливает «Шахту Древних» на указанной локации (по умолчанию 0 ~ 0).
     */
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

        // Загружаем чанки в радиусе 16 блоков
        for (int dx = -MINE_RADIUS - 1; dx <= MINE_RADIUS + 1; dx += 16) {
            for (int dz = -MINE_RADIUS - 1; dz <= MINE_RADIUS + 1; dz += 16) {
                world.getChunkAt((cx + dx) >> 4, (cz + dz) >> 4).load(true);
            }
        }

        // Сохраняем бэкап исходной местности и вставляем схему 31x31
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

            // Запоминаем слоты рудной жилы в 4 карьерах
            double dist = Math.hypot(sb.dx(), sb.dz());
            boolean isQuarryVein = (sb.dy() == 0 || sb.dy() == 1 || sb.dy() == 2)
                    && Math.abs(sb.dx()) >= 4 && Math.abs(sb.dx()) <= 11
                    && Math.abs(sb.dz()) >= 4 && Math.abs(sb.dz()) <= 11
                    && dist <= 13.0
                    && !block.getType().isAir();
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

        // Заполняем рудную жилу рудами и начальными Древними жеодами
        regenerateAllOresAndGeodes();

        // Спавним 2-метрового NPC «Древний Кузнец» в центре на пьедестале
        spawnBlacksmithNpc();

        // Запускаем постоянный таймер регенерации и лёгких частиц
        startMineTicker();

        plugin.getLogger().info("Постоянный ивент «Шахта Древних» активен на координатах: "
                + world.getName() + " " + cx + " " + cy + " " + cz);
    }

    /**
     * Полностью обновляет все рудные блоки в жиле и размещает начальные Древние жеоды.
     */
    public void regenerateAllOresAndGeodes() {
        if (centerLocation == null || centerLocation.getWorld() == null) {
            return;
        }
        activeGeodeBlocks.clear();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        List<Location> shuffled = new ArrayList<>(veinLocations);
        Collections.shuffle(shuffled, rnd);

        int initialGeodes = Math.min(12, Math.max(6, shuffled.size() / 10));
        for (int i = 0; i < shuffled.size(); i++) {
            Location loc = shuffled.get(i);
            Block b = loc.getBlock();
            if (i < initialGeodes) {
                BrushTier tier = plugin.getBrushLootManager().rollRandomGeodeTier();
                placeGeodeBlock(b, tier);
            } else {
                b.setType(randomOreMaterial(rnd), false);
            }
        }

        this.secondsUntilFullRegen = getConfiguredRegenIntervalSeconds();
        this.secondsUntilNextGeodeSpawn = 20;
    }

    private Material randomOreMaterial(ThreadLocalRandom rnd) {
        int roll = rnd.nextInt(100);
        if (roll < 30) {
            return Material.DEEPSLATE_IRON_ORE;
        } else if (roll < 55) {
            return Material.DEEPSLATE_GOLD_ORE;
        } else if (roll < 75) {
            return Material.DEEPSLATE_EMERALD_ORE;
        } else if (roll < 92) {
            return Material.DEEPSLATE_DIAMOND_ORE;
        } else {
            return Material.ANCIENT_DEBRIS;
        }
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
        if (veinLocations.isEmpty() || activeGeodeBlocks.size() >= 22) {
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

        // Голограмма над 2-метровым Кузнецом
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
                a.setCustomName(ColorUtil.color("&#FFB81C&lДревний Кузнец &7(Раскол жеод)"));
            });
            hologramEntityUuids.add(as.getUniqueId());
        }
    }

    private String buildBlacksmithHologramText() {
        return ColorUtil.color(
                "&#FFB81C&lДревний Кузнец\n"
                        + "&fЖеод в жиле: &#34D711" + activeGeodeBlocks.size() + " шт.\n"
                        + "&fОбновление руд через: &#E0C912" + ColorUtil.formatDuration(secondsUntilFullRegen) + "\n"
                        + "&7Добудь жеоду в карьере и нажми на меня!"
        );
    }

    @SuppressWarnings("deprecation")
    private void updateBlacksmithHologram() {
        if (centerLocation == null || centerLocation.getWorld() == null) {
            return;
        }
        // Если чанк выгружен — не трогаем сущности до подхода игроков
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
        this.secondsUntilNextGeodeSpawn = 20;

        this.tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (centerLocation == null || centerLocation.getWorld() == null) {
                    return;
                }
                secondsUntilFullRegen--;
                secondsUntilNextGeodeSpawn--;

                if (secondsUntilFullRegen <= 0) {
                    regenerateAllOresAndGeodes();
                    // Уведомляем только игроков, находящихся рядом с Шахтой Древних (без спама на весь сервер)
                    for (Player p : centerLocation.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(centerLocation) <= 45.0 * 45.0) {
                            p.sendMessage(ColorUtil.color("&e┠ &#FFB81C&lШахта Древних: &fРудная жила и древние жеоды полностью обновились!"));
                            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.2f);
                        }
                    }
                } else if (secondsUntilNextGeodeSpawn <= 0) {
                    spawnSingleRandomGeode();
                    secondsUntilNextGeodeSpawn = 20;
                }

                updateBlacksmithHologram();
                spawnLightweightGeodeParticles();
                if (plugin.getTopRightHudManager() != null) {
                    plugin.getTopRightHudManager().updateMineHud();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Лёгкие частицы над активными жеодами (только если рядом есть игроки, без нагрузки на клиент).
     */
    private void spawnLightweightGeodeParticles() {
        if (centerLocation == null || centerLocation.getWorld() == null || activeGeodeBlocks.isEmpty()) {
            return;
        }
        World world = centerLocation.getWorld();
        boolean playerNearby = false;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(centerLocation) <= 38.0 * 38.0) {
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

    /**
     * Проверяет, находится ли блок в пределах «Шахты Древних».
     */
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
        return dx <= MINE_RADIUS + 1 && dz <= MINE_RADIUS + 1 && dy >= -4 && dy <= 14;
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
        return dx <= MINE_ZONE_LIMIT && dz <= MINE_ZONE_LIMIT;
    }

    /**
     * Обрабатывает ломание блока в «Шахте Древних».
     * Если это блок жилы или жеоды — выдаёт ресурс/жеоду и ставит породу до следующей регенерации.
     * Все остальные блоки карьера защищены от разрушения.
     *
     * @return true если блок находится на территории шахты (событие должно быть отменено).
     */
    public boolean handleMineBlockBreak(Player player, Block block) {
        if (!isInsideMineTerritory(block.getLocation())) {
            return false;
        }

        String key = blockKey(block.getLocation());
        BrushLootManager lootManager = plugin.getBrushLootManager();

        // 1. Проверяем, не является ли этот блок «Древней жеодой»
        BrushTier geodeTier = activeGeodeBlocks.get(key);
        if (geodeTier != null) {
            int currentItems = lootManager.countPlayerBrushes(player);
            if (currentItems >= 3) {
                player.sendMessage(ColorUtil.color("&e┠ &cВ инвентаре может быть максимум &#E0C9123 древние жеоды&c! Расколите их у Древнего Кузнеца в центре."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.9f, 1.0f);
                return true;
            }
            activeGeodeBlocks.remove(key);
            block.setType(Material.COBBLED_DEEPSLATE, false);

            ItemStack geodeItem = lootManager.createGeodeItem(geodeTier);
            player.getInventory().addItem(geodeItem);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.2f);
            player.sendMessage(ColorUtil.color("&e┠ &fВы добыли " + lootManager.getGeodeDisplayName(geodeTier)
                    + "&f! Отнесите её &#FFB81CДревнему Кузнецу &fв центр шахты."));
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
            ItemStack drop = switch (brokenType) {
                case DEEPSLATE_IRON_ORE, IRON_ORE -> new ItemStack(Material.IRON_INGOT, 2);
                case DEEPSLATE_GOLD_ORE, GOLD_ORE -> new ItemStack(Material.GOLD_INGOT, 2);
                case DEEPSLATE_EMERALD_ORE, EMERALD_ORE -> new ItemStack(Material.EMERALD, 2);
                case DEEPSLATE_DIAMOND_ORE, DIAMOND_ORE -> new ItemStack(Material.DIAMOND, 1);
                case ANCIENT_DEBRIS -> new ItemStack(Material.NETHERITE_SCRAP, 1);
                default -> new ItemStack(Material.COBBLESTONE, 1);
            };
            Map<Integer, ItemStack> left = player.getInventory().addItem(drop);
            for (ItemStack leftover : left.values()) {
                block.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 0.9f, 1.1f);

            // Небольшой шанс (12%), что прямо под рудой сразу откроется новая Древняя жеода!
            if (ThreadLocalRandom.current().nextInt(100) < 12) {
                BrushTier bonusTier = lootManager.rollRandomGeodeTier();
                placeGeodeBlock(block, bonusTier);
                player.playSound(block.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);
            }
            return true;
        }

        // Остальные блоки структуры защищены
        return true;
    }

    /**
     * Проверяет, является ли сущность NPC «Древний Кузнец».
     */
    public boolean isBlacksmithNpc(Entity entity) {
        return entity != null && blacksmithNpcUuid != null && blacksmithNpcUuid.equals(entity.getUniqueId());
    }

    /**
     * Раскалывает 1 Древнюю жеоду из инвентаря игрока при клике по Древнему Кузнецу или наковальне в центре.
     */
    public boolean handleBlacksmithInteract(Player player) {
        BrushLootManager lootManager = plugin.getBrushLootManager();

        // Ищем жеоду сначала в основной руке, затем во всём инвентаре
        ItemStack hand = player.getInventory().getItemInMainHand();
        BrushTier tier = lootManager.getGeodeTier(hand);
        int foundSlot = -1;
        if (tier != null) {
            foundSlot = player.getInventory().getHeldItemSlot();
        } else {
            ItemStack[] contents = player.getInventory().getStorageContents();
            for (int i = 0; i < contents.length; i++) {
                BrushTier t = lootManager.getGeodeTier(contents[i]);
                if (t != null) {
                    tier = t;
                    foundSlot = i;
                    break;
                }
            }
        }

        if (tier == null || foundSlot < 0) {
            player.sendMessage(ColorUtil.color("&e┠ &#FFB81CДревний Кузнец: &fДобудь &#E0C912Древнюю жеоду &fв рудной жиле вокруг меня, и я расколю её на ценные артефакты!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1.0f, 0.9f);
            return true;
        }

        // Забираем 1 жеоду
        ItemStack stack = player.getInventory().getItem(foundSlot);
        if (stack != null) {
            if (stack.getAmount() > 1) {
                stack.setAmount(stack.getAmount() - 1);
            } else {
                player.getInventory().setItem(foundSlot, null);
            }
        }

        // Выдаём лут из таблицы «Шахты Древних»
        ItemStack reward = lootManager.rollLootForMine(tier);
        if (reward != null && !reward.getType().isAir()) {
            Map<Integer, ItemStack> left = player.getInventory().addItem(reward);
            for (ItemStack leftover : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.9f, 1.15f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);
        if (centerLocation != null && centerLocation.getWorld() != null) {
            centerLocation.getWorld().spawnParticle(
                    Particle.LAVA,
                    centerLocation.clone().add(0.0, 2.5, 0.0),
                    4, 0.25, 0.25, 0.25, 0.02
            );
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
        return centerLocation != null && centerLocation.getWorld() != null;
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
