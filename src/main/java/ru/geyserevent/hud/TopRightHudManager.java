package ru.geyserevent.hud;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.event.InfernalRiftManager;
import ru.geyserevent.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер HUD-панели в правом верхнем углу экрана для Bedrock-игроков (GeyserMC)
 * и авто-установщик кастомного ресурспака GeyserEvent-Pack.mcpack.
 *
 * Отображает в правом верхнем углу:
 * - Какой ивент активен
 * - Координаты (X Y Z)
 * - Редкость ивента
 * - Здоровье босса (3500/3500 ❤)
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
     * Экспортирует встроенный Bedrock-ресурспак GeyserEvent-Pack.mcpack
     * в папку плагина и автоматически копирует его в plugins/Geyser-Spigot/packs/ (если папка есть).
     */
    public final void exportAndInstallResourcePack() {
        File packDir = new File(plugin.getDataFolder(), "resourcepack");
        if (!packDir.exists()) {
            packDir.mkdirs();
        }
        File exportedPack = new File(packDir, "GeyserEvent-Pack.mcpack");
        if (!exportedPack.exists()) {
            try {
                plugin.saveResource("resourcepack/GeyserEvent-Pack.mcpack", true);
            } catch (Exception ex) {
                plugin.getLogger().warning("Не удалось распаковать GeyserEvent-Pack.mcpack: " + ex.getMessage());
            }
        }

        if (plugin.getPluginConfig().isAutoInstallResourcePackToGeyser() && exportedPack.exists()) {
            File pluginsDir = plugin.getDataFolder().getParentFile();
            if (pluginsDir != null) {
                File geyserPacksDir = new File(pluginsDir, "Geyser-Spigot/packs");
                if (geyserPacksDir.exists() && geyserPacksDir.isDirectory()) {
                    File targetFile = new File(geyserPacksDir, "GeyserEvent-Pack.mcpack");
                    try {
                        Files.copy(exportedPack.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().info("Кастомный ресурспак GeyserEvent-Pack.mcpack автоматически установлен в Geyser-Spigot/packs/!");
                    } catch (IOException ex) {
                        plugin.getLogger().warning("Не удалось скопировать ресурспак в Geyser-Spigot/packs: " + ex.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Обновляет HUD в правом верхнем углу у всех участников ивента.
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
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : cfg.getWorldName();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getBedrockService().canParticipate(player)) {
                removeHudFromPlayer(player);
                continue;
            }
            applyHudToPlayer(player, sbManager, cfg, manager, worldName, loc, curHp, maxHp);
        }
    }

    @SuppressWarnings("deprecation")
    private void applyHudToPlayer(
            Player player,
            ScoreboardManager sbManager,
            PluginConfig cfg,
            InfernalRiftManager manager,
            String worldName,
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
            player.setScoreboard(customBoard);
            current = customBoard;
        } else {
            obj.setDisplayName(cfg.getTopRightHudTitle());
        }

        // Очищаем старые строки
        for (String entry : new ArrayList<>(current.getEntries())) {
            current.resetScores(entry);
        }

        int myDmg = (int) Math.round(manager.getLeaderboard().getDamage(uuid));
        int myRank = manager.getLeaderboard().getPlayerRank(uuid);
        String rankSuffix = myRank > 0 ? " &7(#" + myRank + ")" : "";

        List<String> lines = List.of(
                ColorUtil.color("&e┏ &8&m──────────────────"),
                ColorUtil.color("&e┠ &fИвент: " + cfg.getTopRightEventDisplayName()),
                ColorUtil.color("&e┠ &fРедкость: " + cfg.getTopRightRarityDisplay()),
                ColorUtil.color("&e┠ &fЗдоровье: &c" + curHp + "&7/&c" + maxHp + " ❤"),
                ColorUtil.color("&e┠ &fМир: &#E0C912" + worldName),
                ColorUtil.color("&e┠ &fКоорд: &#E0C912" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()),
                ColorUtil.color("&e┠ &fВаш урон: &a" + myDmg + " ❤" + rankSuffix),
                ColorUtil.color("&e┗ &fТрекер: &6/event gps")
        );

        int score = lines.size();
        for (String line : lines) {
            // Ограничиваем длину строки для совместимости с экранами телефонов Bedrock
            String safeLine = line.length() > 48 ? line.substring(0, 48) : line;
            obj.getScore(safeLine).setScore(score--);
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
