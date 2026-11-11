package ru.geyserevent.fishing;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.boss.BossAbilityController;
import ru.geyserevent.excavation.BrushLootManager;
import ru.geyserevent.excavation.BrushTier;
import ru.geyserevent.schematic.RiftSchematicManager;
import ru.geyserevent.util.ColorUtil;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ивент «Рыбалка»: пруд + рыбак. Удочки трёх редкостей по PDC.
 * Ванильная удочка и ванильный житель без метки не меняются.
 */
public final class FishingEventManager {

    public static final String REGION_ID = "geyserevent_fishing";
    public static final String SCHEMATIC_FILE = "fishing_pond.schem.yml";
    public static final int ARENA_RADIUS = 14;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final GeyserEventPlugin plugin;
    private final RiftSchematicManager schematicManager;
    private final NamespacedKey npcKey;
    private final List<UUID> hologramIds = new ArrayList<>();

    private boolean active;
    private Location center;
    private long eventEndEpochSeconds;
    private String openedAtClock = "--:--";
    private BossBar bossBar;
    private BukkitRunnable tickTask;
    private UUID npcUuid;
    private String activeWarpName;
    private final Map<UUID, Long> rodTakeCooldown = new ConcurrentHashMap<>();

    public FishingEventManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.schematicManager = new RiftSchematicManager(plugin, SCHEMATIC_FILE);
        this.npcKey = new NamespacedKey(plugin, "fishing_npc");
    }

    public boolean startEvent(Location targetCenter, Integer durationSecondsOverride) {
        if (targetCenter == null || targetCenter.getWorld() == null) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("fishing.enabled", true)) {
            plugin.getLogger().warning("Ивент «Рыбалка» выключен (fishing.enabled: false).");
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
        if (plugin.getRonaldoEventManager() != null && plugin.getRonaldoEventManager().isActive()) {
            plugin.getRonaldoEventManager().stopEvent();
        }
        stopEvent(false);

        targetCenter.getChunk().load(true);
        this.active = true;
        this.center = snapToSurface(targetCenter);
        this.npcUuid = null;
        this.eventEndEpochSeconds = System.currentTimeMillis() / 1000L
                + ((durationSecondsOverride != null && durationSecondsOverride > 0)
                ? durationSecondsOverride
                : Math.max(60, plugin.getConfig().getInt("fishing.duration-minutes", 8) * 60));
        this.openedAtClock = LocalTime.now().format(CLOCK);

        try {
            plugin.saveResource("schematics/" + SCHEMATIC_FILE, true);
            schematicManager.loadSchematicFile();
        } catch (IllegalArgumentException ignored) {
        }
        setChunksForceLoaded(this.center, true);
        schematicManager.pasteSchematic(this.center, ARENA_RADIUS, 7);
        schematicManager.persistRestoreState(restoreSnapshotFile());
        spawnNpc();
        spawnHologram();
        createBossBar();
        deleteEventWarp();
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
        removeNpc();
        removeHologram();
        plugin.getBrushLootManager().removeAllFishingRodsFromOnlinePlayers();
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
        this.npcUuid = null;
        this.activeWarpName = null;
        this.rodTakeCooldown.clear();
    }

    public boolean isActive() {
        return active;
    }

    public Location getCenterLocation() {
        return center == null ? null : center.clone();
    }

    public int getSecondsRemaining() {
        if (!active) {
            return 0;
        }
        return (int) Math.max(0L, eventEndEpochSeconds - System.currentTimeMillis() / 1000L);
    }

    public boolean isFisherman(Entity entity) {
        if (entity == null) {
            return false;
        }
        return entity.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }

    public boolean handleNpcClick(Player player) {
        if (!active || player == null || !plugin.getBedrockService().canParticipate(player) && !player.isOp()) {
            if (player != null && !plugin.getBedrockService().canParticipate(player) && !player.isOp()) {
                player.sendMessage(plugin.getPluginConfig().getMsgOnlyBedrock());
            }
            return false;
        }
        BrushLootManager loot = plugin.getBrushLootManager();
        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(1, plugin.getConfig().getInt("fishing.rod-cooldown-seconds", 4)) * 1000L;
        Long last = rodTakeCooldown.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            long left = (cooldownMs - (now - last) + 999L) / 1000L;
            player.sendMessage(ColorUtil.color("&e┠ &cПодождите ещё " + left + " сек. перед следующей удочкой."));
            return true;
        }
        int max = 1;
        if (loot.countPlayerFishingRods(player) >= max) {
            player.sendMessage(ColorUtil.color("&e┠ &cСначала используйте удочку, которая уже есть."));
            return true;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ColorUtil.color("&e┠ &cОсвободите место в инвентаре!"));
            return true;
        }
        BrushTier tier = loot.rollRandomFishingRodTier();
        ItemStack rod = loot.createFishingRod(tier);
        player.getInventory().addItem(rod);
        rodTakeCooldown.put(player.getUniqueId(), now);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.15f);
        player.sendMessage(ColorUtil.color("&e┠ &fРыбак выдал: " + loot.getFishingRodTitle(tier)));
        return true;
    }

    public boolean handleFishCatch(PlayerFishEvent event) {
        if (!active || event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return false;
        }
        Player player = event.getPlayer();
        ItemStack rod = player.getInventory().getItemInMainHand();
        BrushLootManager loot = plugin.getBrushLootManager();
        BrushTier tier = loot.getFishingRodTier(rod);
        if (tier == null) {
            rod = player.getInventory().getItemInOffHand();
            tier = loot.getFishingRodTier(rod);
        }
        if (tier == null) {
            return false;
        }
        if (!(event.getCaught() instanceof Item caught)) {
            return false;
        }
        ItemStack prize = loot.rollLootForFishing(tier);
        if (prize == null || prize.getType().isAir()) {
            prize = new ItemStack(Material.COD, 1);
        }
        caught.setItemStack(prize);
        loot.consumeFishingRodUse(player, rod);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.35f, 1.6f);
        return true;
    }

    public void cleanupOrphanSchematic() {
        File snapshot = restoreSnapshotFile();
        if (snapshot.exists()) {
            plugin.getLogger().info("Откат схемы «Рыбалка», оставшейся после рестарта...");
            schematicManager.restorePersistedState(snapshot);
        }
        deleteEventWarp();
    }

    private void spawnNpc() {
        if (center == null || center.getWorld() == null) {
            return;
        }
        Location loc = schematicManager.getBossSpawnLocation();
        if (loc == null) {
            loc = center.clone().add(0.5, 1.0, 9.5);
        }
        loc.setYaw(0f);
        Villager npc = (Villager) center.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        npc.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
        npc.setAdult();
        npc.setProfession(Villager.Profession.FISHERMAN);
        npc.setVillagerType(Villager.Type.PLAINS);
        npc.setRecipes(java.util.Collections.emptyList());
        npc.setAI(false);
        npc.setAware(false);
        npc.setInvulnerable(true);
        npc.setCollidable(false);
        npc.setRemoveWhenFarAway(false);
        npc.setPersistent(false);
        npc.setCanPickupItems(false);
        npc.setCustomNameVisible(true);
        npc.setCustomName(ColorUtil.color("&b&lРыбак"));
        npc.setGlowing(false);
        BossAbilityController.applyEntityScale(npc, 1.15);
        EntityEquipment equip = npc.getEquipment();
        if (equip != null) {
            equip.setItemInMainHand(new ItemStack(Material.FISHING_ROD, 1));
            equip.setItemInMainHandDropChance(0.0f);
        }
        this.npcUuid = npc.getUniqueId();
    }

    private void removeNpc() {
        if (npcUuid != null) {
            Entity entity = Bukkit.getEntity(npcUuid);
            if (entity != null) {
                entity.remove();
            }
            npcUuid = null;
        }
    }

    private void startTickLoop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) {
                    cancel();
                    return;
                }
                if (getSecondsRemaining() <= 0) {
                    Bukkit.broadcastMessage(ColorUtil.color("&e┼ &bРыбалка окончена."));
                    stopEvent();
                    return;
                }
                refreshHologram();
                refreshBossBar();
                stripFarPlayers();
                if (bossBar != null) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (plugin.getBedrockService().canParticipate(player) || player.isOp()) {
                            bossBar.addPlayer(player);
                        } else {
                            bossBar.removePlayer(player);
                        }
                    }
                }
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void stripFarPlayers() {
        if (center == null) {
            return;
        }
        double max = (ARENA_RADIUS + 8) * (ARENA_RADIUS + 8);
        BrushLootManager loot = plugin.getBrushLootManager();
        for (Player player : center.getWorld().getPlayers()) {
            if (loot.countPlayerFishingRods(player) <= 0) {
                continue;
            }
            if (player.getLocation().distanceSquared(center) > max) {
                if (loot.removeAllFishingRods(player)) {
                    player.sendMessage(ColorUtil.color("&e┠ &cВы вышли из зоны рыбалки — удочки пропали."));
                }
            }
        }
    }

    private void spawnHologram() {
        removeHologram();
        if (center == null || center.getWorld() == null) {
            return;
        }
        Location base = hologramLocation();
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
        String coords = center == null ? "—"
                : center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ();
        return new String[]{
                ColorUtil.color("&b&lРыбалка"),
                ColorUtil.color("&7Возьмите удочку у рыбака"),
                ColorUtil.color("&7До конца: &#E0C912" + ColorUtil.formatDuration(getSecondsRemaining())),
                ColorUtil.color("&7Координаты: &#E0C912" + coords)
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

    private void createBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
        bossBar = Bukkit.createBossBar(ColorUtil.color("&b&lРыбалка"), BarColor.BLUE, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        refreshBossBar();
    }

    private void refreshBossBar() {
        if (bossBar == null) {
            return;
        }
        int left = getSecondsRemaining();
        int total = Math.max(1, plugin.getConfig().getInt("fishing.duration-minutes", 8) * 60);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, left / (double) total)));
        bossBar.setTitle(ColorUtil.color("&b&lРыбалка &7· &#E0C912" + ColorUtil.formatDuration(left)));
    }

    private void broadcastSpawn() {
        if (center == null) {
            return;
        }
        Bukkit.broadcastMessage(ColorUtil.color("&e┼ &bРыбалка на пруду!"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fКоординаты: &#E0C912"
                + center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ()));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fЛокатор: &#34D711/event gps"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &7Возьмите удочку у рыбака в будке."));
    }

    private Location snapToSurface(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return loc;
        }
        World world = loc.getWorld();
        int y = world.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Location snapped = loc.clone();
        snapped.setY(y);
        return snapped;
    }

    private File restoreSnapshotFile() {
        return new File(plugin.getDataFolder(), "fishing-restore.yml");
    }

    private void createEventWarp() {
        if (center == null) {
            return;
        }
        String name = warpName();
        this.activeWarpName = name;
        Location warpLoc = snapToSurface(center.clone());
        warpLoc.setY(warpLoc.getY() + 1.0);
        essentialsWarp(true, name, warpLoc);
    }

    private void deleteEventWarp() {
        String name = activeWarpName != null ? activeWarpName : warpName();
        essentialsWarp(false, name, null);
        this.activeWarpName = null;
    }

    private String warpName() {
        String name = plugin.getConfig().getString("fishing.warp-name", "fishing");
        return (name == null || name.isBlank()) ? "fishing" : name.trim();
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
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                Chunk chunk = world.getChunkAt(cx + dx, cz + dz);
                chunk.setForceLoaded(loaded);
                if (loaded) {
                    chunk.load(true);
                }
            }
        }
    }
}
