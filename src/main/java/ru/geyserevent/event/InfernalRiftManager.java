package ru.geyserevent.event;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.boss.BossAbilityController;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.excavation.BrushTier;
import ru.geyserevent.util.ColorUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Главный менеджер ивентов:
 * 1) «Адский Разлом (Вторжение Босса)»
 * 2) «Пляжные Раскопки (2-метровый Житель с кисточками по клику)»
 * 3) «Котёл Алхимика (Мистическое болото 31x31, сбор реагентов и варка в котле)»
 */
public class InfernalRiftManager {

    private final GeyserEventPlugin plugin;
    private final NamespacedKey bossKey;
    private final NamespacedKey villagerKey;
    private final DamageLeaderboard leaderboard = new DamageLeaderboard();

    /**
     * Игроки с включённым локатором (/event gps) и их персональные BossBar-индикаторы.
     */
    private final Set<UUID> gpsTrackedPlayers = new HashSet<>();
    private final Set<UUID> gpsWasOutsideRadius = new HashSet<>();
    private final Map<UUID, BossBar> gpsBossBars = new HashMap<>();

    private boolean active = false;
    private boolean defeated = false;
    private EventType activeEventType = EventType.INFERNAL_RIFT;
    private int bossPhase = 1;

    private Location riftCenterLocation;
    private Location bossHomeLocation;
    private UUID bossUuid;
    private UUID villagerUuid;
    private double bossCurrentHealth;
    private double bossMaxHealth;

    private int brushCountdownSeconds;
    private boolean brushReadyToClaim;
    private int reagentSproutCooldownSeconds;

    private BossBar bossBar;
    private BukkitTask autoStartTask;
    private BukkitTask activeTickTask;
    private BukkitTask gpsFastTask;
    private BukkitTask cleanupTask;

    private long nextAutoStartEpochSeconds;
    private long eventEndEpochSeconds;
    private int secondsElapsed;
    private int minionCooldownSeconds;

