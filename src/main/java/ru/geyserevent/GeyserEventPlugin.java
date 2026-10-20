package ru.geyserevent;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.geyserevent.boss.BossAbilityController;
import ru.geyserevent.boss.MinionLootManager;
import ru.geyserevent.command.EventPlayerCommand;
import ru.geyserevent.command.RiftCommand;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.event.InfernalRiftManager;
import ru.geyserevent.hook.BedrockService;
import ru.geyserevent.hook.PlayerPointsHook;
import ru.geyserevent.hud.TopRightHudManager;
import ru.geyserevent.listener.RiftEventListener;
import ru.geyserevent.schematic.RiftSchematicManager;

/**
 * Главный класс плагина GeyserEvent v1.1.0 — ивент «Адский Разлом (Вторжение Босса)»
 * для Purpur 26.2 с поддержкой только игроков Bedrock Edition (GeyserMC),
 * кастомным ресурспаком HUD в правом верхнем углу, GPS-трекером (/event gps),
 * лутом с приспешников и выдачей донат-валюты (осколков) через PlayerPoints.
 */
public final class GeyserEventPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private BedrockService bedrockService;
    private PlayerPointsHook playerPointsHook;
    private RiftSchematicManager schematicManager;
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

        // 3. Менеджер схематики Адского Разлома и менеджер HUD/Ресурспака
        this.schematicManager = new RiftSchematicManager(this);
        this.topRightHudManager = new TopRightHudManager(this);
        this.minionLootManager = new MinionLootManager(this);

        // 4. Контроллер способностей босса и менеджер ивента
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

        // 7. Регистрация админ-команд (/rift start, stop, ability, setworld, saveschem, reload)
        PluginCommand riftCmd = getCommand("rift");
        if (riftCmd != null) {
            RiftCommand adminExecutor = new RiftCommand(this);
            riftCmd.setExecutor(adminExecutor);
            riftCmd.setTabCompleter(adminExecutor);
        }

        getLogger().info("==================================================");
        getLogger().info(" GeyserEvent v1.1.0 успешно запущен! (Purpur 26.2)");
        getLogger().info(" Ивент: Адский Разлом (Большой Брутальный Пиглин 2м)");
        getLogger().info(" Команды игроков: /event delay, /event gps");
        getLogger().info(" Админ-команды: /rift <start|start-here|stop|ability|setworld|saveschem|reload>");
        getLogger().info(" Ресурспак HUD: GeyserEvent-Pack.mcpack (Правый верхний угол)");
        getLogger().info("==================================================");
    }

    @Override
    public void onDisable() {
        if (this.riftManager != null && this.riftManager.isActive()) {
            getLogger().info("Завершение активного ивента «Адский Разлом» и откат схематики...");
            this.riftManager.stopEvent(false);
        } else {
            if (this.schematicManager != null) {
                this.schematicManager.restoreSchematic();
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
