package ru.geyserevent.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.event.EventType;
import ru.geyserevent.event.InfernalRiftManager;
import ru.geyserevent.util.ColorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Команды для игроков (/event delay, /event gps).
 * /event delay не раскрывает, какой именно ивент появится следующим.
 */
public class EventPlayerCommand implements CommandExecutor, TabCompleter {

    private final GeyserEventPlugin plugin;

    public EventPlayerCommand(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginConfig cfg = plugin.getPluginConfig();
        InfernalRiftManager manager = plugin.getRiftManager();

        if (sender instanceof Player player && !plugin.getBedrockService().canParticipate(player)) {
            player.sendMessage(cfg.getMsgOnlyBedrock());
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ColorUtil.color("&e┠ &fКоманды ивента:"));
            sender.sendMessage(ColorUtil.color("&e┠ &#34D711/event delay &8— &fСколько осталось до ивента"));
            sender.sendMessage(ColorUtil.color("&e┠ &#34D711/event gps &8— &fПоставить локатор до ивента"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "delay", "time", "info" -> {
                if (manager.isActive() && manager.getRiftCenterLocation() != null) {
                    List<String> lines = (manager.getActiveEventType() == EventType.BEACH_EXCAVATION)
                            ? cfg.getExcavationDelayActiveLines()
                            : cfg.getDelayActiveLines();
                    for (String line : lines) {
                        sender.sendMessage(manager.formatEventPlaceholders(line, manager.getRiftCenterLocation()));
                    }
                } else {
                    for (String line : cfg.getDelayWaitingLines()) {
                        sender.sendMessage(manager.formatEventPlaceholders(line, null));
                    }
                }
                return true;
            }

            case "gps", "track", "compass" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cКоманда /event gps доступна только игрокам в игре!"));
                    return true;
                }
                manager.togglePlayerGps(player);
                return true;
            }

            default -> {
                sender.sendMessage(ColorUtil.color("&e┠ &fИспользуйте: &#34D711/event delay &fили &#34D711/event gps"));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String lower = args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String opt : Arrays.asList("delay", "gps")) {
                if (opt.startsWith(lower)) {
                    result.add(opt);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
