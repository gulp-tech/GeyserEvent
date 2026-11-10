package ru.geyserevent.ronaldo;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.boss.BossAbilityController;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.schematic.RiftSchematicManager;
import ru.geyserevent.util.ColorUtil;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Ивент «Роналду SIUU»: большой стадион, босс CR7 (husk → скин на Bedrock),
 * волны клонов, крик SIUU, варп на старте и удаление варпа в конце.
 */
public final class RonaldoEventManager {

    public static final String REGION_ID = "geyserevent_ronaldo";
    public static final String SCHEMATIC_FILE = "ronaldo_stadium.schem.yml";
    public static final int ARENA_RADIUS = 34;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final GeyserEventPlugin plugin;
    private final RiftSchematicManager schematicManager;
    private final NamespacedKey cloneKey;
    private final Random random = new Random();

    private boolean active;
    private Location center;
    private long eventEndEpochSeconds;
    private long eventStartEpochSeconds;
    private String openedAtClock = "--:--";
    private int secondsToNextWave;
    private int waveNumber;
    private int siuuTicks;
    private BossBar bossBar;
    private BukkitRunnable tickTask;
    private UUID hologramUuid;
    private UUID bossUuid;
    private boolean bossDefeated;
    private final Set<UUID> cloneIds = new HashSet<>();
    private String activeWarpName;

