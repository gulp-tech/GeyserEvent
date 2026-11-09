package ru.geyserevent.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.event.EventType;
import ru.geyserevent.util.ColorUtil;
import ru.geyserevent.wind.GravityHoardManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Отдельная админ-команда запуска «Древнего клада».
 * /klad — случайная точка в мире ивентов
 * /klad here — на позиции игрока
 * /klad stop — остановить
 */
public final class KladCommand implements CommandExecutor, TabCompleter {

    private final GeyserEventPlugin plugin;

    public KladCommand(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("geyserevent.admin")) {
            sender.sendMessage(ColorUtil.color("&e┠ &cНет прав. Нужно &#E0C912geyserevent.admin&c."));
            return true;
        }
        String sub = args.length == 0 ? "start" : args[0].toLowerCase(Locale.ROOT);
        GravityHoardManager wind = plugin.getGravityHoardManager();
        if (wind == null) {
            sender.sendMessage(ColorUtil.color("&e┠ &cМенеджер клада не загружен."));
            return true;
        }
        switch (sub) {
            case "here", "тут", "здесь" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cТолько для игроков: &#E0C912/" + label + " here"));
                    return true;
                }
                boolean started = plugin.getRiftManager().startEventAt(player.getLocation(), EventType.GRAVITY_HOARD);
                if (!started) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cНе удалось поставить клад здесь."));
                } else {
                    sender.sendMessage(ColorUtil.color("&e┠ &#34D711Древний клад запущен на твоей позиции."));
                }
                return true;
            }
            case "stop", "off", "стоп" -> {
                if (!wind.isActive()) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cКлад сейчас не активен."));
                    return true;
                }
                wind.stopEvent();
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Древний клад остановлен."));
                return true;
            }
            case "help", "?" -> {
                sendHelp(sender, label);
                return true;
            }
            default -> {
                boolean started = plugin.getRiftManager().startEventRandomInConfiguredWorld(EventType.GRAVITY_HOARD);
                if (!started) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cНе удалось запустить клад в мире ивентов."));
                } else {
                    sender.sendMessage(ColorUtil.color("&e┠ &#34D711Древний клад запущен. &#E0C912/" + label + " stop &fчтобы убрать."));
                }
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtil.color("&e┼ &#7DF9FFДревний клад"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " &8— &fзапустить в мире ивентов"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " here &8— &fпоставить на своей позиции"));
        sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " stop &8— &fостановить"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("geyserevent.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Arrays.asList("start", "here", "stop", "help").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
