package ru.geyserevent.mine;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.geyserevent.GeyserEventPlugin;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Постоянный анархический ивент «Шахта Древних (Жила Жеод)» v1.8.2 (schema-version: 4).
 *
 * Полностью обновлённая архитектура и механики:
 * 1) Расположен постоянно в центре мира (по умолчанию 0 ~ 0 в мире ивентов world2).
 * 2) При загрузке плагина автоматически удаляет старого НПС, старые голограммы, старые забаганные
 *    рюкзаки, откатывает старую постройку и перезаписывает новую схематику (schema-version: 4).
 * 3) Красивая симметричная архитектура 45x45 (MINE_RADIUS = 22, зона = 24.5):
 *    - Чистые каменные проспекты с дварфийскими фонарями (без «каши» из руд на полу);
 *    - Просторная Центральная Площадь Кузни: Древний Кузнец стоит свободно на подиуме в центре,
 *      а 4 кузнечных горна расположены по углам ротонды под высоким куполом;
 *    - 4 углублённых котлована карьера (NW, NE, SW, SE) со ступенями вниз и амфитеатром
 *      скальных рудных уступов по краям и П-образными деревянными крепями.
 * 4) БоссБар убран — оповещения об обновлении рудной жилы пишутся в чат.
 * 5) При вскапывании руды или жеоды предмет выпадает НА ПОЛ, а при смерти игрока вся его руда выпадает на пол.
 */
public class AncientMineManager {

    private static final int CURRENT_MINE_SCHEMA_VERSION = 4;
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
     * Гарантированно удаляет старого НПС, старые голограммы, старые стойки рюкзаков и перезаписывает схему.
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

        // Загружаем чанки вокруг 0 ~ 0
        for (int cx = (centerX - MINE_RADIUS - 16) >> 4; cx <= (centerX + MINE_RADIUS + 16) >> 4; cx++) {
            for (int cz = (centerZ - MINE_RADIUS - 16) >> 4; cz <= (centerZ + MINE_RADIUS + 16) >> 4; cz++) {
                Chunk ch = world.getChunkAt(cx, cz);
                if (!ch.isLoaded()) {
                    ch.load(true);
                }
            }
        }

        // 1. Если есть бэкап от прошлой версии схемы, сначала откатываем его, чтобы не осталось старых блоков!
        if (backupFile.exists()) {
            YamlConfiguration backupYml = YamlConfiguration.loadConfiguration(backupFile);
            int backupSchemaVer = backupYml.getInt("backup-schema-version", 1);
            if (backupSchemaVer < CURRENT_MINE_SCHEMA_VERSION) {
                plugin.getLogger().info("Обнаружена старая постройка Шахты Древних (v" + backupSchemaVer
                        + "). Выполняется полный откат и перезапись на новую архитектуру v" + CURRENT_MINE_SCHEMA_VERSION + "...");
                restoreOrCaptureTerrain(world, centerX, centerZ, true);
                backupFile.delete();
            }
        }

        int centerY = resolveGroundY(world, centerX, centerZ);
        this.centerLocation = new Location(world, centerX + 0.5, centerY, centerZ + 0.5);

        // 2. Полная очистка всех старых НПС, голограмм и забаганных рюкзаков вокруг 0 ~ 0
        purgeOldMineEntities(world, centerX, centerY, centerZ);
        if (plugin.getMinerBackpackManager() != null) {
            plugin.getMinerBackpackManager().purgeOrphanedBackpackStands();
        }

        // 3. Сохраняем чистый ландшафт (или восстанавливаем перед установкой) и ставим новую схему v4
        restoreOrCaptureTerrain(world, centerX, centerZ, false);
        pasteMineSchematic(this.centerLocation);

        // 4. Спавним нового 2-метрового Древнего Кузнеца и чистую голограмму
        spawnOrVerifyBlacksmith();
        regenerateVeinsAndGeodes(true, false);

        this.secondsUntilFullRegen = getRegenIntervalSeconds();
        this.mineActive = true;
        startTasks();

