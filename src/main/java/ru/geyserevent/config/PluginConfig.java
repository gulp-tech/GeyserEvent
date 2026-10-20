package ru.geyserevent.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.util.ColorUtil;

/**
 * Конфигурация плагина GeyserEvent (Адский Разлом).
 */
public class PluginConfig {

    private final GeyserEventPlugin plugin;

    // Bedrock (GeyserMC)
    private boolean strictBedrockOnly;
    private boolean allowAdminBypass;
    private boolean useCumulusForms;
    private boolean actionbarCompass;
    private String fallbackPrefix;

    // Event
    private String worldName;
    private boolean autoStart;
    private int intervalMinutes;
    private int durationMinutes;
    private int minBedrockPlayers;
    private int cleanupDelaySeconds;
    private int centerX;
    private int centerZ;
    private int minRadius;
    private int maxRadius;
    private int maxSpawnAttempts;

    // Schematic
    private String schematicFile;
    private boolean restoreBlocksOnEnd;
    private boolean protectRegion;
    private int protectionRadius;
    private boolean disablePortalTeleport;

    // Boss (Большой Брутальный Пиглин 2м)
    private String bossName;
    private double bossHealth;
    private double bossDamage;
    private double bossHeightMeters;
    private double bossScale;
    private Material bossWeaponMaterial;
    private String bossWeaponName;
    private boolean bossWeaponGlow;
    private double bossMovementSpeed;
    private double bossKnockbackResistance;
    private double bossLeashRadius;

    // Minions (Приспешники)
    private boolean minionsEnabled;
    private int minionSummonIntervalSeconds;
    private int minionCountPerWave;
    private int maxActiveMinions;
    private EntityType minionEntityType;
    private String minionName;
    private double minionHealth;
    private double minionDamage;
    private double minionScale;
    private Material minionWeaponMaterial;

    // Abilities (в радиусе 15 блоков)
    private double abilityRadius;
    private int abilityCastIntervalSeconds;

    // 1. Притягивание (Pull)
    private boolean pullEnabled;
    private double pullHorizontalStrength;
    private double pullVerticalBoost;
    private double pullDamage;
    private int pullSlownessTicks;

    // 2. Отталкивание (Push)
    private boolean pushEnabled;
    private double pushHorizontalStrength;
    private double pushVerticalBoost;
    private double pushDamage;

    // 3. Стяжка всех в одно место (Gather)
    private boolean gatherEnabled;
    private double gatherDistanceInFront;
    private double gatherDamage;
    private int gatherSlownessTicks;

    // Rewards (PlayerPoints — Осколки)
    private String currencyName;
    private boolean usePlayerPointsApi;
    private String fallbackCommand;
    private int rewardFirstPlace;
    private int rewardSecondPlace;
    private int rewardThirdPlace;

    // Messages
    private String prefix;
    private String msgOnlyBedrock;
    private String msgPortalNoTeleport;
    private String msgRegionProtected;
    private String msgAbilityPull;
    private String msgAbilityPush;
    private String msgAbilityGather;
    private String msgAbilityMinions;

