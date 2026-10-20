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
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.boss.BossAbilityType;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.event.DamageLeaderboard;
import ru.geyserevent.event.InfernalRiftManager;
import ru.geyserevent.util.ColorUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Админ-команды управления ивентом «Адский Разлом» (/rift).
 * Без каких-либо меню — только прямые команды управления.
 */
public class RiftCommand implements CommandExecutor, TabCompleter {

    private final GeyserEventPlugin plugin;

    public RiftCommand(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("geyserevent.admin")) {
            sender.sendMessage(ColorUtil.color("&e┠ &cУ вас нет прав администратора! Для игроков: &#34D711/event delay &cи &#34D711/event gps"));
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
            case "start" -> {
                if (manager.startEventRandomInConfiguredWorld()) {
                    sender.sendMessage(ColorUtil.color("&e┠ &#34D711Ивент «Адский Разлом» запущен в мире &#E0C912" + cfg.getWorldName() + "&#34D711!"));
                } else {
                    sender.sendMessage(ColorUtil.color("&e┠ &cНе удалось запустить ивент в мире &#E0C912" + cfg.getWorldName()
                            + "&c. Убедитесь, что мир создан, или используйте &#E0C912/rift start-here&c."));
                }
                return true;
            }

            case "start-here", "starthere" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cЭту команду может выполнить только игрок!"));
                    return true;
                }
                if (manager.startEventAt(player.getLocation())) {
                    player.sendMessage(ColorUtil.color("&e┠ &#34D711Ивент «Адский Разлом» запущен прямо на вашей позиции!"));
                }
                return true;
            }

            case "stop" -> {
                if (!manager.isActive()) {
                    sender.sendMessage(ColorUtil.color("&e┠ &eИвент сейчас не активен."));
                    return true;
                }
                manager.stopEvent(false);
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Ивент остановлен, схематика полностью откачена."));
                return true;
            }

            case "ability", "cast" -> {
                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.color("&e┠ &fИспользование: &#E0C912/" + label + " ability <pull|push|gather|minions>"));
                    return true;
                }
                LivingEntity boss = manager.getBossEntity();
                if (boss == null || boss.isDead()) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cБосс сейчас не активен!"));
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
                    sender.sendMessage(ColorUtil.color("&e┠ &cНеизвестная способность! Доступно: &#E0C912pull, push, gather, minions"));
                    return true;
                }
                int count = plugin.getAbilityController().executeAbility(boss, type);
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Босс применил способность &#E0C912"
                        + type.getDisplayName() + " &7(целей/мобов: &f" + count + "&7)."));
                return true;
            }

            case "setworld" -> {
                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.color("&e┠ &fИспользование: &#E0C912/" + label + " setworld <имя_мира> &7(текущий: &#E0C912" + cfg.getWorldName() + "&7)"));
                    return true;
                }
                String newWorld = args[1];
                cfg.setWorldNameAndSave(newWorld);
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Мир появления ивента изменён на &#E0C912" + newWorld + " &#34D711и сохранён в config.yml!"));
                return true;
            }

            case "saveschem" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cТолько для игроков."));
                    return true;
                }
                int radius = 6;
                if (args.length >= 2) {
                    try {
                        radius = Math.max(2, Math.min(20, Integer.parseInt(args[1])));
                    } catch (NumberFormatException ignored) {
                    }
                }
                try {
                    int saved = plugin.getSchematicManager().saveCustomSchematicFromWorld(player.getLocation(), radius);
                    player.sendMessage(ColorUtil.color("&e┠ &#34D711Схематика успешно сохранена (&f" + saved + " блоков&#34D711)!"));
                } catch (IOException ex) {
                    player.sendMessage(ColorUtil.color("&e┠ &cОшибка сохранения схематики: " + ex.getMessage()));
                }
                return true;
            }

            case "status", "info" -> {
                if (manager.isActive() && manager.getRiftCenterLocation() != null) {
                    Location loc = manager.getRiftCenterLocation();
                    sender.sendMessage(ColorUtil.color("&e┠ &fСтатус ивента: &#34D711АКТИВЕН"));
                    sender.sendMessage(ColorUtil.color("&e┠ &fМир: &#E0C912" + loc.getWorld().getName()));
                    sender.sendMessage(ColorUtil.color("&e┠ &fКоординаты: &#E0C912" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
                    sender.sendMessage(ColorUtil.color("&e┠ &fЗдоровье босса: &c" + (int) Math.ceil(manager.getBossCurrentHealth()) + "/" + (int) Math.ceil(manager.getBossMaxHealth()) + " ❤"));
                    sender.sendMessage(ColorUtil.color("&e┠ &fЖивых приспешников: &#E0C912" + plugin.getAbilityController().getActiveMinionCount()));
                } else {
                    sender.sendMessage(ColorUtil.color("&e┠ &fСтатус ивента: &cОжидание"));
                    sender.sendMessage(ColorUtil.color("&e┠ &fДо авто-старта: &#E0C912" + ColorUtil.formatDurationVerbose(manager.getSecondsUntilNextStart())));
                    sender.sendMessage(ColorUtil.color("&e┠ &fМир в конфиге: &#E0C912" + cfg.getWorldName()));
                }
                return true;
            }

            case "top" -> {
                List<DamageLeaderboard.DamageEntry> top = manager.getLeaderboard().getTop(5);
                sender.sendMessage(ColorUtil.color("&e┠ &fТекущий топ урона по Боссу:"));
                if (top.isEmpty()) {
                    sender.sendMessage(ColorUtil.color("&e┠ &7Пока никто не нанёс урона."));
                } else {
                    for (int i = 0; i < top.size(); i++) {
                        DamageLeaderboard.DamageEntry e = top.get(i);
                        sender.sendMessage(ColorUtil.color("&e┠ &6#" + (i + 1) + " &#E0C912" + e.playerName() + " &8— &c" + (int) e.damage() + " ❤"));
                    }
                }
                return true;
            }

            case "reload" -> {
                cfg.reload();
                plugin.getSchematicManager().loadSchematicFile();
                plugin.getBedrockService().detectHooks();
                plugin.getPlayerPointsHook().setupHook();
                plugin.getTopRightHudManager().exportAndInstallResourcePack();
                manager.startAutoScheduler();
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Конфигурация, схематика и ресурспак GeyserEvent успешно перезагружены!"));
                return true;
            }

            default -> {
                sendAdminHelp(sender, label, cfg);
                return true;
            }
        }
    }

    private void sendAdminHelp(CommandSender sender, String label, PluginConfig cfg) {
        sender.sendMessage(ColorUtil.color("&e┠ &fАдмин-команды &#34D711Адский Разлом&f:"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " start &8— &fЗапустить ивент в мире " + cfg.getWorldName()));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " start-here &8— &fЗапустить ивент прямо здесь"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " stop &8— &fОстановить ивент и откатить схематику"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " ability <pull|push|gather|minions> &8— &fСпособность босса"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " setworld <мир> &8— &fИзменить мир ивента"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " saveschem [радиус] &8— &fСохранить свою схематику"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " status &8| &#E0C912top &8— &fСтатус и топ урона"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " reload &8— &fПерезагрузить конфиг и схематику"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("geyserevent.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(Arrays.asList("start", "start-here", "stop", "ability", "setworld", "saveschem", "status", "top", "reload"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("ability") || args[0].equalsIgnoreCase("cast")) {
                return filterPrefix(Arrays.asList("pull", "push", "gather", "minions"), args[1]);
            }
            if (args[0].equalsIgnoreCase("setworld")) {
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
