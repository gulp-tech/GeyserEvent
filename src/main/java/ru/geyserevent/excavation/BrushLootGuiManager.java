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
import ru.geyserevent.util.ColorUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Удобное GUI-меню настройки лута и шансов:
 * - Главное меню (/rift menu): оба ивента («Пляжные Раскопки» и «Котёл Алхимика»)
 * - Отдельное меню «Котла Алхимика» (/rift cauldronmenu, /cauldronmenu, /rift menu cauldron)
 * - Отдельное меню «Пляжных Раскопок» (/rift menu excavation)
 * Полностью адаптировано как под телефоны (Bedrock GeyserMC), так и под Bedrock ПК / Java.
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
        private final boolean cauldron;

        public EventMenuHolder(boolean cauldron) {
            this.cauldron = cauldron;
        }

        public boolean isCauldron() {
            return cauldron;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class TierMenuHolder implements InventoryHolder {
        private final BrushTier tier;
        private final boolean cauldron;

        public TierMenuHolder(BrushTier tier, boolean cauldron) {
            this.tier = tier;
            this.cauldron = cauldron;
        }

        public BrushTier getTier() {
            return tier;
        }

        public boolean isCauldron() {
            return cauldron;
        }

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
     * Открывает главное меню выбора ивента и редкости (36 слотов):
     * - Слоты 11, 13, 15: Кисточки «Пляжных Раскопок»
     * - Слоты 20, 22, 24: Реагенты «Котла Алхимика»
     */
    @SuppressWarnings("deprecation")
    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(
                new MainMenuHolder(),
                36,
                ColorUtil.color("&8Настройка лута ивентов")
        );

        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, filler);
        }

        // Иконка-заголовок ряда «Пляжные Раскопки» (слот 9)
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

        // Ряд 1: Кисточки «Пляжных Раскопок» (11, 13, 15)
        inv.setItem(11, createTierSelectIcon(BrushTier.COMMON, false));
        inv.setItem(13, createTierSelectIcon(BrushTier.EPIC, false));
        inv.setItem(15, createTierSelectIcon(BrushTier.LEGENDARY, false));

        // Иконка-заголовок ряда «Котёл Алхимика» (слот 18)
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

        // Ряд 2: Реагенты «Котла Алхимика» (20, 22, 24)
        inv.setItem(20, createTierSelectIcon(BrushTier.COMMON, true));
        inv.setItem(22, createTierSelectIcon(BrushTier.EPIC, true));
        inv.setItem(24, createTierSelectIcon(BrushTier.LEGENDARY, true));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }

    /**
     * Открывает отдельное 27-слотовое GUI-меню настройки лута «Котла Алхимика»
     * (команды: /rift cauldronmenu, /cauldronmenu, /rift menu cauldron).
     */
    @SuppressWarnings("deprecation")
    public void openCauldronMenu(Player player) {
        Inventory inv = Bukkit.createInventory(
                new EventMenuHolder(true),
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

        inv.setItem(11, createTierSelectIcon(BrushTier.COMMON, true));
        inv.setItem(13, createTierSelectIcon(BrushTier.EPIC, true));
        inv.setItem(15, createTierSelectIcon(BrushTier.LEGENDARY, true));

        inv.setItem(18, createButton(
                Material.ARROW,
                "&e« Все ивенты (/rift menu)",
                List.of("&7Перейти в общее меню всех ивентов")
        ));

        inv.setItem(26, createButton(
                Material.SAND,
                "&#FFD614Перейти к «Пляжным Раскопкам» »",
                List.of("&7Открыть меню настройки лута кисточек")
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
                new EventMenuHolder(false),
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

        inv.setItem(11, createTierSelectIcon(BrushTier.COMMON, false));
        inv.setItem(13, createTierSelectIcon(BrushTier.EPIC, false));
        inv.setItem(15, createTierSelectIcon(BrushTier.LEGENDARY, false));

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

    private ItemStack createTierSelectIcon(BrushTier tier, boolean cauldron) {
        BrushLootManager mgr = plugin.getBrushLootManager();
        double weight = mgr.getTierWeight(tier, cauldron);
        int count = mgr.getLootEntries(tier, cauldron).size();

        if (cauldron) {
            Material iconMat = switch (tier) {
                case COMMON -> Material.SLIME_BALL;
                case EPIC -> Material.AMETHYST_SHARD;
                case LEGENDARY -> Material.BLAZE_POWDER;
            };
            return createButton(
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
        } else {
            Material iconMat = Material.matchMaterial("BRUSH");
            if (iconMat == null) {
                iconMat = Material.FEATHER;
            }
            return createButton(
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
    }

    public void openTierMenu(Player player, BrushTier tier) {
        openTierMenu(player, tier, false);
    }

    /**
     * Открывает меню настройки лута и шансов конкретной кисточки или реагента (54 слота).
     */
    @SuppressWarnings("deprecation")
    public void openTierMenu(Player player, BrushTier tier, boolean cauldron) {
        BrushLootManager mgr = plugin.getBrushLootManager();
        String titleLabel = cauldron ? mgr.getReagentDisplayName(tier) : tier.getItemTitle();

        Inventory inv = Bukkit.createInventory(
                new TierMenuHolder(tier, cauldron),
                54,
                ColorUtil.color("&8Лут: " + titleLabel)
        );

        EditClickMode mode = playerClickMode.getOrDefault(player.getUniqueId(), EditClickMode.PLUS_5);

        List<BrushLootManager.BrushLootEntry> entries = mgr.getLootEntries(tier, cauldron);
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

        // Кнопка «Назад» (45)
        inv.setItem(45, createButton(
                Material.ARROW,
                "&e« Назад в меню " + (cauldron ? "Котла Алхимика" : "Пляжных Раскопок"),
                List.of(
                        "&7ЛКМ — вернуться в меню ивента",
                        "&7ПКМ — открыть общее меню /rift menu"
                )
        ));

        // Управление шансом выпадения самой кисточки / реагента (47, 48, 49)
        double weight = mgr.getTierWeight(tier, cauldron);
        String itemKind = cauldron ? "реагента" : "кисточки";
        inv.setItem(47, createButton(
                Material.RED_STAINED_GLASS_PANE,
                "&cУменьшить шанс " + itemKind + " (-5%)",
                List.of(
                        "&7Текущий вес " + itemKind + ": &e" + weight + "%",
                        "&eНажмите, чтобы уменьшить на 5%"
                )
        ));

        Material centerIconMat;
        if (cauldron) {
            centerIconMat = switch (tier) {
                case COMMON -> Material.SLIME_BALL;
                case EPIC -> Material.AMETHYST_SHARD;
                case LEGENDARY -> Material.BLAZE_POWDER;
            };
        } else {
            Material b = Material.matchMaterial("BRUSH");
            centerIconMat = b != null ? b : Material.FEATHER;
        }

        inv.setItem(48, createButton(
                centerIconMat,
                titleLabel,
                List.of(
                        "&7Вес появления этого " + itemKind + ": &#34D711" + weight + "%",
                        "",
                        "&7Используйте кнопки слева и справа",
                        "&7для изменения шанса самого " + itemKind + "."
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

        // Кнопка очистки всего лута этой редкости (50)
        inv.setItem(50, createButton(
                Material.RED_WOOL,
                "&4Очистить весь лут этой редкости",
                List.of(
                        "&7Предметов сейчас: &e" + entries.size() + " шт.",
                        "&cНажмите Shift+ЛКМ (или Q),",
                        "&cчтобы полностью очистить список."
                )
        ));

        // Переключатель режима клика по предметам (51)
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

        // Подсказка по добавлению предметов (53)
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
                case 11 -> openTierMenu(player, BrushTier.COMMON, false);
                case 13 -> openTierMenu(player, BrushTier.EPIC, false);
                case 15 -> openTierMenu(player, BrushTier.LEGENDARY, false);
                case 18 -> openCauldronMenu(player);
                case 20 -> openTierMenu(player, BrushTier.COMMON, true);
                case 22 -> openTierMenu(player, BrushTier.EPIC, true);
                case 24 -> openTierMenu(player, BrushTier.LEGENDARY, true);
                default -> {
                }
            }
            return true;
        }

        if (holder instanceof EventMenuHolder eventHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return true;
            }
            boolean cauldron = eventHolder.isCauldron();
            int slot = event.getSlot();
            switch (slot) {
                case 11 -> openTierMenu(player, BrushTier.COMMON, cauldron);
                case 13 -> openTierMenu(player, BrushTier.EPIC, cauldron);
                case 15 -> openTierMenu(player, BrushTier.LEGENDARY, cauldron);
                case 18 -> openMainMenu(player);
                case 26 -> {
                    if (cauldron) {
                        openExcavationMenu(player);
                    } else {
                        openCauldronMenu(player);
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
            boolean cauldron = tierHolder.isCauldron();
            BrushLootManager mgr = plugin.getBrushLootManager();

            // Если админ кликнул по предмету в СВОЁМ нижнем инвентаре — добавляем его в лут!
            if (event.getClickedInventory() == player.getInventory()) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType().isAir()) {
                    return true;
                }
                if (mgr.getBrushTier(clickedItem) != null) {
                    player.sendMessage(ColorUtil.color("&e┠ &cСаму кисточку или реагент ивента нельзя добавить в лут."));
                    return true;
                }
                if (mgr.getLootEntries(tier, cauldron).size() >= 36) {
                    player.sendMessage(ColorUtil.color("&e┠ &cСписок лута заполнен (максимум 36 слотов)."));
                    return true;
                }
                mgr.addLootItem(tier, clickedItem.clone(), 25.0, cauldron);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
                openTierMenu(player, tier, cauldron);
                return true;
            }

            if (event.getClickedInventory() != top) {
                return true;
            }

            int slot = event.getSlot();
            if (slot == 45) {
                if (event.getClick() == ClickType.RIGHT) {
                    openMainMenu(player);
                } else if (cauldron) {
                    openCauldronMenu(player);
                } else {
                    openExcavationMenu(player);
                }
                return true;
            }
            if (slot == 47) {
                mgr.setTierWeight(tier, mgr.getTierWeight(tier, cauldron) - 5.0, cauldron);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                openTierMenu(player, tier, cauldron);
                return true;
            }
            if (slot == 49) {
                mgr.setTierWeight(tier, mgr.getTierWeight(tier, cauldron) + 5.0, cauldron);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.3f);
                openTierMenu(player, tier, cauldron);
                return true;
            }
            if (slot == 50) {
                ClickType click = event.getClick();
                if (click.isShiftClick() || click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.MIDDLE) {
                    mgr.clearLoot(tier, cauldron);
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
                    openTierMenu(player, tier, cauldron);
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
                openTierMenu(player, tier, cauldron);
                return true;
            }

            // Клик по предмету лута в слотах 0..35
            List<BrushLootManager.BrushLootEntry> entries = mgr.getLootEntries(tier, cauldron);
            if (slot >= 0 && slot < entries.size()) {
                BrushLootManager.BrushLootEntry entry = entries.get(slot);
                ClickType click = event.getClick();
                EditClickMode mode = playerClickMode.getOrDefault(player.getUniqueId(), EditClickMode.PLUS_5);

                if (click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.MIDDLE || mode == EditClickMode.DELETE) {
                    mgr.removeLootItem(tier, slot + 1, cauldron);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.2f);
                } else if (click == ClickType.SHIFT_LEFT) {
                    mgr.updateLootEntryChance(tier, slot, entry.chance() + 1.0, cauldron);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.3f);
                } else if (click == ClickType.SHIFT_RIGHT) {
                    mgr.updateLootEntryChance(tier, slot, entry.chance() - 1.0, cauldron);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                } else if (click == ClickType.RIGHT) {
                    mgr.updateLootEntryChance(tier, slot, entry.chance() - 5.0, cauldron);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                } else {
                    switch (mode) {
                        case PLUS_5 -> mgr.updateLootEntryChance(tier, slot, entry.chance() + 5.0, cauldron);
                        case MINUS_5 -> mgr.updateLootEntryChance(tier, slot, entry.chance() - 5.0, cauldron);
                        case PLUS_1 -> mgr.updateLootEntryChance(tier, slot, entry.chance() + 1.0, cauldron);
                        case MINUS_1 -> mgr.updateLootEntryChance(tier, slot, entry.chance() - 1.0, cauldron);
                        default -> {
                        }
                    }
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                }
                openTierMenu(player, tier, cauldron);
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
