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
     * Служебный маркер: РОВНО 12 ASCII-символов.
     * Ресурспак GeyserEvent-Pack.mcpack берёт первые 12 символов action bar ('%.12s')
     * и сравнивает с этим маркером: если совпало — прячет обычный action bar и рисует
     * текст ивента в правом верхнем углу экрана.
     * Маркер SPECIAL намеренно ASCII: у разных клиентов '%.12s' считается либо по символам,
     * либо по байтам, а 12 ASCII-символов = 12 байт = 12 символов, поэтому работает всегда.
     */
    private static final String HUD_MARKER = "------------";

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

        String payload = HUD_MARKER + " " + text;
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

    /**
     * Тест панели: 15 секунд показывает панель в правом верхнем углу экрана,
     * даже если сейчас не идёт ни один ивент. Нужно, чтобы проверить ресурспак у себя в клиенте.
     */
    public void startCornerHudTest(Player player) {
        if (player == null) {
            return;
        }
        // на время теста обычная панель ивента молчит, чтобы не перебивать тест
        suppressCornerHud(15_000L);
        String demo = ColorUtil.color("&#FFD614&lПРОВЕРКА ПАНЕЛИ &8· &fработает ресурспак &8· &e0 0 0");
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticksLeft = 300;

            @Override
            public void run() {
                if (ticksLeft <= 0 || !player.isOnline()) {
                    cancel();
                    return;
                }
                player.sendActionBar(HUD_MARKER + demo);
                ticksLeft -= 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    /** Путь до установленного ресурспака (для диагностики). */
    public File getInstalledPackFile() {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        if (pluginsDir == null) {
            return null;
        }
        return new File(new File(pluginsDir, "Geyser-Spigot" + File.separator + "packs"), "GeyserEvent-Pack.mcpack");
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
