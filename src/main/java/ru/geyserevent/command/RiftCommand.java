package ru.geyserevent.command;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
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
import ru.geyserevent.excavation.BrushLootManager.LootCategory;
import ru.geyserevent.excavation.BrushTier;
import ru.geyserevent.hook.BedrockService;
import ru.geyserevent.mine.AncientMineManager;
import ru.geyserevent.summon.SummonAltarManager;
import ru.geyserevent.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Админ-команды /rift, /cauldronmenu и /minemenu для управления ивентами:
 * 1) «Адский Разлом»
 * 2) «Пляжные Раскопки»
 * 3) «Котёл Алхимика»
 * 4) Постоянный ивент «Шахта Древних (Жила Жеод)» на 0 ~ 0
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

        String cmdName = command.getName().toLowerCase(Locale.ROOT);
        if (cmdName.equals("cauldronmenu") || label.equalsIgnoreCase("cauldronmenu")
                || label.equalsIgnoreCase("alchemistmenu") || label.equalsIgnoreCase("cmenu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtil.color("&e┠ &cМеню доступно только в игре!"));
                return true;
            }
            if (args.length >= 1) {
                BrushTier tier = BrushTier.fromString(args[0]);
                if (tier != null) {
                    plugin.getBrushLootGuiManager().openTierMenu(player, tier, LootCategory.CAULDRON);
                    return true;
                }
            }
            plugin.getBrushLootGuiManager().openCauldronMenu(player);
            return true;
        }

        if (cmdName.equals("minemenu") || label.equalsIgnoreCase("minemenu")
                || label.equalsIgnoreCase("geodemenu") || label.equalsIgnoreCase("mmenu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtil.color("&e┠ &cМеню доступно только в игре!"));
                return true;
            }
            if (args.length >= 1) {
                BrushTier tier = BrushTier.fromString(args[0]);
                if (tier != null) {
                    plugin.getBrushLootGuiManager().openTierMenu(player, tier, LootCategory.MINE);
                    return true;
                }
            }
            plugin.getBrushLootGuiManager().openMineMenu(player);
            return true;
        }

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
                    if (args[1].equalsIgnoreCase("cauldron") || args[1].equalsIgnoreCase("котел")
                            || args[1].equalsIgnoreCase("котёл") || args[1].equalsIgnoreCase("alchemist")
                            || args[1].equalsIgnoreCase("алхимик")) {
                        if (args.length >= 3) {
                            BrushTier tier = BrushTier.fromString(args[2]);
                            if (tier != null) {
                                plugin.getBrushLootGuiManager().openTierMenu(player, tier, LootCategory.CAULDRON);
                                return true;
                            }
                        }
                        plugin.getBrushLootGuiManager().openCauldronMenu(player);
                        return true;
                    }
                    if (args[1].equalsIgnoreCase("mine") || args[1].equalsIgnoreCase("шахта")
                            || args[1].equalsIgnoreCase("geode") || args[1].equalsIgnoreCase("жеоды")) {
                        if (args.length >= 3) {
                            BrushTier tier = BrushTier.fromString(args[2]);
                            if (tier != null) {
                                plugin.getBrushLootGuiManager().openTierMenu(player, tier, LootCategory.MINE);
                                return true;
                            }
                        }
                        plugin.getBrushLootGuiManager().openMineMenu(player);
                        return true;
                    }
                    if (args[1].equalsIgnoreCase("excavation") || args[1].equalsIgnoreCase("раскопки")
                            || args[1].equalsIgnoreCase("beach") || args[1].equalsIgnoreCase("пляж")) {
                        if (args.length >= 3) {
                            BrushTier tier = BrushTier.fromString(args[2]);
                            if (tier != null) {
                                plugin.getBrushLootGuiManager().openTierMenu(player, tier, LootCategory.EXCAVATION);
                                return true;
                            }
                        }
                        plugin.getBrushLootGuiManager().openExcavationMenu(player);
                        return true;
                    }
                    BrushTier tier = BrushTier.fromString(args[1]);
                    if (tier != null) {
                        plugin.getBrushLootGuiManager().openTierMenu(player, tier, LootCategory.EXCAVATION);
                        return true;
                    }
                }
                plugin.getBrushLootGuiManager().openMainMenu(player);
                return true;
            }

            case "cauldronmenu", "cmenu", "alchemistmenu", "cauldron" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cМеню доступно только в игре!"));
                    return true;
                }
                if (args.length >= 2) {
                    BrushTier tier = BrushTier.fromString(args[1]);
                    if (tier != null) {
                        plugin.getBrushLootGuiManager().openTierMenu(player, tier, LootCategory.CAULDRON);
                        return true;
                    }
                }
                plugin.getBrushLootGuiManager().openCauldronMenu(player);
                return true;
            }

            case "minemenu", "mmenu", "geodemenu" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cМеню доступно только в игре!"));
                    return true;
                }
                if (args.length >= 2) {
                    BrushTier tier = BrushTier.fromString(args[1]);
                    if (tier != null) {
                        plugin.getBrushLootGuiManager().openTierMenu(player, tier, LootCategory.MINE);
                        return true;
                    }
                }
                plugin.getBrushLootGuiManager().openMineMenu(player);
                return true;
            }

            case "excavationmenu", "beachmenu", "bmenu" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cМеню доступно только в игре!"));
                    return true;
                }
                if (args.length >= 2) {
                    BrushTier tier = BrushTier.fromString(args[1]);
                    if (tier != null) {
                        plugin.getBrushLootGuiManager().openTierMenu(player, tier, LootCategory.EXCAVATION);
                        return true;
                    }
                }
                plugin.getBrushLootGuiManager().openExcavationMenu(player);
                return true;
            }

            case "mine", "шахта" -> {
                handleMineSubcommand(sender, label, args);
                return true;
            }

            case "start" -> {
                EventType type = null;
                if (args.length >= 2) {
                    type = EventType.fromString(args[1]);
                    if (type == null) {
                        sender.sendMessage(ColorUtil.color("&e┠ &cУкажите ивент: &#E0C912rift&c, &#E0C912excavation&c, &#E0C912cauldron&c, &#E0C912wind&c, &#E0C912skibidi&c, &#E0C912ronaldo &cили &#E0C912fishing&c."));
                        return true;
                    }
                }
                if (type != null && InfernalRiftManager.isTemporarilyDisabled(type)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cИвент «" + type.getDisplayName() + "» временно выключен в коде."));
                    return true;
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
                        sender.sendMessage(ColorUtil.color("&e┠ &cУкажите ивент: &#E0C912rift&c, &#E0C912excavation&c, &#E0C912cauldron&c, &#E0C912wind&c, &#E0C912skibidi&c, &#E0C912ronaldo &cили &#E0C912fishing&c."));
                        return true;
                    }
                    type = parsed;
                }
                if (InfernalRiftManager.isTemporarilyDisabled(type)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cИвент «" + type.getDisplayName() + "» временно выключен в коде."));
                    return true;
                }
                manager.startEventAt(player.getLocation(), type);
                return true;
            }

            case "stop" -> {
                boolean riftActive = manager.isActive();
                boolean windActive = plugin.getGravityHoardManager() != null
                        && plugin.getGravityHoardManager().isActive();
                boolean skibidiActive = plugin.getSkibidiToiletManager() != null
                        && plugin.getSkibidiToiletManager().isActive();
                boolean ronaldoActive = plugin.getRonaldoEventManager() != null
                        && plugin.getRonaldoEventManager().isActive();
                boolean fishingActive = plugin.getFishingEventManager() != null
                        && plugin.getFishingEventManager().isActive();
                if (!riftActive && !windActive && !skibidiActive && !ronaldoActive && !fishingActive) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cИвент не активен."));
                    return true;
                }
                if (riftActive) {
                    manager.stopEvent(false);
                }
                if (windActive) {
                    plugin.getGravityHoardManager().stopEvent();
                }
                if (skibidiActive) {
                    plugin.getSkibidiToiletManager().stopEvent();
                }
                if (ronaldoActive) {
                    plugin.getRonaldoEventManager().stopEvent();
                }
                if (fishingActive) {
                    plugin.getFishingEventManager().stopEvent();
                }
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Ивент остановлен и схема откачена."));
                return true;
            }

            case "brushloot", "loot" -> {
                if (args.length == 1 && sender instanceof Player player) {
                    plugin.getBrushLootGuiManager().openExcavationMenu(player);
                    return true;
                }
                if (args.length == 2 && sender instanceof Player player) {
                    BrushTier tier = BrushTier.fromString(args[1]);
                    if (tier != null) {
                        plugin.getBrushLootGuiManager().openTierMenu(player, tier, LootCategory.EXCAVATION);
                        return true;
                    }
                }
                handleBrushLootSubcommand(sender, label, args, LootCategory.EXCAVATION);
                return true;
            }

            case "cauldronloot", "reagentloot", "alchemistloot" -> {
                if (args.length == 1 && sender instanceof Player player) {
                    plugin.getBrushLootGuiManager().openCauldronMenu(player);
                    return true;
                }
                if (args.length == 2 && sender instanceof Player player) {
                    BrushTier tier = BrushTier.fromString(args[1]);
                    if (tier != null) {
                        plugin.getBrushLootGuiManager().openTierMenu(player, tier, LootCategory.CAULDRON);
                        return true;
                    }
                }
                handleBrushLootSubcommand(sender, label, args, LootCategory.CAULDRON);
                return true;
            }

            case "mineloot", "geodeloot" -> {
                if (args.length == 1 && sender instanceof Player player) {
                    plugin.getBrushLootGuiManager().openMineMenu(player);
                    return true;
                }
                if (args.length == 2 && sender instanceof Player player) {
                    BrushTier tier = BrushTier.fromString(args[1]);
                    if (tier != null) {
                        plugin.getBrushLootGuiManager().openTierMenu(player, tier, LootCategory.MINE);
                        return true;
                    }
                }
                handleBrushLootSubcommand(sender, label, args, LootCategory.MINE);
                return true;
            }

            case "givebrush", "brush" -> {
                handleGiveItemSubcommand(sender, label, args, LootCategory.EXCAVATION);
                return true;
            }

            case "givereagent", "reagent" -> {
                handleGiveItemSubcommand(sender, label, args, LootCategory.CAULDRON);
                return true;
            }

            case "givegeode", "geode" -> {
                handleGiveItemSubcommand(sender, label, args, LootCategory.MINE);
                return true;
            }

            case "copylot", "copyloot", "cloneloot", "скопироватьлут" -> {
                handleCopyLootSubcommand(sender, label, args);
                return true;
            }

            case "minplayers", "minplayer", "игроки", "players" -> {
                handleMinPlayersSubcommand(sender, label, args);
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

            case "summonaltar", "summon", "altar" -> {
                SummonAltarManager summon = plugin.getSummonAltarManager();
                if (summon == null) {
                    return true;
                }
                if (args.length >= 4) {
                    // /rift summonaltar <x> <y> <z> [мир]
                    try {
                        World w = args.length >= 5 ? Bukkit.getWorld(args[4]) : null;
                        if (w == null && sender instanceof Player sp) {
                            w = sp.getWorld();
                        }
                        if (w == null) {
                            w = Bukkit.getWorld(plugin.getPluginConfig().getSummonWorldName());
                        }
                        if (w == null) {
                            sender.sendMessage(ColorUtil.color("&e┼ &cМир не найден."));
                            return true;
                        }
                        Location target = new Location(w, Double.parseDouble(args[1]),
                                Double.parseDouble(args[2]), Double.parseDouble(args[3]));
                        if (summon.createAltar(target)) {
                            sender.sendMessage(ColorUtil.color("&e┼ &#34D711Алтарь призыва построен: &f"
                                    + target.getBlockX() + " " + target.getBlockY() + " " + target.getBlockZ()));
                        } else {
                            sender.sendMessage(ColorUtil.color("&e┼ &cНе удалось построить алтарь."));
                        }
                        return true;
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(ColorUtil.color("&e┼ &fИспользование: &#E0C912/" + label + " summonaltar <x> <y> <z> [мир]"));
                        return true;
                    }
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┼ &fИспользование: &#E0C912/" + label + " summonaltar <x> <y> <z> [мир]"));
                    return true;
                }
                // без аргументов — ставим туда, куда смотрит админ
                Block look = player.getTargetBlockExact(6);
                Location loc = look != null ? look.getLocation() : player.getLocation();
                if (summon.createAltar(loc)) {
                    sender.sendMessage(ColorUtil.color("&e┼ &#34D711Алтарь призыва построен туда, куда вы смотрите: &f"
                            + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
                    sender.sendMessage(ColorUtil.color("&e┼ &7Рядом появятся 4 пилона с кастомными текстурами."));
                } else {
                    sender.sendMessage(ColorUtil.color("&e┼ &cНе удалось построить алтарь."));
                }
                return true;
            }

            case "summonremove", "altarremove" -> {
                SummonAltarManager summon = plugin.getSummonAltarManager();
                if (summon == null) {
                    return true;
                }
                int removed = 0;
                for (SummonAltarManager.Altar altar : summon.getAltars()) {
                    if (summon.removeAltar(altar.toLocation())) {
                        removed++;
                    }
                }
                sender.sendMessage(ColorUtil.color("&e┼ &#34D711Удалено алтарей: &f" + removed));
                return true;
            }

            case "summonlist", "altarlist" -> {
                SummonAltarManager summon = plugin.getSummonAltarManager();
                if (summon == null) {
                    return true;
                }
                List<String> list = summon.describeAltars();
                if (list.isEmpty()) {
                    sender.sendMessage(ColorUtil.color("&e┼ &7Алтарей призыва нет. Постройте: &#E0C912/" + label + " summonaltar"));
                } else {
                    sender.sendMessage(ColorUtil.color("&e┼ &fАлтари призыва: &f" + list.size()));
                    for (String line : list) {
                        sender.sendMessage(ColorUtil.color("&e┼ &8• &#E0C912" + line));
                    }
                }
                return true;
            }

            case "givesummon", "givealtar" -> {
                SummonAltarManager summon = plugin.getSummonAltarManager();
                if (summon == null) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┼ &cТолько для игроков."));
                    return true;
                }
                int amount = 1;
                if (args.length >= 2) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
                    } catch (NumberFormatException ignored) {
                    }
                }
                player.getInventory().addItem(summon.createAltarItem(amount));
                sender.sendMessage(ColorUtil.color("&e┼ &#34D711Выдан предмет-призывалка (x" + amount
                        + "). Поставьте его — рядом появятся пилоны."));
                return true;
            }

            case "status", "info" -> {
                AncientMineManager mineMgr = plugin.getAncientMineManager();
                if (mineMgr != null && mineMgr.isMineActive()) {
                    Location mLoc = mineMgr.getCenterLocation();
                    sender.sendMessage(ColorUtil.color("&e┠ &fПостоянный ивент: &#FFB81CШахта Древних &8(&f"
                            + mLoc.getBlockX() + " " + mLoc.getBlockY() + " " + mLoc.getBlockZ()
                            + ", жеод: &a" + mineMgr.getActiveGeodeCount() + " шт.&8)"));
                }
                if (manager.isActive() && manager.getRiftCenterLocation() != null) {
                    Location loc = manager.getRiftCenterLocation();
                    sender.sendMessage(ColorUtil.color("&e┠ &fАктивный ивент: &#34D711" + manager.getActiveEventType().getDisplayName()));
                    if (manager.getActiveEventType() == EventType.INFERNAL_RIFT) {
                        sender.sendMessage(ColorUtil.color("&e┠ &fЗдоровье: &c" + (int) Math.ceil(manager.getBossCurrentHealth()) + "/" + (int) Math.ceil(manager.getBossMaxHealth()) + " ❤"));
                    } else {
                        sender.sendMessage(ColorUtil.color("&e┠ &fДо следующего предмета: &#E0C912" + manager.getSecondsUntilNextBrush() + " сек."));
                    }
                    sender.sendMessage(ColorUtil.color("&e┠ &fКоординаты: &#E0C912" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
                } else {
                    sender.sendMessage(ColorUtil.color("&e┠ &fДо появления временного ивента: &#E0C912" + ColorUtil.formatDurationVerbose(manager.getSecondsUntilNextStart())));
                    sender.sendMessage(ColorUtil.color("&e┠ &7Авто: rift " + cfg.getAutoMinPlayers(EventType.INFERNAL_RIFT)
                            + " / раскопки " + cfg.getAutoMinPlayers(EventType.BEACH_EXCAVATION)
                            + " / котёл " + cfg.getAutoMinPlayers(EventType.ALCHEMIST_CAULDRON)
                            + " / гейзер " + cfg.getAutoMinPlayers(EventType.GRAVITY_HOARD)
                            + " игроков  &8| &fсейчас " + plugin.getBedrockService().getOnlineBedrockCount()));
                if (plugin.getSummonAltarManager() != null) {
                    sender.sendMessage(ColorUtil.color("&e┼ &fПризыв: &f" + plugin.getSummonAltarManager().describe()));
                }
                }
                if (plugin.getGravityHoardManager() != null && plugin.getGravityHoardManager().isActive()) {
                    var wind = plugin.getGravityHoardManager();
                    Location wLoc = wind.getCenterLocation();
                    sender.sendMessage(ColorUtil.color("&e┼ &fИвент: &#7DF9FFДревний клад &8(&fбез босса&8)"));
                    if (wLoc != null) {
                        sender.sendMessage(ColorUtil.color("&e┠ &fКоординаты: &#E0C912"
                                + wLoc.getBlockX() + " " + wLoc.getBlockY() + " " + wLoc.getBlockZ()
                                + " &8(&fосталось " + wind.getSecondsRemaining() + " сек.&8)"));
                    }
                }
                if (plugin.getRonaldoEventManager() != null && plugin.getRonaldoEventManager().isActive()) {
                    var ronaldo = plugin.getRonaldoEventManager();
                    Location rLoc = ronaldo.getCenterLocation();
                    sender.sendMessage(ColorUtil.color("&e┼ &fИвент: &#E0C912Роналду SIUU"));
                    if (rLoc != null) {
                        sender.sendMessage(ColorUtil.color("&e┠ &fКоординаты: &#E0C912"
                                + rLoc.getBlockX() + " " + rLoc.getBlockY() + " " + rLoc.getBlockZ()
                                + " &8(&fосталось " + ronaldo.getSecondsRemaining() + " сек.&8)"));
                    }
                }
                if (plugin.getFishingEventManager() != null && plugin.getFishingEventManager().isActive()) {
                    var fishing = plugin.getFishingEventManager();
                    Location fLoc = fishing.getCenterLocation();
                    sender.sendMessage(ColorUtil.color("&e┼ &fИвент: &#55CFFFРыбалка"));
                    if (fLoc != null) {
                        sender.sendMessage(ColorUtil.color("&e┠ &fКоординаты: &#E0C912"
                                + fLoc.getBlockX() + " " + fLoc.getBlockY() + " " + fLoc.getBlockZ()
                                + " &8(&fосталось " + fishing.getSecondsRemaining() + " сек.&8)"));
                    }
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

            case "hudinfo" -> {
                BedrockService bedrock = plugin.getBedrockService();
                boolean bedrockApi;
                try {
                    Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                    bedrockApi = true;
                } catch (Throwable ignored) {
                    bedrockApi = false;
                }
                if (sender instanceof Player player) {
                    boolean isBedrock = bedrock != null && bedrock.isBedrockPlayer(player);
                    sender.sendMessage(ColorUtil.color("&e┠ &fВаш клиент: "
                        + (isBedrock ? "&aBedrock" : "&eJava") + " &7(информация ивента — в BossBar сверху + GPS в action bar)"));
                }
                sender.sendMessage(ColorUtil.color("&e┠ &fAPI Bedrock: " + (bedrockApi ? "&aподключено" : "&cне найдено")
                        + " &7(Geyser / Floodgate)"));
                String packsDir = plugin.getPluginConfig().getGeyserPacksDir();
                if (packsDir != null && !packsDir.isBlank()) {
                    sender.sendMessage(ColorUtil.color("&e┠ &fПапка packs (из конфига): &f" + packsDir));
                } else {
                    sender.sendMessage(ColorUtil.color("&e┠ &fПапки packs (проверяются по порядку):"));
                    for (String cand : plugin.getTopRightHudManager().getGeyserPacksDirCandidates()) {
                        sender.sendMessage(ColorUtil.color("&e┠ &8  • &f" + cand));
                    }
                }
                sender.sendMessage(ColorUtil.color("&e┠ &fУстановка пака: &f"
                        + plugin.getTopRightHudManager().getPackInstallResult()));
                File pack = plugin.getTopRightHudManager().getInstalledPackFile();
                if (pack != null && pack.exists()) {
                    sender.sendMessage(ColorUtil.color("&e┠ &fРесурспак в packs: &aесть &7(" + pack.length() + " байт)"));
                } else {
                    File exported = plugin.getTopRightHudManager().getExportedPackFile();
                    sender.sendMessage(ColorUtil.color("&e┠ &fРесурспак в packs: &cНЕТ &7— скопируйте вручную:"));
                    sender.sendMessage(ColorUtil.color("&e┠ &f   "
                            + (exported != null ? exported.getPath() : "plugins/GeyserEvent/resourcepack/GeyserEvent-Pack.mcpack")));
                    sender.sendMessage(ColorUtil.color("&e┠ &f   → в папку &epacks &fBedrock-движка, затем перезапустите сервер."));
                }
                sender.sendMessage(ColorUtil.color("&e┠ &fИвент сейчас: "
                        + (manager.isActive() ? "&aидёт «" + manager.getActiveEventType().getDisplayName() + "»" : "&7не идёт")));
                sender.sendMessage(ColorUtil.color("&e┠ &fПак нужен для &eрюкзака шахтёра &fи блоков призыва — скачивается автоматически."));
                sender.sendMessage(ColorUtil.color("&e┠ &fНа экране плагин рисует только BossBar сверху + GPS в action bar."));
                return true;
            }

            case "reload" -> {
                cfg.reload();
                plugin.getSchematicManager().loadSchematicFile();
                plugin.getBeachSchematicManager().loadSchematic();
                plugin.getAlchemistSchematicManager().loadSchematic();
                if (plugin.getAncientMineManager() != null) {
                    plugin.getAncientMineManager().loadSchematic();
                }
                plugin.getBrushLootManager().reload();
                plugin.getBedrockService().detectHooks();
                plugin.getPlayerPointsHook().setupHook();
                plugin.getTopRightHudManager().exportAndInstallResourcePack();
                manager.startAutoScheduler();
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Конфиг, схемы и таблицы лута всех ивентов перезагружены."));
                return true;
            }

            default -> {
                sendAdminHelp(sender, label, cfg);
                return true;
            }
        }
    }

    private void handleMineSubcommand(CommandSender sender, String label, String[] args) {
        AncientMineManager mineMgr = plugin.getAncientMineManager();
        if (mineMgr == null) {
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "regen", "обновить" -> {
                mineMgr.regenerateAllOresAndGeodes();
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Рудная жила и жеоды в «Шахте Древних» полностью обновлены!"));
            }
            case "spawn", "0" -> {
                mineMgr.initializePermanentMine();
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Постоянная «Шахта Древних» установлена на координатах 0 ~ 0!"));
            }
            case "set-here", "sethere" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cТолько для игроков."));
                    return;
                }
                mineMgr.spawnMineAt(player.getLocation(), true);
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Постоянная «Шахта Древних» установлена на вашей позиции!"));
            }
            case "remove", "stop" -> {
                mineMgr.shutdownMine(true);
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711«Шахта Древних» убрана, ландшафт восстановлен."));
            }
            case "menu", "gui" -> {
                if (sender instanceof Player player) {
                    plugin.getBrushLootGuiManager().openMineMenu(player);
                }
            }
            default -> {
                if (mineMgr.isMineActive()) {
                    Location loc = mineMgr.getCenterLocation();
                    sender.sendMessage(ColorUtil.color("&e┠ &#FFB81CШахта Древних (Постоянный ивент):"));
                    sender.sendMessage(ColorUtil.color("&e┠ &fКоординаты: &#E0C912" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + " &8(" + loc.getWorld().getName() + ")"));
                    sender.sendMessage(ColorUtil.color("&e┠ &fАктивных жеод в жиле: &#34D711" + mineMgr.getActiveGeodeCount() + " шт."));
                    sender.sendMessage(ColorUtil.color("&e┠ &fДо регенерации руд: &#E0C912" + ColorUtil.formatDuration(mineMgr.getSecondsUntilFullRegen())));
                    sender.sendMessage(ColorUtil.color("&e┠ &fУправление: &#E0C912/" + label + " mine <regen|spawn|set-here|remove|menu>"));
                } else {
                    sender.sendMessage(ColorUtil.color("&e┠ &cШахта Древних сейчас отключена. Запустить на 0 ~ 0: &#E0C912/" + label + " mine spawn"));
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void handleBrushLootSubcommand(CommandSender sender, String label, String[] args, LootCategory category) {
        String subCmd = switch (category) {
            case EXCAVATION -> "brushloot";
            case CAULDRON -> "cauldronloot";
            case MINE -> "mineloot";
            case FISHING -> "fishingloot";
        };
        String eventTitle = switch (category) {
            case EXCAVATION -> "«Пляжные Раскопки»";
            case CAULDRON -> "«Котёл Алхимика»";
            case MINE -> "«Шахта Древних»";
            case FISHING -> "«Рыбалка»";
        };
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.color("&e┠ &fНастройка лута (" + eventTitle + "):"));
            sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " menu &8— &fоткрыть удобное GUI-меню настройки лута и шансов"));
            sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " " + subCmd + " add <обычная|эпическая|легендарная> [шанс] &8— &fдобавить предмет из руки"));
            sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " " + subCmd + " list <обычная|эпическая|легендарная> &8— &fсписок предметов"));
            sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " " + subCmd + " remove <обычная|эпическая|легендарная> <номер> &8— &fудалить предмет"));
            sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " " + subCmd + " clear <обычная|эпическая|легендарная> &8— &fочистить лут"));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        BrushTier tier = BrushTier.fromString(args[2]);
        if (tier == null) {
            sender.sendMessage(ColorUtil.color("&e┠ &cУкажите редкость: &#E0C912обычная &8(common)&c, &#E0C912эпическая &8(epic) &cили &#E0C912легендарная &8(legendary)&c."));
            return;
        }

        BrushLootManager lootManager = plugin.getBrushLootManager();
        String tierTitle = lootManager.getCategoryItemTitle(tier, category);

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
                lootManager.addLootItem(tier, inHand, chance, category);
                sender.sendMessage(ColorUtil.color(String.format(
                        "&e┠ &#34D711Предмет &f%s x%d &#34D711добавлен в лут (%s&r&#34D711, шанс: &#E0C912%.1f%%&#34D711).",
                        inHand.getType().name(), inHand.getAmount(), tierTitle, chance
                )));
            }

            case "list", "список" -> {
                List<BrushLootManager.BrushLootEntry> entries = lootManager.getLootEntries(tier, category);
                sender.sendMessage(ColorUtil.color("&e┠ &fЛут " + tierTitle + " &8(&f" + entries.size() + " шт.&8):"));
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
                    sender.sendMessage(ColorUtil.color("&e┠ &fИспользование: &#E0C912/" + label + " " + subCmd + " remove " + tier.getConfigKey() + " <номер>"));
                    return;
                }
                int idx;
                try {
                    idx = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cУкажите корректный номер предмета из /" + label + " " + subCmd + " list " + tier.getConfigKey()));
                    return;
                }
                if (lootManager.removeLootItem(tier, idx, category)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &#34D711Предмет #" + idx + " удалён из лута " + tierTitle + "&f."));
                } else {
                    sender.sendMessage(ColorUtil.color("&e┠ &cПредмет с номером #" + idx + " не найден."));
                }
            }

            case "clear", "очистить" -> {
                lootManager.clearLoot(tier, category);
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Лут " + tierTitle + " &#34D711полностью очищен."));
            }

            default -> sender.sendMessage(ColorUtil.color("&e┠ &cИспользуйте: &#E0C912/" + label + " menu &cили &#E0C912/" + label + " " + subCmd + " <add|list|remove|clear> <обычная|эпическая|легендарная>"));
        }
    }

    private void handleGiveItemSubcommand(CommandSender sender, String label, String[] args, LootCategory category) {
        if (args.length < 2) {
            String cmdName = switch (category) {
                case EXCAVATION -> "givebrush";
                case CAULDRON -> "givereagent";
                case MINE -> "givegeode";
                case FISHING -> "giverod";
            };
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

        BrushLootManager mgr = plugin.getBrushLootManager();
        ItemStack stack = switch (category) {
            case EXCAVATION -> mgr.createBrushItem(tier);
            case CAULDRON -> mgr.createReagentItem(tier);
            case MINE -> mgr.createGeodeItem(tier);
            case FISHING -> mgr.createFishingRod(tier);
        };
        target.getInventory().addItem(stack);
        sender.sendMessage(ColorUtil.color("&e┠ &fВыдан " + mgr.getCategoryItemTitle(tier, category) + " &fигроку &#E0C912" + target.getName() + "&f."));
    }

    private void sendAdminHelp(CommandSender sender, String label, PluginConfig cfg) {
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " menu &8— &fОбщее GUI-меню настройки лута всех ивентов"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/minemenu &8(&#E0C912/" + label + " minemenu&8) — &fGUI-меню лута «Шахты Древних (0 ~ 0)»"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/cauldronmenu &8(&#E0C912/" + label + " cauldronmenu&8) — &fGUI-меню лута «Котла Алхимика»"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " mine <regen|spawn|set-here|remove> &8— &fУправление «Шахтой Древних»"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " start [rift|excavation|cauldron|wind|skibidi|ronaldo|fishing] &8— &fЗапустить ивент в " + cfg.getWorldName()));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " start-here [rift|excavation|cauldron|wind|skibidi|ronaldo|fishing] &8— &fЗапустить на вашей позиции"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/skibidi &8(&#E0C912/skibidi here&8 / &#E0C912/skibidi stop&8) — &fШкибиди Туалет"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/ronaldo &8(&#E0C912/ronaldo here&8 / &#E0C912/ronaldo stop&8) — &fРоналду SIUU"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/fishing &8(&#E0C912/fishing here&8 / &#E0C912/fishing stop&8) — &fРыбалка"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/klad &8(&#E0C912/klad here&8 / &#E0C912/klad stop&8) — &fОтдельная команда «Древний клад»"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " stop &8— &fОстановить временный ивент"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " mineloot <add|list|remove|clear> <обычная|эпическая|легендарная>"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " cauldronloot <add|list|remove|clear> <обычная|эпическая|легендарная>"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " brushloot <add|list|remove|clear> <обычная|эпическая|легендарная>"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " givegeode / givereagent / givebrush <обычная|эпическая|легендарная> [игрок]"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " copylot <откуда> <куда> [редкость] &8— &fСкопировать лут с другого ивента"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " minplayers [ивент|all] [число] &8— &fСколько игроков нужно для авто-старта"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " reload"));
    }

    private void handleMinPlayersSubcommand(CommandSender sender, String label, String[] args) {
        PluginConfig cfg = plugin.getPluginConfig();
        int online = plugin.getBedrockService().getOnlineBedrockCount();
        if (args.length == 1) {
            sender.sendMessage(ColorUtil.color("&e┠ &fОнлайн сейчас: &#E0C912" + online
                    + " &8| &fручной старт всегда без проверки"));
            for (EventType type : EventType.values()) {
                sender.sendMessage(ColorUtil.color("&e┠ &#E0C912" + type.getId()
                        + " &8— &f" + type.getDisplayName() + ": &#34D711" + cfg.getAutoMinPlayers(type)
                        + " &7игроков для авто"));
            }
            sender.sendMessage(ColorUtil.color("&e┠ &7Пример: &#E0C912/" + label + " minplayers wind 1"));
            return;
        }
        String targetRaw = args[1];
        boolean all = targetRaw.equalsIgnoreCase("all") || targetRaw.equalsIgnoreCase("все");
        EventType type = all ? null : EventType.fromString(targetRaw);
        if (!all && type == null) {
            sender.sendMessage(ColorUtil.color("&e┠ &cИвент: &#E0C912rift&c, &#E0C912excavation&c, &#E0C912cauldron&c, &#E0C912wind &cили &#E0C912all"));
            return;
        }
        if (args.length < 3) {
            if (all) {
                sender.sendMessage(ColorUtil.color("&e┠ &fИспользование: &#E0C912/" + label + " minplayers all <число>"));
            } else {
                sender.sendMessage(ColorUtil.color("&e┠ &fАвто-старт &#E0C912" + type.getDisplayName()
                        + "&f: &#34D711" + cfg.getAutoMinPlayers(type) + " &fигроков"));
            }
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ColorUtil.color("&e┠ &cУкажите число игроков, например &#E0C9125"));
            return;
        }
        if (amount < 0 || amount > 1000) {
            sender.sendMessage(ColorUtil.color("&e┠ &cЧисло от 0 до 1000."));
            return;
        }
        if (!all && PluginConfig.isBossEvent(type) && amount < 10) {
            sender.sendMessage(ColorUtil.color("&e┠ &cБосс-ивенты автостартуют только от &f10 &cонлайна."));
            amount = 10;
        }
        cfg.setAutoMinPlayers(type, amount);
        if (all) {
            sender.sendMessage(ColorUtil.color("&e┠ &#34D711Для авто-старта всех ивентов нужно &f" + amount + " &aигроков"));
        } else {
            sender.sendMessage(ColorUtil.color("&e┠ &#34D711Авто &#E0C912" + type.getDisplayName()
                    + "&a: нужно &f" + amount + " &aигроков онлайн"));
        }
        sender.sendMessage(ColorUtil.color("&e┠ &7/" + label + " start и start-here работают при любом онлайне"));
    }

    private enum CopyLootTarget {
        EXCAVATION, CAULDRON, MINE, SKIBIDI, WIND, RONALDO, FISHING
    }

    private CopyLootTarget parseCopyLootTarget(String raw) {
        if (raw == null) {
            return null;
        }
        EventType type = EventType.fromString(raw);
        if (type == EventType.BEACH_EXCAVATION) {
            return CopyLootTarget.EXCAVATION;
        }
        if (type == EventType.ALCHEMIST_CAULDRON) {
            return CopyLootTarget.CAULDRON;
        }
        if (type == EventType.SKIBIDI) {
            return CopyLootTarget.SKIBIDI;
        }
        if (type == EventType.GRAVITY_HOARD) {
            return CopyLootTarget.WIND;
        }
        if (type == EventType.RONALDO) {
            return CopyLootTarget.RONALDO;
        }
        if (type == EventType.FISHING) {
            return CopyLootTarget.FISHING;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "mine", "шахта", "geode", "жеода", "minemenu" -> CopyLootTarget.MINE;
            case "brush", "excavationmenu" -> CopyLootTarget.EXCAVATION;
            case "reagent", "cauldronmenu" -> CopyLootTarget.CAULDRON;
            case "toilet", "шкибиди", "туалет" -> CopyLootTarget.SKIBIDI;
            case "klad", "клад", "hoard" -> CopyLootTarget.WIND;
            case "cr7", "siuu", "роналду" -> CopyLootTarget.RONALDO;
            case "fish", "рыбалка", "удочка", "rybalka" -> CopyLootTarget.FISHING;
            default -> null;
        };
    }

    private String copyLootTitle(CopyLootTarget target) {
        return switch (target) {
            case EXCAVATION -> "Пляжные Раскопки";
            case CAULDRON -> "Котёл Алхимика";
            case MINE -> "Шахта Древних";
            case SKIBIDI -> "Шкибиди Туалет";
            case WIND -> "Древний клад";
            case RONALDO -> "Роналду SIUU";
            case FISHING -> "Рыбалка";
        };
    }

    private LootCategory toLootCategory(CopyLootTarget target) {
        return switch (target) {
            case EXCAVATION -> LootCategory.EXCAVATION;
            case CAULDRON -> LootCategory.CAULDRON;
            case MINE -> LootCategory.MINE;
            case FISHING -> LootCategory.FISHING;
            case SKIBIDI, WIND, RONALDO -> null;
        };
    }

    private String flatLootPath(CopyLootTarget target) {
        return switch (target) {
            case SKIBIDI -> "skibidi.loot";
            case WIND -> "wind.loot";
            case RONALDO -> "ronaldo.loot";
            default -> null;
        };
    }

    private List<PluginConfig.AlienLootItem> flatLootItems(CopyLootTarget target) {
        return switch (target) {
            case SKIBIDI -> plugin.getPluginConfig().getSkibidiLoot();
            case WIND -> plugin.getPluginConfig().getWindLoot();
            case RONALDO -> plugin.getPluginConfig().getRonaldoLoot();
            default -> List.of();
        };
    }

    private static final List<String> COPY_LOOT_EVENTS = Arrays.asList(
            "excavation", "cauldron", "mine", "skibidi", "wind", "ronaldo", "fishing");

    private void handleCopyLootSubcommand(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.color("&e┠ &fИспользование: &#E0C912/" + label + " copylot <откуда> <куда> [редкость]"));
            sender.sendMessage(ColorUtil.color("&e┠ &7Ивенты: &#E0C912excavation&7, &#E0C912cauldron&7, &#E0C912mine&7, &#E0C912skibidi&7, &#E0C912wind&7, &#E0C912ronaldo&7, &#E0C912fishing"));
            sender.sendMessage(ColorUtil.color("&e┠ &7У каждого предмета свой шанс (chance). Пример: &#E0C912/" + label + " copylot excavation skibidi"));
            sender.sendMessage(ColorUtil.color("&e┠ &7Только легендарка: &#E0C912/" + label + " copylot mine excavation legendary"));
            return;
        }
        CopyLootTarget from = parseCopyLootTarget(args[1]);
        CopyLootTarget to = parseCopyLootTarget(args[2]);
        if (from == null || to == null) {
            sender.sendMessage(ColorUtil.color("&e┠ &cУкажите ивенты: &#E0C912excavation&c, &#E0C912cauldron&c, &#E0C912mine&c, &#E0C912skibidi&c, &#E0C912wind&c, &#E0C912ronaldo&c, &#E0C912fishing"));
            return;
        }
        if (from == to) {
            sender.sendMessage(ColorUtil.color("&e┠ &cОткуда и куда — разные ивенты."));
            return;
        }
        BrushTier onlyTier = args.length >= 4 ? BrushTier.fromString(args[3]) : null;
        if (args.length >= 4 && onlyTier == null) {
            sender.sendMessage(ColorUtil.color("&e┠ &cРедкость: &#E0C912обычная&c, &#E0C912эпическая&c или &#E0C912легендарная"));
            return;
        }

        int copied;
        LootCategory fromCat = toLootCategory(from);
        LootCategory toCat = toLootCategory(to);
        if (fromCat != null && toCat != null) {
            copied = plugin.getBrushLootManager().copyLoot(fromCat, toCat, onlyTier);
        } else if (fromCat != null) {
            copied = copyCategoryToFlat(fromCat, to, onlyTier);
        } else if (toCat != null) {
            copied = copyFlatToCategory(from, toCat, onlyTier);
        } else {
            copied = copyFlatToFlat(from, to, onlyTier);
        }

        String tierNote = onlyTier != null
                ? " &8(" + onlyTier.getConfigKey() + ")"
                : " &8(все редкости, свой chance)";
        sender.sendMessage(ColorUtil.color(
                "&e┠ &#34D711Скопировано предметов: &f" + copied
                        + " &7из &#E0C912" + copyLootTitle(from)
                        + " &7в &#E0C912" + copyLootTitle(to) + tierNote
        ));
    }

    private int copyCategoryToFlat(LootCategory from, CopyLootTarget to, BrushTier onlyTier) {
        String root = flatLootPath(to);
        if (root == null) {
            return 0;
        }
        plugin.getConfig().set(root, null);
        int n = 0;
        BrushTier[] tiers = onlyTier != null ? new BrushTier[]{onlyTier} : BrushTier.values();
        for (BrushTier tier : tiers) {
            for (BrushLootManager.BrushLootEntry entry : plugin.getBrushLootManager().getLootEntries(tier, from)) {
                n++;
                String key = (onlyTier == null ? tier.getConfigKey() + "_" : "") + "item" + n;
                writeFlatLootItem(root + "." + key, entry.item(), entry.chance(), tier == BrushTier.LEGENDARY);
            }
        }
        plugin.saveConfig();
        plugin.getPluginConfig().reload();
        return n;
    }

    private int copyFlatToCategory(CopyLootTarget from, LootCategory to, BrushTier onlyTier) {
        List<PluginConfig.AlienLootItem> src = flatLootItems(from);
        BrushLootManager loot = plugin.getBrushLootManager();
        int n = 0;
        BrushTier[] tiers = onlyTier != null ? new BrushTier[]{onlyTier} : BrushTier.values();
        for (BrushTier tier : tiers) {
            loot.clearLoot(tier, to);
            for (PluginConfig.AlienLootItem item : src) {
                if (item.material() == null || item.material().isAir()) {
                    continue;
                }
                if (onlyTier == BrushTier.LEGENDARY && !item.rare()) {
                    continue;
                }
                loot.addLootItem(tier, new ItemStack(item.material(), Math.max(1, item.maxAmount())), item.chancePercent(), to);
                n++;
            }
        }
        return n;
    }

    private int copyFlatToFlat(CopyLootTarget from, CopyLootTarget to, BrushTier onlyTier) {
        String root = flatLootPath(to);
        if (root == null) {
            return 0;
        }
        plugin.getConfig().set(root, null);
        int n = 0;
        for (PluginConfig.AlienLootItem item : flatLootItems(from)) {
            if (item.material() == null || item.material().isAir()) {
                continue;
            }
            if (onlyTier == BrushTier.LEGENDARY && !item.rare()) {
                continue;
            }
            if (onlyTier != null && onlyTier != BrushTier.LEGENDARY && item.rare()) {
                continue;
            }
            n++;
            String key = item.key() == null || item.key().isBlank() ? "item" + n : item.key();
            writeFlatLootItem(root + "." + key, new ItemStack(item.material(), Math.max(1, item.maxAmount())),
                    item.chancePercent(), item.rare());
        }
        plugin.saveConfig();
        plugin.getPluginConfig().reload();
        return n;
    }

    private void writeFlatLootItem(String path, ItemStack item, double chance, boolean rare) {
        plugin.getConfig().set(path + ".material", item.getType().name());
        int amount = Math.max(1, item.getAmount());
        plugin.getConfig().set(path + ".min", amount);
        plugin.getConfig().set(path + ".max", amount);
        plugin.getConfig().set(path + ".chance", chance);
        plugin.getConfig().set(path + ".rare", rare);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("geyserevent.admin")) {
            return Collections.emptyList();
        }
        String cmdName = command.getName().toLowerCase(Locale.ROOT);
        if (cmdName.equals("cauldronmenu") || cmdName.equals("minemenu")) {
            if (args.length == 1) {
                return filterPrefix(Arrays.asList("обычная", "эпическая", "легендарная", "common", "epic", "legendary"), args[0]);
            }
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(Arrays.asList(
                    "menu", "minemenu", "cauldronmenu", "excavationmenu", "mine", "gui",
                    "start", "start-here", "stop", "mineloot", "cauldronloot", "brushloot",
                    "givegeode", "givereagent", "givebrush", "copylot", "minplayers", "ability", "setworld", "saveschem", "status", "top", "reload"
            ), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("mine") || sub.equals("шахта")) {
                return filterPrefix(Arrays.asList("regen", "spawn", "set-here", "remove", "menu", "status"), args[1]);
            }
            if (sub.equals("start") || sub.equals("start-here") || sub.equals("starthere")) {
                return filterPrefix(Arrays.asList("rift", "excavation", "cauldron", "wind", "skibidi", "ronaldo", "fishing"), args[1]);
            }
            if (sub.equals("menu") || sub.equals("gui") || sub.equals("cauldronmenu") || sub.equals("cmenu")
                    || sub.equals("minemenu") || sub.equals("mmenu") || sub.equals("excavationmenu")
                    || sub.equals("givebrush") || sub.equals("brush")
                    || sub.equals("givereagent") || sub.equals("reagent")
                    || sub.equals("givegeode") || sub.equals("geode")) {
                return filterPrefix(Arrays.asList("mine", "cauldron", "excavation", "common", "epic", "legendary", "обычная", "эпическая", "легендарная"), args[1]);
            }
            if (sub.equals("brushloot") || sub.equals("loot") || sub.equals("cauldronloot")
                    || sub.equals("reagentloot") || sub.equals("alchemistloot")
                    || sub.equals("mineloot") || sub.equals("geodeloot")) {
                return filterPrefix(Arrays.asList("add", "list", "remove", "clear", "обычная", "эпическая", "легендарная"), args[1]);
            }
            if (sub.equals("copylot") || sub.equals("copyloot") || sub.equals("cloneloot")) {
                return filterPrefix(COPY_LOOT_EVENTS, args[1]);
            }
            if (sub.equals("minplayers") || sub.equals("minplayer") || sub.equals("игроки") || sub.equals("players")) {
                return filterPrefix(Arrays.asList("rift", "excavation", "cauldron", "wind", "skibidi", "ronaldo", "fishing", "all"), args[1]);
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
            if (sub.equals("copylot") || sub.equals("copyloot") || sub.equals("cloneloot")) {
                return filterPrefix(COPY_LOOT_EVENTS, args[2]);
            }
            if (sub.equals("minplayers") || sub.equals("minplayer") || sub.equals("игроки") || sub.equals("players")) {
                return filterPrefix(Arrays.asList("1", "2", "3", "5", "8", "10"), args[2]);
            }
            if (sub.equals("brushloot") || sub.equals("loot") || sub.equals("cauldronloot")
                    || sub.equals("reagentloot") || sub.equals("alchemistloot")
                    || sub.equals("mineloot") || sub.equals("geodeloot") || sub.equals("menu")) {
                return filterPrefix(Arrays.asList("common", "epic", "legendary", "обычная", "эпическая", "легендарная"), args[2]);
            }
        }
        if (args.length == 4) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("copylot") || sub.equals("copyloot") || sub.equals("cloneloot")) {
                return filterPrefix(Arrays.asList("обычная", "эпическая", "легендарная", "common", "epic", "legendary"), args[3]);
            }
            if ((sub.equals("brushloot") || sub.equals("loot") || sub.equals("cauldronloot")
                    || sub.equals("reagentloot") || sub.equals("alchemistloot")
                    || sub.equals("mineloot") || sub.equals("geodeloot")) && args[1].equalsIgnoreCase("add")) {
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
