package ru.geyserevent.wind;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lidded;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.util.ColorUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «Древний клад» — один эндер-сундук с текстурой ancient-chest, голограмма,
 * таймер по клику, дрожь самого сундука, крышка только в конце, GUI на 54 слота.
 */
public final class GravityHoardManager {

    public static final String REGION_ID = "geyserevent_wind";
    public static final int ARENA_RADIUS = 22;
    public static final int CONE_HEIGHT = 11;

    private final GeyserEventPlugin plugin;
    private final NamespacedKey chestKey;
    private final Random random = new Random();
    private record SavedBlock(World world, int x, int y, int z, BlockData data) {
    }

    private final List<SavedBlock> savedBlocks = new ArrayList<>();
    private final Set<String> savedKeys = new HashSet<>();
    private final Set<String> protectedKeys = ConcurrentHashMap.newKeySet();
    private final List<UUID> hologramIds = new ArrayList<>();

    private boolean active;
    private boolean activated;
    private boolean unlocked;
    private boolean lootFilled;
    private Location center;
    private Location chestLocation;
    private long startEpochSeconds;
    private long unlockAtEpoch;
    private long eventEndEpochSeconds;
    private BossBar bossBar;
    private BukkitRunnable tickTask;
    private BukkitRunnable animTask;
    private int shakeCounter;
    private int eruptWaves;
    private String eruptTier = "common";

