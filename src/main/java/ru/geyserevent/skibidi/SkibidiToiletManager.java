package ru.geyserevent.skibidi;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
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
 * Ивент «Шкибиди Туалет»: схема + волны 3D-моделей (ravager → skibidi на Bedrock).
 * Малые: урон 30, 30 ур.; большой: урон 40, 50 ур. Прыжки выключены.
 */
public final class SkibidiToiletManager {

    public static final String REGION_ID = "geyserevent_skibidi";
    public static final String SCHEMATIC_FILE = "skibidi_toilet.schem.yml";
    public static final int ARENA_RADIUS = 20;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final GeyserEventPlugin plugin;
    private final RiftSchematicManager schematicManager;
    private final NamespacedKey toiletKey;
    private final Random random = new Random();

    private boolean active;
    private Location center;
    private long eventEndEpochSeconds;
    private long eventStartEpochSeconds;
    private String openedAtClock = "--:--";
    private int secondsToNextWave;
    private int waveNumber;
    private int ambientTicks;
    private BossBar bossBar;
    private BukkitRunnable tickTask;
    private BukkitRunnable jumpTask;
    private final List<UUID> hologramIds = new ArrayList<>();
    private UUID bigToiletUuid;
    private Location toiletHome;
    private boolean bigDefeated;
    private final Set<UUID> toiletIds = new HashSet<>();

