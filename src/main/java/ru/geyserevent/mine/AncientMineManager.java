package ru.geyserevent.mine;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.boss.BossAbilityController;
import ru.geyserevent.excavation.BrushTier;
import ru.geyserevent.mine.MinerBackpackManager.MineOreType;
import ru.geyserevent.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Постоянный анархический ивент «Шахта Древних (Жила Жеод)» v1.8.3 (schema-version: 5).
 *
 * Исправления и архитектура v5:
 * 1) Исправлен краш частицы Particle.FLASH в Purpur 26.2 (из-за которого таймер не сбрасывался
 *    и руды обновлялись каждую секунду бесконечно).
 * 2) 2.5-метровый Древний Кузнец стоит НА вершине подиума (centerY + 2.0, не проваливается в блок!)
 *    и имеет увеличенный рост (scale = 1.38).
 * 3) Полностью переработанная цельная схематика 45x45 (schema-version: 5):
 *    - Полноценная сплошная ступенчатая крыша над центральной Ротондой Кузни (без дыр и висящих колец);
 *    - Ни одного блока в воздухе: все деревянные шахтёрские арки стоят на твёрдом полу и имеют сплошные балки;
 *    - Сгруппированные реалистичные жилы руд по секторам (Медно-Железная, Золотая, Изумрудная, Алмазно-Древняя),
 *      которые при обновлении сохраняют тип своей жилы, а не превращаются в разноцветную «кашу».
 */
public class AncientMineManager {

    private static final int CURRENT_MINE_SCHEMA_VERSION = 7;
    public static final int MINE_RADIUS = 22;
    public static final double MINE_ZONE_LIMIT = 24.5;

    private final GeyserEventPlugin plugin;
    private final File schematicFile;
    private final File backupFile;

    private final List<BlockEntry> cachedSchematic = new ArrayList<>();
    private final List<Offset> geodePoolOffsets = new ArrayList<>();
    private final Set<BlockKey> protectedBlocks = new HashSet<>();
    private final Set<BlockKey> activeGeodeBlocks = new HashSet<>();
    private final Set<BlockKey> mineableOreSpots = new HashSet<>();
    private final Map<BlockKey, Material> defaultOreMaterials = new HashMap<>();
    private final Set<BlockKey> anvilStations = new HashSet<>();

    private Location centerLocation;
    private UUID blacksmithUuid;
    private final List<UUID> hologramUuids = new ArrayList<>();

    private BukkitTask mainTickTask;
    private BukkitTask particleTask;
    private int secondsUntilFullRegen = 180;
    private int regenCycleCounter = 0;
    private boolean richVeinActive = false;
    private boolean mineActive = false;

    public record BlockEntry(int dx, int dy, int dz, BlockData blockData, boolean geodeSpot) {
    }

    public record Offset(int dx, int dy, int dz) {
    }

    public record BlockKey(String world, int x, int y, int z) {
        public static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }

