package ru.geyserevent.mine;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.excavation.BrushTier;
import ru.geyserevent.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Менеджер 3D-Рюкзака Шахтёра, его прокачки (Уровни 1..6) и кастомной руды
 * для постоянного ивента «Шахта Древних (Жила Жеод)» на 0 ~ 0.
 *
 * Ключевые механики:
 * 1) Когда игрок заходит в радиус ивента «Шахта Древних», на его спине появляется
 *    кастомный 3D-рюкзак (через ресурс-пак GeyserEvent-Pack.mcpack), который НЕ трогает
 *    и НЕ занимает слоты брони игрока! При выходе из зоны рюкзак мгновенно исчезает.
 * 2) Все старые/забаганные стойки с рюкзаками автоматически удаляются при загрузке плагина
 *    и каждую секунду при проверке мира.
 * 3) Вместимость рюкзака определяет, сколько руды и жеод игрок может нести одновременно:
 *    Уровень 1: 15 руды | Уровень 2: 30 | Уровень 3: 50 | Уровень 4: 75 | Уровень 5: 110 | Уровень 6: 160.
 * 4) Когда игрок ломает руду или жеоду в шахте, предмет падает НА ПОЛ, а не сразу в инвентарь.
 * 5) Если игрок умирает, вся его добытая руда и жеоды выпадают НА ПОЛ на месте смерти.
 */
public class MinerBackpackManager {

    public static final int MAX_BACKPACK_LEVEL = 6;

    private static final int[] LEVEL_CAPACITIES = {0, 15, 30, 50, 75, 110, 160};
    private static final int[] LEVEL_UPGRADE_COSTS = {0, 0, 150, 400, 900, 1800, 3500};
    private static final int[] LEVEL_GEODE_BONUS_PCT = {0, 0, 5, 10, 15, 20, 25};
    private static final String[] LEVEL_NAMES = {
            "",
            "&7Походный кожаный мешок",
            "&aУкреплённый рюкзак рудокопа",
            "&bКованый шахтёрский ранец",
            "&dАметистовый горный короб",
            "&6Королевский ранец кузнеца",
            "&#FFB81C&lДревний Ковчег Титанов"
    };

    private final GeyserEventPlugin plugin;
    private final NamespacedKey mineOreKey;
    private final NamespacedKey visualBackpackKey;
    private final NamespacedKey visualBackpackOwnerKey;
    private final File dataFile;

    private final Map<UUID, Integer> playerLevels = new HashMap<>();
    private final Map<UUID, Integer> playerForgePoints = new HashMap<>();
    private final Map<UUID, Integer> playerTotalMined = new HashMap<>();
    private final Map<UUID, ArmorStand> visualBackpackStands = new HashMap<>();
    private final Map<UUID, Float> lastSyncedYaw = new HashMap<>();
    private final Map<UUID, Long> fullBackpackWarnCooldown = new HashMap<>();

    private BukkitTask backpackSyncTask;

    public enum MineOreType {
        COPPER("COPPER", "&6Древняя медная руда", Material.RAW_COPPER, Material.COPPER_INGOT, 2, 3, 1),
        IRON("IRON", "&fДревняя железная руда", Material.RAW_IRON, Material.IRON_INGOT, 4, 2, 2),
        GOLD("GOLD", "&eДревняя золотая руда", Material.RAW_GOLD, Material.GOLD_INGOT, 7, 2, 4),
        EMERALD("EMERALD", "&aДревний кристалл изумруда", Material.EMERALD, Material.EMERALD, 12, 2, 8),
        DIAMOND("DIAMOND", "&bДревний алмазный осколок", Material.DIAMOND, Material.DIAMOND, 18, 1, 14),
        ANCIENT_DEBRIS("ANCIENT_DEBRIS", "&#FF8C00Осколок древних обломков", Material.NETHERITE_SCRAP, Material.NETHERITE_SCRAP, 35, 1, 30);

        private final String id;
        private final String displayName;
        private final Material dropMaterial;
        private final Material smeltedMaterial;
        private final int forgePoints;
        private final int smeltedAmount;
        private final int bonusShards;