    public RonaldoEventManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.schematicManager = new RiftSchematicManager(plugin, SCHEMATIC_FILE);
        this.cloneKey = new NamespacedKey(plugin, "ronaldo_clone");
    }

    public boolean startEvent(Location targetCenter, Integer durationSecondsOverride) {
        if (targetCenter == null || targetCenter.getWorld() == null) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("ronaldo.enabled", true)) {
            plugin.getLogger().warning("Ивент «Роналду» выключен (ronaldo.enabled: false).");
            return false;
        }
        if (plugin.getRiftManager() != null && plugin.getRiftManager().isActive()) {
            plugin.getRiftManager().stopEvent(false);
        }
        if (plugin.getAlienInvasionManager() != null && plugin.getAlienInvasionManager().isActive()) {
            plugin.getAlienInvasionManager().stopEvent();
        }
        if (plugin.getGravityHoardManager() != null && plugin.getGravityHoardManager().isActive()) {
            plugin.getGravityHoardManager().stopEvent();
        }
        if (plugin.getSkibidiToiletManager() != null && plugin.getSkibidiToiletManager().isActive()) {
            plugin.getSkibidiToiletManager().stopEvent();
        }
        stopEvent(false);

        targetCenter.getChunk().load(true);
        this.active = true;
        this.center = snapToSurface(targetCenter);
        this.cloneIds.clear();
        this.bossUuid = null;
        this.bossDefeated = false;
        this.waveNumber = 0;
        this.siuuTicks = 0;
        this.eventStartEpochSeconds = System.currentTimeMillis() / 1000L;
        this.openedAtClock = LocalTime.now().format(CLOCK);

        int duration = (durationSecondsOverride != null && durationSecondsOverride > 0)
                ? durationSecondsOverride
                : Math.max(60, plugin.getConfig().getInt("ronaldo.duration-minutes", 20) * 60);
        this.eventEndEpochSeconds = this.eventStartEpochSeconds + duration;
        this.secondsToNextWave = 0;

        setChunksForceLoaded(this.center, true);
        schematicManager.pasteSchematic(this.center, ARENA_RADIUS + 2, 16);
        schematicManager.persistRestoreState(restoreSnapshotFile());
        spawnHologram();
        createBossBar();
        createEventWarp();
        broadcastSpawn();
        spawnBoss();
        spawnWave();
        shoutSiuu(this.center, 2.6f, 1.0f);

        if (plugin.getWorldGuardHook() != null) {
            plugin.getWorldGuardHook().syncProtectionRegion(
                    this.center.getWorld(),
                    REGION_ID,
                    this.center.getBlockX(),
                    this.center.getBlockY(),
                    this.center.getBlockZ(),
                    ARENA_RADIUS + 4
            );
        }
        startTickLoop();
        return true;
    }

    public void stopEvent() {
        stopEvent(true);
    }

    private void stopEvent(boolean clearWarp) {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (clearWarp) {
            deleteEventWarp();
        }
        removeClones();
        removeHologram();
        schematicManager.restoreSchematic();
        schematicManager.restorePersistedState(restoreSnapshotFile());
        if (center != null && center.getWorld() != null && plugin.getWorldGuardHook() != null) {
            plugin.getWorldGuardHook().removeRegion(center.getWorld(), REGION_ID);
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        if (center != null) {
            setChunksForceLoaded(center, false);
        }
        this.active = false;
        this.center = null;
        this.bossUuid = null;
        this.bossDefeated = false;
        this.waveNumber = 0;
        this.secondsToNextWave = 0;
        this.activeWarpName = null;
    }

    public boolean isActive() {
        return active;
    }

    public Location getCenterLocation() {
        return center;
    }

    public Location getBossLocation() {
        if (bossUuid != null) {
            Entity entity = Bukkit.getEntity(bossUuid);
            if (entity != null && entity.isValid() && !entity.isDead()) {
                return entity.getLocation();
            }
        }
        for (UUID id : cloneIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid() && !entity.isDead()) {
                return entity.getLocation();
            }
        }
        return center;
    }

    public int getSecondsRemaining() {
        long left = eventEndEpochSeconds - System.currentTimeMillis() / 1000L;
        return (int) Math.max(0L, left);
    }

    public int getSecondsToNextWave() {
        return Math.max(0, secondsToNextWave);
    }

    public boolean isRonaldo(Entity entity) {
        if (!active || entity == null) {
            return false;
        }
        if (cloneIds.contains(entity.getUniqueId())) {
            return true;
        }
        return entity.getPersistentDataContainer().has(cloneKey, PersistentDataType.BYTE);
    }

    public boolean isBoss(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (bossUuid != null && bossUuid.equals(entity.getUniqueId())) {
            return true;
        }
        Byte tag = entity.getPersistentDataContainer().get(cloneKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 2;
    }

    public boolean isProtectedBlock(Location loc) {
        return active && schematicManager.isProtectedBlock(loc);
    }

    public void handleDeath(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        boolean boss = isBoss(entity);
        cloneIds.remove(entity.getUniqueId());
        if (boss) {
            bossUuid = null;
            bossDefeated = true;
        }
        Location loc = entity.getLocation();
        dropLoot(loc);
        grantKillLevels(entity, boss);
        shoutSiuu(loc, 2.2f, boss ? 0.85f : 1.15f);
        if (loc.getWorld() != null) {
            loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 1.2, 0), 24, 0.4, 0.6, 0.4, 0.02);
        }
    }

    public int rollDeathLevels(boolean boss) {
        int value = boss
                ? plugin.getConfig().getInt("ronaldo.boss-levels", 50)
                : plugin.getConfig().getInt("ronaldo.levels", 30);
        return Math.max(1, value);
    }

    public void cleanupOrphanSchematic() {
        File snapshot = restoreSnapshotFile();
        if (snapshot.exists()) {
            plugin.getLogger().info("Откат схемы «Роналду», оставшейся после рестарта...");
            schematicManager.restorePersistedState(snapshot);
        }
        deleteEventWarp();
    }

    private File restoreSnapshotFile() {
        return new File(plugin.getDataFolder(), "ronaldo-restore.yml");
    }

    private void grantKillLevels(LivingEntity entity, boolean boss) {
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        int levels = rollDeathLevels(boss);
        killer.giveExpLevels(levels);
        killer.sendMessage(ColorUtil.color("&e┠ &f+" + levels + " &aур. &7за "
                + (boss ? "&eCR7" : "&fклона Роналду")));
    }

    private void spawnWave() {
        if (!active || center == null || center.getWorld() == null) {
            return;
        }
        int min = Math.max(1, plugin.getConfig().getInt("ronaldo.wave-min", 3));
        int max = Math.max(min, plugin.getConfig().getInt("ronaldo.wave-max", 6));
        int count = min + random.nextInt(max - min + 1);
        waveNumber++;
        secondsToNextWave = Math.max(30, plugin.getConfig().getInt("ronaldo.wave-interval-seconds", 180));
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Location loc = randomPitchSpawn();
            if (loc == null) {
                continue;
            }
            LivingEntity clone = spawnClone(loc, false);
            if (clone != null) {
                cloneIds.add(clone.getUniqueId());
                spawned++;
            }
        }
        shoutSiuu(center, 2.4f, 1.05f);
        Bukkit.broadcastMessage(ColorUtil.color("&e┼ &e&lSIUUU &f— волна клонов &#E0C912#" + waveNumber
                + " &f(&#E0C912" + spawned + "&f)"));
        refreshHologram();
        refreshBossBar();
    }

    private void spawnBoss() {
        if (bossDefeated || center == null) {
            return;
        }
        if (bossUuid != null) {
            Entity existing = Bukkit.getEntity(bossUuid);
            if (existing != null && existing.isValid() && !existing.isDead()) {
                return;
            }
        }
        Location loc = snapToSurface(center.clone());
        loc.setY(loc.getY() + 1.0);
        LivingEntity boss = spawnClone(loc, true);
        if (boss != null) {
            cloneIds.add(boss.getUniqueId());
            bossUuid = boss.getUniqueId();
        }
    }

    private LivingEntity spawnClone(Location loc, boolean boss) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        loc.getChunk().load(true);
        Husk husk = world.spawn(loc, Husk.class, spawned -> {
            spawned.setSilent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setPersistent(false);
            spawned.setCanPickupItems(false);
            spawned.setCustomNameVisible(true);
            spawned.setCustomName(ColorUtil.color(boss ? "&e&lCR7 &6SIUU" : "&eКлон CR7"));
            spawned.setGravity(true);
            spawned.setAI(true);
            spawned.setAware(true);
            spawned.setCollidable(true);
            spawned.setInvulnerable(false);
            spawned.setAdult();
            spawned.setShouldBurnInDay(false);
            spawned.getPersistentDataContainer().set(cloneKey, PersistentDataType.BYTE, boss ? (byte) 2 : (byte) 1);
        });
        if (husk == null || !husk.isValid()) {
            return null;
        }
        husk.setSilent(true);
        if (husk instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
            try {
                zombie.setConversionTime(-1);
            } catch (Throwable ignored) {
            }
        }
        husk.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 2000000, 0, false, false));
        double health = Math.max(1.0, plugin.getConfig().getDouble(
                boss ? "ronaldo.boss-health" : "ronaldo.health", boss ? 900.0 : 220.0));
        double damage = Math.max(1.0, plugin.getConfig().getDouble(
                boss ? "ronaldo.boss-damage" : "ronaldo.damage", boss ? 40.0 : 30.0));
        double scale = plugin.getConfig().getDouble(
                boss ? "ronaldo.boss-scale" : "ronaldo.scale", boss ? 1.35 : 1.0);
        scale = Math.min(scale, boss ? 1.5 : 1.15);
        BossAbilityController.applyEntityScale(husk, scale);
        BossAbilityController.setEntityAttribute(husk, "MAX_HEALTH", "generic.max_health", health);
        BossAbilityController.setEntityAttribute(husk, "ATTACK_DAMAGE", "generic.attack_damage", damage);
        BossAbilityController.setEntityAttribute(husk, "FOLLOW_RANGE", "generic.follow_range", 48.0);
        BossAbilityController.setEntityAttribute(husk, "MOVEMENT_SPEED", "generic.movement_speed",
                boss ? 0.32 : 0.28);
        husk.setHealth(Math.min(health, husk.getMaxHealth()));
        acquireTarget(husk);
        world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1.2, 0), 16, 0.4, 0.5, 0.4, 0.02);
        return husk;
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
                    Bukkit.broadcastMessage(ColorUtil.color("&e┼ &eРоналду &cушёл со стадиона. Варп снят."));
                    stopEvent();
                    return;
                }
                pruneDead();
                keepOnPitch();
                if (!bossDefeated) {
                    spawnBoss();
                }
                secondsToNextWave--;
                if (secondsToNextWave <= 0) {
                    spawnWave();
                }
                siuuTicks++;
                int every = Math.max(4, plugin.getConfig().getInt("ronaldo.siuu-interval-seconds", 8));
                if (siuuTicks >= every) {
                    siuuTicks = 0;
                    shoutSiuu(getBossLocation() != null ? getBossLocation() : center, 2.0f, 1.0f);
                }
                refreshHologram();
                refreshBossBar();
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void pruneDead() {
        Iterator<UUID> it = cloneIds.iterator();
        while (it.hasNext()) {
            Entity entity = Bukkit.getEntity(it.next());
            if (entity == null || !entity.isValid() || entity.isDead()) {
                it.remove();
            }
        }
    }

    private void keepOnPitch() {
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        double maxDist = (ARENA_RADIUS - 4.0) * (ARENA_RADIUS - 4.0);
        for (UUID id : new ArrayList<>(cloneIds)) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity living) || living.isDead()) {
                continue;
            }
            Location loc = living.getLocation();
            int surfaceY = world.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (loc.getY() < surfaceY - 1.5 || loc.getY() < center.getY() - 3) {
                Location up = new Location(world, loc.getX(), surfaceY + 1.0, loc.getZ(), loc.getYaw(), loc.getPitch());
                living.teleport(up);
                loc = up;
            }
            if (loc.distanceSquared(center) > maxDist) {
                Location back = isBoss(living) ? snapToSurface(center.clone()) : randomPitchSpawn();
                if (back != null) {
                    back.setY(back.getY() + 1.0);
                    living.teleport(back);
                }
            }
            if (living instanceof Mob mob && (mob.getTarget() == null || mob.getTarget().isDead())) {
                acquireTarget(living);
            }
        }
    }

    private void acquireTarget(LivingEntity clone) {
        if (!(clone instanceof Mob mob) || clone.getWorld() == null) {
            return;
        }
        Player best = null;
        double bestDist = 48 * 48;
        for (Player player : clone.getWorld().getPlayers()) {
            if (!plugin.getBedrockService().canParticipate(player) || player.isDead()) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(clone.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        mob.setTarget(best);
    }

    private void dropLoot(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        List<PluginConfig.AlienLootItem> loot = plugin.getPluginConfig().getRonaldoLoot();
        if (loot == null || loot.isEmpty()) {
            loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.GOLD_INGOT, 1 + random.nextInt(3)));
            return;
        }
        for (PluginConfig.AlienLootItem item : loot) {
            if (item.material() == null || item.material().isAir()) {
                continue;
            }
            if (random.nextDouble() * 100.0 > item.chancePercent()) {
                continue;
            }
            int min = Math.max(1, item.minAmount());
            int max = Math.max(min, item.maxAmount());
            int amount = min + random.nextInt(max - min + 1);
            ItemStack stack = ru.geyserevent.util.LootStacks.fromAlien(item, amount);
            Item dropped = loc.getWorld().dropItemNaturally(loc, stack);
            dropped.setPickupDelay(5);
        }
    }

    private void spawnHologram() {
        removeHologram();
        if (center == null || center.getWorld() == null) {
            return;
        }
        Location loc = hologramLocation();
        loc.getChunk().load(true);
        TextDisplay display = center.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setGravity(false);
            d.setInvulnerable(true);
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(280);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(170, 24, 16, 0));
            d.setText(hologramText());
        });
        enlargeDisplay(display, 3.6f);
        hologramUuid = display.getUniqueId();
    }

    private void refreshHologram() {
        if (!active) {
            return;
        }
        Entity entity = hologramUuid == null ? null : Bukkit.getEntity(hologramUuid);
        if (!(entity instanceof TextDisplay display) || !display.isValid()) {
            spawnHologram();
            return;
        }
        display.setText(hologramText());
        display.setBillboard(Display.Billboard.CENTER);
        display.setGravity(false);
        Location home = hologramLocation();
        if (home != null && display.getLocation().distanceSquared(home) > 0.25) {
            display.teleport(home);
        }
    }

    private Location hologramLocation() {
        if (center == null) {
            return null;
        }
        Location loc = new Location(center.getWorld(), center.getX(), center.getY() + 3.2, center.getZ() - 28.5);
        loc.setYaw(180f);
        loc.setPitch(0f);
        return loc;
    }

    private String hologramText() {
        String next = secondsToNextWave <= 0
                ? "сейчас"
                : ColorUtil.formatDuration(secondsToNextWave);
        String warp = plugin.getConfig().getString("ronaldo.warp-name", "ronaldo");
        return ColorUtil.color("&e&lSIUU  Роналду\n"
                + "&7Открытие: &#E0C912" + openedAtClock + "\n"
                + "&7Следующая волна: &#E0C912" + next + "\n"
                + "&7Варп: &#E0C912/" + "warp " + warp);
    }

    private void enlargeDisplay(Display display, float scale) {
        try {
            Transformation transformation = display.getTransformation();
            Object vec = transformation.getClass().getMethod("getScale").invoke(transformation);
            vec.getClass().getMethod("set", float.class, float.class, float.class)
                    .invoke(vec, scale, scale, scale);
            display.setTransformation(transformation);
        } catch (Throwable ignored) {
        }
    }

    private void removeHologram() {
        if (hologramUuid != null) {
            Entity entity = Bukkit.getEntity(hologramUuid);
            if (entity != null) {
                entity.remove();
            }
            hologramUuid = null;
        }
    }

    private void removeClones() {
        for (UUID id : cloneIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        cloneIds.clear();
    }

    private void createBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
        bossBar = Bukkit.createBossBar(ColorUtil.color("&e&lРоналду SIUU"), BarColor.YELLOW, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        refreshBossBar();
    }

    private void refreshBossBar() {
        if (bossBar == null) {
            return;
        }
        int left = getSecondsRemaining();
        int total = Math.max(1, plugin.getConfig().getInt("ronaldo.duration-minutes", 20) * 60);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, left / (double) total)));
        bossBar.setTitle(ColorUtil.color("&e&lSIUU &8| &fволна &#E0C912"
                + ColorUtil.formatDuration(Math.max(0, secondsToNextWave))
                + " &8| &fна поле &#E0C912" + aliveCount()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getBedrockService().canParticipate(player)) {
                bossBar.addPlayer(player);
            }
        }
    }

    private int aliveCount() {
        int n = 0;
        for (UUID id : cloneIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid() && !entity.isDead()) {
                n++;
            }
        }
        return n;
    }

    private void broadcastSpawn() {
        if (center == null) {
            return;
        }
        String warp = plugin.getConfig().getString("ronaldo.warp-name", "ronaldo");
        Bukkit.broadcastMessage(ColorUtil.color("&e┼ &e&lSIUUUUU &f— Роналду вышел на стадион!"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fБольшая арена, босс &#E0C912CR7 &fи волны клонов."));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fВарп: &#E0C912/warp " + warp + " &8(снимется после ивента)"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fКоординаты: &#E0C912"
                + center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ()));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fЛокатор: &#34D711/event gps"));
    }

    private Location randomPitchSpawn() {
        if (center == null || center.getWorld() == null) {
            return null;
        }
        World world = center.getWorld();
        for (int attempt = 0; attempt < 28; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = 8.0 + random.nextDouble() * 14.0;
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (y < world.getMinHeight() + 4) {
                continue;
            }
            Block ground = world.getBlockAt(x, y, z);
            if (!ground.getType().isSolid()) {
                y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                ground = world.getBlockAt(x, y, z);
            }
            if (!ground.getType().isSolid()) {
                continue;
            }
            return new Location(world, x + 0.5, y + 1.0, z + 0.5);
        }
        Location fallback = snapToSurface(center.clone().add(10, 0, 0));
        fallback.setY(fallback.getY() + 1.0);
        return fallback;
    }

    private Location snapToSurface(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private void shoutSiuu(Location loc, float volume, float pitch) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        loc.getWorld().playSound(loc, "ronaldo_siuu", volume, pitch);
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= 80 * 80) {
                player.playSound(player.getLocation(), "ronaldo_siuu", volume, pitch);
            }
        }
    }

    private void createEventWarp() {
        if (center == null) {
            return;
        }
        String name = warpName();
        this.activeWarpName = name;
        Location warpLoc = snapToSurface(center.clone());
        warpLoc.setY(warpLoc.getY() + 1.0);
        if (essentialsWarp(true, name, warpLoc)) {
            plugin.getLogger().info("Essentials: /setwarp " + name + " @ "
                    + warpLoc.getBlockX() + " " + warpLoc.getBlockY() + " " + warpLoc.getBlockZ());
        } else {
            plugin.getLogger().warning("Не удалось /setwarp " + name
                    + " — консоль Essentials setwarp не умеет. Нужен плагин Essentials.");
        }
    }

    private void deleteEventWarp() {
        String name = activeWarpName != null ? activeWarpName : warpName();
        if (essentialsWarp(false, name, null)) {
            plugin.getLogger().info("Essentials: /delwarp " + name);
        }
        this.activeWarpName = null;
    }

    private String warpName() {
        String name = plugin.getConfig().getString("ronaldo.warp-name", "ronaldo");
        return (name == null || name.isBlank()) ? "ronaldo" : name.trim();
    }

    private Plugin findEssentials() {
        for (String id : new String[]{"Essentials", "EssentialsX", "EssentialsX-Core"}) {
            Plugin ess = Bukkit.getPluginManager().getPlugin(id);
            if (ess != null && ess.isEnabled()) {
                return ess;
            }
        }
        return null;
    }

    private boolean essentialsWarp(boolean set, String name, Location loc) {
        Plugin ess = findEssentials();
        if (ess == null) {
            plugin.getLogger().warning("Essentials не найден — варп " + name + " не выставлен.");
            return false;
        }
        try {
            Object warps = ess.getClass().getMethod("getWarps").invoke(ess);
            if (set) {
                invokeNamed(warps, name, loc, "setWarp");
            } else {
                try {
                    invokeNamed(warps, name, null, "removeWarp", "delWarp", "remove");
                } catch (NoSuchMethodException missing) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "delwarp " + name);
                    return true;
                }
            }
            return true;
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            plugin.getLogger().warning("Essentials " + (set ? "setwarp" : "delwarp") + " " + name + ": " + cause);
            return false;
        }
    }

    private static void invokeNamed(Object warps, String name, Location loc, String... methods) throws Exception {
        NoSuchMethodException last = null;
        for (String method : methods) {
            try {
                if (loc != null) {
                    warps.getClass().getMethod(method, String.class, Location.class).invoke(warps, name, loc);
                } else {
                    warps.getClass().getMethod(method, String.class).invoke(warps, name);
                }
                return;
            } catch (NoSuchMethodException ex) {
                last = ex;
            }
        }
        if (last != null) {
            throw last;
        }
    }

    private void setChunksForceLoaded(Location loc, boolean loaded) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        World world = loc.getWorld();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                Chunk chunk = world.getChunkAt(cx + dx, cz + dz);
                chunk.setForceLoaded(loaded);
                if (loaded) {
                    chunk.load(true);
                }
            }
        }
    }
}
