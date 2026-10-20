package ru.geyserevent.command;

import org.bukkit.Bukkit;
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
import ru.geyserevent.event.InfernalRiftManager;
import ru.geyserevent.util.ColorUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Обработчик команд /rift (/event, /razlom, /geyserevent).
 */
public class RiftCommand implements CommandExecutor, TabCompleter {

    private final GeyserEventPlugin plugin;

    public RiftCommand(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginConfig cfg = plugin.getPluginConfig();
        InfernalRiftManager manager = plugin.getRiftManager();

        // Если команда вызвана игроком без аргументов — открываем нативное окно Bedrock (Cumulus)
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&cЭта команда доступна только игрокам. Для консоли: /rift <start|stop|reload|setworld>"));
                return true;
            }
            if (!plugin.getBedrockService().canParticipate(player)) {
                player.sendMessage(cfg.getMsgOnlyBedrock());
                return true;
            }
            plugin.getFormMenu().openMainForm(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "top" -> {
                if (sender instanceof Player player) {
                    if (!plugin.getBedrockService().canParticipate(player)) {
                        player.sendMessage(cfg.getMsgOnlyBedrock());
                        return true;
                    }
                    plugin.getFormMenu().openLeaderboardForm(player);
                } else {
                    sender.sendMessage(cfg.getPrefix() + "Топ урона доступен в игре.");
                }
                return true;
            }

            case "compass", "nav" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(cfg.getPrefix() + "Только для игроков.");
                    return true;
                }
                if (!plugin.getBedrockService().canParticipate(player)) {
                    player.sendMessage(cfg.getMsgOnlyBedrock());
                    return true;
                }
                boolean enabled = manager.toggleCompass(player);
                player.sendMessage(cfg.getPrefix() + ColorUtil.color(
                        enabled ? "&aНавигатор до Адского Разлома включён!" : "&eНавигатор до Адского Разлома выключен."
                ));
                return true;
            }

            case "info", "boss" -> {
                if (sender instanceof Player player) {
                    if (!plugin.getBedrockService().canParticipate(player)) {
                        player.sendMessage(cfg.getMsgOnlyBedrock());
                        return true;
                    }
                    plugin.getFormMenu().openBossInfoForm(player);
                }
                return true;
            }

            case "admin" -> {
                if (!(sender instanceof Player player)) {
                    return true;
                }
                if (!player.hasPermission("geyserevent.admin")) {
                    player.sendMessage(cfg.getPrefix() + ColorUtil.color("&cУ вас нет прав администратора!"));
                    return true;
                }
                plugin.getFormMenu().openAdminForm(player);
                return true;
            }

            case "start" -> {
                if (!sender.hasPermission("geyserevent.admin")) {
                    sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&cНедостаточно прав!"));
                    return true;
                }
                if (manager.startEventRandomInConfiguredWorld()) {
                    sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&aИвент «Адский Разлом» запущен в мире &e" + cfg.getWorldName() + "&a!"));
                } else {
                    sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&cНе удалось запустить ивент в мире &e" + cfg.getWorldName()
                            + "&c. Убедитесь, что мир загружен, или используйте &e/rift start-here&c."));
                }
                return true;
            }

            case "start-here", "starthere" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&cЭту команду может выполнить только игрок!"));
                    return true;
                }
                if (!player.hasPermission("geyserevent.admin")) {
                    player.sendMessage(cfg.getPrefix() + ColorUtil.color("&cНедостаточно прав!"));
                    return true;
                }
                if (manager.startEventAt(player.getLocation())) {
                    player.sendMessage(cfg.getPrefix() + ColorUtil.color("&aИвент «Адский Разлом» запущен прямо на вашей позиции!"));
                }
                return true;
            }

            case "stop" -> {
                if (!sender.hasPermission("geyserevent.admin")) {
                    sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&cНедостаточно прав!"));
                    return true;
                }
                if (!manager.isActive()) {
                    sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&eИвент сейчас не активен."));
                    return true;
                }
                manager.stopEvent(false);
                sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&aИвент остановлен, схематика полностью откачена."));
                return true;
            }

            case "ability", "cast" -> {
                if (!sender.hasPermission("geyserevent.admin")) {
                    sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&cНедостаточно прав!"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&eИспользование: &6/" + label + " ability <pull|push|gather|minions>"));
                    return true;
                }
                LivingEntity boss = manager.getBossEntity();
                if (boss == null || boss.isDead()) {
                    sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&cБосс сейчас не активен!"));
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
                    sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&cНеизвестная способность! Доступно: &epull, push, gather, minions"));
                    return true;
                }
                int count = plugin.getAbilityController().executeAbility(boss, type);
                sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&aБосс применил способность &e"
                        + type.getDisplayName() + " &7(целей/мобов: &f" + count + "&7)."));
                return true;
            }

            case "setworld" -> {
                if (!sender.hasPermission("geyserevent.admin")) {
                    sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&cНедостаточно прав!"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&eИспользование: &6/" + label + " setworld <имя_мира> &7(текущий: &e" + cfg.getWorldName() + "&7)"));
                    return true;
                }
                String newWorld = args[1];
                cfg.setWorldNameAndSave(newWorld);
                sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&aМир появления ивента изменён на &e" + newWorld + " &aи сохранён в config.yml!"));
                return true;
            }

            case "saveschem" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(cfg.getPrefix() + "Только для игроков.");
                    return true;
                }
                if (!player.hasPermission("geyserevent.admin")) {
                    player.sendMessage(cfg.getPrefix() + ColorUtil.color("&cНедостаточно прав!"));
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
                    player.sendMessage(cfg.getPrefix() + ColorUtil.color("&aСхематика успешно сохранена (&e" + saved + " блоков&a)!"));
                } catch (IOException ex) {
                    player.sendMessage(cfg.getPrefix() + ColorUtil.color("&cОшибка сохранения схематики: " + ex.getMessage()));
                }
                return true;
            }

            case "reload" -> {
                if (!sender.hasPermission("geyserevent.admin")) {
                    sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&cНедостаточно прав!"));
                    return true;
                }
                cfg.reload();
                plugin.getSchematicManager().loadSchematicFile();
                plugin.getBedrockService().detectHooks();
                plugin.getPlayerPointsHook().setupHook();
                manager.startAutoScheduler();
                sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&aКонфигурация и схематика GeyserEvent успешно перезагружены!"));
                return true;
            }

            default -> {
                sender.sendMessage(cfg.getPrefix() + ColorUtil.color("&eДоступные команды:"));
                sender.sendMessage(ColorUtil.color(" &6/" + label + " &8— &fОткрыть меню Адского Разлома (Bedrock UI)"));
                sender.sendMessage(ColorUtil.color(" &6/" + label + " top &8— &fТоп урона и награды (3000/2000/1000 осколков)"));
                sender.sendMessage(ColorUtil.color(" &6/" + label + " compass &8— &fВкл/выкл навигатор до Разлома"));
                if (sender.hasPermission("geyserevent.admin")) {
                    sender.sendMessage(ColorUtil.color(" &c/" + label + " start &8— &7Запустить ивент в мире " + cfg.getWorldName()));
                    sender.sendMessage(ColorUtil.color(" &c/" + label + " start-here &8— &7Запустить ивент на вашей позиции"));
                    sender.sendMessage(ColorUtil.color(" &c/" + label + " stop &8— &7Остановить ивент и откатить блоки"));
                    sender.sendMessage(ColorUtil.color(" &c/" + label + " ability <pull|push|gather|minions> &8— &7Способность босса"));
                    sender.sendMessage(ColorUtil.color(" &c/" + label + " setworld <мир> &8— &7Изменить мир ивента"));
                    sender.sendMessage(ColorUtil.color(" &c/" + label + " reload &8— &7Перезагрузить конфиг и схематику"));
                }
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("menu", "top", "compass", "info"));
            if (sender.hasPermission("geyserevent.admin")) {
                base.addAll(Arrays.asList("admin", "start", "start-here", "stop", "ability", "setworld", "saveschem", "reload"));
            }
            return filterPrefix(base, args[0]);
        }

        if (args.length == 2 && sender.hasPermission("geyserevent.admin")) {
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
