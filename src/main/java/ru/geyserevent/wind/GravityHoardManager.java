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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.event.DamageLeaderboard;
import ru.geyserevent.hook.WorldGuardHook;
import ru.geyserevent.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ивент «Сердце Ветра» — механика, которой нет на обычных анархиях.
 *
 * <p>В воронке из меди и аметиста парит ядро и стреляет ветряными зарядами.
 * Шалкеры — парящие сундуки, с них падает лут. Убил ядро — сундуки на пьедесталах.</p>
 */
public final class GravityHoardManager {

    public static final String REGION_ID = "geyserevent_wind";
    public static final int ARENA_RADIUS = 10;

    private final GeyserEventPlugin plugin;
    private final NamespacedKey coreKey;
    private final NamespacedKey shulkerKey;
    private final NamespacedKey chestKey;
    private final DamageLeaderboard leaderboard = new DamageLeaderboard();
    private final Random random = new Random();
    private final List<BlockState> savedBlocks = new ArrayList<>();
    private final Set<String> protectedKeys = ConcurrentHashMap.newKeySet();
    private final Set<UUID> shulkers = ConcurrentHashMap.newKeySet();

    private boolean active;
    private boolean defeated;
    private Location center;
    private UUID coreUuid;
    private double coreHealth;
    private double coreMaxHealth;
    private long eventEndEpochSeconds;
    private int secondsElapsed;
    private int pulseCooldown;
    private int shulkerCooldown;
    private BossBar bossBar;
    private BukkitRunnable tickTask;
    private BukkitRunnable cleanupTask;

