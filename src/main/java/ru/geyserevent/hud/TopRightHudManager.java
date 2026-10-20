package ru.geyserevent.hud;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.config.PluginConfig;
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
     * Обновляет инфо-бар ивента (без использования Scoreboard).
     */
    public void updateTopRightHud() {
        PluginConfig cfg = plugin.getPluginConfig();
        InfernalRiftManager manager = plugin.getRiftManager();

        if (!cfg.isTopRightHudEnabled() || !manager.isActive() || manager.getRiftCenterLocation() == null) {
            clearAllHud();
            return;
        }

        Location loc = manager.getRiftCenterLocation();
        EventType type = manager.getActiveEventType();

        String title;
        BarColor color;
        if (type == EventType.BEACH_EXCAVATION) {
            title = ColorUtil.color(String.format(
                    "%s &8| &fКисточка через: &#E0C912%dс &8| &fКоординаты: &#E0C912%d %d %d",
                    cfg.getTopRightExcavationDisplayName(),
                    manager.getSecondsUntilNextBrush(),
                    loc.getBlockX(),
                    loc.getBlockY(),
                    loc.getBlockZ()
            ));
            color = BarColor.YELLOW;
        } else {
            title = ColorUtil.color(String.format(
                    "%s &8| &fРедкость: %s &8| &fКоординаты: &#E0C912%d %d %d",
                    cfg.getTopRightEventDisplayName(),
                    cfg.getTopRightRarityDisplay(),
                    loc.getBlockX(),
                    loc.getBlockY(),
                    loc.getBlockZ()
            ));
            color = BarColor.PURPLE;
        }

        if (infoBossBar == null) {
            infoBossBar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
            infoBossBar.setProgress(1.0);
        } else {
            infoBossBar.setColor(color);
            infoBossBar.setTitle(title);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getBedrockService().canParticipate(player)) {
                infoBossBar.removePlayer(player);
                continue;
            }
            if (!infoBossBar.getPlayers().contains(player)) {
                infoBossBar.addPlayer(player);
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
