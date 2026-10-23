package ru.geyserevent.mine;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.excavation.BrushLootManager;
import ru.geyserevent.excavation.BrushTier;
import ru.geyserevent.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер Шахтёрского Рюкзака, добытой руды и меню «Кузница Древних»:
 * 1) Визуальный 3D-рюкзак на спине игрока, который автоматически появляется при входе в радиус
 *    «Шахты Древних» (использует GeyserEvent-Pack.mcpack) и НЕ занимает слоты брони игрока.
 * 2) Прокачка рюкзака (Уровни 1..6: от 15 до 160 ед. руды/жеод + бонус к шансу жеод).
 * 3) Переплавка добытой руды и раскол жеод у Древнего Кузнеца.
 */
public class MinerBackpackManager {

    public enum MineOreType {
        COPPER("Медный самородок Древних", "&6Медный самородок Древних", Material.RAW_COPPER, Material.COPPER_INGOT, 2, 3, 0),
        IRON("Железная порода Древних", "&fЖелезная порода Древних", Material.RAW_IRON, Material.IRON_INGOT, 4, 2, 0),
        GOLD("Золотая жила Древних", "&eЗолотая жила Древних", Material.RAW_GOLD, Material.GOLD_INGOT, 7, 2, 1),
        EMERALD("Изумрудный кристалл Древних", "&aИзумрудный кристалл Древних", Material.EMERALD, Material.EMERALD, 12, 2, 2),
        DIAMOND("Алмазное ядро Древних", "&bАлмазное ядро Древних", Material.DIAMOND, Material.DIAMOND, 20, 1, 5),
        ANCIENT_DEBRIS("Осколок Древних Недр", "&#FF5555Осколок Древних Недр", Material.NETHERITE_SCRAP, Material.NETHERITE_SCRAP, 40, 1, 12);

        private final String plainName;
        private final String displayName;
        private final Material oreItemMaterial;
        private final Material smeltedMaterial;
        private final int forgePoints;
        private final int smeltedAmount;
        private final int bonusShards;

        MineOreType(String plainName, String displayName, Material oreItemMaterial, Material smeltedMaterial,
                    int forgePoints, int smeltedAmount, int bonusShards) {
            this.plainName = plainName;
            this.displayName = displayName;
            this.oreItemMaterial = oreItemMaterial;
            this.smeltedMaterial = smeltedMaterial;
            this.forgePoints = forgePoints;
            this.smeltedAmount = smeltedAmount;
            this.bonusShards = bonusShards;
        }

        public String getPlainName() {
            return plainName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Material getOreItemMaterial() {
            return oreItemMaterial;
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
    }

    public static class ForgeMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final int MAX_BACKPACK_LEVEL = 6;
    private static final int[] LEVEL_CAPACITIES = {0, 15, 30, 50, 75, 110, 160};
    private static final int[] LEVEL_UPGRADE_COSTS = {0, 0, 40, 100, 220, 450, 900};
    private static final int[] LEVEL_GEODE_BONUS_PERCENT = {0, 0, 3, 6, 10, 15, 25};
    private static final String[] LEVEL_TITLES = {
            "",
            "&7Кожаный рюкзак &8(Ур. 1)",
            "&6Медный кованый рюкзак &8(Ур. 2)",
            "&fЖелезный ранец рудокопа &8(Ур. 3)",
            "&eЗолотой ранец Древних &8(Ур. 4)",
            "&bАлмазный гео-ранец &8(Ур. 5)",
            "&#FF5555&lНезеритовый ранец Владыки Недр &8(Ур. 6 МАКС)"
    };

    private final GeyserEventPlugin plugin;
    private final NamespacedKey oreTypeKey;
    private final NamespacedKey visualBackpackKey;
    private final File dataFile;

    private final Map<UUID, Integer> backpackLevels = new HashMap<>();
    private final Map<UUID, Integer> playerForgePoints = new HashMap<>();
    private final Map<UUID, UUID> activeBackVisualEntities = new HashMap<>();
    private final Map<UUID, Long> fullBackpackWarnCooldown = new HashMap<>();

    private BukkitTask visualSyncTask;

    public MinerBackpackManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.oreTypeKey = new NamespacedKey(plugin, "mine_ore_type");
        this.visualBackpackKey = new NamespacedKey(plugin, "visual_miner_backpack");
        this.dataFile = new File(plugin.getDataFolder(), "backpacks.yml");
        loadData();
        startVisualBackpackSyncTask();
    }

