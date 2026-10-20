package ru.geyserevent.hud;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.event.InfernalRiftManager;
import ru.geyserevent.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер инфо-панели в правом верхнем углу экрана и установщик ресурспака.
 * Когда появляется ивент, отображает справа вверху:
 * - Какой ивент
 * - Координаты
 * - Редкость
 * - Здоровье босса
 */
public class TopRightHudManager {

    private static final String OBJECTIVE_NAME = "geyserevent_hud";

    private final GeyserEventPlugin plugin;
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();

    public TopRightHudManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        exportAndInstallResourcePack();
    }

    /**
     * Экспортирует встроенные ресурспаки (GeyserEvent-Pack.mcpack и GeyserEvent-ResourcePack.zip)
     * и автоматически копирует .mcpack в plugins/Geyser-Spigot/packs/.
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
     * Обновляет панель информации об ивенте в правом верхнем углу экрана.
     */
    public void updateTopRightHud() {
        PluginConfig cfg = plugin.getPluginConfig();
        InfernalRiftManager manager = plugin.getRiftManager();

        if (!cfg.isTopRightHudEnabled() || !manager.isActive() || manager.getRiftCenterLocation() == null) {
            clearAllHud();
            return;
        }

        ScoreboardManager sbManager = Bukkit.getScoreboardManager();
        if (sbManager == null) {
            return;
        }

        Location loc = manager.getRiftCenterLocation();
        int curHp = (int) Math.ceil(manager.getBossCurrentHealth());
        int maxHp = (int) Math.ceil(manager.getBossMaxHealth());

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getBedrockService().canParticipate(player)) {
                removeHudFromPlayer(player);
                continue;
            }
            applyHudToPlayer(player, sbManager, cfg, loc, curHp, maxHp);
        }
    }

    @SuppressWarnings("deprecation")
    private void applyHudToPlayer(
            Player player,
            ScoreboardManager sbManager,
            PluginConfig cfg,
            Location loc,
            int curHp,
            int maxHp
    ) {
        UUID uuid = player.getUniqueId();
        Scoreboard current = player.getScoreboard();
        Objective obj = current.getObjective(OBJECTIVE_NAME);

        if (obj == null) {
            previousScoreboards.putIfAbsent(uuid, current);
            Scoreboard customBoard = sbManager.getNewScoreboard();
            obj = customBoard.registerNewObjective(OBJECTIVE_NAME, "dummy", cfg.getTopRightHudTitle());
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            hideObjectiveNumbers(obj);
            player.setScoreboard(customBoard);
            current = customBoard;
        } else {
            obj.setDisplayName(cfg.getTopRightHudTitle());
            hideObjectiveNumbers(obj);
        }

        for (String entry : new ArrayList<>(current.getEntries())) {
            current.resetScores(entry);
        }

        List<String> lines = List.of(
                ColorUtil.color("&e┏ &8&m──────────────────"),
                ColorUtil.color("&e┠ &fИвент: " + cfg.getTopRightEventDisplayName()),
                ColorUtil.color("&e┠ &fКоординаты: &#E0C912" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()),
                ColorUtil.color("&e┠ &fРедкость: " + cfg.getTopRightRarityDisplay()),
                ColorUtil.color("&e┠ &fЗдоровье: &c" + curHp + "&7/&c" + maxHp + " ❤"),
                ColorUtil.color("&e┗ &8&m──────────────────")
        );

        int scoreVal = lines.size();
        for (String line : lines) {
            Score score = obj.getScore(line);
            score.setScore(scoreVal--);
            hideScoreNumber(score);
        }
    }

    /**
     * Скрывает красные цифры справа у Objective через Paper/Purpur NumberFormat.blank().
     */
    private void hideObjectiveNumbers(Objective obj) {
        try {
            Class<?> nfClass = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Method blankMethod = nfClass.getMethod("blank");
            Object blankFormat = blankMethod.invoke(null);
            Method setFormat = obj.getClass().getMethod("numberFormat", nfClass);
            setFormat.invoke(obj, blankFormat);
        } catch (Throwable ignored) {
        }
    }

    private void hideScoreNumber(Score score) {
        try {
            Class<?> nfClass = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Method blankMethod = nfClass.getMethod("blank");
            Object blankFormat = blankMethod.invoke(null);
            Method setFormat = score.getClass().getMethod("numberFormat", nfClass);
            setFormat.invoke(score, blankFormat);
        } catch (Throwable ignored) {
        }
    }

    public void removeHudFromPlayer(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Scoreboard prev = previousScoreboards.remove(uuid);
        if (prev != null && player.isOnline()) {
            player.setScoreboard(prev);
        } else if (player.isOnline()) {
            Scoreboard cur = player.getScoreboard();
            Objective obj = cur.getObjective(OBJECTIVE_NAME);
            if (obj != null) {
                obj.unregister();
            }
        }
    }

    public void clearAllHud() {
        for (UUID uuid : new ArrayList<>(previousScoreboards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeHudFromPlayer(player);
            }
        }
        previousScoreboards.clear();
    }
}
