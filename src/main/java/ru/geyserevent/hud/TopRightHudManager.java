package ru.geyserevent.hud;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.hook.BedrockService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Авто-установщик ресурспака GeyserEvent-Pack.mcpack в папку packs Bedrock-движка.
 * НЕ трогает Scoreboard игроков (чтобы не конфликтовать со скорбордом сервера).
 *
 * <p>ВАЖНО (v1.9.29): панель ивента в углу экрана УБРАНА по просьбе пользователя:
 * она показывала моланг-выражения текстом вместо данных, пока не найдена
 * правильная схема. На экране остаётся только BossBar вверху
 * (GPS-локатор), а action bar свободен — он тоже будет показывать только GPS.
 * Пак больше не содержит ui/hud_screen.json и не меняет ничего в HUD клиента.
 * Генератор панели сохранён: .packtools/make_hud.py — когда будем делать заново.</p>
 */
public class TopRightHudManager {

    private final GeyserEventPlugin plugin;

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

        logExportedPackInfo(exportedMcpack);

        packInstallResult = "скопирован в " + String.join(", ", copied);
        plugin.getLogger().info("Ресурспак ивентов скопирован: " + String.join(", ", copied));
        plugin.getLogger().info("Bedrock-игроки получат его при подключении. Если пак не применился —");
        plugin.getLogger().info("перезапустите сервер ещё раз (движок читает папку packs при старте).");

        // Дополнительно регистрируем пак прямо в движке Bedrock через Geyser API:
        // папку packs движок читает один раз при старте, а так пак попадает в список
        // отправляемых клиентам в любом случае.
        GeyserPackBridge.subscribe(plugin, exportedMcpack);
    }

    /**
     * Пишет в консоль версию и UUID пака, который получат клиент.
     * Bedrock кэширует паки по UUID и НЕ перекачивает их, пока UUID тот же,
     * поэтому эта строка — главная проверка, что игроки получили новый пак.
     */
    private void logExportedPackInfo(File packFile) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(packFile)) {
            java.util.zip.ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) {
                return;
            }
            String json = new String(zip.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String version = json.contains("\"version\"") ? json.split("\"version\"")[1].replaceAll("[^0-9,]", "").replace(",", ".") : "?";
            String uuid = json.contains("\"uuid\"") ? json.split("\"uuid\"")[1].replaceAll("[^0-9a-fA-F-]", "") : "?";
            plugin.getLogger().info("Пак для клиентов: версия " + version + ", UUID " + uuid);
            plugin.getLogger().info("Если панель ивента у игрока старая — пусть перезайдёт в игру (клиент кэширует пак по UUID).");
        } catch (Exception ignored) {
        }
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

        // Папка, которую движок Bedrock назвал сам (самый надёжный вариант)
        File apiDir = GeyserPackBridge.geyserPacksDirectory();
        if (apiDir != null && !dirs.contains(apiDir)) {
            dirs.add(apiDir);
            plugin.getLogger().info("Движок Bedrock сообщил свою папку packs: " + apiDir.getPath());
        }

        addCandidate(dirs, serverRoot, "bedrock" + File.separator + "packs");

        String dataDirProp = System.getProperty("purpurbedrock.dataDir");
        if (dataDirProp != null && !dataDirProp.isBlank()) {
            addCandidate(dirs, new File(dataDirProp), "packs");
        }

        addCandidate(dirs, serverRoot, "plugins" + File.separator + "Geyser-Spigot" + File.separator + "packs");
        addCandidate(dirs, serverRoot, "plugins" + File.separator + "Geyser" + File.separator + "packs");
        addCandidate(dirs, serverRoot, "geyser" + File.separator + "packs");
        addCandidate(dirs, serverRoot, "packs");
        addCandidate(dirs, serverRoot, "bedrock" + File.separator + "pack");

        // Запасной вариант: обходим папку сервера и plugins и ищем любые папки packs
        // (движки разных сборок кладут их в разные места).
        scanForPacksFolders(dirs, serverRoot);
        scanForPacksFolders(dirs, new File(serverRoot, "plugins"));

        if (dirs.isEmpty()) {
            // движок Purpurbedrock: паки кладём в <папка сервера>/bedrock/packs
            dirs.add(new File(serverRoot, "bedrock" + File.separator + "packs"));
        }
        return dirs;
    }

    /**
     * Ищет папки packs внутри указанной папки: любая подпапка с именем packs,
     * а также <подпапка>/packs у подпапок bedrock/geyser (встроенные движки).
     */
    private void scanForPacksFolders(List<File> dirs, File base) {
        if (base == null || !base.isDirectory()) {
            return;
        }
        File[] children = base.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (!child.isDirectory()) {
                continue;
            }
            String name = child.getName().toLowerCase(Locale.ROOT);
            if (name.equals("packs") || name.equals("resource_packs") || name.equals("resourcepacks")) {
                if (!dirs.contains(child)) {
                    dirs.add(child);
                }
            } else if (name.equals("bedrock") || name.equals("geyser") || name.equals("floodgate")) {
                File inner = new File(child, "packs");
                if (inner.isDirectory() && !dirs.contains(inner)) {
                    dirs.add(inner);
                }
            }
        }
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

    /** BossBar ивента убран (v1.9.23) — оставлено для совместимости вызовов. */
    public void removeHudFromPlayer(Player player) {
        // ничего не делаем: инфо-полоски больше нет
    }

    /**
     * Панели ивента больше нет (v1.9.29, по просьбе пользователя) —
     * информацию ивента показывает только GPS-локатор в виде BossBar вверху экрана.
     * Метод оставлен для совместимости вызовов.
     */
    public void clearAllHud() {
        // ничего не делаем: панелей нет, action bar свободен для GPS
    }
}