        public static BlockKey of(World world, int x, int y, int z) {
            return new BlockKey(world.getName(), x, y, z);
        }
    }

    public AncientMineManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        File schemFolder = new File(plugin.getDataFolder(), "schematics");
        if (!schemFolder.exists()) {
            schemFolder.mkdirs();
        }
        this.schematicFile = new File(schemFolder, "ancient_mine.schem.yml");
        this.backupFile = new File(plugin.getDataFolder(), "mine_terrain_backup.yml");
        ensureDefaultMineSchematic();
        loadMineSchematic();
    }

    /**
     * Запускает постоянный ивент «Шахта Древних» на 0 ~ 0 при старте сервера.
     * Гарантированно удаляет старого НПС, старые голограммы, старые стойки рюкзаков и перезаписывает схему v5.
     */
    public void initializePermanentMine() {
        if (!plugin.getConfig().getBoolean("ancient-mine.enabled", true)) {
            plugin.getLogger().info("Постоянный ивент «Шахта Древних» отключён в config.yml.");
            return;
        }

        String worldName = plugin.getConfig().getString("ancient-mine.world", plugin.getPluginConfig().getWorldName());
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            plugin.getLogger().warning("Не найден мир для постоянного ивента «Шахта Древних»!");
            return;
        }

        int centerX = plugin.getConfig().getInt("ancient-mine.center-x", 0);
        int centerZ = plugin.getConfig().getInt("ancient-mine.center-z", 0);

        for (int cx = (centerX - MINE_RADIUS - 16) >> 4; cx <= (centerX + MINE_RADIUS + 16) >> 4; cx++) {
            for (int cz = (centerZ - MINE_RADIUS - 16) >> 4; cz <= (centerZ + MINE_RADIUS + 16) >> 4; cz++) {
                Chunk ch = world.getChunkAt(cx, cz);
                if (!ch.isLoaded()) {
                    ch.load(true);
                }
            }
        }

        Integer preservedCenterY = null;
        if (backupFile.exists()) {
            YamlConfiguration backupYml = YamlConfiguration.loadConfiguration(backupFile);
            if (backupYml.contains("center-y")) {
                preservedCenterY = backupYml.getInt("center-y");
            }
            int backupSchemaVer = backupYml.getInt("backup-schema-version", 1);
            if (backupSchemaVer < CURRENT_MINE_SCHEMA_VERSION) {
                plugin.getLogger().info("Обнаружена старая постройка Шахты Древних (v" + backupSchemaVer
                        + "). Выполняется полный откат и перезапись на новую архитектуру v" + CURRENT_MINE_SCHEMA_VERSION + "...");
                restoreOrCaptureTerrain(world, centerX, centerZ, true);
                backupFile.delete();
            }
        }

        int centerY = preservedCenterY != null ? preservedCenterY : resolveGroundY(world, centerX, centerZ);
        this.centerLocation = new Location(world, centerX + 0.5, centerY, centerZ + 0.5);

        purgeOldMineEntities(world, centerX, centerY, centerZ);
        if (plugin.getMinerBackpackManager() != null) {
            plugin.getMinerBackpackManager().purgeOrphanedBackpackStands();
        }

        restoreOrCaptureTerrain(world, centerX, centerZ, false);
        pasteMineSchematic(this.centerLocation);

        spawnOrVerifyBlacksmith();
        this.secondsUntilFullRegen = getRegenIntervalSeconds();
        regenerateVeinsAndGeodes(true, false);

        this.mineActive = true;
        startTasks();

        plugin.getLogger().info("Постоянный ивент «Шахта Древних (45x45, v7)» активен на координатах "
                + centerX + " " + centerY + " " + centerZ + " (" + world.getName() + ")!");
    }

    private void purgeOldMineEntities(World world, int centerX, int centerY, int centerZ) {
        int baseCx = centerX >> 4;
        int baseCz = centerZ >> 4;
        // Удерживаем все 4 центральных чанка вокруг (0 ~ 0): (-1,-1), (-1,0), (0,-1), (0,0)
        for (int cx = baseCx - 1; cx <= baseCx; cx++) {
            for (int cz = baseCz - 1; cz <= baseCz; cz++) {
                Chunk c = world.getChunkAt(cx, cz);
                c.load(true);
                c.setForceLoaded(true);
            }
        }

        for (int cx = baseCx - 3; cx <= baseCx + 3; cx++) {
            for (int cz = baseCz - 3; cz <= baseCz + 3; cz++) {
                Chunk ch = world.getChunkAt(cx, cz);
                if (!ch.isLoaded()) {
                    ch.load(true);
                }
                for (Entity entity : ch.getEntities()) {
                    if (entity instanceof Player || entity instanceof org.bukkit.entity.Item) {
                        continue;
                    }
                    if (entity instanceof Villager || entity instanceof ArmorStand) {
                        entity.remove();
                    } else if (isInsideMineProtection(entity.getLocation())) {
                        entity.remove();
                    }
                }
            }
        }

        Location center = new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
        for (Entity entity : world.getNearbyEntities(center, 60.0, 40.0, 60.0)) {
            if (entity instanceof Player || entity instanceof org.bukkit.entity.Item) {
                continue;
            }
            if (entity instanceof Villager || entity instanceof ArmorStand) {
                entity.remove();
            } else if (isInsideMineProtection(entity.getLocation())) {
                entity.remove();
            }
        }
        blacksmithUuid = null;
        hologramUuids.clear();
    }

    /**
     * Вызывается мгновенно в момент подгрузки сущностей чанка с диска (EntitiesLoadEvent / ChunkLoadEvent),
     * когда игрок подходит к 0 ~ 0. Сразу удаляет любые старые ArmorStand и лишних жителей во всех
     * соседних чанках (-3..+3), чтобы у игрока не проседал FPS при приближении к НПС!
     */
    public void onChunkEntitiesLoaded(Chunk chunk, List<Entity> entities) {
        if (centerLocation == null || centerLocation.getWorld() == null || chunk == null) {
            return;
        }
        if (!chunk.getWorld().equals(centerLocation.getWorld())) {
            return;
        }
        int baseCx = centerLocation.getBlockX() >> 4;
        int baseCz = centerLocation.getBlockZ() >> 4;
        if (Math.abs(chunk.getX() - baseCx) > 3 || Math.abs(chunk.getZ() - baseCz) > 3) {
            return;
        }

        Location npcLoc = centerLocation.clone().add(0.0, 2.0, 0.0);
        for (Entity entity : entities) {
            if (entity == null || entity instanceof Player || entity instanceof org.bukkit.entity.Item) {
                continue;
            }
            if (entity instanceof ArmorStand stand) {
                if (plugin.getMinerBackpackManager() == null
                        || !plugin.getMinerBackpackManager().isActiveMountedBackpackStand(stand)) {
                    stand.remove();
                }
                continue;
            }
            if (entity instanceof Villager v) {
                if (blacksmithUuid != null && v.getUniqueId().equals(blacksmithUuid) && v.isValid() && !v.isDead()) {
                    continue;
                }
                if (blacksmithUuid == null && v.isValid() && !v.isDead() && v.getLocation().distanceSquared(npcLoc) <= 16.0) {
                    blacksmithUuid = v.getUniqueId();
                    continue;
                }
                v.remove();
                continue;
            }
            if (isInsideMineProtection(entity.getLocation())) {
                entity.remove();
            }
        }
    }

    private int resolveGroundY(World world, int x, int z) {
        int configuredY = plugin.getConfig().getInt("ancient-mine.center-y", -9999);
        if (configuredY != -9999) {
            return configuredY;
        }
        if (backupFile.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(backupFile);
            if (yml.contains("center-y")) {
                return yml.getInt("center-y", 64);
            }
        }
        int highest = world.getHighestBlockYAt(x, z);
        for (int y = Math.min(world.getMaxHeight() - 15, highest + 2); y >= world.getMinHeight() + 10; y--) {
            Material m = world.getBlockAt(x, y, z).getType();
            if (m.isSolid() && !m.name().contains("LEAVES") && !m.name().contains("LOG")) {
                return y;
            }
        }
        return Math.max(64, highest);
    }

    public int getRegenIntervalSeconds() {
        return Math.max(45, plugin.getConfig().getInt("ancient-mine.regen-interval-seconds", 180));
    }

    public int getActiveGeodesPerCycle() {
        int base = Math.max(12, plugin.getConfig().getInt("ancient-mine.geodes-per-cycle", 20));
        return richVeinActive ? (int) Math.round(base * 1.4) : base;
    }

    private void startTasks() {
        stopTasks();

        mainTickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!mineActive || centerLocation == null || centerLocation.getWorld() == null) {
                    return;
                }
                spawnOrVerifyBlacksmith();
                secondsUntilFullRegen--;

                if (secondsUntilFullRegen == 30) {
                    notifyNearbyMiners(ColorUtil.color(
                            "&e┠ &6Шахта Древних (0 ~ 0) &8» &fРудная жила и древние жеоды обновятся через &e30 сек.&f!"
                    ));
                }

                if (secondsUntilFullRegen <= 0) {
                    // Сначала сбрасываем таймер, чтобы он НИКОГДА не зациклился!
                    secondsUntilFullRegen = getRegenIntervalSeconds();
                    regenCycleCounter++;
                    richVeinActive = (regenCycleCounter % 3 == 0);
                    try {
                        regenerateVeinsAndGeodes(false, true);
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Ошибка при обновлении жил Шахты Древних: " + t.getMessage());
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void notifyNearbyMiners(String message) {
        if (centerLocation == null || centerLocation.getWorld() == null) {
            return;
        }
        for (Player p : centerLocation.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(centerLocation) <= 65.0 * 65.0) {
                p.sendMessage(message);
            }
        }
    }

    private void stopTasks() {
        if (mainTickTask != null) {
            mainTickTask.cancel();
            mainTickTask = null;
        }
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
    }

    public void shutdownMine(boolean restoreTerrain) {
        stopTasks();
        removeBlacksmithAndHologram();
        if (restoreTerrain && centerLocation != null && centerLocation.getWorld() != null) {
            restoreOrCaptureTerrain(centerLocation.getWorld(), centerLocation.getBlockX(), centerLocation.getBlockZ(), true);
        }
        protectedBlocks.clear();
        activeGeodeBlocks.clear();
        mineableOreSpots.clear();
        defaultOreMaterials.clear();
        anvilStations.clear();
        mineActive = false;
    }

    /**
     * Обновляет все вскопанные рудные жилы (сохраняя родной тип жилы в каждом секторе, без «каши»!)
     * и пробуждает Древние жеоды. Пишет оповещение в чат (без БоссБара).
     */
    public void regenerateVeinsAndGeodes(boolean silent, boolean announceInChat) {
        if (centerLocation == null || centerLocation.getWorld() == null) {
            return;
        }
        World w = centerLocation.getWorld();
        int ox = centerLocation.getBlockX();
        int oy = centerLocation.getBlockY();
        int oz = centerLocation.getBlockZ();

        // 1. Восстанавливаем все рудные точки строго в их родной материал жилы (чтобы жилы были аккуратными!)
        for (BlockKey oreKey : mineableOreSpots) {
            Material veinMat = defaultOreMaterials.getOrDefault(oreKey, Material.DEEPSLATE_GOLD_ORE);
            Block b = w.getBlockAt(oreKey.x(), oreKey.y(), oreKey.z());
            if (b.getType() != veinMat) {
                b.setType(veinMat, false);
            }
        }

        // 2. Сбрасываем старые жеоды в красивый аметистовый блок (без белых пятен кальцита!)
        for (BlockKey oldKey : activeGeodeBlocks) {
            Block b = w.getBlockAt(oldKey.x(), oldKey.y(), oldKey.z());
            b.setType(Material.AMETHYST_BLOCK, false);
            Block above = b.getRelative(BlockFace.UP);
            if (above.getType() == Material.AMETHYST_CLUSTER || above.getType() == Material.LARGE_AMETHYST_BUD) {
                above.setType(Material.AIR, false);
            }
        }
        activeGeodeBlocks.clear();

        if (!geodePoolOffsets.isEmpty()) {
            List<Offset> shuffled = new ArrayList<>(geodePoolOffsets);
            java.util.Collections.shuffle(shuffled, ThreadLocalRandom.current());
            int count = Math.min(getActiveGeodesPerCycle(), shuffled.size());

            for (int i = 0; i < count; i++) {
                Offset off = shuffled.get(i);
                Block b = w.getBlockAt(ox + off.dx(), oy + off.dy(), oz + off.dz());
                b.setType(Material.BUDDING_AMETHYST, false);

                Block above = b.getRelative(BlockFace.UP);
                if (above.getType() == Material.AIR || above.getType() == Material.AMETHYST_CLUSTER) {
                    above.setType(Material.AMETHYST_CLUSTER, false);
                }
                activeGeodeBlocks.add(BlockKey.of(b));
            }
        }

        if (!silent) {
            w.playSound(centerLocation, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 1.05f);
            w.playSound(centerLocation, Sound.BLOCK_ANVIL_LAND, 0.55f, 0.85f);
            // Используем END_ROD вместо FLASH (т.к. FLASH в Purpur 26.2 требует org.bukkit.Color!)
            w.spawnParticle(Particle.END_ROD, centerLocation.clone().add(0, 2.8, 0), 25, 0.8, 0.8, 0.8, 0.04);
        }

        if (announceInChat) {
            if (richVeinActive) {
                Bukkit.broadcastMessage(ColorUtil.color(
                        "&e┠ &#FFB81C&lШахта Древних (0 ~ 0) &8» &fПробудилась &#34D711&lБОГАТАЯ РУДНАЯ ЖИЛА&f! В карьере обновились все руды и &d"
                                + activeGeodeBlocks.size() + " древних жеод&f!"
                ));
            } else {
                Bukkit.broadcastMessage(ColorUtil.color(
                        "&e┠ &#FFB81CШахта Древних (0 ~ 0) &8» &fРудные жилы обновились! Пробудилось &a"
                                + activeGeodeBlocks.size() + " древних жеод&f."
                ));
            }
        }
    }

    /**
     * Обрабатывает ломание блока игроком в «Шахте Древних»:
     * 1) Если это Древняя жеода или Кристалл аметиста — предмет жеоды падает НА ПОЛ!
     * 2) Если это рудная жила — кастомная руда падает НА ПОЛ, а блок временно становится глубинным сланцем!
     */
    public boolean handleMineBlockBreak(Player player, Block block) {
        if (!mineActive || centerLocation == null || !block.getWorld().equals(centerLocation.getWorld())) {
            return false;
        }
        BlockKey key = BlockKey.of(block);

        if (!activeGeodeBlocks.contains(key) && block.getType() == Material.AMETHYST_CLUSTER) {
            Block below = block.getRelative(BlockFace.DOWN);
            BlockKey belowKey = BlockKey.of(below);
            if (activeGeodeBlocks.contains(belowKey)) {
                mineGeodeNode(player, below, belowKey);
                return true;
            }
        }

        if (activeGeodeBlocks.contains(key)) {
            mineGeodeNode(player, block, key);
            return true;
        }

        if (mineableOreSpots.contains(key)) {
            MineOreType oreType = MineOreType.fromBlockMaterial(block.getType());
            if (oreType != null) {
                mineOreNode(player, block, oreType);
            }
            return true;
        }

        return isInsideMineProtection(block.getLocation());
    }

    private void mineGeodeNode(Player player, Block geodeBlock, BlockKey key) {
        if (!plugin.getBedrockService().canParticipate(player)) {
            player.sendMessage(plugin.getPluginConfig().getMsgOnlyBedrock());
            return;
        }

        activeGeodeBlocks.remove(key);
        geodeBlock.setType(Material.AMETHYST_BLOCK, false);
        Block above = geodeBlock.getRelative(BlockFace.UP);
        if (above.getType() == Material.AMETHYST_CLUSTER || above.getType() == Material.LARGE_AMETHYST_BUD) {
            above.setType(Material.AIR, false);
        }

        BrushTier tier = rollGeodeTierForPlayer(player);
        ItemStack geodeItem = plugin.getBrushLootManager().createGeodeItem(tier);

        Location dropLoc = geodeBlock.getLocation().add(0.5, 0.65, 0.5);
        geodeBlock.getWorld().dropItemNaturally(dropLoc, geodeItem);
        plugin.getMinerBackpackManager().incrementTotalMined(player.getUniqueId(), 1);

        World w = geodeBlock.getWorld();
        w.playSound(dropLoc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.15f);
        w.playSound(dropLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
        w.spawnParticle(Particle.WAX_OFF, dropLoc, 10, 0.25, 0.25, 0.25, 0.03);

        String tierLabel = switch (tier) {
            case COMMON -> "&aКварцевая жеода";
            case EPIC -> "&dАметистовая жеода";
            case LEGENDARY -> "&6Магмовая жеода Древних";
        };
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(ColorUtil.color(
                        "&eВыбили: " + tierLabel + " &8(&fупала на пол! Подберите в рюкзак&8)"
                ))
        );
    }

    private void mineOreNode(Player player, Block oreBlock, MineOreType oreType) {
        if (!plugin.getBedrockService().canParticipate(player)) {
            player.sendMessage(plugin.getPluginConfig().getMsgOnlyBedrock());
            return;
        }

        oreBlock.setType(Material.COBBLED_DEEPSLATE, false);

        ItemStack oreDrop = plugin.getMinerBackpackManager().createMineOreItem(oreType, 1);
        Location dropLoc = oreBlock.getLocation().add(0.5, 0.55, 0.5);
        oreBlock.getWorld().dropItemNaturally(dropLoc, oreDrop);
        plugin.getMinerBackpackManager().incrementTotalMined(player.getUniqueId(), 1);

        World w = oreBlock.getWorld();
        w.playSound(dropLoc, Sound.BLOCK_DEEPSLATE_BREAK, 1.0f, 1.1f);
        w.spawnParticle(Particle.CRIT, dropLoc, 8, 0.22, 0.22, 0.22, 0.04);

        int carried = plugin.getMinerBackpackManager().countCarriedMineItems(player);
        int maxCap = plugin.getMinerBackpackManager().getBackpackCapacity(player.getUniqueId());
        int lvl = plugin.getMinerBackpackManager().getBackpackLevel(player.getUniqueId());
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(ColorUtil.color(
                        "&eВскопано: " + oreType.getDisplayName() + " &8(&fна полу&8) &8| &6Рюкзак (Ур. " + lvl + "): &a" + carried + "&7/&a" + maxCap
                ))
        );
    }

    private BrushTier rollGeodeTierForPlayer(Player player) {
        int bonus = plugin.getMinerBackpackManager() != null
                ? plugin.getMinerBackpackManager().getGeodeBonusPercent(player.getUniqueId())
                : 0;
        int wCommon = Math.max(10, plugin.getConfig().getInt("ancient-mine.weights.common", 35) - bonus);
        int wEpic = Math.max(1, plugin.getConfig().getInt("ancient-mine.weights.epic", 40));
        int wLeg = Math.max(1, plugin.getConfig().getInt("ancient-mine.weights.legendary", 25) + bonus);
        int total = wCommon + wEpic + wLeg;
        int roll = ThreadLocalRandom.current().nextInt(total);
        if (roll < wCommon) {
            return BrushTier.COMMON;
        } else if (roll < wCommon + wEpic) {
            return BrushTier.EPIC;
        } else {
            return BrushTier.LEGENDARY;
        }
    }

    public boolean handleBlacksmithOrAnvilInteract(Player player) {
        if (!mineActive || centerLocation == null) {
            return false;
        }
        if (!plugin.getBedrockService().canParticipate(player)) {
            player.sendMessage(plugin.getPluginConfig().getMsgOnlyBedrock());
            return true;
        }
        plugin.getMinerBackpackManager().openForgeAndBackpackMenu(player);
        return true;
    }

    public boolean handleBlacksmithInteract(Player player) {
        return handleBlacksmithOrAnvilInteract(player);
    }

    public boolean isInsideMineTerritory(Location loc) {
        return isInsideMineProtection(loc);
    }

    public boolean isAnvilStation(Block block) {
        if (!mineActive || block == null) {
            return false;
        }
        if (anvilStations.contains(BlockKey.of(block))) {
            return true;
        }
        Material m = block.getType();
        if (m == Material.ANVIL || m == Material.CHIPPED_ANVIL || m == Material.DAMAGED_ANVIL
                || m == Material.BLAST_FURNACE || m == Material.SMITHING_TABLE) {
            return isInsideMineProtection(block.getLocation());
        }
        return false;
    }

    public boolean isBlacksmithNpc(Entity entity) {
        if (!(entity instanceof Villager)) {
            return false;
        }
        if (blacksmithUuid != null && blacksmithUuid.equals(entity.getUniqueId())) {
            return true;
        }
        return isInsideMineProtection(entity.getLocation());
    }

    public boolean isInsideMineProtection(Location loc) {
        if (!mineActive || centerLocation == null || loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!loc.getWorld().equals(centerLocation.getWorld())) {
            return false;
        }
        int dx = Math.abs(loc.getBlockX() - centerLocation.getBlockX());
        int dz = Math.abs(loc.getBlockZ() - centerLocation.getBlockZ());
        int dy = loc.getBlockY() - centerLocation.getBlockY();
        return dx <= MINE_RADIUS + 1 && dz <= MINE_RADIUS + 1 && dy >= -6 && dy <= 16;
    }

    public boolean isPlayerInsideMineZone(Location loc) {
        if (!mineActive || centerLocation == null || loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!loc.getWorld().equals(centerLocation.getWorld())) {
            return false;
        }
        double dx = loc.getX() - centerLocation.getX();
        double dz = loc.getZ() - centerLocation.getZ();
        double dy = loc.getY() - centerLocation.getY();
        return (dx * dx + dz * dz) <= (MINE_ZONE_LIMIT * MINE_ZONE_LIMIT) && dy >= -6.0 && dy <= 16.0;
    }

    // =========================================================================
    // 2.5-МЕТРОВЫЙ ДРЕВНИЙ КУЗНЕЦ НА ВЕРШИНЕ ПОДИУМА (centerY + 2.0)
    // =========================================================================

    public void spawnOrVerifyBlacksmith() {
        if (centerLocation == null || centerLocation.getWorld() == null) {
            return;
        }
        World w = centerLocation.getWorld();
        Location npcLoc = centerLocation.clone().add(0.0, 2.0, 0.0);
        npcLoc.setYaw(0.0f);
        npcLoc.setPitch(0.0f);

        // 1. Если в радиусе 64 блоков от 0 ~ 0 нет ни одного игрока — НЕ трогаем чанки и НЕ спавним НПС!
        //    Когда рядом нет игроков, чанки выгружают свои сущности (isEntitiesLoaded() == false),
        //    и попытка спавнить НПС каждую секунду накапливала сотни сущностей в чанке!
        boolean anyPlayerNear = false;
        for (Player p : w.getPlayers()) {
            if (p.isOnline() && p.getLocation().distanceSquared(npcLoc) <= 64.0 * 64.0) {
                anyPlayerNear = true;
                break;
            }
        }
        if (!anyPlayerNear) {
            return;
        }

        Chunk centerChunk = npcLoc.getChunk();
        if (!centerChunk.isLoaded() || !centerChunk.isEntitiesLoaded()) {
            return;
        }

        // 2. Подиум Кузнеца на (0 ~ 0) стоит ровно на стыке 4 чанков: (-1,-1), (-1,0), (0,-1) и (0,0)!
        //    Проходим по всем загруженным чанкам вокруг 0 ~ 0 (-2..+2), удаляем ВСЕ старые ArmorStand
        //    и оставляем ровно ОДНОГО жителя!
        int baseCx = centerLocation.getBlockX() >> 4;
        int baseCz = centerLocation.getBlockZ() >> 4;
        Villager validBlacksmith = null;

        for (int cx = baseCx - 2; cx <= baseCx + 2; cx++) {
            for (int cz = baseCz - 2; cz <= baseCz + 2; cz++) {
                if (!w.isChunkLoaded(cx, cz)) {
                    continue;
                }
                Chunk ch = w.getChunkAt(cx, cz);
                if (!ch.isEntitiesLoaded()) {
                    continue;
                }
                for (Entity entity : ch.getEntities()) {
                    if (entity instanceof Player || entity instanceof org.bukkit.entity.Item) {
                        continue;
                    }
                    if (entity instanceof ArmorStand stand) {
                        if (plugin.getMinerBackpackManager() == null
                                || !plugin.getMinerBackpackManager().isActiveMountedBackpackStand(stand)) {
                            stand.remove();
                        }
                        continue;
                    }
                    if (entity instanceof Villager v) {
                        if (!v.isValid() || v.isDead()) {
                            v.remove();
                            continue;
                        }
                        if (validBlacksmith == null
                                && ((blacksmithUuid != null && v.getUniqueId().equals(blacksmithUuid))
                                || v.getLocation().distanceSquared(npcLoc) <= 16.0)) {
                            validBlacksmith = v;
                            this.blacksmithUuid = v.getUniqueId();
                        } else {
                            v.remove();
                        }
                        continue;
                    }
                    if (isInsideMineProtection(entity.getLocation())) {
                        entity.remove();
                    }
                }
            }
        }

        if (validBlacksmith != null && validBlacksmith.isValid() && !validBlacksmith.isDead()) {
            // Если Кузнец уже стоит на месте — НЕ отправляем лишние пакеты атрибутов/имени каждую секунду!
            if (validBlacksmith.getLocation().distanceSquared(npcLoc) > 0.25) {
                validBlacksmith.teleport(npcLoc);
            }
            return;
        }

        removeBlacksmithAndHologram();

        Villager v = (Villager) w.spawnEntity(npcLoc, EntityType.VILLAGER);
        v.setProfession(Villager.Profession.WEAPONSMITH);
        v.setVillagerType(Villager.Type.TAIGA);
        v.setVillagerLevel(5);
        v.setRecipes(java.util.Collections.emptyList());
        v.setAdult();
        v.setAI(false);
        v.setAware(false);
        v.setGravity(false);
        v.setCollidable(false);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setRemoveWhenFarAway(false);
        v.setPersistent(false);
        v.setCustomName(ColorUtil.color("&#FFB81C&lДревний Кузнец &8[&aКузница и Рюкзак&8]"));
        v.setCustomNameVisible(true);

        BossAbilityController.applyEntityScale(v, 1.38);
        this.blacksmithUuid = v.getUniqueId();
    }

    private boolean areHologramsValid() {
        for (UUID id : hologramUuids) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid()) {
                return false;
            }
        }
        return true;
    }

    private void spawnHologramLine(Location loc, String text) {
        if (loc.getWorld() == null) {
            return;
        }
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setGravity(false);
            s.setMarker(true);
            s.setSmall(true);
            s.setBasePlate(false);
            s.setCustomName(text);
            s.setCustomNameVisible(true);
            s.setInvulnerable(true);
            s.setPersistent(false);
        });
        hologramUuids.add(stand.getUniqueId());
    }

    private void clearHologramsOnly() {
        for (UUID id : hologramUuids) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        hologramUuids.clear();
    }

    private void removeBlacksmithAndHologram() {
        if (blacksmithUuid != null) {
            Entity e = Bukkit.getEntity(blacksmithUuid);
            if (e != null) {
                e.remove();
            }
            blacksmithUuid = null;
        }
        clearHologramsOnly();
    }

    // =========================================================================
    // СХЕМАТИКА 45x45 «ШАХТА ДРЕВНИХ (ЖИЛА ЖЕОД)» (schema-version: 5)
    // =========================================================================

    private void ensureDefaultMineSchematic() {
        if (schematicFile.exists()) {
            YamlConfiguration existing = YamlConfiguration.loadConfiguration(schematicFile);
            if (existing.getInt("schema-version", 1) >= CURRENT_MINE_SCHEMA_VERSION) {
                return;
            }
            plugin.getLogger().info("Перезапись схемы ancient_mine.schem.yml до версии "
                    + CURRENT_MINE_SCHEMA_VERSION + " (цельная крыша и сгруппированные жилы руд)...");
        }

        YamlConfiguration yml = new YamlConfiguration();
        yml.set("schema-version", CURRENT_MINE_SCHEMA_VERSION);
        yml.set("name", "ancient_mine_45x45_v5");
        yml.set("size.width", 45);
        yml.set("size.length", 45);
        yml.set("size.height", 14);

        List<Map<String, Object>> serialized = new ArrayList<>();
        for (BlockEntry entry : buildDefaultSchematic()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dx", entry.dx());
            m.put("dy", entry.dy());
            m.put("dz", entry.dz());
            m.put("data", entry.blockData().getAsString());
            if (entry.geodeSpot()) {
                m.put("geode", true);
            }
            serialized.add(m);
        }
        yml.set("blocks", serialized);

        try {
            yml.save(schematicFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить ancient_mine.schem.yml: " + e.getMessage());
        }
    }

    public final void loadSchematic() {
        ensureDefaultMineSchematic();
        loadMineSchematic();
        initializePermanentMine();
    }

    public final void loadMineSchematic() {
        cachedSchematic.clear();
        geodePoolOffsets.clear();
        if (!schematicFile.exists()) {
            ensureDefaultMineSchematic();
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(schematicFile);
        List<Map<?, ?>> list = yml.getMapList("blocks");
        for (Map<?, ?> raw : list) {
            try {
                int dx = ((Number) raw.get("dx")).intValue();
                int dy = ((Number) raw.get("dy")).intValue();
                int dz = ((Number) raw.get("dz")).intValue();
                String dataStr = String.valueOf(raw.get("data"));
                BlockData data = Bukkit.createBlockData(dataStr);
                boolean geode = raw.containsKey("geode") && Boolean.TRUE.equals(raw.get("geode"));
                cachedSchematic.add(new BlockEntry(dx, dy, dz, data, geode));
                if (geode) {
                    geodePoolOffsets.add(new Offset(dx, dy, dz));
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Строит цельную, аккуратную и симметричную архитектуру 45x45 (schema-version: 5):
     * 1) Ровный каменный пол на dy = 0 (без ям, в которых висят столбы!).
     * 2) Полноценная сплошная шатровая крыша над Центральной Ротондой Кузни (-7..+7, dy = 6..10)
     *    на 8 мощных каменных колоннах — без дыр и без висящих в воздухе блоков!
     * 3) В 4 секторах (NW, NE, SW, SE) — аккуратные скальные гряды из глубинного сланца и туфа,
     *    в которые встроены СГРУППИРОВАННЫЕ ЖИЛЫ РУД (каждая жила состоит из 6-9 блоков одной породы,
     *    а не вразброс!).
     * 4) Цельные П-образные деревянные шахтёрские крепи (стойки на полу dy = 1..3 + сплошная перекладина dy = 4).
     */
    private List<BlockEntry> buildDefaultSchematic() {
        Map<String, BlockEntry> entries = new LinkedHashMap<>();

        // 0. Очистка воздуха над всей территорией 45x45
        for (int dx = -MINE_RADIUS; dx <= MINE_RADIUS; dx++) {
            for (int dz = -MINE_RADIUS; dz <= MINE_RADIUS; dz++) {
                if (Math.hypot(dx, dz) <= MINE_RADIUS + 0.5) {
                    for (int dy = 1; dy <= 11; dy++) {
                        add(entries, dx, dy, dz, Material.AIR);
                    }
                }
            }
        }

        // 1. Твёрдый фундамент и ровный каменный пол 45x45 (dy = -2..0)
        for (int dx = -MINE_RADIUS; dx <= MINE_RADIUS; dx++) {
            for (int dz = -MINE_RADIUS; dz <= MINE_RADIUS; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist > MINE_RADIUS + 0.5) {
                    continue;
                }
                int ax = Math.abs(dx);
                int az = Math.abs(dz);

                add(entries, dx, -2, dz, Material.DEEPSLATE_BRICKS);
                add(entries, dx, -1, dz, Material.POLISHED_DEEPSLATE);

                if (ax <= 6 && az <= 6) {
                    // Центральная площадь Ротонды Кузни
                    if (ax == 6 || az == 6 || ax == az) {
                        add(entries, dx, 0, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    } else {
                        add(entries, dx, 0, dz, Material.DEEPSLATE_TILES);
                    }
                } else if (ax <= 2 || az <= 2) {
                    // 4 главных проспекта (ширина 5 блоков)
                    if (ax == 0 || az == 0) {
                        add(entries, dx, 0, dz, Material.CHISELED_DEEPSLATE);
                    } else if (ax == 2 || az == 2) {
                        add(entries, dx, 0, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    } else {
                        add(entries, dx, 0, dz, Material.POLISHED_DEEPSLATE);
                    }
                } else {
                    // Пол в 4 шахтёрских секторах — ровный тёмный сланец и туф
                    if ((ax + az) % 3 == 0) {
                        add(entries, dx, 0, dz, Material.DEEPSLATE_BRICKS);
                    } else {
                        add(entries, dx, 0, dz, Material.COBBLED_DEEPSLATE);
                    }
                }
            }
        }

        // 2. Внешняя крепостная стена карьера (радиус 20.2..22.5) и 4 входные арки
        for (int dx = -MINE_RADIUS; dx <= MINE_RADIUS; dx++) {
            for (int dz = -MINE_RADIUS; dz <= MINE_RADIUS; dz++) {
                double dist = Math.hypot(dx, dz);
                int ax = Math.abs(dx);
                int az = Math.abs(dz);
                boolean isMainAvenue = (ax <= 2 || az <= 2);

                if (dist >= 20.2 && dist <= 22.5 && !isMainAvenue) {
                    add(entries, dx, 1, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    add(entries, dx, 2, dz, Material.DEEPSLATE_BRICKS);
                    add(entries, dx, 3, dz, Material.DEEPSLATE_BRICK_WALL);
                    if (dist >= 21.0 && (ax + az) % 4 == 0) {
                        add(entries, dx, 3, dz, Material.CHISELED_POLISHED_BLACKSTONE);
                        add(entries, dx, 4, dz, Material.SHROOMLIGHT);
                        add(entries, dx, 5, dz, Material.DEEPSLATE_TILE_SLAB);
                    }
                }
            }
        }

        // 4 парадных портала-арки на входах (Север, Юг, Запад, Восток)
        int[][] gateSigns = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] g : gateSigns) {
            for (int side : new int[]{-3, 3}) {
                int gx = g[0] != 0 ? g[0] * 21 : side;
                int gz = g[1] != 0 ? g[1] * 21 : side;
                for (int y = 1; y <= 5; y++) {
                    add(entries, gx, y, gz, y == 3 ? Material.CHISELED_DEEPSLATE : Material.POLISHED_BLACKSTONE_BRICKS);
                }
                add(entries, gx, 6, gz, Material.SHROOMLIGHT);
                add(entries, gx, 7, gz, Material.DEEPSLATE_BRICK_SLAB);
            }
            for (int span = -2; span <= 2; span++) {
                int bx = g[0] != 0 ? g[0] * 21 : span;
                int bz = g[1] != 0 ? g[1] * 21 : span;
                add(entries, bx, 6, bz, Material.POLISHED_BLACKSTONE_BRICKS);
                add(entries, bx, 7, bz, Material.DEEPSLATE_TILE_SLAB);
                if (span == 0) {
                    add(entries, bx, 5, bz, Material.CHAIN);
                    add(entries, bx, 4, bz, Material.SOUL_LANTERN);
                }
            }
        }

        // 3. Фонарные столбы вдоль главных проспектов
        for (int d : new int[]{11, 17}) {
            int[][] lampCoords = {
                    {-3, -d}, {3, -d}, {-3, d}, {3, d},
                    {-d, -3}, {-d, 3}, {d, -3}, {d, 3}
            };
            for (int[] lc : lampCoords) {
                add(entries, lc[0], 1, lc[1], Material.POLISHED_BLACKSTONE_BRICKS);
                add(entries, lc[0], 2, lc[1], Material.DEEPSLATE_BRICK_WALL);
                add(entries, lc[0], 3, lc[1], Material.DEEPSLATE_BRICK_WALL);
                add(entries, lc[0], 4, lc[1], Material.CHISELED_DEEPSLATE);
                add(entries, lc[0], 5, lc[1], Material.LANTERN);
            }
        }

        // 4. Четыре шахтёрских сектора со СГРУППИРОВАННЫМИ ЖИЛАМИ РУД и цельными скалами:
        //    - Сектор NW (-1, -1): Медная жила + Железная жила
        //    - Сектор NE (+1, -1): Золотая жила + Позолоченный чернит
        //    - Сектор SW (-1, +1): Изумрудная жила + Золотая жила
        //    - Сектор SE (+1, +1): Алмазная жила + Древние обломки
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                Material primaryOre;
                Material secondaryOre;
                if (sx < 0 && sz < 0) {
                    primaryOre = Material.DEEPSLATE_IRON_ORE;
                    secondaryOre = Material.DEEPSLATE_COPPER_ORE;
                } else if (sx > 0 && sz < 0) {
                    primaryOre = Material.DEEPSLATE_GOLD_ORE;
                    secondaryOre = Material.GILDED_BLACKSTONE;
                } else if (sx < 0 && sz > 0) {
                    primaryOre = Material.DEEPSLATE_EMERALD_ORE;
                    secondaryOre = Material.DEEPSLATE_GOLD_ORE;
                } else {
                    primaryOre = Material.DEEPSLATE_DIAMOND_ORE;
                    secondaryOre = Material.ANCIENT_DEBRIS;
                }

                // 4a. Внешняя скальная порода сектора (чистый сланец и туф, без «конфетти»!)
                for (int ax = 6; ax <= 19; ax++) {
                    for (int az = 6; az <= 19; az++) {
                        double dist = Math.hypot(ax, az);
                        if (dist < 14.5 || dist > 19.8) {
                            continue;
                        }
                        int dx = sx * ax;
                        int dz = sz * az;
                        int height = dist >= 18.0 ? 3 : (dist >= 16.2 ? 2 : 1);
                        for (int y = 1; y <= height; y++) {
                            Material rock = ((ax + az + y) % 3 == 0) ? Material.TUFF : Material.COBBLED_DEEPSLATE;
                            add(entries, dx, y, dz, rock);
                        }
                    }
                }

                // 4b. Сгруппированная Жила №1 (вдоль северной/южной скальной стены сектора, ax = 7..11, az = 13..15)
                int[][] vein1Offsets = {
                        {7, 1, 13}, {8, 1, 13}, {9, 1, 13}, {10, 1, 13},
                        {8, 1, 14}, {9, 1, 14}, {10, 1, 14}, {11, 1, 14},
                        {8, 2, 14}, {9, 2, 14}, {10, 2, 14},
                        {9, 2, 15}, {10, 2, 15}
                };
                for (int[] v : vein1Offsets) {
                    add(entries, sx * v[0], v[1], sz * v[2], primaryOre);
                }

                // 4c. Сгруппированная Жила №2 (вдоль западной/восточной скальной стены сектора, ax = 13..15, az = 7..11)
                int[][] vein2Offsets = {
                        {13, 1, 7}, {13, 1, 8}, {13, 1, 9}, {13, 1, 10},
                        {14, 1, 8}, {14, 1, 9}, {14, 1, 10}, {14, 1, 11},
                        {14, 2, 8}, {14, 2, 9}, {14, 2, 10},
                        {15, 2, 9}, {15, 2, 10}
                };
                for (int[] v : vein2Offsets) {
                    add(entries, sx * v[0], v[1], sz * v[2], secondaryOre);
                }

                // 4d. Центральный рудный утёс внутри сектора (ax = 8..10, az = 8..10) — цельная жила!
                for (int ax = 8; ax <= 10; ax++) {
                    for (int az = 8; az <= 10; az++) {
                        if (ax == 10 && az == 10) {
                            continue;
                        }
                        add(entries, sx * ax, 1, sz * az, primaryOre);
                    }
                }
                add(entries, sx * 9, 2, sz * 9, primaryOre);
                add(entries, sx * 8, 2, sz * 9, secondaryOre);
                add(entries, sx * 9, 2, sz * 8, secondaryOre);

                // 4e. Две цельные П-образные деревянные шахтёрские крепи (стойки на полу dy=1..3 + сплошная балка dy=4)
                // Крепь №1: от (sx*7, sz*12) до (sx*11, sz*12)
                for (int y = 1; y <= 3; y++) {
                    add(entries, sx * 7, y, sz * 12, Material.DARK_OAK_LOG);
                    add(entries, sx * 11, y, sz * 12, Material.DARK_OAK_LOG);
                }
                for (int span = 7; span <= 11; span++) {
                    add(entries, sx * span, 4, sz * 12, Material.DARK_OAK_PLANKS);
                }
                add(entries, sx * 9, 3, sz * 12, Material.LANTERN);

                // Крепь №2: от (sx*12, sz*7) до (sx*12, sz*11)
                for (int y = 1; y <= 3; y++) {
                    add(entries, sx * 12, y, sz * 7, Material.DARK_OAK_LOG);
                    add(entries, sx * 12, y, sz * 11, Material.DARK_OAK_LOG);
                }
                for (int span = 7; span <= 11; span++) {
                    add(entries, sx * 12, 4, sz * span, Material.DARK_OAK_PLANKS);
                }
                add(entries, sx * 12, 3, sz * 9, Material.LANTERN);
            }
        }

        // 5. Аметистовые гнезда Древних жеод (24 точки в красивых аметистовых друзах)
        int[][] geodeCoords = {
                {-7, 1, -7}, {7, 1, -7}, {-7, 1, 7}, {7, 1, 7},
                {-11, 1, -11}, {11, 1, -11}, {-11, 1, 11}, {11, 1, 11},
                {-12, 1, -13}, {12, 1, -13}, {-12, 1, 13}, {12, 1, 13},
                {-13, 1, -12}, {13, 1, -12}, {-13, 1, 12}, {13, 1, 12},
                {-15, 2, -11}, {15, 2, -11}, {-15, 2, 11}, {15, 2, 11},
                {-11, 2, -15}, {11, 2, -15}, {-11, 2, 15}, {11, 2, 15}
        };
        for (int[] gc : geodeCoords) {
            addGeodeSpot(entries, gc[0], gc[1], gc[2], Material.BUDDING_AMETHYST);
            add(entries, gc[0], gc[1] + 1, gc[2], Material.AMETHYST_CLUSTER);
        }

        // 6. Центральный Подиум Древнего Кузнеца на 0 ~ 0 (dy = 1)
        //    На (0, 1, 0) — цельный блок CHISELED_POLISHED_BLACKSTONE, а ступени вокруг ведут ВВЕРХ к центру!
        add(entries, 0, 1, 0, Material.CHISELED_POLISHED_BLACKSTONE);
        addStair(entries, 0, 1, -1, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.SOUTH);
        addStair(entries, 0, 1, 1, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.NORTH);
        addStair(entries, -1, 1, 0, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.EAST);
        addStair(entries, 1, 1, 0, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.WEST);
        add(entries, -1, 1, -1, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        add(entries, 1, 1, -1, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        add(entries, -1, 1, 1, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        add(entries, 1, 1, 1, Material.POLISHED_BLACKSTONE_BRICK_SLAB);

        // 7. 4 Кузнечные Станции по углам площади и 8 Колонн Центральной Ротонды (dy = 1..6)
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                add(entries, sx * 4, 1, sz * 4, Material.ANVIL);
                add(entries, sx * 5, 1, sz * 4, Material.LODESTONE);
                add(entries, sx * 4, 1, sz * 5, Material.SMITHING_TABLE);
                add(entries, sx * 5, 1, sz * 5, Material.CHISELED_POLISHED_BLACKSTONE);

                // Угловые несущие колонны ротонды на (±6, ±6), dy = 1..6
                for (int y = 1; y <= 6; y++) {
                    add(entries, sx * 6, y, sz * 6,
                            (y == 1 || y == 6) ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                }
                // Промежуточные колонны арок на (±6, ±3) и (±3, ±6), dy = 1..6
                for (int y = 1; y <= 6; y++) {
                    Material colMat = (y == 1 || y == 6) ? Material.CHISELED_DEEPSLATE : Material.DEEPSLATE_BRICK_WALL;
                    add(entries, sx * 6, y, sz * 3, colMat);
                    add(entries, sx * 3, y, sz * 6, colMat);
                }
            }
        }

        // 8. ПОЛНОЦЕННАЯ СПЛОШНАЯ СТУПЕНЧАТАЯ КРЫША РОТОНДЫ (dy = 6..10, без единой дыры и висящих блоков!)
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                int ax = Math.abs(dx);
                int az = Math.abs(dz);
                int maxA = Math.max(ax, az);

                if (maxA == 7) {
                    // Внешний карниз крыши (dy = 6)
                    add(entries, dx, 6, dz, Material.DEEPSLATE_TILE_SLAB);
                } else if (maxA == 6) {
                    // Несущее кольцо балок над колоннами (dy = 6) + первый ярус ската (dy = 7)
                    add(entries, dx, 6, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    add(entries, dx, 7, dz, Material.DEEPSLATE_TILE_SLAB);
                } else if (maxA == 5 || maxA == 4) {
                    // Второй сплошной ярус крыши (dy = 7)
                    add(entries, dx, 7, dz, Material.DEEPSLATE_TILES);
                    if (maxA == 4) {
                        add(entries, dx, 8, dz, Material.DEEPSLATE_TILE_SLAB);
                    }
                } else if (maxA == 3 || maxA == 2) {
                    // Третий сплошной ярус крыши (dy = 8)
                    add(entries, dx, 8, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    if (maxA == 2) {
                        add(entries, dx, 9, dz, Material.DEEPSLATE_TILE_SLAB);
                    }
                } else {
                    // Центральный купол крыши (maxA <= 1, dy = 9)
                    add(entries, dx, 9, dz, Material.CHISELED_POLISHED_BLACKSTONE);
                    add(entries, dx, 10, dz, Material.DEEPSLATE_TILE_SLAB);
                }
            }
        }
        // Люстра под куполом в центре ротонды
        add(entries, 0, 9, 0, Material.SHROOMLIGHT);
        add(entries, 0, 8, 0, Material.CHAIN);
        add(entries, 0, 7, 0, Material.SOUL_LANTERN);

        return new ArrayList<>(entries.values());
    }

    private void add(Map<String, BlockEntry> map, int dx, int dy, int dz, Material mat) {
        map.put(dx + ":" + dy + ":" + dz, new BlockEntry(dx, dy, dz, Bukkit.createBlockData(mat), false));
    }

    private void addGeodeSpot(Map<String, BlockEntry> map, int dx, int dy, int dz, Material mat) {
        map.put(dx + ":" + dy + ":" + dz, new BlockEntry(dx, dy, dz, Bukkit.createBlockData(mat), true));
    }

    private void addStair(Map<String, BlockEntry> map, int dx, int dy, int dz, Material mat, BlockFace facing) {
        BlockData data = Bukkit.createBlockData(mat);
        if (data instanceof Directional dir) {
            dir.setFacing(facing);
        }
        map.put(dx + ":" + dy + ":" + dz, new BlockEntry(dx, dy, dz, data, false));
    }

    private void pasteMineSchematic(Location origin) {
        World w = origin.getWorld();
        if (w == null) {
            return;
        }
        protectedBlocks.clear();
        activeGeodeBlocks.clear();
        mineableOreSpots.clear();
        defaultOreMaterials.clear();
        anvilStations.clear();

        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        for (int dx = -MINE_RADIUS - 1; dx <= MINE_RADIUS + 1; dx++) {
            for (int dz = -MINE_RADIUS - 1; dz <= MINE_RADIUS + 1; dz++) {
                if (Math.hypot(dx, dz) <= MINE_RADIUS + 1.5) {
                    for (int dy = 1; dy <= 14; dy++) {
                        w.getBlockAt(ox + dx, oy + dy, oz + dz).setType(Material.AIR, false);
                    }
                }
            }
        }

        for (BlockEntry entry : cachedSchematic) {
            int x = ox + entry.dx();
            int y = oy + entry.dy();
            int z = oz + entry.dz();
            Block block = w.getBlockAt(x, y, z);
            block.setBlockData(entry.blockData(), false);

            BlockKey key = BlockKey.of(w, x, y, z);
            Material m = entry.blockData().getMaterial();
            if (m != Material.AIR) {
                protectedBlocks.add(key);
            }
            if (m == Material.ANVIL || m == Material.CHIPPED_ANVIL || m == Material.DAMAGED_ANVIL) {
                anvilStations.add(key);
            }
            if (!entry.geodeSpot() && MineOreType.fromBlockMaterial(m) != null) {
                mineableOreSpots.add(key);
                defaultOreMaterials.put(key, m);
            }
        }
    }

    private void restoreOrCaptureTerrain(World world, int centerX, int centerZ, boolean forceRestoreOnly) {
        if (backupFile.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(backupFile);
            ConfigurationSection sec = yml.getConfigurationSection("blocks");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    try {
                        String[] parts = key.split("_");
                        int x = Integer.parseInt(parts[0]);
                        int y = Integer.parseInt(parts[1]);
                        int z = Integer.parseInt(parts[2]);
                        BlockData data = Bukkit.createBlockData(sec.getString(key, "minecraft:air"));
                        world.getBlockAt(x, y, z).setBlockData(data, false);
                    } catch (Exception ignored) {
                    }
                }
            }
            if (forceRestoreOnly) {
                return;
            }
        }

        if (forceRestoreOnly) {
            return;
        }

        int centerY = resolveGroundY(world, centerX, centerZ);
        YamlConfiguration backup = new YamlConfiguration();
        backup.set("backup-schema-version", CURRENT_MINE_SCHEMA_VERSION);
        backup.set("world", world.getName());
        backup.set("center-x", centerX);
        backup.set("center-y", centerY);
        backup.set("center-z", centerZ);

        ConfigurationSection sec = backup.createSection("blocks");
        for (int dx = -MINE_RADIUS - 1; dx <= MINE_RADIUS + 1; dx++) {
            for (int dz = -MINE_RADIUS - 1; dz <= MINE_RADIUS + 1; dz++) {
                for (int dy = -3; dy <= 14; dy++) {
                    int x = centerX + dx;
                    int y = centerY + dy;
                    int z = centerZ + dz;
                    Block b = world.getBlockAt(x, y, z);
                    sec.set(x + "_" + y + "_" + z, b.getBlockData().getAsString());
                }
            }
        }
        try {
            backup.save(backupFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить бэкап ландшафта Шахты Древних: " + e.getMessage());
        }
    }

    public void regenerateAllOresAndGeodes() {
        regenerateVeinsAndGeodes(false, true);
        this.secondsUntilFullRegen = getRegenIntervalSeconds();
    }

    public void spawnMineAt(Location targetLoc, boolean saveToConfig) {
        if (targetLoc == null || targetLoc.getWorld() == null) {
            return;
        }
        shutdownMine(true);
        if (saveToConfig) {
            plugin.getConfig().set("ancient-mine.world", targetLoc.getWorld().getName());
            plugin.getConfig().set("ancient-mine.center-x", targetLoc.getBlockX());
            plugin.getConfig().set("ancient-mine.center-y", targetLoc.getBlockY());
            plugin.getConfig().set("ancient-mine.center-z", targetLoc.getBlockZ());
            plugin.saveConfig();
        }
        if (backupFile.exists()) {
            backupFile.delete();
        }
        initializePermanentMine();
    }

    public boolean isMineActive() {
        return mineActive;
    }

    public Location getCenterLocation() {
        return centerLocation;
    }

    public int getActiveGeodeCount() {
        return activeGeodeBlocks.size();
    }

    public int getSecondsUntilFullRegen() {
        return secondsUntilFullRegen;
    }
}
