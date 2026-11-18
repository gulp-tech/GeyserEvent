package ru.geyserevent.excavation;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
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

/**
 * Два режима GUI: предметы (лут) и проценты (шансы).
 * /rift menu — выбор режима, дальше список ивентов.
 */
public class BrushLootGuiManager {

    public enum GuiKind {
        LOOT,
        CHANCES
    }

    public static class HubHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class MainMenuHolder implements InventoryHolder {
        private final GuiKind kind;

        public MainMenuHolder(GuiKind kind) {
            this.kind = kind;
        }

        public GuiKind getKind() {
            return kind;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class EventMenuHolder implements InventoryHolder {
        private final LootCategory category;
        private final GuiKind kind;

        public EventMenuHolder(LootCategory category, GuiKind kind) {
            this.category = category;
            this.kind = kind;
        }

        public LootCategory getCategory() {
            return category;
        }

        public GuiKind getKind() {
            return kind;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class TierMenuHolder implements InventoryHolder {
        private final BrushTier tier;
        private final LootCategory category;
        private final GuiKind kind;

        public TierMenuHolder(BrushTier tier, LootCategory category, GuiKind kind) {
            this.tier = tier;
            this.category = category;
            this.kind = kind;
        }

        public BrushTier getTier() {
            return tier;
        }

        public LootCategory getCategory() {
            return category;
        }

        public GuiKind getKind() {
            return kind;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class FlatLootHolder implements InventoryHolder {
        private final String path;
        private final String title;
        private final Material icon;
        private final boolean yamlList;
        private final GuiKind kind;
        private final List<String> keys = new ArrayList<>();

        public FlatLootHolder(String path, String title, Material icon, boolean yamlList, GuiKind kind) {
            this.path = path;
            this.title = title;
            this.icon = icon;
            this.yamlList = yamlList;
            this.kind = kind;
        }

        public GuiKind getKind() {
            return kind;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class WindMenuHolder implements InventoryHolder {
        private final GuiKind kind;

        public WindMenuHolder(GuiKind kind) {
            this.kind = kind;
        }

        public GuiKind getKind() {
            return kind;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class NewyearMenuHolder implements InventoryHolder {
        private final GuiKind kind;

        public NewyearMenuHolder(GuiKind kind) {
            this.kind = kind;
        }

        public GuiKind getKind() {
            return kind;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final GeyserEventPlugin plugin;

    public BrushLootGuiManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new HubHolder(), 27, ColorUtil.color("&8Что настраиваем?"));
        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(4, createButton(
                Material.BOOK,
                "&f&lНастройка ивентов",
                List.of(
                        "&7Два разных меню — не путай.",
                        "&eЛут &7— какие предметы падают.",
                        "&eШансы &7— проценты и редкость."
                )
        ));
        inv.setItem(11, createButton(
                Material.CHEST,
                "&a&lНастройка лута",
                List.of(
                        "&7Добавить предмет — клик в инвентаре.",
                        "&7Убрать — Shift+ЛКМ по предмету.",
                        "",
                        "&eНажмите, чтобы выбрать ивент"
                )
        ));
        inv.setItem(15, createButton(
                Material.GOLD_INGOT,
                "&6&lНастройка шансов",
                List.of(
                        "&7ЛКМ +5% &8· &7ПКМ -5%",
                        "&7Shift ±1%. Редкость кисточки/удочки тут.",
                        "",
                        "&eНажмите, чтобы выбрать ивент"
                )
        ));
        openLater(player, inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }

    @SuppressWarnings("deprecation")
    public void openEventList(Player player, GuiKind kind) {
        boolean loot = kind == GuiKind.LOOT;
        Inventory inv = Bukkit.createInventory(
                new MainMenuHolder(kind),
                45,
                ColorUtil.color(loot ? "&8Настройка лута" : "&8Настройка шансов")
        );
        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(0, createButton(
                Material.ARROW,
                "&e« Лут или шансы",
                List.of("&7Вернуться к выбору меню")
        ));
        String action = loot ? "предметы лута" : "шансы";
        inv.setItem(9, createButton(
                Material.SAND,
                "&#FFD614&lПляжные Раскопки",
                List.of("&7Кисточки: " + action, "", "&eНажмите")
        ));
        inv.setItem(11, createTierSelectIcon(BrushTier.COMMON, LootCategory.EXCAVATION, kind));
        inv.setItem(13, createTierSelectIcon(BrushTier.EPIC, LootCategory.EXCAVATION, kind));
        inv.setItem(15, createTierSelectIcon(BrushTier.LEGENDARY, LootCategory.EXCAVATION, kind));

        inv.setItem(18, createButton(
                Material.BREWING_STAND,
                "&#C850FF&lКотёл Алхимика",
                List.of("&7Реагенты: " + action, "", "&eНажмите")
        ));
        inv.setItem(20, createTierSelectIcon(BrushTier.COMMON, LootCategory.CAULDRON, kind));
        inv.setItem(22, createTierSelectIcon(BrushTier.EPIC, LootCategory.CAULDRON, kind));
        inv.setItem(24, createTierSelectIcon(BrushTier.LEGENDARY, LootCategory.CAULDRON, kind));

        inv.setItem(27, createButton(
                Material.ANVIL,
                "&#FFB81C&lШахта Древних (0 ~ 0)",
                List.of("&7Жеоды: " + action, "", "&eНажмите")
        ));
        inv.setItem(29, createTierSelectIcon(BrushTier.COMMON, LootCategory.MINE, kind));
        inv.setItem(31, createTierSelectIcon(BrushTier.EPIC, LootCategory.MINE, kind));
        inv.setItem(33, createTierSelectIcon(BrushTier.LEGENDARY, LootCategory.MINE, kind));

        inv.setItem(36, createButton(
                Material.RED_SHULKER_BOX,
                "&c&lНовый год",
                List.of("&7Подарки-шалкеры по редкости", "", "&eНажмите")
        ));
        inv.setItem(38, createButton(
                Material.HOPPER,
                "&f&lШкибиди Туалет",
                List.of("&7" + (loot ? "Предметы с туалетов" : "Шансы предметов"), "", "&eНажмите")
        ));
        inv.setItem(40, createButton(
                Material.MAGMA_BLOCK,
                "&#7DF9FF&lГейзер",
                List.of("&7Лут жерла по редкости", "", "&eНажмите")
        ));
        inv.setItem(42, createButton(
                Material.GOLDEN_HELMET,
                "&e&lРоналду SIUU",
                List.of("&7" + (loot ? "Предметы CR7" : "Шансы предметов"), "", "&eНажмите")
        ));
        inv.setItem(44, createButton(
                Material.FISHING_ROD,
                "&b&lРыбалка",
                List.of("&7Удочки: " + action, "", "&eНажмите")
        ));
        openLater(player, inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }

    public void openMineMenu(Player player) {
        openMineMenu(player, GuiKind.LOOT);
    }

    public void openCauldronMenu(Player player) {
        openCauldronMenu(player, GuiKind.LOOT);
    }

    public void openExcavationMenu(Player player) {
        openExcavationMenu(player, GuiKind.LOOT);
    }

    public void openFishingMenu(Player player) {
        openFishingMenu(player, GuiKind.LOOT);
    }

    @SuppressWarnings("deprecation")
    public void openMineMenu(Player player, GuiKind kind) {
        openCategoryMenu(player, LootCategory.MINE, kind, Material.ANVIL, Material.ORANGE_STAINED_GLASS_PANE,
                "&#FFB81C&lШахта Древних", "&8Шахта Древних",
                List.of("&7Постоянный ивент на &f0 ~ 0&7.", "&7Кузнец отдаёт лут за жеоды."));
    }

    @SuppressWarnings("deprecation")
    public void openCauldronMenu(Player player, GuiKind kind) {
        openCategoryMenu(player, LootCategory.CAULDRON, kind, Material.CAULDRON, Material.PURPLE_STAINED_GLASS_PANE,
                "&#C850FF&lКотёл Алхимика", "&8Котёл Алхимика",
                List.of("&7Награды за реагенты редкости."));
    }

    @SuppressWarnings("deprecation")
    public void openExcavationMenu(Player player, GuiKind kind) {
        openCategoryMenu(player, LootCategory.EXCAVATION, kind, Material.SAND, Material.YELLOW_STAINED_GLASS_PANE,
                "&#FFD614&lПляжные Раскопки", "&8Пляжные Раскопки",
                List.of("&7Лут с подозрительного песка."));
    }

    @SuppressWarnings("deprecation")
    public void openFishingMenu(Player player, GuiKind kind) {
        openCategoryMenu(player, LootCategory.FISHING, kind, Material.FISHING_ROD, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                "&b&lРыбалка", "&8Рыбалка",
                List.of("&7Лут удочек трёх редкостей."));
    }

    @SuppressWarnings("deprecation")
    private void openCategoryMenu(Player player, LootCategory category, GuiKind kind, Material icon, Material borderMat,
                                  String title, String invTitle, List<String> extraLore) {
        boolean loot = kind == GuiKind.LOOT;
        Inventory inv = Bukkit.createInventory(
                new EventMenuHolder(category, kind),
                27,
                ColorUtil.color((loot ? "&8Лут: " : "&8Шансы: ") + invTitle.replace("&8", ""))
        );
        ItemStack border = createButton(borderMat, "&8", List.of());
        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, (i < 9 || i >= 18) ? border : filler);
        }
        List<String> lore = new ArrayList<>(extraLore);
        lore.add("");
        lore.add(loot ? "&fРежим: &aпредметы лута" : "&fРежим: &6шансы");
        lore.add("&fВыберите редкость ниже");
        inv.setItem(4, createButton(icon, title, lore));
        inv.setItem(11, createTierSelectIcon(BrushTier.COMMON, category, kind));
        inv.setItem(13, createTierSelectIcon(BrushTier.EPIC, category, kind));
        inv.setItem(15, createTierSelectIcon(BrushTier.LEGENDARY, category, kind));
        inv.setItem(18, createButton(Material.ARROW, "&e« К списку ивентов", List.of("&7Тот же режим")));
        inv.setItem(22, createButton(
                loot ? Material.GOLD_INGOT : Material.CHEST,
                loot ? "&6Перейти к шансам" : "&aПерейти к луту",
                List.of("&7Другое меню этого ивента")
        ));
        inv.setItem(26, createButton(Material.BARRIER, "&cЗакрыть", List.of("&7Закрыть меню")));
        openLater(player, inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
    }

    @SuppressWarnings("deprecation")
    public void openGeyserMenu(Player player, GuiKind kind) {
        boolean loot = kind == GuiKind.LOOT;
        Inventory inv = Bukkit.createInventory(
                new WindMenuHolder(kind),
                27,
                ColorUtil.color(loot ? "&8Лут: Гейзер" : "&8Шансы: Гейзер")
        );
        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(4, createButton(
                Material.MAGMA_BLOCK,
                "&#7DF9FF&lГейзер — " + (loot ? "лут" : "шансы"),
                List.of(
                        "&7Одна редкость на весь ивент.",
                        "&7Пулы: обычный / редкий / эпик / легенда.",
                        "",
                        "&eВыберите пул"
                )
        ));
        inv.setItem(10, createButton(Material.IRON_INGOT, "&fОбычный пул", List.of("&7wind.loot.common")));
        inv.setItem(12, createButton(Material.LAPIS_LAZULI, "&9Редкий пул", List.of("&7wind.loot.rare")));
        inv.setItem(14, createButton(Material.AMETHYST_SHARD, "&dЭпический пул", List.of("&7wind.loot.epic")));
        inv.setItem(16, createButton(Material.GOLD_INGOT, "&6Легендарный пул", List.of("&7wind.loot.legendary")));
        inv.setItem(18, createButton(Material.ARROW, "&e« К списку ивентов", List.of("&7Тот же режим")));
        inv.setItem(22, createButton(
                loot ? Material.GOLD_INGOT : Material.CHEST,
                loot ? "&6Перейти к шансам" : "&aПерейти к луту",
                List.of("&7Другое меню гейзера")
        ));
        openLater(player, inv);
    }

    @SuppressWarnings("deprecation")
    public void openNewyearMenu(Player player, GuiKind kind) {
        boolean loot = kind == GuiKind.LOOT;
        Inventory inv = Bukkit.createInventory(
                new NewyearMenuHolder(kind),
                27,
                ColorUtil.color(loot ? "&8Лут: Новый год" : "&8Шансы: Новый год")
        );
        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(4, createButton(
                Material.RED_SHULKER_BOX,
                "&c&lНовый год — " + (loot ? "лут" : "шансы"),
                List.of("&7Цвет шалкера = редкость подарка.", "&eВыберите пул")
        ));
        inv.setItem(10, createButton(Material.WHITE_SHULKER_BOX, "&fОбычный", List.of("&7newyear.loot.common")));
        inv.setItem(12, createButton(Material.LIGHT_BLUE_SHULKER_BOX, "&9Редкий", List.of("&7newyear.loot.rare")));
        inv.setItem(14, createButton(Material.MAGENTA_SHULKER_BOX, "&dЭпический", List.of("&7newyear.loot.epic")));
        inv.setItem(16, createButton(Material.RED_SHULKER_BOX, "&6Легендарный", List.of("&7newyear.loot.legendary")));
        inv.setItem(18, createButton(Material.ARROW, "&e« К списку ивентов", List.of("&7Тот же режим")));
        inv.setItem(22, createButton(
                loot ? Material.GOLD_INGOT : Material.CHEST,
                loot ? "&6Перейти к шансам" : "&aПерейти к луту",
                List.of("&7Другое меню")
        ));
        openLater(player, inv);
    }

    @SuppressWarnings("deprecation")
    public void openFlatLootMenu(Player player, String path, String title, Material icon, boolean yamlList) {
        openFlatLootMenu(player, path, title, icon, yamlList, GuiKind.LOOT);
    }

    @SuppressWarnings("deprecation")
    public void openFlatLootMenu(Player player, String path, String title, Material icon, boolean yamlList, GuiKind kind) {
        boolean loot = kind == GuiKind.LOOT;
        FlatLootHolder holder = new FlatLootHolder(path, title, icon, yamlList, kind);
        Inventory inv = Bukkit.createInventory(
                holder,
                54,
                ColorUtil.color((loot ? "&8Лут: " : "&8Шансы: ") + title)
        );
        ItemStack border = createButton(Material.PURPLE_STAINED_GLASS_PANE, "&5", List.of());
        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of());
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, (i < 9 || i >= 45) ? border : filler);
        }
        if (loot) {
            inv.setItem(4, createButton(icon, "&a&l" + title + " — предметы", List.of(
                    "&7Клик в инвентаре — добавить.",
                    "&7Shift+ЛКМ по слоту — удалить.",
                    "&7Шансы правятся в другом меню."
            )));
        } else {
            inv.setItem(4, createButton(icon, "&6&l" + title + " — шансы", List.of(
                    "&7ЛКМ: +5%   ПКМ: -5%",
                    "&7Предметы не добавляются здесь."
            )));
        }

        List<FlatLootEntry> entries = readFlatLoot(holder);
        holder.keys.clear();
        for (FlatLootEntry e : entries) {
            holder.keys.add(e.key);
        }
        int max = Math.min(entries.size(), 36);
        for (int i = 0; i < max; i++) {
            FlatLootEntry e = entries.get(i);
            ItemStack item = new ItemStack(e.material == null ? Material.STONE : e.material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtil.color("&f" + e.material.name() + " &7[" + (int) e.chance + "%]"));
                if (loot) {
                    meta.setLore(List.of(
                            ColorUtil.color("&7Мин: &f" + e.min + " &7Макс: &f" + e.max),
                            ColorUtil.color("&7Shift+ЛКМ — удалить")
                    ));
                } else {
                    meta.setLore(List.of(
                            ColorUtil.color("&7Шанс: &#34D711" + (int) e.chance + "%"),
                            ColorUtil.color("&7ЛКМ +5% &8· &7ПКМ -5%")
                    ));
                }
                item.setItemMeta(meta);
            }
            inv.setItem(9 + i, item);
        }
        inv.setItem(45, createButton(Material.ARROW, "&e« Назад", List.of("&7К списку ивентов")));
        inv.setItem(49, createButton(
                loot ? Material.GOLD_INGOT : Material.CHEST,
                loot ? "&6К шансам" : "&aК предметам",
                List.of("&7Другое меню этой таблицы")
        ));
        inv.setItem(53, createButton(Material.BARRIER, "&cЗакрыть", List.of("&7Закрыть меню")));
        openLater(player, inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 1.2f);
    }

    private record FlatLootEntry(String key, Material material, int min, int max, double chance) {
    }

    private List<FlatLootEntry> readFlatLoot(FlatLootHolder holder) {
        List<FlatLootEntry> out = new ArrayList<>();
        if (holder.yamlList) {
            List<Map<?, ?>> lootList = plugin.getConfig().getMapList(holder.path);
            for (int i = 0; i < lootList.size(); i++) {
                Map<?, ?> entry = lootList.get(i);
                String matName = String.valueOf(entry.get("material"));
                Material mat = Material.matchMaterial(matName);
                if (mat == null || mat.isAir()) {
                    mat = Material.STONE;
                }
                double chance = entry.get("chance") instanceof Number n ? n.doubleValue() : 25.0;
                int min = entry.get("min") instanceof Number n ? n.intValue() : 1;
                int max = entry.get("max") instanceof Number n ? n.intValue() : min;
                out.add(new FlatLootEntry(String.valueOf(i), mat, min, max, chance));
            }
            return out;
        }
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(holder.path);
        if (sec == null) {
            return out;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection item = sec.getConfigurationSection(key);
            if (item == null) {
                continue;
            }
            Material mat = Material.matchMaterial(item.getString("material", "STONE"));
            if (mat == null || mat.isAir()) {
                mat = Material.STONE;
            }
            out.add(new FlatLootEntry(
                    key,
                    mat,
                    Math.max(1, item.getInt("min", 1)),
                    Math.max(1, item.getInt("max", 1)),
                    item.getDouble("chance", 25.0)
            ));
        }
        return out;
    }

    private void handleFlatLootClick(InventoryClickEvent event, Player player, FlatLootHolder holder, int index) {
        List<FlatLootEntry> entries = readFlatLoot(holder);
        if (index < 0 || index >= entries.size()) {
            return;
        }
        FlatLootEntry entry = entries.get(index);
        if (holder.kind == GuiKind.LOOT) {
            if (event.isShiftClick() && event.isLeftClick()) {
                removeFlatLoot(holder, index, entry.key);
                player.sendMessage(ColorUtil.color("&cУдалён предмет из лута «" + holder.title + "»."));
                player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 0.8f, 1.0f);
                openFlatLootMenu(player, holder.path, holder.title, holder.icon, holder.yamlList, holder.kind);
            }
            return;
        }
        double chance = entry.chance;
        if (event.isRightClick()) {
            chance = Math.max(0, chance - 5.0);
        } else {
            chance = Math.min(100, chance + 5.0);
        }
        setFlatLootChance(holder, index, entry.key, chance);
        player.sendMessage(ColorUtil.color("&eШанс: " + (int) chance + "%"));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, event.isRightClick() ? 0.9f : 1.1f);
        openFlatLootMenu(player, holder.path, holder.title, holder.icon, holder.yamlList, holder.kind);
    }

    @SuppressWarnings("unchecked")
    private void setFlatLootChance(FlatLootHolder holder, int index, String key, double chance) {
        if (holder.yamlList) {
            List<Map<?, ?>> lootList = plugin.getConfig().getMapList(holder.path);
            if (index >= 0 && index < lootList.size()) {
                ((Map<String, Object>) lootList.get(index)).put("chance", chance);
                plugin.getConfig().set(holder.path, lootList);
            }
        } else {
            plugin.getConfig().set(holder.path + "." + key + ".chance", chance);
        }
        plugin.saveConfig();
        plugin.getPluginConfig().reload();
    }

    @SuppressWarnings("unchecked")
    private void removeFlatLoot(FlatLootHolder holder, int index, String key) {
        if (holder.yamlList) {
            List<Map<?, ?>> lootList = plugin.getConfig().getMapList(holder.path);
            if (index >= 0 && index < lootList.size()) {
                lootList.remove(index);
                plugin.getConfig().set(holder.path, lootList);
            }
        } else {
            plugin.getConfig().set(holder.path + "." + key, null);
        }
        plugin.saveConfig();
        plugin.getPluginConfig().reload();
    }

    private void addFlatLoot(Player player, FlatLootHolder holder, ItemStack clickedItem) {
        if (holder.yamlList) {
            List<Map<?, ?>> lootList = plugin.getConfig().getMapList(holder.path);
            if (lootList.size() >= 36) {
                player.sendMessage(ColorUtil.color("&e┠ &cСписок лута заполнен (максимум 36 слотов)."));
                return;
            }
            Map<String, Object> newEntry = new HashMap<>();
            newEntry.put("material", clickedItem.getType().name());
            newEntry.put("min", Math.max(1, clickedItem.getAmount()));
            newEntry.put("max", Math.max(1, clickedItem.getAmount()));
            newEntry.put("chance", 25.0);
            lootList.add(newEntry);
            plugin.getConfig().set(holder.path, lootList);
        } else {
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection(holder.path);
            if (sec != null && sec.getKeys(false).size() >= 36) {
                player.sendMessage(ColorUtil.color("&e┠ &cСписок лута заполнен (максимум 36 слотов)."));
                return;
            }
            String key = "gui_" + Integer.toHexString((int) System.nanoTime());
            plugin.getConfig().set(holder.path + "." + key + ".material", clickedItem.getType().name());
            plugin.getConfig().set(holder.path + "." + key + ".min", Math.max(1, clickedItem.getAmount()));
            plugin.getConfig().set(holder.path + "." + key + ".max", Math.max(1, clickedItem.getAmount()));
            plugin.getConfig().set(holder.path + "." + key + ".chance", 25.0);
        }
        plugin.saveConfig();
        plugin.getPluginConfig().reload();
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
        player.sendMessage(ColorUtil.color("&a# " + clickedItem.getType().name() + " добавлен в лут «" + holder.title + "»."));
        openFlatLootMenu(player, holder.path, holder.title, holder.icon, holder.yamlList, holder.kind);
    }

    private ItemStack createTierSelectIcon(BrushTier tier, LootCategory category, GuiKind kind) {
        BrushLootManager mgr = plugin.getBrushLootManager();
        double weight = mgr.getTierWeight(tier, category);
        int count = mgr.getLootEntries(tier, category).size();
        boolean loot = kind == GuiKind.LOOT;
        String click = loot ? "&eНажмите — предметы лута" : "&eНажмите — шансы";
        return switch (category) {
            case CAULDRON -> {
                Material iconMat = switch (tier) {
                    case COMMON -> Material.SLIME_BALL;
                    case EPIC -> Material.AMETHYST_SHARD;
                    case LEGENDARY -> Material.BLAZE_POWDER;
                };
                yield createButton(iconMat, mgr.getReagentDisplayName(tier), List.of(
                        "&7Ивент: &dКотёл Алхимика",
                        "&7Предметов: &e" + count,
                        "&7Шанс реагента: &#34D711" + weight + "%",
                        "",
                        click
                ));
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
                yield createButton(iconMat, mgr.getGeodeDisplayName(tier), List.of(
                        "&7Ивент: &#FFB81CШахта Древних",
                        "&7Предметов: &e" + count,
                        "&7Шанс жеоды: &#34D711" + weight + "%",
                        "",
                        click
                ));
            }
            case FISHING -> createButton(Material.FISHING_ROD, mgr.getFishingRodTitle(tier), List.of(
                    "&7Ивент: &bРыбалка",
                    "&7Предметов: &e" + count,
                    "&7Шанс удочки: &#34D711" + weight + "%",
                    "",
                    click
            ));
            case EXCAVATION -> {
                Material iconMat = Material.matchMaterial("BRUSH");
                if (iconMat == null) {
                    iconMat = Material.FEATHER;
                }
                yield createButton(iconMat, tier.getItemTitle(), List.of(
                        "&7Ивент: &eПляжные Раскопки",
                        "&7Предметов: &e" + count,
                        "&7Шанс кисточки: &#34D711" + weight + "%",
                        "",
                        click
                ));
            }
        };
    }

    public void openTierMenu(Player player, BrushTier tier) {
        openTierMenu(player, tier, LootCategory.EXCAVATION, GuiKind.LOOT);
    }

    public void openTierMenu(Player player, BrushTier tier, boolean cauldron) {
        openTierMenu(player, tier, cauldron ? LootCategory.CAULDRON : LootCategory.EXCAVATION, GuiKind.LOOT);
    }

    public void openTierMenu(Player player, BrushTier tier, LootCategory category) {
        openTierMenu(player, tier, category, GuiKind.LOOT);
    }

    @SuppressWarnings("deprecation")
    public void openTierMenu(Player player, BrushTier tier, LootCategory category, GuiKind kind) {
        BrushLootManager mgr = plugin.getBrushLootManager();
        String titleLabel = mgr.getCategoryItemTitle(tier, category);
        boolean loot = kind == GuiKind.LOOT;
        Inventory inv = Bukkit.createInventory(
                new TierMenuHolder(tier, category, kind),
                54,
                ColorUtil.color((loot ? "&8Лут: " : "&8Шансы: ") + titleLabel)
        );

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
                lore.add(ColorUtil.color("&fШанс: &#34D711" + entry.chance() + "%"));
                if (loot) {
                    lore.add(ColorUtil.color("&7Shift+ЛКМ или Q — удалить"));
                } else {
                    lore.add(ColorUtil.color("&7ЛКМ +5% &8· &7ПКМ -5%"));
                    lore.add(ColorUtil.color("&7Shift ±1%"));
                }
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(i, display);
        }

        ItemStack sep = createButton(Material.BLACK_STAINED_GLASS_PANE, "&8", List.of());
        for (int i = 36; i < 54; i++) {
            inv.setItem(i, sep);
        }

        String backTitle = switch (category) {
            case EXCAVATION -> "Пляжных Раскопок";
            case CAULDRON -> "Котла Алхимика";
            case MINE -> "Шахты Древних";
            case FISHING -> "Рыбалки";
        };
        inv.setItem(45, createButton(
                Material.ARROW,
                "&e« Назад в меню " + backTitle,
                List.of("&7ЛКМ — меню ивента", "&7ПКМ — выбор лут/шансы")
        ));

        double weight = mgr.getTierWeight(tier, category);
        String itemKind = switch (category) {
            case EXCAVATION -> "кисточки";
            case CAULDRON -> "реагента";
            case MINE -> "жеоды";
            case FISHING -> "удочки";
        };

        if (loot) {
            inv.setItem(49, createButton(
                    Material.GOLD_INGOT,
                    "&6К шансам этой редкости",
                    List.of("&7Проценты предметов и вес " + itemKind)
            ));
            inv.setItem(50, createButton(
                    Material.RED_WOOL,
                    "&4Очистить весь лут этой редкости",
                    List.of("&7Shift+ЛКМ или Q — подтвердить")
            ));
            inv.setItem(53, createButton(
                    Material.EMERALD,
                    "&aКак добавить предмет?",
                    List.of(
                            "&fНажмите предмет в своём инвентаре.",
                            "&7Шанс по умолчанию 25% — правьте в меню шансов."
                    )
            ));
        } else {
            inv.setItem(47, createButton(
                    Material.RED_STAINED_GLASS_PANE,
                    "&cВес " + itemKind + " -5%",
                    List.of("&7Сейчас: &e" + weight + "%")
            ));
            inv.setItem(48, createButton(
                    Material.PAPER,
                    titleLabel,
                    List.of("&7Вес появления: &#34D711" + weight + "%", "&7Это шанс самой " + itemKind + ".")
            ));
            inv.setItem(49, createButton(
                    Material.LIME_STAINED_GLASS_PANE,
                    "&aВес " + itemKind + " +5%",
                    List.of("&7Сейчас: &e" + weight + "%")
            ));
            inv.setItem(53, createButton(
                    Material.CHEST,
                    "&aК предметам этой редкости",
                    List.of("&7Добавить / убрать лут")
            ));
        }

        openLater(player, inv);
    }

    public boolean handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (holder instanceof HubHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return true;
            }
            if (event.getSlot() == 11) {
                openEventList(player, GuiKind.LOOT);
            } else if (event.getSlot() == 15) {
                openEventList(player, GuiKind.CHANCES);
            }
            return true;
        }

        if (holder instanceof MainMenuHolder mainHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return true;
            }
            GuiKind kind = mainHolder.getKind();
            int slot = event.getSlot();
            switch (slot) {
                case 0 -> openMainMenu(player);
                case 9 -> openExcavationMenu(player, kind);
                case 11 -> openTierMenu(player, BrushTier.COMMON, LootCategory.EXCAVATION, kind);
                case 13 -> openTierMenu(player, BrushTier.EPIC, LootCategory.EXCAVATION, kind);
                case 15 -> openTierMenu(player, BrushTier.LEGENDARY, LootCategory.EXCAVATION, kind);
                case 18 -> openCauldronMenu(player, kind);
                case 20 -> openTierMenu(player, BrushTier.COMMON, LootCategory.CAULDRON, kind);
                case 22 -> openTierMenu(player, BrushTier.EPIC, LootCategory.CAULDRON, kind);
                case 24 -> openTierMenu(player, BrushTier.LEGENDARY, LootCategory.CAULDRON, kind);
                case 27 -> openMineMenu(player, kind);
                case 29 -> openTierMenu(player, BrushTier.COMMON, LootCategory.MINE, kind);
                case 31 -> openTierMenu(player, BrushTier.EPIC, LootCategory.MINE, kind);
                case 33 -> openTierMenu(player, BrushTier.LEGENDARY, LootCategory.MINE, kind);
                case 36 -> openNewyearMenu(player, kind);
                case 38 -> openFlatLootMenu(player, "skibidi.loot", "Шкибиди Туалет", Material.HOPPER, false, kind);
                case 40 -> openGeyserMenu(player, kind);
                case 42 -> openFlatLootMenu(player, "ronaldo.loot", "Роналду SIUU", Material.GOLDEN_HELMET, false, kind);
                case 44 -> openFishingMenu(player, kind);
                default -> {
                }
            }
            return true;
        }

        if (holder instanceof WindMenuHolder windHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return true;
            }
            GuiKind kind = windHolder.getKind();
            int slot = event.getSlot();
            if (slot == 18) {
                openEventList(player, kind);
                return true;
            }
            if (slot == 22) {
                openGeyserMenu(player, kind == GuiKind.LOOT ? GuiKind.CHANCES : GuiKind.LOOT);
                return true;
            }
            String path = switch (slot) {
                case 10 -> "wind.loot.common";
                case 12 -> "wind.loot.rare";
                case 14 -> "wind.loot.epic";
                case 16 -> "wind.loot.legendary";
                default -> null;
            };
            String title = switch (slot) {
                case 10 -> "Гейзер · обычный";
                case 12 -> "Гейзер · редкий";
                case 14 -> "Гейзер · эпик";
                case 16 -> "Гейзер · легенда";
                default -> null;
            };
            if (path != null) {
                openFlatLootMenu(player, path, title, Material.MAGMA_BLOCK, false, kind);
            }
            return true;
        }

