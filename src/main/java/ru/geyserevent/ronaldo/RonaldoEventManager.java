package ru.geyserevent.ronaldo;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Illusioner;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Spellcaster;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
 * Ивент «Роналду SIUU»: стадион, один босс CR7 (illusioner → скин только на нём),
 * прыжки, файерболы, SIUU + голос. Варп на старте, удаление в конце.
 */
public final class RonaldoEventManager {

    public static final String REGION_ID = "geyserevent_ronaldo";
    public static final String SCHEMATIC_FILE = "ronaldo_stadium.schem.yml";
    public static final int ARENA_RADIUS = 26;

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
    private int siuuTicks;
    private int talkTicks;
    private int jumpTicks;
    private int shootTicks;
    private int dashTicks;
    private int celebrateTicks;
    private int stepTicks;
    private int freeKickTicks;
    private int trapTicks;
    private long lastKickMs;
    private boolean jumping;
    private boolean chargingFreeKick;
    private BossBar bossBar;
    private BukkitRunnable tickTask;
    private final List<UUID> hologramIds = new ArrayList<>();
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
        this.eventStartEpochSeconds = System.currentTimeMillis() / 1000L;
        this.openedAtClock = LocalTime.now().format(CLOCK);

        int duration = (durationSecondsOverride != null && durationSecondsOverride > 0)
                ? durationSecondsOverride
                : Math.max(60, plugin.getConfig().getInt("ronaldo.duration-minutes", 8) * 60);
        this.eventEndEpochSeconds = this.eventStartEpochSeconds + duration;
        this.siuuTicks = 0;
        this.talkTicks = 4;
        this.jumpTicks = 3;
        this.shootTicks = 2;
        this.dashTicks = 5;
        this.celebrateTicks = 10;
        this.stepTicks = 2;
        this.freeKickTicks = 8;
        this.trapTicks = 3;
        this.lastKickMs = 0L;
        this.jumping = false;
        this.chargingFreeKick = false;

