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
 * Удобное GUI-меню настройки лута и шансов для каждой кисточки («Обычная», «Эпическая», «Легендарная»).
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

    public static class TierMenuHolder implements InventoryHolder {
        private final BrushTier tier;

        public TierMenuHolder(BrushTier tier) {
            this.tier = tier;
        }

        public BrushTier getTier() {
            return tier;
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
     * Открывает главное меню выбора кисточки (27 слотов).
     */
    @SuppressWarnings("deprecation")
    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(
                new MainMenuHolder(),
                27,
                ColorUtil.color("&8Настройка кисточек Раскопок")
        );

        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(11, createTierSelectIcon(BrushTier.COMMON));
        inv.setItem(13, createTierSelectIcon(BrushTier.EPIC));
        inv.setItem(15, createTierSelectIcon(BrushTier.LEGENDARY));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }

    private ItemStack createTierSelectIcon(BrushTier tier) {
        BrushLootManager mgr = plugin.getBrushLootManager();
        double weight = mgr.getBrushWeight(tier);
        int count = mgr.getLootEntries(tier).size();

        Material iconMat = Material.matchMaterial("BRUSH");
        if (iconMat == null) {
            iconMat = Material.FEATHER;
        }

        return createButton(
                iconMat,
                tier.getItemTitle(),
                List.of(
                        "&7Предметов в луте: &e" + count + " шт.",
                        "&7Шанс выпадения редкости: &#34D711" + weight + "%",
                        "&7Ивенты: &fПляжные Раскопки &7+ &dКотёл Алхимика",
                        "",
                        "&eНажмите, чтобы настроить лут и шансы!"
                )
        );
    }

    /**
     * Открывает меню настройки лута и шансов конкретной кисточки (54 слота).
     */
    @SuppressWarnings("deprecation")
    public void openTierMenu(Player player, BrushTier tier) {
        Inventory inv = Bukkit.createInventory(
                new TierMenuHolder(tier),
                54,
                ColorUtil.color("&8Лут: " + tier.getItemTitle())
        );

        BrushLootManager mgr = plugin.getBrushLootManager();
        EditClickMode mode = playerClickMode.getOrDefault(player.getUniqueId(), EditClickMode.PLUS_5);

        List<BrushLootManager.BrushLootEntry> entries = mgr.getLootEntries(tier);
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
                lore.add(ColorUtil.color("&fДействие клика: " + mode.getLabel()));
                lore.add(ColorUtil.color("&7(ПК: ЛКМ +5%, ПКМ -5%, Shift+ПКМ удалить)"));
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(i, display);
        }

        // Разделительная линия (36..44)
        ItemStack sep = createButton(Material.BLACK_STAINED_GLASS_PANE, "&8", List.of());
        for (int i = 36; i < 54; i++) {
            inv.setItem(i, sep);
        }

        // Кнопка «Назад» (45)
        inv.setItem(45, createButton(
                Material.ARROW,
                "&e« Назад к выбору кисточки",
                List.of("&7Вернуться в главное меню")
        ));

        // Управление шансом выпадения самой кисточки у Жителя (47, 48, 49)
        double brushWeight = mgr.getBrushWeight(tier);
        inv.setItem(47, createButton(
                Material.RED_STAINED_GLASS_PANE,
                "&cУменьшить шанс кисточки (-5%)",
                List.of(
                        "&7Текущий вес кисточки: &e" + brushWeight + "%",
                        "&eНажмите, чтобы уменьшить на 5%"
                )
        ));

        Material brushMat = Material.matchMaterial("BRUSH");
        inv.setItem(48, createButton(
                brushMat != null ? brushMat : Material.FEATHER,
                tier.getItemTitle(),
                List.of(
                        "&7Вес выпадения этой кисточки",
                        "&7у Жителя-Археолога: &#34D711" + brushWeight + "%",
                        "",
                        "&7Используйте кнопки слева и справа",
                        "&7для изменения шанса самой кисточки."
                )
        ));

        inv.setItem(49, createButton(
                Material.LIME_STAINED_GLASS_PANE,
                "&aУвеличить шанс кисточки (+5%)",
                List.of(
                        "&7Текущий вес кисточки: &e" + brushWeight + "%",
                        "&eНажмите, чтобы увеличить на 5%"
                )
        ));

        // Переключатель режима клика по предметам (51) — удобно и для телефонов, и для ПК
        inv.setItem(51, createButton(
                Material.COMPARATOR,
                "&6Режим клика: " + mode.getLabel(),
                List.of(
                        "&7Сейчас при нажатии на предмет",
                        "&7в верхнем меню сработает:",
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
                        "&fэтой кисточки со всеми чарами!"
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
            if (slot == 11) {
                openTierMenu(player, BrushTier.COMMON);
            } else if (slot == 13) {
                openTierMenu(player, BrushTier.EPIC);
            } else if (slot == 15) {
                openTierMenu(player, BrushTier.LEGENDARY);
            }
            return true;
        }

        if (holder instanceof TierMenuHolder tierHolder) {
            event.setCancelled(true);
            BrushTier tier = tierHolder.getTier();
            BrushLootManager mgr = plugin.getBrushLootManager();

            // Если админ кликнул по предмету в СВОЁМ нижнем инвентаре — добавляем его в лут кисточки!
            if (event.getClickedInventory() == player.getInventory()) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType().isAir()) {
                    return true;
                }
                if (mgr.getBrushTier(clickedItem) != null) {
                    player.sendMessage(ColorUtil.color("&e┠ &cСаму кисточку раскопок нельзя добавить в лут."));
                    return true;
                }
                if (mgr.getLootEntries(tier).size() >= 36) {
                    player.sendMessage(ColorUtil.color("&e┠ &cСписок лута этой кисточки заполнен (максимум 36 слотов)."));
                    return true;
                }
                mgr.addLootItem(tier, clickedItem.clone(), 25.0);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
                openTierMenu(player, tier);
                return true;
            }

            if (event.getClickedInventory() != top) {
                return true;
            }

            int slot = event.getSlot();
            if (slot == 45) {
                openMainMenu(player);
                return true;
            }
            if (slot == 47) {
                mgr.setBrushWeight(tier, mgr.getBrushWeight(tier) - 5.0);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                openTierMenu(player, tier);
                return true;
            }
            if (slot == 49) {
                mgr.setBrushWeight(tier, mgr.getBrushWeight(tier) + 5.0);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.3f);
                openTierMenu(player, tier);
                return true;
            }
            if (slot == 51) {
                EditClickMode current = playerClickMode.getOrDefault(player.getUniqueId(), EditClickMode.PLUS_5);
                EditClickMode next = current.next();
                playerClickMode.put(player.getUniqueId(), next);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.4f);
                openTierMenu(player, tier);
                return true;
            }

            // Клик по предмету лута в слотах 0..35
            List<BrushLootManager.BrushLootEntry> entries = mgr.getLootEntries(tier);
            if (slot >= 0 && slot < entries.size()) {
                BrushLootManager.BrushLootEntry entry = entries.get(slot);
                ClickType click = event.getClick();
                EditClickMode mode = playerClickMode.getOrDefault(player.getUniqueId(), EditClickMode.PLUS_5);

                if (click == ClickType.SHIFT_RIGHT || click == ClickType.DROP || click == ClickType.CONTROL_DROP || mode == EditClickMode.DELETE) {
                    mgr.removeLootItem(tier, slot + 1);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.2f);
                } else if (click == ClickType.RIGHT) {
                    mgr.updateLootEntryChance(tier, slot, entry.chance() - 5.0);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                } else {
                    switch (mode) {
                        case PLUS_5 -> mgr.updateLootEntryChance(tier, slot, entry.chance() + 5.0);
                        case MINUS_5 -> mgr.updateLootEntryChance(tier, slot, entry.chance() - 5.0);
                        case PLUS_1 -> mgr.updateLootEntryChance(tier, slot, entry.chance() + 1.0);
                        case MINUS_1 -> mgr.updateLootEntryChance(tier, slot, entry.chance() - 1.0);
                        default -> {
                        }
                    }
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                }
                openTierMenu(player, tier);
            }
            return true;
        }

        return false;
    }

    public boolean handleInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MainMenuHolder || holder instanceof TierMenuHolder) {
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
