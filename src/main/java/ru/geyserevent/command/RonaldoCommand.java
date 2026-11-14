package ru.geyserevent.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.event.EventType;
import ru.geyserevent.ronaldo.RonaldoEventManager;
import ru.geyserevent.util.ColorUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class RonaldoCommand implements CommandExecutor, TabCompleter {

    private final GeyserEventPlugin plugin;

    public RonaldoCommand(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("geyserevent.admin")) {
            sender.sendMessage(ColorUtil.color("&e┠ &cНет прав. Нужно &#E0C912geyserevent.admin&c."));
            return true;
        }
        String sub = args.length == 0 ? "start" : args[0].toLowerCase(Locale.ROOT);
        RonaldoEventManager mgr = plugin.getRonaldoEventManager();
        if (sub.equals("stop") || sub.equals("off") || sub.equals("стоп")) {
            if (mgr == null || !mgr.isActive()) {
                sender.sendMessage(ColorUtil.color("&e┠ &cРоналду сейчас не активен."));
                return true;
            }
            mgr.stopEvent();
            sender.sendMessage(ColorUtil.color("&e┠ &#34D711Стадион убран, варп удалён."));
            return true;
        }
        sender.sendMessage(ColorUtil.color("&e┠ &cРоналду SIUU временно выключен в коде."));
        return true;
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
