package ru.geyserevent.boss;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.util.ColorUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Контроллер способностей Большого Брутального Пиглина и его приспешников.
 * - Способности действуют в радиусе 15 блоков от босса.
 * - Приспешники появляются только когда игрок подходит ближе чем на 20 блоков (без сообщений в чат).
 */
public class BossAbilityController {

    private final GeyserEventPlugin plugin;
    private final NamespacedKey minionKey;
    private final List<UUID> activeMinions = new ArrayList<>();
    private int rotationIndex = 0;

    public BossAbilityController(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.minionKey = new NamespacedKey(plugin, "rift_minion");
    }

    public void castNextRotationAbility(LivingEntity boss) {
        if (boss == null || boss.isDead() || !boss.isValid()) {
            return;
        }
        PluginConfig cfg = plugin.getPluginConfig();
        List<BossAbilityType> enabled = new ArrayList<>();
        if (cfg.isPullEnabled()) enabled.add(BossAbilityType.PULL);
        if (cfg.isPushEnabled()) enabled.add(BossAbilityType.PUSH);
        if (cfg.isGatherEnabled()) enabled.add(BossAbilityType.GATHER);

        if (enabled.isEmpty()) {
            return;
        }

        List<Player> targets = getTargetPlayersInRadius(boss.getLocation(), cfg.getAbilityRadius());
        if (targets.isEmpty()) {
            return;
        }

        BossAbilityType chosen = enabled.get(rotationIndex % enabled.size());
        rotationIndex = (rotationIndex + 1) % enabled.size();
        executeAbility(boss, chosen);
    }

    public int executeAbility(LivingEntity boss, BossAbilityType type) {
        if (boss == null || boss.isDead() || !boss.isValid()) {
            return 0;
        }
        return switch (type) {
            case PULL -> castPullAbility(boss);
            case PUSH -> castPushAbility(boss);
            case GATHER -> castGatherAllAbility(boss);
            case SUMMON_MINIONS -> summonMinions(boss, true);
        };
    }

    /**
     * 1. СПОСОБНОСТЬ: ПРИТЯГИВАНИЕ (в радиусе 15 блоков от босса).
     */
    public int castPullAbility(LivingEntity boss) {
        PluginConfig cfg = plugin.getPluginConfig();
        Location bossLoc = boss.getLocation();
        World world = bossLoc.getWorld();
        if (world == null) {
            return 0;
        }

        List<Player> targets = getTargetPlayersInRadius(bossLoc, cfg.getAbilityRadius());
        if (targets.isEmpty()) {
            return 0;
        }

        boss.swingMainHand();
        world.playSound(bossLoc, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 2.0f, 0.6f);
        world.playSound(bossLoc, Sound.BLOCK_CHAIN_BREAK, 2.0f, 0.5f);
        // Оптимизация FPS (v1.9.8): 45 -> 22 частицы (на Bedrock-телефонах большое количество
        // частиц в одной точке — самая частая причина просадок FPS)
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, bossLoc.clone().add(0, 1.2, 0), 22, 0.8, 0.8, 0.8, 0.08);

        double horizStrength = cfg.getPullHorizontalStrength();
        double vertBoost = cfg.getPullVerticalBoost();
        double damage = cfg.getPullDamage();
        int slowTicks = cfg.getPullSlownessTicks();

