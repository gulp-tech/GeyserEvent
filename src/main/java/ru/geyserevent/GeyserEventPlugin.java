package ru.geyserevent;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.geyserevent.bedrock.BedrockFormMenu;
import ru.geyserevent.boss.BossAbilityController;
import ru.geyserevent.command.RiftCommand;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.event.InfernalRiftManager;
import ru.geyserevent.hook.BedrockService;
import ru.geyserevent.hook.PlayerPointsHook;
import ru.geyserevent.listener.RiftEventListener;
import ru.geyserevent.schematic.RiftSchematicManager;

/**
 * Главный класс плагина GeyserEvent — ивент «Адский Разлом (Вторжение Босса)»
 * для Purpur 26.2 с поддержкой только игроков Bedrock Edition (GeyserMC)
 * и выдачей донат-валюты (осколков) через PlayerPoints.
 */
public final class GeyserEventPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private BedrockService bedrockService;
    private PlayerPointsHook playerPointsHook;
    private RiftSchematicManager schematicManager;
    private BossAbilityController abilityController;
    private InfernalRiftManager riftManager;
    private BedrockFormMenu formMenu;

    @Override
    public void onEnable() {
        // 1. Загрузка конфигурации config.yml
        this.pluginConfig = new PluginConfig(this);

        // 2. Инициализация интеграций GeyserMC/Floodgate и PlayerPoints
        this.bedrockService = new BedrockService(this);
        this.playerPointsHook = new PlayerPointsHook(this);

        // 3. Менеджер схематики Адского Разлома (с защитой портала от телепортации)
        this.schematicManager = new RiftSchematicManager(this);

        // 4. Контроллер способностей босса и менеджер ивента
        this.abilityController = new BossAbilityController(this);
        this.riftManager = new InfernalRiftManager(this);
        this.formMenu = new BedrockFormMenu(this);

        // 5. Регистрация слушателей событий
        getServer().getPluginManager().registerEvents(new RiftEventListener(this), this);

        // 6. Регистрация команд (/rift, /event, /razlom, /geyserevent)
        PluginCommand cmd = getCommand("rift");
        if (cmd != null) {
            RiftCommand executor = new RiftCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("==================================================");
        getLogger().info(" GeyserEvent v" + getDescription().getVersion() + " успешно запущен! (Purpur 26.2)");
        getLogger().info(" Ивент: Адский Разлом (Большой Брутальный Пиглин 2м)");
        getLogger().info(" Мир по умолчанию: " + pluginConfig.getWorldName() + " (изменяется в config.yml)");
        getLogger().info(" Босс: " + (int) pluginConfig.getBossHealth() + " HP | Урон: " + (int) pluginConfig.getBossDamage() + " | Топор: Незеритовый");
        getLogger().info(" Награды (PlayerPoints): 1м=" + pluginConfig.getRewardFirstPlace()
                + ", 2м=" + pluginConfig.getRewardSecondPlace()
                + ", 3м=" + pluginConfig.getRewardThirdPlace() + " " + pluginConfig.getCurrencyName());
        getLogger().info("==================================================");
    }

    @Override
    public void onDisable() {
        if (this.riftManager != null && this.riftManager.isActive()) {
            getLogger().info("Завершение активного ивента «Адский Разлом» и откат схематики...");
            this.riftManager.stopEvent(false);
        } else if (this.schematicManager != null) {
            this.schematicManager.restoreSchematic();
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

    public BossAbilityController getAbilityController() {
        return abilityController;
    }

    public InfernalRiftManager getRiftManager() {
        return riftManager;
    }

    public BedrockFormMenu getFormMenu() {
        return formMenu;
    }
}
