package ru.geyserevent.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.boss.BossAbilityType;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.event.DamageLeaderboard;
import ru.geyserevent.event.EventType;
import ru.geyserevent.event.InfernalRiftManager;
import ru.geyserevent.excavation.BrushLootManager;
import ru.geyserevent.excavation.BrushTier;
import ru.geyserevent.util.ColorUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Админ-команды управления ивентами «Адский Разлом» и «Пляжные Раскопки» (/rift),
 * а также открытие GUI-меню настройки лута и шансов кисточек (/rift menu).
 */
public class RiftCommand implements CommandExecutor, TabCompleter {

    private final GeyserEventPlugin plugin;

    public RiftCommand(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("geyserevent.admin")) {
            sender.sendMessage(ColorUtil.color("&e┠ &cНедостаточно прав."));
            return true;
        }

        PluginConfig cfg = plugin.getPluginConfig();
        InfernalRiftManager manager = plugin.getRiftManager();

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendAdminHelp(sender, label, cfg);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "menu", "gui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cМеню доступно только в игре!"));
                    return true;
                }
                if (args.length >= 2) {
                    BrushTier tier = BrushTier.fromString(args[1]);
                    if (tier != null) {
                        plugin.getBrushLootGuiManager().openTierMenu(player, tier);
                        return true;
                    }
                }
                plugin.getBrushLootGuiManager().openMainMenu(player);
                return true;
            }

            case "start" -> {
                EventType type = null;
                if (args.length >= 2) {
                    type = EventType.fromString(args[1]);
                    if (type == null) {
                        sender.sendMessage(ColorUtil.color("&e┠ &cУкажите ивент: &#E0C912rift &c(Разлом), &#E0C912excavation &c(Раскопки) или &#E0C912cauldron &c(Котёл Алхимика)."));
                        return true;
                    }
                }
                boolean started = (type != null)
                        ? manager.startEventRandomInConfiguredWorld(type)
                        : manager.startEventRandomInConfiguredWorld();
                if (!started) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cНе удалось запустить ивент в мире &#E0C912" + cfg.getWorldName() + "&c."));
                }
                return true;
            }

            case "start-here", "starthere" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cТолько для игроков."));
                    return true;
                }
                EventType type = EventType.INFERNAL_RIFT;
                if (args.length >= 2) {
                    EventType parsed = EventType.fromString(args[1]);
                    if (parsed == null) {
                        sender.sendMessage(ColorUtil.color("&e┠ &cУкажите ивент: &#E0C912rift&c, &#E0C912excavation &cили &#E0C912cauldron&c."));
                        return true;
                    }
                    type = parsed;
                }
                manager.startEventAt(player.getLocation(), type);
                return true;
            }

            case "stop" -> {
                if (!manager.isActive()) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cИвент не активен."));
                    return true;
                }
                manager.stopEvent(false);
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Ивент остановлен и схема откачена."));
                return true;
            }

            case "brushloot", "loot" -> {
                if (args.length == 1 && sender instanceof Player player) {
                    plugin.getBrushLootGuiManager().openMainMenu(player);
                    return true;
                }
                if (args.length == 2 && sender instanceof Player player) {
                    BrushTier tier = BrushTier.fromString(args[1]);
                    if (tier != null) {
                        plugin.getBrushLootGuiManager().openTierMenu(player, tier);
                        return true;
                    }
                }
                handleBrushLootSubcommand(sender, label, args);
                return true;
            }

            case "givebrush", "brush" -> {
                handleGiveBrushSubcommand(sender, label, args, false);
                return true;
            }

            case "givereagent", "reagent" -> {
                handleGiveBrushSubcommand(sender, label, args, true);
                return true;
            }

            case "ability", "cast" -> {
                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.color("&e┠ &fИспользование: &#E0C912/" + label + " ability <pull|push|gather|minions>"));
                    return true;
                }
                LivingEntity boss = manager.getBossEntity();
                if (boss == null || boss.isDead()) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cБосс не активен."));
                    return true;
                }
                BossAbilityType type = switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "pull", "притянуть" -> BossAbilityType.PULL;
                    case "push", "оттолкнуть" -> BossAbilityType.PUSH;
                    case "gather", "vortex", "стяжка", "собрать" -> BossAbilityType.GATHER;
                    case "minions", "summon", "приспешники" -> BossAbilityType.SUMMON_MINIONS;
                    default -> null;
                };
                if (type == null) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cДоступно: &#E0C912pull, push, gather, minions"));
                    return true;
                }
                plugin.getAbilityController().executeAbility(boss, type);
                return true;
            }

            case "setworld" -> {
                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.color("&e┠ &fИспользование: &#E0C912/" + label + " setworld <мир>"));
                    return true;
                }
                String newWorld = args[1];
                cfg.setWorldNameAndSave(newWorld);
                sender.sendMessage(ColorUtil.color("&e┠ &fМир ивента: &#E0C912" + newWorld));
                return true;
            }

            case "saveschem" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cТолько для игроков."));
                    return true;
                }
                int radius = 7;
                if (args.length >= 2) {
                    try {
                        radius = Math.max(2, Math.min(20, Integer.parseInt(args[1])));
                    } catch (NumberFormatException ignored) {
                    }
                }
                try {
                    int saved = plugin.getSchematicManager().saveCustomSchematicFromWorld(player.getLocation(), radius);
                    sender.sendMessage(ColorUtil.color("&e┠ &#34D711Сохранено блоков: &#E0C912" + saved));
                } catch (IOException ex) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cОшибка: " + ex.getMessage()));
                }
                return true;
            }

            case "status", "info" -> {
                if (manager.isActive() && manager.getRiftCenterLocation() != null) {
                    Location loc = manager.getRiftCenterLocation();
                    sender.sendMessage(ColorUtil.color("&e┠ &fАктивный ивент: &#34D711" + manager.getActiveEventType().getDisplayName()));
                    if (manager.getActiveEventType() == EventType.INFERNAL_RIFT) {
                        sender.sendMessage(ColorUtil.color("&e┠ &fЗдоровье: &c" + (int) Math.ceil(manager.getBossCurrentHealth()) + "/" + (int) Math.ceil(manager.getBossMaxHealth()) + " ❤"));
                    } else {
                        sender.sendMessage(ColorUtil.color("&e┠ &fДо следующей кисточки: &#E0C912" + manager.getSecondsUntilNextBrush() + " сек."));
                    }
                    sender.sendMessage(ColorUtil.color("&e┠ &fКоординаты: &#E0C912" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
                } else {
                    sender.sendMessage(ColorUtil.color("&e┠ &fДо появления ивента: &#E0C912" + ColorUtil.formatDurationVerbose(manager.getSecondsUntilNextStart())));
                }
                return true;
            }

            case "top" -> {
                List<DamageLeaderboard.DamageEntry> top = manager.getLeaderboard().getTop(5);
                if (top.isEmpty()) {
                    sender.sendMessage(ColorUtil.color("&e┠ &7Урон ещё не нанесён."));
                } else {
                    for (int i = 0; i < top.size(); i++) {
                        DamageLeaderboard.DamageEntry e = top.get(i);
                        sender.sendMessage(ColorUtil.color("&e┠ &6" + (i + 1) + " место: &#E0C912" + e.playerName() + " &8— &c" + (int) e.damage() + " ❤"));
                    }
                }
                return true;
            }

            case "reload" -> {
                cfg.reload();
                plugin.getSchematicManager().loadSchematicFile();
                plugin.getBeachSchematicManager().loadSchematic();
                plugin.getAlchemistSchematicManager().loadSchematic();
                plugin.getBrushLootManager().reload();
                plugin.getBedrockService().detectHooks();
                plugin.getPlayerPointsHook().setupHook();
                plugin.getTopRightHudManager().exportAndInstallResourcePack();
                manager.startAutoScheduler();
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Конфиг, схемы и таблицы лута перезагружены."));
                return true;
            }

            default -> {
                sendAdminHelp(sender, label, cfg);
                return true;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void handleBrushLootSubcommand(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.color("&e┠ &fНастройка лута кисточек («Пляжные Раскопки»):"));
            sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " menu &8— &fоткрыть удобное GUI-меню настройки лута и шансов"));
            sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " brushloot add <обычная|эпическая|легендарная> [шанс] &8— &fдобавить предмет из руки"));
            sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " brushloot list <обычная|эпическая|легендарная> &8— &fсписок предметов"));
            sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " brushloot remove <обычная|эпическая|легендарная> <номер> &8— &fудалить предмет"));
            sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " brushloot clear <обычная|эпическая|легендарная> &8— &fочистить лут"));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        BrushTier tier = BrushTier.fromString(args[2]);
        if (tier == null) {
            sender.sendMessage(ColorUtil.color("&e┠ &cУкажите редкость кисточки: &#E0C912обычная &8(common)&c, &#E0C912эпическая &8(epic) &cили &#E0C912легендарная &8(legendary)&c."));
            return;
        }

        BrushLootManager lootManager = plugin.getBrushLootManager();

        switch (action) {
            case "add", "добавить" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cТолько игрок может добавить предмет из руки."));
                    return;
                }
                ItemStack inHand = player.getInventory().getItemInMainHand();
                if (inHand == null || inHand.getType().isAir()) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cВозьмите предмет в основную руку!"));
                    return;
                }
                double chance = 25.0;
                if (args.length >= 4) {
                    try {
                        chance = Math.max(0.1, Double.parseDouble(args[3]));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(ColorUtil.color("&e┠ &cНекорректный шанс: " + args[3]));
                        return;
                    }
                }
                lootManager.addLootItem(tier, inHand, chance);
                sender.sendMessage(ColorUtil.color(String.format(
                        "&e┠ &#34D711Предмет &f%s x%d &#34D711добавлен в лут кисточки %s &8(Шанс: &#E0C912%.1f%%&8).",
                        inHand.getType().name(), inHand.getAmount(), tier.getItemTitle(), chance
                )));
            }

            case "list", "список" -> {
                List<BrushLootManager.BrushLootEntry> entries = lootManager.getLootEntries(tier);
                sender.sendMessage(ColorUtil.color("&e┠ &fЛут кисточки " + tier.getItemTitle() + " &8(&f" + entries.size() + " шт.&8):"));
                if (entries.isEmpty()) {
                    sender.sendMessage(ColorUtil.color("&e┠ &7Список пуст."));
                    return;
                }
                for (int i = 0; i < entries.size(); i++) {
                    BrushLootManager.BrushLootEntry entry = entries.get(i);
                    ItemStack st = entry.item();
                    String itemName = (st.hasItemMeta() && st.getItemMeta() != null && st.getItemMeta().hasDisplayName())
                            ? st.getItemMeta().getDisplayName() + " &7(" + st.getType().name() + ")"
                            : st.getType().name();
                    sender.sendMessage(ColorUtil.color(String.format(
                            "&e┠ &#E0C912#%d &f%s &7x%d &8— &fШанс: &#34D711%.1f%%",
                            i + 1, itemName, st.getAmount(), entry.chance()
                    )));
                }
            }

            case "remove", "del", "delete", "удалить" -> {
                if (args.length < 4) {
                    sender.sendMessage(ColorUtil.color("&e┠ &fИспользование: &#E0C912/" + label + " brushloot remove " + tier.getConfigKey() + " <номер>"));
                    return;
                }
                int idx;
                try {
                    idx = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cУкажите корректный номер предмета из /" + label + " brushloot list " + tier.getConfigKey()));
                    return;
                }
                if (lootManager.removeLootItem(tier, idx)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &#34D711Предмет #" + idx + " удалён из лута кисточки " + tier.getItemTitle() + "&f."));
                } else {
                    sender.sendMessage(ColorUtil.color("&e┠ &cПредмет с номером #" + idx + " не найден."));
                }
            }

            case "clear", "очистить" -> {
                lootManager.clearLoot(tier);
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Лут кисточки " + tier.getItemTitle() + " &#34D711полностью очищен."));
            }

            default -> sender.sendMessage(ColorUtil.color("&e┠ &cИспользуйте: &#E0C912/" + label + " menu &cили &#E0C912/" + label + " brushloot <add|list|remove|clear> <обычная|эпическая|легендарная>"));
        }
    }

    private void handleGiveBrushSubcommand(CommandSender sender, String label, String[] args, boolean isReagent) {
        if (args.length < 2) {
            String cmdName = isReagent ? "givereagent" : "givebrush";
            sender.sendMessage(ColorUtil.color("&e┠ &fИспользование: &#E0C912/" + label + " " + cmdName + " <обычная|эпическая|легендарная> [игрок]"));
            return;
        }
        BrushTier tier = BrushTier.fromString(args[1]);
        if (tier == null) {
            sender.sendMessage(ColorUtil.color("&e┠ &cУкажите редкость: &#E0C912обычная&c, &#E0C912эпическая &cили &#E0C912легендарная&c."));
            return;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(ColorUtil.color("&e┠ &cИгрок " + args[2] + " не найден."));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(ColorUtil.color("&e┠ &cУкажите ник игрока."));
            return;
        }

        if (isReagent) {
            ItemStack reagent = plugin.getBrushLootManager().createReagentItem(tier);
            target.getInventory().addItem(reagent);
            sender.sendMessage(ColorUtil.color("&e┠ &fВыдан " + plugin.getBrushLootManager().getReagentDisplayName(tier) + " &fигроку &#E0C912" + target.getName() + "&f."));
        } else {
            ItemStack brush = plugin.getBrushLootManager().createBrushItem(tier);
            target.getInventory().addItem(brush);
            sender.sendMessage(ColorUtil.color("&e┠ &fВыдана " + tier.getItemTitle() + " &fигроку &#E0C912" + target.getName() + "&f."));
        }
    }

    private void sendAdminHelp(CommandSender sender, String label, PluginConfig cfg) {
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " menu &8— &fОткрыть GUI-меню настройки лута и шансов"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " start [rift|excavation|cauldron] &8— &fЗапустить ивент в " + cfg.getWorldName()));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " start-here [rift|excavation|cauldron] &8— &fЗапустить на вашей позиции"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " stop &8— &fОстановить ивент"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " brushloot <add|list|remove|clear> <обычная|эпическая|легендарная>"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " givebrush <обычная|эпическая|легендарная> [игрок]"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " givereagent <обычная|эпическая|легендарная> [игрок]"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " ability <pull|push|gather|minions>"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " setworld <мир>"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " reload"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("geyserevent.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(Arrays.asList(
                    "menu", "gui", "start", "start-here", "stop", "brushloot", "givebrush", "givereagent",
                    "ability", "setworld", "saveschem", "status", "top", "reload"
            ), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("start") || sub.equals("start-here") || sub.equals("starthere")) {
                return filterPrefix(Arrays.asList("rift", "excavation", "cauldron"), args[1]);
            }
            if (sub.equals("menu") || sub.equals("gui") || sub.equals("givebrush") || sub.equals("brush")
                    || sub.equals("givereagent") || sub.equals("reagent")) {
                return filterPrefix(Arrays.asList("common", "epic", "legendary", "обычная", "эпическая", "легендарная"), args[1]);
            }
            if (sub.equals("brushloot") || sub.equals("loot")) {
                return filterPrefix(Arrays.asList("add", "list", "remove", "clear", "обычная", "эпическая", "легендарная"), args[1]);
            }
            if (sub.equals("ability") || sub.equals("cast")) {
                return filterPrefix(Arrays.asList("pull", "push", "gather", "minions"), args[1]);
            }
            if (sub.equals("setworld")) {
                List<String> worlds = new ArrayList<>();
                worlds.add("world2");
                for (World w : Bukkit.getWorlds()) {
                    if (!worlds.contains(w.getName())) {
                        worlds.add(w.getName());
                    }
                }
                return filterPrefix(worlds, args[1]);
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("brushloot") || sub.equals("loot")) {
                return filterPrefix(Arrays.asList("common", "epic", "legendary", "обычная", "эпическая", "легендарная"), args[2]);
            }
        }
        if (args.length == 4) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ((sub.equals("brushloot") || sub.equals("loot")) && args[1].equalsIgnoreCase("add")) {
                return filterPrefix(Arrays.asList("10", "25", "50", "100"), args[3]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(c);
            }
        }
        return result;
    }
}
