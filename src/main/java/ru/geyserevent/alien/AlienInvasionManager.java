package ru.geyserevent.alien;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.boss.BossAbilityController;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.event.DamageLeaderboard;
import ru.geyserevent.schematic.RiftSchematicManager;
import ru.geyserevent.util.ColorUtil;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ивент «Вторжение Пришельцев» (v1.20.0).
 *
 * <p>На схеме-посадке 25x25 появляется кастомный босс-пришелец (по умолчанию эвокер
 * с перекрашенной в ресурспаке текстурой) и малые пришельцы-приспешники.
 * После убийства босса на 4 пьедесталах открываются сундуки с ресурсами.</p>
 *
 * <p>Всё настраивается в {@code config.yml} → секция {@code alien}: тип и статы босса,
 * тип и статы приспешников, интервал способностей, смещения сундуков и их лут.</p>
 *
 * <p>На экране игроков ничего нового не рисуется: только BossBar сверху (имя и HP
 * пришельца) и GPS-локатор в action bar — как у остальных ивентов.</p>
 */
public final class AlienInvasionManager {

    /** Файл схемы в plugins/GeyserEvent/schematics/. */
    public static final String SCHEMATIC_FILE = "alien_landing.schem.yml";

    private final GeyserEventPlugin plugin;
    private final RiftSchematicManager schematicManager;
    private final NamespacedKey alienBossKey;
    private final NamespacedKey alienMinionKey;
    private final NamespacedKey alienChestKey;
    private final DamageLeaderboard leaderboard = new DamageLeaderboard();
    private final Random random = new Random();

    private boolean active;
    private boolean defeated;
    private Location center;
    private Location bossHome;
    private UUID bossUuid;
    private double bossCurrentHealth;
    private double bossMaxHealth;
    private int bossPhase = 1;
    private long eventEndEpochSeconds;
    private int secondsElapsed;
    private int abilityCooldownSeconds;
    private int minionCooldownSeconds;
    private final Set<Location> spawnedChests = ConcurrentHashMap.newKeySet();
    private final Set<UUID> alienMinions = ConcurrentHashMap.newKeySet();

    private BossBar bossBar;
    private BukkitRunnable tickTask;
    private BukkitRunnable cleanupTask;

