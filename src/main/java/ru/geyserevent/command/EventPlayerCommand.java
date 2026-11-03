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
            sender.sendMessage(ColorUtil.color("&e┠ &#34D711/event mine &8— &fСтатус постоянной «Шахты Древних (0 ~ 0)»"));
            sender.sendMessage(ColorUtil.color("&e┠ &#34D711/event backpack &8— &fОткрыть меню Рюкзака Шахтёра"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "backpack", "рюкзак", "forge", "кузница" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtil.color("&e┠ &cТолько для игроков!"));
                    return true;
                }
                if (plugin.getMinerBackpackManager() != null) {
                    plugin.getMinerBackpackManager().openForgeAndBackpackMenu(player);
                }
                return true;
            }

            case "delay", "time", "info" -> {
                // Ивент «Вторжение Пришельцев» идёт в своём менеджере
                if (plugin.getAlienInvasionManager() != null && plugin.getAlienInvasionManager().isActive()
                        && plugin.getAlienInvasionManager().getCenterLocation() != null) {
                    var alien = plugin.getAlienInvasionManager();
                    for (String line : cfg.getAlienDelayActiveLines()) {
                        sender.sendMessage(ColorUtil.color(line
                                .replace("%hp%", String.valueOf((int) Math.ceil(alien.getBossCurrentHealth())))
                                .replace("%max_hp%", String.valueOf((int) Math.ceil(alien.getBossMaxHealth())))
                                .replace("%remaining%", ColorUtil.formatDuration(alien.getSecondsRemaining()))
                                .replace("%time%", ColorUtil.formatDuration(alien.getSecondsRemaining()))
                                .replace("%x%", String.valueOf(alien.getCenterLocation().getBlockX()))
                                .replace("%y%", String.valueOf(alien.getCenterLocation().getBlockY()))
                                .replace("%z%", String.valueOf(alien.getCenterLocation().getBlockZ()))));
                    }
                    return true;
                }
                if (manager.isActive() && manager.getRiftCenterLocation() != null) {
                    List<String> lines = switch (manager.getActiveEventType()) {
                        case BEACH_EXCAVATION -> cfg.getExcavationDelayActiveLines();
                        case ALCHEMIST_CAULDRON -> cfg.getCauldronDelayActiveLines();
                        default -> cfg.getDelayActiveLines();
                    };
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

            case "mine", "шахта" -> {
                var mineMgr = plugin.getAncientMineManager();
                if (mineMgr != null && mineMgr.isMineActive()) {
                    var loc = mineMgr.getCenterLocation();
                    sender.sendMessage(ColorUtil.color("&e┠ &#FFB81CШахта Древних &8(&fПостоянный ивент&8)"));
                    sender.sendMessage(ColorUtil.color("&e┠ &fКоординаты: &#E0C912" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
                    sender.sendMessage(ColorUtil.color("&e┠ &fЖеод в жиле: &#34D711" + mineMgr.getActiveGeodeCount()
                            + " шт. &8| &fОбновление руд: &#E0C912" + ColorUtil.formatDuration(mineMgr.getSecondsUntilFullRegen())));
                } else {
                    sender.sendMessage(ColorUtil.color("&e┠ &cШахта Древних сейчас недоступна."));
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
            for (String opt : Arrays.asList("delay", "gps", "mine", "backpack")) {
                if (opt.startsWith(lower)) {
                    result.add(opt);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