        if (holder instanceof NewyearMenuHolder nyHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return true;
            }
            GuiKind kind = nyHolder.getKind();
            int slot = event.getSlot();
            if (slot == 18) {
                openEventList(player, kind);
                return true;
            }
            if (slot == 22) {
                openNewyearMenu(player, kind == GuiKind.LOOT ? GuiKind.CHANCES : GuiKind.LOOT);
                return true;
            }
            String path = switch (slot) {
                case 10 -> "newyear.loot.common";
                case 12 -> "newyear.loot.rare";
                case 14 -> "newyear.loot.epic";
                case 16 -> "newyear.loot.legendary";
                default -> null;
            };
            String title = switch (slot) {
                case 10 -> "Новый год · обычный";
                case 12 -> "Новый год · редкий";
                case 14 -> "Новый год · эпик";
                case 16 -> "Новый год · легенда";
                default -> null;
            };
            Material icon = switch (slot) {
                case 10 -> Material.WHITE_SHULKER_BOX;
                case 12 -> Material.LIGHT_BLUE_SHULKER_BOX;
                case 14 -> Material.MAGENTA_SHULKER_BOX;
                case 16 -> Material.RED_SHULKER_BOX;
                default -> Material.SHULKER_BOX;
            };
            if (path != null) {
                openFlatLootMenu(player, path, title, icon, false, kind);
            }
            return true;
        }

