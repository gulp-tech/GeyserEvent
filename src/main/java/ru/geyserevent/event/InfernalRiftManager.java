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
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.boss.BossAbilityController;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.util.ColorUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Главный менеджер ивента «Адский Разлом (Вторжение Босса)».
 * Управляет расписанием, спавном схематики в мире world2,
 * Большим Брутальным Пиглином (2м, 3500 HP, 15 урона, Незеритовый топор),
 * фазами ярости, GPS-трекером (/event gps), HUD в правом верхнем углу и наградами PlayerPoints.
 */
public class InfernalRiftManager {

    private final GeyserEventPlugin plugin;
    private final NamespacedKey bossKey;
    private final DamageLeaderboard leaderboard = new DamageLeaderboard();

    /**
     * Игроки, включившие GPS-трекер командой /event gps.
     */
    private final Set<UUID> gpsTrackedPlayers = new HashSet<>();

    private boolean active = false;
    private boolean defeated = false;
    private int bossPhase = 1;

    private Location riftCenterLocation;
    private Location bossHomeLocation;
    private UUID bossUuid;
    private double bossCurrentHealth;
    private double bossMaxHealth;

    private BossBar bossBar;
    private BukkitTask autoStartTask;
    private BukkitTask activeTickTask;
    private BukkitTask cleanupTask;

    private long nextAutoStartEpochSeconds;
    private long eventEndEpochSeconds;
    private int secondsElapsed;