    public final void loadData() {
        backpackLevels.clear();
        playerForgePoints.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection playersSec = yaml.getConfigurationSection("players");
        if (playersSec == null) {
            return;
        }
        for (String key : playersSec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int level = Math.max(1, Math.min(MAX_BACKPACK_LEVEL, playersSec.getInt(key + ".level", 1)));
                int points = Math.max(0, playersSec.getInt(key + ".points", 0));
                backpackLevels.put(uuid, level);
                playerForgePoints.put(uuid, points);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (UUID uuid : backpackLevels.keySet()) {
            yaml.set("players." + uuid + ".level", backpackLevels.getOrDefault(uuid, 1));
            yaml.set("players." + uuid + ".points", playerForgePoints.getOrDefault(uuid, 0));
        }
        for (UUID uuid : playerForgePoints.keySet()) {
            if (!backpackLevels.containsKey(uuid)) {
                yaml.set("players." + uuid + ".level", 1);
                yaml.set("players." + uuid + ".points", playerForgePoints.getOrDefault(uuid, 0));
            }
        }
        try {
            yaml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить backpacks.yml: " + ex.getMessage());
        }
    }

    public int getBackpackLevel(Player player) {
        if (player == null) {
            return 1;
        }
        return backpackLevels.getOrDefault(player.getUniqueId(), 1);
    }

    public int getMaxCapacity(Player player) {
        int lvl = getBackpackLevel(player);
        return LEVEL_CAPACITIES[Math.max(1, Math.min(MAX_BACKPACK_LEVEL, lvl))];
    }

    public int getGeodeBonusChancePercent(Player player) {
        int lvl = getBackpackLevel(player);
        return LEVEL_GEODE_BONUS_PERCENT[Math.max(1, Math.min(MAX_BACKPACK_LEVEL, lvl))];
    }

    public int getForgePoints(Player player) {
        if (player == null) {
            return 0;
        }
        return playerForgePoints.getOrDefault(player.getUniqueId(), 0);
    }

    public void addForgePoints(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        UUID uuid = player.getUniqueId();
        playerForgePoints.put(uuid, getForgePoints(player) + amount);
        saveData();
    }

    /**
     * Создаёт предмет добытой руды «Шахты Древних» для выброса на пол при вскапывании блока.
     */
    @SuppressWarnings("deprecation")
    public ItemStack createMineOreItem(MineOreType type, int amount) {
        ItemStack stack = new ItemStack(type.getOreItemMaterial(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(type.getDisplayName()));
            meta.setLore(List.of(
                    ColorUtil.color("&7Ивент: &#FFB81CШахта Древних (0 ~ 0)"),
                    ColorUtil.color("&7Ценность: &#34D711+" + type.getForgePoints() + " очков кузницы"),
                    ColorUtil.color("&8Сдайте Древнему Кузнецу в центре для"),
                    ColorUtil.color("&8переплавки и прокачки рюкзака!")
            ));
            Enchantment unb = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
            if (unb != null && (type == MineOreType.DIAMOND || type == MineOreType.ANCIENT_DEBRIS)) {
                meta.addEnchant(unb, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(oreTypeKey, PersistentDataType.STRING, type.name());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public MineOreType getMineOreType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(oreTypeKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return MineOreType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isMineOreItem(ItemStack item) {
        return getMineOreType(item) != null;
    }

    /**
     * Проверяет, является ли предмет рудой или жеодой из «Шахты Древних».
     */
    public boolean isMineOreOrGeode(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return isMineOreItem(item) || plugin.getBrushLootManager().isGeodeItem(item);
    }

    /**
     * Подсчитывает, сколько всего единиц руды и жеод из «Шахты Древних» сейчас несёт игрок.
     */
    public int countCarriedMineItems(Player player) {
        if (player == null) {
            return 0;
        }
        int total = 0;
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getStorageContents()) {
            if (isMineOreOrGeode(item)) {
                total += item.getAmount();
            }
        }
        ItemStack off = inv.getItemInOffHand();
        if (isMineOreOrGeode(off)) {
            total += off.getAmount();
        }
        ItemStack cursor = player.getItemOnCursor();
        if (isMineOreOrGeode(cursor)) {
            total += cursor.getAmount();
        }
        return total;
    }

    /**
     * Удаляет все руды и жеоды «Шахты Древних» у игрока (например, при выходе за пределы зоны шахты).
     */
    public boolean removeAllMineOresAndGeodes(Player player) {
        if (player == null) {
            return false;
        }
        boolean removed = false;
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isMineOreOrGeode(contents[i])) {
                inv.setItem(i, null);
                removed = true;
            }
        }
        if (isMineOreOrGeode(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
            removed = true;
        }
        if (isMineOreOrGeode(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed = true;
        }
        return removed;
    }

    /**
     * Вызывается при смерти игрока: гарантирует, что ВСЕ его руды и жеоды выпадут на пол!
     */
    public void dropAllMineItemsOnDeath(Player player, List<ItemStack> drops) {
        if (player == null || player.getWorld() == null) {
            return;
        }
        Location deathLoc = player.getLocation();
        World world = deathLoc.getWorld();

        // Если сервер использует keepInventory=true или очищает дроп, собираем руды напрямую из инвентаря
        List<ItemStack> toDrop = new ArrayList<>();
        if (drops != null && !drops.isEmpty()) {
            // Оставляем руды и жеоды в стандартном дропе смерти (они сами упадут на пол!)
            return;
        }

        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (isMineOreOrGeode(contents[i])) {
                toDrop.add(contents[i].clone());
                inv.setItem(i, null);
            }
        }
        if (isMineOreOrGeode(inv.getItemInOffHand())) {
            toDrop.add(inv.getItemInOffHand().clone());
            inv.setItemInOffHand(null);
        }
        for (ItemStack st : toDrop) {
            world.dropItemNaturally(deathLoc, st);
        }
    }

    /**
     * Проверяет, может ли игрок поднять с пола предмет руды/жеоды согласно вместимости его Рюкзака.
     */
    @SuppressWarnings("deprecation")
    public boolean canPickupMineItem(Player player, ItemStack stackOnGround) {
        int carried = countCarriedMineItems(player);
        int maxCap = getMaxCapacity(player);
        if (carried >= maxCap) {
            long now = System.currentTimeMillis();
            long last = fullBackpackWarnCooldown.getOrDefault(player.getUniqueId(), 0L);
            if (now - last > 2000L) {
                fullBackpackWarnCooldown.put(player.getUniqueId(), now);
                String msg = ColorUtil.color("&e┠ &cРюкзак заполнен (&f" + carried + "/" + maxCap
                        + "&c)! Сдайте руду или прокачайте рюкзак у &#FFB81CДревнего Кузнеца&c.");
                player.sendMessage(msg);
                player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT_FAIL, 0.9f, 1.0f);
                try {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
                } catch (Throwable ignored) {
                }
            }
            return false;
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    public void notifyMineItemPickedUp(Player player, int addedAmount) {
        int carried = Math.min(getMaxCapacity(player), countCarriedMineItems(player) + addedAmount);
        int maxCap = getMaxCapacity(player);
        int lvl = getBackpackLevel(player);
        String ab = ColorUtil.color("&6Рюкзак шахтёра (Ур. " + lvl + "): &#34D711" + carried + "&7/&e" + maxCap + " руды");
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ab));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Открывает GUI-меню «Кузница Древних и Прокачка Рюкзака» (27 слотов).
     */
    @SuppressWarnings("deprecation")
    public void openForgeAndBackpackMenu(Player player) {
        int lvl = getBackpackLevel(player);
        int maxCap = getMaxCapacity(player);
        int carried = countCarriedMineItems(player);
        int points = getForgePoints(player);

        Inventory inv = Bukkit.createInventory(
                new ForgeMenuHolder(),
                27,
                ColorUtil.color("&8Кузница Древних и Рюкзак")
        );

        ItemStack border = createButton(Material.BLACK_STAINED_GLASS_PANE, "&8", List.of());
        ItemStack orange = createButton(Material.ORANGE_STAINED_GLASS_PANE, "&6", List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, (i == 0 || i == 8 || i == 18 || i == 26) ? orange : border);
        }

        // Подсчёт руды и жеод в инвентаре игрока
        int oreCount = 0;
        int potentialPoints = 0;
        int geodeCount = 0;
        for (ItemStack st : player.getInventory().getStorageContents()) {
            MineOreType oreType = getMineOreType(st);
            if (oreType != null) {
                oreCount += st.getAmount();
                potentialPoints += oreType.getForgePoints() * st.getAmount();
            } else if (plugin.getBrushLootManager().isGeodeItem(st)) {
                geodeCount += st.getAmount();
            }
        }

        // Слот 4: Информация о профиле шахтёра
        inv.setItem(4, createButton(
                Material.NETHER_STAR,
                "&#FFB81C&lПрофиль Шахтёра — " + player.getName(),
                List.of(
                        "&e┠ &fВаш рюкзак: " + LEVEL_TITLES[lvl],
                        "&e┠ &fЗаполненность: &#34D711" + carried + " &7/ &e" + maxCap + " ед.",
                        "&e┠ &fБонус к редким жеодам: &#34D711+" + getGeodeBonusChancePercent(player) + "%",
                        "&e┠ &fОчки Кузницы: &#E0C912" + points + " шт.",
                        "",
                        "&7Рюкзак автоматически появляется у вас на спине,",
                        "&7когда вы находитесь на территории Шахты Древних!"
                )
        ));

        // Слот 11: Переплавить всю добытую руду
        inv.setItem(11, createButton(
                Material.BLAST_FURNACE,
                "&#FF9B14&lПереплавить добытую руду",
                List.of(
                        "&7Сдать всю добытую в карьере породу",
                        "&7в плавильню Древнего Кузнеца.",
                        "",
                        "&e┠ &fРуды в рюкзаке: &#34D711" + oreCount + " шт.",
                        "&e┠ &fВы получите: &#E0C912+" + potentialPoints + " очков кузницы",
                        "&e┠ &fПлюс переплавленные слитки, алмазы и осколки!",
                        "",
                        oreCount > 0 ? "&#34D711» Нажмите, чтобы переплавить всю руду!" : "&c» Сначала накопайте руду в карьере"
                )
        ));

        // Слот 13: Расколоть Древние жеоды
        inv.setItem(13, createButton(
                Material.ANVIL,
                "&#C850FF&lРасколоть Древние жеоды",
                List.of(
                        "&7Кузнец расколет найденные в жиле",
                        "&7Древние жеоды своим молотом!",
                        "",
                        "&e┠ &fЖеод в рюкзаке: &#34D711" + geodeCount + " шт.",
                        "&e┠ &fЗа раскол вы получаете редкий лут",
                        "&e┠ &fи бонусные &#E0C912Очки Кузницы&f!",
                        "",
                        geodeCount > 0 ? "&#34D711» Нажмите, чтобы расколоть жеоду!" : "&c» Добудьте жеоду в рудной жиле"
                )
        ));

        // Слот 15: Прокачать Шахтёрский Рюкзак
        if (lvl < MAX_BACKPACK_LEVEL) {
            int nextLvl = lvl + 1;
            int cost = LEVEL_UPGRADE_COSTS[nextLvl];
            int nextCap = LEVEL_CAPACITIES[nextLvl];
            int nextBonus = LEVEL_GEODE_BONUS_PERCENT[nextLvl];
            boolean canAfford = points >= cost;

            inv.setItem(15, createButton(
                    Material.TURTLE_HELMET,
                    "&#34D711&lПрокачать Рюкзак &8(&fУр. " + lvl + " -> Ур. " + nextLvl + "&8)",
                    List.of(
                            "&7Увеличивает вместимость вашего рюкзака",
                            "&7и шанс находить легендарные жеоды!",
                            "",
                            "&e┠ &fСледующий: " + LEVEL_TITLES[nextLvl],
                            "&e┠ &fВместимость: &e" + maxCap + " &7-> &#34D711" + nextCap + " руды",
                            "&e┠ &fШанс редких жеод: &e+" + LEVEL_GEODE_BONUS_PERCENT[lvl] + "% &7-> &#34D711+" + nextBonus + "%",
                            "",
                            "&e┠ &fСтоимость: &#E0C912" + cost + " очков кузницы &8(&fУ вас: " + points + "&8)",
                            "",
                            canAfford ? "&#34D711» Нажмите, чтобы улучшить рюкзак!" : "&c» Переплавьте больше руды для прокачки"
                    )
            ));
        } else {
            inv.setItem(15, createButton(
                    Material.TURTLE_HELMET,
                    "&#FF5555&lРюкзак прокачан до МАКСИМУМА!",
                    List.of(
                            "&e┠ &fУровень: &66 / 6 (Максимальный)",
                            "&e┠ &fВместимость: &#34D711" + maxCap + " руды",
                            "&e┠ &fБонус к редким жеодам: &#34D711+25%",
                            "",
                            "&aВы достигли вершины мастерства Древних!"
                    )
            ));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.7f, 1.2f);
    }

    public boolean handleForgeMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ForgeMenuHolder)) {
            return false;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != top) {
            return true;
        }

        int slot = event.getSlot();
        if (slot == 11) {
            smeltAllPlayerOres(player);
            openForgeAndBackpackMenu(player);
            return true;
        }
        if (slot == 13) {
            crackPlayerGeodeInForge(player);
            openForgeAndBackpackMenu(player);
            return true;
        }
        if (slot == 15) {
            tryUpgradeBackpack(player);
            openForgeAndBackpackMenu(player);
            return true;
        }
        return true;
    }

