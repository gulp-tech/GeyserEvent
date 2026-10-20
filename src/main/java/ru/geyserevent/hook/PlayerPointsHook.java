package ru.geyserevent.hook;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import ru.geyserevent.GeyserEventPlugin;

import java.util.UUID;

/**
 * Интеграция с плагином PlayerPoints для выдачи донат-валюты (Осколков)
 * победителям по урону (1 место — 3000, 2 место — 2000, 3 место — 1000).
 */
public class PlayerPointsHook {

    private final GeyserEventPlugin plugin;
    private PlayerPointsAPI pointsApi;

    public PlayerPointsHook(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        setupHook();
    }

    public final void setupHook() {
        Plugin pp = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (pp != null && pp.isEnabled()) {
            try {
                this.pointsApi = PlayerPoints.getInstance().getAPI();
                if (this.pointsApi != null) {
                    plugin.getLogger().info("Успешно подключен PlayerPoints API для выдачи осколков!");
                    return;
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Не удалось напрямую получить PlayerPointsAPI, будет использована консольная команда: " + t.getMessage());
            }
        } else {
            plugin.getLogger().warning("Плагин PlayerPoints не найден или ещё не включён. При выдаче наград будет использована команда из config.yml.");
        }
    }

    /**
     * Выдаёт указанное количество осколков (PlayerPoints) игроку.
     *
     * @param playerUuid UUID игрока
     * @param playerName Никнейм игрока (для консольной команды, если API недоступен)
     * @param amount     Количество осколков (например 3000, 2000, 1000)
     * @return true, если выдача прошла успешно
     */
    public boolean giveShards(UUID playerUuid, String playerName, int amount) {
        if (amount <= 0) {
            return false;
        }

        // 1. Попытка выдать через PlayerPointsAPI
        if (plugin.getPluginConfig().isUsePlayerPointsApi()) {
            if (pointsApi == null) {
                setupHook();
            }
            if (pointsApi != null && playerUuid != null) {
                try {
                    boolean result = pointsApi.give(playerUuid, amount);
                    if (result) {
                        return true;
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Ошибка при вызове PlayerPointsAPI.give: " + t.getMessage());
                }
            }
        }

        // 2. Резервная выдача через консольную команду (points give <player> <amount>)
        String cmdTemplate = plugin.getPluginConfig().getFallbackCommand();
        if (cmdTemplate != null && !cmdTemplate.isBlank() && playerName != null && !playerName.isBlank()) {
            String cmd = cmdTemplate
                    .replace("%player%", playerName)
                    .replace("%amount%", String.valueOf(amount));
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            final String finalCmd = cmd;
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd)
            );
            return true;
        }

        return false;
    }

    /**
     * Возвращает текущий баланс осколков игрока (если PlayerPoints API доступен).
     */
    public int lookShards(UUID playerUuid) {
        if (pointsApi == null || playerUuid == null) {
            return 0;
        }
        try {
            return pointsApi.look(playerUuid);
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