        plugin.getLogger().info("Постоянный ивент «Шахта Древних (45x45, v4)» активен на координатах "
                + centerX + " " + centerY + " " + centerZ + " (" + world.getName() + ")!");
    }

    /**
     * Удаляет ВСЕХ старых жителей (Кузнецов), старые голограммы и старые стойки с рюкзаками
     * в радиусе 55 блоков от центра 0 ~ 0 при запуске плагина.
     */
    private void purgeOldMineEntities(World world, int centerX, int centerY, int centerZ) {
        Location center = new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
        for (Entity entity : world.getNearbyEntities(center, 55.0, 35.0, 55.0)) {
            if (entity instanceof Villager) {
                entity.remove();
            } else if (entity instanceof ArmorStand stand) {
                if (!stand.isVisible() || stand.isMarker() || stand.isCustomNameVisible()) {
                    stand.remove();
                }
            }
        }
        blacksmithUuid = null;
        hologramUuids.clear();
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
        int base = Math.max(12, plugin.getConfig().getInt("ancient-mine.geodes-per-cycle", 26));
        return richVeinActive ? (int) Math.round(base * 1.6) : base;
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
                    regenCycleCounter++;
                    richVeinActive = (regenCycleCounter % 3 == 0);
                    regenerateVeinsAndGeodes(false, true);
                    secondsUntilFullRegen = getRegenIntervalSeconds();
                } else if (secondsUntilFullRegen % 25 == 0 && activeGeodeBlocks.size() < 6) {
                    regenerateVeinsAndGeodes(false, false);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!mineActive || centerLocation == null || centerLocation.getWorld() == null) {
                    return;
                }
                World w = centerLocation.getWorld();
                boolean anyPlayerNear = false;
                for (Player p : w.getPlayers()) {
                    if (p.getLocation().distanceSquared(centerLocation) <= 42.0 * 42.0) {
                        anyPlayerNear = true;
                        break;
                    }
                }
                if (!anyPlayerNear) {
                    return;
                }

                for (BlockKey key : activeGeodeBlocks) {
                    Location pLoc = new Location(w, key.x() + 0.5, key.y() + 1.05, key.z() + 0.5);
                    w.spawnParticle(Particle.END_ROD, pLoc, 1, 0.18, 0.15, 0.18, 0.005);
                    w.spawnParticle(Particle.WAX_ON, pLoc, 1, 0.22, 0.2, 0.22, 0.01);
                }
            }
        }.runTaskTimer(plugin, 25L, 25L);
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
     * Обновляет все рудные точки и пробуждает Древние жеоды.
     * Пишет оповещение в чат (без БоссБара!).
     */
    public void regenerateVeinsAndGeodes(boolean silent, boolean announceInChat) {
        if (centerLocation == null || centerLocation.getWorld() == null) {
            return;
        }
        World w = centerLocation.getWorld();
        int ox = centerLocation.getBlockX();
        int oy = centerLocation.getBlockY();
        int oz = centerLocation.getBlockZ();

        // 1. Восстанавливаем все вскопанные рудные блоки
        for (BlockKey oreKey : mineableOreSpots) {
            Material baseMat = defaultOreMaterials.getOrDefault(oreKey, Material.DEEPSLATE_GOLD_ORE);
            Block b = w.getBlockAt(oreKey.x(), oreKey.y(), oreKey.z());
            b.setType( rollFreshOreMaterial(baseMat), false);
        }

        // 2. Сбрасываем старые жеоды
        for (BlockKey oldKey : activeGeodeBlocks) {
            Block b = w.getBlockAt(oldKey.x(), oldKey.y(), oldKey.z());
            b.setType(Material.CALCITE, false);
            Block above = b.getRelative(BlockFace.UP);
            if (above.getType() == Material.AMETHYST_CLUSTER || above.getType() == Material.LARGE_AMETHYST_BUD) {
                above.setType(Material.AIR, false);
            }
        }
        activeGeodeBlocks.clear();

        if (geodePoolOffsets.isEmpty()) {
            return;
        }

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

        if (!silent) {
            w.playSound(centerLocation, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 1.05f);
            w.playSound(centerLocation, Sound.BLOCK_ANVIL_LAND, 0.55f, 0.85f);
            w.spawnParticle(Particle.FLASH, centerLocation.clone().add(0, 2.5, 0), 2, 0.5, 0.5, 0.5, 0.0);
        }

        if (announceInChat) {
            if (richVeinActive) {
                Bukkit.broadcastMessage(ColorUtil.color(
                        "&e┠ &#FFB81C&lШахта Древних (0 ~ 0) &8» &fПробудилась &#34D711&lБОГАТАЯ РУДНАЯ ЖИЛА&f! В карьере появилось &d"
                                + activeGeodeBlocks.size() + " древних жеод &fи редкие руды!"
                ));
            } else {
                Bukkit.broadcastMessage(ColorUtil.color(
                        "&e┠ &#FFB81CШахта Древних (0 ~ 0) &8» &fРудная жила обновилась! Пробудилось &a"
                                + activeGeodeBlocks.size() + " древних жеод&f."
                ));
            }
        }
    }

    private Material rollFreshOreMaterial(Material fallback) {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (richVeinActive) {
            if (roll < 18) return Material.DEEPSLATE_COPPER_ORE;
            if (roll < 38) return Material.DEEPSLATE_IRON_ORE;
            if (roll < 60) return Material.DEEPSLATE_GOLD_ORE;
            if (roll < 80) return Material.DEEPSLATE_EMERALD_ORE;
            if (roll < 94) return Material.DEEPSLATE_DIAMOND_ORE;
            return Material.ANCIENT_DEBRIS;
        } else {
            if (roll < 28) return Material.DEEPSLATE_COPPER_ORE;
            if (roll < 54) return Material.DEEPSLATE_IRON_ORE;
            if (roll < 76) return Material.DEEPSLATE_GOLD_ORE;
            if (roll < 90) return Material.DEEPSLATE_EMERALD_ORE;
            if (roll < 98) return Material.DEEPSLATE_DIAMOND_ORE;
            return Material.ANCIENT_DEBRIS;
        }
    }

    /**
     * Обрабатывает ломание блока игроком в «Шахте Древних»:
     * 1) Если это Древняя жеода или Кристалл аметиста — предмет жеоды падает НА ПОЛ!
     * 2) Если это рудная жила — кастомная руда падает НА ПОЛ, а блок временно становится булыжником!
     */
    public boolean handleMineBlockBreak(Player player, Block block) {
        if (!mineActive || centerLocation == null || !block.getWorld().equals(centerLocation.getWorld())) {
            return false;
        }
        BlockKey key = BlockKey.of(block);

        // Проверяем, не сломал ли игрок кристалл аметиста прямо НАД жеодой
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

        // Проверяем, не является ли этот блок рудной точкой шахты
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
        geodeBlock.setType(Material.COBBLED_DEEPSLATE, false);
        Block above = geodeBlock.getRelative(BlockFace.UP);
        if (above.getType() == Material.AMETHYST_CLUSTER || above.getType() == Material.LARGE_AMETHYST_BUD) {
            above.setType(Material.AIR, false);
        }

        BrushTier tier = rollGeodeTierForPlayer(player);
        ItemStack geodeItem = plugin.getBrushLootManager().createGeodeItem(tier);

        // Предмет жеоды падает НА ПОЛ!
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

        // Руда падает НА ПОЛ, а не сразу в инвентарь!
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

    /**
     * Открывает GUI-меню «Кузница Древних и Прокачка Рюкзака» при клике по Древнему Кузнецу или наковальне.
     */
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
        return anvilStations.contains(BlockKey.of(block));
    }

    public boolean isBlacksmithNpc(Entity entity) {
        return entity != null && blacksmithUuid != null && blacksmithUuid.equals(entity.getUniqueId());
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
    // 2-МЕТРОВЫЙ ДРЕВНИЙ КУЗНЕЦ И ЕДИНАЯ ЧИСТАЯ ГОЛОГРАММА НА 0 ~ 0
    // =========================================================================

    private void spawnOrVerifyBlacksmith() {
        if (centerLocation == null || centerLocation.getWorld() == null) {
            return;
        }
        World w = centerLocation.getWorld();
        Entity existing = blacksmithUuid != null ? Bukkit.getEntity(blacksmithUuid) : null;
        Location npcLoc = centerLocation.clone().add(0.0, 1.0, 0.0);
        npcLoc.setYaw(0.0f);
        npcLoc.setPitch(0.0f);

        if (!(existing instanceof Villager villager) || !existing.isValid() || existing.isDead()) {
            // Перед созданием нового Кузнеца удаляем всех старых жителей и старые голограммы в центре!
            removeBlacksmithAndHologram();
            for (Entity near : w.getNearbyEntities(npcLoc, 8.0, 8.0, 8.0)) {
                if (near instanceof Villager) {
                    near.remove();
                } else if (near instanceof ArmorStand st && st.isCustomNameVisible()) {
                    st.remove();
                }
            }

            Villager v = (Villager) w.spawnEntity(npcLoc, EntityType.VILLAGER);
            v.setProfession(Villager.Profession.WEAPONSMITH);
            v.setVillagerType(Villager.Type.TAIGA);
            v.setVillagerLevel(5);
            v.setAdult();
            v.setAI(false);
            v.setCollidable(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setRemoveWhenFarAway(false);
            v.setPersistent(true);
            // Имя самого жителя выключаем, чтобы оно не накладывалось на 3-строчную голограмму сверху
            v.setCustomNameVisible(false);

            Attribute scaleAttr = resolveScaleAttribute();
            if (scaleAttr != null) {
                AttributeInstance inst = v.getAttribute(scaleAttr);
                if (inst != null) {
                    inst.setBaseValue(1.12);
                }
            }
            v.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
            this.blacksmithUuid = v.getUniqueId();
        } else {
            if (existing.getLocation().distanceSquared(npcLoc) > 0.25) {
                existing.teleport(npcLoc);
            }
        }

        // Проверяем и обновляем ровно 3 строки голограммы высоко над головой Кузнеца (без наложений!)
        if (hologramUuids.size() != 3 || !areHologramsValid()) {
            clearHologramsOnly();
            // Дополнительно чистим любые чужие/старые голограммы над подиумом
            for (Entity near : w.getNearbyEntities(npcLoc, 6.0, 6.0, 6.0)) {
                if (near instanceof ArmorStand st && st.isCustomNameVisible()) {
                    st.remove();
                }
            }
            spawnHologramLine(npcLoc.clone().add(0, 2.95, 0),
                    ColorUtil.color("&#FFB81C&lДревний Кузнец &8[&6Кузница и Рюкзак&8]"));
            spawnHologramLine(npcLoc.clone().add(0, 2.62, 0),
                    ColorUtil.color("&fЖеод в жиле: &a" + activeGeodeBlocks.size() + " шт. &8| &fПлавка руды и Прокачка"));
            spawnHologramLine(npcLoc.clone().add(0, 2.30, 0),
                    ColorUtil.color("&#34D711» Нажмите ПКМ / ЛКМ, чтобы открыть меню «"));
        } else {
            Entity line2 = Bukkit.getEntity(hologramUuids.get(1));
            if (line2 != null) {
                line2.setCustomName(ColorUtil.color(
                        "&fЖеод в жиле: &a" + activeGeodeBlocks.size() + " шт. &8| &fПлавка руды и Прокачка"
                ));
            }
        }
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
    // СХЕМАТИКА 45x45 «ШАХТА ДРЕВНИХ (ЖИЛА ЖЕОД)» (schema-version: 4)
    // =========================================================================

    private void ensureDefaultMineSchematic() {
        if (schematicFile.exists()) {
            YamlConfiguration existing = YamlConfiguration.loadConfiguration(schematicFile);
            if (existing.getInt("schema-version", 1) >= CURRENT_MINE_SCHEMA_VERSION) {
                return;
            }
            plugin.getLogger().info("Перезапись схемы ancient_mine.schem.yml до версии "
                    + CURRENT_MINE_SCHEMA_VERSION + " (красивая архитектура 45x45)...");
        }

        YamlConfiguration yml = new YamlConfiguration();
        yml.set("schema-version", CURRENT_MINE_SCHEMA_VERSION);
        yml.set("name", "ancient_mine_45x45_v4");
        yml.set("size.width", 45);
        yml.set("size.length", 45);
        yml.set("size.height", 16);

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
     * Генерирует красивую, просторную и симметричную архитектуру 45x45 (радиус 22, schema-version: 4):
     * - Чистые каменные проспекты и центральная площадь (без хаоса из руд под ногами);
     * - Просторный подиум Древнего Кузнеца в центре (0, 1, 0), к которому свободный подход со всех сторон;
     * - 4 кузнечных горна на углах площади (±5, 1, ±5) и высокий купол ротонды (dy = 7..10);
     * - 4 углублённых карьерных котлована (dy = -1) со ступенями вниз и скальными рудными уступами по внешнему периметру.
     */
    private List<BlockEntry> buildDefaultSchematic() {
        Map<String, BlockEntry> entries = new LinkedHashMap<>();

        // 0. Предварительная очистка воздуха над всей территорией 45x45 (чтобы убрать деревья/траву)
        for (int dx = -MINE_RADIUS; dx <= MINE_RADIUS; dx++) {
            for (int dz = -MINE_RADIUS; dz <= MINE_RADIUS; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist <= MINE_RADIUS + 0.5) {
                    for (int dy = 1; dy <= 11; dy++) {
                        add(entries, dx, dy, dz, Material.AIR);
                    }
                }
            }
        }

        // 1. Чистый архитектурный пол 45x45 (dy = -2..0)
        for (int dx = -MINE_RADIUS; dx <= MINE_RADIUS; dx++) {
            for (int dz = -MINE_RADIUS; dz <= MINE_RADIUS; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist > MINE_RADIUS + 0.5) {
                    continue;
                }
                int ax = Math.abs(dx);
                int az = Math.abs(dz);

                // Надёжный подземный фундамент
                add(entries, dx, -2, dz, Material.DEEPSLATE_BRICKS);
                add(entries, dx, -1, dz, Material.POLISHED_DEEPSLATE);

                // Главные крестовые проспекты (ширина 5 блоков: -2..+2) и Центральная Площадь (радиус 7)
                if (dist <= 7.2) {
                    if (ax == az || ax == 0 || az == 0) {
                        add(entries, dx, 0, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    } else {
                        add(entries, dx, 0, dz, Material.DEEPSLATE_TILES);
                    }
                } else if (ax <= 2 || az <= 2) {
                    if (ax == 0 || az == 0) {
                        add(entries, dx, 0, dz, Material.CHISELED_DEEPSLATE);
                    } else if (ax == 2 || az == 2) {
                        add(entries, dx, 0, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    } else {
                        add(entries, dx, 0, dz, Material.POLISHED_DEEPSLATE);
                    }
                } else {
                    // Пол в 4 карьерных секторах — тёмный сланец и базальт
                    if ((ax + az) % 4 == 0) {
                        add(entries, dx, 0, dz, Material.TUFF_BRICKS);
                    } else if ((ax + az) % 2 == 0) {
                        add(entries, dx, 0, dz, Material.DEEPSLATE_BRICKS);
                    } else {
                        add(entries, dx, 0, dz, Material.COBBLED_DEEPSLATE);
                    }
                }
            }
        }

        // 2. Внешний крепостной бастион (радиус 20.2..22.5) и 4 Парадные Арки Входа
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
                    if ((ax + az) % 6 == 0) {
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

        // 3. Дварфийские фонарные столбы вдоль 4 главных проспектов
        for (int d : new int[]{10, 16}) {
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

        // 4. Четыре симметричных Карьерных Котлована (NW, NE, SW, SE):
        //    - Углублённая чаша (ax = 6..12, az = 6..12, dy = -1) со ступенями вниз и рудными жилами в полу;
        //    - Скальный амфитеатр уступов с рудами по внешнему краю (ax = 13..19 или az = 13..19, dy = 1..4).
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                // 4a. Углублённый котлован (ax = 6..12, az = 6..12)
                for (int ax = 6; ax <= 12; ax++) {
                    for (int az = 6; az <= 12; az++) {
                        int dx = sx * ax;
                        int dz = sz * az;
                        boolean isRim = (ax == 6 || ax == 12 || az == 6 || az == 12);
                        if (isRim) {
                            add(entries, dx, 0, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                        } else {
                            // Выемка котлована (dy = 0 воздух, dy = -1 пол котлована с рудными жилами)
                            add(entries, dx, 0, dz, Material.AIR);
                            if ((ax + az) % 2 == 0) {
                                add(entries, dx, -1, dz, pickOreMaterial(ax, az, 0));
                            } else {
                                add(entries, dx, -1, dz, Material.COBBLED_DEEPSLATE);
                            }
                        }
                    }
                }
                // Ступени спуска в каждый котлован со стороны главных проспектов
                for (int step = 8; step <= 10; step++) {
                    addStair(entries, sx * 6, 0, sz * step, Material.DEEPSLATE_BRICK_STAIRS,
                            sx > 0 ? BlockFace.WEST : BlockFace.EAST);
                    addStair(entries, sx * step, 0, sz * 6, Material.DEEPSLATE_BRICK_STAIRS,
                            sz > 0 ? BlockFace.NORTH : BlockFace.SOUTH);
                }
                // Два рудных валуна внутри чаши котлована (высотой в 1 блок на dy = 0)
                add(entries, sx * 8, 0, sz * 8, Material.DEEPSLATE_DIAMOND_ORE);
                add(entries, sx * 10, 0, sz * 10, Material.DEEPSLATE_EMERALD_ORE);
                add(entries, sx * 10, 0, sz * 8, Material.DEEPSLATE_GOLD_ORE);
                add(entries, sx * 8, 0, sz * 10, Material.ANCIENT_DEBRIS);

                // 4b. Амфитеатр скальных рудных уступов по внешнему краю сектора (13..19)
                for (int ax = 5; ax <= 19; ax++) {
                    for (int az = 5; az <= 19; az++) {
                        double dist = Math.hypot(ax, az);
                        if (dist < 14.2 || dist > 19.8) {
                            continue;
                        }
                        int dx = sx * ax;
                        int dz = sz * az;
                        int tiers = dist >= 18.0 ? 3 : (dist >= 16.0 ? 2 : 1);
                        for (int y = 1; y <= tiers; y++) {
                            add(entries, dx, y, dz, pickCliffWallMaterial(dx, dz, y));
                        }
                    }
                }

                // 4c. Аккуратные П-образные шахтёрские крепи (Timber Mine Arches) у скальной стены
                int[][] archFrames = {
                        {sx * 14, sz * 8, sx * 14, sz * 11},
                        {sx * 8, sz * 14, sx * 11, sz * 14}
                };
                for (int[] af : archFrames) {
                    for (int y = 1; y <= 3; y++) {
                        add(entries, af[0], y, af[1], Material.DARK_OAK_LOG);
                        add(entries, af[2], y, af[3], Material.DARK_OAK_LOG);
                    }
                    // Перекладина крепи сверху и фонарь посередине
                    int midX = (af[0] + af[2]) / 2;
                    int midZ = (af[1] + af[3]) / 2;
                    add(entries, af[0], 4, af[1], Material.DARK_OAK_SLAB);
                    add(entries, af[2], 4, af[3], Material.DARK_OAK_SLAB);
                    add(entries, midX, 4, midZ, Material.DARK_OAK_SLAB);
                    add(entries, midX, 3, midZ, Material.LANTERN);
                }
            }
        }

        // 5. Точки спавна Древних жеод (28 кристаллических гнезд в котлованах и скальных уступах)
        int[][] geodeCoords = {
                // Внутри 4 углублённых котлованов (dy = 0, прямо в чаше!)
                {-9, 0, -9}, {9, 0, -9}, {-9, 0, 9}, {9, 0, 9},
                {-11, 0, -7}, {11, 0, -7}, {-11, 0, 7}, {11, 0, 7},
                {-7, 0, -11}, {7, 0, -11}, {-7, 0, 11}, {7, 0, 11},
                // На первом скальном уступе (dy = 1)
                {-13, 1, -13}, {13, 1, -13}, {-13, 1, 13}, {13, 1, 13},
                {-15, 1, -9}, {15, 1, -9}, {-15, 1, 9}, {15, 1, 9},
                {-9, 1, -15}, {9, 1, -15}, {-9, 1, 15}, {9, 1, 15},
                // На верхнем скальном уступе (dy = 2)
                {-16, 2, -12}, {16, 2, -12}, {-16, 2, 12}, {16, 2, 12}
        };
        for (int[] gc : geodeCoords) {
            addGeodeSpot(entries, gc[0], gc[1], gc[2], Material.BUDDING_AMETHYST);
            add(entries, gc[0], gc[1] + 1, gc[2], Material.AMETHYST_CLUSTER);
        }

        // 6. Просторная Центральная Площадь и Подиум Древнего Кузнеца на 0 ~ 0
        //    Чистый подход со всех 4 сторон к Кузнецу (никаких печек вплотную к НПС!)
        add(entries, 0, 1, 0, Material.LODESTONE);
        addStair(entries, 0, 1, -1, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.SOUTH);
        addStair(entries, 0, 1, 1, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.NORTH);
        addStair(entries, -1, 1, 0, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.EAST);
        addStair(entries, 1, 1, 0, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.WEST);
        add(entries, -1, 1, -1, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        add(entries, 1, 1, -1, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        add(entries, -1, 1, 1, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        add(entries, 1, 1, 1, Material.POLISHED_BLACKSTONE_BRICK_SLAB);

        // 4 Кузнечные Станции вынесены на диагональные углы площади (±4..±5), оставляя центр открытым!
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                add(entries, sx * 4, 1, sz * 4, Material.ANVIL);
                add(entries, sx * 5, 1, sz * 4, Material.BLAST_FURNACE);
                add(entries, sx * 4, 1, sz * 5, Material.SMITHING_TABLE);
                add(entries, sx * 5, 1, sz * 5, Material.LAVA_CAULDRON);

                // Высокие колонны ротонды (dy = 1..6) на (±6, ±6)
                for (int y = 1; y <= 6; y++) {
                    add(entries, sx * 6, y, sz * 6,
                            (y == 1 || y == 6) ? Material.CHISELED_POLISHED_BLACKSTONE : Material.DEEPSLATE_BRICK_WALL);
                }
            }
        }

        // Высокий купол Ротонды Кузни (dy = 7..9) — высоко над голограммой Кузнеца!
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int ax = Math.abs(dx);
                int az = Math.abs(dz);
                int maxA = Math.max(ax, az);
                if (maxA == 6 && (ax <= 2 || az <= 2 || ax == 6 && az == 6)) {
                    add(entries, dx, 7, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    add(entries, dx, 8, dz, Material.DEEPSLATE_TILE_SLAB);
                } else if (maxA == 4 && ax <= 4 && az <= 4) {
                    add(entries, dx, 8, dz, Material.DEEPSLATE_TILES);
                } else if (maxA <= 2) {
                    add(entries, dx, 9, dz, Material.POLISHED_BLACKSTONE_SLAB);
                }
            }
        }
        add(entries, 0, 9, 0, Material.SHROOMLIGHT);
        add(entries, 0, 8, 0, Material.CHAIN);
        add(entries, 0, 7, 0, Material.SOUL_LANTERN);

        return new ArrayList<>(entries.values());
    }

    private Material pickCliffWallMaterial(int dx, int dz, int dy) {
        int hash = Math.abs(dx * 37 + dz * 59 + dy * 19) % 100;
        // 45% богатых рудных жил в скальных стенах, 55% тёмной скальной породы для красивого контраста
        if (hash < 45) {
            return pickOreMaterial(dx, dz, dy);
        }
        if (hash < 65) return Material.DEEPSLATE_BRICKS;
        if (hash < 82) return Material.COBBLED_DEEPSLATE;
        if (hash < 92) return Material.TUFF;
        return Material.BLACKSTONE;
    }

    private Material pickOreMaterial(int dx, int dz, int dy) {
        int hash = Math.abs(dx * 31 + dz * 17 + dy * 13) % 100;
        if (hash < 24) return Material.DEEPSLATE_COPPER_ORE;
        if (hash < 48) return Material.DEEPSLATE_IRON_ORE;
        if (hash < 70) return Material.DEEPSLATE_GOLD_ORE;
        if (hash < 86) return Material.DEEPSLATE_EMERALD_ORE;
        if (hash < 96) return Material.DEEPSLATE_DIAMOND_ORE;
        return Material.ANCIENT_DEBRIS;
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

        // Очищаем всю зону 45x45 над полом перед установкой, чтобы не осталось старых блоков!
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

    private static Attribute resolveScaleAttribute() {
        for (String name : new String[]{"SCALE", "GENERIC_SCALE"}) {
            try {
                return Attribute.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
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
