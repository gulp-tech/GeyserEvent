package ru.geyserevent.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;
import ru.geyserevent.GeyserEventPlugin;

import java.util.UUID;

/**
 * Сервис проверки игроков Bedrock Edition (GeyserMC / Floodgate).
 */
public class BedrockService {

    private final GeyserEventPlugin plugin;
    private boolean geyserApiAvailable;
    private boolean floodgateApiAvailable;

    public BedrockService(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        detectHooks();
    }

    public final void detectHooks() {
        this.geyserApiAvailable = isClassPresent("org.geysermc.geyser.api.GeyserApi");
        this.floodgateApiAvailable = isClassPresent("org.geysermc.floodgate.api.FloodgateApi");

        if (geyserApiAvailable) {
            plugin.getLogger().info("Подключена поддержка GeyserMC API (Bedrock Edition).");
        }
        if (floodgateApiAvailable) {
            plugin.getLogger().info("Подключена поддержка Floodgate API.");
        }
        if (!geyserApiAvailable && !floodgateApiAvailable) {
            plugin.getLogger().warning("Geyser API / Floodgate API не обнаружены в classpath! Будет использована резервная проверка по префиксу/UUID.");
        }
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Проверяет, играет ли игрок с Bedrock Edition через GeyserMC / Floodgate.
     */
    public boolean isBedrockPlayer(Player player) {
        if (player == null) {
            return false;
        }
        return isBedrockPlayer(player.getUniqueId(), player.getName());
    }

    /**
     * Проверяет по UUID и имени, является ли игрок клиентом Bedrock (GeyserMC).
     */
    public boolean isBedrockPlayer(UUID uuid, String playerName) {
        if (uuid == null) {
            return false;
        }
        // 1. Проверка через GeyserApi
        if (geyserApiAvailable) {
            try {
                GeyserApi api = GeyserApi.api();
                if (api != null && api.isBedrockPlayer(uuid)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. Проверка через FloodgateApi
        if (floodgateApiAvailable) {
            try {
                FloodgateApi api = FloodgateApi.getInstance();
                if (api != null && api.isFloodgatePlayer(uuid)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        // 3. Проверка по XUID Floodgate UUID (старшие 64 бита равны 0 у Floodgate UUID: 00000000-0000-0000-...)
        if (uuid.getMostSignificantBits() == 0L) {
            return true;
        }

        // 4. Резервная проверка по префиксу (например "." для Bedrock игроков)
        String prefix = plugin.getPluginConfig().getFallbackPrefix();
        return prefix != null && !prefix.isEmpty() && playerName != null && playerName.startsWith(prefix);
    }

    /**
     * Проверяет, может ли игрок участвовать в ивенте и использовать команды /event.
     * Учитывает настройку bedrock.strict-only и право обхода для админов.
     */
    public boolean canParticipate(Player player) {
        if (player == null) {
            return false;
        }
        if (isBedrockPlayer(player)) {
            return true;
        }
        if (!plugin.getPluginConfig().isStrictBedrockOnly()) {
            return true;
        }
        return plugin.getPluginConfig().isAllowAdminBypass()
                && (player.hasPermission("geyserevent.bypass.bedrock") || player.hasPermission("geyserevent.admin"));
    }

    /**
     * Подсчитывает количество онлайн игроков с Bedrock Edition.
     */
    public int getOnlineBedrockCount() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isBedrockPlayer(player)) {
                count++;
            }
        }
        return count;
    }
}
