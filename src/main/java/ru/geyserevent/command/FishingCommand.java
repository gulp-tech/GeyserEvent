package ru.geyserevent.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.event.EventType;
import ru.geyserevent.fishing.FishingEventManager;
import ru.geyserevent.util.ColorUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class FishingCommand implements CommandExecutor, TabCompleter {

    private final GeyserEventPlugin plugin;

    public FishingCommand(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("geyserevent.admin")) {
            sender.sendMessage(ColorUtil.color("&e┠ &cНет прав. Нужно &#E0C912geyserevent.admin&c."));
            return true;
        }
        String sub = args.length == 0 ? "start" : args[0].toLowerCase(Locale.ROOT);
        FishingEventManager mgr = plugin.getFishingEventManager();
        if (mgr == null) {
            sender.sendMessage(ColorUtil.color("&e┠ &cМенеджер рыбалки не загружен."));
            return true;
        }
        switch (sub) {
            case "here", "тут", "здесь" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cТолько для игроков: &#E0C912/" + label + " here"));
                    return true;
                }
                boolean started = plugin.getRiftManager().startEventAt(player.getLocation(), EventType.FISHING);
                sender.sendMessage(started
                        ? ColorUtil.color("&e┠ &#34D711Рыбалка запущена здесь. Координаты в чат.")
                        : ColorUtil.color("&e┠ &cНе удалось запустить."));
                return true;
            }
            case "stop", "off", "стоп" -> {
                if (!mgr.isActive()) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cРыбалка сейчас не активна."));
                    return true;
                }
                mgr.stopEvent();
                sender.sendMessage(ColorUtil.color("&e┠ &#34D711Пруд убран."));
                return true;
            }
            case "help", "?" -> {
                sender.sendMessage(ColorUtil.color("&e┼ &b&lРыбалка"));
                sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " &8— &fзапустить в мире ивентов"));
                sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " here &8— &fна своей позиции"));
                sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/" + label + " stop &8— &fстоп"));
                sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/rift menu &8— &fлут удочек"));
                sender.sendMessage(ColorUtil.color("&e┠ &#E0C912/rift copylot excavation fishing"));
                return true;
            }
            default -> {
                boolean started = plugin.getRiftManager().startEventRandomInConfiguredWorld(EventType.FISHING);
                sender.sendMessage(started
                        ? ColorUtil.color("&e┠ &#34D711Рыбалка запущена. Координаты в чат, &#E0C912/event gps")
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
