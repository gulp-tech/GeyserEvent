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
import ru.geyserevent.alien.AlienInvasionManager;
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
                        sender.sendMessage(ColorUtil.color("&e┠ &cУкажите ивент: &#E0C912rift &c(Разлом), &#E0C912excavation &c(Раскопки), &#E0C912cauldron &c(Котёл Алхимика) или &#E0C912alien &c(Пришельцы)."));
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
                        sender.sendMessage(ColorUtil.color("&e┠ &cУкажите ивент: &#E0C912rift&c, &#E0C912excavation&c, &#E0C912cauldron &cили &#E0C912alien&c."));
                        return true;
                    }
                    type = parsed;
                }
                manager.startEventAt(player.getLocation(), type);
                return true;
            }

            case "stop" -> {
                boolean riftActive = manager.isActive();
                boolean alienActive = plugin.getAlienInvasionManager() != null
                        && plugin.getAlienInvasionManager().isActive();
                if (!riftActive && !alienActive) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cИвент не активен."));
                    return true;
                }
                if (riftActive) {
                    manager.stopEvent(false);
                }
                if (alienActive) {
                    plugin.getAlienInvasionManager().stopEvent();
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
                if (plugin.getSummonAltarManager() != null) {
                    sender.sendMessage(ColorUtil.color("&e┼ &fПризыв: &f" + plugin.getSummonAltarManager().describe()));
                }
                }
                if (plugin.getAlienInvasionManager() != null && plugin.getAlienInvasionManager().isActive()) {
                    AlienInvasionManager alien = plugin.getAlienInvasionManager();
                    Location aLoc = alien.getCenterLocation();
                    sender.sendMessage(ColorUtil.color("&e┼ &fИвент пришельцев: &#34D711"
                            + (alien.isDefeated() ? "босс повержен, сундуки открыты" : "Пришелец-Владыка")));
                    sender.sendMessage(ColorUtil.color("&e┠ &fЗдоровье пришельца: &c"
                            + (int) Math.ceil(alien.getBossCurrentHealth()) + "/"
                            + (int) Math.ceil(alien.getBossMaxHealth()) + " ❤"
                            + " &8| &fМалых пришельцев: &a" + alien.countAlienMinions()));
                    if (aLoc != null) {
                        sender.sendMessage(ColorUtil.color("&e┠ &fКоординаты: &#E0C912"
                                + aLoc.getBlockX() + " " + aLoc.getBlockY() + " " + aLoc.getBlockZ()
                                + " &8(&fосталось " + alien.getSecondsRemaining() + " сек.&8)"));
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
        };
        String eventTitle = switch (category) {
            case EXCAVATION -> "«Пляжные Раскопки»";
            case CAULDRON -> "«Котёл Алхимика»";
            case MINE -> "«Шахта Древних»";
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
        };
        target.getInventory().addItem(stack);
        sender.sendMessage(ColorUtil.color("&e┠ &fВыдан " + mgr.getCategoryItemTitle(tier, category) + " &fигроку &#E0C912" + target.getName() + "&f."));
    }

    private void sendAdminHelp(CommandSender sender, String label, PluginConfig cfg) {
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " menu &8— &fОбщее GUI-меню настройки лута всех ивентов"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/minemenu &8(&#E0C912/" + label + " minemenu&8) — &fGUI-меню лута «Шахты Древних (0 ~ 0)»"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/cauldronmenu &8(&#E0C912/" + label + " cauldronmenu&8) — &fGUI-меню лута «Котла Алхимика»"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " mine <regen|spawn|set-here|remove> &8— &fУправление «Шахтой Древних»"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " start [rift|excavation|cauldron] &8— &fЗапустить ивент в " + cfg.getWorldName()));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " start-here [rift|excavation|cauldron] &8— &fЗапустить на вашей позиции"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " stop &8— &fОстановить временный ивент"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " mineloot <add|list|remove|clear> <обычная|эпическая|легендарная>"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " cauldronloot <add|list|remove|clear> <обычная|эпическая|легендарная>"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " brushloot <add|list|remove|clear> <обычная|эпическая|легендарная>"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " givegeode / givereagent / givebrush <обычная|эпическая|легендарная> [игрок]"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " reload"));
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
                    "givegeode", "givereagent", "givebrush", "ability", "setworld", "saveschem", "status", "top", "reload"
            ), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("mine") || sub.equals("шахта")) {
                return filterPrefix(Arrays.asList("regen", "spawn", "set-here", "remove", "menu", "status"), args[1]);
            }
            if (sub.equals("start") || sub.equals("start-here") || sub.equals("starthere")) {
                return filterPrefix(Arrays.asList("rift", "excavation", "cauldron", "alien"), args[1]);
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
            if (sub.equals("brushloot") || sub.equals("loot") || sub.equals("cauldronloot")
                    || sub.equals("reagentloot") || sub.equals("alchemistloot")
                    || sub.equals("mineloot") || sub.equals("geodeloot") || sub.equals("menu")) {
                return filterPrefix(Arrays.asList("common", "epic", "legendary", "обычная", "эпическая", "легендарная"), args[2]);
            }
        }
        if (args.length == 4) {
            String sub = args[0].toLowerCase(Locale.ROOT);
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
