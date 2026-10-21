package ru.geyserevent;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.geyserevent.boss.BossAbilityController;
import ru.geyserevent.boss.MinionLootManager;
import ru.geyserevent.cauldron.AlchemistSchematicManager;
import ru.geyserevent.command.EventPlayerCommand;
import ru.geyserevent.command.RiftCommand;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.event.InfernalRiftManager;
import ru.geyserevent.excavation.BeachSchematicManager;
import ru.geyserevent.excavation.BrushLootGuiManager;
import ru.geyserevent.excavation.BrushLootManager;
import ru.geyserevent.hook.BedrockService;
import ru.geyserevent.hook.PlayerPointsHook;
import ru.geyserevent.hud.TopRightHudManager;
import ru.geyserevent.listener.RiftEventListener;
import ru.geyserevent.schematic.RiftSchematicManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Главный класс плагина GeyserEvent v1.7.0 — три анархических ивента:
 * 1) «Адский Разлом (Вторжение Босса)»
 * 2) «Пляжные Раскопки (Большая схема 31x31, 2-метровый Житель с кисточками по клику)»
 * 3) «Котёл Алхимика (Мистическое болото 31x31, сбор реагентов и варка в котле)»
 * Специально для Purpur 26.2 с поддержкой игроков Bedrock Edition (GeyserMC).
 */
public final class GeyserEventPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private BedrockService bedrockService;
    private PlayerPointsHook playerPointsHook;
    private RiftSchematicManager schematicManager;
    private BeachSchematicManager beachSchematicManager;
    private AlchemistSchematicManager alchemistSchematicManager;
    private BrushLootManager brushLootManager;
    private BrushLootGuiManager brushLootGuiManager;
    private TopRightHudManager topRightHudManager;
    private MinionLootManager minionLootManager;
    private BossAbilityController abilityController;
    private InfernalRiftManager riftManager;

    @Override
    public void onEnable() {
        // 1. Загрузка конфигурации config.yml
        this.pluginConfig = new PluginConfig(this);

        // 2. Инициализация интеграций GeyserMC/Floodgate и PlayerPoints
        this.bedrockService = new BedrockService(this);
        this.playerPointsHook = new PlayerPointsHook(this);

        // 3. Менеджеры схем («Адский Разлом», «Пляжные Раскопки» 31x31, «Котёл Алхимика» 31x31), лута, GUI-меню и HUD
        this.schematicManager = new RiftSchematicManager(this);
        this.beachSchematicManager = new BeachSchematicManager(this);
        this.alchemistSchematicManager = new AlchemistSchematicManager(this);
        this.brushLootManager = new BrushLootManager(this);
        this.brushLootGuiManager = new BrushLootGuiManager(this);
        this.topRightHudManager = new TopRightHudManager(this);
        this.minionLootManager = new MinionLootManager(this);

        // 4. Контроллер способностей босса и главный менеджер ивентов
        this.abilityController = new BossAbilityController(this);
        this.riftManager = new InfernalRiftManager(this);

        // 5. Регистрация слушателей событий
        getServer().getPluginManager().registerEvents(new RiftEventListener(this), this);

        // 6. Регистрация команд для игроков (/event delay, /event gps)
        PluginCommand eventCmd = getCommand("event");
        if (eventCmd != null) {
            EventPlayerCommand playerExecutor = new EventPlayerCommand(this);
            eventCmd.setExecutor(playerExecutor);
            eventCmd.setTabCompleter(playerExecutor);
        }

        // 7. Регистрация админ-команд (/rift)
        PluginCommand riftCmd = getCommand("rift");
        if (riftCmd != null) {
            RiftCommand adminExecutor = new RiftCommand(this);
            riftCmd.setExecutor(adminExecutor);
            riftCmd.setTabCompleter(adminExecutor);
        }

        // 8. Экспорт готового меню для DeluxeMenus (deluxemenus/events.yml -> команда /events)
        exportDeluxeMenusConfig();

        getLogger().info("==================================================");
        getLogger().info(" GeyserEvent v1.7.2 успешно запущен! (Purpur 26.2)");
        getLogger().info(" Ивенты: Адский Разлом, Пляжные Раскопки, Котёл Алхимика");
        getLogger().info(" Меню DeluxeMenus (/events): plugins/GeyserEvent/deluxemenus/events.yml");
        getLogger().info(" Меню настройки лута: /rift menu");
        getLogger().info(" Команды игроков: /event delay, /event gps");
        getLogger().info("==================================================");
    }

    private void exportDeluxeMenusConfig() {
        try {
            saveResource("deluxemenus/events.yml", true);
            File exported = new File(getDataFolder(), "deluxemenus/events.yml");
            File pluginsDir = getDataFolder().getParentFile();
            if (pluginsDir != null && exported.exists()) {
                File dmGuiDir = new File(new File(pluginsDir, "DeluxeMenus"), "gui_menus");
                if (dmGuiDir.exists()) {
                    File target = new File(dmGuiDir, "events.yml");
                    if (!target.exists()) {
                        Files.copy(exported.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDisable() {
        if (this.riftManager != null && this.riftManager.isActive()) {
            getLogger().info("Завершение активного ивента и откат схемы...");
            this.riftManager.stopEvent(false);
        } else {
            if (this.schematicManager != null) {
                this.schematicManager.restoreSchematic();
            }
            if (this.beachSchematicManager != null) {
                this.beachSchematicManager.restoreActiveSchematic();
            }
            if (this.alchemistSchematicManager != null) {
                this.alchemistSchematicManager.restoreActiveSchematic();
            }
            if (this.topRightHudManager != null) {
                this.topRightHudManager.clearAllHud();
            }
        }
        getLogger().info("GeyserEvent выключен.");
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public BedrockService getBedrockService() {
        return bedrockService;
    }

    public PlayerPointsHook getPlayerPointsHook() {
        return playerPointsHook;
    }

    public RiftSchematicManager getSchematicManager() {
        return schematicManager;
    }

    public BeachSchematicManager getBeachSchematicManager() {
        return beachSchematicManager;
    }

    public AlchemistSchematicManager getAlchemistSchematicManager() {
        return alchemistSchematicManager;
    }

    public BrushLootManager getBrushLootManager() {
        return brushLootManager;
    }

    public BrushLootGuiManager getBrushLootGuiManager() {
        return brushLootGuiManager;
    }

    public TopRightHudManager getTopRightHudManager() {
        return topRightHudManager;
    }

    public MinionLootManager getMinionLootManager() {
        return minionLootManager;
    }

    public BossAbilityController getAbilityController() {
        return abilityController;
    }

    public InfernalRiftManager getRiftManager() {
        return riftManager;
    }
}