    public AlienInvasionManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.schematicManager = new RiftSchematicManager(plugin, SCHEMATIC_FILE);
        this.alienBossKey = new NamespacedKey(plugin, "alien_boss");
        this.alienMinionKey = new NamespacedKey(plugin, "alien_minion");
        this.alienChestKey = new NamespacedKey(plugin, "alien_chest");
    }

    // ------------------------------------------------------------------ запуск

    /**
     * Запускает ивент в точке {@code targetCenter}.
     *
     * @param durationSecondsOverride своя длительность в секундах (null — из конфига)
     * @return true, если ивент стартовал
     */
    public boolean startEvent(Location targetCenter, Integer durationSecondsOverride) {
        PluginConfig cfg = plugin.getPluginConfig();
        if (!cfg.isAlienEnabled()) {
            plugin.getLogger().warning("Ивент «Вторжение Пришельцев» выключен в конфиге (alien.enabled: false).");
            return false;
        }
        if (targetCenter == null || targetCenter.getWorld() == null) {
            return false;
        }
        // Временный ивент Разлома и Пришельцы не идут одновременно
        if (plugin.getRiftManager().isActive()) {
            plugin.getRiftManager().stopEvent(false);
        }
        stopEvent();

        targetCenter.getChunk().load(true);

        this.active = true;
        this.defeated = false;
        this.bossPhase = 1;
        this.secondsElapsed = 0;
        this.abilityCooldownSeconds = cfg.getAlienAbilityIntervalSeconds();
        this.minionCooldownSeconds = Math.max(3, cfg.getAlienMinionIntervalSeconds() / 2);
        this.leaderboard.clear();
        this.spawnedChests.clear();
        this.alienMinions.clear();
        this.center = snapToSurface(targetCenter);

        long durationSeconds = (durationSecondsOverride != null && durationSecondsOverride > 0)
                ? durationSecondsOverride
                : (cfg.getAlienDurationMinutes() * 60L);
        this.eventEndEpochSeconds = (System.currentTimeMillis() / 1000L) + durationSeconds;

        setChunksForceLoaded(this.center, true);

        // 1. Строим схему-посадку и получаем точку появления пришельца
        this.bossHome = schematicManager.pasteSchematic(this.center, cfg.getAlienProtectionRadius() + 2, 8);
        if (this.bossHome == null) {
            this.bossHome = this.center.clone().add(0, 1, 0);
        }

        // 2. Спавним босса-пришельца
        LivingEntity boss = spawnAlienBoss(this.bossHome);
        this.bossUuid = boss.getUniqueId();

        // 3. BossBar сверху + сообщения в чат
        createBossBar(boss);
        broadcastLines(cfg.getAlienSpawnLines(), this.center);

        // 4. Сразу пара малых пришельцев, чтобы ивент не выглядел пустым
        spawnMinionWave(true);

        startTickLoop();
        return true;
    }

    /**
     * Прижимаем центр к поверхности (как «Адский Разлом»): посадка не должна
     * висеть в воздухе или уходить под землю.
     */
    private Location snapToSurface(Location raw) {
        Location base = raw.clone();
        World world = base.getWorld();
        if (world == null) {
            return base;
        }
        Block highest = world.getHighestBlockAt(base.getBlockX(), base.getBlockZ());
        if (highest != null && !highest.getType().isAir()
                && highest.getY() > world.getMinHeight() + 5
                && highest.getY() < world.getMaxHeight() - 12) {
            base.setY(highest.getY());
        }
        return base.getBlock().getLocation().add(0.5, 0, 0.5);
    }

    private LivingEntity spawnAlienBoss(Location loc) {
        PluginConfig cfg = plugin.getPluginConfig();
        World world = loc.getWorld();
        if (world == null) {
            throw new IllegalStateException("Мир ивента пришельцев не найден");
        }

        Entity spawned = world.spawnEntity(loc, cfg.getAlienBossType());
        if (!(spawned instanceof LivingEntity boss)) {
            throw new IllegalStateException("Тип босса " + cfg.getAlienBossType() + " не является живым существом");
        }

        boss.getPersistentDataContainer().set(alienBossKey, PersistentDataType.BYTE, (byte) 1);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(false);
        boss.setCanPickupItems(false);
        boss.setCustomNameVisible(true);
        boss.setGlowing(false);

        this.bossMaxHealth = cfg.getAlienBossHealth();
        this.bossCurrentHealth = this.bossMaxHealth;

        BossAbilityController.applyEntityScale(boss, cfg.getAlienBossScale());
        BossAbilityController.setEntityAttribute(boss, "MOVEMENT_SPEED", "generic.movement_speed", cfg.getAlienBossMovementSpeed());
        BossAbilityController.setEntityAttribute(boss, "KNOCKBACK_RESISTANCE", "generic.knockback_resistance", cfg.getAlienBossKnockbackResistance());
        BossAbilityController.setEntityAttribute(boss, "ATTACK_DAMAGE", "generic.attack_damage", cfg.getAlienBossDamage());
        BossAbilityController.setEntityAttribute(boss, "FOLLOW_RANGE", "generic.follow_range", 32.0);
        BossAbilityController.setEntityAttribute(boss, "MAX_HEALTH", "generic.max_health", this.bossMaxHealth);
        boss.setHealth(boss.getMaxHealth());

        Material weapon = cfg.getAlienBossWeapon();
        if (weapon != null && weapon != Material.AIR && boss.getEquipment() != null) {
            ItemStack item = new ItemStack(weapon);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setUnbreakable(true);
                item.setItemMeta(meta);
            }
            boss.getEquipment().setItemInMainHand(item);
            boss.getEquipment().setItemInMainHandDropChance(0.0f);
        }

        // Броня босса: нетэрит-броня по всем слотам (бессмертная, без дропов).
        if (cfg.isAlienBossArmorEnabled() && boss.getEquipment() != null) {
            equipArmor(boss);
        }

        updateBossName(boss);

        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.6f);
        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.4f);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1.0, 0), 120, 0.6, 1.0, 0.6, 0.02);
        return boss;
    }

    private void createBossBar(LivingEntity boss) {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        bossBar = Bukkit.createBossBar(formatBossTitle(boss), BarColor.PURPLE, BarStyle.SEGMENTED_12);
        bossBar.setProgress(1.0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getBedrockService().canParticipate(player)) {
                bossBar.addPlayer(player);
            }
        }
    }

    private String formatBossTitle(LivingEntity boss) {
        PluginConfig cfg = plugin.getPluginConfig();
        int hp = (int) Math.ceil(bossCurrentHealth);
        int max = (int) Math.ceil(bossMaxHealth);
        return ColorUtil.color(cfg.getAlienBossName()
                .replace("%hp%", String.valueOf(hp))
                .replace("%max_hp%", String.valueOf(max)));
    }

    private void updateBossName(LivingEntity boss) {
        if (boss == null || boss.isDead()) {
            return;
        }
        boss.setCustomName(formatBossTitle(boss));
        double entityMax = boss.getMaxHealth();
        double proportional = Math.max(1.0, (bossCurrentHealth / bossMaxHealth) * entityMax);
        boss.setHealth(Math.min(entityMax, proportional));
        if (bossBar != null) {
            bossBar.setTitle(formatBossTitle(boss));
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, bossCurrentHealth / bossMaxHealth)));
        }
    }

    // ------------------------------------------------------------------ приспешники

    private int spawnMinionWave(boolean force) {
        PluginConfig cfg = plugin.getPluginConfig();
        if (!cfg.isAlienMinionsEnabled() || bossUuid == null) {
            return 0;
        }
        LivingEntity boss = getBossEntity();
        if (boss == null || boss.isDead() || boss.getWorld() == null) {
            return 0;
        }
        Location bossLoc = boss.getLocation();

        List<Player> nearby = plugin.getAbilityController()
                .getTargetPlayersInRadius(bossLoc, cfg.getAlienMinionTriggerRadius());
        if (!force && nearby.isEmpty()) {
            return 0;
        }

        int alive = countAlienMinions();
        int maxAllowed = cfg.getAlienMaxActiveAlienMinions();
        if (alive >= maxAllowed) {
            return 0;
        }
        int toSpawn = Math.min(cfg.getAlienMinionCountPerWave(), maxAllowed - alive);

        int spawned = 0;
        for (int i = 0; i < toSpawn; i++) {
            double angle = (2 * Math.PI * i) / toSpawn + (random.nextDouble() * 0.6);
            double radius = 2.0 + random.nextDouble() * 1.5;
            Location spawnLoc = bossLoc.clone().add(Math.cos(angle) * radius, 0.3, Math.sin(angle) * radius);
            LivingEntity minion = plugin.getAbilityController().spawnAlienMinion(
                    cfg.getAlienMinionType(), spawnLoc, cfg.getAlienMinionHealth(), cfg.getAlienMinionDamage(),
                    cfg.getAlienMinionScale(), cfg.getAlienMinionName(), cfg.getAlienMinionWeapon(),
                    nearby, alienMinionKey);
            if (minion != null) {
                alienMinions.add(minion.getUniqueId());
                spawned++;
                World w = spawnLoc.getWorld();
                if (w != null) {
                    w.spawnParticle(Particle.PORTAL, spawnLoc.clone().add(0, 0.8, 0), 30, 0.3, 0.5, 0.3, 0.4);
                }
            }
        }
        if (spawned > 0) {
            World w = bossLoc.getWorld();
            w.playSound(bossLoc, Sound.BLOCK_PORTAL_TRAVEL, 0.8f, 1.3f);
            w.playSound(bossLoc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.4f, 1.1f);
        }
        return spawned;
    }

    /** Сколько «малых пришельцев» этого ивента сейчас живо. */
    public int countAlienMinions() {
        alienMinions.removeIf(uuid -> {
            Entity entity = Bukkit.getEntity(uuid);
            return entity == null || entity.isDead() || !entity.isValid();
        });
        return alienMinions.size();
    }

    // ------------------------------------------------------------------ урон и смерть

    /**
     * Урон по боссу-пришельцу: ведёт лидерборд и фазы, при смерти открывает сундуки.
     *
     * @return true, если босс погиб от этого удара
     */
    public boolean handleBossDamaged(LivingEntity boss, Player attacker, double damageAmount) {
        if (!active || defeated || damageAmount <= 0) {
            return false;
        }

        double effectiveDamage = Math.min(damageAmount, bossCurrentHealth);
        if (attacker != null && effectiveDamage > 0) {
            leaderboard.addDamage(attacker.getUniqueId(), attacker.getName(), effectiveDamage);
        }

        this.bossCurrentHealth = Math.max(0.0, this.bossCurrentHealth - effectiveDamage);
        checkPhases(boss);

        if (this.bossCurrentHealth <= 0.0) {
            this.defeated = true;
            if (boss != null && boss.isValid()) {
                boss.remove();
            }
            handleBossDefeated();
            return true;
        }
        updateBossName(boss);
        return false;
    }

    private void checkPhases(LivingEntity boss) {
        if (bossMaxHealth <= 0 || boss == null || boss.isDead()) {
            return;
        }
        double ratio = bossCurrentHealth / bossMaxHealth;
        PluginConfig cfg = plugin.getPluginConfig();

        if (bossPhase == 1 && ratio <= 0.50 && ratio > 0.0) {
            bossPhase = 2;
            Location loc = boss.getLocation();
            if (loc.getWorld() != null) {
                loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_BREAK_BLOCK, 2.0f, 0.7f);
            }
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 120, 0, false, false));
            spawnMinionWave(true);
        } else if (bossPhase == 2 && ratio <= 0.20 && ratio > 0.0) {
            bossPhase = 3;
            Location loc = boss.getLocation();
            if (loc.getWorld() != null) {
                loc.getWorld().strikeLightningEffect(loc);
                loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.5f, 0.5f);
            }
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 120, 1, false, false));
            plugin.getAbilityController().castGatherAllAbility(boss);
            spawnMinionWave(true);
        }
        // Чем меньше HP, тем чаще пришелец зовёт подкрепление
        int interval = Math.max(6, (int) Math.round(cfg.getAlienMinionIntervalSeconds() * ratio));
        this.minionCooldownSeconds = Math.min(this.minionCooldownSeconds, interval);
    }

    /** Босс убит: награды, сундуки и отложенная очистка схемы. */
    public void handleBossDefeated() {
        if (!active) {
            return;
        }
        this.defeated = true;
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        removeAlienEntities();
        plugin.getAbilityController().removeAllMinions();
        plugin.getRiftManager().clearAllGpsTrackers();

        if (center != null && center.getWorld() != null) {
            World w = center.getWorld();
            w.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 0.9f);
            w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.7f);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 2, 0), 4, 0.5, 0.5, 0.5, 0.0);
        }

        broadcastLines(plugin.getPluginConfig().getAlienDefeatedLines(), center);
        if (plugin.getPluginConfig().isAlienRewardsEnabled()) {
            distributeRewards();
        }

        // 4 сундука с ресурсами на пьедесталах
        int opened = openAlienChests();
        if (opened > 0) {
            Bukkit.broadcastMessage(plugin.getPluginConfig().getAlienChestsLine());
        }

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        // Сундуки остаются нетронутыми alien.chests.loot-time-seconds секунд,
        // затем посадочная площадка откатывается.
        int delaySec = plugin.getPluginConfig().getAlienChestLootSeconds();
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                stopEvent();
            }
        };
        cleanupTask.runTaskLater(plugin, delaySec * 20L);
    }

    private void distributeRewards() {
        List<DamageLeaderboard.DamageEntry> top = leaderboard.getTop(3);
        PluginConfig cfg = plugin.getPluginConfig();
        if (top.isEmpty()) {
            return;
        }
        String[] labels = {"&61 место", "&f2 место", "&c3 место"};
        for (int i = 0; i < top.size(); i++) {
            DamageLeaderboard.DamageEntry entry = top.get(i);
            int dealt = (int) Math.round(entry.damage());
            int reward = cfg.getRewardForPlace(i + 1);
            Bukkit.broadcastMessage(ColorUtil.color(String.format(
                    "&e┼ %s: &#E0C912%s &8(&c%d ❤&8)%s",
                    labels[i], entry.playerName(), dealt,
                    reward > 0 ? " &7+&a" + reward + " " + cfg.getCurrencyName() : "")));

            Player online = Bukkit.getPlayer(entry.uuid());
            if (online != null && online.isOnline()) {
                online.sendTitle(
                        ColorUtil.color("&6&l" + (i + 1) + " МЕСТО!"),
                        ColorUtil.color(reward > 0
                                ? "&7Награда: &a" + reward + " " + cfg.getCurrencyName()
                                : "&7Нанесено урона: &c" + dealt + " ❤"),
                        10, 80, 20);
                online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 0.8f);
            }
            if (reward > 0) {
                boolean given = plugin.getPlayerPointsHook() != null
                        && plugin.getPlayerPointsHook().giveShards(entry.uuid(), entry.playerName(), reward);
                if (!given) {
                    plugin.getLogger().warning("Не удалось выдать " + reward + " "
                            + cfg.getCurrencyName() + " игроку " + entry.playerName());
                }
            }
        }
    }

    // ------------------------------------------------------------------ сундуки

    /**
     * Ставит и набивает сундуки с ресурсами в точках из конфига (alien.chests.offsets).
     *
     * @return сколько сундуков открылось
     */
    private int openAlienChests() {
        PluginConfig cfg = plugin.getPluginConfig();
        if (!cfg.isAlienChestsEnabled() || center == null || center.getWorld() == null) {
            return 0;
        }
        int opened = 0;
        for (int[] offset : cfg.getAlienChestOffsets()) {
            Location loc = center.clone().add(offset[0], offset[1], offset[2]);
            Block block = loc.getBlock();
            block.setType(Material.CHEST, true);
            if (block.getState() instanceof Chest chest) {
                chest.getPersistentDataContainer().set(alienChestKey, PersistentDataType.BYTE, (byte) 1);
                fillChest(chest.getInventory(), cfg.getAlienChestItemsPerChest(), cfg.getAlienChestLoot());
                chest.update(true, false);
            }
            spawnedChests.add(loc);
            opened++;
            World w = loc.getWorld();
            w.playSound(loc, Sound.BLOCK_CHEST_OPEN, 1.2f, 1.0f);
            w.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 1.2, 0.5), 25, 0.4, 0.3, 0.4, 0.0);
        }
        return opened;
    }

    /**
     * Надевает на босса нетэрит-броню по всем слотам: предметы бессмертные,
     * дроп с них — ноль (броня остаётся на боссе при смерти).
     */
    private void equipArmor(LivingEntity boss) {
        org.bukkit.inventory.EntityEquipment equip = boss.getEquipment();
        if (equip == null) {
            return;
        }
        equip.setHelmet(unbreakableArmor(Material.NETHERITE_HELMET));
        equip.setHelmetDropChance(0.0f);
        equip.setChestplate(unbreakableArmor(Material.NETHERITE_CHESTPLATE));
        equip.setChestplateDropChance(0.0f);
        equip.setLeggings(unbreakableArmor(Material.NETHERITE_LEGGINGS));
        equip.setLeggingsDropChance(0.0f);
        equip.setBoots(unbreakableArmor(Material.NETHERITE_BOOTS));
        equip.setBootsDropChance(0.0f);
    }

    private static org.bukkit.inventory.ItemStack unbreakableArmor(Material material) {
        org.bukkit.inventory.ItemStack armor = new org.bukkit.inventory.ItemStack(material);
        org.bukkit.inventory.meta.ItemMeta meta = armor.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            armor.setItemMeta(meta);
        }
        return armor;
    }

    private void fillChest(org.bukkit.inventory.Inventory inventory, int rolls,
                           List<PluginConfig.AlienLootItem> loot) {
        // Гарантированный базовый лут: в каждом сундуке обязательно есть хотя бы
        // один предмет из списка base-loot (не зависит от шансов и роллов).
        for (PluginConfig.AlienLootItem item : plugin.getPluginConfig().getAlienChestBaseLoot()) {
            int min = item.minAmount();
            int max = Math.max(min, item.maxAmount());
            int amount = min + (max > min ? random.nextInt(max - min + 1) : 0);
            org.bukkit.inventory.ItemStack stack = new org.bukkit.inventory.ItemStack(item.material(), Math.max(1, amount));
            inventory.addItem(stack);
        }
        if (loot.isEmpty() || inventory == null) {
            return;
        }
        for (int i = 0; i < rolls; i++) {
            PluginConfig.AlienLootItem item = loot.get(random.nextInt(loot.size()));
            if (item.chancePercent() < 100.0 && random.nextDouble() * 100.0 > item.chancePercent()) {
                continue;
            }
            int min = item.minAmount();
            int max = Math.max(min, item.maxAmount());
            int amount = min + (max > min ? random.nextInt(max - min + 1) : 0);
            ItemStack stack = new ItemStack(item.material(), Math.max(1, amount));
            if (item.displayName() != null || !item.lore().isEmpty()) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    if (item.displayName() != null) {
                        meta.setDisplayName(item.displayName());
                    }
                    if (!item.lore().isEmpty()) {
                        meta.setLore(item.lore());
                    }
                    stack.setItemMeta(meta);
                }
            }
            inventory.addItem(stack);
        }
    }

    public boolean isAlienChest(Location loc) {
        if (loc == null || loc.getBlock().getType() != Material.CHEST) {
            return false;
        }
        if (loc.getBlock().getState() instanceof Chest chest) {
            return chest.getPersistentDataContainer().has(alienChestKey, PersistentDataType.BYTE);
        }
        return false;
    }

    // ------------------------------------------------------------------ стоп

    public void stopEvent() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        removeAlienEntities();
        plugin.getAbilityController().removeAllMinions();
        schematicManager.restoreSchematic();
        plugin.getRiftManager().clearAllGpsTrackers();

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        this.active = false;
        this.defeated = false;
        this.bossPhase = 1;
        this.bossUuid = null;
        this.spawnedChests.clear();
        this.alienMinions.clear();
        this.center = null;
        this.bossHome = null;
    }

    private void removeAlienEntities() {
        if (center != null && center.getWorld() != null) {
            World w = center.getWorld();
            for (Entity entity : w.getNearbyEntities(center, 64, 48, 64)) {
                if (isAlienBoss(entity) || isAlienMinion(entity)) {
                    entity.remove();
                }
            }
            setChunksForceLoaded(center, false);
        }
        LivingEntity boss = getBossEntity();
        if (boss != null) {
            boss.remove();
        }
        this.bossUuid = null;
    }

    // ------------------------------------------------------------------ цикл

    private void startTickLoop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void tick() {
        if (!active) {
            return;
        }
        PluginConfig cfg = plugin.getPluginConfig();
        LivingEntity boss = getBossEntity();

        // Босс пропал не через нашу логику (убит командой, выгружен) — ивент завершаем
        if (boss == null || boss.isDead()) {
            if (!defeated) {
                handleBossDefeated();
            }
            return;
        }

        this.secondsElapsed++;
        updateBossName(boss);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (bossBar != null && plugin.getBedrockService().canParticipate(player)
                    && !bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }

        // Способности пришельца (общий контроллер способностей босса)
        if (--this.abilityCooldownSeconds <= 0) {
            this.abilityCooldownSeconds = cfg.getAlienAbilityIntervalSeconds();
            plugin.getAbilityController().castNextRotationAbility(boss);
        }

        // Волны малых пришельцев
        if (cfg.isAlienMinionsEnabled() && --this.minionCooldownSeconds <= 0) {
            this.minionCooldownSeconds = cfg.getAlienMinionIntervalSeconds();
            spawnMinionWave(false);
        }

        // Таймаут: пришелец улетает на корабле
        long now = System.currentTimeMillis() / 1000L;
        if (now >= eventEndEpochSeconds) {
            Bukkit.broadcastMessage(cfg.getAlienTimeoutLine());
            stopEvent();
        }
    }

    // ------------------------------------------------------------------ утилиты

    private void broadcastLines(List<String> lines, Location loc) {
        PluginConfig cfg = plugin.getPluginConfig();
        for (String line : lines) {
            String text = line
                    .replace("%x%", loc == null ? "?" : String.valueOf(loc.getBlockX()))
                    .replace("%y%", loc == null ? "?" : String.valueOf(loc.getBlockY()))
                    .replace("%z%", loc == null ? "?" : String.valueOf(loc.getBlockZ()))
                    .replace("%world%", loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "?")
                    .replace("%hp%", String.valueOf((int) Math.ceil(bossCurrentHealth)))
                    .replace("%max_hp%", String.valueOf((int) Math.ceil(bossMaxHealth)))
                    .replace("%currency%", cfg.getCurrencyName());
            Bukkit.broadcastMessage(ColorUtil.color(text));
        }
    }

    private void setChunksForceLoaded(Location center, boolean forceLoaded) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        World w = center.getWorld();
        int baseCx = center.getBlockX() >> 4;
        int baseCz = center.getBlockZ() >> 4;
        for (int cx = baseCx - 1; cx <= baseCx + 1; cx++) {
            for (int cz = baseCz - 1; cz <= baseCz + 1; cz++) {
                Chunk ch = w.getChunkAt(cx, cz);
                if (forceLoaded && !ch.isLoaded()) {
                    ch.load(true);
                }
                ch.setForceLoaded(forceLoaded);
            }
        }
    }

    public boolean isAlienBoss(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(alienBossKey, PersistentDataType.BYTE);
    }

    public boolean isAlienMinion(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(alienMinionKey, PersistentDataType.BYTE);
    }

    /**
     * Структура ивента: ломать её во время боя нельзя. После убийства босса
     * защита снимается, чтобы игроки могли вскрыть сундуки с ресурсами.
     */
    public boolean isProtectedBlock(Location loc) {
        if (!active || defeated || loc == null || loc.getWorld() == null || center == null) {
            return false;
        }
        if (!loc.getWorld().equals(center.getWorld())) {
            return false;
        }
        int r = plugin.getPluginConfig().getAlienProtectionRadius();
        double dx = Math.abs(loc.getX() - center.getX());
        double dy = Math.abs(loc.getY() - center.getY());
        double dz = Math.abs(loc.getZ() - center.getZ());
        return dx <= r && dz <= r && dy <= 12;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDefeated() {
        return defeated;
    }

    public Location getCenterLocation() {
        return center;
    }

    public Location getBossHomeLocation() {
        return bossHome;
    }

    public LivingEntity getBossEntity() {
        if (bossUuid == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(bossUuid);
        return entity instanceof LivingEntity living && !living.isDead() ? living : null;
    }

    public double getBossCurrentHealth() {
        return bossCurrentHealth;
    }

    public double getBossMaxHealth() {
        return bossMaxHealth;
    }

    public int getSecondsElapsed() {
        return secondsElapsed;
    }

    public long getSecondsRemaining() {
        return Math.max(0, eventEndEpochSeconds - (System.currentTimeMillis() / 1000L));
    }

    public DamageLeaderboard getLeaderboard() {
        return leaderboard;
    }

    public RiftSchematicManager getSchematicManager() {
        return schematicManager;
    }
}
