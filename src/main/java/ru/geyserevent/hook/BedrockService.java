package ru.geyserevent.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;
import ru.geyserevent.GeyserEventPlugin;

import java.util.Locale;
import java.util.UUID;

/**
 * Сервис проверки игроков Bedrock Edition (телефоны, планшеты, Bedrock ПК, консоли через GeyserMC / Floodgate).
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
            plugin.getLogger().info("Подключена поддержка GeyserMC API (Bedrock Mobile & PC).");
        }
        if (floodgateApiAvailable) {
            plugin.getLogger().info("Подключена поддержка Floodgate API.");
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
     * Проверяет, играет ли игрок с Bedrock Edition (телефон или Bedrock ПК).
     */
    public boolean isBedrockPlayer(Player player) {
        if (player == null) {
            return false;
        }
        if (isBedrockPlayer(player.getUniqueId(), player.getName())) {
            return true;
        }
        try {
            String brand = player.getClientBrandName();
            if (brand != null) {
                String lower = brand.toLowerCase(Locale.ROOT);
                if (lower.contains("geyser") || lower.contains("bedrock") || lower.contains("floodgate")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
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
        if (uuid.getMostSignificantBits() == 0L || uuid.toString().startsWith("00000000-0000-0000-")) {
            return true;
        }

        // 4. Резервная проверка по префиксу (например "." или "*" для Bedrock игроков)
        String prefix = plugin.getPluginConfig().getFallbackPrefix();
        return prefix != null && !prefix.isEmpty() && playerName != null && playerName.startsWith(prefix);
    }

    /**
     * Проверяет, может ли игрок участвовать в ивенте, брать кисточки и использовать команды /event.
     * Все игроки (и с телефонов, и с Bedrock ПК) могут полноценно брать кисточки и раскапывать песок.
     */
    public boolean canParticipate(Player player) {
        if (player == null) {
            return false;
        }
        if (!plugin.getPluginConfig().isStrictBedrockOnly()) {
            return true;
        }
        if (isBedrockPlayer(player)) {
            return true;
        }
        return plugin.getPluginConfig().isAllowAdminBypass()
                && (player.hasPermission("geyserevent.bypass.bedrock") || player.hasPermission("geyserevent.admin") || player.isOp());
    }

    /**
     * Подсчитывает количество онлайн игроков для авто-старта.
     */
    public int getOnlineBedrockCount() {
        if (!plugin.getPluginConfig().isStrictBedrockOnly()) {
            return Bukkit.getOnlinePlayers().size();
        }
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canParticipate(player)) {
                count++;
            }
        }
        return count;
    }
}