    public GravityHoardManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.chestKey = new NamespacedKey(plugin, "wind_chest");
    }

    public boolean startEvent(Location targetCenter, Integer durationSecondsOverride) {
        if (targetCenter == null || targetCenter.getWorld() == null) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("wind.enabled", true)) {
            plugin.getLogger().warning("Ивент «Древний клад» выключен (wind.enabled: false).");
            return false;
        }
        if (plugin.getRiftManager() != null && plugin.getRiftManager().isActive()) {
            plugin.getRiftManager().stopEvent(false);
        }
        if (plugin.getSkibidiToiletManager() != null && plugin.getSkibidiToiletManager().isActive()) {
            plugin.getSkibidiToiletManager().stopEvent();
        }
        if (plugin.getRonaldoEventManager() != null && plugin.getRonaldoEventManager().isActive()) {
            plugin.getRonaldoEventManager().stopEvent();
        }
        stopEvent();

        targetCenter.getChunk().load(true);
        this.active = true;
        this.activated = true;
        this.unlocked = false;
        this.lootFilled = false;
        this.shakeCounter = 0;
        this.eruptWaves = 0;
        this.eruptTier = "common";
        this.center = snapToSurface(targetCenter);
        this.startEpochSeconds = System.currentTimeMillis() / 1000L;

        long durationSeconds = (durationSecondsOverride != null && durationSecondsOverride > 0)
                ? durationSecondsOverride
                : (Math.max(5, plugin.getConfig().getInt("wind.duration-minutes",
                plugin.getPluginConfig().getDurationMinutes())) * 60L);
        this.eventEndEpochSeconds = this.startEpochSeconds + durationSeconds;
        int wait = Math.max(10, plugin.getConfig().getInt("wind.unlock-seconds", 600));
        this.unlockAtEpoch = this.startEpochSeconds + wait;
        this.eventEndEpochSeconds = Math.max(this.eventEndEpochSeconds, this.unlockAtEpoch
                + Math.max(30, plugin.getConfig().getInt("wind.loot-seconds", 600)));

        setChunksForceLoaded(this.center, true);
        pasteArena(this.center);
        this.chestLocation = this.center.clone().add(0, CONE_HEIGHT, 0);
        spawnHolograms();
        createBossBar();
        broadcastSpawn();

        if (plugin.getWorldGuardHook() != null) {
            plugin.getWorldGuardHook().syncProtectionRegion(
                    this.center.getWorld(),
                    REGION_ID,
                    this.center.getBlockX(),
                    this.center.getBlockY(),
                    this.center.getBlockZ(),
                    ARENA_RADIUS + 6
            );
        }
        startTickLoop();
        startAnimLoop();
        return true;
    }

    public void stopEvent() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (animTask != null) {
            animTask.cancel();
            animTask = null;
        }
        clearHolograms();
        this.eruptTier = "common";
        if (center != null && center.getWorld() != null && plugin.getWorldGuardHook() != null) {
            plugin.getWorldGuardHook().removeRegion(center.getWorld(), REGION_ID);
        }
        restoreArena();
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        if (center != null) {
            setChunksForceLoaded(center, false);
        }
        this.active = false;
        this.activated = false;
        this.unlocked = false;
        this.lootFilled = false;
        this.eruptWaves = 0;
        this.center = null;
        this.chestLocation = null;
    }

    public boolean isEventChest(Block block) {
        return false;
    }

    private static boolean sameBlock(Block block, Location loc) {
        return loc != null
                && block.getWorld().equals(loc.getWorld())
                && block.getX() == loc.getBlockX()
                && block.getY() == loc.getBlockY()
                && block.getZ() == loc.getBlockZ();
    }

    /**
     * @return true — открытие сундука надо отменить (ещё не активирован / идёт отсчёт)
     */
    public boolean handleChestClick(Player player) {
        return false;
    }

    public boolean isLootGui(Inventory inventory) {
        return false;
    }

    public boolean isProtectedBlock(Location loc) {
        if (!active || loc == null || loc.getWorld() == null || center == null) {
            return false;
        }
        if (!loc.getWorld().equals(center.getWorld())) {
            return false;
        }
        return protectedKeys.contains(blockKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    public boolean isInsideArena(Location loc) {
        if (!active || loc == null || loc.getWorld() == null || center == null) {
            return false;
        }
        if (!loc.getWorld().equals(center.getWorld())) {
            return false;
        }
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        double dy = loc.getY() - center.getY();
        return Math.abs(dx) <= ARENA_RADIUS + 2
                && Math.abs(dz) <= ARENA_RADIUS + 2
                && dy >= -4 && dy <= 16;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDefeated() {
        return false;
    }

    public Location getCenterLocation() {
        return center;
    }

    public Location getChestLocation() {
        return chestLocation;
    }

    public int getSecondsRemaining() {
        long left = eventEndEpochSeconds - (System.currentTimeMillis() / 1000L);
        return (int) Math.max(0L, left);
    }

    public double getCoreHealth() {
        return 0;
    }

    public double getCoreMaxHealth() {
        return 0;
    }

    public org.bukkit.entity.LivingEntity getCoreEntity() {
        return null;
    }

    public void handleCoreDefeated() {
    }

    public void handleShulkerDeath(org.bukkit.entity.LivingEntity entity) {
    }

    public boolean isCore(Entity entity) {
        return false;
    }

    public boolean isHoardShulker(Entity entity) {
        return false;
    }

    public boolean isHoardEntity(Entity entity) {
        return false;
    }

    public boolean handleDamage(org.bukkit.entity.LivingEntity victim, Player attacker, double damage) {
        return false;
    }

    public void handleChargeHit(ProjectileHitEvent event) {
    }

    public boolean cancelFallInArena(Location loc) {
        return false;
    }

    private void startTickLoop() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) {
                    cancel();
                    return;
                }
                if (getSecondsRemaining() <= 0) {
                    Bukkit.broadcastMessage(ColorUtil.color(
                            "&e┼ &cГейзер затих — жерло закрылось."));
                    stopEvent();
                    return;
                }
                long now = System.currentTimeMillis() / 1000L;
                tryUnlock(now);
                if (unlocked) {
                    eruptBurst();
                }
                if (center != null && center.getWorld() != null && chestLocation != null) {
                    Location fx = chestLocation.clone().add(0.5, 0.9, 0.5);
                    Particle steam = particle("CAMPFIRE_COSY_SMOKE", Particle.CLOUD);
                    center.getWorld().spawnParticle(steam, fx, unlocked ? 10 : 4, 0.22, 0.45, 0.22, 0.01);
                    if (unlocked) {
                        center.getWorld().spawnParticle(Particle.CLOUD, fx, 6, 0.3, 0.7, 0.3, 0.02);
                    }
                }
                refreshBossBar();
                refreshHolograms();
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void startAnimLoop() {
        animTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || chestLocation == null || chestLocation.getWorld() == null) {
                    return;
                }
                if (!activated || unlocked) {
                    return;
                }
                long leftMs = Math.max(0L, unlockAtEpoch * 1000L - System.currentTimeMillis());
                int totalMs = Math.max(1, plugin.getConfig().getInt("wind.unlock-seconds", 600)) * 1000;
                double progress = 1.0 - Math.min(1.0, leftMs / (double) totalMs);
                int period = (int) Math.max(1, Math.round(10.0 - progress * 9.0));
                shakeCounter++;
                if (shakeCounter % period != 0) {
                    return;
                }
                Location fx = chestLocation.clone().add(0.5, 0.4, 0.5);
                chestLocation.getWorld().spawnParticle(Particle.CLOUD, fx, 4 + (int) (progress * 8), 0.4, 0.5, 0.4, 0.02);
                chestLocation.getWorld().playSound(fx, Sound.BLOCK_STONE_HIT, 0.2f + (float) progress * 0.4f, 0.5f);
            }
        };
        animTask.runTaskTimer(plugin, 2L, 2L);
    }

    private void ensureChestBlock() {
        if (chestLocation == null) {
            return;
        }
        if (chestLocation.getBlock().getType() != Material.ENDER_CHEST) {
            placeEnderChestBlock();
        }
    }

    private void shakeChest(double progress) {
        World world = chestLocation.getWorld();
        Location fx = chestLocation.clone().add(0.5, 0.55, 0.5);
        float pitch = (float) (0.55 + progress * 1.1);
        world.playSound(fx, Sound.BLOCK_ENDER_CHEST_CLOSE, 0.35f + (float) progress * 0.45f, pitch);
        world.playSound(fx, Sound.BLOCK_STONE_HIT, 0.25f + (float) progress * 0.4f, 0.45f);
        world.spawnParticle(Particle.CRIT, fx, 4 + (int) (progress * 10), 0.28, 0.18, 0.28, 0.04);
        world.spawnParticle(Particle.SMOKE, fx, 2 + (int) (progress * 5), 0.2, 0.12, 0.2, 0.01);
        world.spawnParticle(Particle.BLOCK, fx, 8 + (int) (progress * 12), 0.25, 0.2, 0.25, 0.03,
                Material.ENDER_CHEST.createBlockData());
    }

    private void placeEventChest() {
        if (center == null) {
            return;
        }
        Block block = center.getBlock().getRelative(0, 1, 0);
        this.chestLocation = block.getLocation();
        placeEnderChestBlock();
        World world = block.getWorld();
        world.playSound(block.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.8f, 0.7f);
        world.spawnParticle(Particle.SCULK_SOUL, block.getLocation().add(0.5, 0.8, 0.5), 16, 0.3, 0.25, 0.3, 0.02);
    }

    private void placeEnderChestBlock() {
        if (chestLocation == null) {
            return;
        }
        Block block = chestLocation.getBlock();
        org.bukkit.block.data.type.EnderChest data =
                (org.bukkit.block.data.type.EnderChest) Material.ENDER_CHEST.createBlockData();
        data.setFacing(BlockFace.SOUTH);
        block.setBlockData(data, false);
        markProtected(block);
        if (block.getState() instanceof org.bukkit.block.TileState state) {
            state.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) 1);
            state.update(true, false);
        }
    }

    private void tryUnlock(long now) {
        if (!activated || unlocked) {
            return;
        }
        if (now < unlockAtEpoch) {
            return;
        }
        unlocked = true;
        eruptWaves = 0;
        rollAndAnnounceRarity(true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || !unlocked) {
                return;
            }
            knockbackNearby();
            if (chestLocation != null && chestLocation.getWorld() != null) {
                World world = chestLocation.getWorld();
                Location fx = chestLocation.clone().add(0.5, 0.8, 0.5);
                world.playSound(fx, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.8f);
                world.playSound(fx, Sound.BLOCK_LAVA_POP, 1.4f, 0.5f);
                world.spawnParticle(Particle.CLOUD, fx, 40, 0.6, 1.2, 0.6, 0.06);
                world.spawnParticle(Particle.SPLASH, fx, 30, 0.5, 0.8, 0.5, 0.04);
            }
            eruptBurst();
        }, 4L);
        refreshHolograms();
        refreshBossBar();
    }

    private void rollAndAnnounceRarity(boolean opening) {
        eruptTier = rollRarity();
        String label = switch (eruptTier) {
            case "rare" -> "&9&lРедкая";
            case "epic" -> "&d&lЭпическая";
            case "legendary" -> "&6&lЛегендарная";
            default -> "&a&lОбычная";
        };
        if (opening) {
            Bukkit.broadcastMessage(ColorUtil.color("&e┼ &#7DF9FFГейзер прорвался!"));
        }
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fИз жерла вылетает " + label + " &fдобыча!"));
    }

    private String rollRarity() {
        double roll = random.nextDouble() * 100.0;
        double common = plugin.getConfig().getDouble("wind.rarity-chances.common", 50);
        double rare = plugin.getConfig().getDouble("wind.rarity-chances.rare", 30);
        double epic = plugin.getConfig().getDouble("wind.rarity-chances.epic", 15);
        if (roll < common) {
            return "common";
        }
        if (roll < common + rare) {
            return "rare";
        }
        if (roll < common + rare + epic) {
            return "epic";
        }
        return "legendary";
    }

    private void keepLidOpen() {
        if (chestLocation == null) {
            return;
        }
        BlockState state = chestLocation.getBlock().getState();
        if (state instanceof Lidded lid && !lid.isOpen()) {
            lid.open();
        }
    }

    private void keepLidClosed() {
        if (chestLocation == null) {
            return;
        }
        BlockState state = chestLocation.getBlock().getState();
        if (state instanceof Lidded lid && lid.isOpen()) {
            lid.close();
        }
    }

    private void knockbackNearby() {
        if (chestLocation == null || chestLocation.getWorld() == null) {
            return;
        }
        Location origin = chestLocation.clone().add(0.5, 0.2, 0.5);
        double radius = ARENA_RADIUS + 2;
        double radiusSq = radius * radius;
        for (Player player : chestLocation.getWorld().getPlayers()) {
            if (!plugin.getBedrockService().canParticipate(player) && !player.isOp()) {
                continue;
            }
            if (player.getLocation().distanceSquared(origin) > radiusSq) {
                continue;
            }
            Vector dir = player.getLocation().toVector().subtract(origin.toVector());
            dir.setY(0);
            if (dir.lengthSquared() < 0.04) {
                dir = new Vector(random.nextGaussian(), 0, random.nextGaussian());
                dir.setY(0);
            }
            if (dir.lengthSquared() < 0.0001) {
                dir = new Vector(1, 0, 0);
            }
            dir.normalize().multiply(1.75).setY(0.48);
            player.setVelocity(dir);
        }
    }

    private void fillInventory(Inventory inv) {
        inv.clear();
        int slot = 0;
        int size = inv.getSize();
        slot = addTierRolls(inv, slot, size, "common", 2);
        slot = addTierRolls(inv, slot, size, "rare", 1);
        slot = addTierRolls(inv, slot, size, "epic", 1);
        slot = addTierRolls(inv, slot, size, "legendary", 1);
        if (slot == 0) {
            inv.addItem(new ItemStack(Material.IRON_INGOT, 8 + random.nextInt(8)));
            inv.addItem(new ItemStack(Material.DIAMOND, 3 + random.nextInt(4)));
            inv.addItem(new ItemStack(Material.ANCIENT_DEBRIS, 1 + random.nextInt(2)));
        }
    }

    private int addTierRolls(Inventory inv, int slot, int size, String tier, int count) {
        List<PluginConfig.AlienLootItem> pool = plugin.getPluginConfig().getWindLoot(tier);
        if (pool == null || pool.isEmpty()) {
            pool = plugin.getPluginConfig().getWindLoot();
        }
        if (pool == null || pool.isEmpty() || count <= 0) {
            return slot;
        }
        for (int i = 0; i < count && slot < size; i++) {
            PluginConfig.AlienLootItem item = pickLoot(pool);
            if (item == null) {
                continue;
            }
            inv.setItem(slot++, stackFromLoot(item));
        }
        return slot;
    }

    private PluginConfig.AlienLootItem pickLoot(List<PluginConfig.AlienLootItem> pool) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        for (int i = 0; i < 8; i++) {
            PluginConfig.AlienLootItem item = pool.get(random.nextInt(pool.size()));
            if (random.nextDouble() * 100.0 <= Math.max(8.0, item.chancePercent())) {
                return item;
            }
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private void eruptBurst() {
        if (chestLocation == null || chestLocation.getWorld() == null) {
            return;
        }
        eruptWaves++;
        if (eruptWaves > 1 && eruptWaves % 15 == 1) {
            rollAndAnnounceRarity(false);
        }
        if (eruptWaves > 90 && eruptWaves % 3 != 0) {
            return;
        }
        World world = chestLocation.getWorld();
        Location mouth = chestLocation.clone().add(0.5, 0.4, 0.5);
        world.playSound(mouth, Sound.BLOCK_LAVA_POP, 1.1f, 0.55f + random.nextFloat() * 0.4f);
        world.playSound(mouth, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 0.9f, 0.9f);
        world.spawnParticle(Particle.CLOUD, mouth, 18, 0.45, 1.4, 0.45, 0.05);
        world.spawnParticle(particle("CAMPFIRE_COSY_SMOKE", Particle.SMOKE), mouth, 14, 0.35, 1.6, 0.35, 0.02);
        world.spawnParticle(Particle.SPLASH, mouth, 20, 0.5, 0.8, 0.5, 0.03);
        int throwsCount = 3 + random.nextInt(4);
        for (int i = 0; i < throwsCount; i++) {
            ItemStack stack = eruptStack();
            if (stack == null) {
                continue;
            }
            org.bukkit.entity.Item dropped = world.dropItem(mouth, stack);
            dropped.setPickupDelay(12);
            dropped.setVelocity(new Vector(
                    (random.nextDouble() - 0.5) * 0.85,
                    0.85 + random.nextDouble() * 0.65,
                    (random.nextDouble() - 0.5) * 0.85
            ));
        }
    }

    private ItemStack eruptStack() {
        String tier = eruptTier == null ? "common" : eruptTier;
        List<PluginConfig.AlienLootItem> pool = plugin.getPluginConfig().getWindLoot(tier);
        if (pool == null || pool.isEmpty()) {
            pool = plugin.getPluginConfig().getWindLoot();
        }
        PluginConfig.AlienLootItem item = pickLoot(pool);
        return item == null ? null : stackFromLoot(item);
    }

    private static Particle particle(String name, Particle fallback) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private ItemStack stackFromLoot(PluginConfig.AlienLootItem item) {
        int min = item.minAmount();
        int max = Math.max(min, item.maxAmount());
        int amount = min + (max > min ? random.nextInt(max - min + 1) : 0);
        return ru.geyserevent.util.LootStacks.fromAlien(item, Math.max(1, amount));
    }

    private void pasteArena(Location origin) {
        restoreArena();
        World world = origin.getWorld();
        int cx = origin.getBlockX();
        int cy = origin.getBlockY();
        int cz = origin.getBlockZ();
        int r = ARENA_RADIUS;
        Material basalt = mat("SMOOTH_BASALT", Material.BASALT);
        Material blackstone = mat("BLACKSTONE", Material.BLACK_CONCRETE);
        Material magma = mat("MAGMA_BLOCK", Material.NETHERRACK);
        Material calcite = mat("CALCITE", Material.DIORITE);
        Material diorite = Material.DIORITE;
        Material quartz = Material.QUARTZ_BLOCK;
        Material orange = mat("ORANGE_TERRACOTTA", Material.ORANGE_CONCRETE);
        Material yellow = mat("YELLOW_TERRACOTTA", Material.YELLOW_CONCRETE);
        Material white = mat("WHITE_TERRACOTTA", calcite);
        Material tuff = mat("TUFF", Material.ANDESITE);

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist > r + 0.4) {
                    continue;
                }
                for (int dy = 1; dy <= CONE_HEIGHT + 2; dy++) {
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    Material type = block.getType();
                    if (type.isAir() || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
                        continue;
                    }
                    saveOnce(block);
                    block.setType(Material.AIR, false);
                }
                setProtected(world, cx + dx, cy - 1, cz + dz, basalt);

                Material floor;
                if (dist <= 2.2) {
                    floor = magma;
                } else if (dist <= 5.5) {
                    floor = blackstone;
                } else if (dist <= 9.0) {
                    floor = calcite;
                } else if (dist <= 13.0) {
                    floor = ((dx + dz) & 1) == 0 ? white : orange;
                } else if (dist <= 17.5) {
                    floor = ((dx * 3 + dz) & 2) == 0 ? yellow : diorite;
                } else {
                    floor = ((dx + dz) & 1) == 0 ? tuff : quartz;
                }
                setProtected(world, cx + dx, cy, cz + dz, floor);

                if (dist > 6.5 && dist < 10.5) {
                    setProtected(world, cx + dx, cy + 1, cz + dz, Material.WATER);
                }

                if (dist <= 8.5) {
                    int height = (int) Math.round(CONE_HEIGHT * (1.0 - dist / 8.8));
                    height = Math.max(dist <= 2.4 ? 1 : 2, height);
                    for (int h = 1; h <= height; h++) {
                        if (dist <= 1.6 && h < height) {
                            setProtected(world, cx + dx, cy + h, cz + dz, magma);
                            continue;
                        }
                        Material wall = h >= height - 1 ? calcite : (h % 2 == 0 ? basalt : blackstone);
                        if (dist > 1.6) {
                            setProtected(world, cx + dx, cy + h, cz + dz, wall);
                        }
                    }
                }
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int h = 0; h <= 2; h++) {
                    setProtected(world, cx + dx, cy + h, cz + dz, magma);
                }
            }
        }

        int[][] vents = {{10, 0}, {-10, 0}, {0, 10}, {0, -10}, {8, 8}, {8, -8}, {-8, 8}, {-8, -8}};
        for (int[] v : vents) {
            setProtected(world, cx + v[0], cy + 1, cz + v[1], calcite);
            setProtected(world, cx + v[0], cy + 2, cz + v[1], Material.LANTERN);
        }
    }

    private void setProtected(World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != material) {
            saveOnce(block);
            block.setType(material, false);
        }
        markProtected(block);
    }

    private void saveOnce(Block block) {
        String key = blockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (savedKeys.add(key)) {
            savedBlocks.add(new SavedBlock(
                    block.getWorld(),
                    block.getX(),
                    block.getY(),
                    block.getZ(),
                    block.getBlockData().clone()
            ));
        }
    }

    private void markProtected(Block block) {
        protectedKeys.add(blockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    }

    private void restoreArena() {
        for (int i = savedBlocks.size() - 1; i >= 0; i--) {
            SavedBlock saved = savedBlocks.get(i);
            if (saved.world() == null) {
                continue;
            }
            Block block = saved.world().getBlockAt(saved.x(), saved.y(), saved.z());
            if (!block.getBlockData().matches(saved.data())) {
                block.setBlockData(saved.data(), false);
            }
        }
        savedBlocks.clear();
        savedKeys.clear();
        protectedKeys.clear();
    }

    private static Material mat(String name, Material fallback) {
        Material material = Material.matchMaterial(name);
        return material != null ? material : fallback;
    }

    private void spawnHolograms() {
        clearHolograms();
        if (chestLocation == null || chestLocation.getWorld() == null) {
            return;
        }
        Location base = chestLocation.clone().add(0.5, 1.55, 0.5);
        String[] lines = hologramLines();
        for (int i = 0; i < lines.length; i++) {
            Location lineLoc = base.clone().add(0, 0.32 * (lines.length - 1 - i), 0);
            String text = lines[i];
            ArmorStand stand = chestLocation.getWorld().spawn(lineLoc, ArmorStand.class, s -> {
                s.setVisible(false);
                s.setGravity(false);
                s.setMarker(true);
                s.setSmall(true);
                s.setBasePlate(false);
                s.setArms(false);
                s.setCustomNameVisible(true);
                s.setInvulnerable(true);
                s.setPersistent(false);
                s.setCustomName(text);
            });
            hologramIds.add(stand.getUniqueId());
        }
    }

    private void refreshHolograms() {
        String[] lines = hologramLines();
        if (hologramIds.size() != lines.length || !hologramsAlive()) {
            if (active && chestLocation != null) {
                spawnHolograms();
            }
            return;
        }
        for (int i = 0; i < lines.length; i++) {
            Entity entity = Bukkit.getEntity(hologramIds.get(i));
            if (entity instanceof ArmorStand stand) {
                stand.setCustomName(lines[i]);
                stand.setCustomNameVisible(true);
            }
        }
    }

    private boolean hologramsAlive() {
        for (UUID id : hologramIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null || !entity.isValid()) {
                return false;
            }
        }
        return true;
    }

    private String[] hologramLines() {
        if (!unlocked) {
            long left = Math.max(0L, unlockAtEpoch - System.currentTimeMillis() / 1000L);
            return new String[]{
                    ColorUtil.color("&#7DF9FF&lГейзер"),
                    ColorUtil.color("&eОткроется через " + ColorUtil.formatDuration(left))
            };
        }
        return new String[]{
                ColorUtil.color("&#34D711&lГейзер открыт"),
                ColorUtil.color("&fЛут летит из жерла")
        };
    }

    private void clearHolograms() {
        for (UUID id : hologramIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        hologramIds.clear();
    }

    private void createBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
        bossBar = Bukkit.createBossBar(ColorUtil.color("&b&lГейзер"), BarColor.BLUE, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        refreshBossBar();
    }

    private void refreshBossBar() {
        if (bossBar == null) {
            return;
        }
        int left = getSecondsRemaining();
        if (!unlocked) {
            long wait = Math.max(0L, unlockAtEpoch - System.currentTimeMillis() / 1000L);
            int totalWait = Math.max(1, plugin.getConfig().getInt("wind.unlock-seconds", 600));
            bossBar.setColor(BarColor.YELLOW);
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, wait / (double) totalWait)));
            bossBar.setTitle(ColorUtil.color("&b&lГейзер откроется через &e"
                    + ColorUtil.formatDuration(wait)));
        } else {
            long total = Math.max(1L, eventEndEpochSeconds - unlockAtEpoch);
            bossBar.setColor(BarColor.GREEN);
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, left / (double) total)));
            bossBar.setTitle(ColorUtil.color("&b&lГейзер бьёт &8| &fосталось "
                    + ColorUtil.formatDuration(left)));
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getBedrockService().canParticipate(player)) {
                bossBar.addPlayer(player);
            }
        }
    }

    private void broadcastSpawn() {
        if (center == null) {
            return;
        }
        int wait = Math.max(10, plugin.getConfig().getInt("wind.unlock-seconds", 600));
        Bukkit.broadcastMessage(ColorUtil.color("&e┼ &#7DF9FFГейзер &fподнялся из земли!"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fЧерез &#E0C912" + ColorUtil.formatDuration(wait)
                + " &fжерло откроется и начнёт разбрасывать ресурсы"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fРедкости: &fобычная &8/ &9редкая &8/ &dэпическая &8/ &6легендарная"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fКоординаты: &#E0C912"
                + center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ()));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fЛокатор: &#34D711/event gps"));
    }

    private Location snapToSurface(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private void setChunksForceLoaded(Location loc, boolean loaded) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        World world = loc.getWorld();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Chunk chunk = world.getChunkAt(cx + dx, cz + dz);
                chunk.setForceLoaded(loaded);
                if (loaded) {
                    chunk.load(true);
                }
            }
        }
    }

    private static String blockKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
