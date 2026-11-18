package ru.geyserevent.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.event.EventType;
import ru.geyserevent.newyear.NewYearEventManager;
import ru.geyserevent.util.ColorUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class NewYearCommand implements CommandExecutor, TabCompleter {

    private final GeyserEventPlugin plugin;

    public NewYearCommand(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("geyserevent.admin")) {
            sender.sendMessage(ColorUtil.color("&e┠ &cНет прав. Нужно &#E0C912geyserevent.admin&c."));
            return true;
        }
        String sub = args.length == 0 ? "start" : args[0].toLowerCase(Locale.ROOT);
        NewYearEventManager mgr = plugin.getNewYearEventManager();
        if (mgr == null) {
            sender.sendMessage(ColorUtil.color("&e┠ &cМенеджер «Новый год» не загружен."));
            return true;
        }
        switch (sub) {
            case "here", "тут", "здесь" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cТолько для игроков: &#E0C912/" + label + " here"));
                    return true;
                }
                boolean started = plugin.getRiftManager().startEventAt(player.getLocation(), EventType.NEWYEAR);
                sender.sendMessage(started
                        ? ColorUtil.color("&e┠ &#34D711Карета Санты здесь.")
                        : ColorUtil.color("&e┠ &cНе удалось запустить."));
                return true;
            }
            case "stop", "off", "стоп" -> {
                if (!mgr.isActive()) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cНовый год сейчас не активен."));
                    return true;
                }
                mgr.stopEvent();
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Карета убрана."));
                return true;
            }
            case "help", "?" -> {
                sender.sendMessage(ColorUtil.color("&e┼ &c&lНовый год"));
                sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " &8— &fслучайная точка в мире ивентов"));
                sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " here &8— &fна своей позиции"));
                sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " stop"));
                sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/rift menu &8— &fлут подарков"));
                return true;
            }
            default -> {
                boolean started = plugin.getRiftManager().startEventRandomInConfiguredWorld(EventType.NEWYEAR);
                sender.sendMessage(started
                        ? ColorUtil.color("&e┠ &#34D711Карета Санты. Координаты в чат, &#E0C912/event gps")
                        : ColorUtil.color("&e┠ &cНе удалось запустить в мире ивентов."));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("geyserevent.admin") || args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Arrays.asList("start", "here", "stop", "help").stream()
                .filter(s -> s.startsWith(prefix))
                .collect(Collectors.toList());
    }
}
