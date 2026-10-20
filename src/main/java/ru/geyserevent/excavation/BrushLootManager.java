package ru.geyserevent.excavation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
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
 * Менеджер кисточек раскопок (Обычная, Эпическая, Легендарная) и их таблиц лута.
 * Позволяет настраивать лут как через config.yml, так и командами прямо из игры.
 */
public class BrushLootManager {

    public record BrushLootEntry(ItemStack item, double chance) {
    }

    private final GeyserEventPlugin plugin;
    private final NamespacedKey tierKey;
    private final NamespacedKey usesKey;

    private final Map<BrushTier, List<BrushLootEntry>> lootByTier = new EnumMap<>(BrushTier.class);
    private final Map<BrushTier, Double> brushWeights = new EnumMap<>(BrushTier.class);
    private final Map<BrushTier, Integer> brushUses = new EnumMap<>(BrushTier.class);

    public BrushLootManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.tierKey = new NamespacedKey(plugin, "brush_tier");
        this.usesKey = new NamespacedKey(plugin, "brush_uses");
        reload();
    }

    public final void reload() {
        FileConfiguration cfg = plugin.getConfig();
        lootByTier.clear();

        for (BrushTier tier : BrushTier.values()) {
            String basePath = "excavation.brushes." + tier.getConfigKey();
            double defaultWeight = switch (tier) {
                case COMMON -> 65.0;
                case EPIC -> 25.0;
                case LEGENDARY -> 10.0;
            };
            brushWeights.put(tier, Math.max(0.1, cfg.getDouble(basePath + ".chance", defaultWeight)));
            brushUses.put(tier, Math.max(1, cfg.getInt(basePath + ".uses", tier.getDefaultUses())));

            List<BrushLootEntry> entries = new ArrayList<>();
            List<Map<?, ?>> rawList = cfg.getMapList(basePath + ".loot");
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
                    entries.add(new BrushLootEntry(new ItemStack(mat, amount), chance));
                }
            }

            if (entries.isEmpty()) {
                entries.addAll(getDefaultLootForTier(tier));
            }
            lootByTier.put(tier, entries);
        }
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

    /**
     * Выбирает случайную редкость кисточки (Обычная, Эпическая, Легендарная) по шансам из конфига.
     */
    public BrushTier rollRandomBrushTier() {
        double wCommon = brushWeights.getOrDefault(BrushTier.COMMON, 65.0);
        double wEpic = brushWeights.getOrDefault(BrushTier.EPIC, 25.0);
        double wLeg = brushWeights.getOrDefault(BrushTier.LEGENDARY, 10.0);
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

    /**
     * Создаёт предмет кисточки указанной редкости.
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
     * Определяет редкость кисточки (или null, если это не кисточка ивента).
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
     * Проверяет, есть ли у игрока хотя бы одна кисточка раскопок в инвентаре, левой руке или на курсоре.
     */
    public boolean hasAnyEventBrush(Player player) {
        if (player == null) {
            return false;
        }
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (getBrushTier(item) != null) {
                return true;
            }
        }
        if (getBrushTier(inv.getItemInOffHand()) != null) {
            return true;
        }
        return getBrushTier(player.getItemOnCursor()) != null;
    }

    /**
     * Удаляет все кисточки ивента у игрока (при уходе далеко от пляжа, выходе с сервера или окончании ивента).
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
     * Удаляет кисточки ивента у всех игроков онлайн.
     */
    public void removeAllEventBrushesFromOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeAllEventBrushes(player);
        }
    }

    /**
     * Проверяет, есть ли сейчас на пляже хотя бы один игрок с активной кисточкой.
     * Пока кисточка есть — Житель находится в режиме ожидания («Ожидание»).
     */
    public boolean hasActiveBrushOnBeach(Location beachCenter, double maxRadius) {
        if (beachCenter == null || beachCenter.getWorld() == null) {
            return false;
        }
        World world = beachCenter.getWorld();
        double maxDistSq = maxRadius * maxRadius;
        for (Player player : world.getPlayers()) {
            if (player.isDead() || !player.isOnline()) {
                continue;
            }
            if (player.getLocation().distanceSquared(beachCenter) <= maxDistSq && hasAnyEventBrush(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Тратит 1 заряд кисточки при успешной раскопке.
     */
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

    /**
     * Выбирает случайный предмет из таблицы лута указанной кисточки.
     */
    public ItemStack rollLootForBrush(BrushTier tier) {
        List<BrushLootEntry> list = lootByTier.get(tier);
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

    /**
     * Добавляет предмет из руки администратора в таблицу лута указанной кисточки и сохраняет в config.yml.
     */
    public void addLootItem(BrushTier tier, ItemStack item, double chance) {
        List<BrushLootEntry> list = lootByTier.computeIfAbsent(tier, k -> new ArrayList<>());
        list.add(new BrushLootEntry(item.clone(), Math.max(0.1, chance)));
        saveTierLootToConfig(tier, list);
    }

    /**
     * Удаляет предмет по номеру (начиная с 1) из таблицы лута указанной кисточки.
     */
    public boolean removeLootItem(BrushTier tier, int index1Based) {
        List<BrushLootEntry> list = lootByTier.get(tier);
        if (list == null || index1Based < 1 || index1Based > list.size()) {
            return false;
        }
        list.remove(index1Based - 1);
        saveTierLootToConfig(tier, list);
        return true;
    }

    /**
     * Очищает все предметы из таблицы лута указанной кисточки.
     */
    public void clearLoot(BrushTier tier) {
        List<BrushLootEntry> list = lootByTier.computeIfAbsent(tier, k -> new ArrayList<>());
        list.clear();
        saveTierLootToConfig(tier, list);
    }

    public List<BrushLootEntry> getLootEntries(BrushTier tier) {
        return lootByTier.getOrDefault(tier, List.of());
    }

    private void saveTierLootToConfig(BrushTier tier, List<BrushLootEntry> entries) {
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
        String path = "excavation.brushes." + tier.getConfigKey() + ".loot";
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