        for (Player target : targets) {
            Location playerLoc = target.getLocation();
            drawParticleLineForPlayer(target, bossLoc.clone().add(0, 1.3, 0), playerLoc.clone().add(0, 1.0, 0), Particle.SOUL_FIRE_FLAME, 0.7);

            Vector toBoss = bossLoc.toVector().subtract(playerLoc.toVector());
            toBoss.setY(0);
            if (toBoss.lengthSquared() > 0.01) {
                toBoss.normalize().multiply(horizStrength);
            }
            toBoss.setY(vertBoost);
            target.setVelocity(toBoss);

            if (damage > 0) {
                target.damage(damage);
            }
            if (slowTicks > 0) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 1, false, false, true));
            }
            target.playSound(playerLoc, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 0.7f);
            sendActionBar(target, "§6§l⚡ §eБосс притянул вас к себе!");
        }

        return targets.size();
    }

    /**
     * 2. СПОСОБНОСТЬ: ОТТАЛКИВАНИЕ (в радиусе 15 блоков от босса).
     */
    public int castPushAbility(LivingEntity boss) {
        PluginConfig cfg = plugin.getPluginConfig();
        Location bossLoc = boss.getLocation();
        World world = bossLoc.getWorld();
        if (world == null) {
            return 0;
        }

        List<Player> targets = getTargetPlayersInRadius(bossLoc, cfg.getAbilityRadius());
        if (targets.isEmpty()) {
            return 0;
        }

        boss.swingMainHand();
        world.playSound(bossLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.75f);
        world.playSound(bossLoc, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 2.0f, 0.5f);

        spawnShockwaveRing(bossLoc.clone().add(0, 0.3, 0), Math.min(cfg.getAbilityRadius(), 15.0));

        double horizStrength = cfg.getPushHorizontalStrength();
        double vertBoost = cfg.getPushVerticalBoost();
        double damage = cfg.getPushDamage();

        for (Player target : targets) {
            Location playerLoc = target.getLocation();
            Vector fromBoss = playerLoc.toVector().subtract(bossLoc.toVector());
            fromBoss.setY(0);
            if (fromBoss.lengthSquared() < 0.01) {
                fromBoss = new Vector(
                        ThreadLocalRandom.current().nextDouble(-1.0, 1.0),
                        0,
                        ThreadLocalRandom.current().nextDouble(-1.0, 1.0)
                );
            }
            fromBoss.normalize().multiply(horizStrength).setY(vertBoost);
            target.setVelocity(fromBoss);

            if (damage > 0) {
                target.damage(damage);
            }
            world.spawnParticle(Particle.LAVA, playerLoc.clone().add(0, 1.0, 0), 10, 0.3, 0.3, 0.3, 0.05);
            sendActionBar(target, "§c§l✦ §eУдарная волна отбросила вас!");
        }

        return targets.size();
    }

    /**
     * 3. СПОСОБНОСТЬ: СОБРАТЬ ВСЕХ В ОДНО МЕСТО (в радиусе 15 блоков от босса).
     */
    public int castGatherAllAbility(LivingEntity boss) {
        PluginConfig cfg = plugin.getPluginConfig();
        Location bossLoc = boss.getLocation();
        World world = bossLoc.getWorld();
        if (world == null) {
            return 0;
        }

        List<Player> targets = getTargetPlayersInRadius(bossLoc, cfg.getAbilityRadius());
        if (targets.isEmpty()) {
            return 0;
        }

        boss.swingMainHand();

        Vector lookDir = bossLoc.getDirection().setY(0);
        if (lookDir.lengthSquared() < 0.01) {
            lookDir = new Vector(0, 0, 1);
        } else {
            lookDir.normalize();
        }

        Location gatherPoint = bossLoc.clone().add(lookDir.multiply(cfg.getGatherDistanceInFront()));
        gatherPoint.setY(bossLoc.getY());
        if (gatherPoint.getBlock().getType().isSolid()) {
            gatherPoint.add(0, 1.0, 0);
            if (gatherPoint.getBlock().getType().isSolid()) {
                gatherPoint = bossLoc.clone().add(0, 0.2, 0);
            }
        }

        world.playSound(gatherPoint, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 2.0f, 0.5f);
        world.playSound(gatherPoint, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.6f);
        world.spawnParticle(Particle.PORTAL, gatherPoint.clone().add(0, 1.0, 0), 40, 0.6, 0.8, 0.6, 0.35);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, gatherPoint.clone().add(0, 0.5, 0), 20, 0.4, 0.4, 0.4, 0.04);

        double damage = cfg.getGatherDamage();
        int slowTicks = cfg.getGatherSlownessTicks();

        for (Player target : targets) {
            Location oldLoc = target.getLocation();
            drawParticleLineForPlayer(target, oldLoc.clone().add(0, 1.0, 0), gatherPoint.clone().add(0, 1.0, 0), Particle.PORTAL, 0.7);

            Location destination = gatherPoint.clone();
            destination.setYaw(oldLoc.getYaw());
            destination.setPitch(oldLoc.getPitch());

            target.teleport(destination);
            target.setVelocity(new Vector(0, -0.15, 0));

            if (damage > 0) {
                target.damage(damage);
            }
            if (slowTicks > 0) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 2, false, false, true));
            }
            target.playSound(destination, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.2f, 0.6f);
            sendActionBar(target, "§d§l✦ §fБосс стянул всех в одну точку!");
        }

        if (boss instanceof Mob mob && !targets.isEmpty()) {
            mob.setTarget(targets.get(0));
        }

        return targets.size();
    }

    /**
     * 4. СПОСОБНОСТЬ: ПРИЗЫВ ПРИСПЕШНИКОВ.
     * Срабатывает ТОЛЬКО если хотя бы один игрок подошёл ближе чем на 20 блоков к боссу (или если вызвано админом force=true).
     * Никаких сообщений в чат не отправляется!
     */
    public int summonMinions(LivingEntity boss, boolean force) {
        PluginConfig cfg = plugin.getPluginConfig();
        if (!cfg.isMinionsEnabled() || boss == null || boss.isDead()) {
            return 0;
        }

        Location bossLoc = boss.getLocation();
        World world = bossLoc.getWorld();
        if (world == null) {
            return 0;
        }

        // Проверяем наличие игроков ближе чем на 20 блоков от босса
        List<Player> playersWithin20 = getTargetPlayersInRadius(bossLoc, cfg.getMinionTriggerRadius());
        if (!force && playersWithin20.isEmpty()) {
            return 0;
        }

        cleanupDeadMinions();
        int currentCount = activeMinions.size();
        int maxAllowed = cfg.getMaxActiveMinions();
        if (currentCount >= maxAllowed) {
            return 0;
        }

        int toSpawn = Math.min(cfg.getMinionCountPerWave(), maxAllowed - currentCount);
        int spawned = 0;

        for (int i = 0; i < toSpawn; i++) {
            double angle = (2 * Math.PI * i) / toSpawn;
            double offsetX = Math.cos(angle) * 2.5;
            double offsetZ = Math.sin(angle) * 2.5;
            Location spawnLoc = bossLoc.clone().add(offsetX, 0.2, offsetZ);

            Entity entity = world.spawnEntity(spawnLoc, cfg.getMinionEntityType());
            if (entity instanceof LivingEntity minion) {
                configureMinion(minion, playersWithin20);
                activeMinions.add(minion.getUniqueId());
                world.spawnParticle(Particle.FLAME, spawnLoc.clone().add(0, 1.0, 0), 12, 0.3, 0.5, 0.3, 0.05);
                world.spawnParticle(Particle.LARGE_SMOKE, spawnLoc.clone().add(0, 1.0, 0), 8, 0.3, 0.5, 0.3, 0.02);
                spawned++;
            }
        }

        if (spawned > 0) {
            world.playSound(bossLoc, Sound.BLOCK_PORTAL_TRAVEL, 0.7f, 1.4f);
            world.playSound(bossLoc, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1.5f, 0.8f);
        }

        return spawned;
    }

    private void configureMinion(LivingEntity minion, List<Player> nearbyPlayers) {
        PluginConfig cfg = plugin.getPluginConfig();
        minion.getPersistentDataContainer().set(minionKey, PersistentDataType.BYTE, (byte) 1);
        minion.setRemoveWhenFarAway(false);
        minion.setCustomNameVisible(true);

        if (minion instanceof PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true);
            piglin.setAdult();
        }

        double hp = cfg.getMinionHealth();
        setEntityAttribute(minion, "MAX_HEALTH", "generic.max_health", hp);
        minion.setHealth(Math.min(hp, minion.getMaxHealth()));
        setEntityAttribute(minion, "ATTACK_DAMAGE", "generic.attack_damage", cfg.getMinionDamage());
        applyEntityScale(minion, cfg.getMinionScale());
        updateMinionName(minion);

        EntityEquipment equip = minion.getEquipment();
        if (equip != null) {
            ItemStack axe = new ItemStack(cfg.getMinionWeaponMaterial());
            ItemMeta meta = axe.getItemMeta();
            if (meta != null) {
                meta.setUnbreakable(true);
                axe.setItemMeta(meta);
            }
            equip.setItemInMainHand(axe);
            equip.setItemInMainHandDropChance(0.0f);
        }

        if (minion instanceof Mob mob && !nearbyPlayers.isEmpty()) {
            mob.setTarget(nearbyPlayers.get(ThreadLocalRandom.current().nextInt(nearbyPlayers.size())));
        }
    }

    public void updateMinionName(LivingEntity minion) {
        if (minion == null || minion.isDead()) {
            return;
        }
        String raw = plugin.getPluginConfig().getMinionName()
                .replace("%hp%", String.valueOf((int) Math.ceil(minion.getHealth())));
        minion.setCustomName(ColorUtil.color(raw));
    }

    public boolean isMinion(Entity entity) {
        if (entity == null) {
            return false;
        }
        return entity.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE);
    }

    public void cleanupDeadMinions() {
        Iterator<UUID> it = activeMinions.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null || entity.isDead() || !entity.isValid()) {
                it.remove();
            }
        }
    }

    public void removeAllMinions() {
        for (UUID uuid : activeMinions) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        activeMinions.clear();
    }

    public int getActiveMinionCount() {
        cleanupDeadMinions();
        return activeMinions.size();
    }

    public List<Player> getTargetPlayersInRadius(Location center, double radius) {
        List<Player> list = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) {
            return list;
        }
        double radiusSq = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.isDead() || !player.isValid()) {
                continue;
            }
            GameMode gm = player.getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
                continue;
            }
            if (!plugin.getBedrockService().canParticipate(player)) {
                continue;
            }
            if (player.getLocation().distanceSquared(center) <= radiusSq) {
                list.add(player);
            }
        }
        return list;
    }

    /**
     * Невидимость НЕ работает на НПС «Адского Разлома».
     *
     * <p>Ванильный ИИ мобов физически не может навестись на игрока с зельем
     * невидимости, поэтому цель Боссу и Приспешникам выставляется принудительно
     * вручную — они всегда видят и атакуют ближайшего игрока.</p>
     */
    public void forceTargetPlayers(LivingEntity boss) {
        if (!plugin.getPluginConfig().isNpcsIgnoreInvisibility()) {
            return;
        }
        if (boss == null || boss.isDead() || !boss.isValid()) {
            return;
        }

        // Сам Босс тоже не должен становиться невидимым (сплэш/остаточные зелья)
        boss.removePotionEffect(PotionEffectType.INVISIBILITY);

        if (activeMinions.isEmpty()) {
            ensureMobTarget(boss, boss.getLocation(), Math.max(
                    plugin.getPluginConfig().getAbilityRadius(),
                    plugin.getPluginConfig().getMinionTriggerRadius()));
            return;
        }

        // Оптимизация: список кандидатов собираем ОДИН раз на все мобы (босс + приспешники),
        // вместо того чтобы перебирать игроков мира заново для каждого моба.
        PluginConfig cfg = plugin.getPluginConfig();
        double bossRadius = Math.max(cfg.getAbilityRadius(), cfg.getMinionTriggerRadius());
        List<Player> candidates = getTargetPlayersInRadius(boss.getLocation(), bossRadius);
        if (!candidates.isEmpty()) {
            ensureMobTarget(boss, candidates);
        }

        for (UUID uuid : activeMinions) {
            Entity entity = Bukkit.getEntity(uuid);
            if (!(entity instanceof LivingEntity minion) || minion.isDead() || !minion.isValid()) {
                continue;
            }
            minion.removePotionEffect(PotionEffectType.INVISIBILITY);
            if (!candidates.isEmpty()) {
                ensureMobTarget(minion, candidates);
            }
        }
    }

    /**
     * Выставляет мобу цель — ближайшего игрока (невидимого включительно).
     * Если у моба уже есть живая цель-игрок, она не меняется.
     */
    private void ensureMobTarget(LivingEntity mob, List<Player> players) {
        if (!(mob instanceof Mob bukkitMob)) {
            return;
        }
        LivingEntity current = bukkitMob.getTarget();
        if (current != null && current.isValid() && !current.isDead() && current instanceof Player) {
            // У моба уже есть живая цель-игрок (даже если он невидим — бей его!)
            return;
        }
        Player closest = null;
        double closestDistanceSq = Double.MAX_VALUE;
        Location mobLocation = mob.getLocation();
        for (Player player : players) {
            double distanceSq = player.getLocation().distanceSquared(mobLocation);
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closest = player;
            }
        }
        if (closest != null && bukkitMob.getTarget() != closest) {
            bukkitMob.setTarget(closest);
        }
    }

    /**
     * Быстрый вариант для случая «приспешников нет»: ищет ближайшего игрока без
     * создания промежуточных списков.
     */
    private void ensureMobTarget(LivingEntity mob, Location center, double radius) {
        if (!(mob instanceof Mob bukkitMob)) {
            return;
        }
        LivingEntity current = bukkitMob.getTarget();
        if (current != null && current.isValid() && !current.isDead() && current instanceof Player) {
            return;
        }
        List<Player> players = getTargetPlayersInRadius(center, radius);
        if (players.isEmpty()) {
            return;
        }
        ensureMobTarget(mob, players);
    }

    private void drawParticleLine(Location start, Location end, Particle particle, double step) {
        // step уже подобран вызывающим кодом (0.7 — оптимально по FPS для Bedrock)
        World world = start.getWorld();
        if (world == null || !world.equals(end.getWorld())) {
            return;
        }
        double distance = start.distance(end);
        if (distance <= 0.1) {
            return;
        }
        Vector dir = end.toVector().subtract(start.toVector()).normalize().multiply(step);
        Location cur = start.clone();
        int steps = (int) Math.min(40, distance / step);
        for (int i = 0; i < steps; i++) {
            world.spawnParticle(particle, cur, 1, 0.02, 0.02, 0.02, 0.0);
            cur.add(dir);
        }
    }

    /**
     * Оптимизация FPS (v1.9.8): линия частиц рисуется ТОЛЬКО для конкретного игрока.
     * При 10 игроках под боссом раньше все 10 линий видели все окружающие — теперь каждый
     * видит только свою, поэтому частиц на клиентах становится в разы меньше.
     */
    private void drawParticleLineForPlayer(Player viewer, Location start, Location end, Particle particle, double step) {
        if (viewer == null || start.getWorld() == null || !start.getWorld().equals(end.getWorld())) {
            return;
        }
        double distance = start.distance(end);
        if (distance <= 0.1) {
            return;
        }
        Vector dir = end.toVector().subtract(start.toVector()).normalize().multiply(step);
        Location cur = start.clone();
        int steps = (int) Math.min(40, distance / step);
        for (int i = 0; i < steps; i++) {
            viewer.spawnParticle(particle, cur, 1, 0.02, 0.02, 0.02, 0.0);
            cur.add(dir);
        }
    }

    private void spawnShockwaveRing(Location center, double maxRadius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION, center, 3, 0.5, 0.2, 0.5, 0.02);
        // Оптимизация FPS: кольца реже (шаг 4.0) и точек на кольцо в 2 раза меньше
        for (double r = 2.0; r <= maxRadius; r += 4.0) {
            int points = (int) (r * 3);
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                double x = center.getX() + Math.cos(angle) * r;
                double z = center.getZ() + Math.sin(angle) * r;
                world.spawnParticle(Particle.FLAME, x, center.getY() + 0.2, z, 1, 0, 0, 0, 0);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void sendActionBar(Player player, String message) {
        // Панель ивента в углу экрана (Bedrock) использует тот же action bar —
        // на 2.5 секунды уступаем ему место, чтобы сообщение способности не пропало мгновенно.
        try {
            if (plugin.getTopRightHudManager() != null) {
                plugin.getTopRightHudManager().suppressCornerHud(2500L);
            }
        } catch (Throwable ignored) {
        }
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (Throwable ignored) {
        }
    }

    public static void applyEntityScale(LivingEntity entity, double scale) {
        setEntityAttribute(entity, "SCALE", "generic.scale", scale);
    }

    public static void setEntityAttribute(LivingEntity entity, String shortName, String registryKey, double value) {
        if (entity == null) {
            return;
        }
        try {
            Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(registryKey));
            if (attr == null && registryKey.startsWith("generic.")) {
                attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(registryKey.substring("generic.".length())));
            }
            if (attr == null) {
                for (Attribute candidate : Registry.ATTRIBUTE) {
                    String key = candidate.getKey().getKey().toUpperCase();
                    if (key.equals(shortName) || key.equals("GENERIC_" + shortName)) {
                        attr = candidate;
                        break;
                    }
                }
            }
            if (attr != null) {
                AttributeInstance inst = entity.getAttribute(attr);
                if (inst != null) {
                    inst.setBaseValue(value);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static ItemStack createBossAxe(PluginConfig cfg) {
        ItemStack item = new ItemStack(cfg.getBossWeaponMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(cfg.getBossWeaponName());
            meta.setUnbreakable(true);
            if (cfg.isBossWeaponGlow()) {
                Enchantment sharp = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
                if (sharp != null) {
                    meta.addEnchant(sharp, 5, true);
                }
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