    public InfernalRiftManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "infernal_rift_boss");
        startAutoScheduler();
    }

    /**
     * Запускает таймер автоматического старта ивента.
     */
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
                        startEventRandomInConfiguredWorld();
                    } else {
                        nextAutoStartEpochSeconds = now + 300L;
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void resetNextAutoStartTimer() {
        long now = System.currentTimeMillis() / 1000L;
        this.nextAutoStartEpochSeconds = now + (plugin.getPluginConfig().getIntervalMinutes() * 60L);
    }

    /**
     * Запускает ивент «Адский Разлом» на случайных координатах в мире из config.yml (по умолчанию world2).
     */
    public boolean startEventRandomInConfiguredWorld() {
        PluginConfig cfg = plugin.getPluginConfig();
        String worldName = cfg.getWorldName();
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            plugin.getLogger().warning("Мир '" + worldName + "' не найден! Создайте мир '" + worldName
                    + "' или измените параметр event.world в config.yml (/rift setworld <мир>).");
            return false;
        }

        Location safeSpot = findSafeSurfaceLocation(world, cfg);
        if (safeSpot == null) {
            plugin.getLogger().warning("Не удалось найти безопасную точку спавна в мире '" + worldName + "'.");
            return false;
        }

        return startEventAt(safeSpot);
    }

    /**
     * Запускает ивент «Адский Разлом» в конкретной точке мира.
     */
    public boolean startEventAt(Location targetCenter) {
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
        this.bossPhase = 1;
        this.secondsElapsed = 0;
        this.leaderboard.clear();
        this.riftCenterLocation = targetCenter.getBlock().getLocation().add(0.5, 0, 0.5);

        // 1. Строим схематику Адского Разлома (с защитой портала от телепортации)
        this.bossHomeLocation = plugin.getSchematicManager().pasteSchematic(this.riftCenterLocation);
        if (this.bossHomeLocation == null) {
            this.bossHomeLocation = this.riftCenterLocation.clone().add(0, 1, 2);
        }

        // 2. Спавним Большого Брутального Пиглина (2 метра, 3500 HP, 15 урона, Незеритовый топор)
        this.bossMaxHealth = cfg.getBossHealth();
        this.bossCurrentHealth = this.bossMaxHealth;
        PiglinBrute boss = spawnInfernalPiglinBoss(this.bossHomeLocation);
        this.bossUuid = boss.getUniqueId();

        // 3. Создаём BossBar и HUD в правом верхнем углу
        this.eventEndEpochSeconds = (System.currentTimeMillis() / 1000L) + (cfg.getDurationMinutes() * 60L);
        createAndShowBossBar();
        plugin.getTopRightHudManager().updateTopRightHud();

        // 4. Анонсируем вторжение в фирменном стиле &e┠
        broadcastEventStart();

        // 5. Запускаем основной цикл ивента
        startActiveTickLoop();
        return true;
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

        // Размер 2 метра (Attribute.SCALE)
        BossAbilityController.applyEntityScale(boss, cfg.getBossScale());

        // Скорость, сопротивление откидыванию и базовый урон 15
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

                if (now >= eventEndEpochSeconds) {
                    Bukkit.broadcastMessage(ColorUtil.color("&e┠ &cВремя вторжения истекло! &fБрутальный Владыка скрылся обратно в Адском Разломе."));
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

                // Проверка дистанции увода босса от портала (Leash)
                if (bossHomeLocation != null && boss.getWorld().equals(bossHomeLocation.getWorld())) {
                    double maxDist = cfg.getBossLeashRadius();
                    if (boss.getLocation().distanceSquared(bossHomeLocation) > maxDist * maxDist) {
                        boss.teleport(bossHomeLocation);
                        boss.getWorld().playSound(bossHomeLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.6f);
                        boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, bossHomeLocation.clone().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.05);
                    }
                }

                // Фоновые частицы вокруг портала Адского Разлома
                if (riftCenterLocation != null && riftCenterLocation.getWorld() != null) {
                    World w = riftCenterLocation.getWorld();
                    w.spawnParticle(Particle.PORTAL, riftCenterLocation.clone().add(0, 3.5, -2.0), 18, 1.2, 1.5, 0.3, 0.25);
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, riftCenterLocation.clone().add(0, 1.2, 0), 6, 2.5, 0.3, 2.5, 0.01);
                }

                // В 3-й фазе ярости (< 20% HP) способности срабатывают чаще
                int castInterval = (bossPhase >= 3)
                        ? Math.max(5, cfg.getAbilityCastIntervalSeconds() - 4)
                        : cfg.getAbilityCastIntervalSeconds();

                if (secondsElapsed % castInterval == 0) {
                    plugin.getAbilityController().castNextRotationAbility(boss);
                }

                // Периодический призыв приспешников
                if (cfg.isMinionsEnabled() && secondsElapsed % cfg.getMinionSummonIntervalSeconds() == 0) {
                    plugin.getAbilityController().summonMinions(boss);
                }

                // Обновляем BossBar, HUD в правом верхнем углу и GPS-трекер (/event gps)
                updateBossNameAndHealthBar(boss);
                plugin.getTopRightHudManager().updateTopRightHud();
                updateGpsTrackerForPlayers();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Обрабатывает получение урона Боссом (с учётом пула 3500 HP, фаз ярости и таблицы урона игроков).
     */
    public boolean handleBossDamaged(LivingEntity boss, Player attacker, double damageAmount) {
        if (!active || defeated || damageAmount <= 0) {
            return false;
        }

        if (attacker != null) {
            leaderboard.addDamage(attacker.getUniqueId(), attacker.getName(), damageAmount);
        }

        this.bossCurrentHealth = Math.max(0.0, this.bossCurrentHealth - damageAmount);

        // Проверка переходов в фазы ярости (50% и 20% HP)
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
            plugin.getAbilityController().summonMinions(boss);
            Bukkit.broadcastMessage(ColorUtil.color("&e┠ &4&l🔥 ФАЗА II: &fУ Брутального Пиглина осталось меньше &c50% ❤&f! Он впал в ярость и призвал стражу!"));
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
            Bukkit.broadcastMessage(ColorUtil.color("&e┠ &4&l⚡ ФАЗА III: &fУ Босса осталось меньше &c20% ❤&f! Разлом нестабилен — способности ускорены!"));
        }
    }

    /**
     * Завершает ивент победой игроков, выдаёт 3000 / 2000 / 1000 осколков через PlayerPoints
     * и запускает таймер отката схематики.
     */
    public void handleBossDefeated() {
        if (!active) {
            return;
        }
        this.defeated = true;
        if (activeTickTask != null) {
            activeTickTask.cancel();
            activeTickTask = null;
        }

        plugin.getAbilityController().removeAllMinions();
        plugin.getTopRightHudManager().clearAllHud();
        gpsTrackedPlayers.clear();

        if (riftCenterLocation != null && riftCenterLocation.getWorld() != null) {
            World w = riftCenterLocation.getWorld();
            w.playSound(riftCenterLocation, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 0.9f);
            w.playSound(riftCenterLocation, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.7f);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, riftCenterLocation.clone().add(0, 2, 0), 120, 2.0, 2.0, 2.0, 0.12);
        }

        distributeTopRewards();

        if (bossBar != null) {
            bossBar.setProgress(0.0);
            bossBar.setColor(BarColor.GREEN);
            bossBar.setTitle(ColorUtil.color("&a&l✔ АДСКИЙ РАЗЛОМ ЗАКРЫТ! &7Топ-3 получили осколки!"));
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
     * Выдаёт награды за 1, 2 и 3 место по нанесённому урону через PlayerPoints:
     * 1 место — 3000 осколков
     * 2 место — 2000 осколков
     * 3 место — 1000 осколков
     */
    private void distributeTopRewards() {
        PluginConfig cfg = plugin.getPluginConfig();
        List<DamageLeaderboard.DamageEntry> top = leaderboard.getTop(3);
        String currency = cfg.getCurrencyName();

        Bukkit.broadcastMessage(ColorUtil.color("&e┏ &8&m──────────────────────────────────────────"));
        Bukkit.broadcastMessage(ColorUtil.color("&e┠ &#34D711А&#41CF10д&#4EC80Eс&#5BC00Dк&#68B90Bи&#75B10Aй&#81A909-&#8EA207Р&#9B9A06а&#A89304з&#B58B03л&#C28402о&#CF7C00м &fзавершён! Босс повержен!"));

        if (top.isEmpty()) {
            Bukkit.broadcastMessage(ColorUtil.color("&e┠ &cНикто из игроков не нанёс урона боссу."));
        } else {
            String[] medals = {"&6🥇 1 место", "&f🥈 2 место", "&c🥉 3 место"};
            for (int i = 0; i < top.size(); i++) {
                int place = i + 1;
                DamageLeaderboard.DamageEntry entry = top.get(i);
                int rewardShards = cfg.getRewardForPlace(place);

                if (rewardShards > 0) {
                    plugin.getPlayerPointsHook().giveShards(entry.uuid(), entry.playerName(), rewardShards);
                }

                double percent = (bossMaxHealth > 0) ? Math.min(100.0, (entry.damage() / bossMaxHealth) * 100.0) : 0.0;
                Bukkit.broadcastMessage(ColorUtil.color(String.format(
                        "&e┠ %s: &#E0C912%s &8— &c%.0f ❤ &7(%.1f%%) &8» &#34D711+%d %s",
                        medals[i], entry.playerName(), entry.damage(), percent, rewardShards, currency
                )));

                Player onlineWinner = Bukkit.getPlayer(entry.uuid());
                if (onlineWinner != null && onlineWinner.isOnline()) {
                    onlineWinner.sendTitle(
                            ColorUtil.color("&6&l🏆 " + place + " МЕСТО!"),
                            ColorUtil.color("&a+" + rewardShards + " " + currency + " &7(Урон: " + (int) entry.damage() + ")"),
                            10, 80, 20
                    );
                    onlineWinner.playSound(onlineWinner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 0.8f);
                }
            }
        }

        Bukkit.broadcastMessage(ColorUtil.color("&e┗ &8&m──────────────────────────────────────────"));
    }

    /**
     * Полностью останавливает активный ивент, удаляет босса/приспешников, скрывает HUD и откатывает схематику.
     */
    public void stopEvent(boolean distributeRewards) {
        if (activeTickTask != null) {
            activeTickTask.cancel();
            activeTickTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        if (distributeRewards && active && !defeated) {
            distributeTopRewards();
        }

        LivingEntity boss = getBossEntity();
        if (boss != null && boss.isValid()) {
            boss.remove();
        }
        bossUuid = null;

        plugin.getAbilityController().removeAllMinions();
        plugin.getSchematicManager().restoreSchematic();
        plugin.getTopRightHudManager().clearAllHud();
        gpsTrackedPlayers.clear();

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        this.active = false;
        this.defeated = false;
        this.bossPhase = 1;
        this.riftCenterLocation = null;
        this.bossHomeLocation = null;
        resetNextAutoStartTimer();
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
        bossBar = Bukkit.createBossBar(
                ColorUtil.color("&4&lАДСКИЙ РАЗЛОМ &8| &cВторжение Босса"),
                BarColor.RED,
                BarStyle.SEGMENTED_10,
                BarFlag.PLAY_BOSS_MUSIC
        );
        bossBar.setProgress(1.0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getBedrockService().canParticipate(player)) {
                bossBar.addPlayer(player);
            }
        }
    }

    /**
     * Обновляет GPS-трекер (/event gps) для игроков, которые находятся в мире ивента.
     * Если игрок подошёл ближе чем на 10 блоков (arrival-distance: 10.0) —
     * трекер автоматически отключается и выводится информация об ивенте!
     */
    @SuppressWarnings("deprecation")
    private void updateGpsTrackerForPlayers() {
        if (bossBar == null || riftCenterLocation == null || riftCenterLocation.getWorld() == null) {
            return;
        }
        PluginConfig cfg = plugin.getPluginConfig();
        World eventWorld = riftCenterLocation.getWorld();
        double arrivalDist = cfg.getGpsArrivalDistance();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getBedrockService().canParticipate(player)) {
                bossBar.removePlayer(player);
                continue;
            }
            if (!bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }

            UUID uuid = player.getUniqueId();
            if (!gpsTrackedPlayers.contains(uuid)) {
                continue;
            }

            // Трекер работает и отображается тогда, когда игрок находится в мире ивента
            if (!player.getWorld().equals(eventWorld)) {
                continue;
            }

            Location targetLoc = getBossEntity() != null ? getBossEntity().getLocation() : riftCenterLocation;
            double dist = player.getLocation().distance(targetLoc);

            // Если игрок подошёл ближе чем на 10 блоков — отключаем трекер и показываем инфо об ивенте!
            if (dist <= arrivalDist) {
                gpsTrackedPlayers.remove(uuid);
                sendGpsArrivalEventInfo(player);
                continue;
            }

            String arrow = ColorUtil.getDirectionArrow(player, targetLoc);
            String actionbarText = ColorUtil.color(String.format(
                    "&e🧭 GPS: &#34D711%s %dм &8| &fКоординаты: &#E0C912%d %d %d &8| &c❤ %d",
                    arrow,
                    (int) Math.round(dist),
                    riftCenterLocation.getBlockX(),
                    riftCenterLocation.getBlockY(),
                    riftCenterLocation.getBlockZ(),
                    (int) Math.ceil(bossCurrentHealth)
            ));

            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbarText));
            } catch (Throwable ignored) {
            }

            // Визуальная дорожка путеводных частиц перед игроком
            if (cfg.isGpsParticleTrail()) {
                spawnGpsDirectionParticles(player, targetLoc);
            }
        }
    }

    /**
     * Вызывается, когда игрок с включённым /event gps подошёл ближе чем на 10 блоков к ивенту.
     */
    @SuppressWarnings("deprecation")
    private void sendGpsArrivalEventInfo(Player player) {
        PluginConfig cfg = plugin.getPluginConfig();
        Location loc = riftCenterLocation != null ? riftCenterLocation : player.getLocation();

        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1.5f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f, 1.1f);

        try {
            player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(ColorUtil.color("&#34D711✔ Вы прибыли к Адскому Разлому! GPS-трекер отключён."))
            );
        } catch (Throwable ignored) {
        }

        for (String rawLine : cfg.getGpsArrivedInfoLines()) {
            player.sendMessage(formatEventPlaceholders(rawLine, loc));
        }
    }

    /**
     * Вызывается при переходе игрока между мирами (PlayerChangedWorldEvent).
     * Если у игрока стоял /event gps и он перешёл в мир ивента — активирует трекер!
     */
    public void handlePlayerChangedWorld(Player player) {
        if (!active || riftCenterLocation == null || riftCenterLocation.getWorld() == null) {
            return;
        }
        if (!gpsTrackedPlayers.contains(player.getUniqueId())) {
            return;
        }
        if (player.getWorld().equals(riftCenterLocation.getWorld())) {
            int dist = (int) Math.round(player.getLocation().distance(riftCenterLocation));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.3f);
            player.sendMessage(ColorUtil.color("&e┠ &fВы перешли в мир ивента! &#34D711GPS-трекер активирован &7(До цели: &#E0C912" + dist + "м&7)."));
        }
    }

    private void spawnGpsDirectionParticles(Player player, Location target) {
        Location eye = player.getLocation().add(0, 1.1, 0);
        Vector dir = target.toVector().subtract(eye.toVector()).setY(0);
        if (dir.lengthSquared() < 0.1) {
            return;
        }
        dir.normalize();
        for (double step = 1.5; step <= 3.5; step += 1.0) {
            Location point = eye.clone().add(dir.clone().multiply(step));
            player.spawnParticle(Particle.END_ROD, point, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /**
     * Включает или выключает GPS-трекер (/event gps) для игрока.
     */
    public void togglePlayerGps(Player player) {
        UUID uuid = player.getUniqueId();
        PluginConfig cfg = plugin.getPluginConfig();

        if (gpsTrackedPlayers.contains(uuid)) {
            gpsTrackedPlayers.remove(uuid);
            player.sendMessage(ColorUtil.color("&e┠ &fGPS-трекер &#FF4500отключён&f."));
            return;
        }

        if (!active || riftCenterLocation == null || riftCenterLocation.getWorld() == null) {
            gpsTrackedPlayers.add(uuid);
            player.sendMessage(ColorUtil.color("&e┠ &fGPS-трекер &#34D711поставлен в ожидание&f!"));
            player.sendMessage(ColorUtil.color("&e┠ &fОн автоматически включится, когда начнётся ивент в мире &#E0C912" + cfg.getWorldName() + "&f."));
            return;
        }

        World eventWorld = riftCenterLocation.getWorld();
        if (player.getWorld().equals(eventWorld)) {
            double dist = player.getLocation().distance(riftCenterLocation);
            if (dist <= cfg.getGpsArrivalDistance()) {
                sendGpsArrivalEventInfo(player);
                return;
            }
            gpsTrackedPlayers.add(uuid);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f, 1.5f);
            player.sendMessage(ColorUtil.color("&e┠ &fGPS-трекер &#34D711включён&f! Идите по стрелке (&#E0C912"
                    + (int) Math.round(dist) + "м&f). При приближении ближе 10 блоков трекер отключится."));
        } else {
            gpsTrackedPlayers.add(uuid);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f, 1.2f);
            player.sendMessage(ColorUtil.color("&e┠ &fGPS-трекер &#34D711установлен&f!"));
            player.sendMessage(ColorUtil.color("&e┠ &fТрекер появится автоматически, когда вы перейдёте в мир &#E0C912" + eventWorld.getName() + "&f."));
        }
    }

    /**
     * Анонсирует появление ивента в чате в фирменном стиле &e┠.
     */
    private void broadcastEventStart() {
        PluginConfig cfg = plugin.getPluginConfig();
        for (String line : cfg.getSpawnBroadcastLines()) {
            Bukkit.broadcastMessage(formatEventPlaceholders(line, riftCenterLocation));
        }

        String worldName = riftCenterLocation.getWorld().getName();
        int x = riftCenterLocation.getBlockX();
        int y = riftCenterLocation.getBlockY();
        int z = riftCenterLocation.getBlockZ();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getBedrockService().canParticipate(player)) {
                player.sendTitle(
                        ColorUtil.color("&#34D711&lАДСКИЙ РАЗЛОМ"),
                        ColorUtil.color("&fКоординаты: &#E0C912" + x + " " + y + " " + z + " &7(" + worldName + ")"),
                        10, 70, 20
                );
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.7f);
            }
        }
    }

    /**
     * Подставляет плейсхолдеры (%loot%, %x%, %y%, %z%, %world%, %hp%, %max_hp%, %damage%, %time%, %remaining%) в строку.
     */
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

    public boolean isActive() {
        return active;
    }

    public boolean isDefeated() {
        return defeated;
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
