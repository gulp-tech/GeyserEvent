package ru.geyserevent.skibidi;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.boss.BossAbilityController;
import ru.geyserevent.schematic.RiftSchematicManager;
import ru.geyserevent.util.ColorUtil;

import java.util.Random;
import java.util.UUID;

/**
 * Ивент «Шкибиди Туалет»: схема-туалет + 3D-модель (ravager → skibidi),
 * каждые 3 секунды из рта вылетает 2–3 ресурса. Длится 3 минуты. Боя нет.
 */
public final class SkibidiToiletManager {

    public static final String REGION_ID = "geyserevent_skibidi";
    public static final String SCHEMATIC_FILE = "skibidi_toilet.schem.yml";
    public static final int ARENA_RADIUS = 20;

    private static final Material[] LOOT = {
            Material.IRON_INGOT, Material.GOLD_INGOT, Material.COPPER_INGOT,
            Material.COAL, Material.REDSTONE, Material.LAPIS_LAZULI,
            Material.DIAMOND, Material.EMERALD, Material.QUARTZ,
            Material.RAW_IRON, Material.RAW_GOLD, Material.AMETHYST_SHARD,
            Material.GOLD_NUGGET, Material.IRON_NUGGET, Material.ECHO_SHARD
    };

    private final GeyserEventPlugin plugin;
    private final RiftSchematicManager schematicManager;
    private final NamespacedKey toiletKey;
    private final Random random = new Random();

    private boolean active;
    private Location center;
    private Location toiletHome;
    private UUID toiletUuid;
    private long eventEndEpochSeconds;
    private int spitCooldownTicks;
    private BossBar bossBar;
    private BukkitRunnable tickTask;

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
        stopEvent();

        targetCenter.getChunk().load(true);
        this.active = true;
        this.center = snapToSurface(targetCenter);
        this.spitCooldownTicks = 0;

        int duration = (durationSecondsOverride != null && durationSecondsOverride > 0)
                ? durationSecondsOverride
                : Math.max(30, plugin.getConfig().getInt("skibidi.duration-seconds", 180));
        this.eventEndEpochSeconds = System.currentTimeMillis() / 1000L + duration;

        setChunksForceLoaded(this.center, true);
        this.toiletHome = schematicManager.pasteSchematic(this.center, ARENA_RADIUS + 2, 36);
        if (this.toiletHome == null) {
            this.toiletHome = this.center.clone().add(0.5, 8, 0.5);
        }

        LivingEntity toilet = spawnToilet(this.toiletHome);
        this.toiletUuid = toilet != null ? toilet.getUniqueId() : null;