        MineOreType(String id, String displayName, Material dropMaterial, Material smeltedMaterial,
                    int forgePoints, int smeltedAmount, int bonusShards) {
            this.id = id;
            this.displayName = displayName;
            this.dropMaterial = dropMaterial;
            this.smeltedMaterial = smeltedMaterial;
            this.forgePoints = forgePoints;
            this.smeltedAmount = smeltedAmount;
            this.bonusShards = bonusShards;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Material getDropMaterial() {
            return dropMaterial;
        }

        public Material getSmeltedMaterial() {
            return smeltedMaterial;
        }

        public int getForgePoints() {
            return forgePoints;
        }

        public int getSmeltedAmount() {
            return smeltedAmount;
        }

        public int getBonusShards() {
            return bonusShards;
        }

        public static MineOreType fromBlockMaterial(Material blockMat) {
            return switch (blockMat) {
                case DEEPSLATE_COPPER_ORE, COPPER_ORE, RAW_COPPER_BLOCK -> COPPER;
                case DEEPSLATE_IRON_ORE, IRON_ORE, RAW_IRON_BLOCK -> IRON;
                case DEEPSLATE_GOLD_ORE, GOLD_ORE, NETHER_GOLD_ORE, GILDED_BLACKSTONE, RAW_GOLD_BLOCK -> GOLD;
                case DEEPSLATE_EMERALD_ORE, EMERALD_ORE -> EMERALD;
                case DEEPSLATE_DIAMOND_ORE, DIAMOND_ORE -> DIAMOND;
                case ANCIENT_DEBRIS -> ANCIENT_DEBRIS;
                default -> null;
            };
        }

        public static MineOreType fromId(String raw) {
            if (raw == null) {
                return null;
            }
            for (MineOreType t : values()) {
                if (t.id.equalsIgnoreCase(raw.trim())) {
                    return t;
                }
            }
            return null;
        }
    }

    public MinerBackpackManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.mineOreKey = new NamespacedKey(plugin, "mine_ore_type");
        this.visualBackpackKey = new NamespacedKey(plugin, "visual_backpack");
        this.visualBackpackOwnerKey = new NamespacedKey(plugin, "visual_backpack_owner");
        this.dataFile = new File(plugin.getDataFolder(), "backpacks.yml");
        loadData();
        // При старте сразу очищаем любые старые стойки рюкзаков в мире
        Bukkit.getScheduler().runTaskLater(plugin, this::purgeOrphanedBackpackStands, 5L);
        startVisualBackpackSyncTask();
    }

    public final void loadData() {
        playerLevels.clear();
        playerForgePoints.clear();
        playerTotalMined.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = yml.getConfigurationSection("players");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int lvl = Math.max(1, Math.min(MAX_BACKPACK_LEVEL, sec.getInt(key + ".level", 1)));
                int pts = Math.max(0, sec.getInt(key + ".points", 0));
                int mined = Math.max(0, sec.getInt(key + ".mined", 0));
                playerLevels.put(uuid, lvl);
                playerForgePoints.put(uuid, pts);
                playerTotalMined.put(uuid, mined);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void saveData() {
        YamlConfiguration yml = new YamlConfiguration();
        Set<UUID> allUuids = new HashSet<>();
        allUuids.addAll(playerLevels.keySet());
        allUuids.addAll(playerForgePoints.keySet());
        allUuids.addAll(playerTotalMined.keySet());

        for (UUID uuid : allUuids) {
            String path = "players." + uuid;
            yml.set(path + ".level", getBackpackLevel(uuid));
            yml.set(path + ".points", getForgePoints(uuid));
            yml.set(path + ".mined", getTotalMined(uuid));
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить backpacks.yml: " + e.getMessage());
        }
    }

    // =========================================================================
    // 3D-РЮКЗАК НА СПИНЕ ИГРОКА (БЕЗ ЗАТРАГИВАНИЯ БРОНИ, С АВТО-ОЧИСТКОЙ)
    // =========================================================================

    private void startVisualBackpackSyncTask() {
        if (backpackSyncTask != null) {
            backpackSyncTask.cancel();
        }
        // Поскольку рюкзак прикреплён к игроку через addPassenger, проверка зоны раз в 5 тиков не создаёт нагрузки
        backpackSyncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncVisualBackpacks, 5L, 5L);
    }

    /**
     * Обновляет позицию 3D-рюкзака за спиной каждого игрока внутри «Шахты Древних»
     * и мгновенно убирает его, если игрок вышел за пределы территории ивента.
     */
    public void syncVisualBackpacks() {
        AncientMineManager mineMgr = plugin.getAncientMineManager();
        if (mineMgr == null || !mineMgr.isMineActive()) {
            clearAllVisualBackpacks();
            return;
        }

        Set<UUID> activeInside = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (mineMgr.isPlayerInsideMineZone(player.getLocation())) {
                activeInside.add(player.getUniqueId());
                syncSinglePlayerBackpack(player);
            }
        }

