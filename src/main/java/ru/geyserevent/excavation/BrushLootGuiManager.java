package ru.geyserevent.excavation;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.excavation.BrushLootManager.LootCategory;
import ru.geyserevent.util.ColorUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Удобное GUI-меню настройки лута и шансов для всех трёх ивентов с лутом:
 * - Главное меню (/rift menu, 45 слотов):
 *   1-й ряд: «Пляжные Раскопки» (Кисточки)
 *   2-й ряд: «Котёл Алхимика» (Реагенты)
 *   3-й ряд: «Шахта Древних (0 ~ 0)» (Древние жеоды)
 * - Отдельное меню «Шахты Древних» (/minemenu, /rift minemenu, /rift menu mine)
 * - Отдельное меню «Котла Алхимика» (/cauldronmenu, /rift cauldronmenu, /rift menu cauldron)
 * - Отдельное меню «Пляжных Раскопок» (/rift excavationmenu, /rift menu excavation)
 */
public class BrushLootGuiManager {

    public enum EditClickMode {
        PLUS_5("&a+5% к шансу предмета"),
        MINUS_5("&c-5% к шансу предмета"),
        PLUS_1("&e+1% к шансу предмета"),
        MINUS_1("&6-1% к шансу предмета"),
        DELETE("&4Удалить предмет из лута");

        private final String label;

        EditClickMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public EditClickMode next() {
            EditClickMode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    public static class MainMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class EventMenuHolder implements InventoryHolder {
        private final LootCategory category;

        public EventMenuHolder(LootCategory category) {
            this.category = category;
        }

        public LootCategory getCategory() {
            return category;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class TierMenuHolder implements InventoryHolder {
        private final BrushTier tier;
        private final LootCategory category;

        public TierMenuHolder(BrushTier tier, LootCategory category) {
            this.tier = tier;
            this.category = category;
        }

        public BrushTier getTier() {
            return tier;
        }

        public LootCategory getCategory() {
            return category;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class AlienMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final GeyserEventPlugin plugin;
    private final Map<UUID, EditClickMode> playerClickMode = new HashMap<>();

    public BrushLootGuiManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Открывает главное меню выбора ивента и редкости (45 слотов):
     * - Слоты 11, 13, 15: Кисточки «Пляжных Раскопок»
     * - Слоты 20, 22, 24: Реагенты «Котла Алхимика»
     * - Слоты 29, 31, 33: Жеоды «Шахты Древних (0 ~ 0)»
     */
    @SuppressWarnings("deprecation")
    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(
                new MainMenuHolder(),
                45,
                ColorUtil.color("&8Настройка лута ивентов")
        );

        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, filler);
        }

        // Ряд 1: «Пляжные Раскопки» (9, 11, 13, 15)
        inv.setItem(9, createButton(
                Material.SAND,
                "&#FFD614&lПляжные Раскопки",
                List.of(
                        "&7Выберите кисточку справа",
                        "&7для настройки её лута и шансов.",
                        "",
                        "&eНажмите, чтобы открыть отдельное меню"
                )
        ));
        inv.setItem(11, createTierSelectIcon(BrushTier.COMMON, LootCategory.EXCAVATION));
        inv.setItem(13, createTierSelectIcon(BrushTier.EPIC, LootCategory.EXCAVATION));
        inv.setItem(15, createTierSelectIcon(BrushTier.LEGENDARY, LootCategory.EXCAVATION));

        // Ряд 2: «Котёл Алхимика» (18, 20, 22, 24)
        inv.setItem(18, createButton(
                Material.BREWING_STAND,
                "&#C850FF&lКотёл Алхимика",
                List.of(
                        "&7Выберите реагент справа",
                        "&7для настройки лута Котла Алхимика.",
                        "",
                        "&eНажмите, чтобы открыть отдельное меню"
                )
        ));
        inv.setItem(20, createTierSelectIcon(BrushTier.COMMON, LootCategory.CAULDRON));
        inv.setItem(22, createTierSelectIcon(BrushTier.EPIC, LootCategory.CAULDRON));
        inv.setItem(24, createTierSelectIcon(BrushTier.LEGENDARY, LootCategory.CAULDRON));

        // Ряд 3: «Шахта Древних (Постоянный, 0 ~ 0)» (27, 29, 31, 33)
        inv.setItem(27, createButton(
                Material.ANVIL,
                "&#FFB81C&lШахта Древних (0 ~ 0)",
                List.of(
                        "&7Постоянный ивент в центре мира!",
                        "&7Выберите древнюю жеоду справа",
                        "&7для настройки наград у Кузнеца.",
                        "",
                        "&eНажмите, чтобы открыть отдельное меню"
                )
        ));
        inv.setItem(29, createTierSelectIcon(BrushTier.COMMON, LootCategory.MINE));
        inv.setItem(31, createTierSelectIcon(BrushTier.EPIC, LootCategory.MINE));
        inv.setItem(33, createTierSelectIcon(BrushTier.LEGENDARY, LootCategory.MINE));

        // Ряд 4: «Пришельцы» (36)
        inv.setItem(36, createButton(
                Material.ENDER_EYE,
                "&#A020FF&lПришельцы — Настройка лута",
                List.of(
                        "&7Лут из сундуков посадочной площадки",
                        "&7пришельцев (alien.chests.loot).",
                        "",
                        "&eНажмите, чтобы открыть меню лута"
                )
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }

    /**
     * Открывает 27-слотовое GUI-меню настройки лута Пришельцев (alien.chests.loot).
     * Лут — flat-список (без tiers): материал, min, max, chance.
     */
    @SuppressWarnings("deprecation")
    public void openAlienMenu(Player player) {
        Inventory inv = Bukkit.createInventory(
                new AlienMenuHolder(),
                27,
                ColorUtil.color("&8Лут: Пришельцы")
        );

        ItemStack border = createButton(Material.PURPLE_STAINED_GLASS_PANE, "&5", List.of());
        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, (i < 9 || i >= 18) ? border : filler);
        }

        inv.setItem(4, createButton(
                Material.ENDER_EYE,
                "&#A020FF&lПришельцы — Настройка лута",
                List.of(
                        "&7Лут из сундуков посадочной площадки.",
                        "&7LEFT click: +5% &8| &7RIGHT click: -5%",
                        "&7SHIFT+LEFT: удалить предмет",
                        "",
                        "&fНажмите на предмет в инвентаре,",
                        "&fчтобы добавить новый лут-предмет."
                )
        ));

        // Показываем существующий лут (максимум 14 слотов: 2 ряда × 7)
        List<Map<?, ?>> lootList = plugin.getConfig().getMapList("alien.chests.loot");
        int slot = 10;
        for (int i = 0; i < Math.min(lootList.size(), 14); i++) {
            Map<?, ?> entry = lootList.get(i);
            String matName = String.valueOf(entry.get("material"));
            Material mat = Material.matchMaterial(matName);
            if (mat == null || mat == Material.AIR) mat = Material.STONE;
            double chance = entry.get("chance") instanceof Number n ? n.doubleValue() : 25.0;
            int min = entry.get("min") instanceof Number n ? n.intValue() : 1;
            int max = entry.get("max") instanceof Number n ? n.intValue() : min;

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtil.color("&f" + matName + " &7[" + (int) chance + "%]"));
                meta.setLore(List.of(
                        ColorUtil.color("&7Мин: &f" + min + " &7Макс: &f" + max),
                        ColorUtil.color("&7LEFT: +5% &7RIGHT: -5%"),
                        ColorUtil.color("&7SHIFT+LEFT: удалить")
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
            slot++;
            if (slot == 17) slot = 19;
        }

        // Кнопки управления
        inv.setItem(18, createButton(
                Material.ARROW,
                "&e« Все ивенты (/rift menu)",
                List.of("&7Перейти в общее меню всех ивентов")
        ));
        inv.setItem(26, createButton(
                Material.BARRIER,
                "&cЗакрыть",
                List.of("&7Закрыть меню")
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 1.2f);
    }

    /**
     * Обрабатывает клик на лут-предмет в меню Пришельцев.
     * LEFT: +5% &nbsp;|&nbsp; RIGHT: -5% &nbsp;|&nbsp; SHIFT+LEFT: удалить
     */
    private void handleAlienLootClick(InventoryClickEvent event, Player player, List<Map<?, ?>> lootList, int index) {
        if (index < 0 || index >= lootList.size()) {
            return;
        }
        Map<?, ?> entry = lootList.get(index);
        double chance = entry.get("chance") instanceof Number n ? n.doubleValue() : 25.0;

        if (event.isShiftClick()) {
            lootList.remove(index);
            plugin.getConfig().set("alien.chests.loot", lootList);
            plugin.saveConfig();
            player.sendMessage(ColorUtil.color("&cУдалён предмет из лута Пришельцев."));
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 0.8f, 1.0f);
            openAlienMenu(player);
        } else if (event.isRightClick()) {
            chance = Math.max(0, chance - 5.0);
            setAlienLootChance(lootList, index, chance);
            player.sendMessage(ColorUtil.color("&eШанс: " + (int) chance + "%"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
            openAlienMenu(player);
        } else {
            chance = Math.min(100, chance + 5.0);
            setAlienLootChance(lootList, index, chance);
            player.sendMessage(ColorUtil.color("&aШанс: " + (int) chance + "%"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
            openAlienMenu(player);
        }
    }

    @SuppressWarnings("unchecked")
    private void setAlienLootChance(List<Map<?, ?>> lootList, int index, double chance) {
        Map<String, Object> entry = (Map<String, Object>) lootList.get(index);
        entry.put("chance", chance);
        plugin.getConfig().set("alien.chests.loot", lootList);
        plugin.saveConfig();
    }

    /**
     * Открывает отдельное 27-слотовое GUI-меню настройки лута постоянного ивента «Шахта Древних»
     * (команды: /minemenu, /geodemenu, /rift minemenu, /rift menu mine).
     */
    @SuppressWarnings("deprecation")
    public void openMineMenu(Player player) {
        Inventory inv = Bukkit.createInventory(
                new EventMenuHolder(LootCategory.MINE),
                27,
                ColorUtil.color("&8Лут: Шахта Древних")
        );

        ItemStack border = createButton(Material.ORANGE_STAINED_GLASS_PANE, "&6", List.of());
        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, (i < 9 || i >= 18) ? border : filler);
        }

        inv.setItem(4, createButton(
                Material.ANVIL,
                "&#FFB81C&lШахта Древних — Настройка лута",
                List.of(
                        "&7Постоянный ивент на координатах &f0 ~ 0&7.",
                        "&7Здесь настраиваются награды, которые",
                        "&7Древний Кузнец выдаёт за раскол жеод.",
                        "",
                        "&fВыберите редкость жеоды ниже!"
                )
        ));

        inv.setItem(11, createTierSelectIcon(BrushTier.COMMON, LootCategory.MINE));
        inv.setItem(13, createTierSelectIcon(BrushTier.EPIC, LootCategory.MINE));
        inv.setItem(15, createTierSelectIcon(BrushTier.LEGENDARY, LootCategory.MINE));

        inv.setItem(18, createButton(
                Material.ARROW,
                "&e« Все ивенты (/rift menu)",
                List.of("&7Перейти в общее меню всех ивентов")
        ));

        inv.setItem(26, createButton(
                Material.BREWING_STAND,
                "&#C850FFПерейти к «Котлу Алхимика» »",
                List.of("&7Открыть меню настройки лута реагентов")
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.7f, 1.2f);
    }

    /**
     * Открывает отдельное 27-слотовое GUI-меню настройки лута «Котла Алхимика»
     * (команды: /rift cauldronmenu, /cauldronmenu, /rift menu cauldron).
     */
    @SuppressWarnings("deprecation")
    public void openCauldronMenu(Player player) {
        Inventory inv = Bukkit.createInventory(
                new EventMenuHolder(LootCategory.CAULDRON),
                27,
                ColorUtil.color("&8Лут: Котёл Алхимика")
        );

        ItemStack border = createButton(Material.PURPLE_STAINED_GLASS_PANE, "&5", List.of());
        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, (i < 9 || i >= 18) ? border : filler);
        }

        inv.setItem(4, createButton(
                Material.CAULDRON,
                "&#C850FF&lКотёл Алхимика — Настройка лута",
                List.of(
                        "&7Здесь настраиваются награды,",
                        "&7которые Алхимик варит из реагентов",
                        "&7соответствующей редкости.",
                        "",
                        "&fВыберите редкость реагента ниже!"
                )
        ));

        inv.setItem(11, createTierSelectIcon(BrushTier.COMMON, LootCategory.CAULDRON));
        inv.setItem(13, createTierSelectIcon(BrushTier.EPIC, LootCategory.CAULDRON));
        inv.setItem(15, createTierSelectIcon(BrushTier.LEGENDARY, LootCategory.CAULDRON));

        inv.setItem(18, createButton(
                Material.ARROW,
                "&e« Все ивенты (/rift menu)",
                List.of("&7Перейти в общее меню всех ивентов")
        ));

        inv.setItem(26, createButton(
                Material.ANVIL,
                "&#FFB81CПерейти к «Шахте Древних» »",
                List.of("&7Открыть меню настройки лута жеод")
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1.2f);
    }

    /**
     * Открывает отдельное 27-слотовое GUI-меню настройки лута «Пляжных Раскопок»
     * (команда: /rift menu excavation).
     */
    @SuppressWarnings("deprecation")
    public void openExcavationMenu(Player player) {
        Inventory inv = Bukkit.createInventory(
                new EventMenuHolder(LootCategory.EXCAVATION),
                27,
                ColorUtil.color("&8Лут: Пляжные Раскопки")
        );

        ItemStack border = createButton(Material.YELLOW_STAINED_GLASS_PANE, "&e", List.of());
        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, (i < 9 || i >= 18) ? border : filler);
        }

        inv.setItem(4, createButton(
                Material.SAND,
                "&#FFD614&lПляжные Раскопки — Настройка лута",
                List.of(
                        "&7Здесь настраиваются предметы,",
                        "&7выпадающие при раскопке подозрительного",
                        "&7песка кисточками разной редкости.",
                        "",
                        "&fВыберите редкость кисточки ниже!"
                )
        ));

        inv.setItem(11, createTierSelectIcon(BrushTier.COMMON, LootCategory.EXCAVATION));
        inv.setItem(13, createTierSelectIcon(BrushTier.EPIC, LootCategory.EXCAVATION));
        inv.setItem(15, createTierSelectIcon(BrushTier.LEGENDARY, LootCategory.EXCAVATION));

        inv.setItem(18, createButton(
                Material.ARROW,
                "&e« Все ивенты (/rift menu)",
                List.of("&7Перейти в общее меню всех ивентов")
        ));

        inv.setItem(26, createButton(
                Material.BREWING_STAND,
                "&#C850FFПерейти к «Котлу Алхимика» »",
                List.of("&7Открыть меню настройки лута реагентов")
        ));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }

    private ItemStack createTierSelectIcon(BrushTier tier, LootCategory category) {
        BrushLootManager mgr = plugin.getBrushLootManager();
        double weight = mgr.getTierWeight(tier, category);
        int count = mgr.getLootEntries(tier, category).size();

        return switch (category) {
            case CAULDRON -> {
                Material iconMat = switch (tier) {
                    case COMMON -> Material.SLIME_BALL;
                    case EPIC -> Material.AMETHYST_SHARD;
                    case LEGENDARY -> Material.BLAZE_POWDER;
                };
                yield createButton(
                        iconMat,
                        mgr.getReagentDisplayName(tier),
                        List.of(
                                "&7Ивент: &dКотёл Алхимика",
                                "&7Предметов в луте: &e" + count + " шт.",
                                "&7Шанс появления реагента: &#34D711" + weight + "%",
                                "",
                                "&eНажмите, чтобы настроить лут Котла!"
                        )
                );
            }
            case MINE -> {
                Material iconMat = switch (tier) {
                    case COMMON -> Material.QUARTZ;
                    case EPIC -> {
                        Material echo = Material.matchMaterial("ECHO_SHARD");
                        yield echo != null ? echo : Material.PRISMARINE_SHARD;
                    }
                    case LEGENDARY -> Material.MAGMA_CREAM;
                };
                yield createButton(
                        iconMat,
                        mgr.getGeodeDisplayName(tier),
                        List.of(
                                "&7Ивент: &#FFB81CШахта Древних (0 ~ 0)",
                                "&7Предметов в луте: &e" + count + " шт.",
                                "&7Шанс появления жеоды: &#34D711" + weight + "%",
                                "",
                                "&eНажмите, чтобы настроить лут Жеоды!"
                        )
                );
            }
            case EXCAVATION -> {
                Material iconMat = Material.matchMaterial("BRUSH");
                if (iconMat == null) {
                    iconMat = Material.FEATHER;
                }
                yield createButton(
                        iconMat,
                        tier.getItemTitle(),
                        List.of(
                                "&7Ивент: &eПляжные Раскопки",
                                "&7Предметов в луте: &e" + count + " шт.",
                                "&7Шанс выпадения кисточки: &#34D711" + weight + "%",
                                "&7Зарядов раскопок: &e" + tier.getDefaultUses(),
                                "",
                                "&eНажмите, чтобы настроить лут кисточки!"
                        )
                );
            }
        };
    }

    public void openTierMenu(Player player, BrushTier tier) {
        openTierMenu(player, tier, LootCategory.EXCAVATION);
    }

    public void openTierMenu(Player player, BrushTier tier, boolean cauldron) {
        openTierMenu(player, tier, cauldron ? LootCategory.CAULDRON : LootCategory.EXCAVATION);
    }

    /**
     * Открывает меню настройки лута и шансов конкретной кисточки, реагента или древней жеоды (54 слота).
     */
    @SuppressWarnings("deprecation")
    public void openTierMenu(Player player, BrushTier tier, LootCategory category) {
        BrushLootManager mgr = plugin.getBrushLootManager();
        String titleLabel = mgr.getCategoryItemTitle(tier, category);

        Inventory inv = Bukkit.createInventory(
                new TierMenuHolder(tier, category),
                54,
                ColorUtil.color("&8Лут: " + titleLabel)
        );

        EditClickMode mode = playerClickMode.getOrDefault(player.getUniqueId(), EditClickMode.PLUS_5);

        List<BrushLootManager.BrushLootEntry> entries = mgr.getLootEntries(tier, category);
        int maxSlots = Math.min(36, entries.size());
        for (int i = 0; i < maxSlots; i++) {
            BrushLootManager.BrushLootEntry entry = entries.get(i);
            ItemStack display = entry.item().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null
                        ? new ArrayList<>(meta.getLore())
                        : new ArrayList<>();
                lore.add(ColorUtil.color("&8------------------------"));
                lore.add(ColorUtil.color("&fШанс выпадения: &#34D711" + entry.chance() + "%"));
                lore.add(ColorUtil.color("&fРежим клика: " + mode.getLabel()));
                lore.add(ColorUtil.color("&7ЛКМ: +5% | ПКМ: -5% | Shift+ЛКМ: +1%"));
                lore.add(ColorUtil.color("&7Shift+ПКМ: -1% | Q / Колёсико: Удалить"));
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(i, display);
        }

        // Разделительная линия (36..53)
        ItemStack sep = createButton(Material.BLACK_STAINED_GLASS_PANE, "&8", List.of());
        for (int i = 36; i < 54; i++) {
            inv.setItem(i, sep);
        }

        String backTitle = switch (category) {
            case EXCAVATION -> "Пляжных Раскопок";
            case CAULDRON -> "Котла Алхимика";
            case MINE -> "Шахты Древних";
        };
        inv.setItem(45, createButton(
                Material.ARROW,
                "&e« Назад в меню " + backTitle,
                List.of(
                        "&7ЛКМ — вернуться в меню ивента",
                        "&7ПКМ — открыть общее меню /rift menu"
                )
        ));

        double weight = mgr.getTierWeight(tier, category);
        String itemKind = switch (category) {
            case EXCAVATION -> "кисточки";
            case CAULDRON -> "реагента";
            case MINE -> "жеоды";
        };
        inv.setItem(47, createButton(
                Material.RED_STAINED_GLASS_PANE,
                "&cУменьшить шанс " + itemKind + " (-5%)",
                List.of(
                        "&7Текущий вес " + itemKind + ": &e" + weight + "%",
                        "&eНажмите, чтобы уменьшить на 5%"
                )
        ));

        Material centerIconMat = switch (category) {
            case CAULDRON -> switch (tier) {
                case COMMON -> Material.SLIME_BALL;
                case EPIC -> Material.AMETHYST_SHARD;
                case LEGENDARY -> Material.BLAZE_POWDER;
            };
            case MINE -> switch (tier) {
                case COMMON -> Material.QUARTZ;
                case EPIC -> {
                    Material echo = Material.matchMaterial("ECHO_SHARD");
                    yield echo != null ? echo : Material.PRISMARINE_SHARD;
                }
                case LEGENDARY -> Material.MAGMA_CREAM;
            };
            case EXCAVATION -> {
                Material b = Material.matchMaterial("BRUSH");
                yield b != null ? b : Material.FEATHER;
            }
        };

        inv.setItem(48, createButton(
                centerIconMat,
                titleLabel,
                List.of(
                        "&7Вес появления: &#34D711" + weight + "%",
                        "",
                        "&7Используйте кнопки слева и справа",
                        "&7для изменения шанса " + itemKind + "."
                )
        ));

        inv.setItem(49, createButton(
                Material.LIME_STAINED_GLASS_PANE,
                "&aУвеличить шанс " + itemKind + " (+5%)",
                List.of(
                        "&7Текущий вес " + itemKind + ": &e" + weight + "%",
                        "&eНажмите, чтобы увеличить на 5%"
                )
        ));

        inv.setItem(50, createButton(
                Material.RED_WOOL,
                "&4Очистить весь лут этой редкости",
                List.of(
                        "&7Предметов сейчас: &e" + entries.size() + " шт.",
                        "&cНажмите Shift+ЛКМ (или Q),",
                        "&cчтобы полностью очистить список."
                )
        ));

        inv.setItem(51, createButton(
                Material.COMPARATOR,
                "&6Режим клика: " + mode.getLabel(),
                List.of(
                        "&7Сейчас при обычном нажатии (ЛКМ)",
                        "&7или тапе с телефона сработает:",
                        "&e» " + mode.getLabel(),
                        "",
                        "&eНажмите сюда, чтобы сменить режим:",
                        "&a+5% &7/ &c-5% &7/ &e+1% &7/ &6-1% &7/ &4Удалить"
                )
        ));

        inv.setItem(53, createButton(
                Material.EMERALD,
                "&aКак добавить свой предмет?",
                List.of(
                        "&fПросто нажмите на любой предмет",
                        "&fв своём инвентаре (снизу),",
                        "&fи он сразу добавится в лут",
                        "&fсо всеми чарами и количеством!"
                )
        ));

        player.openInventory(inv);
    }

    public boolean handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (holder instanceof MainMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return true;
            }
            int slot = event.getSlot();
            switch (slot) {
                case 9 -> openExcavationMenu(player);
                case 11 -> openTierMenu(player, BrushTier.COMMON, LootCategory.EXCAVATION);
                case 13 -> openTierMenu(player, BrushTier.EPIC, LootCategory.EXCAVATION);
                case 15 -> openTierMenu(player, BrushTier.LEGENDARY, LootCategory.EXCAVATION);
                case 18 -> openCauldronMenu(player);
                case 20 -> openTierMenu(player, BrushTier.COMMON, LootCategory.CAULDRON);
                case 22 -> openTierMenu(player, BrushTier.EPIC, LootCategory.CAULDRON);
                case 24 -> openTierMenu(player, BrushTier.LEGENDARY, LootCategory.CAULDRON);
                case 27 -> openMineMenu(player);
                case 29 -> openTierMenu(player, BrushTier.COMMON, LootCategory.MINE);
                case 31 -> openTierMenu(player, BrushTier.EPIC, LootCategory.MINE);
                case 33 -> openTierMenu(player, BrushTier.LEGENDARY, LootCategory.MINE);
                case 36 -> openAlienMenu(player);
                default -> {
                }
            }
            return true;
        }

        if (holder instanceof AlienMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                // Клик в инвентаре игрока — добавляем новый предмет в лут
                if (event.getClickedInventory() == player.getInventory()) {
                    ItemStack clickedItem = event.getCurrentItem();
                    if (clickedItem == null || clickedItem.getType().isAir()) {
                        return true;
                    }
                    // Проверяем, не является ли предмет "кисточкой/реагентом/жеодой"
                    BrushLootManager mgr = plugin.getBrushLootManager();
                    if (mgr.getBrushTier(clickedItem) != null) {
                        player.sendMessage(ColorUtil.color("&e┠ &cСам предмет ивента нельзя добавить в лут Пришельцев."));
                        return true;
                    }
                    // Добавляем новый лут-преdmet
                    List<Map<?, ?>> lootList = plugin.getConfig().getMapList("alien.chests.loot");
                    if (lootList.size() >= 36) {
                        player.sendMessage(ColorUtil.color("&e┠ &cСписок лута заполнен (максимум 36 слотов)."));
                        return true;
                    }
                    Map<String, Object> newEntry = new HashMap<>();
                    newEntry.put("material", clickedItem.getType().name());
                    newEntry.put("min", 1);
                    newEntry.put("max", 3);
                    newEntry.put("chance", 25.0);
                    lootList.add(newEntry);
                    plugin.getConfig().set("alien.chests.loot", lootList);
                    plugin.saveConfig();
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
                    player.sendMessage(ColorUtil.color("&a# " + clickedItem.getType().name() + " добавлен в лут Пришельцев (25%)."));
                    openAlienMenu(player);
                    return true;
                }
                return true;
            }

            int slot = event.getSlot();
            List<Map<?, ?>> lootList = plugin.getConfig().getMapList("alien.chests.loot");

            // Управление кнопками
            if (slot == 18) {
                openMainMenu(player);
                return true;
            }
            if (slot == 26) {
                player.closeInventory();
                return true;
            }

            // Редактирование лут-предметов (слоты 10-16, 19-25)
            if (slot >= 10 && slot <= 16) {
                int idx = slot - 10;
                handleAlienLootClick(event, player, lootList, idx);
                return true;
            }
            if (slot >= 19 && slot <= 25) {
                int idx = slot - 19 + 7;
                handleAlienLootClick(event, player, lootList, idx);
                return true;
            }
            return true;
        }

        if (holder instanceof EventMenuHolder eventHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return true;
            }
            LootCategory category = eventHolder.getCategory();
            int slot = event.getSlot();
            switch (slot) {
                case 11 -> openTierMenu(player, BrushTier.COMMON, category);
                case 13 -> openTierMenu(player, BrushTier.EPIC, category);
                case 15 -> openTierMenu(player, BrushTier.LEGENDARY, category);
                case 18 -> openMainMenu(player);
                case 26 -> {
                    switch (category) {
                        case EXCAVATION -> openCauldronMenu(player);
                        case CAULDRON -> openMineMenu(player);
                        case MINE -> openCauldronMenu(player);
                    }
                }
                default -> {
                }
            }
            return true;
        }

