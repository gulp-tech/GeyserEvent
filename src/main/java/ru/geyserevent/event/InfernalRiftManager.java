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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
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
 * его способностями и выдачей наград через PlayerPoints (3000 / 2000 / 1000 осколков).
 */
public class InfernalRiftManager {

    private final GeyserEventPlugin plugin;
    private final NamespacedKey bossKey;
    private final DamageLeaderboard leaderboard = new DamageLeaderboard();
    private final Set<UUID> compassEnabledPlayers = new HashSet<>();

    private boolean active = false;
    private boolean defeated = false;
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
        PluginConfig cfg = plugin.getPluginConfig();
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
                        // Откладываем старт на 5 минут, если недостаточно Bedrock-игроков
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
        World world = targetCenter.getWorld();
        targetCenter.getChunk().load(true);

        this.active = true;
        this.defeated = false;
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

        // 3. Создаём BossBar для игроков Bedrock
        this.eventEndEpochSeconds = (System.currentTimeMillis() / 1000L) + (cfg.getDurationMinutes() * 60L);
        createAndShowBossBar();

        // 4. Анонсируем вторжение всем игрокам
        broadcastEventStart();

        // 5. Запускаем основной цикл ивента (способности в радиусе 15 блоков, призыв приспешников, HUD)
        startActiveTickLoop();
        return true;
    }

    /**
     * Создаёт и настраивает босса — Большого Брутального Пиглина (2 метра).
     */
    private PiglinBrute spawnInfernalPiglinBoss(Location loc) {
        PluginConfig cfg = plugin.getPluginConfig();
        World world = loc.getWorld();

        Entity spawned = world.spawnEntity(loc, EntityType.PIGLIN_BRUTE);
        PiglinBrute boss = (PiglinBrute) spawned;

        // Помечаем PDC-ключом босса
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);

        // Отключаем зомбификацию в обычном мире (world2)!
        boss.setImmuneToZombification(true);
        boss.setAdult();
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);
        boss.setCanPickupItems(false);
        boss.setCustomNameVisible(true);
        boss.setGlowing(true);

        // Размер 2 метра (Attribute.SCALE в Purpur 26.2)
        BossAbilityController.applyEntityScale(boss, cfg.getBossScale());

        // Скорость, сопротивление откидыванию и базовый урон 15
        BossAbilityController.setEntityAttribute(boss, "MOVEMENT_SPEED", "generic.movement_speed", cfg.getBossMovementSpeed());
        BossAbilityController.setEntityAttribute(boss, "KNOCKBACK_RESISTANCE", "generic.knockback_resistance", cfg.getBossKnockbackResistance());
        BossAbilityController.setEntityAttribute(boss, "ATTACK_DAMAGE", "generic.attack_damage", cfg.getBossDamage());
        BossAbilityController.setEntityAttribute(boss, "FOLLOW_RANGE", "generic.follow_range", 32.0);

        // Устанавливаем здоровье (поддерживает 3500 HP даже если в spigot.yml лимит 2048)
        BossAbilityController.setEntityAttribute(boss, "MAX_HEALTH", "generic.max_health", cfg.getBossHealth());
        boss.setHealth(boss.getMaxHealth());

        // Оружие: Незеритовый топор
        EntityEquipment equip = boss.getEquipment();
        if (equip != null) {
            equip.setItemInMainHand(BossAbilityController.createBossAxe(cfg));
            equip.setItemInMainHandDropChance(0.0f);
        }

        updateBossNameAndHealthBar(boss);

        // Эффект появления босса из Разлома
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

                // Проверка истечения времени ивента
                if (now >= eventEndEpochSeconds) {
                    broadcastMessage("&c&lВремя вторжения истекло! &7Брутальный Владыка скрылся обратно в Адском Разломе...");
                    stopEvent(false);
                    return;
                }

                LivingEntity boss = getBossEntity();
                if (boss == null || boss.isDead() || !boss.isValid()) {
                    return;
                }

                // Гарантируем, что пиглин никогда не зомбифицируется в мире world2
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

                // Использование способностей босса (притягивание, отталкивание, стяжка всех в 1 точку в радиусе 15 блоков)
                if (secondsElapsed % cfg.getAbilityCastIntervalSeconds() == 0) {
                    plugin.getAbilityController().castNextRotationAbility(boss);
                }

                // Периодический призыв приспешников
                if (cfg.isMinionsEnabled() && secondsElapsed % cfg.getMinionSummonIntervalSeconds() == 0) {
                    plugin.getAbilityController().summonMinions(boss);
                }

                // Обновляем BossBar и ActionBar-компас для Bedrock игроков
                updateBossNameAndHealthBar(boss);
                updateHudForBedrockPlayers();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Обрабатывает получение урона Боссом (с учётом пула 3500 HP и таблицы урона игроков).
     *
     * @return true, если босс был убит этим ударом
     */
    public boolean handleBossDamaged(LivingEntity boss, Player attacker, double damageAmount) {
        if (!active || defeated || damageAmount <= 0) {
            return false;
        }

        if (attacker != null) {
            leaderboard.addDamage(attacker.getUniqueId(), attacker.getName(), damageAmount);
        }

        this.bossCurrentHealth = Math.max(0.0, this.bossCurrentHealth - damageAmount);

        if (this.bossCurrentHealth <= 0.0) {
            this.defeated = true;
            boss.setHealth(0.0);
            handleBossDefeated();
            return true;
        } else {
            // Синхронизируем полоску здоровья сущности пропорционально пулу 3500 HP
            double entityMax = boss.getMaxHealth();
            double proportional = Math.max(1.0, (this.bossCurrentHealth / this.bossMaxHealth) * entityMax);
            boss.setHealth(Math.min(entityMax, proportional));
            updateBossNameAndHealthBar(boss);
            return false;
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

        Bukkit.broadcastMessage(ColorUtil.color("&8&m===================================================="));
        Bukkit.broadcastMessage(ColorUtil.color(" &4&l🔥 ВЛАДЫКА АДСКОГО РАЗЛОМА ПОВЕРЖЕН! 🔥"));
        Bukkit.broadcastMessage(ColorUtil.color(" &7Брутальный Пиглин уничтожен! Награды за топ урона:"));
        Bukkit.broadcastMessage("");

        if (top.isEmpty()) {
            Bukkit.broadcastMessage(ColorUtil.color(" &cНикто из игроков не нанёс урона боссу."));
        } else {
            String[] medals = {"&6&l🥇 1 место", "&f&l🥈 2 место", "&c&l🥉 3 место"};
            for (int i = 0; i < top.size(); i++) {
                int place = i + 1;
                DamageLeaderboard.DamageEntry entry = top.get(i);
                int rewardShards = cfg.getRewardForPlace(place);

                if (rewardShards > 0) {
                    plugin.getPlayerPointsHook().giveShards(entry.uuid(), entry.playerName(), rewardShards);
                }

                double percent = (bossMaxHealth > 0) ? Math.min(100.0, (entry.damage() / bossMaxHealth) * 100.0) : 0.0;
                Bukkit.broadcastMessage(ColorUtil.color(String.format(
                        " %s: &e%s &8— &c%.0f ❤ &7(%.1f%%) &8» &d&l+%d %s",
                        medals[i], entry.playerName(), entry.damage(), percent, rewardShards, currency
                )));

                Player onlineWinner = Bukkit.getPlayer(entry.uuid());
                if (onlineWinner != null && onlineWinner.isOnline()) {
                    onlineWinner.sendTitle(
                            ColorUtil.color("&6&l🏆 " + place + " МЕСТО!"),
                            ColorUtil.color("&d+" + rewardShards + " " + currency + " &7(Урон: " + (int) entry.damage() + ")"),
                            10, 80, 20
                    );
                    onlineWinner.playSound(onlineWinner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 0.8f);
                }
            }
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ColorUtil.color("&8&m===================================================="));
    }

    /**
     * Полностью останавливает активный ивент, удаляет босса/приспешников и откатывает схематику.
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

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        this.active = false;
        this.defeated = false;
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
                    "&4&lАДСКИЙ РАЗЛОМ &8| &c❤ %d/%d &8| &e%s &7(%d, %d, %d)",
                    curHp, maxHp, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
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

    @SuppressWarnings("deprecation")
    private void updateHudForBedrockPlayers() {
        if (bossBar == null || riftCenterLocation == null) {
            return;
        }
        boolean sendActionbar = plugin.getPluginConfig().isActionbarCompass();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getBedrockService().canParticipate(player)) {
                bossBar.removePlayer(player);
                continue;
            }
            if (!bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }

            if (sendActionbar && (compassEnabledPlayers.contains(player.getUniqueId())
                    || player.getWorld().equals(riftCenterLocation.getWorld()))) {
                String hud;
                if (player.getWorld().equals(riftCenterLocation.getWorld())) {
                    int dist = (int) player.getLocation().distance(riftCenterLocation);
                    String arrow = ColorUtil.getDirectionArrow(player, riftCenterLocation);
                    int myDmg = (int) leaderboard.getDamage(player.getUniqueId());
                    int rank = leaderboard.getPlayerRank(player.getUniqueId());
                    String rankStr = rank > 0 ? " (#" + rank + ")" : "";
                    hud = ColorUtil.color(String.format(
                            "&4&lРазлом: &e%s %dм &8| &c❤ %d &8| &6Урон: &f%d%s",
                            arrow, dist, (int) Math.ceil(bossCurrentHealth), myDmg, rankStr
                    ));
                } else {
                    hud = ColorUtil.color("&4&lАдский Разлом &7активен в мире &e" + riftCenterLocation.getWorld().getName()
                            + " &7(" + riftCenterLocation.getBlockX() + ", " + riftCenterLocation.getBlockY() + ", " + riftCenterLocation.getBlockZ() + ")");
                }
                try {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(hud));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void broadcastEventStart() {
        PluginConfig cfg = plugin.getPluginConfig();
        String worldName = riftCenterLocation.getWorld().getName();
        int x = riftCenterLocation.getBlockX();
        int y = riftCenterLocation.getBlockY();
        int z = riftCenterLocation.getBlockZ();

        Bukkit.broadcastMessage(ColorUtil.color("&8&m===================================================="));
        Bukkit.broadcastMessage(ColorUtil.color(" &4&l🔥 ИВЕНТ: АДСКИЙ РАЗЛОМ (ВТОРЖЕНИЕ БОССА) 🔥"));
        Bukkit.broadcastMessage(ColorUtil.color(" &7В мире &e" + worldName + " &7открылся портал Адского Разлома!"));
        Bukkit.broadcastMessage(ColorUtil.color(" &7Координаты: &6X: " + x + " Y: " + y + " Z: " + z));
        Bukkit.broadcastMessage(ColorUtil.color(" &7Босс: &c&lБольшой Брутальный Пиглин (2м) &8[&c" + (int) cfg.getBossHealth() + " ❤&8]"));
        Bukkit.broadcastMessage(ColorUtil.color(" &7Награды за урон: &61м: &d" + cfg.getRewardFirstPlace()
                + " &7| &f2м: &d" + cfg.getRewardSecondPlace()
                + " &7| &c3м: &d" + cfg.getRewardThirdPlace() + " " + cfg.getCurrencyName()));
        Bukkit.broadcastMessage(ColorUtil.color(" &eМеню ивента и навигатор (Bedrock): &6/rift"));
        Bukkit.broadcastMessage(ColorUtil.color("&8&m===================================================="));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getBedrockService().canParticipate(player)) {
                player.sendTitle(
                        ColorUtil.color("&4&lАДСКИЙ РАЗЛОМ"),
                        ColorUtil.color("&eМир " + worldName + ": &6" + x + ", " + y + ", " + z),
                        10, 70, 20
                );
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.7f);
            }
        }
    }

    private void broadcastMessage(String msg) {
        Bukkit.broadcastMessage(plugin.getPluginConfig().getPrefix() + ColorUtil.color(msg));
    }

    /**
     * Ищет безопасную точку на поверхности земли в указанном мире (world2).
     */
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

    public boolean toggleCompass(Player player) {
        UUID uuid = player.getUniqueId();
        if (compassEnabledPlayers.contains(uuid)) {
            compassEnabledPlayers.remove(uuid);
            return false;
        } else {
            compassEnabledPlayers.add(uuid);
            return true;
        }
    }

    public boolean isCompassEnabled(Player player) {
        return compassEnabledPlayers.contains(player.getUniqueId());
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