    public PluginConfig(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        // Bedrock
        this.strictBedrockOnly = cfg.getBoolean("bedrock.strict-only", true);
        this.allowAdminBypass = cfg.getBoolean("bedrock.allow-admin-bypass", true);
        this.useCumulusForms = cfg.getBoolean("bedrock.use-cumulus-forms", true);
        this.actionbarCompass = cfg.getBoolean("bedrock.actionbar-compass", true);
        this.fallbackPrefix = cfg.getString("bedrock.fallback-prefix", ".");

        // Event
        this.worldName = cfg.getString("event.world", "world2");
        this.autoStart = cfg.getBoolean("event.auto-start", true);
        this.intervalMinutes = Math.max(1, cfg.getInt("event.interval-minutes", 60));
        this.durationMinutes = Math.max(1, cfg.getInt("event.duration-minutes", 25));
        this.minBedrockPlayers = Math.max(0, cfg.getInt("event.min-bedrock-players", 1));
        this.cleanupDelaySeconds = Math.max(1, cfg.getInt("event.cleanup-delay-seconds", 20));
        this.centerX = cfg.getInt("event.spawn.center-x", 0);
        this.centerZ = cfg.getInt("event.spawn.center-z", 0);
        this.minRadius = Math.max(10, cfg.getInt("event.spawn.min-radius", 100));
        this.maxRadius = Math.max(this.minRadius + 20, cfg.getInt("event.spawn.max-radius", 1000));
        this.maxSpawnAttempts = Math.max(5, cfg.getInt("event.spawn.max-attempts", 35));

        // Schematic
        this.schematicFile = cfg.getString("schematic.file", "infernal_rift.schem.yml");
        this.restoreBlocksOnEnd = cfg.getBoolean("schematic.restore-blocks-on-end", true);
        this.protectRegion = cfg.getBoolean("schematic.protect-region", true);
        this.protectionRadius = Math.max(5, cfg.getInt("schematic.protection-radius", 12));
        this.disablePortalTeleport = cfg.getBoolean("schematic.disable-portal-teleport", true);

        // Boss
        this.bossName = cfg.getString("boss.name", "&4&lБрутальный Владыка Разлома &8[&c%hp%&7/&c%max_hp% ❤&8]");
        this.bossHealth = Math.max(10.0, cfg.getDouble("boss.health", 3500.0));
        this.bossDamage = Math.max(1.0, cfg.getDouble("boss.damage", 15.0));
        this.bossHeightMeters = Math.max(1.0, cfg.getDouble("boss.height-meters", 2.0));
        this.bossScale = Math.max(0.5, cfg.getDouble("boss.scale", 1.22));
        this.bossWeaponMaterial = parseMaterial(cfg.getString("boss.weapon.material", "NETHERITE_AXE"), Material.NETHERITE_AXE);
        this.bossWeaponName = ColorUtil.color(cfg.getString("boss.weapon.name", "&4&lСекира Адского Разлома"));
        this.bossWeaponGlow = cfg.getBoolean("boss.weapon.enchanted-glow", true);
        this.bossMovementSpeed = cfg.getDouble("boss.movement-speed", 0.35);
        this.bossKnockbackResistance = cfg.getDouble("boss.knockback-resistance", 0.85);
        this.bossLeashRadius = Math.max(12.0, cfg.getDouble("boss.leash-radius", 35.0));

        // Minions
        this.minionsEnabled = cfg.getBoolean("minions.enabled", true);
        this.minionSummonIntervalSeconds = Math.max(5, cfg.getInt("minions.summon-interval-seconds", 25));
        this.minionCountPerWave = Math.max(1, cfg.getInt("minions.count-per-wave", 3));
        this.maxActiveMinions = Math.max(1, cfg.getInt("minions.max-active", 8));
        this.minionEntityType = parseEntityType(cfg.getString("minions.entity-type", "PIGLIN_BRUTE"), EntityType.PIGLIN_BRUTE);
        this.minionName = cfg.getString("minions.name", "&cПриспешник Разлома &7[%hp% ❤]");
        this.minionHealth = Math.max(5.0, cfg.getDouble("minions.health", 60.0));
        this.minionDamage = Math.max(1.0, cfg.getDouble("minions.damage", 7.0));
        this.minionScale = Math.max(0.5, cfg.getDouble("minions.scale", 0.92));
        this.minionWeaponMaterial = parseMaterial(cfg.getString("minions.weapon", "GOLDEN_AXE"), Material.GOLDEN_AXE);

        // Abilities (Radius 15 blocks)
        this.abilityRadius = Math.max(3.0, cfg.getDouble("abilities.radius", 15.0));
        this.abilityCastIntervalSeconds = Math.max(4, cfg.getInt("abilities.cast-interval-seconds", 14));

        this.pullEnabled = cfg.getBoolean("abilities.pull.enabled", true);
        this.pullHorizontalStrength = cfg.getDouble("abilities.pull.horizontal-strength", 1.65);
        this.pullVerticalBoost = cfg.getDouble("abilities.pull.vertical-boost", 0.42);
        this.pullDamage = cfg.getDouble("abilities.pull.damage", 4.0);
        this.pullSlownessTicks = cfg.getInt("abilities.pull.slowness-ticks", 40);

        this.pushEnabled = cfg.getBoolean("abilities.push.enabled", true);
        this.pushHorizontalStrength = cfg.getDouble("abilities.push.horizontal-strength", 2.05);
        this.pushVerticalBoost = cfg.getDouble("abilities.push.vertical-boost", 0.65);
        this.pushDamage = cfg.getDouble("abilities.push.damage", 6.0);

        this.gatherEnabled = cfg.getBoolean("abilities.gather.enabled", true);
        this.gatherDistanceInFront = cfg.getDouble("abilities.gather.distance-in-front", 3.5);
        this.gatherDamage = cfg.getDouble("abilities.gather.damage", 5.0);
        this.gatherSlownessTicks = cfg.getInt("abilities.gather.slowness-ticks", 60);

        // Rewards (PlayerPoints)
        this.currencyName = cfg.getString("rewards.currency-name", "осколков");
        this.usePlayerPointsApi = cfg.getBoolean("rewards.use-playerpoints-api", true);
        this.fallbackCommand = cfg.getString("rewards.fallback-command", "points give %player% %amount%");
        this.rewardFirstPlace = cfg.getInt("rewards.places.1", 3000);
        this.rewardSecondPlace = cfg.getInt("rewards.places.2", 2000);
        this.rewardThirdPlace = cfg.getInt("rewards.places.3", 1000);

        // Messages
        this.prefix = ColorUtil.color(cfg.getString("messages.prefix", "&8[&4&lАдский Разлом&8] &r"));
        this.msgOnlyBedrock = ColorUtil.color(cfg.getString("messages.only-bedrock",
                "&cЭтот ивент и меню доступны только для игроков с Bedrock Edition (GeyserMC)!"));
        this.msgPortalNoTeleport = ColorUtil.color(cfg.getString("messages.portal-no-teleport",
                "&cПортал Адского Разлома нестабилен — телепортация заблокирована!"));
        this.msgRegionProtected = ColorUtil.color(cfg.getString("messages.region-protected",
                "&cВы не можете ломать или ставить блоки в зоне Адского Разлома!"));
        this.msgAbilityPull = ColorUtil.color(cfg.getString("messages.ability-pull",
                "&6&l⚡ &eБрутальный Пиглин &6&lПРИТЯНУЛ &eвас к себе цепями Разлома!"));
        this.msgAbilityPush = ColorUtil.color(cfg.getString("messages.ability-push",
                "&c&l💥 &eБрутальный Пиглин &c&lОТТОЛКНУЛ &eвсех сокрушительной волной!"));
        this.msgAbilityGather = ColorUtil.color(cfg.getString("messages.ability-gather",
                "&5&l🌀 &eБрутальный Пиглин &d&lСТЯНУЛ ВСЕХ В ОДНУ ТОЧКУ &eперед собой!"));
        this.msgAbilityMinions = ColorUtil.color(cfg.getString("messages.ability-minions",
                "&4&l🔥 &cВладыка Разлома призвал подкрепление приспешников из портала!"));
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        Material mat = Material.matchMaterial(name.trim().toUpperCase());
        return mat != null ? mat : fallback;
    }

