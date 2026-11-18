package ru.geyserevent.newyear;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.schematic.RiftSchematicManager;
import ru.geyserevent.util.ColorUtil;
import ru.geyserevent.util.LootStacks;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Новогодний ивент: карета Санты, 9 оленей, подарки-шалкеры по редкости.
 * Ванильный шалкер без PDC не меняется.
 */
public final class NewYearEventManager {

    public static final String REGION_ID = "geyserevent_newyear";
    public static final String SCHEMATIC_FILE = "santa_sleigh.schem.yml";
    public static final int ARENA_RADIUS = 22;

    private final GeyserEventPlugin plugin;
    private final RiftSchematicManager schematicManager;
    private final NamespacedKey giftKey;
    private final NamespacedKey rarityKey;
    private final NamespacedKey giftIdKey;
    private final NamespacedKey santaKey;
    private final List<UUID> hologramIds = new ArrayList<>();
    private final Set<String> spentGiftIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> exchangeBusy = ConcurrentHashMap.newKeySet();

    private boolean active;
    private Location center;
    private long eventEndEpochSeconds;
    private BossBar bossBar;
    private BukkitRunnable tickTask;
    private UUID santaUuid;
    private int scatterTick;

    public NewYearEventManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.schematicManager = new RiftSchematicManager(plugin, SCHEMATIC_FILE);
        this.giftKey = new NamespacedKey(plugin, "newyear_gift");
        this.rarityKey = new NamespacedKey(plugin, "newyear_rarity");
        this.giftIdKey = new NamespacedKey(plugin, "newyear_gift_id");
        this.santaKey = new NamespacedKey(plugin, "newyear_santa");
        loadSpentIds();
    }

    public static final class GiftExchangeHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public boolean startEvent(Location targetCenter, Integer durationSecondsOverride) {
        if (targetCenter == null || targetCenter.getWorld() == null) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("newyear.enabled", true)) {
            plugin.getLogger().warning("Ивент «Новый год» выключен (newyear.enabled: false).");
            return false;
        }
        if (plugin.getRiftManager() != null && plugin.getRiftManager().isActive()) {
            plugin.getRiftManager().stopEvent(false);
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
        if (plugin.getFishingEventManager() != null && plugin.getFishingEventManager().isActive()) {
            plugin.getFishingEventManager().stopEvent();
        }
        stopEvent(false);

        targetCenter.getChunk().load(true);
        this.active = true;
        this.center = snapToSurface(targetCenter);
        this.santaUuid = null;
        this.scatterTick = 0;
        this.eventEndEpochSeconds = System.currentTimeMillis() / 1000L
                + ((durationSecondsOverride != null && durationSecondsOverride > 0)
                ? durationSecondsOverride
                : Math.max(60, plugin.getConfig().getInt("newyear.duration-minutes", 10) * 60));

        try {
            plugin.saveResource("schematics/" + SCHEMATIC_FILE, true);
            schematicManager.loadSchematicFile();
        } catch (IllegalArgumentException ignored) {
        }
        setChunksForceLoaded(this.center, true);
        schematicManager.pasteSchematic(this.center, ARENA_RADIUS, 10);
        schematicManager.persistRestoreState(restoreSnapshotFile());
        spawnSanta();
        spawnHologram();
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
        stopEvent(true);
    }

    private void stopEvent(boolean unused) {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        removeSanta();
        removeHologram();
        clearGiftDrops();
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
        this.santaUuid = null;
    }

    public void cleanupOrphanSchematic() {
        File snapshot = restoreSnapshotFile();
        if (snapshot.exists()) {
            plugin.getLogger().info("Откат схемы «Новый год», оставшейся после рестарта...");
            schematicManager.restorePersistedState(snapshot);
        }
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
        return (int) Math.max(0, eventEndEpochSeconds - System.currentTimeMillis() / 1000L);
    }

    public boolean isGift(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        Byte mark = stack.getItemMeta().getPersistentDataContainer().get(giftKey, PersistentDataType.BYTE);
        return mark != null && mark == (byte) 1;
    }

    public boolean isHoldingGift(Player player) {
        if (player == null) {
            return false;
        }
        return isGift(player.getInventory().getItemInMainHand())
                || isGift(player.getInventory().getItemInOffHand());
    }

    public boolean hasAnyGift(Player player) {
        if (player == null) {
            return false;
        }
        PlayerInventory inv = player.getInventory();
        for (ItemStack stack : inv.getContents()) {
            if (isGift(stack)) {
                return true;
            }
        }
        return isGift(player.getItemOnCursor());
    }

    public String giftRarity(ItemStack stack) {
        if (!isGift(stack)) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .getOrDefault(rarityKey, PersistentDataType.STRING, "common");
    }

    public boolean isSpentGift(ItemStack stack) {
        String id = giftId(stack);
        return id != null && spentGiftIds.contains(id);
    }

    private String giftId(ItemStack stack) {
        if (!isGift(stack)) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(giftIdKey, PersistentDataType.STRING);
    }

    public void sanitizePlayer(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isSpentGift(stack)) {
                inv.setItem(i, null);
            } else {
                stampGiftId(stack);
            }
        }
        if (isSpentGift(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        } else {
            stampGiftId(player.getItemOnCursor());
        }
    }

    public void stampGiftId(ItemStack stack) {
        if (!isGift(stack) || giftId(stack) != null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(giftIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        stack.setItemMeta(meta);
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
                    Bukkit.broadcastMessage(ColorUtil.color("&e┼ &cКарета Санты улетела."));
                    stopEvent();
                    return;
                }
                scatterTick++;
                if (scatterTick >= Math.max(3, plugin.getConfig().getInt("newyear.gift-interval-seconds", 4))) {
                    scatterTick = 0;
                    scatterGifts();
                }
                if (scatterTick % 2 == 0) {
                    snowBurst();
                }
                refreshHologram();
                refreshBossBar();
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

    private void scatterGifts() {
        if (center == null || center.getWorld() == null) {
            return;
        }
        if (countGroundGifts() >= 12) {
            return;
        }
        Location mouth = sackLocation();
        World world = mouth.getWorld();
        world.playSound(mouth, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.3f);
        world.playSound(mouth, Sound.ENTITY_SNOWBALL_THROW, 0.45f, 0.7f);
        int n = 1 + ThreadLocalRandom.current().nextInt(2);
        for (int i = 0; i < n; i++) {
            String rarity = rollRarity();
            ItemStack gift = createGift(rarity);
            Item dropped = world.dropItem(mouth, gift);
            dropped.setPickupDelay(10);
            dropped.setGlowing(true);
            dropped.setCustomName(gift.getItemMeta() != null ? gift.getItemMeta().getDisplayName() : null);
            dropped.setCustomNameVisible(true);
            dropped.setVelocity(new Vector(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.7,
                    0.38 + ThreadLocalRandom.current().nextDouble() * 0.22,
                    -0.15 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.55
            ));
        }
    }

    private Location sackLocation() {
        if (center == null) {
            return null;
        }
        return center.clone().add(0.5, 3.6, 16.5);
    }

    private int countGroundGifts() {
        if (center == null || center.getWorld() == null) {
            return 0;
        }
        int n = 0;
        for (Entity entity : center.getWorld().getNearbyEntities(center, ARENA_RADIUS + 6, 8, ARENA_RADIUS + 6)) {
            if (entity instanceof Item item && isGift(item.getItemStack())) {
                n++;
            }
        }
        return n;
    }

    private void snowBurst() {
        if (center == null || center.getWorld() == null) {
            return;
        }
        Location loc = center.clone().add(0, 4.5, 8);
        center.getWorld().spawnParticle(Particle.CLOUD, loc, 6, 3.5, 1.2, 6.0, 0.01);
    }

    private String rollRarity() {
        int r = ThreadLocalRandom.current().nextInt(100);
        if (r < 40) {
            return "common";
        }
        if (r < 70) {
            return "rare";
        }
        if (r < 90) {
            return "epic";
        }
        return "legendary";
    }

    private ItemStack createGift(String rarity) {
        Material box = switch (rarity) {
            case "rare" -> Material.LIGHT_BLUE_SHULKER_BOX;
            case "epic" -> Material.MAGENTA_SHULKER_BOX;
            case "legendary" -> Material.RED_SHULKER_BOX;
            default -> Material.WHITE_SHULKER_BOX;
        };
        ItemStack stack = new ItemStack(box, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(switch (rarity) {
                case "rare" -> "&9&lРедкий подарок";
                case "epic" -> "&d&lЭпический подарок";
                case "legendary" -> "&6&lЛегендарный подарок";
                default -> "&f&lОбычный подарок";
            }));
            meta.setLore(List.of(
                    ColorUtil.color("&7Обмен: &#E0C912/gifts"),
                    ColorUtil.color("&8Не ставится и не открывается")
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            meta.getPersistentDataContainer().set(giftKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, rarity);
            String id = UUID.randomUUID().toString();
            meta.getPersistentDataContainer().set(giftIdKey, PersistentDataType.STRING, id);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack rollLoot(String rarity) {
        List<PluginConfig.AlienLootItem> pool = plugin.getPluginConfig().getNewyearLoot(rarity);
        if (pool == null || pool.isEmpty()) {
            pool = plugin.getPluginConfig().getNewyearLoot("common");
        }
        if (pool == null || pool.isEmpty()) {
            return new ItemStack(Material.COOKIE, 4);
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        PluginConfig.AlienLootItem item = pool.get(rng.nextInt(pool.size()));
        for (int i = 0; i < 6; i++) {
            PluginConfig.AlienLootItem cand = pool.get(rng.nextInt(pool.size()));
            if (rng.nextDouble() * 100.0 <= Math.max(8.0, cand.chancePercent())) {
                item = cand;
                break;
            }
        }
        int min = item.minAmount();
        int max = Math.max(min, item.maxAmount());
        int amount = min + (max > min ? rng.nextInt(max - min + 1) : 0);
        return LootStacks.fromAlien(item, Math.max(1, amount));
    }

    private String rarityLabel(String rarity) {
        return switch (rarity) {
            case "rare" -> "&9Редкий";
            case "epic" -> "&dЭпический";
            case "legendary" -> "&6Легендарный";
            default -> "&fОбычный";
        };
    }

    private void spawnSanta() {
        Location loc = schematicManager.getBossSpawnLocation();
        if (loc == null && center != null) {
            loc = center.clone().add(0.5, 3.0, 10.5);
        }
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        loc.setYaw(180f);
        ArmorStand santa = loc.getWorld().spawn(loc, ArmorStand.class, s -> {
            s.setPersistent(false);
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setBasePlate(false);
            s.setArms(true);
            s.setSmall(false);
            s.setCollidable(false);
            s.setCustomNameVisible(true);
            s.setCustomName(ColorUtil.color("&c&lСанта"));
            s.setSilent(true);
            s.getPersistentDataContainer().set(santaKey, PersistentDataType.BYTE, (byte) 1);
        });
        if (santa.getEquipment() != null) {
            santa.getEquipment().setChestplate(dyed(Material.LEATHER_CHESTPLATE, Color.fromRGB(180, 20, 20)));
            santa.getEquipment().setLeggings(dyed(Material.LEATHER_LEGGINGS, Color.fromRGB(180, 20, 20)));
            santa.getEquipment().setBoots(dyed(Material.LEATHER_BOOTS, Color.fromRGB(40, 20, 20)));
            santa.getEquipment().setHelmet(dyed(Material.LEATHER_HELMET, Color.fromRGB(220, 30, 30)));
            santa.getEquipment().setItemInMainHand(createGift("legendary"));
        }
        this.santaUuid = santa.getUniqueId();
    }

    private ItemStack dyed(Material mat, Color color) {
        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(color);
            stack.setItemMeta(leather);
        }
        return stack;
    }

    private void removeSanta() {
        if (santaUuid != null) {
            Entity entity = Bukkit.getEntity(santaUuid);
            if (entity != null) {
                entity.remove();
            }
            santaUuid = null;
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
        return new Location(center.getWorld(), center.getX() + 0.5, center.getY() + 4.6, center.getZ() + 14.5);
    }

    private String[] hologramLines() {
        String coords = center == null ? "—"
                : center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ();
        return new String[]{
                ColorUtil.color("&c&lКарета Санты"),
                ColorUtil.color("&7Собирай подарки-шалкеры"),
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
        bossBar = Bukkit.createBossBar(ColorUtil.color("&c&lНовый год"), BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        refreshBossBar();
    }

    private void refreshBossBar() {
        if (bossBar == null) {
            return;
        }
        int left = getSecondsRemaining();
        int total = Math.max(1, plugin.getConfig().getInt("newyear.duration-minutes", 10) * 60);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, left / (double) total)));
        bossBar.setTitle(ColorUtil.color("&c&lНовый год &7· &#E0C912" + ColorUtil.formatDuration(left)));
    }

    private void broadcastSpawn() {
        if (center == null) {
            return;
        }
        center.getWorld().playSound(center, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.1f);
        Bukkit.broadcastMessage(ColorUtil.color("&e┼ &cКарета Санты приземлилась!"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &f9 оленей, мешок с подарками-шалкерами"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &f&lбелый &8/ &9голубой &8/ &dпурпур &8/ &cкрасный &f— редкость"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fОбмен подарков: &#E0C912/gifts"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fКоординаты: &#E0C912"
                + center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ()));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &fЛокатор: &#34D711/event gps"));
    }

    private void clearGiftDrops() {
        if (center == null || center.getWorld() == null) {
            return;
        }
        for (Entity entity : center.getWorld().getNearbyEntities(center, ARENA_RADIUS + 8, 10, ARENA_RADIUS + 8)) {
            if (entity instanceof Item item && isGift(item.getItemStack())) {
                item.remove();
            }
        }
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

    private void setChunksForceLoaded(Location loc, boolean loaded) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                loc.getWorld().getChunkAt(cx + dx, cz + dz).setForceLoaded(loaded);
            }
        }
    }

    private File restoreSnapshotFile() {
        return new File(plugin.getDataFolder(), "schematics/newyear-restore.yml");
    }

    @SuppressWarnings("deprecation")
    public void openExchangeMenu(Player player) {
        sanitizePlayer(player);
        Inventory inv = Bukkit.createInventory(new GiftExchangeHolder(), 27, ColorUtil.color("&8Обмен подарков"));
        ItemStack fill = icon(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, fill);
        }
        inv.setItem(10, icon(Material.WHITE_SHULKER_BOX, "&f&lОбычный подарок",
                List.of("&7Нужен белый шалкер-подарок", "&eНажмите, чтобы обменять")));
        inv.setItem(12, icon(Material.LIGHT_BLUE_SHULKER_BOX, "&9&lРедкий подарок",
                List.of("&7Нужен голубой шалкер-подарок", "&eНажмите, чтобы обменять")));
        inv.setItem(14, icon(Material.MAGENTA_SHULKER_BOX, "&d&lЭпический подарок",
                List.of("&7Нужен пурпурный шалкер-подарок", "&eНажмите, чтобы обменять")));
        inv.setItem(16, icon(Material.RED_SHULKER_BOX, "&6&lЛегендарный подарок",
                List.of("&7Нужен красный шалкер-подарок", "&eНажмите, чтобы обменять")));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.openInventory(inv);
            }
        });
    }

    public boolean handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GiftExchangeHolder)) {
            return false;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return true;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return true;
        }
        String rarity = switch (event.getSlot()) {
            case 10 -> "common";
            case 12 -> "rare";
            case 14 -> "epic";
            case 16 -> "legendary";
            default -> null;
        };
        if (rarity == null) {
            return true;
        }
        if (!exchangeBusy.add(player.getUniqueId())) {
            return true;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                exchangeOne(player, rarity);
            } finally {
                exchangeBusy.remove(player.getUniqueId());
            }
        });
        return true;
    }

    public boolean handleInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GiftExchangeHolder) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    private void exchangeOne(Player player, String rarity) {
        if (!player.isOnline()) {
            return;
        }
        sanitizePlayer(player);
        if (!takeOneGift(player, rarity)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.6f);
            return;
        }
        ItemStack prize = rollLoot(rarity);
        if (prize != null && !prize.getType().isAir()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(prize);
            leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
    }

    private boolean takeOneGift(Player player, String rarity) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            final int slot = i;
            if (consumeGiftSlot(inv.getItem(slot), rarity, stack -> inv.setItem(slot, stack))) {
                return true;
            }
        }
        if (consumeGiftSlot(inv.getItemInOffHand(), rarity, inv::setItemInOffHand)) {
            return true;
        }
        return consumeGiftSlot(player.getItemOnCursor(), rarity, player::setItemOnCursor);
    }

    private boolean consumeGiftSlot(ItemStack stack, String rarity, java.util.function.Consumer<ItemStack> set) {
        if (!isGift(stack) || !rarity.equalsIgnoreCase(giftRarity(stack))) {
            return false;
        }
        if (isSpentGift(stack)) {
            set.accept(null);
            return false;
        }
        String id = giftId(stack);
        if (id != null) {
            markSpent(id);
        }
        if (stack.getAmount() <= 1) {
            set.accept(null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
            set.accept(stack);
        }
        return true;
    }

    private void markSpent(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        spentGiftIds.add(id);
        while (spentGiftIds.size() > 8000) {
            String first = spentGiftIds.iterator().next();
            spentGiftIds.remove(first);
        }
        saveSpentIds();
    }

    private void loadSpentIds() {
        File file = spentFile();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getStringList("spent")) {
            if (id != null && !id.isEmpty()) {
                spentGiftIds.add(id);
            }
        }
    }

    private void saveSpentIds() {
        File file = spentFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spent", new ArrayList<>(spentGiftIds));
        try {
            yaml.save(file);
        } catch (IOException ignored) {
        }
    }

    private File spentFile() {
        return new File(plugin.getDataFolder(), "newyear-spent.yml");
    }

    @SuppressWarnings("deprecation")
    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(name));
            List<String> colored = new ArrayList<>();
            for (String line : lore) {
                colored.add(ColorUtil.color(line));
            }
            meta.setLore(colored);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