    public GravityHoardManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.coreKey = new NamespacedKey(plugin, "wind_core");
        this.shulkerKey = new NamespacedKey(plugin, "wind_shulker");
        this.chestKey = new NamespacedKey(plugin, "wind_chest");
    }

    public boolean startEvent(Location targetCenter, Integer durationSecondsOverride) {
        if (targetCenter == null || targetCenter.getWorld() == null) {
            return false;
        }
        if (plugin.getConfig().getBoolean("wind.enabled", true) == false) {
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
        this.defeated = false;
        this.secondsElapsed = 0;
        this.leaderboard.clear();
        this.shulkers.clear();
        this.center = snapToSurface(targetCenter);
        this.pulseCooldown = Math.max(2, plugin.getConfig().getInt("wind.pulse-seconds", 3));
        this.shulkerCooldown = 2;
        this.coreMaxHealth = Math.max(200.0, plugin.getConfig().getDouble("wind.core-health", 900.0));
        this.coreHealth = this.coreMaxHealth;

        long durationSeconds = (durationSecondsOverride != null && durationSecondsOverride > 0)
                ? durationSecondsOverride
                : (Math.max(5, plugin.getConfig().getInt("wind.duration-minutes",
                plugin.getPluginConfig().getDurationMinutes())) * 60L);
        this.eventEndEpochSeconds = (System.currentTimeMillis() / 1000L) + durationSeconds;

        setChunksForceLoaded(this.center, true);
        pasteArena(this.center);

        LivingEntity core = spawnCore(this.center.clone().add(0.5, 2.2, 0.5));
        this.coreUuid = core.getUniqueId();
        createBossBar();
        spawnShulkerWave();
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
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (center != null && center.getWorld() != null && plugin.getWorldGuardHook() != null) {
            plugin.getWorldGuardHook().removeRegion(center.getWorld(), REGION_ID);
        }
        removeEntities();
        restoreArena();
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        if (center != null) {
            setChunksForceLoaded(center, false);
        }
        this.active = false;
        this.defeated = false;
        this.coreUuid = null;
        this.center = null;
        this.shulkers.clear();
        this.leaderboard.clear();
    }

    public void handleCoreDefeated() {
        if (!active || defeated) {
            return;
        }
        this.defeated = true;
        removeEntities();
        openChests();
        Bukkit.broadcastMessage(ColorUtil.color(
                "&e┼ &#7DF9FFСердце Ветра разрушено! &fГравитация спала — сундуки на пьедесталах."));
        if (bossBar != null) {
            bossBar.setTitle(ColorUtil.color("&b&lСердце Ветра &8[&aразрушено&8]"));
            bossBar.setProgress(0.0);
            bossBar.setColor(BarColor.GREEN);
        }
        int lootSeconds = Math.max(30, plugin.getConfig().getInt("wind.loot-seconds", 90));
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                stopEvent();
            }
        };
        cleanupTask.runTaskLater(plugin, lootSeconds * 20L);
    }

    public boolean handleDamage(LivingEntity victim, Player attacker, double damage) {
        if (!active || defeated || victim == null) {
            return false;
        }
        if (isCore(victim)) {
            coreHealth = Math.max(0.0, coreHealth - damage);
            if (attacker != null) {
                leaderboard.addDamage(attacker.getUniqueId(), attacker.getName(), damage);
            }
            refreshBossBar();
            if (coreHealth <= 0.0) {
                victim.remove();
                handleCoreDefeated();
            }
            return true;
        }
        return isHoardShulker(victim);
    }

    public void handleShulkerDeath(LivingEntity shulker) {
        if (!active) {
            return;
        }
        shulkers.remove(shulker.getUniqueId());
        dropShulkerLoot(shulker.getLocation());
        shulker.getWorld().playSound(shulker.getLocation(), Sound.ENTITY_SHULKER_DEATH, 1.2f, 1.4f);
    }

    public boolean isCore(Entity entity) {
        return entity != null && coreUuid != null && coreUuid.equals(entity.getUniqueId());
    }

    public boolean isHoardShulker(Entity entity) {
        return entity != null && shulkers.contains(entity.getUniqueId());
    }

    public boolean isHoardEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (isCore(entity) || isHoardShulker(entity)) {
            return true;
        }
        return entity.getPersistentDataContainer().has(coreKey, PersistentDataType.BYTE)
                || entity.getPersistentDataContainer().has(shulkerKey, PersistentDataType.BYTE);
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
                && dy >= -4 && dy <= 18;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDefeated() {
        return defeated;
    }

    public Location getCenterLocation() {
        return center;
    }

    public LivingEntity getCoreEntity() {
        if (coreUuid == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(coreUuid);
        return entity instanceof LivingEntity living ? living : null;
    }

    public double getCoreHealth() {
        return coreHealth;
    }

    public double getCoreMaxHealth() {
        return coreMaxHealth;
    }

    public int getSecondsRemaining() {
        long left = eventEndEpochSeconds - (System.currentTimeMillis() / 1000L);
        return (int) Math.max(0L, left);
    }

    public boolean cancelFallInArena(Location loc) {
        return isInsideArena(loc);
    }

    private void startTickLoop() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) {
                    cancel();
                    return;
                }
                secondsElapsed++;
                if (!defeated && getSecondsRemaining() <= 0) {
                    Bukkit.broadcastMessage(ColorUtil.color(
                            "&e┼ &cСердце Ветра затихло — ядро унесло обратно в небо."));
                    stopEvent();
                    return;
                }
                if (defeated) {
                    refreshBossBar();
                    return;
                }
                pulseCooldown--;
                if (pulseCooldown <= 0) {
                    shootWindCharges();
                    pulseCooldown = Math.max(2, plugin.getConfig().getInt("wind.pulse-seconds", 3));
                }
                shulkerCooldown--;
                if (shulkerCooldown <= 0) {
                    spawnShulkerWave();
                    shulkerCooldown = 22;
                }
                leashCore();
                leashShulkers();
                refreshBossBar();
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void shootWindCharges() {
        LivingEntity core = getCoreEntity();
        if (core == null || center == null || center.getWorld() == null || defeated) {
            return;
        }
        Player target = nearestPlayer();
        if (target == null) {
            return;
        }
        if (core instanceof Mob mob) {
            mob.setTarget(target);
        }
        Location from = core.getEyeLocation();
        Vector dir = target.getEyeLocation().toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 0.05) {
            return;
        }
        dir.normalize();
        World world = center.getWorld();
        world.playSound(from, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.6f, 1.35f);
        world.spawnParticle(Particle.CLOUD, from, 8, 0.2, 0.2, 0.2, 0.02);
        EntityType chargeType = resolveChargeType();
        Entity spawned = world.spawnEntity(from.clone().add(dir.clone().multiply(0.6)), chargeType);
        spawned.setVelocity(dir.multiply(1.45));
        if (spawned instanceof Projectile projectile) {
            projectile.setShooter(core);
            projectile.getPersistentDataContainer().set(coreKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    public void handleChargeHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!projectile.getPersistentDataContainer().has(coreKey, PersistentDataType.BYTE)) {
            ProjectileSource shooter = projectile.getShooter();
            if (!(shooter instanceof Entity shooterEntity) || !isCore(shooterEntity)) {
                return;
            }
        }
        if (!(event.getHitEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.getBedrockService().canParticipate(player) || !isInsideArena(player.getLocation())) {
            return;
        }
        double damage = Math.max(2.0, plugin.getConfig().getDouble("wind.charge-damage", 6.0));
        player.damage(damage, getCoreEntity());
        Vector knock = player.getLocation().toVector().subtract(center.toVector());
        if (knock.lengthSquared() > 0.2) {
            knock.normalize().multiply(0.45).setY(0.28);
            player.setVelocity(player.getVelocity().add(knock));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
    }

    private Player nearestPlayer() {
        if (center == null || center.getWorld() == null) {
            return null;
        }
        Player best = null;
        double bestDist = 28.0 * 28.0;
        for (Player player : center.getWorld().getPlayers()) {
            if (!player.isValid() || player.isDead()) {
                continue;
            }
            if (!plugin.getBedrockService().canParticipate(player)) {
                continue;
            }
            if (!isInsideArena(player.getLocation())) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(center);
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    private static EntityType resolveChargeType() {
        for (String name : new String[]{"WIND_CHARGE", "BREEZE_WIND_CHARGE"}) {
            for (EntityType type : EntityType.values()) {
                if (name.equals(type.name())) {
                    return type;
                }
            }
        }
        return EntityType.SNOWBALL;
    }

    private LivingEntity spawnCore(Location loc) {
        World world = loc.getWorld();
        EntityType type = resolveCoreType();
        Entity spawned = world.spawnEntity(loc, type);
        if (!(spawned instanceof LivingEntity core)) {
            spawned.remove();
            throw new IllegalStateException("Не удалось заспавнить ядро ветра");
        }
        core.getPersistentDataContainer().set(coreKey, PersistentDataType.BYTE, (byte) 1);
        core.setRemoveWhenFarAway(false);
        core.setPersistent(false);
        core.setCustomNameVisible(true);
        core.setGlowing(true);
        core.setGravity(false);
        ru.geyserevent.boss.BossAbilityController.setEntityAttribute(
                core, "MAX_HEALTH", "generic.max_health", coreMaxHealth);
        core.setHealth(Math.min(core.getMaxHealth(), coreMaxHealth));
        if (core instanceof Mob mob) {
            mob.setAware(true);
            mob.setTarget(null);
        }
        core.setCustomName(ColorUtil.color("&b&lСердце Ветра &8[&f" + (int) coreHealth + " ❤&8]"));
        return core;
    }

    private static EntityType resolveCoreType() {
        for (EntityType type : EntityType.values()) {
            if ("BREEZE".equals(type.name())) {
                return type;
            }
        }
        return EntityType.BLAZE;
    }

    private void spawnShulkerWave() {
        if (center == null || defeated) {
            return;
        }
        shulkers.removeIf(id -> {
            Entity entity = Bukkit.getEntity(id);
            return entity == null || entity.isDead();
        });
        int max = Math.max(2, plugin.getConfig().getInt("wind.shulkers", 4));
        int[][] spots = {{6, 5, 0}, {-6, 5, 0}, {0, 6, 6}, {0, 6, -6}, {4, 8, 4}, {-4, 7, -4}};
        for (int[] spot : spots) {
            if (shulkers.size() >= max) {
                break;
            }
            Location loc = center.clone().add(spot[0] + 0.5, spot[1], spot[2] + 0.5);
            Entity spawned = loc.getWorld().spawnEntity(loc, EntityType.SHULKER);
            if (!(spawned instanceof LivingEntity shulker)) {
                spawned.remove();
                continue;
            }
            shulker.getPersistentDataContainer().set(shulkerKey, PersistentDataType.BYTE, (byte) 1);
            shulker.setRemoveWhenFarAway(false);
            shulker.setGravity(false);
            shulker.setAI(false);
            if (shulker instanceof Mob mob) {
                mob.setAware(false);
            }
            shulker.setGlowing(true);
            shulker.setCustomNameVisible(true);
            shulker.setCustomName(ColorUtil.color("&dПарящий сундук"));
            shulker.setMaxHealth(30.0);
            shulker.setHealth(30.0);
            shulkers.add(shulker.getUniqueId());
        }
    }

    private void dropShulkerLoot(Location loc) {
        List<PluginConfig.AlienLootItem> loot = plugin.getPluginConfig().getAlienChestLoot();
        if (loot == null || loot.isEmpty()) {
            loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.AMETHYST_SHARD, 4 + random.nextInt(5)));
            loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.DIAMOND, 1 + random.nextInt(2)));
            return;
        }
        int rolls = 2 + random.nextInt(2);
        for (int i = 0; i < rolls; i++) {
            PluginConfig.AlienLootItem item = loot.get(random.nextInt(loot.size()));
            if (random.nextDouble() * 100.0 > item.chancePercent()) {
                continue;
            }
            int min = item.minAmount();
            int max = Math.max(min, item.maxAmount());
            int amount = min + (max > min ? random.nextInt(max - min + 1) : 0);
            ItemStack stack = new ItemStack(item.material(), Math.max(1, amount));
            if (item.displayName() != null) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(item.displayName());
                    stack.setItemMeta(meta);
                }
            }
            loc.getWorld().dropItemNaturally(loc, stack);
        }
    }

    private void openChests() {
        if (center == null) {
            return;
        }
        int[][] pedestals = {{7, 1, 0}, {-7, 1, 0}, {0, 1, 7}, {0, 1, -7}};
        for (int[] p : pedestals) {
            Block block = center.clone().add(p[0], p[1], p[2]).getBlock();
            block.setType(Material.CHEST, false);
            if (block.getState() instanceof Chest chest) {
                chest.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) 1);
                fillLikeAlien(chest);
                chest.update(true, false);
            }
            markProtected(block);
        }
    }

    private void fillLikeAlien(Chest chest) {
        List<PluginConfig.AlienLootItem> loot = plugin.getPluginConfig().getAlienChestLoot();
        List<PluginConfig.AlienLootItem> base = plugin.getPluginConfig().getAlienChestBaseLoot();
        int slot = 0;
        if (base != null) {
            for (PluginConfig.AlienLootItem item : base) {
                int min = item.minAmount();
                int max = Math.max(min, item.maxAmount());
                int amount = min + (max > min ? random.nextInt(max - min + 1) : 0);
                chest.getInventory().setItem(slot++, new ItemStack(item.material(), Math.max(1, amount)));
            }
        }
        if (loot == null || loot.isEmpty()) {
            return;
        }
        int rolls = plugin.getPluginConfig().getAlienChestItemsPerChest();
        for (int i = 0; i < rolls && slot < 27; i++) {
            PluginConfig.AlienLootItem item = loot.get(random.nextInt(loot.size()));
            if (random.nextDouble() * 100.0 > item.chancePercent()) {
                continue;
            }
            int min = item.minAmount();
            int max = Math.max(min, item.maxAmount());
            int amount = min + (max > min ? random.nextInt(max - min + 1) : 0);
            chest.getInventory().setItem(slot++, new ItemStack(item.material(), Math.max(1, amount)));
        }
    }

    private void pasteArena(Location origin) {
        restoreArena();
        World world = origin.getWorld();
        int cx = origin.getBlockX();
        int cy = origin.getBlockY();
        int cz = origin.getBlockZ();
        int r = ARENA_RADIUS;
        Material exposed = mat("EXPOSED_COPPER", Material.COPPER_BLOCK);
        Material oxidized = mat("OXIDIZED_COPPER", Material.WEATHERED_COPPER);
        if (oxidized == Material.WEATHERED_COPPER) {
            oxidized = mat("WEATHERED_COPPER", Material.COPPER_BLOCK);
        }
        Material grate = mat("COPPER_GRATE", Material.CUT_COPPER);
        Material cluster = mat("AMETHYST_CLUSTER", Material.AMETHYST_BLOCK);
        Material tuffBricks = mat("TUFF_BRICKS", Material.TUFF);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist > r + 0.35) {
                    continue;
                }
                for (int dy = -1; dy <= 8; dy++) {
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    savedBlocks.add(block.getState());
                    if (dy >= 1) {
                        block.setType(Material.AIR, false);
                    }
                }
                Block foundation = world.getBlockAt(cx + dx, cy - 1, cz + dz);
                foundation.setType(Material.COBBLED_DEEPSLATE, false);
                markProtected(foundation);

                Material floor;
                if (dist < 1.5) {
                    floor = Material.LODESTONE;
                } else if (dist < 2.6) {
                    floor = grate;
                } else if (dist < 4.2) {
                    floor = Material.AMETHYST_BLOCK;
                } else if (dist < 6.4) {
                    floor = (Math.abs(dx + dz) % 2 == 0) ? Material.COPPER_BLOCK : exposed;
                } else if (dist < 8.3) {
                    floor = tuffBricks;
                } else {
                    floor = Material.COBBLED_DEEPSLATE;
                }
                Block floorBlock = world.getBlockAt(cx + dx, cy, cz + dz);
                floorBlock.setType(floor, false);
                markProtected(floorBlock);

                if (dist > 8.35 && dist < 10.05) {
                    Block wall = world.getBlockAt(cx + dx, cy + 1, cz + dz);
                    wall.setType(oxidized, false);
                    markProtected(wall);
                    if (((dx + dz) & 3) == 0) {
                        Block light = world.getBlockAt(cx + dx, cy + 2, cz + dz);
                        light.setType(Material.COPPER_BULB, false);
                        markProtected(light);
                    }
                }
                if (dist > 3.6 && dist < 4.3 && ((dx + 2 * dz) % 3 == 0)) {
                    Block gem = world.getBlockAt(cx + dx, cy + 1, cz + dz);
                    gem.setType(cluster, false);
                    markProtected(gem);
                }
            }
        }
        placeTower(world, cx + 6, cy, cz + 6);
        placeTower(world, cx + 6, cy, cz - 6);
        placeTower(world, cx - 6, cy, cz + 6);
        placeTower(world, cx - 6, cy, cz - 6);
        placePedestal(world, cx + 7, cy, cz);
        placePedestal(world, cx - 7, cy, cz);
        placePedestal(world, cx, cy, cz + 7);
        placePedestal(world, cx, cy, cz - 7);
        setProtected(world, cx, cy + 1, cz, Material.LIGHTNING_ROD);
    }

    private void placeTower(World world, int x, int y, int z) {
        Material cut = Material.CUT_COPPER;
        setProtected(world, x, y, z, cut);
        setProtected(world, x, y + 1, z, cut);
        setProtected(world, x, y + 2, z, cut);
        setProtected(world, x, y + 3, z, Material.LIGHTNING_ROD);
    }

    private void placePedestal(World world, int x, int y, int z) {
        setProtected(world, x, y, z, Material.CUT_COPPER);
        setProtected(world, x, y + 1, z, Material.AMETHYST_BLOCK);
        setProtected(world, x, y + 2, z, mat("AMETHYST_CLUSTER", Material.AMETHYST_BLOCK));
    }

    private void setProtected(World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(material, false);
        markProtected(block);
    }

    private static Material mat(String name, Material fallback) {
        Material material = Material.matchMaterial(name);
        return material != null ? material : fallback;
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

    private void removeEntities() {
        if (center == null || center.getWorld() == null) {
            return;
        }
        for (Entity entity : center.getWorld().getNearbyEntities(center, 32, 24, 32)) {
            if (isHoardEntity(entity)) {
                entity.remove();
            }
        }
        LivingEntity core = getCoreEntity();
        if (core != null) {
            core.remove();
        }
        shulkers.clear();
    }

    private void leashCore() {
        LivingEntity core = getCoreEntity();
        if (core == null || center == null) {
            if (!defeated && active) {
                LivingEntity spawned = spawnCore(center.clone().add(0.5, 2.2, 0.5));
                this.coreUuid = spawned.getUniqueId();
            }
            return;
        }
        if (core.getLocation().distanceSquared(center) > 49) {
            core.teleport(center.clone().add(0.5, 2.2, 0.5));
        }
        core.setCustomName(ColorUtil.color("&b&lСердце Ветра &8[&f" + (int) Math.ceil(coreHealth) + " ❤&8]"));
        Player target = nearestPlayer();
        if (target != null && core instanceof Mob mob) {
            mob.setTarget(target);
        }
    }

    private void leashShulkers() {
        if (center == null || center.getWorld() == null) {
            return;
        }
        int[][] spots = {{6, 5, 0}, {-6, 5, 0}, {0, 6, 6}, {0, 6, -6}, {4, 8, 4}, {-4, 7, -4}};
        int i = 0;
        for (UUID id : List.copyOf(shulkers)) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity shulker) || shulker.isDead()) {
                shulkers.remove(id);
                continue;
            }
            if (shulker.getLocation().distanceSquared(center) > 14 * 14) {
                int[] spot = spots[i % spots.length];
                shulker.teleport(center.clone().add(spot[0] + 0.5, spot[1], spot[2] + 0.5));
            }
            i++;
        }
    }

    private void createBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
        bossBar = Bukkit.createBossBar(ColorUtil.color("&b&lСердце Ветра"), BarColor.BLUE, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        refreshBossBar();
    }

    private void refreshBossBar() {
        if (bossBar == null) {
            return;
        }
        double progress = coreMaxHealth <= 0 ? 0 : Math.max(0.0, Math.min(1.0, coreHealth / coreMaxHealth));
        bossBar.setProgress(progress);
        if (!defeated) {
            bossBar.setTitle(ColorUtil.color("&b&lСердце Ветра &8[&f" + (int) Math.ceil(coreHealth)
                    + "&7/&f" + (int) Math.ceil(coreMaxHealth) + " ❤&8] &7осталось "
                    + ColorUtil.formatDuration(getSecondsRemaining())));
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
        Bukkit.broadcastMessage(ColorUtil.color("&e┼ &#7DF9FFСердце Ветра &fраскололо небо!"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fЯдро стреляет ветряными зарядами — уворачивайся"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fБей ядро и &#d580ffпарящие сундуки &fза лут"));
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