        if (holder instanceof FlatLootHolder flatHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                if (event.getClickedInventory() == player.getInventory() && flatHolder.kind == GuiKind.LOOT) {
                    ItemStack clickedItem = event.getCurrentItem();
                    if (clickedItem == null || clickedItem.getType().isAir()) {
                        return true;
                    }
                    if (plugin.getBrushLootManager().getBrushTier(clickedItem) != null) {
                        player.sendMessage(ColorUtil.color("&e┠ &cСам предмет ивента нельзя добавить в лут."));
                        return true;
                    }
                    addFlatLoot(player, flatHolder, clickedItem);
                    return true;
                }
                return true;
            }
            int slot = event.getSlot();
            if (slot == 45) {
                if (flatHolder.path.startsWith("wind.loot.")) {
                    openGeyserMenu(player, flatHolder.kind);
                } else if (flatHolder.path.startsWith("newyear.loot.")) {
                    openNewyearMenu(player, flatHolder.kind);
                } else {
                    openEventList(player, flatHolder.kind);
                }
                return true;
            }
            if (slot == 49) {
                openFlatLootMenu(player, flatHolder.path, flatHolder.title, flatHolder.icon, flatHolder.yamlList,
                        flatHolder.kind == GuiKind.LOOT ? GuiKind.CHANCES : GuiKind.LOOT);
                return true;
            }
            if (slot == 53) {
                return true;
            }
            if (slot >= 9 && slot <= 44) {
                handleFlatLootClick(event, player, flatHolder, slot - 9);
            }
            return true;
        }

        if (holder instanceof EventMenuHolder eventHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return true;
            }
            LootCategory category = eventHolder.getCategory();
            GuiKind kind = eventHolder.getKind();
            int slot = event.getSlot();
            switch (slot) {
                case 11 -> openTierMenu(player, BrushTier.COMMON, category, kind);
                case 13 -> openTierMenu(player, BrushTier.EPIC, category, kind);
                case 15 -> openTierMenu(player, BrushTier.LEGENDARY, category, kind);
                case 18 -> openEventList(player, kind);
                case 22 -> openCategoryByKind(player, category, kind == GuiKind.LOOT ? GuiKind.CHANCES : GuiKind.LOOT);
                default -> {
                }
            }
            return true;
        }

        if (holder instanceof TierMenuHolder tierHolder) {
            event.setCancelled(true);
            BrushTier tier = tierHolder.getTier();
            LootCategory category = tierHolder.getCategory();
            GuiKind kind = tierHolder.getKind();
            BrushLootManager mgr = plugin.getBrushLootManager();

            if (event.getClickedInventory() == player.getInventory()) {
                if (kind != GuiKind.LOOT) {
                    return true;
                }
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType().isAir()) {
                    return true;
                }
                if (mgr.getBrushTier(clickedItem) != null) {
                    player.sendMessage(ColorUtil.color("&e┠ &cСам предмет ивента нельзя добавить в лут."));
                    return true;
                }
                if (mgr.getLootEntries(tier, category).size() >= 36) {
                    player.sendMessage(ColorUtil.color("&e┠ &cСписок лута заполнен (максимум 36 слотов)."));
                    return true;
                }
                mgr.addLootItem(tier, clickedItem.clone(), 25.0, category);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
                openTierMenu(player, tier, category, kind);
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
                    openCategoryByKind(player, category, kind);
                }
                return true;
            }
            if (kind == GuiKind.LOOT) {
                if (slot == 49) {
                    openTierMenu(player, tier, category, GuiKind.CHANCES);
                    return true;
                }
                if (slot == 50) {
                    ClickType click = event.getClick();
                    if (click.isShiftClick() || click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.MIDDLE) {
                        mgr.clearLoot(tier, category);
                        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
                        openTierMenu(player, tier, category, kind);
                    } else {
                        player.sendMessage(ColorUtil.color("&e┠ &cНажмите &#E0C912Shift+ЛКМ &cили &#E0C912Q&c для очистки."));
                    }
                    return true;
                }
            } else {
                if (slot == 47) {
                    mgr.setTierWeight(tier, mgr.getTierWeight(tier, category) - 5.0, category);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                    openTierMenu(player, tier, category, kind);
                    return true;
                }
                if (slot == 49) {
                    mgr.setTierWeight(tier, mgr.getTierWeight(tier, category) + 5.0, category);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.3f);
                    openTierMenu(player, tier, category, kind);
                    return true;
                }
                if (slot == 53) {
                    openTierMenu(player, tier, category, GuiKind.LOOT);
                    return true;
                }
            }

            List<BrushLootManager.BrushLootEntry> entries = mgr.getLootEntries(tier, category);
            if (slot >= 0 && slot < entries.size()) {
                BrushLootManager.BrushLootEntry entry = entries.get(slot);
                ClickType click = event.getClick();
                if (kind == GuiKind.LOOT) {
                    if (click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.MIDDLE
                            || (click.isShiftClick() && click.isLeftClick())) {
                        mgr.removeLootItem(tier, slot + 1, category);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.2f);
                        openTierMenu(player, tier, category, kind);
                    }
                    return true;
                }
                double next = entry.chance();
                if (click == ClickType.SHIFT_LEFT) {
                    next += 1.0;
                } else if (click == ClickType.SHIFT_RIGHT) {
                    next -= 1.0;
                } else if (click == ClickType.RIGHT) {
                    next -= 5.0;
                } else {
                    next += 5.0;
                }
                mgr.updateLootEntryChance(tier, slot, next, category);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
                openTierMenu(player, tier, category, kind);
            }
            return true;
        }

        return false;
    }

    private void openCategoryByKind(Player player, LootCategory category, GuiKind kind) {
        switch (category) {
            case EXCAVATION -> openExcavationMenu(player, kind);
            case CAULDRON -> openCauldronMenu(player, kind);
            case MINE -> openMineMenu(player, kind);
            case FISHING -> openFishingMenu(player, kind);
        }
    }

    public boolean handleInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof HubHolder || holder instanceof MainMenuHolder || holder instanceof EventMenuHolder
                || holder instanceof TierMenuHolder || holder instanceof FlatLootHolder
                || holder instanceof WindMenuHolder || holder instanceof NewyearMenuHolder) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    private void openLater(Player player, Inventory inv) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.openInventory(inv);
            }
        });
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