        setChunksForceLoaded(this.center, true);
        schematicManager.pasteSchematic(this.center, ARENA_RADIUS + 2, 16);
        schematicManager.persistRestoreState(restoreSnapshotFile());
        spawnHologram();
        createBossBar();
        createEventWarp();
        broadcastSpawn();
        spawnBoss();
        LivingEntity boss = livingBoss();
        if (boss != null) {
            playKeyed(boss.getLocation(), "ronaldo_siuu", 2.2f, 1.0f);
        }

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
        stopCustomSounds();
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
        return getSecondsRemaining();
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
        if (boss) {
            playKeyed(loc, "ronaldo_siuu", 2.2f, 0.9f, true);
            spawnBurst(loc, Color.RED, Color.GREEN);
            if (loc.getWorld() != null) {
                loc.getWorld().strikeLightningEffect(loc);
            }
            dropTrophy(loc);
            Bukkit.getScheduler().runTaskLater(plugin, this::stopCustomSounds, 100L);
        }
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
        killer.sendMessage(ColorUtil.color("&e┠ &f+" + levels + " &aур. &7за &eCR7"));
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
        LivingEntity boss = spawnBossEntity(loc);
        if (boss != null) {
            cloneIds.add(boss.getUniqueId());
            bossUuid = boss.getUniqueId();
        }
    }

    private LivingEntity spawnBossEntity(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        loc.getChunk().load(true);
        Illusioner mob = world.spawn(loc, Illusioner.class, spawned -> {
            spawned.setSilent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setPersistent(false);
            spawned.setCanPickupItems(false);
            spawned.setCustomNameVisible(true);
            spawned.setCustomName(ColorUtil.color("&e&lCR7"));
            spawned.setGravity(true);
            spawned.setAI(true);
            spawned.setAware(true);
            spawned.setCollidable(true);
            spawned.setInvulnerable(false);
            spawned.getPersistentDataContainer().set(cloneKey, PersistentDataType.BYTE, (byte) 2);
            try {
                spawned.setSpell(Spellcaster.Spell.NONE);
            } catch (Throwable ignored) {
            }
        });
        if (mob == null || !mob.isValid()) {
            return null;
        }
        mob.setSilent(true);
        mob.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 2000000, 0, false, false));
        double health = Math.max(1.0, plugin.getConfig().getDouble("ronaldo.boss-health", 900.0));
        double damage = Math.max(1.0, plugin.getConfig().getDouble("ronaldo.boss-damage", 40.0));
        double scale = Math.min(1.5, plugin.getConfig().getDouble("ronaldo.boss-scale", 1.35));
        BossAbilityController.applyEntityScale(mob, scale);
        BossAbilityController.setEntityAttribute(mob, "MAX_HEALTH", "generic.max_health", health);
        BossAbilityController.setEntityAttribute(mob, "ATTACK_DAMAGE", "generic.attack_damage", damage);
        BossAbilityController.setEntityAttribute(mob, "FOLLOW_RANGE", "generic.follow_range", 48.0);
        BossAbilityController.setEntityAttribute(mob, "MOVEMENT_SPEED", "generic.movement_speed", 0.34);
        mob.setHealth(Math.min(health, mob.getMaxHealth()));
        acquireTarget(mob);
        world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1.2, 0), 16, 0.4, 0.5, 0.4, 0.02);
        return mob;
    }

    private LivingEntity livingBoss() {
        if (bossUuid == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(bossUuid);
        if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
            return living;
        }
        return null;
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
                LivingEntity boss = livingBoss();
                if (boss != null) {
                    suppressSpells(boss);
                    tickEnrage(boss);
                    tickJump(boss);
                    tickShoot(boss);
                    tickDash(boss);
                    tickStepOver(boss);
                    tickFreeKick(boss);
                    tickCelebrate(boss);
                    tickNetTrap(boss);
                    tickVoices(boss);
                }
                refreshHologram();
                refreshBossBar();
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void suppressSpells(LivingEntity boss) {
        if (boss instanceof Spellcaster caster) {
            try {
                caster.setSpell(Spellcaster.Spell.NONE);
            } catch (Throwable ignored) {
            }
        }
    }

    private void tickJump(LivingEntity boss) {
        jumpTicks++;
        if (jumping && boss.isOnGround()) {
            jumping = false;
            Location loc = boss.getLocation();
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.85f);
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 0.2, 0), 3, 0.4, 0.1, 0.4, 0.01);
            loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.4, 0), 18, 0.7, 0.2, 0.7, 0.04);
            loc.getWorld().createExplosion(loc, 1.55f, false, false, boss);
            launchNearby(loc, 5.5, 1.15, 0.55);
            spawnBurst(loc, Color.RED, Color.GREEN);
            playKeyed(loc, "ronaldo_siuu", 1.6f, 1.05f);
            return;
        }
        int every = Math.max(4, plugin.getConfig().getInt("ronaldo.jump-interval-seconds", 6));
        if (enraged(boss)) {
            every = Math.max(3, every - 2);
        }
        if (jumpTicks >= every && boss.isOnGround()) {
            jumpTicks = 0;
            jumping = true;
            Vector dir = new Vector(0, 1.05, 0);
            if (boss instanceof Mob mob && mob.getTarget() != null) {
                Vector to = mob.getTarget().getLocation().toVector().subtract(boss.getLocation().toVector());
                to.setY(0);
                if (to.lengthSquared() > 0.01) {
                    to.normalize().multiply(0.55);
                    dir.add(to);
                }
            }
            boss.setVelocity(dir);
            Location loc = boss.getLocation();
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.2f, 0.7f);
            loc.getWorld().spawnParticle(Particle.CLOUD, loc, 10, 0.3, 0.1, 0.3, 0.02);
        }
    }

    private void tickShoot(LivingEntity boss) {
        shootTicks++;
        int every = Math.max(2, plugin.getConfig().getInt("ronaldo.shoot-interval-seconds", 4));
        if (enraged(boss)) {
            every = Math.max(2, every - 1);
        }
        if (shootTicks < every) {
            return;
        }
        shootTicks = 0;
        Player target = currentTarget(boss);
        if (target == null) {
            return;
        }
        Vector dir = target.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector());
        if (dir.lengthSquared() < 0.04) {
            return;
        }
        dir.normalize();
        int shots = enraged(boss) ? 3 : 1;
        for (int i = 0; i < shots; i++) {
            Vector shot = dir.clone();
            if (shots > 1) {
                double yaw = Math.toRadians((i - 1) * 14.0);
                double x = shot.getX() * Math.cos(yaw) - shot.getZ() * Math.sin(yaw);
                double z = shot.getX() * Math.sin(yaw) + shot.getZ() * Math.cos(yaw);
                shot.setX(x).setZ(z);
            }
            SmallFireball ball = boss.launchProjectile(SmallFireball.class, shot.multiply(1.12));
            ball.getPersistentDataContainer().set(cloneKey, PersistentDataType.BYTE, (byte) 3);
            ball.setIsIncendiary(false);
            homeBall(ball, target);
        }
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.1f, 0.9f);
    }

    private void tickDash(LivingEntity boss) {
        dashTicks++;
        int every = enraged(boss) ? 6 : 8;
        if (dashTicks < every || jumping) {
            return;
        }
        Player target = currentTarget(boss);
        if (target == null) {
            return;
        }
        dashTicks = 0;
        Vector to = target.getLocation().toVector().subtract(boss.getLocation().toVector());
        to.setY(0);
        if (to.lengthSquared() < 4.0) {
            return;
        }
        to.normalize().multiply(1.35).setY(0.28);
        boss.setVelocity(to);
        Location loc = boss.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.8f);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 8, 0.3, 0.1, 0.3, 0.02);
    }

    private void tickCelebrate(LivingEntity boss) {
        celebrateTicks++;
        int every = Math.max(12, plugin.getConfig().getInt("ronaldo.goal-interval-seconds", 22));
        if (celebrateTicks < every || jumping || center == null) {
            return;
        }
        celebrateTicks = 0;
        Location goal = nearestGoal(boss.getLocation());
        Vector to = goal.toVector().subtract(boss.getLocation().toVector());
        to.setY(0.4);
        if (to.lengthSquared() > 1.0) {
            to.normalize().multiply(1.55);
        }
        boss.setVelocity(to);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            LivingEntity live = livingBoss();
            if (live == null) {
                return;
            }
            Location at = live.getLocation();
            at.getWorld().createExplosion(at, 1.7f, false, false, live);
            at.getWorld().strikeLightningEffect(at);
            launchNearby(at, 6.5, 1.25, 0.7);
            spawnBurst(at, Color.YELLOW, Color.RED);
            playKeyed(at, "ronaldo_siuu", 2.0f, 1.0f);
            double max = live.getMaxHealth();
            live.setHealth(Math.min(max, live.getHealth() + Math.max(40.0, max * 0.12)));
            shoutGoal();
        }, 14L);
    }

    private void tickStepOver(LivingEntity boss) {
        stepTicks++;
        if (stepTicks < 7 || jumping) {
            return;
        }
        Player target = currentTarget(boss);
        if (target == null) {
            return;
        }
        stepTicks = 0;
        Vector to = target.getLocation().toVector().subtract(boss.getLocation().toVector());
        to.setY(0);
        if (to.lengthSquared() < 2.0) {
            return;
        }
        to.normalize();
        Vector side = new Vector(-to.getZ(), 0.22, to.getX()).multiply(random.nextBoolean() ? 1.05 : -1.05);
        boss.setVelocity(side);
        Location loc = boss.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.4f);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 6, 0.25, 0.05, 0.25, 0.01);
    }

    private void tickFreeKick(LivingEntity boss) {
        freeKickTicks++;
        Location loc = boss.getLocation();
        if (chargingFreeKick) {
            chargingFreeKick = false;
            Player target = currentTarget(boss);
            if (target == null) {
                return;
            }
            Vector dir = target.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector());
            if (dir.lengthSquared() < 0.04) {
                return;
            }
            Snowball ball = boss.launchProjectile(Snowball.class, dir.normalize().multiply(1.65));
            ball.getPersistentDataContainer().set(cloneKey, PersistentDataType.BYTE, (byte) 4);
            try {
                ball.setItem(new ItemStack(Material.SLIME_BALL));
            } catch (Throwable ignored) {
            }
            homeBall(ball, target);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.4f);
            loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.3f, 0.7f);
            loc.getWorld().strikeLightningEffect(loc.clone().add(dir.clone().multiply(2)));
            return;
        }
        int every = enraged(boss) ? 12 : 16;
        if (freeKickTicks < every || jumping) {
            return;
        }
        freeKickTicks = 0;
        chargingFreeKick = true;
        loc.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(0, 1.2, 0), 18, 0.4, 0.5, 0.4, 0.05);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1.4, 0), 10, 0.3, 0.4, 0.3, 0.02);
        loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, 0.8f);
    }

    private void tickNetTrap(LivingEntity boss) {
        trapTicks++;
        if (trapTicks < 6 || center == null || center.getWorld() == null) {
            return;
        }
        trapTicks = 0;
        boolean hit = false;
        Location[] goals = {
                center.clone().add(0.0, 1.0, -17.0),
                center.clone().add(0.0, 1.0, 17.0)
        };
        for (Location goal : goals) {
            for (Player player : center.getWorld().getPlayers()) {
                if (!plugin.getBedrockService().canParticipate(player) || player.isDead()) {
                    continue;
                }
                if (player.getLocation().distanceSquared(goal) > 16.0) {
                    continue;
                }
                Vector out = center.toVector().subtract(player.getLocation().toVector());
                out.setY(0);
                if (out.lengthSquared() < 0.01) {
                    out = new Vector(0, 0, 1);
                }
                player.setVelocity(out.normalize().multiply(1.15).setY(0.95));
                player.sendMessage(ColorUtil.color("&e┠ &cСетки ворот! &fCR7 тебя вытащил."));
                hit = true;
            }
        }
        if (hit) {
            playKeyed(boss.getLocation(), "ronaldo_siuu", 1.4f, 1.15f);
        }
    }

    private void shoutGoal() {
        Bukkit.broadcastMessage(ColorUtil.color("&e┼ &e&lGOOOOL &f— SIUUUUU"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getBedrockService().canParticipate(player)) {
                continue;
            }
            try {
                player.sendTitle(ColorUtil.color("&e&lGOOOOL"), ColorUtil.color("&fSIUUUUU"), 5, 28, 8);
            } catch (Throwable ignored) {
            }
        }
    }

    private void dropTrophy(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        ItemStack ball = new ItemStack(Material.SLIME_BALL, 1);
        var meta = ball.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color("&e&lМяч CR7"));
            ball.setItemMeta(meta);
        }
        Item dropped = loc.getWorld().dropItemNaturally(loc, ball);
        dropped.setPickupDelay(10);
        dropped.setCustomName(ColorUtil.color("&e&lМяч CR7"));
        dropped.setCustomNameVisible(true);
    }

    private void tickEnrage(LivingEntity boss) {
        if (!enraged(boss)) {
            return;
        }
        boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
    }

    public void handleMeleeHit(Player player) {
        if (!active || player == null || livingBoss() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastKickMs < 1600L) {
            return;
        }
        lastKickMs = now;
        LivingEntity boss = livingBoss();
        Vector kick = player.getLocation().toVector().subtract(boss.getLocation().toVector());
        if (kick.lengthSquared() < 0.01) {
            kick = player.getLocation().getDirection();
        }
        kick.setY(0);
        if (kick.lengthSquared() > 0.01) {
            kick.normalize();
        }
        kick.multiply(1.35).setY(0.85);
        player.setVelocity(kick);
        Location loc = player.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.4f, 0.65f);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 10, 0.2, 0.2, 0.2, 0.03);
        player.sendMessage(ColorUtil.color("&e┠ &fCR7 тебя &eвыбил с поля!"));
    }

    private boolean enraged(LivingEntity boss) {
        return boss.getHealth() / Math.max(1.0, boss.getMaxHealth()) <= 0.4;
    }

    private Player currentTarget(LivingEntity boss) {
        if (boss instanceof Mob mob && mob.getTarget() instanceof Player player && !player.isDead()) {
            return player;
        }
        return null;
    }

    private Location nearestGoal(Location from) {
        Location a = center.clone().add(0.0, 1.0, -17.0);
        Location b = center.clone().add(0.0, 1.0, 17.0);
        return from.distanceSquared(a) <= from.distanceSquared(b) ? a : b;
    }

    private void launchNearby(Location loc, double radius, double power, double up) {
        if (loc.getWorld() == null) {
            return;
        }
        double r2 = radius * radius;
        for (Player player : loc.getWorld().getPlayers()) {
            if (!plugin.getBedrockService().canParticipate(player) || player.isDead()) {
                continue;
            }
            if (player.getLocation().distanceSquared(loc) > r2) {
                continue;
            }
            Vector kick = player.getLocation().toVector().subtract(loc.toVector());
            kick.setY(0);
            if (kick.lengthSquared() < 0.01) {
                kick = new Vector(random.nextGaussian(), 0, random.nextGaussian());
            }
            if (kick.lengthSquared() > 0.01) {
                kick.normalize();
            }
            player.setVelocity(kick.multiply(power).setY(up));
        }
    }

    private void spawnBurst(Location loc, Color a, Color b) {
        if (loc.getWorld() == null) {
            return;
        }
        Firework firework = loc.getWorld().spawn(loc.clone().add(0, 1, 0), Firework.class, fw -> {
            fw.setPersistent(false);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(a, b, Color.WHITE)
                    .with(FireworkEffect.Type.BALL)
                    .flicker(true)
                    .trail(true)
                    .build());
            meta.setPower(0);
            fw.setFireworkMeta(meta);
        });
        Bukkit.getScheduler().runTaskLater(plugin, firework::detonate, 2L);
    }

    private void homeBall(Projectile ball, Player target) {
        new BukkitRunnable() {
            int n = 0;

            @Override
            public void run() {
                if (!active || !ball.isValid() || ball.isDead() || target.isDead() || n++ > 35) {
                    cancel();
                    return;
                }
                Vector to = target.getEyeLocation().toVector().subtract(ball.getLocation().toVector());
                if (to.lengthSquared() < 0.04) {
                    return;
                }
                ball.setVelocity(to.normalize().multiply(0.92));
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private void tickVoices(LivingEntity boss) {
        siuuTicks++;
        talkTicks++;
        int siuuEvery = Math.max(6, plugin.getConfig().getInt("ronaldo.siuu-interval-seconds", 10));
        int talkEvery = Math.max(8, plugin.getConfig().getInt("ronaldo.talk-interval-seconds", 14));
        Location loc = boss.getLocation();
        if (siuuTicks >= siuuEvery) {
            siuuTicks = 0;
            playKeyed(loc, "ronaldo_siuu", 1.8f, 1.0f);
        }
        if (talkTicks >= talkEvery) {
            talkTicks = 0;
            playKeyed(loc, "ronaldo_talk", 1.6f, 1.0f);
        }
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
        return new Location(center.getWorld(), center.getX(), center.getY() + 3.2, center.getZ() - 19.5);
    }

    private String[] hologramLines() {
        String warp = plugin.getConfig().getString("ronaldo.warp-name", "ronaldo");
        return new String[]{
                ColorUtil.color("&e&lSIUU  Роналду"),
                ColorUtil.color("&7Открытие: &#E0C912" + openedAtClock),
                ColorUtil.color("&7До конца: &#E0C912" + ColorUtil.formatDuration(getSecondsRemaining())),
                ColorUtil.color("&7Варп: &#E0C912/warp " + warp)
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
        int total = Math.max(1, plugin.getConfig().getInt("ronaldo.duration-minutes", 8) * 60);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, left / (double) total)));
        LivingEntity boss = livingBoss();
        String mood = (boss != null && enraged(boss)) ? "&c&lCR7 ЗЛИТСЯ" : "&e&lCR7";
        bossBar.setColor((boss != null && enraged(boss)) ? BarColor.RED : BarColor.YELLOW);
        bossBar.setTitle(ColorUtil.color(mood + " &8| &fдо конца &#E0C912"
                + ColorUtil.formatDuration(left)));
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
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fCR7: ножницы, штрафной, мяч, рывок, сетка ворот."));
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

    private void playKeyed(Location loc, String key, float volume, float pitch) {
        playKeyed(loc, key, volume, pitch, false);
    }

    private void playKeyed(Location loc, String key, float volume, float pitch, boolean force) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        if (!force && livingBoss() == null) {
            return;
        }
        loc.getWorld().playSound(loc, key, volume, pitch);
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= 48 * 48) {
                player.playSound(player.getLocation(), key, volume, pitch);
            }
        }
    }

    private void stopCustomSounds() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.stopSound("ronaldo_siuu");
                player.stopSound("ronaldo_talk");
            } catch (Throwable ignored) {
            }
        }
    }

    public boolean isRonaldoProjectile(Entity entity) {
        if (entity == null) {
            return false;
        }
        Byte tag = entity.getPersistentDataContainer().get(cloneKey, PersistentDataType.BYTE);
        return tag != null && (tag == (byte) 3 || tag == (byte) 4);
    }

    public void handleProjectileHit(Entity projectile, Location hit) {
        if (projectile == null || hit == null || hit.getWorld() == null) {
            return;
        }
        Byte tag = projectile.getPersistentDataContainer().get(cloneKey, PersistentDataType.BYTE);
        projectile.remove();
        boolean ball = tag != null && tag == (byte) 4;
        hit.getWorld().createExplosion(hit, ball ? 1.45f : 1.15f, false, false, projectile);
        hit.getWorld().playSound(hit, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, ball ? 0.9f : 1.15f);
        hit.getWorld().spawnParticle(Particle.EXPLOSION, hit, ball ? 4 : 2, 0.25, 0.25, 0.25, 0.01);
        if (ball) {
            launchNearby(hit, 4.5, 1.2, 0.55);
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
