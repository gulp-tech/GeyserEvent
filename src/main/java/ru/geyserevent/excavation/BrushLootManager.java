package ru.geyserevent.excavation;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.util.ColorUtil;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Менеджер предметов и независимых таблиц лута для трёх ивентов:
 * 1) Кисточки «Пляжных Раскопок» (excavation.brushes.<tier>)
 * 2) Реагенты «Котла Алхимика» (cauldron.reagents.<tier>)
 * 3) Древние жеоды постоянного ивента «Шахта Древних» (mine.geodes.<tier>)
 * Полностью настраивается через GUI-меню (/rift menu, /cauldronmenu, /minemenu), команды и config.yml.
 */
public class BrushLootManager {

    public enum LootCategory {
        EXCAVATION("excavation.brushes."),
        CAULDRON("cauldron.reagents."),
        MINE("mine.geodes."),
        FISHING("fishing.rods.");

        private final String configPrefix;

        LootCategory(String configPrefix) {
            this.configPrefix = configPrefix;
        }

        public String getConfigPrefix() {
            return configPrefix;
        }
    }

    public record BrushLootEntry(ItemStack item, double chance) {
    }

    private final GeyserEventPlugin plugin;
    private final NamespacedKey tierKey;
    private final NamespacedKey usesKey;
    private final NamespacedKey reagentKey;
    private final NamespacedKey geodeKey;
    private final NamespacedKey fishingKey;
    private final NamespacedKey fishingUsesKey;

    // 1. Таблицы лута и шансов для «Пляжных Раскопок» (Кисточки)
    private final Map<BrushTier, List<BrushLootEntry>> lootByTier = new EnumMap<>(BrushTier.class);
    private final Map<BrushTier, Double> brushWeights = new EnumMap<>(BrushTier.class);
    private final Map<BrushTier, Integer> brushUses = new EnumMap<>(BrushTier.class);

    // 2. Отдельные таблицы лута и шансов для «Котла Алхимика» (Реагенты)
    private final Map<BrushTier, List<BrushLootEntry>> cauldronLootByTier = new EnumMap<>(BrushTier.class);
    private final Map<BrushTier, Double> reagentWeights = new EnumMap<>(BrushTier.class);

    // 3. Отдельные таблицы лута и шансов для постоянного ивента «Шахта Древних» (Древние жеоды)
    private final Map<BrushTier, List<BrushLootEntry>> mineLootByTier = new EnumMap<>(BrushTier.class);
    private final Map<BrushTier, Double> geodeWeights = new EnumMap<>(BrushTier.class);
    private final Map<BrushTier, List<BrushLootEntry>> fishingLootByTier = new EnumMap<>(BrushTier.class);
    private final Map<BrushTier, Double> fishingWeights = new EnumMap<>(BrushTier.class);
    private final Map<BrushTier, Integer> fishingUses = new EnumMap<>(BrushTier.class);

