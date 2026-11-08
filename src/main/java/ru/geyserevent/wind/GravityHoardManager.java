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
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.hook.WorldGuardHook;
import ru.geyserevent.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «Сердце Ветра» — клад в руинах древнего города. Без босса и приспешников:
 * схема + один ивентовый сундук (trapped chest с кастомной моделью в паке Bedrock,
 * обычные сундуки не трогаем).
 */
public final class GravityHoardManager {

    public static final String REGION_ID = "geyserevent_wind";
    public static final int ARENA_RADIUS = 12;

    private final GeyserEventPlugin plugin;
    private final NamespacedKey chestKey;
    private final Random random = new Random();
    private final List<BlockState> savedBlocks = new ArrayList<>();
    private final Set<String> protectedKeys = ConcurrentHashMap.newKeySet();

    private boolean active;
    private Location center;
    private Location chestLocation;
    private long eventEndEpochSeconds;
    private BossBar bossBar;
    private BukkitRunnable tickTask;

    public GravityHoardManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.chestKey = new NamespacedKey(plugin, "wind_chest");
    }

    public boolean startEvent(Location targetCenter, Integer durationSecondsOverride) {
        if (targetCenter == null || targetCenter.getWorld() == null) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("wind.enabled", true)) {
            plugin.getLogger().warning("Ивент «Сердце Ветра» выключен (wind.enabled: false).");
            return false;
        }
        if (plugin.getRiftManager() != null && plugin.getRiftManager().isActive()) {
            plugin.getRiftManager().stopEvent(false);
        }
        if (plugin.getAlienInvasionManager() != null && plugin.getAlienInvasionManager().isActive()) {
            plugin.getAlienInvasionManager().stopEvent();
        }
        stopEvent();

        targetCenter.getChunk().load(true);
        this.active = true;
        this.center = snapToSurface(targetCenter);

        long durationSeconds = (durationSecondsOverride != null && durationSecondsOverride > 0)
                ? durationSecondsOverride
                : (Math.max(5, plugin.getConfig().getInt("wind.duration-minutes",
                plugin.getPluginConfig().getDurationMinutes())) * 60L);
        this.eventEndEpochSeconds = (System.currentTimeMillis() / 1000L) + durationSeconds;

        setChunksForceLoaded(this.center, true);
        pasteArena(this.center);
        placeEventChest();
        createBossBar();
        broadcastSpawn();

        if (plugin.getWorldGuardHook() != null) {
            plugin.getWorldGuardHook().syncProtectionRegion(
                    this.center.getWorld(),
                    REGION_ID,
                    this.center.getBlockX(),
                    this.center.getBlockY(),
                    this.center.getBlockZ(),
                    ARENA_RADIUS + 2
            );
        }
        startTickLoop();
        return true;
    }

    public void stopEvent() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
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
        this.center = null;
        this.chestLocation = null;
    }

    public boolean isEventChest(Block block) {
        if (!active || block == null || chestLocation == null) {
            return false;
        }
        return block.getX() == chestLocation.getBlockX()
                && block.getY() == chestLocation.getBlockY()
                && block.getZ() == chestLocation.getBlockZ()
                && block.getWorld().equals(chestLocation.getWorld());
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
        return dx * dx + dz * dz <= (ARENA_RADIUS + 2) * (ARENA_RADIUS + 2)
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

    /** Совместимость со старым статусом /rift — босса больше нет. */
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
        // босса больше нет
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
                            "&e┼ &cРуины древнего города погрузились обратно в скалк..."));
                    stopEvent();
                    return;
                }
                if (center != null && center.getWorld() != null && chestLocation != null) {
                    Location fx = chestLocation.clone().add(0.5, 0.9, 0.5);
                    center.getWorld().spawnParticle(Particle.SCULK_SOUL, fx, 3, 0.25, 0.2, 0.25, 0.01);
                }
                refreshBossBar();
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void placeEventChest() {
        if (center == null) {
            return;
        }
        Block block = center.getBlock().getRelative(0, 1, 0);
        block.setType(Material.TRAPPED_CHEST, false);
        markProtected(block);
        this.chestLocation = block.getLocation();
        if (block.getState() instanceof Chest chest) {
            chest.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) 1);
            fillChest(chest);
            chest.update(true, false);
        }
        World world = block.getWorld();
        world.playSound(block.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.1f, 0.7f);
        world.spawnParticle(Particle.SCULK_SOUL, block.getLocation().add(0.5, 0.8, 0.5), 18, 0.4, 0.3, 0.4, 0.02);
    }

    private void fillChest(Chest chest) {
        List<PluginConfig.AlienLootItem> loot = plugin.getPluginConfig().getAlienChestLoot();
        List<PluginConfig.AlienLootItem> base = plugin.getPluginConfig().getAlienChestBaseLoot();
        int slot = 0;
        if (base != null) {
            for (PluginConfig.AlienLootItem item : base) {
                chest.getInventory().setItem(slot++, stackFromLoot(item));
            }
        }
        if (loot == null || loot.isEmpty()) {
            chest.getInventory().addItem(new ItemStack(Material.ECHO_SHARD, 4 + random.nextInt(4)));
            chest.getInventory().addItem(new ItemStack(Material.DIAMOND, 2 + random.nextInt(3)));
            return;
        }
        int rolls = plugin.getPluginConfig().getAlienChestItemsPerChest();
        for (int i = 0; i < rolls && slot < 27; i++) {
            PluginConfig.AlienLootItem item = loot.get(random.nextInt(loot.size()));
            if (random.nextDouble() * 100.0 > item.chancePercent()) {
                continue;
            }
            chest.getInventory().setItem(slot++, stackFromLoot(item));
        }
    }

    private ItemStack stackFromLoot(PluginConfig.AlienLootItem item) {
        int min = item.minAmount();
        int max = Math.max(min, item.maxAmount());
        int amount = min + (max > min ? random.nextInt(max - min + 1) : 0);
        ItemStack stack = new ItemStack(item.material(), Math.max(1, amount));
        if (item.displayName() != null) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(item.displayName());
                if (item.lore() != null && !item.lore().isEmpty()) {
                    meta.setLore(item.lore());
                }
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    private void pasteArena(Location origin) {
        restoreArena();
        World world = origin.getWorld();
        int cx = origin.getBlockX();
        int cy = origin.getBlockY();
        int cz = origin.getBlockZ();
        int r = ARENA_RADIUS;
        Material tiles = mat("DEEPSLATE_TILES", Material.DEEPSLATE);
        Material crackedTiles = mat("CRACKED_DEEPSLATE_TILES", tiles);
        Material bricks = mat("DEEPSLATE_BRICKS", Material.COBBLED_DEEPSLATE);
        Material crackedBricks = mat("CRACKED_DEEPSLATE_BRICKS", bricks);
        Material chiseled = mat("CHISELED_DEEPSLATE", bricks);
        Material polished = mat("POLISHED_DEEPSLATE", bricks);
        Material sculk = mat("SCULK", Material.BLACK_CONCRETE);
        Material vein = mat("SCULK_VEIN", Material.AIR);
        Material catalyst = mat("SCULK_CATALYST", chiseled);
        Material sensor = mat("SCULK_SENSOR", chiseled);

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist > r + 0.4) {
                    continue;
                }
                for (int dy = -1; dy <= 7; dy++) {
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    savedBlocks.add(block.getState());
                    if (dy >= 1) {
                        block.setType(Material.AIR, false);
                    }
                }
                setProtected(world, cx + dx, cy - 1, cz + dz, Material.DEEPSLATE);

                Material floor;
                boolean outer = dist > r - 1.15;
                boolean walk = dist <= r - 1.15;
                if (outer) {
                    floor = Material.COBBLED_DEEPSLATE;
                } else if (dist < 2.2) {
                    floor = chiseled;
                } else if (dist < 4.5) {
                    floor = polished;
                } else if (((dx * 13 + dz * 7) & 5) == 0) {
                    floor = sculk;
                } else if (((dx + dz) & 1) == 0) {
                    floor = tiles;
                } else {
                    floor = crackedTiles;
                }
                setProtected(world, cx + dx, cy, cz + dz, floor);

                if (walk && vein != Material.AIR && ((dx * dz) & 7) == 0 && dist > 3) {
                    setProtected(world, cx + dx, cy + 1, cz + dz, vein);
                }
            }
        }

        // Стены по кольцу
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist < r - 0.2 || dist > r + 0.45) {
                    continue;
                }
                boolean cracked = ((dx + dz) & 2) == 0;
                Material wall = cracked ? crackedBricks : bricks;
                setProtected(world, cx + dx, cy + 1, cz + dz, wall);
                setProtected(world, cx + dx, cy + 2, cz + dz, wall);
                if (((dx + 3 * dz) & 3) == 0) {
                    setProtected(world, cx + dx, cy + 3, cz + dz, chiseled);
                }
            }
        }

        // Угловые башни
        int[][] towers = {{9, 9}, {9, -9}, {-9, 9}, {-9, -9}};
        for (int[] t : towers) {
            buildTower(world, cx + t[0], cy, cz + t[1], bricks, chiseled);
        }

        // Скалковые алтари по сторонам
        placeShrine(world, cx + 8, cy, cz, catalyst, sensor, chiseled);
        placeShrine(world, cx - 8, cy, cz, catalyst, sensor, chiseled);
        placeShrine(world, cx, cy, cz + 8, catalyst, sensor, chiseled);
        placeShrine(world, cx, cy, cz - 8, catalyst, sensor, chiseled);

        // Пьедестал под сундук
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setProtected(world, cx + dx, cy, cz + dz, chiseled);
            }
        }
        setProtected(world, cx, cy, cz, mat("LODESTONE", chiseled));
        setProtected(world, cx + 1, cy + 1, cz, Material.SOUL_LANTERN);
        setProtected(world, cx - 1, cy + 1, cz, Material.SOUL_LANTERN);
        setProtected(world, cx, cy + 1, cz + 1, Material.SOUL_LANTERN);
        setProtected(world, cx, cy + 1, cz - 1, Material.SOUL_LANTERN);
    }

    private void buildTower(World world, int x, int y, int z, Material bricks, Material chiseled) {
        for (int h = 0; h <= 4; h++) {
            setProtected(world, x, y + h, z, h == 4 ? chiseled : bricks);
        }
        setProtected(world, x, y + 5, z, Material.SOUL_LANTERN);
        setProtected(world, x + 1, y + 1, z, bricks);
        setProtected(world, x - 1, y + 1, z, bricks);
        setProtected(world, x, y + 1, z + 1, bricks);
        setProtected(world, x, y + 1, z - 1, bricks);
    }

    private void placeShrine(World world, int x, int y, int z, Material catalyst, Material sensor, Material chiseled) {
        setProtected(world, x, y, z, chiseled);
        setProtected(world, x, y + 1, z, catalyst);
        if ((x + z) % 2 == 0) {
            setProtected(world, x + 1, y + 1, z, sensor);
        }
        setProtected(world, x, y + 2, z, Material.BLACK_CANDLE);
    }

    private void setProtected(World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(material, false);
        markProtected(block);
    }

    private void markProtected(Block block) {
        protectedKeys.add(blockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    }

    private void restoreArena() {
        for (int i = savedBlocks.size() - 1; i >= 0; i--) {
            savedBlocks.get(i).update(true, false);
        }
        savedBlocks.clear();
        protectedKeys.clear();
    }

    private static Material mat(String name, Material fallback) {
        Material material = Material.matchMaterial(name);
        return material != null ? material : fallback;
    }

    private void createBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
        bossBar = Bukkit.createBossBar(ColorUtil.color("&3&lДревний клад"), BarColor.BLUE, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        refreshBossBar();
    }

    private void refreshBossBar() {
        if (bossBar == null) {
            return;
        }
        int left = getSecondsRemaining();
        long total = Math.max(1L, plugin.getConfig().getInt("wind.duration-minutes", 20) * 60L);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, left / (double) total)));
        bossBar.setTitle(ColorUtil.color("&3&lДревний клад &8| &fосталось "
                + ColorUtil.formatDuration(left)));
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
        Bukkit.broadcastMessage(ColorUtil.color("&e┼ &#7DF9FFДревний город &fподнялся из скалка!"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fВ центре — &#d6b85aдревний сундук &fс кладом"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fБосса нет: успей забрать лут, пока руины не исчезли"));
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
