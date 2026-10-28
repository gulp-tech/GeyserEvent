package ru.geyserevent.hud;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.hook.BedrockService;
import ru.geyserevent.event.EventType;
import ru.geyserevent.event.InfernalRiftManager;
import ru.geyserevent.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Менеджер инфо-панели ивента и авто-установщик ресурспака GeyserEvent-Pack.mcpack.
 * НЕ трогает Scoreboard игроков (чтобы не конфликтовать со скорбордом сервера).
 * Вся информация выводится через BossBar, который ресурспаком GeyserEvent-Pack.mcpack
 * переносится в правый верхний угол экрана на Bedrock.
 */
public class TopRightHudManager {

    private final GeyserEventPlugin plugin;
    private BossBar infoBossBar;

    /**
     * Служебный маркер: 4 символа из приватной области Unicode (U+F8C1..U+F8C4) = 12 байт UTF-8.
     * Именно по нему ресурспак GeyserEvent-Pack.mcpack понимает, что action bar — это наша
     * панель ивента: он прячет обычный action bar и рисует текст в правом верхнем углу экрана.
     */
    private static final String HUD_MARKER = "\uf8c1\uf8c2\uf8c3\uf8c4";

    /** До какого момента панель ивента молчит (чтобы не перебивать сообщения способностей босса). */
    private long cornerHudSuppressedUntil = 0L;

    /** Показана ли сейчас панель ивента в углу (нужно, чтобы не слать лишние пакеты). */
    private boolean cornerHudActive = false;

    public TopRightHudManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        exportAndInstallResourcePack();
    }

    /**
     * Экспортирует встроенный ресурспак GeyserEvent-Pack.mcpack
     * и автоматически копирует его в plugins/Geyser-Spigot/packs/.
     */
    public final void exportAndInstallResourcePack() {
        File packDir = new File(plugin.getDataFolder(), "resourcepack");
        if (!packDir.exists()) {
            packDir.mkdirs();
        }

        File exportedMcpack = new File(packDir, "GeyserEvent-Pack.mcpack");
        try {
            plugin.saveResource("resourcepack/GeyserEvent-Pack.mcpack", true);
        } catch (Exception ignored) {
        }

        if (plugin.getPluginConfig().isAutoInstallResourcePackToGeyser() && exportedMcpack.exists()) {
            File pluginsDir = plugin.getDataFolder().getParentFile();
            if (pluginsDir != null) {
                File geyserDir = new File(pluginsDir, "Geyser-Spigot");
                File geyserPacksDir = new File(geyserDir, "packs");
                if (geyserDir.exists()) {
                    if (!geyserPacksDir.exists()) {
                        geyserPacksDir.mkdirs();
                    }
                    File targetFile = new File(geyserPacksDir, "GeyserEvent-Pack.mcpack");
                    try {
                        Files.copy(exportedMcpack.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    /**
     * Обновляет главный информационный BossBar активного ивента (без использования Scoreboard и без дублирования полосок).
     */
    public void updateTopRightHud() {
        clearAllHud();
        InfernalRiftManager manager = plugin.getRiftManager();
        if (manager != null && manager.isActive()) {
            manager.updateEventBossBar();
        }
    }

    /**
     * Просит панель ивента не показываться указанное время (миллисекунды).
     * Вызывается при сообщениях способностей босса, чтобы они не пропадали мгновенно.
     */
    public void suppressCornerHud(long millis) {
        this.cornerHudSuppressedUntil = System.currentTimeMillis() + Math.max(0L, millis);
    }

    /**
     * Раз в секунду отправляет Bedrock-игрокам текст панели ивента (action bar с маркером).
     * Java-игроки видят ту же информацию в BossBar, поэтому им ничего не отправляем.
     * Нагрузка: один пакет на игрока в секунду — это нулевая нагрузка на клиент и сервер.
     */
    public void tickCornerHud() {
        if (!plugin.getPluginConfig().isTopRightHudEnabled()) {
            return;
        }
        InfernalRiftManager manager = plugin.getRiftManager();
        if (manager == null || !manager.isActive() || manager.isDefeated()) {
            clearCornerHud();
            return;
        }
        if (System.currentTimeMillis() < cornerHudSuppressedUntil) {
            return;
        }
        String text = manager.buildEventCornerHudText();
        if (text == null || text.isEmpty()) {
            clearCornerHud();
            return;
        }

        String payload = HUD_MARKER + text;
        BedrockService bedrock = plugin.getBedrockService();
        boolean anySent = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (bedrock == null || !bedrock.isBedrockPlayer(player)) {
                continue;
            }
            player.sendActionBar(payload);
            anySent = true;
        }
        cornerHudActive = anySent;
    }

    /**
     * Убирает панель ивента из правого верхнего угла (ивент закончился или выключен в конфиге).
     */
    public void clearCornerHud() {
        if (!cornerHudActive) {
            return;
        }
        cornerHudActive = false;
        BedrockService bedrock = plugin.getBedrockService();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != null && player.isOnline() && bedrock != null && bedrock.isBedrockPlayer(player)) {
                player.sendActionBar(" ");
            }
        }
    }

    public void removeHudFromPlayer(Player player) {
        if (player != null && infoBossBar != null) {
            infoBossBar.removePlayer(player);
        }
    }

    public void clearAllHud() {
        if (infoBossBar != null) {
            infoBossBar.removeAll();
            infoBossBar = null;
        }
    }
}