    public BrushLootManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.tierKey = new NamespacedKey(plugin, "brush_tier");
        this.usesKey = new NamespacedKey(plugin, "brush_uses");
        this.reagentKey = new NamespacedKey(plugin, "alchemist_reagent");
        this.geodeKey = new NamespacedKey(plugin, "ancient_geode");
        this.fishingKey = new NamespacedKey(plugin, "fishing_rod");
        this.fishingUsesKey = new NamespacedKey(plugin, "fishing_uses");
        reload();
    }

    public final void reload() {
        FileConfiguration cfg = plugin.getConfig();
        lootByTier.clear();
        cauldronLootByTier.clear();
        mineLootByTier.clear();
        fishingLootByTier.clear();

        // Авто-обновление старых шансов (65/25/10 -> 35/40/25)
        double cChance = cfg.getDouble("excavation.brushes.common.chance", 35.0);
        double eChance = cfg.getDouble("excavation.brushes.epic.chance", 40.0);
        double lChance = cfg.getDouble("excavation.brushes.legendary.chance", 25.0);
        if (Math.abs(cChance - 65.0) < 0.01 && Math.abs(eChance - 25.0) < 0.01 && Math.abs(lChance - 10.0) < 0.01) {
            cfg.set("excavation.brushes.common.chance", 35.0);
            cfg.set("excavation.brushes.epic.chance", 40.0);
            cfg.set("excavation.brushes.legendary.chance", 25.0);
            plugin.saveConfig();
        }

        for (BrushTier tier : BrushTier.values()) {
            double defaultWeight = switch (tier) {
                case COMMON -> 35.0;
                case EPIC -> 40.0;
                case LEGENDARY -> 25.0;
            };

            // 1. Загрузка лута Кисточек («Пляжные Раскопки»)
            String brushBasePath = "excavation.brushes." + tier.getConfigKey();
            brushWeights.put(tier, Math.max(1.0, cfg.getDouble(brushBasePath + ".chance", defaultWeight)));
            brushUses.put(tier, Math.max(1, cfg.getInt(brushBasePath + ".uses", tier.getDefaultUses())));

            List<BrushLootEntry> brushEntries = loadLootListFromConfig(cfg, brushBasePath + ".loot");
            if (brushEntries.isEmpty()) {
                brushEntries.addAll(getDefaultLootForTier(tier));
            }
            lootByTier.put(tier, brushEntries);

            // 2. Загрузка отдельного лута Реагентов («Котёл Алхимика»)
            String reagentBasePath = "cauldron.reagents." + tier.getConfigKey();
            reagentWeights.put(tier, Math.max(1.0, cfg.getDouble(reagentBasePath + ".chance", defaultWeight)));

            List<BrushLootEntry> cauldronEntries = loadLootListFromConfig(cfg, reagentBasePath + ".loot");
            if (cauldronEntries.isEmpty()) {
                cauldronEntries.addAll(getDefaultLootForTier(tier));
            }
            cauldronLootByTier.put(tier, cauldronEntries);

            // 3. Загрузка отдельного лута Древних Жеод («Шахта Древних»)
            String geodeBasePath = "mine.geodes." + tier.getConfigKey();
            geodeWeights.put(tier, Math.max(1.0, cfg.getDouble(geodeBasePath + ".chance", defaultWeight)));

            List<BrushLootEntry> mineEntries = loadLootListFromConfig(cfg, geodeBasePath + ".loot");
            if (mineEntries.isEmpty()) {
                mineEntries.addAll(getDefaultLootForTier(tier));
            }
            mineLootByTier.put(tier, mineEntries);

            String fishingBasePath = "fishing.rods." + tier.getConfigKey();
            fishingWeights.put(tier, Math.max(1.0, cfg.getDouble(fishingBasePath + ".chance", defaultWeight)));
            fishingUses.put(tier, Math.max(1, cfg.getInt(fishingBasePath + ".uses", tier.getDefaultUses() + 2)));
            List<BrushLootEntry> fishingEntries = loadLootListFromConfig(cfg, fishingBasePath + ".loot");
            if (fishingEntries.isEmpty()) {
                fishingEntries.addAll(getDefaultFishingLootForTier(tier));
            }
            fishingLootByTier.put(tier, fishingEntries);
        }
    }

    private List<BrushLootEntry> loadLootListFromConfig(FileConfiguration cfg, String path) {
        List<BrushLootEntry> entries = new ArrayList<>();
        List<Map<?, ?>> rawList = cfg.getMapList(path);
        for (Map<?, ?> map : rawList) {
            double chance = toDouble(map.get("chance"), 25.0);
            Object serializedStack = map.get("item");
            if (serializedStack instanceof ItemStack stack && !stack.getType().isAir()) {
                entries.add(new BrushLootEntry(stack.clone(), chance));
                continue;
            }
            String matName = String.valueOf(map.get("material"));
            Material mat = Material.matchMaterial(matName);
            if (mat != null && !mat.isAir()) {
                int amount = Math.max(1, toInt(map.get("amount"), 1));
                ItemStack stack = new ItemStack(mat, amount);
                Object nameObj = map.get("name");
                Object loreObj = map.get("lore");
                List<String> lore = new ArrayList<>();
                if (loreObj instanceof List<?> loreList) {
                    for (Object line : loreList) {
                        lore.add(ColorUtil.color(String.valueOf(line)));
                    }
                }
                String name = nameObj != null ? ColorUtil.color(String.valueOf(nameObj)) : null;
                ru.geyserevent.util.LootStacks.apply(stack, name, lore, ru.geyserevent.util.LootStacks.readEnchants(map.get("enchants")));
                entries.add(new BrushLootEntry(stack, chance));
            }
        }
        return entries;
    }

    private List<BrushLootEntry> getDefaultLootForTier(BrushTier tier) {
        List<BrushLootEntry> list = new ArrayList<>();
        switch (tier) {
            case COMMON -> {
                list.add(new BrushLootEntry(new ItemStack(Material.GOLD_INGOT, 4), 45.0));
                list.add(new BrushLootEntry(new ItemStack(Material.IRON_INGOT, 6), 50.0));
                list.add(new BrushLootEntry(new ItemStack(Material.EMERALD, 3), 40.0));
                list.add(new BrushLootEntry(new ItemStack(Material.DIAMOND, 2), 25.0));
                list.add(new BrushLootEntry(new ItemStack(Material.GOLDEN_APPLE, 1), 20.0));
                list.add(new BrushLootEntry(new ItemStack(Material.EXPERIENCE_BOTTLE, 8), 35.0));
            }
            case EPIC -> {
                list.add(new BrushLootEntry(new ItemStack(Material.DIAMOND, 5), 45.0));
                list.add(new BrushLootEntry(new ItemStack(Material.GOLD_BLOCK, 2), 40.0));
                list.add(new BrushLootEntry(new ItemStack(Material.GOLDEN_APPLE, 3), 35.0));
                list.add(new BrushLootEntry(new ItemStack(Material.EMERALD_BLOCK, 2), 30.0));
                list.add(new BrushLootEntry(new ItemStack(Material.NETHERITE_SCRAP, 1), 18.0));
                list.add(new BrushLootEntry(new ItemStack(Material.TOTEM_OF_UNDYING, 1), 15.0));
                list.add(new BrushLootEntry(new ItemStack(Material.EXPERIENCE_BOTTLE, 16), 35.0));
            }
            case LEGENDARY -> {
                list.add(new BrushLootEntry(new ItemStack(Material.NETHERITE_INGOT, 1), 25.0));
                list.add(new BrushLootEntry(new ItemStack(Material.ANCIENT_DEBRIS, 2), 35.0));
                list.add(new BrushLootEntry(new ItemStack(Material.DIAMOND_BLOCK, 2), 40.0));
                list.add(new BrushLootEntry(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 15.0));
                list.add(new BrushLootEntry(new ItemStack(Material.TOTEM_OF_UNDYING, 1), 30.0));
                list.add(new BrushLootEntry(new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1), 25.0));
                list.add(new BrushLootEntry(new ItemStack(Material.NETHERITE_PICKAXE, 1), 10.0));
            }
        }
        return list;
    }

    public BrushTier rollRandomBrushTier() {
        return rollRandomTierFromMap(brushWeights);
    }

    public BrushTier rollRandomReagentTier() {
        return rollRandomTierFromMap(reagentWeights);
    }

    public BrushTier rollRandomGeodeTier() {
        return rollRandomTierFromMap(geodeWeights);
    }

    private BrushTier rollRandomTierFromMap(Map<BrushTier, Double> weights) {
        double wCommon = weights.getOrDefault(BrushTier.COMMON, 35.0);
        double wEpic = weights.getOrDefault(BrushTier.EPIC, 40.0);
        double wLeg = weights.getOrDefault(BrushTier.LEGENDARY, 25.0);
        double total = wCommon + wEpic + wLeg;

        double roll = ThreadLocalRandom.current().nextDouble(0.0, total);
        if (roll < wCommon) {
            return BrushTier.COMMON;
        } else if (roll < wCommon + wEpic) {
            return BrushTier.EPIC;
        } else {
            return BrushTier.LEGENDARY;
        }
    }

    private Map<BrushTier, List<BrushLootEntry>> getLootMap(LootCategory cat) {
        return switch (cat) {
            case EXCAVATION -> lootByTier;
            case CAULDRON -> cauldronLootByTier;
            case MINE -> mineLootByTier;
            case FISHING -> fishingLootByTier;
        };
    }

    private Map<BrushTier, Double> getWeightsMap(LootCategory cat) {
        return switch (cat) {
            case EXCAVATION -> brushWeights;
            case CAULDRON -> reagentWeights;
            case MINE -> geodeWeights;
            case FISHING -> fishingWeights;
        };
    }

    public double getBrushWeight(BrushTier tier) {
        return getTierWeight(tier, LootCategory.EXCAVATION);
    }

    public double getTierWeight(BrushTier tier, boolean cauldron) {
        return getTierWeight(tier, cauldron ? LootCategory.CAULDRON : LootCategory.EXCAVATION);
    }

    public double getTierWeight(BrushTier tier, LootCategory category) {
        return getWeightsMap(category).getOrDefault(tier, 35.0);
    }

    public void setBrushWeight(BrushTier tier, double weight) {
        setTierWeight(tier, weight, LootCategory.EXCAVATION);
    }

    public void setTierWeight(BrushTier tier, double weight, boolean cauldron) {
        setTierWeight(tier, weight, cauldron ? LootCategory.CAULDRON : LootCategory.EXCAVATION);
    }

    public void setTierWeight(BrushTier tier, double weight, LootCategory category) {
        double clamped = Math.max(1.0, Math.min(500.0, Math.round(weight * 10.0) / 10.0));
        getWeightsMap(category).put(tier, clamped);
        plugin.getConfig().set(category.getConfigPrefix() + tier.getConfigKey() + ".chance", clamped);
        plugin.saveConfig();
    }

    /**
     * Создаёт предмет кисточки указанной редкости для ивента «Пляжные Раскопки».
     */
    @SuppressWarnings("deprecation")
    public ItemStack createBrushItem(BrushTier tier) {
        Material mat = Material.matchMaterial("BRUSH");
        if (mat == null) {
            mat = Material.FEATHER;
        }
        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            int uses = brushUses.getOrDefault(tier, tier.getDefaultUses());
            String title = plugin.getConfig().getString(
                    "excavation.brushes." + tier.getConfigKey() + ".name",
                    tier.getItemTitle()
            );
            meta.setDisplayName(ColorUtil.color(title));

            List<String> lore = List.of(
                    ColorUtil.color("&7Редкость: " + tier.getItemTitle()),
                    ColorUtil.color("&7Зарядов раскопок: &e" + uses),
                    ColorUtil.color("&8Нажмите по песку на пляже")
            );
            meta.setLore(lore);

            if (tier == BrushTier.EPIC || tier == BrushTier.LEGENDARY) {
                Enchantment unb = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
                if (unb != null) {
                    meta.addEnchant(unb, 1, true);
                }
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(tierKey, PersistentDataType.STRING, tier.name());
            pdc.set(usesKey, PersistentDataType.INTEGER, uses);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Создаёт нестабильный Алхимический Реагент указанной редкости для ивента «Котёл Алхимика».
     */
    @SuppressWarnings("deprecation")
    public ItemStack createReagentItem(BrushTier tier) {
        Material mat = switch (tier) {
            case COMMON -> Material.SLIME_BALL;
            case EPIC -> Material.AMETHYST_SHARD;
            case LEGENDARY -> Material.BLAZE_POWDER;
        };
        String title = getReagentDisplayName(tier);

        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(title));
            List<String> lore = List.of(
                    ColorUtil.color("&7Ивент: &dКотёл Алхимика"),
                    ColorUtil.color("&8Нажмите по Алхимику или Котлу в центре,"),
                    ColorUtil.color("&8чтобы сварить предмет этой редкости!")
            );
            meta.setLore(lore);

            Enchantment unb = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
            if (unb != null) {
                meta.addEnchant(unb, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(tierKey, PersistentDataType.STRING, tier.name());
            pdc.set(reagentKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(usesKey, PersistentDataType.INTEGER, 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Создаёт Древнюю Жеоду указанной редкости для постоянного ивента «Шахта Древних».
     */
    @SuppressWarnings("deprecation")
    public ItemStack createGeodeItem(BrushTier tier) {
        Material mat = switch (tier) {
            case COMMON -> Material.QUARTZ;
            case EPIC -> {
                Material echo = Material.matchMaterial("ECHO_SHARD");
                yield echo != null ? echo : Material.PRISMARINE_SHARD;
            }
            case LEGENDARY -> Material.MAGMA_CREAM;
        };
        String title = getGeodeDisplayName(tier);

        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(title));
            List<String> lore = List.of(
                    ColorUtil.color("&7Ивент: &#FFB81CШахта Древних (0 ~ 0)"),
                    ColorUtil.color("&8Нажмите по Древнему Кузнецу или наковальне,"),
                    ColorUtil.color("&8чтобы расколоть жеоду и забрать артефакт!")
            );
            meta.setLore(lore);

            Enchantment unb = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
            if (unb != null) {
                meta.addEnchant(unb, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(tierKey, PersistentDataType.STRING, tier.name());
            pdc.set(geodeKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(usesKey, PersistentDataType.INTEGER, 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public String getReagentDisplayName(BrushTier tier) {
        return switch (tier) {
            case COMMON -> "&aОбычный реагент &7(Болотный Спороцвет)";
            case EPIC -> "&dЭпический реагент &7(Кристалл Бездны)";
            case LEGENDARY -> "&6Легендарный реагент &7(Сердце Пламени)";
        };
    }

    public String getGeodeDisplayName(BrushTier tier) {
        return switch (tier) {
            case COMMON -> "&aОбычная жеода &7(Кварцевая жеода)";
            case EPIC -> "&dЭпическая жеода &7(Аметистовая жеода)";
            case LEGENDARY -> "&6Легендарная жеода &7(Магмовая жеода)";
        };
    }

    public String getCategoryItemTitle(BrushTier tier, LootCategory category) {
        return switch (category) {
            case EXCAVATION -> tier.getItemTitle();
            case CAULDRON -> getReagentDisplayName(tier);
            case MINE -> getGeodeDisplayName(tier);
            case FISHING -> getFishingRodTitle(tier);
        };
    }

    public static final int FISHING_ROD_CMD_COMMON = 27101;
    public static final int FISHING_ROD_CMD_EPIC = 27102;
    public static final int FISHING_ROD_CMD_LEGENDARY = 27103;

    public String getFishingRodTitle(BrushTier tier) {
        return switch (tier) {
            case COMMON -> "&aМедная удочка удачи";
            case EPIC -> "&dЗолотая удочка удачи";
            case LEGENDARY -> "&6Кристальная удочка удачи";
        };
    }

    public int fishingRodCustomModelData(BrushTier tier) {
        return switch (tier) {
            case COMMON -> FISHING_ROD_CMD_COMMON;
            case EPIC -> FISHING_ROD_CMD_EPIC;
            case LEGENDARY -> FISHING_ROD_CMD_LEGENDARY;
        };
    }

    public BrushTier rollRandomFishingRodTier() {
        return rollRandomTierFromMap(fishingWeights);
    }

    /**
     * Ванильная удочка + PDC. Модель не подменяется: обычная удочка без метки остаётся ванильной.
     */
    @SuppressWarnings("deprecation")
    public ItemStack createFishingRod(BrushTier tier) {
        ItemStack stack = new ItemStack(Material.FISHING_ROD, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            int uses = fishingUses.getOrDefault(tier, tier.getDefaultUses() + 2);
            String title = plugin.getConfig().getString(
                    "fishing.rods." + tier.getConfigKey() + ".name",
                    getFishingRodTitle(tier)
            );
            meta.setDisplayName(ColorUtil.color(title));
            meta.setLore(List.of(
                    ColorUtil.color("&7Редкость: " + getFishingRodTitle(tier)),
                    ColorUtil.color("&7Поклёвок: &e" + uses),
                    ColorUtil.color("&8Ловите в пруду ивента")
            ));
            if (tier == BrushTier.EPIC || tier == BrushTier.LEGENDARY) {
                Enchantment unb = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
                if (unb != null) {
                    meta.addEnchant(unb, 1, true);
                }
                Enchantment luck = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("luck_of_the_sea"));
                if (luck != null) {
                    meta.addEnchant(luck, tier == BrushTier.LEGENDARY ? 3 : 1, true);
                }
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(fishingKey, PersistentDataType.STRING, tier.name());
            pdc.set(fishingUsesKey, PersistentDataType.INTEGER, uses);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isFishingRod(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(fishingKey, PersistentDataType.STRING);
    }

    public BrushTier getFishingRodTier(ItemStack item) {
        if (!isFishingRod(item)) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(fishingKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return BrushTier.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public int countPlayerFishingRods(Player player) {
        if (player == null) {
            return 0;
        }
        int count = 0;
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getStorageContents()) {
            if (isFishingRod(item)) {
                count += item.getAmount();
            }
        }
        if (isFishingRod(inv.getItemInOffHand())) {
            count += inv.getItemInOffHand().getAmount();
        }
        if (isFishingRod(player.getItemOnCursor())) {
            count += player.getItemOnCursor().getAmount();
        }
        return count;
    }

    public boolean removeAllFishingRods(Player player) {
        if (player == null) {
            return false;
        }
        boolean removed = false;
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isFishingRod(contents[i])) {
                inv.setItem(i, null);
                removed = true;
            }
        }
        if (isFishingRod(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
            removed = true;
        }
        if (isFishingRod(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed = true;
        }
        return removed;
    }

    public void removeAllFishingRodsFromOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeAllFishingRods(player);
        }
    }

    @SuppressWarnings("deprecation")
    public void consumeFishingRodUse(Player player, ItemStack rod) {
        if (rod == null || !rod.hasItemMeta()) {
            return;
        }
        ItemMeta meta = rod.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int remaining = pdc.getOrDefault(fishingUsesKey, PersistentDataType.INTEGER, 1) - 1;
        BrushTier tier = getFishingRodTier(rod);
        if (remaining <= 0) {
            rod.setAmount(rod.getAmount() - 1);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } else {
            pdc.set(fishingUsesKey, PersistentDataType.INTEGER, remaining);
            if (tier != null) {
                meta.setLore(List.of(
                        ColorUtil.color("&7Редкость: " + getFishingRodTitle(tier)),
                        ColorUtil.color("&7Поклёвок: &e" + remaining),
                        ColorUtil.color("&8Ловите в пруду ивента")
                ));
            }
            rod.setItemMeta(meta);
        }
    }

    public ItemStack rollLootForFishing(BrushTier tier) {
        return rollLootFromList(fishingLootByTier.get(tier));
    }

    private List<BrushLootEntry> getDefaultFishingLootForTier(BrushTier tier) {
        List<BrushLootEntry> list = new ArrayList<>();
        switch (tier) {
            case COMMON -> {
                list.add(new BrushLootEntry(new ItemStack(Material.COD, 3), 45.0));
                list.add(new BrushLootEntry(new ItemStack(Material.SALMON, 2), 35.0));
                list.add(new BrushLootEntry(new ItemStack(Material.IRON_INGOT, 3), 25.0));
                list.add(new BrushLootEntry(new ItemStack(Material.PRISMARINE_SHARD, 2), 20.0));
            }
            case EPIC -> {
                list.add(new BrushLootEntry(new ItemStack(Material.SALMON, 4), 35.0));
                list.add(new BrushLootEntry(new ItemStack(Material.GOLD_INGOT, 4), 40.0));
                list.add(new BrushLootEntry(new ItemStack(Material.PRISMARINE_CRYSTALS, 3), 30.0));
                list.add(new BrushLootEntry(new ItemStack(Material.DIAMOND, 1), 18.0));
                list.add(new BrushLootEntry(new ItemStack(Material.NAUTILUS_SHELL, 1), 12.0));
            }
            case LEGENDARY -> {
                list.add(new BrushLootEntry(new ItemStack(Material.GOLDEN_APPLE, 1), 25.0));
                list.add(new BrushLootEntry(new ItemStack(Material.DIAMOND, 2), 35.0));
                list.add(new BrushLootEntry(new ItemStack(Material.HEART_OF_THE_SEA, 1), 15.0));
                list.add(new BrushLootEntry(new ItemStack(Material.EMERALD, 6), 30.0));
                list.add(new BrushLootEntry(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 6.0));
            }
        }
        return list;
    }

    public boolean isReagentItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(reagentKey, PersistentDataType.BYTE);
    }

    public boolean isGeodeItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(geodeKey, PersistentDataType.BYTE);
    }

    public BrushTier getGeodeTier(ItemStack item) {
        if (!isGeodeItem(item)) {
            return null;
        }
        return getBrushTier(item);
    }

    public boolean hasAnyReagent(Player player) {
        if (player == null) {
            return false;
        }
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isReagentItem(item)) {
                return true;
            }
        }
        return isReagentItem(player.getInventory().getItemInOffHand());
    }

    public BrushTier findAndConsumeReagent(Player player) {
        if (player == null) {
            return null;
        }
        PlayerInventory inv = player.getInventory();

        ItemStack main = inv.getItemInMainHand();
        if (isReagentItem(main)) {
            BrushTier tier = getBrushTier(main);
            main.setAmount(main.getAmount() - 1);
            return tier;
        }

        ItemStack off = inv.getItemInOffHand();
        if (isReagentItem(off)) {
            BrushTier tier = getBrushTier(off);
            off.setAmount(off.getAmount() - 1);
            return tier;
        }

        BrushTier[] priority = {BrushTier.LEGENDARY, BrushTier.EPIC, BrushTier.COMMON};
        ItemStack[] contents = inv.getStorageContents();
        for (BrushTier targetTier : priority) {
            for (int i = 0; i < contents.length; i++) {
                ItemStack st = contents[i];
                if (isReagentItem(st) && getBrushTier(st) == targetTier) {
                    if (st.getAmount() <= 1) {
                        inv.setItem(i, null);
                    } else {
                        st.setAmount(st.getAmount() - 1);
                    }
                    return targetTier;
                }
            }
        }
        return null;
    }

    /**
     * Определяет редкость кисточки, реагента или древней жеоды (или null, если это обычный предмет).
     */
    public BrushTier getBrushTier(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return BrushTier.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Проверяет, относится ли предмет к временному ивенту (Кисточка или Реагент, но не Жеода Шахты).
     */
    public boolean isTemporaryEventItem(ItemStack item) {
        return (getBrushTier(item) != null && !isGeodeItem(item)) || isFishingRod(item);
    }

    /**
     * Подсчитывает общее количество ивентовых предметов в инвентаре игрока (максимум разрешено держать 3 шт.).
     */
    public int countPlayerBrushes(Player player) {
        if (player == null) {
            return 0;
        }
        int count = 0;
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getStorageContents()) {
            if (getBrushTier(item) != null) {
                count += item.getAmount();
            }
        }
        ItemStack off = inv.getItemInOffHand();
        if (getBrushTier(off) != null) {
            count += off.getAmount();
        }
        ItemStack cursor = player.getItemOnCursor();
        if (getBrushTier(cursor) != null) {
            count += cursor.getAmount();
        }
        return count;
    }

    public boolean hasAnyEventBrush(Player player) {
        return countPlayerBrushes(player) > 0;
    }

    /**
     * Удаляет только предметы временного ивента (Кисточки и Реагенты), когда игрок покидает зону временного ивента.
     */
    public boolean removeAllTemporaryEventItems(Player player) {
        if (player == null) {
            return false;
        }
        boolean removed = false;
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isTemporaryEventItem(contents[i])) {
                inv.setItem(i, null);
                removed = true;
            }
        }
        if (isTemporaryEventItem(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
            removed = true;
        }
        if (isTemporaryEventItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed = true;
        }
        return removed;
    }

    /**
     * Удаляет все Древние жеоды, когда игрок покидает зону «Шахты Древних» (0 ~ 0).
     */
    public boolean removeAllPlayerGeodes(Player player) {
        if (player == null) {
            return false;
        }
        boolean removed = false;
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isGeodeItem(contents[i])) {
                inv.setItem(i, null);
                removed = true;
            }
        }
        if (isGeodeItem(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
            removed = true;
        }
        if (isGeodeItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed = true;
        }
        return removed;
    }

    /**
     * Удаляет абсолютно все ивентовые предметы (Кисточки, Реагенты, Жеоды) — например, при выходе с сервера.
     */
    public boolean removeAllEventBrushes(Player player) {
        if (player == null) {
            return false;
        }
        boolean removed = false;
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (getBrushTier(contents[i]) != null) {
                inv.setItem(i, null);
                removed = true;
            }
        }
        if (getBrushTier(inv.getItemInOffHand()) != null) {
            inv.setItemInOffHand(null);
            removed = true;
        }
        if (getBrushTier(player.getItemOnCursor()) != null) {
            player.setItemOnCursor(null);
            removed = true;
        }
        return removed;
    }

    /**
     * Удаляет кисточки и реагенты из инвентарей всех игроков онлайн.
     *
     * <p><b>Оптимизация (v1.9.7):</b> раньше здесь дополнительно перебирались ВСЕ предметы
     * ВО ВСЕХ мирах сервера (с копией ItemMeta на каждый — большая просадка при окончании ивента).
     * Предметы на земле теперь чистятся через реестр отслеживаемых ивентовых предметов
     * ({@code InfernalRiftManager#purgeTrackedEventItems(boolean)}), а инвентарь игрока
     * и так проверяется каждые полсекунды.</p>
     */
    public void removeAllEventBrushesFromOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeAllTemporaryEventItems(player);
        }
    }

    @SuppressWarnings("deprecation")
    public void consumeBrushUse(Player player, ItemStack brush) {
        if (brush == null || !brush.hasItemMeta()) {
            return;
        }
        ItemMeta meta = brush.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int remaining = pdc.getOrDefault(usesKey, PersistentDataType.INTEGER, 1) - 1;
        BrushTier tier = getBrushTier(brush);

        if (remaining <= 0) {
            brush.setAmount(brush.getAmount() - 1);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } else {
            pdc.set(usesKey, PersistentDataType.INTEGER, remaining);
            if (tier != null) {
                meta.setLore(List.of(
                        ColorUtil.color("&7Редкость: " + tier.getItemTitle()),
                        ColorUtil.color("&7Зарядов раскопок: &e" + remaining),
                        ColorUtil.color("&8Нажмите по песку на пляже")
                ));
            }
            brush.setItemMeta(meta);
        }
    }

    public ItemStack rollLootForBrush(BrushTier tier) {
        return rollLootFromList(lootByTier.get(tier));
    }

    public ItemStack rollLootForCauldron(BrushTier tier) {
        return rollLootFromList(cauldronLootByTier.get(tier));
    }

    public ItemStack rollLootForMine(BrushTier tier) {
        return rollLootFromList(mineLootByTier.get(tier));
    }

    private ItemStack rollLootFromList(List<BrushLootEntry> list) {
        if (list == null || list.isEmpty()) {
            return new ItemStack(Material.GOLD_INGOT, 2);
        }

        double totalWeight = 0.0;
        for (BrushLootEntry entry : list) {
            totalWeight += Math.max(0.1, entry.chance());
        }

        double roll = ThreadLocalRandom.current().nextDouble(0.0, totalWeight);
        double current = 0.0;
        for (BrushLootEntry entry : list) {
            current += Math.max(0.1, entry.chance());
            if (roll <= current) {
                return entry.item().clone();
            }
        }
        return list.get(0).item().clone();
    }

    public void addLootItem(BrushTier tier, ItemStack item, double chance) {
        addLootItem(tier, item, chance, LootCategory.EXCAVATION);
    }

    public void addLootItem(BrushTier tier, ItemStack item, double chance, boolean cauldron) {
        addLootItem(tier, item, chance, cauldron ? LootCategory.CAULDRON : LootCategory.EXCAVATION);
    }

    public void addLootItem(BrushTier tier, ItemStack item, double chance, LootCategory category) {
        List<BrushLootEntry> list = getLootMap(category).computeIfAbsent(tier, k -> new ArrayList<>());
        list.add(new BrushLootEntry(item.clone(), Math.max(0.5, chance)));
        saveTierLootToConfig(tier, list, category);
    }

    public void updateLootEntryChance(BrushTier tier, int index0Based, double newChance) {
        updateLootEntryChance(tier, index0Based, newChance, LootCategory.EXCAVATION);
    }

    public void updateLootEntryChance(BrushTier tier, int index0Based, double newChance, boolean cauldron) {
        updateLootEntryChance(tier, index0Based, newChance, cauldron ? LootCategory.CAULDRON : LootCategory.EXCAVATION);
    }

    public void updateLootEntryChance(BrushTier tier, int index0Based, double newChance, LootCategory category) {
        List<BrushLootEntry> list = getLootMap(category).get(tier);
        if (list == null || index0Based < 0 || index0Based >= list.size()) {
            return;
        }
        double clamped = Math.max(0.5, Math.min(100.0, Math.round(newChance * 10.0) / 10.0));
        BrushLootEntry old = list.get(index0Based);
        list.set(index0Based, new BrushLootEntry(old.item(), clamped));
        saveTierLootToConfig(tier, list, category);
    }

    public boolean removeLootItem(BrushTier tier, int index1Based) {
        return removeLootItem(tier, index1Based, LootCategory.EXCAVATION);
    }

    public boolean removeLootItem(BrushTier tier, int index1Based, boolean cauldron) {
        return removeLootItem(tier, index1Based, cauldron ? LootCategory.CAULDRON : LootCategory.EXCAVATION);
    }

    public boolean removeLootItem(BrushTier tier, int index1Based, LootCategory category) {
        List<BrushLootEntry> list = getLootMap(category).get(tier);
        if (list == null || index1Based < 1 || index1Based > list.size()) {
            return false;
        }
        list.remove(index1Based - 1);
        saveTierLootToConfig(tier, list, category);
        return true;
    }

    public void clearLoot(BrushTier tier) {
        clearLoot(tier, LootCategory.EXCAVATION);
    }

    public void clearLoot(BrushTier tier, boolean cauldron) {
        clearLoot(tier, cauldron ? LootCategory.CAULDRON : LootCategory.EXCAVATION);
    }

    public void clearLoot(BrushTier tier, LootCategory category) {
        List<BrushLootEntry> list = getLootMap(category).computeIfAbsent(tier, k -> new ArrayList<>());
        list.clear();
        saveTierLootToConfig(tier, list, category);
    }

    public List<BrushLootEntry> getLootEntries(BrushTier tier) {
        return getLootEntries(tier, LootCategory.EXCAVATION);
    }

    public List<BrushLootEntry> getLootEntries(BrushTier tier, boolean cauldron) {
        return getLootEntries(tier, cauldron ? LootCategory.CAULDRON : LootCategory.EXCAVATION);
    }

    public List<BrushLootEntry> getLootEntries(BrushTier tier, LootCategory category) {
        return getLootMap(category).getOrDefault(tier, List.of());
    }

    /**
     * Копирует таблицу лута с одного ивента на другой (предметы и шансы).
     * {@code onlyTier == null} — все три редкости.
     * @return сколько предметов записано, или -1 если from == to
     */
    public int copyLoot(LootCategory from, LootCategory to, BrushTier onlyTier) {
        if (from == null || to == null || from == to) {
            return -1;
        }
        int copied = 0;
        BrushTier[] tiers = onlyTier != null ? new BrushTier[]{onlyTier} : BrushTier.values();
        for (BrushTier tier : tiers) {
            List<BrushLootEntry> dest = new ArrayList<>();
            for (BrushLootEntry entry : getLootEntries(tier, from)) {
                dest.add(new BrushLootEntry(entry.item().clone(), entry.chance()));
            }
            getLootMap(to).put(tier, dest);
            saveTierLootToConfig(tier, dest, to);
            copied += dest.size();
        }
        return copied;
    }

    private void saveTierLootToConfig(BrushTier tier, List<BrushLootEntry> entries, LootCategory category) {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (BrushLootEntry e : entries) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("chance", e.chance());
            if (!e.item().hasItemMeta()) {
                map.put("material", e.item().getType().name());
                map.put("amount", e.item().getAmount());
            } else {
                map.put("item", e.item());
            }
            serialized.add(map);
        }
        String path = category.getConfigPrefix() + tier.getConfigKey() + ".loot";
        plugin.getConfig().set(path, serialized);
        plugin.saveConfig();
    }

    private int toInt(Object obj, int def) {
        if (obj instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception ex) {
            return def;
        }
    }

    private double toDouble(Object obj, double def) {
        if (obj instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (Exception ex) {
            return def;
        }
    }
}
