package ru.geyserevent.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Конфигурация плагина GeyserEvent v1.1.0 (Адский Разлом).
 */
public class PluginConfig {

    public record LootItemConfig(
            String key,
            Material material,
            double chancePercent,
            int minAmount,
            int maxAmount,
            boolean rare
    ) {
    }

    private final GeyserEventPlugin plugin;

    // Bedrock (GeyserMC)
    private boolean strictBedrockOnly;
    private boolean allowAdminBypass;
    private String fallbackPrefix;

    // ResourcePack & Top-Right HUD
    private boolean autoInstallResourcePackToGeyser;
    private boolean topRightHudEnabled;
    private String topRightHudTitle;
    private String topRightEventDisplayName;
    private String topRightRarityDisplay;

    // Event
    private String worldName;
    private String lootRarity;
    private boolean autoStart;
    private int intervalMinutes;
    private int durationMinutes;
    private int minBedrockPlayers;
    private int cleanupDelaySeconds;
    private double gpsArrivalDistance;
    private boolean gpsParticleTrail;
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

    // Boss
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
    private double maxRangedAttackDistance;

    // Minions & Minion Loot
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
    private boolean minionLootEnabled;
    private int minionMinExp;
    private int minionMaxExp;
    private boolean announceRareDrops;
    private final List<LootItemConfig> minionLootItems = new ArrayList<>();

    // Abilities (Radius 15 blocks)
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

    // Rewards (PlayerPoints)
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
    private String msgRareMinionDrop;
    private List<String> spawnBroadcastLines;
    private List<String> delayWaitingLines;
    private List<String> delayActiveLines;
    private List<String> gpsArrivedInfoLines;