    public boolean handleForgeMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ForgeMenuHolder) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    private void smeltAllPlayerOres(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();

        int totalOres = 0;
        int totalPoints = 0;
        int totalShards = 0;
        List<ItemStack> smeltedItems = new ArrayList<>();

        for (int i = 0; i < contents.length; i++) {
            ItemStack st = contents[i];
            MineOreType oreType = getMineOreType(st);
            if (oreType != null) {
                int amt = st.getAmount();
                totalOres += amt;
                totalPoints += oreType.getForgePoints() * amt;
                totalShards += oreType.getBonusShards() * amt;
                smeltedItems.add(new ItemStack(oreType.getSmeltedMaterial(), oreType.getSmeltedAmount() * amt));
                inv.setItem(i, null);
            }
        }

        if (totalOres <= 0) {
            player.sendMessage(ColorUtil.color("&e┠ &cУ вас нет добытой руды для переплавки! Вскопайте руду в карьере."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        addForgePoints(player, totalPoints);
        for (ItemStack reward : smeltedItems) {
            Map<Integer, ItemStack> left = inv.addItem(reward);
            for (ItemStack drop : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
        if (totalShards > 0) {
            plugin.getPlayerPointsHook().giveShards(player.getUniqueId(), player.getName(), totalShards);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 1.2f, 1.1f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.25f);
        player.sendMessage(ColorUtil.color("&e┠ &#FFB81CКузница Древних: &fПереплавлено &#34D711" + totalOres
                + " шт. руды&f! Получено &#E0C912+" + totalPoints + " очков кузницы"
                + (totalShards > 0 ? " &fи &#34D711+" + totalShards + " осколков" : "") + "&f."));
    }

    private void crackPlayerGeodeInForge(Player player) {
        BrushLootManager lootManager = plugin.getBrushLootManager();
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();

        int foundSlot = -1;
        BrushTier tier = null;
        for (int i = 0; i < contents.length; i++) {
            BrushTier t = lootManager.getGeodeTier(contents[i]);
            if (t != null) {
                foundSlot = i;
                tier = t;
                break;
            }
        }

        if (tier == null || foundSlot < 0) {
            player.sendMessage(ColorUtil.color("&e┠ &cУ вас нет Древних жеод! Добудьте их в рудной жиле карьера."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ItemStack stack = inv.getItem(foundSlot);
        if (stack != null) {
            if (stack.getAmount() > 1) {
                stack.setAmount(stack.getAmount() - 1);
            } else {
                inv.setItem(foundSlot, null);
            }
        }

        int bonusPoints = switch (tier) {
            case COMMON -> 10;
            case EPIC -> 25;
            case LEGENDARY -> 55;
        };
        addForgePoints(player, bonusPoints);

        ItemStack reward = lootManager.rollLootForMine(tier);
        if (reward != null && !reward.getType().isAir()) {
            Map<Integer, ItemStack> left = inv.addItem(reward);
            for (ItemStack drop : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.15f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);
        player.sendMessage(ColorUtil.color("&e┠ &#FFB81CДревний Кузнец: &fЖеода расколота! Получена награда и &#E0C912+"
                + bonusPoints + " очков кузницы&f."));
    }

    private void tryUpgradeBackpack(Player player) {
        int lvl = getBackpackLevel(player);
        if (lvl >= MAX_BACKPACK_LEVEL) {
            player.sendMessage(ColorUtil.color("&e┠ &aУ вас уже максимальный уровень рюкзака!"));
            return;
        }
        int nextLvl = lvl + 1;
        int cost = LEVEL_UPGRADE_COSTS[nextLvl];
        int points = getForgePoints(player);
        if (points < cost) {
            player.sendMessage(ColorUtil.color("&e┠ &cНедостаточно очков кузницы! Нужно: &#E0C912" + cost
                    + "&c, у вас: &#E0C912" + points + " &7(переплавьте добытую руду)."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        UUID uuid = player.getUniqueId();
        playerForgePoints.put(uuid, points - cost);
        backpackLevels.put(uuid, nextLvl);
        saveData();

        // Обновляем визуальный рюкзак на спине игрока
        removeVisualBackpack(player);
        updateVisualBackpackForPlayer(player);

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        player.sendMessage(ColorUtil.color("&e┠ &#34D711Рюкзак улучшен до " + LEVEL_TITLES[nextLvl]
                + "&#34D711! Вместимость: &#E0C912" + LEVEL_CAPACITIES[nextLvl] + " руды&#34D711."));
    }

    /**
     * Запускает синхронизацию 3D-рюкзака на спине игроков, находящихся в радиусе «Шахты Древних».
     * Броне игрока (шлем, нагрудник, поножи, ботинки) это НЕ мешает ни на 1%!
     */
    private void startVisualBackpackSyncTask() {
        if (visualSyncTask != null) {
            visualSyncTask.cancel();
        }
        visualSyncTask = new BukkitRunnable() {
            @Override
            public void run() {
                AncientMineManager mineMgr = plugin.getAncientMineManager();
                if (mineMgr == null || !mineMgr.isMineActive()) {
                    clearAllVisualBackpacks();
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateVisualBackpackForPlayer(player);
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    public void updateVisualBackpackForPlayer(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            if (player != null) {
                removeVisualBackpack(player);
            }
            return;
        }
        AncientMineManager mineMgr = plugin.getAncientMineManager();
        boolean insideMine = mineMgr != null
                && mineMgr.isMineActive()
                && mineMgr.isPlayerInsideMineZone(player.getLocation());

        if (!insideMine) {
            removeVisualBackpack(player);
            return;
        }

        UUID playerUuid = player.getUniqueId();
        UUID standUuid = activeBackVisualEntities.get(playerUuid);
        Entity existing = standUuid != null ? Bukkit.getEntity(standUuid) : null;

        Location backLoc = computePlayerBackLocation(player);

        if (!(existing instanceof ArmorStand stand) || !stand.isValid() || !stand.getWorld().equals(player.getWorld())) {
            if (existing != null) {
                existing.remove();
            }
            ArmorStand spawned = spawnVisualBackpackStand(player, backLoc);
            activeBackVisualEntities.put(playerUuid, spawned.getUniqueId());
        } else {
            stand.teleport(backLoc);
        }
    }

    private Location computePlayerBackLocation(Player player) {
        Location pLoc = player.getLocation();
        float bodyYaw = pLoc.getYaw();
        double rad = Math.toRadians(bodyYaw);
        // Направление взгляда по горизонтали: (-sin(yaw), cos(yaw))
        // Спина находится строго позади игрока (-0.30 блока назад)
        double backX = Math.sin(rad) * 0.30;
        double backZ = -Math.cos(rad) * 0.30;
        double yOffset = player.isSneaking() ? -0.65 : -0.45;

        Location loc = new Location(
                pLoc.getWorld(),
                pLoc.getX() + backX,
                pLoc.getY() + yOffset,
                pLoc.getZ() + backZ,
                bodyYaw,
                0.0f
        );
        return loc;
    }

    @SuppressWarnings("deprecation")
    private ArmorStand spawnVisualBackpackStand(Player owner, Location backLoc) {
        int lvl = getBackpackLevel(owner);
        return backLoc.getWorld().spawn(backLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSmall(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.setHeadPose(new EulerAngle(0, 0, 0));
            stand.getPersistentDataContainer().set(visualBackpackKey, PersistentDataType.BYTE, (byte) 1);

            EntityEquipment eq = stand.getEquipment();
            if (eq != null) {
                // TURTLE_HELMET на невидимой стойке рендерит кастомную 3D-геометрию рюкзака из GeyserEvent-Pack.mcpack
                ItemStack backpackModelItem = new ItemStack(Material.TURTLE_HELMET, 1);
                ItemMeta meta = backpackModelItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ColorUtil.color(LEVEL_TITLES[lvl]));
                    if (lvl >= 4) {
                        Enchantment unb = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
                        if (unb != null) {
                            meta.addEnchant(unb, 1, true);
                        }
                    }
                    backpackModelItem.setItemMeta(meta);
                }
                eq.setHelmet(backpackModelItem);
            }
        });
    }

    public void removeVisualBackpack(Player player) {
        if (player == null) {
            return;
        }
        UUID standUuid = activeBackVisualEntities.remove(player.getUniqueId());
        if (standUuid != null) {
            Entity e = Bukkit.getEntity(standUuid);
            if (e != null) {
                e.remove();
            }
        }
    }

    public void clearAllVisualBackpacks() {
        if (visualSyncTask != null) {
            visualSyncTask.cancel();
            visualSyncTask = null;
        }
        for (UUID standUuid : activeBackVisualEntities.values()) {
            Entity e = Bukkit.getEntity(standUuid);
            if (e != null) {
                e.remove();
            }
        }
        activeBackVisualEntities.clear();
    }

    @SuppressWarnings("deprecation")
    private ItemStack createButton(Material material, String name, List<String> loreLines) {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(name));
            List<String> colored = new ArrayList<>();
            for (String line : loreLines) {
                colored.add(ColorUtil.color(line));
            }
            meta.setLore(colored);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