        createBossBar();
        broadcastSpawn();

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
        if (toiletUuid != null) {
            Entity entity = Bukkit.getEntity(toiletUuid);
            if (entity != null) {
                entity.remove();
            }
            toiletUuid = null;
        }
        schematicManager.restoreSchematic();
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
    }

    public boolean isActive() {
        return active;
    }

    public Location getCenterLocation() {
        return center;
    }

    public Location getToiletLocation() {
        Entity entity = toiletUuid == null ? null : Bukkit.getEntity(toiletUuid);
        return entity != null ? entity.getLocation() : toiletHome;
    }

    public int getSecondsRemaining() {
        long left = eventEndEpochSeconds - System.currentTimeMillis() / 1000L;
        return (int) Math.max(0L, left);
    }

    public boolean isToilet(Entity entity) {
        if (!active || entity == null || toiletUuid == null) {
            return false;
        }
        return toiletUuid.equals(entity.getUniqueId());
    }

    public boolean isProtectedBlock(Location loc) {
        return active && schematicManager.isProtectedBlock(loc);
    }

    private LivingEntity spawnToilet(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        loc.getChunk().load(true);
        Entity spawned = world.spawnEntity(loc, EntityType.RAVAGER);
        if (!(spawned instanceof Ravager ravager)) {
            spawned.remove();
            return null;
        }
        ravager.getPersistentDataContainer().set(toiletKey, PersistentDataType.BYTE, (byte) 1);
        ravager.setRemoveWhenFarAway(false);
        ravager.setPersistent(false);
        ravager.setCanPickupItems(false);
        ravager.setCustomNameVisible(true);
        ravager.setCustomName(ColorUtil.color("&f&lШкибиди Туалет"));
        ravager.setSilent(true);
        ravager.setGravity(true);
        ravager.setAI(false);
        ravager.setAware(false);
        ravager.setCollidable(false);
        ravager.setInvulnerable(true);
        if (ravager instanceof Mob mob) {
            mob.setTarget(null);
        }
        BossAbilityController.applyEntityScale(ravager, plugin.getConfig().getDouble("skibidi.scale", 1.25));
        BossAbilityController.setEntityAttribute(ravager, "MAX_HEALTH", "generic.max_health", 2048.0);
        ravager.setHealth(ravager.getMaxHealth());
        world.playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 0.7f, 0.6f);
        world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 2, 0), 24, 0.6, 0.5, 0.6, 0.04);
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
                    Bukkit.broadcastMessage(ColorUtil.color("&e┼ &fШкибиди Туалет &cуехал. Ресурсы кончились."));
                    stopEvent();
                    return;
                }
                keepToiletHome();
                spitCooldownTicks++;
                int interval = Math.max(20, plugin.getConfig().getInt("skibidi.spit-interval-ticks", 60));
                if (spitCooldownTicks >= interval) {
                    spitCooldownTicks = 0;
                    spitResources();
                }
                refreshBossBar();
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void keepToiletHome() {
        if (toiletUuid == null || toiletHome == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(toiletUuid);
        if (entity == null || !entity.isValid()) {
            LivingEntity spawned = spawnToilet(toiletHome);
            toiletUuid = spawned != null ? spawned.getUniqueId() : null;
            return;
        }
        if (entity.getLocation().distanceSquared(toiletHome) > 4.0) {
            entity.teleport(toiletHome);
        }
    }

    private void spitResources() {
        Entity entity = toiletUuid == null ? null : Bukkit.getEntity(toiletUuid);
        if (!(entity instanceof LivingEntity living) || living.getWorld() == null) {
            return;
        }
        Vector look = living.getLocation().getDirection();
        if (look.lengthSquared() < 0.01) {
            look = new Vector(0, 0, -1);
        }
        look.normalize();
        Location mouth = living.getEyeLocation().add(look.clone().multiply(1.6)).add(0, 0.35, 0);
        int count = 2 + random.nextInt(2);
        World world = living.getWorld();
        for (int i = 0; i < count; i++) {
            Material mat = LOOT[random.nextInt(LOOT.length)];
            int amount = 1 + random.nextInt(3);
            Item dropped = world.dropItem(mouth, new ItemStack(mat, amount));
            Vector vel = look.clone().multiply(0.55 + random.nextDouble() * 0.35);
            vel.add(new Vector(
                    (random.nextDouble() - 0.5) * 0.45,
                    0.28 + random.nextDouble() * 0.22,
                    (random.nextDouble() - 0.5) * 0.45
            ));
            dropped.setVelocity(vel);
            dropped.setPickupDelay(8);
        }
        world.playSound(mouth, Sound.ENTITY_LLAMA_SPIT, 1.1f, 0.7f);
        world.playSound(mouth, Sound.ENTITY_PLAYER_BURP, 0.7f, 0.8f);
        world.spawnParticle(Particle.CLOUD, mouth, 10, 0.25, 0.15, 0.25, 0.03);
        world.spawnParticle(Particle.CRIT, mouth, 6, 0.2, 0.15, 0.2, 0.03);
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
        int total = Math.max(1, plugin.getConfig().getInt("skibidi.duration-seconds", 180));
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, left / (double) total)));
        bossBar.setTitle(ColorUtil.color("&f&lШкибиди Туалет &8| &eресурсы изо рта &8| &f"
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
        Bukkit.broadcastMessage(ColorUtil.color("&e┼ &f&lШкибиди Туалет &fприехал на карту!"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fКаждые &#E0C9123 сек. &fиз рта летят &#E0C9122–3 ресурса"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fДлительность: &#E0C9123 минуты&f. Боя нет — подбирай лут."));
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
}