    public PluginConfig(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public final void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        // Bedrock
        this.strictBedrockOnly = cfg.getBoolean("bedrock.strict-only", true);
        this.allowAdminBypass = cfg.getBoolean("bedrock.allow-admin-bypass", true);
        this.fallbackPrefix = cfg.getString("bedrock.fallback-prefix", ".");

        // ResourcePack & Top-Right HUD
        this.autoInstallResourcePackToGeyser = cfg.getBoolean("resourcepack.auto-install-to-geyser", true);
        this.topRightHudEnabled = cfg.getBoolean("resourcepack.top-right-hud.enabled", true);
        this.topRightHudTitle = ColorUtil.color(cfg.getString("resourcepack.top-right-hud.title", "&#FF2E2E&l🔥 АДСКИЙ РАЗЛОМ"));
        this.topRightEventDisplayName = ColorUtil.color(cfg.getString("resourcepack.top-right-hud.event-display-name", "&#FF4500Адский Разлом"));
        this.topRightRarityDisplay = ColorUtil.color(cfg.getString("resourcepack.top-right-hud.rarity-display",
                "&#FF2E2EМ&#F64620и&#ED5E12ф&#E47604и&#EB8E08ч&#F2A60Cе&#F9BE10с&#FFD614к&#FFE042и&#FFEA70й"));

        // Event
        this.worldName = cfg.getString("event.world", "world2");
        this.lootRarity = ColorUtil.color(cfg.getString("event.loot-rarity",
                "&#FF2E2EМ&#F64620и&#ED5E12ф&#E47604и&#EB8E08ч&#F2A60Cе&#F9BE10с&#FFD614к&#FFE042и&#FFEA70й"));
        this.autoStart = cfg.getBoolean("event.auto-start", true);
        this.intervalMinutes = Math.max(1, cfg.getInt("event.interval-minutes", 60));
        this.durationMinutes = Math.max(1, cfg.getInt("event.duration-minutes", 25));
        this.minBedrockPlayers = Math.max(0, cfg.getInt("event.min-bedrock-players", 1));
        this.cleanupDelaySeconds = Math.max(1, cfg.getInt("event.cleanup-delay-seconds", 20));
        this.gpsArrivalDistance = Math.max(2.0, cfg.getDouble("event.gps.arrival-distance", 10.0));
        this.gpsParticleTrail = cfg.getBoolean("event.gps.particle-trail", true);
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
        this.maxRangedAttackDistance = Math.max(10.0, cfg.getDouble("boss.max-ranged-attack-distance", 28.0));

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

        // Minion Loot
        this.minionLootEnabled = cfg.getBoolean("minions.loot.enabled", true);
        this.minionMinExp = Math.max(0, cfg.getInt("minions.loot.min-exp", 50));
        this.minionMaxExp = Math.max(this.minionMinExp, cfg.getInt("minions.loot.max-exp", 120));
        this.announceRareDrops = cfg.getBoolean("minions.loot.announce-rare-drops", true);
        loadMinionLootTable(cfg.getConfigurationSection("minions.loot.items"));

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
        this.prefix = ColorUtil.color(cfg.getString("messages.prefix", "&e┠ &r"));
        this.msgOnlyBedrock = ColorUtil.color(cfg.getString("messages.only-bedrock",
                "&e┠ &cЭтот ивент доступен только для игроков с Bedrock Edition (GeyserMC)!"));
        this.msgPortalNoTeleport = ColorUtil.color(cfg.getString("messages.portal-no-teleport",
                "&e┠ &cПортал Адского Разлома нестабилен — телепортация заблокирована!"));
        this.msgRegionProtected = ColorUtil.color(cfg.getString("messages.region-protected",
                "&e┠ &cВы не можете ломать или ставить блоки в зоне Адского Разлома!"));
        this.msgAbilityPull = ColorUtil.color(cfg.getString("messages.ability-pull",
                "&e┠ &6⚡ &fБрутальный Пиглин &#FF8C00притянул &fвас к себе цепями Разлома!"));
        this.msgAbilityPush = ColorUtil.color(cfg.getString("messages.ability-push",
                "&e┠ &c💥 &fБрутальный Пиглин &#FF3333оттолкнул &fвсех сокрушительной волной!"));
        this.msgAbilityGather = ColorUtil.color(cfg.getString("messages.ability-gather",
                "&e┠ &d🌀 &fБрутальный Пиглин &#D442FFстянул всех в одну точку &fперед собой!"));
        this.msgAbilityMinions = ColorUtil.color(cfg.getString("messages.ability-minions",
                "&e┠ &4🔥 &fВладыка Разлома призвал подкрепление приспешников из портала!"));
        this.msgRareMinionDrop = cfg.getString("messages.rare-minion-drop",
                "&e┠ &fИгрок &#E0C912%player% &fвыбил редкий дроп с приспешника: &#FF4500%item% &7(x%amount%)");

        this.spawnBroadcastLines = cfg.getStringList("messages.spawn-broadcast");
        if (this.spawnBroadcastLines.isEmpty()) {
            this.spawnBroadcastLines = List.of(
                    "&e┠ &#34D711А&#41CF10д&#4EC80Eс&#5BC00Dк&#68B90Bи&#75B10Aй&#81A909-&#8EA207Р&#9B9A06а&#A89304з&#B58B03л&#C28402о&#CF7C00м &fпоявился на карте.",
                    "&e┠ &fРедкость: %loot%",
                    "&e┠ &fЗдоровье босса: &c%hp% ❤ &7(Урон: &c%damage%&7)",
                    "&e┠ &fКоординаты: &#E0C912%x% %y% %z%"
            );
        }

        this.delayWaitingLines = cfg.getStringList("messages.delay-waiting");
        if (this.delayWaitingLines.isEmpty()) {
            this.delayWaitingLines = List.of(
                    "&e┠ &fДо появления ивента &#34D711Адский-Разлом&f:",
                    "&e┠ &fОсталось времени: &#E0C912%time%",
                    "&e┠ &fМир появления: &#E0C912%world%"
            );
        }

        this.delayActiveLines = cfg.getStringList("messages.delay-active");
        if (this.delayActiveLines.isEmpty()) {
            this.delayActiveLines = List.of(
                    "&e┠ &#34D711Адский-Разлом &fуже активен на карте!",
                    "&e┠ &fЗдоровье босса: &c%hp%/%max_hp% ❤",
                    "&e┠ &fКоординаты: &#E0C912%x% %y% %z%",
                    "&e┠ &fВключить трекер: &#34D711/event gps"
            );
        }

        this.gpsArrivedInfoLines = cfg.getStringList("messages.gps-arrived-info");
        if (this.gpsArrivedInfoLines.isEmpty()) {
            this.gpsArrivedInfoLines = List.of(
                    "&e┠ &#34D711Вы прибыли к ивенту Адский Разлом! &7(GPS-трекер отключён)",
                    "&e┠ &fБосс: &cБольшой Брутальный Пиглин (2м)",
                    "&e┠ &fЗдоровье: &c%hp%/%max_hp% ❤ &7| &fУрон: &c%damage%",
                    "&e┠ &fРедкость: %loot%",
                    "&e┠ &fКоординаты: &#E0C912%x% %y% %z%",
                    "&e┠ &fНаграды: &61м: &d%r1% &7| &f2м: &d%r2% &7| &c3м: &d%r3% %currency%"
            );
        }
    }

