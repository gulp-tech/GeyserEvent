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
import java.util.ArrayList;
import java.util.List;
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

        if (!plugin.getPluginConfig().isAutoInstallResourcePackToGeyser()) {
            packInstallResult = "авто-установка выключена (auto-install-to-geyser: false)";
            return;
        }
        if (!exportedMcpack.exists()) {
            packInstallResult = "не удалось экспортировать ресурспак";
            return;
        }

        List<File> targets = resolveGeyserPacksDirs();
        if (targets.isEmpty()) {
            packInstallResult = "не скопирован: папка packs Bedrock-движка не найдена";
            logPackHelp(exportedMcpack);
            return;
        }

        List<String> copied = new ArrayList<>();
        for (File packsDir : targets) {
            if (!packsDir.exists() && !packsDir.mkdirs()) {
                continue;
            }
            File targetFile = new File(packsDir, "GeyserEvent-Pack.mcpack");
            try {
                Files.copy(exportedMcpack.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                copied.add(targetFile.getPath());
            } catch (IOException ignored) {
            }
        }

        if (copied.isEmpty()) {
            packInstallResult = "не скопирован: нет доступа к папкам packs";
            logPackHelp(exportedMcpack);
            return;
        }

        packInstallResult = "скопирован в " + String.join(", ", copied);
        plugin.getLogger().info("Ресурспак ивентов скопирован: " + String.join(", ", copied));
        plugin.getLogger().info("Bedrock-игроки получат его при подключении. Если пак не применился —");
        plugin.getLogger().info("перезапустите сервер ещё раз (движок читает папку packs при старте).");
    }

    /** Крупная подсказка в консоль, если пак не удалось положить в packs автоматически. */
    private void logPackHelp(File exportedMcpack) {
        plugin.getLogger().warning("==================================================");
        plugin.getLogger().warning(" Ресурспак GeyserEvent-Pack.mcpack НЕ скопирован в папку packs.");
        plugin.getLogger().warning(" Без него Bedrock-игроки не увидят панель ивента в углу экрана");
        plugin.getLogger().warning(" и 3D-рюкзак шахтёра.");
        plugin.getLogger().warning(" Скопируйте вручную этот файл:");
        plugin.getLogger().warning("   " + exportedMcpack.getPath());
        plugin.getLogger().warning(" в папку packs вашего Bedrock-движка, например:");
        plugin.getLogger().warning("   <папка сервера>/bedrock/packs");
        plugin.getLogger().warning(" Либо укажите путь явно в конфиге:");
        plugin.getLogger().warning("   resourcepack.geyser-packs-dir: \"<папка сервера>/bedrock/packs\"");
        plugin.getLogger().warning(" После копирования перезапустите сервер и перезайдите в игру.");
        plugin.getLogger().warning("==================================================");
    }

    /**
     * Находит все папки packs Bedrock-движка, куда нужно положить ресурспак:
     *  1) resourcepack.geyser-packs-dir из конфига (если указан — только он);
     *  2) <папка сервера>/bedrock/packs — встроенный движок Purpurbedrock;
     *  3) -Dpurpurbedrock.dataDir=<...>/packs — если движку задан свой каталог;
     *  4) plugins/Geyser-Spigot/packs и plugins/Geyser/packs — плагин Geyser;
     *  5) <папка сервера>/geyser/packs.
     * Если ничего не найдено — используется <папка сервера>/bedrock/packs.
     */
    private List<File> resolveGeyserPacksDirs() {
        List<File> dirs = new ArrayList<>();

        String configured = plugin.getPluginConfig().getGeyserPacksDir();
        if (configured != null && !configured.isBlank()) {
            File dir = new File(configured);
            if (dir.isDirectory() || (dir.getParentFile() != null && dir.getParentFile().isDirectory())) {
                dirs.add(dir);
            }
            return dirs;
        }

        // <папка сервера> = родитель папки plugins, в которой лежит этот плагин
        File serverRoot = null;
        File pluginsDir = plugin.getDataFolder().getParentFile();
        if (pluginsDir != null) {
            serverRoot = pluginsDir.getParentFile();
        }
        if (serverRoot == null) {
            serverRoot = new File(".");
        }

        addCandidate(dirs, serverRoot, "bedrock" + File.separator + "packs");

        String dataDirProp = System.getProperty("purpurbedrock.dataDir");
        if (dataDirProp != null && !dataDirProp.isBlank()) {
            addCandidate(dirs, new File(dataDirProp), "packs");
        }

        addCandidate(dirs, serverRoot, "plugins" + File.separator + "Geyser-Spigot" + File.separator + "packs");
        addCandidate(dirs, serverRoot, "plugins" + File.separator + "Geyser" + File.separator + "packs");
        addCandidate(dirs, serverRoot, "geyser" + File.separator + "packs");

        if (dirs.isEmpty()) {
            // движок Purpurbedrock: паки кладём в <папка сервера>/bedrock/packs
            dirs.add(new File(serverRoot, "bedrock" + File.separator + "packs"));
        }
        return dirs;
    }

    /** Добавляет кандидата, если папка уже есть или её можно создать. */
    private void addCandidate(List<File> dirs, File base, String relative) {
        if (base == null) {
            return;
        }
        File dir = new File(base, relative);
        if (dir.isDirectory() || (dir.getParentFile() != null && dir.getParentFile().isDirectory())) {
            if (!dirs.contains(dir)) {
                dirs.add(dir);
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

    /** Файл ресурспака в папке packs Bedrock-движка (если он там есть). */
    public File getInstalledPackFile() {
        for (File packsDir : resolveGeyserPacksDirs()) {
            File target = new File(packsDir, "GeyserEvent-Pack.mcpack");
            if (target.exists()) {
                return target;
            }
        }
        return null;
    }

    /** Список проверяемых папок packs (для диагностики). */
    public List<String> getGeyserPacksDirCandidates() {
        List<String> out = new ArrayList<>();
        for (File dir : resolveGeyserPacksDirs()) {
            out.add(dir.getPath());
        }
        return out;
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
