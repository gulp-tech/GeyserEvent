package ru.geyserevent;

import org.bukkit.Bukkit;
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
import ru.geyserevent.hook.WorldGuardHook;
import ru.geyserevent.hud.TopRightHudManager;
import ru.geyserevent.summon.SummonAltarManager;
import ru.geyserevent.listener.RiftEventListener;
import ru.geyserevent.mine.AncientMineManager;
import ru.geyserevent.mine.MinerBackpackManager;
import ru.geyserevent.schematic.RiftSchematicManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Главный класс плагина GeyserEvent v1.8.1 — четыре анархических ивента:
 * 1) «Адский Разлом (Вторжение Босса)»
 * 2) «Пляжные Раскопки (Большая схема 31x31, 2-метровый Житель с кисточками по клику)»
 * 3) «Котёл Алхимика (Мистическое болото 31x31, сбор реагентов и варка в котле)»
 * 4) Постоянный ивент «Шахта Древних (Жила Жеод, 45x45, 3D-Рюкзак на спине)» на координатах 0 ~ 0
 * Специально для Purpur 26.2 с поддержкой игроков Bedrock Edition (GeyserMC).
 */
public final class GeyserEventPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private BedrockService bedrockService;
    private PlayerPointsHook playerPointsHook;
    private WorldGuardHook worldGuardHook;
    private RiftSchematicManager schematicManager;
    private BeachSchematicManager beachSchematicManager;
    private AlchemistSchematicManager alchemistSchematicManager;
    private AncientMineManager ancientMineManager;
    private MinerBackpackManager minerBackpackManager;
    private BrushLootManager brushLootManager;
    private BrushLootGuiManager brushLootGuiManager;
    private TopRightHudManager topRightHudManager;
    private MinionLootManager minionLootManager;
    private BossAbilityController abilityController;
    private SummonAltarManager summonAltarManager;
    private InfernalRiftManager riftManager;

    @Override
    public void onLoad() {
        // Перезаписываем текстурпак в папке Geyser ещё на этапе onLoad (до того, как Geyser-Spigot просканирует packs/)
        exportResourcePackAndOverwriteGeyser();
    }

    @Override
    public void onEnable() {
        // 1. Загрузка конфигурации config.yml
        this.pluginConfig = new PluginConfig(this);

        // 2. Инициализация интеграций GeyserMC/Floodgate, PlayerPoints и WorldGuard
        this.bedrockService = new BedrockService(this);
        this.playerPointsHook = new PlayerPointsHook(this);
        this.worldGuardHook = new WorldGuardHook();
        if (this.worldGuardHook.isAvailable()) {
            getLogger().info("WorldGuard найден: регионы WorldGuard не будут мешать ломать руды «Шахты Древних».");
        }

        // 3. Менеджеры схем, лута, 3D-рюкзака, GUI-меню и HUD
        this.schematicManager = new RiftSchematicManager(this);
        this.beachSchematicManager = new BeachSchematicManager(this);
        this.alchemistSchematicManager = new AlchemistSchematicManager(this);
        this.brushLootManager = new BrushLootManager(this);
        this.brushLootGuiManager = new BrushLootGuiManager(this);
        this.minerBackpackManager = new MinerBackpackManager(this);
        this.ancientMineManager = new AncientMineManager(this);
        this.topRightHudManager = new TopRightHudManager(this);
        this.minionLootManager = new MinionLootManager(this);

        // 4. Контроллер способностей босса и главный менеджер ивентов
        this.abilityController = new BossAbilityController(this);
        this.riftManager = new InfernalRiftManager(this);

        // 4a. Призывные ивенты: алтари призыва босса (спецблоки с кастомными текстурами)
        this.summonAltarManager = new SummonAltarManager(this);

        // 5. Регистрация слушателей событий
        getServer().getPluginManager().registerEvents(new RiftEventListener(this), this);

        // 6. Регистрация команд для игроков (/event delay, /event gps, /event mine, /event backpack)
        PluginCommand eventCmd = getCommand("event");
        if (eventCmd != null) {
            EventPlayerCommand playerExecutor = new EventPlayerCommand(this);
            eventCmd.setExecutor(playerExecutor);
            eventCmd.setTabCompleter(playerExecutor);
        }

        // 7. Регистрация админ-команд (/rift, /cauldronmenu, /minemenu)
        RiftCommand adminExecutor = new RiftCommand(this);
        PluginCommand riftCmd = getCommand("rift");
        if (riftCmd != null) {
            riftCmd.setExecutor(adminExecutor);
            riftCmd.setTabCompleter(adminExecutor);
        }
        PluginCommand cauldronMenuCmd = getCommand("cauldronmenu");
        if (cauldronMenuCmd != null) {
            cauldronMenuCmd.setExecutor(adminExecutor);
            cauldronMenuCmd.setTabCompleter(adminExecutor);
        }
        PluginCommand mineMenuCmd = getCommand("minemenu");
        if (mineMenuCmd != null) {
            mineMenuCmd.setExecutor(adminExecutor);
            mineMenuCmd.setTabCompleter(adminExecutor);
        }

        // 8. Экспорт и автоматическая перезапись меню DeluxeMenus (events.yml) и текстурпака (GeyserEvent-Pack.mcpack)
        exportDeluxeMenusConfig();
        exportResourcePackAndOverwriteGeyser();

        // 9. Автоматический запуск постоянного ивента «Шахта Древних (Жила Жеод)» 45x45 на 0 ~ 0
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (this.ancientMineManager != null) {
                this.ancientMineManager.initializePermanentMine();
            }
        }, 20L);

        getLogger().info("==================================================");
        getLogger().info(" GeyserEvent v1.9.29 успешно запущен! (Purpur 26.2)");
        getLogger().info(" Временные ивенты: Адский Разлом, Пляжные Раскопки, Котёл Алхимика");
        getLogger().info(" Постоянный ивент: Шахта Древних 45x45 (v7) + 3D-Рюкзак на 0 ~ 0");
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
                    Files.copy(exported.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void exportResourcePackAndOverwriteGeyser() {
        try {
            saveResource("resourcepack/GeyserEvent-Pack.mcpack", true);
            File exported = new File(getDataFolder(), "resourcepack/GeyserEvent-Pack.mcpack");
            if (exported.exists()) {
                File rootCopy = new File(getDataFolder(), "GeyserEvent-Pack.mcpack");
                Files.copy(exported.toPath(), rootCopy.toPath(), StandardCopyOption.REPLACE_EXISTING);

                File pluginsDir = getDataFolder().getParentFile();
                if (pluginsDir != null) {
                    for (String geyserFolder : new String[]{"Geyser-Spigot", "Geyser-Paper", "Geyser-Purpur", "Geyser"}) {
                        File geyserDir = new File(pluginsDir, geyserFolder);
                        if (geyserDir.exists()) {
                            File packsDir = new File(geyserDir, "packs");
                            if (!packsDir.exists()) {
                                packsDir.mkdirs();
                            }
                            File targetPack = new File(packsDir, "GeyserEvent-Pack.mcpack");
                            Files.copy(exported.toPath(), targetPack.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            getLogger().info("Текстурпак GeyserEvent-Pack.mcpack автоматически перезаписан в " + targetPack.getPath());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDisable() {
        if (this.minerBackpackManager != null) {
            this.minerBackpackManager.saveData();
            this.minerBackpackManager.clearAllVisualBackpacks();
        }
        if (this.ancientMineManager != null) {
            this.ancientMineManager.shutdownMine(true);
        }
        if (this.summonAltarManager != null) {
            this.summonAltarManager.shutdown();
        }
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

    public WorldGuardHook getWorldGuardHook() {
        return worldGuardHook;
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

    public AncientMineManager getAncientMineManager() {
        return ancientMineManager;
    }

    public MinerBackpackManager getMinerBackpackManager() {
        return minerBackpackManager;
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

    public SummonAltarManager getSummonAltarManager() {
        return summonAltarManager;
    }

    public InfernalRiftManager getRiftManager() {
        return riftManager;
    }
}