    private void loadMinionLootTable(ConfigurationSection sec) {
        minionLootItems.clear();
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection itemSec = sec.getConfigurationSection(key);
                if (itemSec == null) continue;
                Material mat = Material.matchMaterial(itemSec.getString("material", ""));
                if (mat == null || mat.isAir()) continue;
                double chance = itemSec.getDouble("chance", 25.0);
                int min = Math.max(1, itemSec.getInt("min", 1));
                int max = Math.max(min, itemSec.getInt("max", min));
                boolean rare = itemSec.getBoolean("rare", false);
                minionLootItems.add(new LootItemConfig(key, mat, chance, min, max, rare));
            }
        }
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
    public String getFallbackPrefix() { return fallbackPrefix; }

    public boolean isAutoInstallResourcePackToGeyser() { return autoInstallResourcePackToGeyser; }
    public boolean isTopRightHudEnabled() { return topRightHudEnabled; }
    public String getTopRightHudTitle() { return topRightHudTitle; }
    public String getTopRightEventDisplayName() { return topRightEventDisplayName; }
    public String getTopRightRarityDisplay() { return topRightRarityDisplay; }

    public String getWorldName() { return worldName; }
    public String getLootRarity() { return lootRarity; }
    public boolean isAutoStart() { return autoStart; }
    public int getIntervalMinutes() { return intervalMinutes; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getMinBedrockPlayers() { return minBedrockPlayers; }
    public int getCleanupDelaySeconds() { return cleanupDelaySeconds; }
    public double getGpsArrivalDistance() { return gpsArrivalDistance; }
    public boolean isGpsParticleTrail() { return gpsParticleTrail; }
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
    public double getMaxRangedAttackDistance() { return maxRangedAttackDistance; }

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
    public boolean isMinionLootEnabled() { return minionLootEnabled; }
    public int getMinionMinExp() { return minionMinExp; }
    public int getMinionMaxExp() { return minionMaxExp; }
    public boolean isAnnounceRareDrops() { return announceRareDrops; }
    public List<LootItemConfig> getMinionLootItems() { return minionLootItems; }

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
    public String getMsgOnlyBedrock() { return msgOnlyBedrock; }
    public String getMsgPortalNoTeleport() { return msgPortalNoTeleport; }
    public String getMsgRegionProtected() { return msgRegionProtected; }
    public String getMsgAbilityPull() { return msgAbilityPull; }
    public String getMsgAbilityPush() { return msgAbilityPush; }
    public String getMsgAbilityGather() { return msgAbilityGather; }
    public String getMsgAbilityMinions() { return msgAbilityMinions; }
    public String getMsgRareMinionDrop() { return msgRareMinionDrop; }
    public List<String> getSpawnBroadcastLines() { return spawnBroadcastLines; }
    public List<String> getDelayWaitingLines() { return delayWaitingLines; }
    public List<String> getDelayActiveLines() { return delayActiveLines; }
    public List<String> getGpsArrivedInfoLines() { return gpsArrivedInfoLines; }
}