    public SkibidiToiletManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.schematicManager = new RiftSchematicManager(plugin, SCHEMATIC_FILE);
        this.toiletKey = new NamespacedKey(plugin, "skibidi_toilet");
    }

    public boolean startEvent(Location targetCenter, Integer durationSecondsOverride) {
        if (targetCenter == null || targetCenter.getWorld() == null) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("skibidi.enabled", true)) {
            plugin.getLogger().warning("Ивент «Шкибиди Туалет» выключен (skibidi.enabled: false).");
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
        if (plugin.getRonaldoEventManager() != null && plugin.getRonaldoEventManager().isActive()) {
            plugin.getRonaldoEventManager().stopEvent();
        }
        stopEvent();

        targetCenter.getChunk().load(true);
        this.active = true;
        this.center = snapToSurface(targetCenter);
        this.toiletIds.clear();
        this.bigToiletUuid = null;
        this.bigDefeated = false;
        this.waveNumber = 0;
        this.ambientTicks = 0;
        this.eventStartEpochSeconds = System.currentTimeMillis() / 1000L;
        this.openedAtClock = LocalTime.now().format(CLOCK);

        int duration = (durationSecondsOverride != null && durationSecondsOverride > 0)
                ? durationSecondsOverride
                : resolveDurationSeconds();
        this.eventEndEpochSeconds = this.eventStartEpochSeconds + duration;
        this.secondsToNextWave = 0;

        setChunksForceLoaded(this.center, true);
        schematicManager.pasteSchematic(this.center, ARENA_RADIUS, 8);
        this.toiletHome = bigToiletSpawnLocation();
        if (this.toiletHome == null) {
            this.toiletHome = this.center.clone().add(0.5, 1.0, -18.0);
        }
        schematicManager.persistRestoreState(restoreSnapshotFile());
        spawnHologram();
        createBossBar();
        broadcastSpawn();
        spawnBigToilet();
        spawnWave();

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
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (jumpTask != null) {
            jumpTask.cancel();
            jumpTask = null;
        }
        removeToilets();
        removeHologram();
        schematicManager.restoreSchematic();
        schematicManager.discardPersistedState(restoreSnapshotFile());
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
        this.toiletHome = null;
        this.bigToiletUuid = null;
        this.bigDefeated = false;
        this.waveNumber = 0;
        this.secondsToNextWave = 0;
    }

    public boolean isActive() {
        return active;
    }

    public Location getCenterLocation() {
        return center;
    }

    public Location getToiletLocation() {
        for (UUID id : toiletIds) {
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

    public boolean isToilet(Entity entity) {
        if (!active || entity == null) {
            return false;
        }
        if (toiletIds.contains(entity.getUniqueId())) {
            return true;
        }
        return entity.getPersistentDataContainer().has(toiletKey, PersistentDataType.BYTE);
    }

    public boolean isProtectedBlock(Location loc) {
        return active && schematicManager.isProtectedBlock(loc);
    }

    public void handleToiletDeath(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        boolean big = isBigToilet(entity);
        toiletIds.remove(entity.getUniqueId());
        if (big) {
            bigToiletUuid = null;
            bigDefeated = true;
        }
        Location loc = entity.getLocation();
        dropLoot(loc);
        grantKillLevels(entity, big);
        playCustomSound(loc, "skibidi_death", 1.6f, 1.0f);
        if (loc.getWorld() != null) {
            loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 1.2, 0), 28, 0.5, 0.6, 0.5, 0.04);
        }
    }

    public boolean isBigToilet(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (bigToiletUuid != null && bigToiletUuid.equals(entity.getUniqueId())) {
            return true;
        }
        Byte tag = entity.getPersistentDataContainer().get(toiletKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 2;
    }

    public int rollDeathLevels(boolean big) {
        int value = big
                ? plugin.getConfig().getInt("skibidi.big-levels", 50)
                : plugin.getConfig().getInt("skibidi.levels", 30);
        return Math.max(1, value);
    }

    public void cleanupOrphanSchematic() {
        File snapshot = restoreSnapshotFile();
        if (snapshot.exists()) {
            plugin.getLogger().info("Откат схемы «Шкибиди Туалет», оставшейся после рестарта...");
            schematicManager.restorePersistedState(snapshot);
        }
    }

    private File restoreSnapshotFile() {
        return new File(plugin.getDataFolder(), "skibidi-restore.yml");
    }

    private void grantKillLevels(LivingEntity entity, boolean big) {
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        int levels = rollDeathLevels(big);
        killer.giveExpLevels(levels);
        killer.sendMessage(ColorUtil.color("&e┠ &f+" + levels + " &aур. &7за шкибиди-туалет"
                + (big ? " &8(&eбольшой&8)" : "")));
    }

    private void spawnWave() {
        if (!active || center == null || center.getWorld() == null) {
            return;
        }
        int min = Math.max(1, plugin.getConfig().getInt("skibidi.wave-min", 2));
        int max = Math.max(min, plugin.getConfig().getInt("skibidi.wave-max", 5));
        int count = min + random.nextInt(max - min + 1);
        waveNumber++;
        secondsToNextWave = Math.max(30, plugin.getConfig().getInt("skibidi.wave-interval-seconds", 300));
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Location loc = randomSurfaceSpawn();
            if (loc == null) {
                continue;
            }
            LivingEntity toilet = spawnToilet(loc, false);
            if (toilet != null) {
                toiletIds.add(toilet.getUniqueId());
                spawned++;
            }
        }
        playCustomSound(center, "skibidi_boss", 2.2f, 0.85f);
        Bukkit.broadcastMessage(ColorUtil.color("&e┼ &f&lШкибиди Туалет &f— волна &#E0C912#" + waveNumber
                + " &f(туалетов: &#E0C912" + spawned + "&f)"));
        refreshHologram();
        refreshBossBar();
    }

    private void spawnBigToilet() {
        if (bigDefeated || toiletHome == null) {
            return;
        }
        if (bigToiletUuid != null) {
            Entity existing = Bukkit.getEntity(bigToiletUuid);
            if (existing != null && existing.isValid() && !existing.isDead()) {
                return;
            }
        }
        LivingEntity big = spawnToilet(toiletHome, true);
        if (big != null) {
            toiletIds.add(big.getUniqueId());
            bigToiletUuid = big.getUniqueId();
        }
    }

    private LivingEntity spawnToilet(Location loc, boolean big) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        loc.getChunk().load(true);
        Ravager ravager = world.spawn(loc, Ravager.class, spawned -> {
            spawned.setSilent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setPersistent(false);
            spawned.setCanPickupItems(false);
            spawned.setCustomNameVisible(true);
            spawned.setCustomName(ColorUtil.color(big ? "&6&lБольшой Шкибиди Туалет" : "&f&lШкибиди Туалет"));
            spawned.setGravity(true);
            spawned.setAI(true);
            spawned.setAware(true);
            spawned.setCollidable(false);
            spawned.setInvulnerable(false);
            spawned.getPersistentDataContainer().set(toiletKey, PersistentDataType.BYTE, big ? (byte) 2 : (byte) 1);
        });
        if (ravager == null || !ravager.isValid()) {
            return null;
        }
        ravager.setSilent(true);
        boolean legacy = isLegacySpitConfig();
        double health = Math.max(1.0, plugin.getConfig().getDouble(
                big ? "skibidi.big-health" : "skibidi.health", 600.0));
        double damage = big
                ? Math.max(1.0, plugin.getConfig().getDouble("skibidi.big-damage", 40.0))
                : Math.max(1.0, legacy ? 30.0 : plugin.getConfig().getDouble("skibidi.damage", 30.0));
        if (big && legacy) {
            damage = 40.0;
        }
        double scale = plugin.getConfig().getDouble(
                big ? "skibidi.big-scale" : "skibidi.scale", big ? 1.15 : 1.0);
        scale = Math.min(scale, big ? 1.2 : 1.1);
        BossAbilityController.applyEntityScale(ravager, scale);
        BossAbilityController.setEntityAttribute(ravager, "MAX_HEALTH", "generic.max_health", health);
        BossAbilityController.setEntityAttribute(ravager, "ATTACK_DAMAGE", "generic.attack_damage", damage);
        BossAbilityController.setEntityAttribute(ravager, "FOLLOW_RANGE", "generic.follow_range", 48.0);
        BossAbilityController.setEntityAttribute(ravager, "KNOCKBACK_RESISTANCE", "generic.knockback_resistance", 0.85);
        BossAbilityController.setEntityAttribute(ravager, "JUMP_STRENGTH", "generic.jump_strength", 0.0);
        BossAbilityController.setEntityAttribute(ravager, "JUMP_STRENGTH", "jump_strength", 0.0);
        ravager.setHealth(Math.min(health, ravager.getMaxHealth()));
        acquireTarget(ravager);
        world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1.4, 0), 18, 0.45, 0.4, 0.45, 0.03);
        playCustomSound(loc, "skibidi_chase", 1.1f, 1.0f);
        return ravager;
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
                    Bukkit.broadcastMessage(ColorUtil.color("&e┼ &fШкибиди Туалет &cуехал."));
                    stopEvent();
                    return;
                }
                pruneDeadToilets();
                keepToiletsOnSurface();
                if (!bigDefeated) {
                    spawnBigToilet();
                }
                secondsToNextWave--;
                if (secondsToNextWave <= 0) {
                    spawnWave();
                }
                ambientTicks++;
                if (ambientTicks >= 8) {
                    ambientTicks = 0;
                    playAmbient();
                }
                refreshHologram();
                refreshBossBar();
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
        if (jumpTask != null) {
            jumpTask.cancel();
        }
        jumpTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) {
                    cancel();
                    return;
                }
                for (UUID id : new ArrayList<>(toiletIds)) {
                    Entity entity = Bukkit.getEntity(id);
                    if (entity instanceof LivingEntity living && !living.isDead()) {
                        suppressJump(living);
                    }
                }
            }
        };
        jumpTask.runTaskTimer(plugin, 1L, 1L);
    }

    private void suppressJump(LivingEntity living) {
        try {
            living.setJumping(false);
        } catch (Throwable ignored) {
        }
        Vector velocity = living.getVelocity();
        if (living.isOnGround() && velocity.getY() > 0) {
            velocity.setY(0);
            living.setVelocity(velocity);
        } else if (!living.isOnGround() && velocity.getY() > 0.12) {
            velocity.setY(0);
            living.setVelocity(velocity);
        }
    }

    private void pruneDeadToilets() {
        Iterator<UUID> it = toiletIds.iterator();
        while (it.hasNext()) {
            Entity entity = Bukkit.getEntity(it.next());
            if (entity == null || !entity.isValid() || entity.isDead()) {
                it.remove();
            }
        }
    }

    private void keepToiletsOnSurface() {
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        for (UUID id : new ArrayList<>(toiletIds)) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity living) || living.isDead()) {
                continue;
            }
            Location loc = living.getLocation();
            boolean big = isBigToilet(living);
            if (big && toiletHome != null) {
                if (loc.distanceSquared(toiletHome) > 25.0) {
                    living.teleport(toiletHome);
                    loc = toiletHome;
                }
            } else {
                int surfaceY = world.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (loc.getY() < surfaceY - 1.5 || loc.getY() < center.getY() - 3) {
                    Location up = new Location(world, loc.getX(), surfaceY + 1.0, loc.getZ(), loc.getYaw(), loc.getPitch());
                    living.teleport(up);
                    loc = up;
                }
                if (loc.distanceSquared(center) > 48 * 48) {
                    Location back = randomSurfaceSpawn();
                    if (back != null) {
                        living.teleport(back);
                    }
                }
            }
            living.setCollidable(false);
            if (living instanceof Mob mob && (mob.getTarget() == null || mob.getTarget().isDead())) {
                acquireTarget(living);
            }
        }
    }

    private void acquireTarget(LivingEntity toilet) {
        if (!(toilet instanceof Mob mob) || toilet.getWorld() == null) {
            return;
        }
        Player best = null;
        double bestDist = 40 * 40;
        for (Player player : toilet.getWorld().getPlayers()) {
            if (!plugin.getBedrockService().canParticipate(player) || player.isDead()) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(toilet.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        mob.setTarget(best);
    }

    private void playAmbient() {
        Location src = getToiletLocation();
        if (src == null) {
            src = center;
        }
        if (src == null) {
            return;
        }
        boolean chasing = false;
        for (UUID id : toiletIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Mob mob && mob.getTarget() instanceof Player) {
                chasing = true;
                break;
            }
        }
        playCustomSound(src, chasing ? "skibidi_chase" : "skibidi_ambient", chasing ? 1.0f : 0.7f, chasing ? 1.05f : 0.9f);
    }

    private void dropLoot(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        List<PluginConfig.AlienLootItem> loot = plugin.getPluginConfig().getSkibidiLoot();
        if (loot == null || loot.isEmpty()) {
            loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.IRON_INGOT, 1 + random.nextInt(3)));
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

    private Location bigToiletSpawnLocation() {
        if (center == null || center.getWorld() == null) {
            return null;
        }
        Location loc = snapToSurface(center.clone().add(0, 0, -12));
        loc.setY(loc.getY() + 1.0);
        loc.setYaw(180f);
        loc.setPitch(0f);
        return loc;
    }

    private void spawnHologram() {
        removeHologram();
        if (center == null || center.getWorld() == null) {
            return;
        }
        Location base = hologramLocation();
        if (base == null) {
            return;
        }
        base.getChunk().load(true);
        String[] lines = hologramLines();
        for (int i = 0; i < lines.length; i++) {
            Location loc = base.clone().add(0, -0.28 * i, 0);
            final String line = lines[i];
            ArmorStand stand = center.getWorld().spawn(loc, ArmorStand.class, s -> {
                s.setPersistent(false);
                s.setGravity(false);
                s.setInvulnerable(true);
                s.setInvisible(true);
                s.setBasePlate(false);
                s.setArms(false);
                s.setSmall(true);
                s.setCollidable(false);
                s.setCustomNameVisible(true);
                s.setCustomName(line);
                s.setSilent(true);
            });
            hologramIds.add(stand.getUniqueId());
        }
    }

    private void refreshHologram() {
        if (!active) {
            return;
        }
        String[] lines = hologramLines();
        if (hologramIds.size() != lines.length) {
            spawnHologram();
            return;
        }
        Location base = hologramLocation();
        for (int i = 0; i < lines.length; i++) {
            Entity entity = Bukkit.getEntity(hologramIds.get(i));
            if (!(entity instanceof ArmorStand stand) || !stand.isValid()) {
                spawnHologram();
                return;
            }
            stand.setCustomName(lines[i]);
            stand.setCustomNameVisible(true);
            stand.setInvisible(true);
            stand.setGravity(false);
            if (base != null) {
                Location home = base.clone().add(0, -0.28 * i, 0);
                if (stand.getLocation().distanceSquared(home) > 0.25) {
                    stand.teleport(home);
                }
            }
        }
    }

    private Location hologramLocation() {
        if (center == null) {
            return null;
        }
        return new Location(center.getWorld(), center.getX(), center.getY() + 2.8, center.getZ());
    }

    private String[] hologramLines() {
        String next = secondsToNextWave <= 0
                ? "сейчас"
                : ColorUtil.formatDuration(secondsToNextWave);
        return new String[]{
                ColorUtil.color("&f&lУбейте Шкибиди"),
                ColorUtil.color("&7До конца: &#E0C912" + ColorUtil.formatDuration(getSecondsRemaining())),
                ColorUtil.color("&7Волна: &#E0C912" + next)
        };
    }

    private void removeHologram() {
        for (UUID id : hologramIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        hologramIds.clear();
    }

    private void removeToilets() {
        for (UUID id : toiletIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        toiletIds.clear();
    }

    private void createBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
        bossBar = Bukkit.createBossBar(ColorUtil.color("&f&lШкибиди Туалет"), BarColor.WHITE, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        refreshBossBar();
    }

    private void refreshBossBar() {
        if (bossBar == null) {
            return;
        }
        int left = getSecondsRemaining();
        int total = Math.max(1, resolveDurationSeconds());
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, left / (double) total)));
        bossBar.setTitle(ColorUtil.color("&f&lШкибиди Туалет &8| &eволна &#E0C912"
                + ColorUtil.formatDuration(Math.max(0, secondsToNextWave))
                + " &8| &fживых &#E0C912" + aliveToiletCount()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getBedrockService().canParticipate(player)) {
                bossBar.addPlayer(player);
            }
        }
    }

    private int aliveToiletCount() {
        int n = 0;
        for (UUID id : toiletIds) {
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
        Bukkit.broadcastMessage(ColorUtil.color("&e┼ &f&lШкибиди Туалет &fприехал на карту!"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fВолны каждые &#E0C9125 мин&f: &#E0C9122–5 туалетов"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fМалые: урон &#E0C91230&f, &#E0C91230 ур."));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fБольшой: урон &#E0C91240&f, &#E0C91250 ур."));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fКоординаты: &#E0C912"
                + center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ()));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fЛокатор: &#34D711/event gps"));
    }

    private Location randomSurfaceSpawn() {
        if (center == null || center.getWorld() == null) {
            return null;
        }
        World world = center.getWorld();
        for (int attempt = 0; attempt < 28; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = 14.0 + random.nextDouble() * 7.0;
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (y < world.getMinHeight() + 4) {
                continue;
            }
            if (y < center.getBlockY() - 2) {
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
            Location loc = new Location(world, x + 0.5, y + 1.0, z + 0.5);
            Block feet = loc.getBlock();
            Block head = loc.clone().add(0, 1, 0).getBlock();
            if (feet.isLiquid() && head.isLiquid()) {
                continue;
            }
            if (!feet.getType().isAir() && feet.getType().isSolid()) {
                loc.setY(y + 1.5);
            }
            return loc;
        }
        Location fallback = snapToSurface(center.clone().add(16, 0, 0));
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

    private boolean isLegacySpitConfig() {
        return plugin.getConfig().isSet("skibidi.spit-interval-ticks")
                && !plugin.getConfig().isSet("skibidi.big-damage");
    }

    private int resolveDurationSeconds() {
        if (plugin.getConfig().isSet("skibidi.duration-minutes")) {
            return Math.max(60, plugin.getConfig().getInt("skibidi.duration-minutes", 8) * 60);
        }
        if (isLegacySpitConfig()) {
            return 8 * 60;
        }
        int seconds = plugin.getConfig().getInt("skibidi.duration-seconds", 0);
        if (seconds >= 30) {
            return seconds;
        }
        return 8 * 60;
    }

    private void playCustomSound(Location loc, String key, float volume, float pitch) {
        if (loc == null || loc.getWorld() == null || key == null) {
            return;
        }
        loc.getWorld().playSound(loc, key, volume, pitch);
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
}