        // Удаляем визуальный рюкзак у всех, кто вышел из зоны шахты или вышел с сервера
        var it = visualBackpackStands.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!activeInside.contains(entry.getKey())) {
                lastSyncedYaw.remove(entry.getKey());
                ArmorStand stand = entry.getValue();
                Player p = Bukkit.getPlayer(entry.getKey());
                if (stand != null) {
                    if (p != null) {
                        p.removePassenger(stand);
                    }
                    if (stand.isValid()) {
                        stand.remove();
                    }
                }
                it.remove();
                if (p != null && p.isOnline()) {
                    p.sendMessage(ColorUtil.color("&e┠ &7Рюкзак шахтёра снят со спины (вы покинули Шахту Древних)."));
                }
            }
        }
    }

    /**
     * Вызывается также при движении/телепортации игрока для мгновенного обновления без задержки.
     */
    public void onPlayerMovedOrTeleported(Player player) {
        if (player == null) {
            return;
        }
        AncientMineManager mineMgr = plugin.getAncientMineManager();
        if (mineMgr == null || !mineMgr.isMineActive() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            removeVisualBackpack(player);
            return;
        }
        if (mineMgr.isPlayerInsideMineZone(player.getLocation())) {
            syncSinglePlayerBackpack(player);
        } else if (visualBackpackStands.containsKey(player.getUniqueId())) {
            removeVisualBackpack(player);
            player.sendMessage(ColorUtil.color("&e┠ &7Рюкзак шахтёра снят со спины (вы покинули Шахту Древних)."));
        }
    }

    private void syncSinglePlayerBackpack(Player player) {
        UUID uuid = player.getUniqueId();
        Location pLoc = player.getLocation();
        if (pLoc.getWorld() == null) {
            return;
        }

        float yaw = pLoc.getYaw();
        ArmorStand stand = visualBackpackStands.get(uuid);
        if (stand == null || !stand.isValid() || !stand.getWorld().equals(pLoc.getWorld())) {
            if (stand != null) {
                player.removePassenger(stand);
                stand.remove();
            }
            ItemStack backpackModelItem = createVisualBackpackModelItem(getBackpackLevel(uuid));
            Location spawnLoc = pLoc.clone();
            spawnLoc.setPitch(0.0f);
            stand = pLoc.getWorld().spawn(spawnLoc, ArmorStand.class, s -> {
                s.setVisible(false);
                s.setGravity(false);
                s.setMarker(true);
                s.setSmall(false);
                s.setBasePlate(false);
                s.setArms(false);
                s.setSilent(true);
                s.setInvulnerable(true);
                s.setPersistent(false);
                s.setCustomNameVisible(false);
                s.setHeadPose(EulerAngle.ZERO);
                s.setBodyPose(EulerAngle.ZERO);
                s.getPersistentDataContainer().set(visualBackpackKey, PersistentDataType.BYTE, (byte) 1);
                s.getPersistentDataContainer().set(visualBackpackOwnerKey, PersistentDataType.STRING, uuid.toString());
                EntityEquipment equip = s.getEquipment();
                if (equip != null) {
                    equip.setHelmet(backpackModelItem);
                }
            });
            player.addPassenger(stand);
            stand.setRotation(yaw, 0.0f);
            visualBackpackStands.put(uuid, stand);
            lastSyncedYaw.put(uuid, yaw);

            int lvl = getBackpackLevel(uuid);
            int cap = getBackpackCapacity(uuid);
            int carried = countCarriedMineItems(player);
            player.sendMessage(ColorUtil.color(
                    "&e┠ &63D-Рюкзак шахтёра &8(&eУр. " + lvl + " &7| &a" + carried + "&7/&a" + cap + " руды&8) &fнадет на спину!"
            ));
        } else {
            if (!player.getPassengers().contains(stand)) {
                player.addPassenger(stand);
            }
            Float prevYaw = lastSyncedYaw.get(uuid);
            if (prevYaw == null || Math.abs(wrapAngleDelta(yaw - prevYaw)) >= 6.0f) {
                stand.setRotation(yaw, 0.0f);
                lastSyncedYaw.put(uuid, yaw);
            }
        }
    }

    private static float wrapAngleDelta(float delta) {
        float d = delta % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    /**
     * Очищает все старые/забаганные стойки с черепашьим шлемом (3D-рюкзаком) во всех мирах,
     * которые остались после рестарта или отсоединились от игрока.
     */
    public void purgeOrphanedBackpackStands() {
        AncientMineManager mineMgr = plugin.getAncientMineManager();
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                boolean isBackpackStand = stand.getPersistentDataContainer().has(visualBackpackKey, PersistentDataType.BYTE);
                if (!isBackpackStand) {
                    EntityEquipment eq = stand.getEquipment();
                    if (eq != null && eq.getHelmet() != null && eq.getHelmet().getType() == Material.TURTLE_HELMET && !stand.isVisible()) {
                        isBackpackStand = true;
                    }
                }
                if (!isBackpackStand) {
                    continue;
                }

                // Проверяем, принадлежит ли эта стойка активному игроку внутри зоны шахты
                boolean keep = false;
                for (Map.Entry<UUID, ArmorStand> entry : visualBackpackStands.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().getUniqueId().equals(stand.getUniqueId())) {
                        Player owner = Bukkit.getPlayer(entry.getKey());
                        if (owner != null && owner.isOnline() && !owner.isDead()
                                && mineMgr != null && mineMgr.isMineActive()
                                && mineMgr.isPlayerInsideMineZone(owner.getLocation())) {
                            keep = true;
                        }
                        break;
                    }
                }

                if (!keep) {
                    stand.remove();
                }
            }
        }
    }

    public boolean isActiveMountedBackpackStand(ArmorStand stand) {
        if (stand == null || !stand.isValid() || !(stand.getVehicle() instanceof Player owner) || !owner.isOnline() || owner.isDead()) {
            return false;
        }
        ArmorStand active = visualBackpackStands.get(owner.getUniqueId());
        return active != null && active.getUniqueId().equals(stand.getUniqueId());
    }

    public boolean isVisualBackpackEntity(Entity entity) {
        if (!(entity instanceof ArmorStand stand)) {
            return false;
        }
        if (stand.getPersistentDataContainer().has(visualBackpackKey, PersistentDataType.BYTE)) {
            return true;
        }
        EntityEquipment eq = stand.getEquipment();
        return !stand.isVisible() && eq != null && eq.getHelmet() != null && eq.getHelmet().getType() == Material.TURTLE_HELMET;
    }

    private ItemStack createVisualBackpackModelItem(int level) {
        ItemStack item = new ItemStack(Material.TURTLE_HELMET, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color("&6Рюкзак Шахтёра &7(Ур. " + level + ")"));
            meta.setCustomModelData(7100 + level);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void refreshVisualBackpackModel(Player player) {
        if (player == null) {
            return;
        }
        ArmorStand stand = visualBackpackStands.get(player.getUniqueId());
        if (stand != null && stand.isValid()) {
            EntityEquipment equip = stand.getEquipment();
            if (equip != null) {
                equip.setHelmet(createVisualBackpackModelItem(getBackpackLevel(player.getUniqueId())));
            }
        }
    }

    public void removeVisualBackpack(Player player) {
        if (player == null) {
            return;
        }
        lastSyncedYaw.remove(player.getUniqueId());
        ArmorStand stand = visualBackpackStands.remove(player.getUniqueId());
        if (stand != null) {
            player.removePassenger(stand);
            if (stand.isValid()) {
                stand.remove();
            }
        }
    }

    public void clearAllVisualBackpacks() {
        if (backpackSyncTask != null) {
            backpackSyncTask.cancel();
            backpackSyncTask = null;
        }
        for (ArmorStand stand : visualBackpackStands.values()) {
            if (stand != null && stand.isValid()) {
                stand.remove();
            }
        }
        visualBackpackStands.clear();
        lastSyncedYaw.clear();
        purgeOrphanedBackpackStands();
    }

    // =========================================================================
    // КАСТОМНЫЕ РУДЫ И ВМЕСТИМОСТЬ РЮКЗАКА
    // =========================================================================

    public ItemStack createMineOreItem(MineOreType type, int amount) {
        ItemStack item = new ItemStack(type.getDropMaterial(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(type.getDisplayName()));
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&8Ресурс ивента «Шахта Древних»"));
            lore.add("");
            lore.add(ColorUtil.color("&e┠ &fПереплавьте у &6Древнего Кузнеца &fна &e0 ~ 0&f:"));
            lore.add(ColorUtil.color("&e┠ &a+" + type.getForgePoints() + " очков кузни &7(для прокачки рюкзака)"));
            lore.add(ColorUtil.color("&e┠ &e+" + type.getSmeltedAmount() + "x слитков/кристаллов"));
            lore.add("");
            lore.add(ColorUtil.color("&cВыпадает на пол при смерти! Пропадает вне шахты."));
            meta.setLore(lore);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(mineOreKey, PersistentDataType.STRING, type.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    public MineOreType getMineOreType(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(mineOreKey, PersistentDataType.STRING);
        return MineOreType.fromId(raw);
    }

    public boolean isMineOreItem(ItemStack item) {
        return getMineOreType(item) != null;
    }

    /**
     * Проверяет, является ли предмет добытой в шахте рудой ИЛИ древней жеодой.
     */
    public boolean isMineOreOrGeode(ItemStack item) {
        return isMineOreItem(item) || plugin.getBrushLootManager().isGeodeItem(item);
    }

    /**
     * Считает общее количество единиц руды и древних жеод, которые сейчас несёт игрок.
     */
    public int countCarriedMineItems(Player player) {
        if (player == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMineOreOrGeode(item)) {
                total += item.getAmount();
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (isMineOreOrGeode(cursor)) {
            total += cursor.getAmount();
        }
        return total;
    }

    /**
     * Удаляет все непереплавленные руды и жеоды из инвентаря игрока (при выходе из зоны шахты).
     */
    public boolean removeAllMineOresAndGeodes(Player player) {
        if (player == null) {
            return false;
        }
        boolean removed = false;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isMineOreOrGeode(contents[i])) {
                player.getInventory().setItem(i, null);
                removed = true;
            }
        }
        if (isMineOreOrGeode(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
            removed = true;
        }
        if (isMineOreOrGeode(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed = true;
        }
        return removed;
    }

    /**
     * Выбрасывает все руды и жеоды игрока НА ПОЛ при его смерти (даже если на сервере включён keepInventory!).
     */
    public void dropAllMineItemsOnDeath(Player player, List<ItemStack> drops) {
        if (player == null) {
            return;
        }
        Location deathLoc = player.getLocation();
        World world = deathLoc.getWorld();
        if (world == null) {
            return;
        }

        // 1. Если сервер положил их в event.getDrops(), оставляем их в дропах (они упадут на пол сами)
        boolean dropsManagedByInventory = false;
        if (drops != null && !drops.isEmpty()) {
            for (ItemStack d : drops) {
                if (isMineOreOrGeode(d)) {
                    dropsManagedByInventory = true;
                    break;
                }
            }
        }

        // 2. Если keepInventory=true (или дропы пустые), вручную вынимаем из инвентаря и кидаем на пол!
        if (!dropsManagedByInventory) {
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (isMineOreOrGeode(item)) {
                    ItemStack clone = item.clone();
                    player.getInventory().setItem(i, null);
                    world.dropItemNaturally(deathLoc, clone);
                }
            }
            ItemStack off = player.getInventory().getItemInOffHand();
            if (isMineOreOrGeode(off)) {
                ItemStack clone = off.clone();
                player.getInventory().setItemInOffHand(null);
                world.dropItemNaturally(deathLoc, clone);
            }
        }
    }

    /**
     * Проверяет, помещается ли предмет с пола в рюкзак игрока.
     */
    public boolean canPickupMineItem(Player player, ItemStack stackToPickup) {
        int level = getBackpackLevel(player.getUniqueId());
        int maxCap = getBackpackCapacity(player.getUniqueId());
        int current = countCarriedMineItems(player);
        if (current + stackToPickup.getAmount() > maxCap) {
            long now = System.currentTimeMillis();
            long lastWarn = fullBackpackWarnCooldown.getOrDefault(player.getUniqueId(), 0L);
            if (now - lastWarn >= 2000L) {
                fullBackpackWarnCooldown.put(player.getUniqueId(), now);
                player.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ColorUtil.color(
                                "&cРюкзак полон (&e" + current + "/" + maxCap + "&c)! Сдайте руду Кузнецу или прокачайте рюкзак (Ур. " + level + ")"
                        ))
                );
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
            }
            return false;
        }
        return true;
    }

    public void notifyMineItemPickedUp(Player player, int addedAmount) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            int current = countCarriedMineItems(player);
            int maxCap = getBackpackCapacity(player.getUniqueId());
            int level = getBackpackLevel(player.getUniqueId());
            player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(ColorUtil.color(
                            "&6Рюкзак шахтёра &8(&eУр. " + level + "&8): &a" + current + "&7/&a" + maxCap + " руды &8| &7Сдайте у Древнего Кузнеца"
                    ))
            );
        }, 1L);
    }

    // =========================================================================
    // УРОВНИ, ПРОКАЧКА И ГЕТТЕРЫ
    // =========================================================================

    public int getBackpackLevel(UUID uuid) {
        return playerLevels.getOrDefault(uuid, 1);
    }

    public int getBackpackCapacity(UUID uuid) {
        int lvl = Math.max(1, Math.min(MAX_BACKPACK_LEVEL, getBackpackLevel(uuid)));
        return LEVEL_CAPACITIES[lvl];
    }

    public int getGeodeBonusPercent(UUID uuid) {
        int lvl = Math.max(1, Math.min(MAX_BACKPACK_LEVEL, getBackpackLevel(uuid)));
        return LEVEL_GEODE_BONUS_PCT[lvl];
    }

    public String getBackpackLevelTitle(int level) {
        int lvl = Math.max(1, Math.min(MAX_BACKPACK_LEVEL, level));
        return LEVEL_NAMES[lvl];
    }

    public int getForgePoints(UUID uuid) {
        return playerForgePoints.getOrDefault(uuid, 0);
    }

    public void addForgePoints(UUID uuid, int delta) {
        int current = getForgePoints(uuid);
        playerForgePoints.put(uuid, Math.max(0, current + delta));
    }

    public int getTotalMined(UUID uuid) {
        return playerTotalMined.getOrDefault(uuid, 0);
    }

    public void incrementTotalMined(UUID uuid, int delta) {
        playerTotalMined.put(uuid, getTotalMined(uuid) + Math.max(1, delta));
    }

    // =========================================================================
    // GUI-МЕНЮ КУЗНИЦЫ ДРЕВНИХ И ПРОКАЧКИ РЮКЗАКА
    // =========================================================================

    public static final class ForgeMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public void openForgeAndBackpackMenu(Player player) {
        UUID uuid = player.getUniqueId();
        int level = getBackpackLevel(uuid);
        int cap = getBackpackCapacity(uuid);
        int carried = countCarriedMineItems(player);
        int points = getForgePoints(uuid);
        int mined = getTotalMined(uuid);

        Inventory inv = Bukkit.createInventory(
                new ForgeMenuHolder(),
                27,
                ColorUtil.color("&8Кузница Древних и Рюкзак")
        );

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Слот 11: Переплавить всю добытую руду
        ItemStack smeltBtn = new ItemStack(Material.BLAST_FURNACE);
        ItemMeta smeltMeta = smeltBtn.getItemMeta();
        if (smeltMeta != null) {
            smeltMeta.setDisplayName(ColorUtil.color("&#FFB81C&lПереплавить всю руду из рюкзака"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&8Кузнечный горн Древних"));
            lore.add("");
            lore.add(ColorUtil.color("&e┠ &fСейчас в рюкзаке: &a" + carried + " &7/ &a" + cap + " ед."));
            lore.add(ColorUtil.color("&e┠ &fВаши очки кузни: &6" + points + " очк."));
            lore.add("");
            lore.add(ColorUtil.color("&6При переплавке вы получите:"));
            lore.add(ColorUtil.color("&e┠ &fВанильные слитки, алмазы, изумруды и незеритовый лом"));
            lore.add(ColorUtil.color("&e┠ &6+Очки кузни &fдля прокачки вместимости рюкзака"));
            lore.add("");
            lore.add(ColorUtil.color("&#34D711» Нажмите ЛКМ, чтобы переплавить всю руду!"));
            smeltMeta.setLore(lore);
            smeltBtn.setItemMeta(smeltMeta);
        }
        inv.setItem(11, smeltBtn);

        // Слот 13: Расколоть Древние Жеоды
        ItemStack geodeBtn = new ItemStack(Material.AMETHYST_CLUSTER);
        ItemMeta geodeMeta = geodeBtn.getItemMeta();
        if (geodeMeta != null) {
            geodeMeta.setDisplayName(ColorUtil.color("&d&lРасколоть Древние Жеоды"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&8Наковальня Древнего Кузнеца"));
            lore.add("");
            lore.add(ColorUtil.color("&e┠ &fРаскалывает все жеоды из вашего рюкзака"));
            lore.add(ColorUtil.color("&e┠ &fи выдаёт редкие награды из таблицы лута!"));
            lore.add(ColorUtil.color("&e┠ &fБонус от вашего рюкзака: &a+" + getGeodeBonusPercent(uuid) + "% к удаче"));
            lore.add("");
            lore.add(ColorUtil.color("&#34D711» Нажмите ЛКМ, чтобы расколоть все жеоды!"));
            geodeMeta.setLore(lore);
            geodeMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            geodeMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            geodeBtn.setItemMeta(geodeMeta);
        }
        inv.setItem(13, geodeBtn);

        // Слот 15: Прокачка 3D-Рюкзака Шахтёра
        ItemStack backpackBtn = new ItemStack(Material.TURTLE_HELMET);
        ItemMeta bpMeta = backpackBtn.getItemMeta();
        if (bpMeta != null) {
            bpMeta.setDisplayName(ColorUtil.color("&6&l3D-Рюкзак Шахтёра &8[&eУровень " + level + "/" + MAX_BACKPACK_LEVEL + "&8]"));
            bpMeta.setCustomModelData(7100 + level);
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color(getBackpackLevelTitle(level)));
            lore.add("");
            lore.add(ColorUtil.color("&e┠ &fТекущая вместимость: &a" + cap + " руды/жеод"));
            lore.add(ColorUtil.color("&e┠ &fБонус удачи жеод: &a+" + getGeodeBonusPercent(uuid) + "%"));
            lore.add(ColorUtil.color("&e┠ &fВсего вскопано в шахте: &e" + mined + " блоков"));
            lore.add(ColorUtil.color("&e┠ &fОчки кузни: &6" + points + " очк."));
            lore.add("");
            if (level < MAX_BACKPACK_LEVEL) {
                int nextLvl = level + 1;
                int nextCap = LEVEL_CAPACITIES[nextLvl];
                int cost = LEVEL_UPGRADE_COSTS[nextLvl];
                int nextBonus = LEVEL_GEODE_BONUS_PCT[nextLvl];
                lore.add(ColorUtil.color("&6Следующий уровень (" + nextLvl + "):"));
                lore.add(ColorUtil.color("&e┠ &fНовая вместимость: &a" + cap + " &7-> &a&l" + nextCap + " руды"));
                lore.add(ColorUtil.color("&e┠ &fБонус удачи жеод: &a+" + nextBonus + "%"));
                lore.add(ColorUtil.color("&e┠ &fСтоимость прокачки: &6" + cost + " очков кузни"));
                lore.add("");
                if (points >= cost) {
                    lore.add(ColorUtil.color("&#34D711» Нажмите ЛКМ, чтобы прокачать рюкзак до Ур. " + nextLvl + "!"));
                } else {
                    lore.add(ColorUtil.color("&c» Не хватает ещё " + (cost - points) + " очков кузни (плавьте руду)!"));
                }
            } else {
                lore.add(ColorUtil.color("&#FFB81C&l» Ваш рюкзак прокачан до МАКСИМАЛЬНОГО уровня!"));
            }
            bpMeta.setLore(lore);
            bpMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            if (level >= 4) {
                bpMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            }
            backpackBtn.setItemMeta(bpMeta);
        }
        inv.setItem(15, backpackBtn);

        player.openInventory(inv);
    }

    public boolean handleForgeMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ForgeMenuHolder)) {
            return false;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return true;
        }
        int slot = event.getRawSlot();
        if (slot == 11) {
            smeltAllPlayerOres(player);
            openForgeAndBackpackMenu(player);
        } else if (slot == 13) {
            crackAllPlayerGeodes(player);
            openForgeAndBackpackMenu(player);
        } else if (slot == 15) {
            upgradePlayerBackpack(player);
            openForgeAndBackpackMenu(player);
        }
        return true;
    }

    public boolean handleForgeMenuDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ForgeMenuHolder) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    /**
     * Переплавляет все кастомные руды из рюкзака игрока в слитки + очки кузни (без выдачи осколков).
     */
    public void smeltAllPlayerOres(Player player) {
        int totalOres = 0;
        int totalPoints = 0;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            MineOreType oreType = getMineOreType(stack);
            if (oreType == null) {
                continue;
            }
            int amt = stack.getAmount();
            player.getInventory().setItem(i, null);

            totalOres += amt;
            totalPoints += oreType.getForgePoints() * amt;

            ItemStack reward = new ItemStack(oreType.getSmeltedMaterial(), oreType.getSmeltedAmount() * amt);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
            for (ItemStack left : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }

        if (totalOres <= 0) {
            player.sendMessage(ColorUtil.color("&e┠ &cВ вашем рюкзаке нет непереплавленной руды! Вскопайте руду в карьере."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            return;
        }

        UUID uuid = player.getUniqueId();
        addForgePoints(uuid, totalPoints);
        saveData();

        player.playSound(player.getLocation(), Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 1.0f, 1.1f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.25f);
        player.sendMessage(ColorUtil.color(
                "&e┠ &6Древний Кузнец &8» &fПереплавлено &a" + totalOres + " шт. руды&f! Получено: &6+"
                        + totalPoints + " очков кузни&f!"
        ));
    }

    /**
     * Раскалывает все древние жеоды из инвентаря игрока с учётом бонуса удачи от уровня рюкзака.
     */
    public void crackAllPlayerGeodes(Player player) {
        int cracked = 0;
        UUID uuid = player.getUniqueId();
        int bonusPct = getGeodeBonusPercent(uuid);

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!plugin.getBrushLootManager().isGeodeItem(stack)) {
                continue;
            }
            BrushTier tier = plugin.getBrushLootManager().getBrushTier(stack);
            if (tier == null) {
                continue;
            }
            int amt = stack.getAmount();
            player.getInventory().setItem(i, null);

            for (int c = 0; c < amt; c++) {
                cracked++;
                addForgePoints(uuid, switch (tier) {
                    case COMMON -> 10;
                    case EPIC -> 20;
                    case LEGENDARY -> 35;
                });
                ItemStack reward = plugin.getBrushLootManager().rollLootForMine(tier);
                if (reward != null && reward.getType() != Material.AIR) {
                    Map<Integer, ItemStack> left = player.getInventory().addItem(reward);
                    for (ItemStack l : left.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), l);
                    }
                }
                // Дополнительный шанс на бонусный лут от прокачанного рюкзака
                if (bonusPct > 0 && java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < bonusPct) {
                    ItemStack extra = plugin.getBrushLootManager().rollLootForMine(tier);
                    if (extra != null && extra.getType() != Material.AIR) {
                        Map<Integer, ItemStack> left = player.getInventory().addItem(extra);
                        for (ItemStack l : left.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), l);
                        }
                    }
                }
            }
        }

        if (cracked <= 0) {
            player.sendMessage(ColorUtil.color("&e┠ &cВ вашем рюкзаке нет Древних жеод! Ищите светящиеся аметисты и кристаллы в шахте."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            return;
        }

        saveData();
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.9f, 1.25f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.15f);
        player.sendMessage(ColorUtil.color(
                "&e┠ &6Древний Кузнец &8» &fРасколото &d" + cracked + " древних жеод&f! Награды выданы в инвентарь."
        ));
    }

    /**
     * Повышает уровень рюкзака игрока за Очки Кузни.
     */
    public void upgradePlayerBackpack(Player player) {
        UUID uuid = player.getUniqueId();
        int level = getBackpackLevel(uuid);
        if (level >= MAX_BACKPACK_LEVEL) {
            player.sendMessage(ColorUtil.color("&e┠ &6Ваш 3D-Рюкзак Шахтёра уже прокачан до максимального 6-го уровня!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.9f, 1.1f);
            return;
        }

        int nextLvl = level + 1;
        int cost = LEVEL_UPGRADE_COSTS[nextLvl];
        int points = getForgePoints(uuid);
        if (points < cost) {
            player.sendMessage(ColorUtil.color(
                    "&e┠ &cНедостаточно очков кузни для прокачки! Нужно: &6" + cost + "&c, у вас: &e" + points + " &7(переплавляйте руду у Кузнеца)."
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            return;
        }

        playerForgePoints.put(uuid, points - cost);
        playerLevels.put(uuid, nextLvl);
        saveData();
        refreshVisualBackpackModel(player);

        int newCap = getBackpackCapacity(uuid);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0f, 1.1f);
        player.sendMessage(ColorUtil.color(
                "&e┠ &#34D711&lПРОКАЧКА РЮКЗАКА! &fВаш рюкзак улучшен до &eУровня " + nextLvl
                        + " &8(" + getBackpackLevelTitle(nextLvl) + "&8)&f! Вместимость: &a&l" + newCap + " руды&f!"
        ));
    }
}