        if (holder instanceof TierMenuHolder tierHolder) {
            event.setCancelled(true);
            BrushTier tier = tierHolder.getTier();
            LootCategory category = tierHolder.getCategory();
            BrushLootManager mgr = plugin.getBrushLootManager();

            if (event.getClickedInventory() == player.getInventory()) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType().isAir()) {
                    return true;
                }
                if (mgr.getBrushTier(clickedItem) != null) {
                    player.sendMessage(ColorUtil.color("&e┠ &cСам предмет ивента (кисточку/реагент/жеоду) нельзя добавить в лут."));
                    return true;
                }
                if (mgr.getLootEntries(tier, category).size() >= 36) {
                    player.sendMessage(ColorUtil.color("&e┠ &cСписок лута заполнен (максимум 36 слотов)."));
                    return true;
                }
                mgr.addLootItem(tier, clickedItem.clone(), 25.0, category);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
                openTierMenu(player, tier, category);
                return true;
            }

            if (event.getClickedInventory() != top) {
                return true;
            }

            int slot = event.getSlot();
            if (slot == 45) {
                if (event.getClick() == ClickType.RIGHT) {
                    openMainMenu(player);
                } else {
                    switch (category) {
                        case EXCAVATION -> openExcavationMenu(player);
                        case CAULDRON -> openCauldronMenu(player);
                        case MINE -> openMineMenu(player);
                    }
                }
                return true;
            }
            if (slot == 47) {
                mgr.setTierWeight(tier, mgr.getTierWeight(tier, category) - 5.0, category);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                openTierMenu(player, tier, category);
                return true;
            }
            if (slot == 49) {
                mgr.setTierWeight(tier, mgr.getTierWeight(tier, category) + 5.0, category);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.3f);
                openTierMenu(player, tier, category);
                return true;
            }
            if (slot == 50) {
                ClickType click = event.getClick();
                if (click.isShiftClick() || click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.MIDDLE) {
                    mgr.clearLoot(tier, category);
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
                    openTierMenu(player, tier, category);
                } else {
                    player.sendMessage(ColorUtil.color("&e┠ &cНажмите &#E0C912Shift+ЛКМ &cили клавишу &#E0C912Q&c, чтобы подтвердить полную очистку лута."));
                }
                return true;
            }
            if (slot == 51) {
                EditClickMode current = playerClickMode.getOrDefault(player.getUniqueId(), EditClickMode.PLUS_5);
                EditClickMode next = current.next();
                playerClickMode.put(player.getUniqueId(), next);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.4f);
                openTierMenu(player, tier, category);
                return true;
            }

            List<BrushLootManager.BrushLootEntry> entries = mgr.getLootEntries(tier, category);
            if (slot >= 0 && slot < entries.size()) {
                BrushLootManager.BrushLootEntry entry = entries.get(slot);
                ClickType click = event.getClick();
                EditClickMode mode = playerClickMode.getOrDefault(player.getUniqueId(), EditClickMode.PLUS_5);

                if (click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.MIDDLE || mode == EditClickMode.DELETE) {
                    mgr.removeLootItem(tier, slot + 1, category);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.2f);
                } else if (click == ClickType.SHIFT_LEFT) {
                    mgr.updateLootEntryChance(tier, slot, entry.chance() + 1.0, category);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.3f);
                } else if (click == ClickType.SHIFT_RIGHT) {
                    mgr.updateLootEntryChance(tier, slot, entry.chance() - 1.0, category);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                } else if (click == ClickType.RIGHT) {
                    mgr.updateLootEntryChance(tier, slot, entry.chance() - 5.0, category);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                } else {
                    switch (mode) {
                        case PLUS_5 -> mgr.updateLootEntryChance(tier, slot, entry.chance() + 5.0, category);
                        case MINUS_5 -> mgr.updateLootEntryChance(tier, slot, entry.chance() - 5.0, category);
                        case PLUS_1 -> mgr.updateLootEntryChance(tier, slot, entry.chance() + 1.0, category);
                        case MINUS_1 -> mgr.updateLootEntryChance(tier, slot, entry.chance() - 1.0, category);
                        default -> {
                        }
                    }
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                }
                openTierMenu(player, tier, category);
            }
            return true;
        }

        return false;
    }

    public boolean handleInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MainMenuHolder || holder instanceof EventMenuHolder || holder instanceof TierMenuHolder) {
            event.setCancelled(true);
            return true;
        }
        return false;
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
