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

    /** Результат последней попытки скопировать ресурспак в Geyser (для /rift hudinfo). */
    private String packInstallResult = "ещё не выполнялась";

    public TopRightHudManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        exportAndInstallResourcePack();
    }

    /**
     * Экспортирует встроенный ресурспак GeyserEvent-Pack.mcpack в plugins/GeyserEvent/resourcepack/
     * и копирует его в папку packs вашего Geyser, чтобы Bedrock-игроки получили пак автоматически.
     *
     * Папка packs определяется так:
     *  1) resourcepack.geyser-packs-dir в конфиге (если указан) — так находят прокси-Geyрер
     *     (BungeeCord / Velocity / Standalone), у которого папка packs лежит вне сервера;
     *  2) иначе plugins/Geyser-Spigot/packs (плагин Geyser-Spigot прямо на сервере).
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
        packInstallResult = "экспортирован: " + exportedMcpack.getPath();

        if (!plugin.getPluginConfig().isAutoInstallResourcePackToGeyser()) {
            packInstallResult = "авто-установка выключена (auto-install-to-geyser: false)";
            return;
        }
        if (!exportedMcpack.exists()) {
            packInstallResult = "не удалось экспортировать ресурспак";
            return;
        }

        File packsDir = resolveGeyserPacksDir();
        if (packsDir == null) {
            packInstallResult = "не скопирован: папка packs Geyser не найдена";
            plugin.getLogger().warning("==================================================");
            plugin.getLogger().warning(" Ресурспак GeyserEvent-Pack.mcpack НЕ скопирован в Geyser.");
            plugin.getLogger().warning(" Без него Bedrock-игроки не увидят панель ивента в углу экрана");
            plugin.getLogger().warning(" и 3D-рюкзак шахтёра.");
            plugin.getLogger().warning(" Скопируйте вручную этот файл:");
            plugin.getLogger().warning("   " + exportedMcpack.getPath());
            plugin.getLogger().warning(" в папку packs вашего Geyser (прокси), например:");
            plugin.getLogger().warning("   /путь/к/geyser/packs/");
            plugin.getLogger().warning(" Либо укажите путь в конфиге:");
            plugin.getLogger().warning("   resourcepack.geyser-packs-dir: \"/путь/к/geyser/packs\"");
            plugin.getLogger().warning("==================================================");
            return;
        }

        if (!packsDir.exists() && !packsDir.mkdirs()) {
            packInstallResult = "не скопирован: не удалось создать " + packsDir.getPath();
            return;
        }
        File targetFile = new File(packsDir, "GeyserEvent-Pack.mcpack");
        try {
            Files.copy(exportedMcpack.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            packInstallResult = "скопирован в " + targetFile.getPath();
            plugin.getLogger().info("Ресурспак ивентов скопирован в " + targetFile.getPath()
                    + " (Bedrock-игроки получат его автоматически).");
        } catch (IOException e) {
            packInstallResult = "не скопирован: " + e.getMessage();
        }
    }

    /**
     * Находит папку packs Geyser: сначала из конфига, потом plugins/Geyser-Spigot/packs.
     * Возвращает null, если папку найти/создать не удалось.
     */
    private File resolveGeyserPacksDir() {
        String configured = plugin.getPluginConfig().getGeyserPacksDir();
        if (configured != null && !configured.isBlank()) {
            File dir = new File(configured);
            if (dir.isDirectory() || (dir.getParentFile() != null && dir.getParentFile().isDirectory())) {
                return dir;
            }
            return null;
        }
        File pluginsDir = plugin.getDataFolder().getParentFile();
        if (pluginsDir == null) {
            return null;
        }
        File geyserDir = new File(pluginsDir, "Geyser-Spigot");
        if (!geyserDir.isDirectory()) {
            return null;
        }
        return new File(geyserDir, "packs");
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

    /** Файл ресурспака в папке packs Geyser (если он там есть). */
    public File getInstalledPackFile() {
        File packsDir = resolveGeyserPacksDir();
        if (packsDir == null) {
            return null;
        }
        File target = new File(packsDir, "GeyserEvent-Pack.mcpack");
        return target.exists() ? target : null;
    }

    /** Результат копирования ресурспака в Geyser (для диагностики). */
    public String getPackInstallResult() {
        return packInstallResult;
    }

    /** Экспортированный ресурспак (отсюда его можно скопировать в packs Geyser вручную). */
    public File getExportedPackFile() {
        return new File(new File(plugin.getDataFolder(), "resourcepack"), "GeyserEvent-Pack.mcpack");
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