    public InfernalRiftManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "infernal_rift_boss");
        this.villagerKey = new NamespacedKey(plugin, "beach_excavation_villager");
        startAutoScheduler();
        startFastGpsLoop();
    }

    public final void startAutoScheduler() {
        if (autoStartTask != null) {
            autoStartTask.cancel();
        }
        resetNextAutoStartTimer();

        autoStartTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (active || !plugin.getPluginConfig().isAutoStart()) {
                    return;
                }
                long now = System.currentTimeMillis() / 1000L;
                if (now >= nextAutoStartEpochSeconds) {
                    int onlineBedrock = plugin.getBedrockService().getOnlineBedrockCount();
                    if (onlineBedrock >= plugin.getPluginConfig().getMinBedrockPlayers()) {
                        EventType[] types = EventType.values();
                        EventType randomType = types[ThreadLocalRandom.current().nextInt(types.length)];
                        startEventRandomInConfiguredWorld(randomType);
                    } else {
                        nextAutoStartEpochSeconds = now + 300L;
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Быстрый цикл каждые 5 тиков (0.25 сек):
     * 1) Проверяет и удаляет кисточки/реагенты у тех, кто ушёл далеко от ивента
     * 2) Обновляет локатор (/event gps)
     */
    private void startFastGpsLoop() {
        if (gpsFastTask != null) {
            gpsFastTask.cancel();
        }
        gpsFastTask = new BukkitRunnable() {
            @Override
            public void run() {
                enforceBrushZoneForPlayers();
                updateGpsTrackerForPlayers();
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    /**
     * Проверяет одного игрока: если у него есть кисточка или реагент ивента, но он вышел за пределы
     * схемы ивента (радиус 16.5 блоков от центра 31x31 схемы), предмет и свечение мгновенно удаляются.
     */
    public void enforceBrushZoneForPlayer(Player player) {
        if (player == null || !plugin.getBrushLootManager().hasAnyEventBrush(player)) {
            return;
        }
        double zoneRadius = plugin.getPluginConfig().getExcavationBrushRemoveDistance();
        double zoneRadiusSq = zoneRadius * zoneRadius;

        boolean isItemEventActive = active
                && (activeEventType == EventType.BEACH_EXCAVATION || activeEventType == EventType.ALCHEMIST_CAULDRON);

        boolean outside = !isItemEventActive
                || riftCenterLocation == null
                || riftCenterLocation.getWorld() == null
                || !player.getWorld().equals(riftCenterLocation.getWorld());

        if (!outside) {
            Location pLoc = player.getLocation();
            double dx = pLoc.getX() - riftCenterLocation.getX();
            double dz = pLoc.getZ() - riftCenterLocation.getZ();
            double dy = Math.abs(pLoc.getY() - riftCenterLocation.getY());
            if ((dx * dx + dz * dz) > zoneRadiusSq || dy > 14.0) {
                outside = true;
            }
        }

        if (outside) {
            boolean hadReagent = plugin.getBrushLootManager().hasAnyReagent(player);
            if (plugin.getBrushLootManager().removeAllEventBrushes(player)) {
                player.removePotionEffect(PotionEffectType.GLOWING);
                if (hadReagent) {
                    player.sendMessage(ColorUtil.color("&e┠ &cВы вышли из зоны ивента — реагенты пропали."));
                } else {
                    player.sendMessage(ColorUtil.color("&e┠ &cВы вышли из зоны ивента — кисточки пропали."));
                }
            }
        }
    }

    /**
     * Проверяет всех игроков онлайн и выброшенные предметы: если игрок или предмет находится
     * за пределами зоны ивента, кисточки и реагенты сразу пропадают.
     */
    public void enforceBrushZoneForPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            enforceBrushZoneForPlayer(player);
        }

        double zoneRadius = plugin.getPluginConfig().getExcavationBrushRemoveDistance();
        double zoneRadiusSq = zoneRadius * zoneRadius;

        if (active
                && (activeEventType == EventType.BEACH_EXCAVATION || activeEventType == EventType.ALCHEMIST_CAULDRON)
                && riftCenterLocation != null
                && riftCenterLocation.getWorld() != null) {
            World w = riftCenterLocation.getWorld();
            for (Entity entity : w.getEntitiesByClass(Item.class)) {
                if (entity instanceof Item itemEntity && plugin.getBrushLootManager().getBrushTier(itemEntity.getItemStack()) != null) {
                    Location iLoc = itemEntity.getLocation();
                    double dx = iLoc.getX() - riftCenterLocation.getX();
                    double dz = iLoc.getZ() - riftCenterLocation.getZ();
                    if ((dx * dx + dz * dz) > zoneRadiusSq) {
                        itemEntity.remove();
                    }
                }
            }
        }
    }

    /**
     * Динамический интервал выдачи кисточек / реагентов у НПС:
     * Под конец ивента НПС начинает раздавать предметы значительно быстрее!
     * - Первые 10 минут (осталось > 10 мин): 30 сек
     * - Осталось 5..10 минут: 20 сек
     * - Осталось 2..5 минут: 12 сек
     * - Последние 2 минуты: 7 сек
     */
    public int getDynamicBrushIntervalSeconds() {
        int base = plugin.getPluginConfig().getExcavationBrushIntervalSeconds();
        long rem = getSecondsRemainingInEvent();
        if (rem <= 120L) {
            return Math.max(5, Math.min(base, 7));
        } else if (rem <= 300L) {
            return Math.max(6, Math.min(base, 12));
        } else if (rem <= 600L) {
            return Math.max(8, Math.min(base, 20));
        }
        return base;
    }

    /**
     * Динамический интервал появления новых Реагентов на 16 пьедесталах болота («Котёл Алхимика»):
     * Под конец ивента котёл закипает и реагенты вырастают намного быстрее!
     */
    public int getDynamicReagentSproutIntervalSeconds() {
        long rem = getSecondsRemainingInEvent();
        if (rem <= 120L) {
            return 4;
        } else if (rem <= 300L) {
            return 6;
        } else if (rem <= 600L) {
            return 9;
        }
        return 12;
    }

    private void resetNextAutoStartTimer() {
        long now = System.currentTimeMillis() / 1000L;
        this.nextAutoStartEpochSeconds = now + (plugin.getPluginConfig().getIntervalMinutes() * 60L);
    }

    public boolean startEventRandomInConfiguredWorld() {
        EventType[] types = EventType.values();
        EventType randomType = types[ThreadLocalRandom.current().nextInt(types.length)];
        return startEventRandomInConfiguredWorld(randomType);
    }

    public boolean startEventRandomInConfiguredWorld(EventType type) {
        PluginConfig cfg = plugin.getPluginConfig();
        String worldName = cfg.getWorldName();
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            plugin.getLogger().warning("Мир '" + worldName + "' не найден! Измените event.world в config.yml (/rift setworld <мир>).");
            return false;
        }

        Location safeSpot = findSafeSurfaceLocation(world, cfg);
        if (safeSpot == null) {
            return false;
        }

        return startEventAt(safeSpot, type);
    }

    public boolean startEventAt(Location targetCenter) {
        return startEventAt(targetCenter, EventType.INFERNAL_RIFT);
    }

    public boolean startEventAt(Location targetCenter, EventType type) {
        if (targetCenter == null || targetCenter.getWorld() == null) {
            return false;
        }
        if (active) {
            stopEvent(false);
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        PluginConfig cfg = plugin.getPluginConfig();
        targetCenter.getChunk().load(true);

        this.active = true;
        this.defeated = false;
        this.activeEventType = (type != null) ? type : EventType.INFERNAL_RIFT;
        this.bossPhase = 1;
        this.secondsElapsed = 0;
        this.minionCooldownSeconds = 0;
        this.leaderboard.clear();
        this.riftCenterLocation = targetCenter.getBlock().getLocation().add(0.5, 0, 0.5);
        this.eventEndEpochSeconds = (System.currentTimeMillis() / 1000L) + (cfg.getDurationMinutes() * 60L);

        if (this.activeEventType == EventType.BEACH_EXCAVATION) {
            // 1. Строим большую схему 31x31 «Пляжные Раскопки» и получаем точку спавна 2м Жителя на пьедестале
            this.bossHomeLocation = plugin.getBeachSchematicManager().pasteSchematic(this.riftCenterLocation);
            this.riftCenterLocation = this.bossHomeLocation.clone().subtract(0, 2.0, 0);

            // 2. Спавним 2-метрового Жителя-Археолога в центре пляжа
            this.brushCountdownSeconds = cfg.getExcavationBrushIntervalSeconds();
            this.brushReadyToClaim = false;
            Villager villager = spawnExcavationVillager(this.bossHomeLocation);
            this.villagerUuid = villager.getUniqueId();

            createAndShowBossBar();
            plugin.getTopRightHudManager().updateTopRightHud();
            broadcastEventStart();
            startActiveTickLoop();
            return true;
        } else if (this.activeEventType == EventType.ALCHEMIST_CAULDRON) {
            // 1. Строим большую схему 31x31 «Котёл Алхимика» и получаем точку спавна 2м Алхимика у котла
            this.bossHomeLocation = plugin.getAlchemistSchematicManager().pasteSchematic(this.riftCenterLocation);
            this.riftCenterLocation = this.bossHomeLocation.clone().subtract(0, 2.0, 0);

            // 2. Спавним 2-метрового Магистра Алхимика в центре болота
            this.brushCountdownSeconds = cfg.getExcavationBrushIntervalSeconds();
            this.brushReadyToClaim = false;
            this.reagentSproutCooldownSeconds = getDynamicReagentSproutIntervalSeconds();
            Villager alchemist = spawnAlchemistNpc(this.bossHomeLocation);
            this.villagerUuid = alchemist.getUniqueId();

            createAndShowBossBar();
            plugin.getTopRightHudManager().updateTopRightHud();
            broadcastEventStart();
            startActiveTickLoop();
            return true;
        } else {
            // 1. Строим схему «Адский Разлом»
            this.bossHomeLocation = plugin.getSchematicManager().pasteSchematic(this.riftCenterLocation);
            if (this.bossHomeLocation == null) {
                this.bossHomeLocation = this.riftCenterLocation.clone().add(0, 1, 2);
            }

            // 2. Спавним Большого Брутального Пиглина (2м, 3500 HP, 8 урона, Незеритовый топор)
            this.bossMaxHealth = cfg.getBossHealth();
            this.bossCurrentHealth = this.bossMaxHealth;
            PiglinBrute boss = spawnInfernalPiglinBoss(this.bossHomeLocation);
            this.bossUuid = boss.getUniqueId();

            createAndShowBossBar();
            plugin.getTopRightHudManager().updateTopRightHud();
            broadcastEventStart();
            startActiveTickLoop();
            return true;
        }
    }

    private Villager spawnExcavationVillager(Location loc) {
        PluginConfig cfg = plugin.getPluginConfig();
        World world = loc.getWorld();

        Entity spawned = world.spawnEntity(loc, EntityType.VILLAGER);
        Villager villager = (Villager) spawned;

        villager.getPersistentDataContainer().set(villagerKey, PersistentDataType.BYTE, (byte) 1);
        villager.setAdult();
        villager.setProfession(Villager.Profession.CARTOGRAPHER);
        villager.setVillagerType(Villager.Type.DESERT);
        villager.setAI(false);
        villager.setGravity(false);
        villager.setInvulnerable(true);
        villager.setCollidable(false);
        villager.setRemoveWhenFarAway(false);
        villager.setPersistent(true);
        villager.setCanPickupItems(false);
        villager.setCustomNameVisible(true);
        villager.setGlowing(true);

        // 2-метровый рост Жителя (~1.95 * 1.10 = 2.15м)
        BossAbilityController.applyEntityScale(villager, cfg.getExcavationVillagerScale());

        EntityEquipment equip = villager.getEquipment();
        if (equip != null) {
            Material brushMat = Material.matchMaterial("BRUSH");
            if (brushMat != null) {
                equip.setItemInMainHand(new ItemStack(brushMat, 1));
                equip.setItemInMainHandDropChance(0.0f);
            }
        }

        updateExcavationVillagerNameAndBar(villager);

        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.2f);
        world.playSound(loc, Sound.ENTITY_VILLAGER_CELEBRATE, 2.0f, 0.9f);
        world.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1.2, 0), 15, 0.6, 0.8, 0.6, 0.03);

        return villager;
    }

    /**
     * Спавнит 2-метрового Магистра Алхимика (Житель-Священник в пурпурном болотном одеянии с Дыханием Дракона в руке).
     */
    private Villager spawnAlchemistNpc(Location loc) {
        PluginConfig cfg = plugin.getPluginConfig();
        World world = loc.getWorld();

        Entity spawned = world.spawnEntity(loc, EntityType.VILLAGER);
        Villager alchemist = (Villager) spawned;

        alchemist.getPersistentDataContainer().set(villagerKey, PersistentDataType.BYTE, (byte) 1);
        alchemist.setAdult();
        alchemist.setProfession(Villager.Profession.CLERIC);
        alchemist.setVillagerType(Villager.Type.SWAMP);
        alchemist.setAI(false);
        alchemist.setGravity(false);
        alchemist.setInvulnerable(true);
        alchemist.setCollidable(false);
        alchemist.setRemoveWhenFarAway(false);
        alchemist.setPersistent(true);
        alchemist.setCanPickupItems(false);
        alchemist.setCustomNameVisible(true);
        alchemist.setGlowing(true);

        BossAbilityController.applyEntityScale(alchemist, cfg.getExcavationVillagerScale());

        EntityEquipment equip = alchemist.getEquipment();
        if (equip != null) {
            equip.setItemInMainHand(new ItemStack(Material.DRAGON_BREATH, 1));
            equip.setItemInMainHandDropChance(0.0f);
        }

        updateAlchemistNameAndBar(alchemist);

        world.playSound(loc, Sound.BLOCK_BREWING_STAND_BREW, 2.0f, 0.8f);
        world.playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 2.0f, 0.9f);
        world.spawnParticle(Particle.WITCH, loc.clone().add(0, 1.2, 0), 15, 0.6, 0.8, 0.6, 0.03);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1.0, 0), 10, 0.5, 0.6, 0.5, 0.02);

        return alchemist;
    }

    private PiglinBrute spawnInfernalPiglinBoss(Location loc) {
        PluginConfig cfg = plugin.getPluginConfig();
        World world = loc.getWorld();

        Entity spawned = world.spawnEntity(loc, EntityType.PIGLIN_BRUTE);
        PiglinBrute boss = (PiglinBrute) spawned;

        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
        boss.setImmuneToZombification(true);
        boss.setAdult();
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);
        boss.setCanPickupItems(false);
        boss.setCustomNameVisible(true);
        boss.setGlowing(true);

        BossAbilityController.applyEntityScale(boss, cfg.getBossScale());
        BossAbilityController.setEntityAttribute(boss, "MOVEMENT_SPEED", "generic.movement_speed", cfg.getBossMovementSpeed());
        BossAbilityController.setEntityAttribute(boss, "KNOCKBACK_RESISTANCE", "generic.knockback_resistance", cfg.getBossKnockbackResistance());
        BossAbilityController.setEntityAttribute(boss, "ATTACK_DAMAGE", "generic.attack_damage", cfg.getBossDamage());
        BossAbilityController.setEntityAttribute(boss, "FOLLOW_RANGE", "generic.follow_range", 32.0);

        BossAbilityController.setEntityAttribute(boss, "MAX_HEALTH", "generic.max_health", cfg.getBossHealth());
        boss.setHealth(boss.getMaxHealth());

        EntityEquipment equip = boss.getEquipment();
        if (equip != null) {
            equip.setItemInMainHand(BossAbilityController.createBossAxe(cfg));
            equip.setItemInMainHandDropChance(0.0f);
        }

        updateBossNameAndHealthBar(boss);

        world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.7f);
        world.playSound(loc, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 2.5f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0, 1.0, 0), 2, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1.2, 0), 80, 0.8, 1.2, 0.8, 0.08);

        return boss;
    }

    private void startActiveTickLoop() {
        if (activeTickTask != null) {
            activeTickTask.cancel();
        }

        activeTickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || defeated) {
                    cancel();
                    return;
                }

                secondsElapsed++;
                PluginConfig cfg = plugin.getPluginConfig();
                long now = System.currentTimeMillis() / 1000L;

                if (activeEventType == EventType.BEACH_EXCAVATION) {
                    tickBeachExcavation(cfg, now);
                } else if (activeEventType == EventType.ALCHEMIST_CAULDRON) {
                    tickAlchemistCauldron(cfg, now);
                } else {
                    tickInfernalRift(cfg, now);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void tickBeachExcavation(PluginConfig cfg, long now) {
        if (now >= eventEndEpochSeconds) {
            Bukkit.broadcastMessage(ColorUtil.color(
                    "&e┠ &#34D711П&#40CE10л&#4CC50Eя&#58BC0Dж&#64B30Bн&#70AA0Aы&#7CA109е-&#889807Р&#948F06а&#A08605с&#AC7D03к&#B87402о&#C46B01п&#CF7C00к&#DB8500и &fзакончились."
            ));
            stopEvent(false);
            return;
        }

        LivingEntity villager = getExcavationVillagerEntity();
        if (villager != null && villager.isValid() && bossHomeLocation != null) {
            if (!villager.getLocation().getWorld().equals(bossHomeLocation.getWorld())
                    || villager.getLocation().distanceSquared(bossHomeLocation) > 0.25) {
                villager.teleport(bossHomeLocation);
            }
        }

        // Чтобы забрать кисточку — нужно кликнуть по НПС!
        // Когда игрок забирает кисточку — НПС НЕ уходит в ожидание, а сразу отсчитывает таймер до следующей кисточки!
        if (!brushReadyToClaim) {
            if (brushCountdownSeconds > 0) {
                brushCountdownSeconds--;
            }
            if (brushCountdownSeconds <= 0) {
                brushReadyToClaim = true;
                if (bossHomeLocation != null && bossHomeLocation.getWorld() != null) {
                    bossHomeLocation.getWorld().playSound(bossHomeLocation, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f, 1.2f);
                    bossHomeLocation.getWorld().spawnParticle(
                            Particle.HAPPY_VILLAGER,
                            bossHomeLocation.clone().add(0, 1.3, 0),
                            8, 0.4, 0.5, 0.4, 0.03
                    );
                }
            }
        }

        if (villager != null) {
            updateExcavationVillagerNameAndBar(villager);
        }
        plugin.getTopRightHudManager().updateTopRightHud();
    }

    private void tickAlchemistCauldron(PluginConfig cfg, long now) {
        if (now >= eventEndEpochSeconds) {
            Bukkit.broadcastMessage(ColorUtil.color(
                    "&e┠ &#34D711К&#42CE0Fо&#50C50Eт&#5EBC0Cё&#6CB30Bл-&#7AAA09А&#88A108л&#969806х&#A48F05и&#B28603м&#C07D02и&#CE7400к&#DC6B00а &fзакончился."
            ));
            stopEvent(false);
            return;
        }

        LivingEntity alchemist = getExcavationVillagerEntity();
        if (alchemist != null && alchemist.isValid() && bossHomeLocation != null) {
            if (!alchemist.getLocation().getWorld().equals(bossHomeLocation.getWorld())
                    || alchemist.getLocation().distanceSquared(bossHomeLocation) > 0.25) {
                alchemist.teleport(bossHomeLocation);
            }
        }

        // 1. Красивые атмосферные частицы над 4 котлами и над всеми активными реагентами на 16 пьедесталах
        plugin.getAlchemistSchematicManager().spawnAmbientEffects();

        // 2. Периодический звук бурления центрального котла
        if (secondsElapsed % 4 == 0 && bossHomeLocation != null && bossHomeLocation.getWorld() != null) {
            bossHomeLocation.getWorld().playSound(bossHomeLocation, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.4f, 0.9f);
        }

        // 3. Рост новых нестабильных Реагентов на 16 пьедесталах болота (ускоряется под конец ивента!)
        if (reagentSproutCooldownSeconds > 0) {
            reagentSproutCooldownSeconds--;
        }
        if (reagentSproutCooldownSeconds <= 0) {
            BrushTier tier = plugin.getBrushLootManager().rollRandomReagentTier();
            plugin.getAlchemistSchematicManager().spawnRandomReagentNode(tier);
            reagentSproutCooldownSeconds = getDynamicReagentSproutIntervalSeconds();
        }

        // 4. Таймер бонусного реагента у самого Алхимика (также без режима ожидания и с ускорением под конец!)
        if (!brushReadyToClaim) {
            if (brushCountdownSeconds > 0) {
                brushCountdownSeconds--;
            }
            if (brushCountdownSeconds <= 0) {
                brushReadyToClaim = true;
                if (bossHomeLocation != null && bossHomeLocation.getWorld() != null) {
                    bossHomeLocation.getWorld().playSound(bossHomeLocation, Sound.BLOCK_BREWING_STAND_BREW, 1.5f, 1.2f);
                    bossHomeLocation.getWorld().spawnParticle(
                            Particle.WITCH,
                            bossHomeLocation.clone().add(0, 1.3, 0),
                            8, 0.4, 0.5, 0.4, 0.03
                    );
                }
            }
        }

        // 5. Подсветка (Glowing) для курьеров, несущих реагент по болоту к котлу
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getBrushLootManager().hasAnyReagent(p)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 35, 0, false, false));
            }
        }

        if (alchemist != null) {
            updateAlchemistNameAndBar(alchemist);
        }
        plugin.getTopRightHudManager().updateTopRightHud();
    }

    /**
     * Обрабатывает клик (ПКМ или ЛКМ) по 2-метровому Жителю-Археологу или Магистру Алхимику.
     */
    @SuppressWarnings("deprecation")
    public void handleVillagerInteraction(Player player) {
        if (!active) {
            return;
        }
        if (activeEventType == EventType.ALCHEMIST_CAULDRON) {
            handleAlchemistInteraction(player);
            return;
        }
        if (activeEventType != EventType.BEACH_EXCAVATION) {
            return;
        }
        if (!plugin.getBedrockService().canParticipate(player)) {
            player.sendMessage(plugin.getPluginConfig().getMsgOnlyBedrock());
            return;
        }

        if (brushReadyToClaim || brushCountdownSeconds <= 0) {
            // Игрок может держать максимум 3 кисточки в инвентаре
            if (plugin.getBrushLootManager().countPlayerBrushes(player) >= 3) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                String msg = ColorUtil.color("&e┠ &cВ инвентаре можно держать максимум 3 кисточки!");
                player.sendMessage(msg);
                try {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
                } catch (Throwable ignored) {
                }
                return;
            }

            if (player.getInventory().firstEmpty() == -1) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                player.sendMessage(ColorUtil.color("&e┠ &cОсвободите место в инвентаре для получения кисточки!"));
                return;
            }

            brushReadyToClaim = false;
            brushCountdownSeconds = getDynamicBrushIntervalSeconds();
            giveRandomBrushToPlayer(player);

            LivingEntity v = getExcavationVillagerEntity();
            if (v != null) {
                updateExcavationVillagerNameAndBar(v);
            }
            plugin.getTopRightHudManager().updateTopRightHud();
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 1.0f, 1.0f);
            String msg = ColorUtil.color("&eАрхеолог &8| &fСледующая кисточка через &#E0C912" + brushCountdownSeconds + " сек.");
            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Обрабатывает клик по 2-метровому Алхимику или центральному Котлу:
     * 1) Если у игрока есть сорванный Реагент — мгновенно варит его в Котле и выдаёт награду этой редкости!
     * 2) Если реагента нет, но у Алхимика готов бонусный реагент по таймеру — выдаёт реагент игроку!
     */
    @SuppressWarnings("deprecation")
    public void handleAlchemistInteraction(Player player) {
        if (!active || activeEventType != EventType.ALCHEMIST_CAULDRON) {
            return;
        }
        if (!plugin.getBedrockService().canParticipate(player)) {
            player.sendMessage(plugin.getPluginConfig().getMsgOnlyBedrock());
            return;
        }

        // 1. Если у игрока в инвентаре есть Реагент — сдаём его Алхимику и варим награду!
        if (plugin.getBrushLootManager().hasAnyReagent(player)) {
            brewPlayerReagentInCauldron(player);
            return;
        }

        // 2. Если реагентов с собой нет, проверяем готовность бонусного реагента у самого Алхимика
        if (brushReadyToClaim || brushCountdownSeconds <= 0) {
            if (plugin.getBrushLootManager().countPlayerBrushes(player) >= 3) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                String msg = ColorUtil.color("&e┠ &cВ инвентаре можно держать максимум 3 реагента!");
                player.sendMessage(msg);
                return;
            }
            if (player.getInventory().firstEmpty() == -1) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                player.sendMessage(ColorUtil.color("&e┠ &cОсвободите место в инвентаре!"));
                return;
            }

            brushReadyToClaim = false;
            brushCountdownSeconds = getDynamicBrushIntervalSeconds();

            BrushTier tier = plugin.getBrushLootManager().rollRandomReagentTier();
            ItemStack reagent = plugin.getBrushLootManager().createReagentItem(tier);
            player.getInventory().addItem(reagent);

            player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 1.2f);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
            if (bossHomeLocation != null && bossHomeLocation.getWorld() != null) {
                bossHomeLocation.getWorld().spawnParticle(
                        Particle.WITCH,
                        bossHomeLocation.clone().add(0, 1.2, 0),
                        8, 0.4, 0.5, 0.4, 0.03
                );
            }

            player.sendMessage(ColorUtil.color(
                    "&e┠ &fВы получили у Алхимика: " + plugin.getBrushLootManager().getReagentDisplayName(tier)
            ));
            try {
                String ab = ColorUtil.color("&dНажмите по Алхимику или Котлу ещё раз, чтобы сварить реагент!");
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ab));
            } catch (Throwable ignored) {
            }

            LivingEntity v = getExcavationVillagerEntity();
            if (v != null) {
                updateAlchemistNameAndBar(v);
            }
            plugin.getTopRightHudManager().updateTopRightHud();
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.2f);
            String msg = ColorUtil.color(
                    "&dАлхимик &8| &fСорвите реагент на болоте или ждите &#E0C912" + brushCountdownSeconds + " сек."
            );
            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Варит 1 реагент игрока в Котле Алхимика и выдаёт предмет соответствующей редкости (без спама в чат).
     */
    @SuppressWarnings("deprecation")
    public boolean brewPlayerReagentInCauldron(Player player) {
        BrushTier tier = plugin.getBrushLootManager().findAndConsumeReagent(player);
        if (tier == null) {
            return false;
        }
        if (!plugin.getBrushLootManager().hasAnyReagent(player)) {
            player.removePotionEffect(PotionEffectType.GLOWING);
        }

        ItemStack reward = plugin.getBrushLootManager().rollLootForCauldron(tier);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
        if (!leftovers.isEmpty()) {
            for (ItemStack drop : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }

        Location effectLoc = (bossHomeLocation != null) ? bossHomeLocation.clone().add(0, 0.8, 0) : player.getLocation();
        World w = effectLoc.getWorld();
        if (w != null) {
            w.playSound(effectLoc, Sound.BLOCK_BREWING_STAND_BREW, 1.6f, 1.15f);
            w.playSound(effectLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f, 1.3f);
            w.spawnParticle(Particle.WITCH, effectLoc, 10, 0.4, 0.5, 0.4, 0.04);
            w.spawnParticle(Particle.HAPPY_VILLAGER, effectLoc, 6, 0.4, 0.4, 0.4, 0.03);
        }

        try {
            String ab = ColorUtil.color("&dКотёл Алхимика &8| &aСварен предмет (" + tier.getItemTitle() + "&a)!");
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ab));
        } catch (Throwable ignored) {
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private void giveRandomBrushToPlayer(Player recipient) {
        BrushTier tier = plugin.getBrushLootManager().rollRandomBrushTier();
        ItemStack brush = plugin.getBrushLootManager().createBrushItem(tier);

        recipient.getInventory().addItem(brush);

        recipient.playSound(recipient.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.2f, 1.1f);
        recipient.playSound(recipient.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
        if (bossHomeLocation != null && bossHomeLocation.getWorld() != null) {
            bossHomeLocation.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    bossHomeLocation.clone().add(0, 1.2, 0),
                    8, 0.4, 0.5, 0.4, 0.03
            );
        }

        recipient.sendMessage(ColorUtil.color(
                "&e┠ &fВы забрали у Археолога предмет: " + tier.getItemTitle()
        ));
        try {
            String ab = ColorUtil.color("&eПолучена: " + tier.getItemTitle() + " &8(&fНажмите по песку на пляже!&8)");
            recipient.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ab));
        } catch (Throwable ignored) {
        }
    }

    private void tickInfernalRift(PluginConfig cfg, long now) {
        if (minionCooldownSeconds > 0) {
            minionCooldownSeconds--;
        }

        // Если босса не убили в течение 20 минут — он пропадает
        if (now >= eventEndEpochSeconds) {
            Bukkit.broadcastMessage(ColorUtil.color(
                    "&e┠ &#34D711А&#41CF10д&#4EC80Eс&#5BC00Dк&#68B90Bи&#75B10Aй&#81A909-&#8EA207Р&#9B9A06а&#A89304з&#B58B03л&#C28402о&#CF7C00м &fзакончился."
            ));
            stopEvent(false);
            return;
        }

        LivingEntity boss = getBossEntity();
        if (boss == null || boss.isDead() || !boss.isValid()) {
            return;
        }

        if (boss instanceof PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true);
        }

        // Возврат босса к алтарю при уводе слишком далеко
        if (bossHomeLocation != null && boss.getWorld().equals(bossHomeLocation.getWorld())) {
            double maxDist = cfg.getBossLeashRadius();
            if (boss.getLocation().distanceSquared(bossHomeLocation) > maxDist * maxDist) {
                boss.teleport(bossHomeLocation);
                boss.getWorld().playSound(bossHomeLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.6f);
                boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, bossHomeLocation.clone().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.05);
            }
        }

        // Фоновые частицы врат Разлома (оптимизировано против лагов)
        if (riftCenterLocation != null && riftCenterLocation.getWorld() != null) {
            World w = riftCenterLocation.getWorld();
            w.spawnParticle(Particle.PORTAL, riftCenterLocation.clone().add(0, 3.5, -2.0), 6, 1.0, 1.2, 0.3, 0.2);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, riftCenterLocation.clone().add(0, 1.2, 0), 3, 2.0, 0.3, 2.0, 0.01);
        }

        // Способности босса в радиусе 15 блоков
        int castInterval = (bossPhase >= 3)
                ? Math.max(5, cfg.getAbilityCastIntervalSeconds() - 4)
                : cfg.getAbilityCastIntervalSeconds();

        if (secondsElapsed % castInterval == 0) {
            plugin.getAbilityController().castNextRotationAbility(boss);
        }

        // Приспешники появляются тогда, когда игрок подошёл ближе чем на 20 блоков (без сообщений в чат!)
        if (cfg.isMinionsEnabled() && minionCooldownSeconds <= 0) {
            int spawned = plugin.getAbilityController().summonMinions(boss, false);
            if (spawned > 0) {
                minionCooldownSeconds = cfg.getMinionSummonIntervalSeconds();
            }
        }

        updateBossNameAndHealthBar(boss);
        plugin.getTopRightHudManager().updateTopRightHud();
    }

    /**
     * Обрабатывает урон по Боссу, ограничивая засчитанный урон текущим остатком HP (чтобы не было 3501 из 3500).
     */
    public boolean handleBossDamaged(LivingEntity boss, Player attacker, double damageAmount) {
        if (!active || activeEventType != EventType.INFERNAL_RIFT || defeated || damageAmount <= 0) {
            return false;
        }

        double effectiveDamage = Math.min(damageAmount, this.bossCurrentHealth);
        if (attacker != null && effectiveDamage > 0) {
            leaderboard.addDamage(attacker.getUniqueId(), attacker.getName(), effectiveDamage);
        }

        this.bossCurrentHealth = Math.max(0.0, this.bossCurrentHealth - effectiveDamage);
        checkBossEnragePhases(boss);

        if (this.bossCurrentHealth <= 0.0) {
            this.defeated = true;
            boss.setHealth(0.0);
            handleBossDefeated();
            return true;
        } else {
            double entityMax = boss.getMaxHealth();
            double proportional = Math.max(1.0, (this.bossCurrentHealth / this.bossMaxHealth) * entityMax);
            boss.setHealth(Math.min(entityMax, proportional));
            updateBossNameAndHealthBar(boss);
            return false;
        }
    }

    private void checkBossEnragePhases(LivingEntity boss) {
        if (bossMaxHealth <= 0) {
            return;
        }
        double ratio = bossCurrentHealth / bossMaxHealth;

        if (bossPhase == 1 && ratio <= 0.50 && ratio > 0.0) {
            bossPhase = 2;
            Location loc = boss.getLocation();
            World w = loc.getWorld();
            if (w != null) {
                w.playSound(loc, Sound.ENTITY_WITHER_BREAK_BLOCK, 2.0f, 0.7f);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1.5, 0), 90, 1.0, 1.2, 1.0, 0.1);
            }
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 120, 0, false, false));
            plugin.getAbilityController().summonMinions(boss, false);
        } else if (bossPhase == 2 && ratio <= 0.20 && ratio > 0.0) {
            bossPhase = 3;
            Location loc = boss.getLocation();
            World w = loc.getWorld();
            if (w != null) {
                w.strikeLightningEffect(loc);
                w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.5f, 0.6f);
                w.spawnParticle(Particle.LAVA, loc.clone().add(0, 1.5, 0), 60, 1.0, 1.0, 1.0, 0.1);
            }
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 120, 1, false, false));
            plugin.getAbilityController().castGatherAllAbility(boss);
        }
    }

    public void handleBossDefeated() {
        if (!active || activeEventType != EventType.INFERNAL_RIFT) {
            return;
        }
        this.defeated = true;
        if (activeTickTask != null) {
            activeTickTask.cancel();
            activeTickTask = null;
        }

        plugin.getAbilityController().removeAllMinions();
        plugin.getTopRightHudManager().clearAllHud();
        clearAllGpsTrackers();

        if (riftCenterLocation != null && riftCenterLocation.getWorld() != null) {
            World w = riftCenterLocation.getWorld();
            w.playSound(riftCenterLocation, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 0.9f);
            w.playSound(riftCenterLocation, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.7f);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, riftCenterLocation.clone().add(0, 2, 0), 120, 2.0, 2.0, 2.0, 0.12);
        }

        distributeTopRewards();

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        int delaySec = plugin.getPluginConfig().getCleanupDelaySeconds();
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                stopEvent(false);
            }
        }.runTaskLater(plugin, delaySec * 20L);
    }

    /**
     * Выдаёт награды за 1, 2 и 3 место через PlayerPoints без неизвестных символов.
     */
    private void distributeTopRewards() {
        PluginConfig cfg = plugin.getPluginConfig();
        List<DamageLeaderboard.DamageEntry> top = leaderboard.getTop(3);
        String currency = cfg.getCurrencyName();

        Bukkit.broadcastMessage(ColorUtil.color(
                "&e┠ &#34D711А&#41CF10д&#4EC80Eс&#5BC00Dк&#68B90Bи&#75B10Aй&#81A909-&#8EA207Р&#9B9A06а&#A89304з&#B58B03л&#C28402о&#CF7C00м &fзавершён."
        ));

        if (!top.isEmpty()) {
            String[] placeLabels = {"&61 место", "&f2 место", "&c3 место"};
            for (int i = 0; i < top.size(); i++) {
                int place = i + 1;
                DamageLeaderboard.DamageEntry entry = top.get(i);
                int rewardShards = cfg.getRewardForPlace(place);

                if (rewardShards > 0) {
                    plugin.getPlayerPointsHook().giveShards(entry.uuid(), entry.playerName(), rewardShards);
                }

                int dealtDmg = (int) Math.round(entry.damage());
                Bukkit.broadcastMessage(ColorUtil.color(String.format(
                        "&e┠ %s: &#E0C912%s &8(&c%d ❤&8) &8— &#34D711+%d %s",
                        placeLabels[i], entry.playerName(), dealtDmg, rewardShards, currency
                )));

                Player onlineWinner = Bukkit.getPlayer(entry.uuid());
                if (onlineWinner != null && onlineWinner.isOnline()) {
                    onlineWinner.sendTitle(
                            ColorUtil.color("&6&l" + place + " МЕСТО!"),
                            ColorUtil.color("&a+" + rewardShards + " " + currency + " &7(Урон: " + dealtDmg + ")"),
                            10, 80, 20
                    );
                    onlineWinner.playSound(onlineWinner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 0.8f);
                }
            }
        }
    }

    public void stopEvent(boolean distributeRewards) {
        if (activeTickTask != null) {
            activeTickTask.cancel();
            activeTickTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        if (distributeRewards && active && activeEventType == EventType.INFERNAL_RIFT && !defeated) {
            distributeTopRewards();
        }

        LivingEntity boss = getBossEntity();
        if (boss != null && boss.isValid()) {
            boss.remove();
        }
        bossUuid = null;

        LivingEntity villager = getExcavationVillagerEntity();
        if (villager != null && villager.isValid()) {
            villager.remove();
        }
        villagerUuid = null;

        plugin.getBrushLootManager().removeAllEventBrushesFromOnlinePlayers();
        plugin.getAbilityController().removeAllMinions();
        plugin.getSchematicManager().restoreSchematic();
        plugin.getBeachSchematicManager().restoreActiveSchematic();
        plugin.getAlchemistSchematicManager().restoreActiveSchematic();
        plugin.getTopRightHudManager().clearAllHud();
        clearAllGpsTrackers();

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        this.active = false;
        this.defeated = false;
        this.brushReadyToClaim = false;
        this.bossPhase = 1;
        this.riftCenterLocation = null;
        this.bossHomeLocation = null;
        resetNextAutoStartTimer();
    }

    public void updateExcavationVillagerNameAndBar(LivingEntity villager) {
        if (villager == null) {
            return;
        }
        String customName;
        if (brushReadyToClaim || brushCountdownSeconds <= 0) {
            customName = ColorUtil.color("&e&lАрхеолог &8[&aНажмите, чтобы забрать!&8]");
        } else {
            String raw = plugin.getPluginConfig().getExcavationVillagerName()
                    .replace("%sec%", String.valueOf(brushCountdownSeconds));
            customName = ColorUtil.color(raw);
        }
        villager.setCustomName(customName);

        if (bossBar != null && riftCenterLocation != null) {
            long remSec = getSecondsRemainingInEvent();
            long totalSec = plugin.getPluginConfig().getDurationMinutes() * 60L;
            double progress = totalSec > 0 ? Math.max(0.0, Math.min(1.0, (double) remSec / totalSec)) : 1.0;
            bossBar.setProgress(progress);

            String brushStatus = (brushReadyToClaim || brushCountdownSeconds <= 0)
                    ? "&aГотова (Нажмите по Жителю!)"
                    : "&#E0C912" + brushCountdownSeconds + "с";
            String barTitle = String.format(
                    "&e&lПЛЯЖНЫЕ РАСКОПКИ &8| &fКисточка: %s &8| &#E0C912%d %d %d",
                    brushStatus,
                    riftCenterLocation.getBlockX(),
                    riftCenterLocation.getBlockY(),
                    riftCenterLocation.getBlockZ()
            );
            bossBar.setTitle(ColorUtil.color(barTitle));
        }
    }

    public void updateAlchemistNameAndBar(LivingEntity alchemist) {
        if (alchemist == null) {
            return;
        }
        String customName;
        if (brushReadyToClaim || brushCountdownSeconds <= 0) {
            customName = ColorUtil.color("&d&lАлхимик &8[&aРеагент готов — Нажмите!&8]");
        } else {
            customName = ColorUtil.color("&d&lАлхимик &8[&fСдайте реагент &7| &#E0C912" + brushCountdownSeconds + "с&8]");
        }
        alchemist.setCustomName(customName);

        if (bossBar != null && riftCenterLocation != null) {
            long remSec = getSecondsRemainingInEvent();
            long totalSec = plugin.getPluginConfig().getDurationMinutes() * 60L;
            double progress = totalSec > 0 ? Math.max(0.0, Math.min(1.0, (double) remSec / totalSec)) : 1.0;
            bossBar.setProgress(progress);

            int activeNodes = plugin.getAlchemistSchematicManager().getActiveReagentCount();
            String barTitle = String.format(
                    "&d&lКОТЁЛ АЛХИМИКА &8| &fРеагентов на болоте: &a%d шт. &8| &#E0C912%d %d %d",
                    activeNodes,
                    riftCenterLocation.getBlockX(),
                    riftCenterLocation.getBlockY(),
                    riftCenterLocation.getBlockZ()
            );
            bossBar.setTitle(ColorUtil.color(barTitle));
        }
    }

    public void updateBossNameAndHealthBar(LivingEntity boss) {
        if (boss == null) {
            return;
        }
        int curHp = (int) Math.ceil(this.bossCurrentHealth);
        int maxHp = (int) Math.ceil(this.bossMaxHealth);

        String formattedName = plugin.getPluginConfig().getBossName()
                .replace("%hp%", String.valueOf(curHp))
                .replace("%max_hp%", String.valueOf(maxHp));
        boss.setCustomName(ColorUtil.color(formattedName));

        if (bossBar != null && !defeated) {
            double progress = Math.max(0.0, Math.min(1.0, this.bossCurrentHealth / this.bossMaxHealth));
            bossBar.setProgress(progress);
            Location loc = boss.getLocation();
            String barTitle = String.format(
                    "&4&lАДСКИЙ РАЗЛОМ &8| &c❤ %d/%d &8| &#E0C912%d %d %d",
                    curHp, maxHp, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
            );
            bossBar.setTitle(ColorUtil.color(barTitle));
        }
    }

    private void createAndShowBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
        if (activeEventType == EventType.BEACH_EXCAVATION) {
            bossBar = Bukkit.createBossBar(
                    ColorUtil.color("&e&lПЛЯЖНЫЕ РАСКОПКИ &8| &fНажмите по Жителю за кисточкой"),
                    BarColor.YELLOW,
                    BarStyle.SEGMENTED_10
            );
        } else if (activeEventType == EventType.ALCHEMIST_CAULDRON) {
            bossBar = Bukkit.createBossBar(
                    ColorUtil.color("&d&lКОТЁЛ АЛХИМИКА &8| &fСобирайте реагенты и сдавайте Алхимику"),
                    BarColor.PURPLE,
                    BarStyle.SEGMENTED_10
            );
        } else {
            bossBar = Bukkit.createBossBar(
                    ColorUtil.color("&4&lАДСКИЙ РАЗЛОМ &8| &c❤ " + (int) bossCurrentHealth + "/" + (int) bossMaxHealth),
                    BarColor.RED,
                    BarStyle.SEGMENTED_10
            );
        }
        bossBar.setProgress(1.0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getBedrockService().canParticipate(player)) {
                bossBar.addPlayer(player);
            }
        }
    }

    /**
     * Обновляет локатор (/event gps) каждые 5 тиков.
     * Когда игрок подходит ближе чем на 10 блоков к ивенту — локатор молча пропадает (без сообщений в чат).
     */
    @SuppressWarnings("deprecation")
    private void updateGpsTrackerForPlayers() {
        if (!active || riftCenterLocation == null || riftCenterLocation.getWorld() == null) {
            return;
        }
        PluginConfig cfg = plugin.getPluginConfig();
        World eventWorld = riftCenterLocation.getWorld();
        double arrivalDist = cfg.getGpsArrivalDistance();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (bossBar != null && plugin.getBedrockService().canParticipate(player) && !bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }

            UUID uuid = player.getUniqueId();
            if (!gpsTrackedPlayers.contains(uuid)) {
                continue;
            }

            if (!player.getWorld().equals(eventWorld)) {
                BossBar personalBar = gpsBossBars.get(uuid);
                if (personalBar != null) {
                    personalBar.setTitle(ColorUtil.color(
                            "&eЛокатор &8| &fПерейдите в мир &#E0C912" + eventWorld.getName()
                                    + " &8(&#E0C912" + riftCenterLocation.getBlockX() + " " + riftCenterLocation.getBlockY() + " " + riftCenterLocation.getBlockZ() + "&8)"
                    ));
                }
                continue;
            }

            Location targetLoc = (activeEventType == EventType.INFERNAL_RIFT && getBossEntity() != null)
                    ? getBossEntity().getLocation()
                    : riftCenterLocation;
            double dist = player.getLocation().distance(targetLoc);

            if (dist > arrivalDist + 1.5) {
                gpsWasOutsideRadius.add(uuid);
            }

            if (dist <= arrivalDist && gpsWasOutsideRadius.contains(uuid)) {
                disableGpsSilently(player);
                continue;
            }

            player.setCompassTarget(targetLoc);
            String arrow = ColorUtil.getDirectionArrow(player, targetLoc);
            int blocks = (int) Math.round(dist);

            String locatorLine = ColorUtil.color(String.format(
                    "&eЛокатор: &#34D711%s %dм &8| &fКоординаты: &#E0C912%d %d %d",
                    arrow,
                    blocks,
                    riftCenterLocation.getBlockX(),
                    riftCenterLocation.getBlockY(),
                    riftCenterLocation.getBlockZ()
            ));

            BossBar personalBar = gpsBossBars.computeIfAbsent(uuid, k -> {
                BossBar b = Bukkit.createBossBar(locatorLine, BarColor.YELLOW, BarStyle.SOLID);
                b.addPlayer(player);
                return b;
            });
            personalBar.setTitle(locatorLine);
            personalBar.setProgress(Math.max(0.05, Math.min(1.0, 1.0 - (dist / 500.0))));
            if (!personalBar.getPlayers().contains(player)) {
                personalBar.addPlayer(player);
            }

            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(locatorLine));
            } catch (Throwable ignored) {
            }

            if (cfg.isGpsParticleTrail()) {
                spawnGpsDirectionParticles(player, targetLoc);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void disableGpsSilently(Player player) {
        UUID uuid = player.getUniqueId();
        gpsTrackedPlayers.remove(uuid);
        gpsWasOutsideRadius.remove(uuid);
        BossBar bar = gpsBossBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
        } catch (Throwable ignored) {
        }
    }

    private void clearAllGpsTrackers() {
        gpsTrackedPlayers.clear();
        gpsWasOutsideRadius.clear();
        for (BossBar bar : gpsBossBars.values()) {
            bar.removeAll();
        }
        gpsBossBars.clear();
    }

    public void handlePlayerChangedWorld(Player player) {
        boolean hadReagent = plugin.getBrushLootManager().hasAnyReagent(player);
        if (plugin.getBrushLootManager().removeAllEventBrushes(player)) {
            if (hadReagent) {
                player.sendMessage(ColorUtil.color("&e┠ &cВы покинули мир Котла Алхимика — реагенты испарились."));
            } else {
                player.sendMessage(ColorUtil.color("&e┠ &cВы покинули мир раскопок — кисточки пропали."));
            }
        }
        if (!active || riftCenterLocation == null || riftCenterLocation.getWorld() == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!gpsTrackedPlayers.contains(uuid)) {
            return;
        }
        if (player.getWorld().equals(riftCenterLocation.getWorld())) {
            double dist = player.getLocation().distance(riftCenterLocation);
            if (dist > plugin.getPluginConfig().getGpsArrivalDistance()) {
                gpsWasOutsideRadius.add(uuid);
            }
            updateGpsTrackerForPlayers();
        }
    }

    private void spawnGpsDirectionParticles(Player player, Location target) {
        Location eye = player.getLocation().add(0, 1.1, 0);
        Vector dir = target.toVector().subtract(eye.toVector()).setY(0);
        if (dir.lengthSquared() < 0.1) {
            return;
        }
        dir.normalize();
        Location point = eye.clone().add(dir.multiply(2.0));
        player.spawnParticle(Particle.END_ROD, point, 1, 0.02, 0.02, 0.02, 0.0);
    }

    public void togglePlayerGps(Player player) {
        UUID uuid = player.getUniqueId();

        if (gpsTrackedPlayers.contains(uuid)) {
            disableGpsSilently(player);
            player.sendMessage(ColorUtil.color("&e┠ &fЛокатор &#FF4500выключен&f."));
            return;
        }

        gpsTrackedPlayers.add(uuid);

        if (active && riftCenterLocation != null && riftCenterLocation.getWorld() != null) {
            if (player.getWorld().equals(riftCenterLocation.getWorld())) {
                double dist = player.getLocation().distance(riftCenterLocation);
                if (dist > plugin.getPluginConfig().getGpsArrivalDistance()) {
                    gpsWasOutsideRadius.add(uuid);
                } else {
                    gpsWasOutsideRadius.remove(uuid);
                }
            } else {
                gpsWasOutsideRadius.add(uuid);
            }
            updateGpsTrackerForPlayers();
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
        player.sendMessage(ColorUtil.color("&e┠ &fЛокатор &#34D711включён&f."));
    }

    private void broadcastEventStart() {
        PluginConfig cfg = plugin.getPluginConfig();
        List<String> lines = switch (activeEventType) {
            case BEACH_EXCAVATION -> cfg.getExcavationSpawnBroadcastLines();
            case ALCHEMIST_CAULDRON -> cfg.getCauldronSpawnBroadcastLines();
            default -> cfg.getSpawnBroadcastLines();
        };
        for (String line : lines) {
            Bukkit.broadcastMessage(formatEventPlaceholders(line, riftCenterLocation));
        }
    }

    public String formatEventPlaceholders(String rawLine, Location loc) {
        PluginConfig cfg = plugin.getPluginConfig();
        String worldName = (loc != null && loc.getWorld() != null) ? loc.getWorld().getName() : cfg.getWorldName();
        int x = loc != null ? loc.getBlockX() : 0;
        int y = loc != null ? loc.getBlockY() : 0;
        int z = loc != null ? loc.getBlockZ() : 0;
        int curHp = (int) Math.ceil(active ? bossCurrentHealth : cfg.getBossHealth());
        int maxHp = (int) Math.ceil(active ? bossMaxHealth : cfg.getBossHealth());

        String replaced = rawLine
                .replace("%loot%", cfg.getLootRarity())
                .replace("%rarity%", cfg.getLootRarity())
                .replace("%world%", worldName)
                .replace("%x%", String.valueOf(x))
                .replace("%y%", String.valueOf(y))
                .replace("%z%", String.valueOf(z))
                .replace("%hp%", String.valueOf(curHp))
                .replace("%max_hp%", String.valueOf(maxHp))
                .replace("%damage%", String.valueOf((int) cfg.getBossDamage()))
                .replace("%time%", ColorUtil.formatDurationVerbose(getSecondsUntilNextStart()))
                .replace("%remaining%", ColorUtil.formatDurationVerbose(getSecondsRemainingInEvent()))
                .replace("%r1%", String.valueOf(cfg.getRewardFirstPlace()))
                .replace("%r2%", String.valueOf(cfg.getRewardSecondPlace()))
                .replace("%r3%", String.valueOf(cfg.getRewardThirdPlace()))
                .replace("%currency%", cfg.getCurrencyName());

        return ColorUtil.color(replaced);
    }

    private Location findSafeSurfaceLocation(World world, PluginConfig cfg) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int minR = cfg.getMinRadius();
        int maxR = cfg.getMaxRadius();

        for (int attempt = 0; attempt < cfg.getMaxSpawnAttempts(); attempt++) {
            double angle = rnd.nextDouble(0, 2 * Math.PI);
            double dist = rnd.nextDouble(minR, maxR);
            int x = cfg.getCenterX() + (int) Math.round(Math.cos(angle) * dist);
            int z = cfg.getCenterZ() + (int) Math.round(Math.sin(angle) * dist);

            if (!world.getWorldBorder().isInside(new Location(world, x, 64, z))) {
                continue;
            }

            Block highest = world.getHighestBlockAt(x, z);
            Material mat = highest.getType();
            if (mat.isAir() || mat == Material.WATER || mat == Material.LAVA || mat == Material.MAGMA_BLOCK) {
                continue;
            }
            if (mat.name().contains("LEAVES")) {
                continue;
            }
            int y = highest.getY();
            if (y <= world.getMinHeight() + 5 || y >= world.getMaxHeight() - 15) {
                continue;
            }
            return new Location(world, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    public boolean isBoss(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (bossUuid != null && bossUuid.equals(entity.getUniqueId())) {
            return true;
        }
        return entity.getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE);
    }

    public boolean isExcavationVillager(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (villagerUuid != null && villagerUuid.equals(entity.getUniqueId())) {
            return true;
        }
        return entity.getPersistentDataContainer().has(villagerKey, PersistentDataType.BYTE);
    }

    public LivingEntity getBossEntity() {
        if (bossUuid == null) {
            return null;
        }
        Entity ent = Bukkit.getEntity(bossUuid);
        if (ent instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    public LivingEntity getExcavationVillagerEntity() {
        if (villagerUuid == null) {
            return null;
        }
        Entity ent = Bukkit.getEntity(villagerUuid);
        if (ent instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDefeated() {
        return defeated;
    }

    public EventType getActiveEventType() {
        return activeEventType;
    }

    public boolean isBrushReadyToClaim() {
        return brushReadyToClaim || brushCountdownSeconds <= 0;
    }

    public int getSecondsUntilNextBrush() {
        return Math.max(0, brushCountdownSeconds);
    }

    public int getSecondsElapsed() {
        return secondsElapsed;
    }

    public Location getRiftCenterLocation() {
        return riftCenterLocation;
    }

    public double getBossCurrentHealth() {
        return bossCurrentHealth;
    }

    public double getBossMaxHealth() {
        return bossMaxHealth;
    }

    public DamageLeaderboard getLeaderboard() {
        return leaderboard;
    }

    public long getSecondsUntilNextStart() {
        long now = System.currentTimeMillis() / 1000L;
        return Math.max(0L, nextAutoStartEpochSeconds - now);
    }

    public long getSecondsRemainingInEvent() {
        long now = System.currentTimeMillis() / 1000L;
        return Math.max(0L, eventEndEpochSeconds - now);
    }
}