    private EntityType parseEntityType(String name, EntityType fallback) {
        if (name == null) return fallback;
        try {
            return EntityType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public void setWorldNameAndSave(String newWorldName) {
        this.worldName = newWorldName;
        plugin.getConfig().set("event.world", newWorldName);
        plugin.saveConfig();
    }

    public void updateFromAdminForm(
            String newWorld,
            double newHealth,
            double newDamage,
            double newAbilityRadius,
            int firstReward,
            int secondReward,
            int thirdReward
    ) {
        this.worldName = newWorld;
        this.bossHealth = newHealth;
        this.bossDamage = newDamage;
        this.abilityRadius = newAbilityRadius;
        this.rewardFirstPlace = firstReward;
        this.rewardSecondPlace = secondReward;
        this.rewardThirdPlace = thirdReward;

        FileConfiguration cfg = plugin.getConfig();
        cfg.set("event.world", newWorld);
        cfg.set("boss.health", newHealth);
        cfg.set("boss.damage", newDamage);
        cfg.set("abilities.radius", newAbilityRadius);
        cfg.set("rewards.places.1", firstReward);
        cfg.set("rewards.places.2", secondReward);
        cfg.set("rewards.places.3", thirdReward);
        plugin.saveConfig();
    }

    public int getRewardForPlace(int place) {
        return switch (place) {
            case 1 -> rewardFirstPlace;
            case 2 -> rewardSecondPlace;
            case 3 -> rewardThirdPlace;
            default -> 0;
        };
    }

    public boolean isStrictBedrockOnly() { return strictBedrockOnly; }
    public boolean isAllowAdminBypass() { return allowAdminBypass; }
    public boolean isUseCumulusForms() { return useCumulusForms; }
    public boolean isActionbarCompass() { return actionbarCompass; }
    public String getFallbackPrefix() { return fallbackPrefix; }

    public String getWorldName() { return worldName; }
    public boolean isAutoStart() { return autoStart; }
    public int getIntervalMinutes() { return intervalMinutes; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getMinBedrockPlayers() { return minBedrockPlayers; }
    public int getCleanupDelaySeconds() { return cleanupDelaySeconds; }
    public int getCenterX() { return centerX; }
    public int getCenterZ() { return centerZ; }
    public int getMinRadius() { return minRadius; }
    public int getMaxRadius() { return maxRadius; }
    public int getMaxSpawnAttempts() { return maxSpawnAttempts; }

    public String getSchematicFile() { return schematicFile; }
    public boolean isRestoreBlocksOnEnd() { return restoreBlocksOnEnd; }
    public boolean isProtectRegion() { return protectRegion; }
    public int getProtectionRadius() { return protectionRadius; }
    public boolean isDisablePortalTeleport() { return disablePortalTeleport; }

    public String getBossName() { return bossName; }
    public double getBossHealth() { return bossHealth; }
    public double getBossDamage() { return bossDamage; }
    public double getBossHeightMeters() { return bossHeightMeters; }
    public double getBossScale() { return bossScale; }
    public Material getBossWeaponMaterial() { return bossWeaponMaterial; }
    public String getBossWeaponName() { return bossWeaponName; }
    public boolean isBossWeaponGlow() { return bossWeaponGlow; }
    public double getBossMovementSpeed() { return bossMovementSpeed; }
    public double getBossKnockbackResistance() { return bossKnockbackResistance; }
    public double getBossLeashRadius() { return bossLeashRadius; }

    public boolean isMinionsEnabled() { return minionsEnabled; }
    public int getMinionSummonIntervalSeconds() { return minionSummonIntervalSeconds; }
    public int getMinionCountPerWave() { return minionCountPerWave; }
    public int getMaxActiveMinions() { return maxActiveMinions; }
    public EntityType getMinionEntityType() { return minionEntityType; }
    public String getMinionName() { return minionName; }
    public double getMinionHealth() { return minionHealth; }
    public double getMinionDamage() { return minionDamage; }
    public double getMinionScale() { return minionScale; }
    public Material getMinionWeaponMaterial() { return minionWeaponMaterial; }

    public double getAbilityRadius() { return abilityRadius; }
    public int getAbilityCastIntervalSeconds() { return abilityCastIntervalSeconds; }

    public boolean isPullEnabled() { return pullEnabled; }
    public double getPullHorizontalStrength() { return pullHorizontalStrength; }
    public double getPullVerticalBoost() { return pullVerticalBoost; }
    public double getPullDamage() { return pullDamage; }
    public int getPullSlownessTicks() { return pullSlownessTicks; }

    public boolean isPushEnabled() { return pushEnabled; }
    public double getPushHorizontalStrength() { return pushHorizontalStrength; }
    public double getPushVerticalBoost() { return pushVerticalBoost; }
    public double getPushDamage() { return pushDamage; }

    public boolean isGatherEnabled() { return gatherEnabled; }
    public double getGatherDistanceInFront() { return gatherDistanceInFront; }
    public double getGatherDamage() { return gatherDamage; }
    public int getGatherSlownessTicks() { return gatherSlownessTicks; }

    public String getCurrencyName() { return currencyName; }
    public boolean isUsePlayerPointsApi() { return usePlayerPointsApi; }
    public String getFallbackCommand() { return fallbackCommand; }
    public int getRewardFirstPlace() { return rewardFirstPlace; }
    public int getRewardSecondPlace() { return rewardSecondPlace; }
    public int getRewardThirdPlace() { return rewardThirdPlace; }

    public String getPrefix() { return prefix; }
    public String getMsgOnlyBedrock() { return prefix + msgOnlyBedrock; }
    public String getMsgPortalNoTeleport() { return prefix + msgPortalNoTeleport; }
    public String getMsgRegionProtected() { return prefix + msgRegionProtected; }
    public String getMsgAbilityPull() { return prefix + msgAbilityPull; }
    public String getMsgAbilityPush() { return prefix + msgAbilityPush; }
    public String getMsgAbilityGather() { return prefix + msgAbilityGather; }
    public String getMsgAbilityMinions() { return prefix + msgAbilityMinions; }
}
