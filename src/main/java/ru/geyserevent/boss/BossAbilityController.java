package ru.geyserevent.boss;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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
 * Все боевые способности действуют в радиусе 15 блоков от босса (настраивается в config.yml).
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

    /**
     * Выбирает и применяет следующую способность босса по ротации (если в радиусе 15 блоков есть игроки).
     */
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

    /**
     * Принудительно активирует указанную способность босса.
     */
    public int executeAbility(LivingEntity boss, BossAbilityType type) {
        if (boss == null || boss.isDead() || !boss.isValid()) {
            return 0;
        }
        return switch (type) {
            case PULL -> castPullAbility(boss);
            case PUSH -> castPushAbility(boss);
            case GATHER -> castGatherAllAbility(boss);
            case SUMMON_MINIONS -> summonMinions(boss);
        };
    }

    /**
     * 1. СПОСОБНОСТЬ: ПРИТЯГИВАНИЕ (в радиусе 15 блоков от босса).
     * Цепями Адского Разлома притягивает всех игроков к боссу.
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
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, bossLoc.clone().add(0, 1.2, 0), 45, 0.8, 0.8, 0.8, 0.08);

        double horizStrength = cfg.getPullHorizontalStrength();
        double vertBoost = cfg.getPullVerticalBoost();
        double damage = cfg.getPullDamage();
        int slowTicks = cfg.getPullSlownessTicks();

        for (Player target : targets) {
            Location playerLoc = target.getLocation();
            drawParticleLine(bossLoc.clone().add(0, 1.3, 0), playerLoc.clone().add(0, 1.0, 0), Particle.SOUL_FIRE_FLAME, 0.45);

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
            target.sendMessage(cfg.getMsgAbilityPull());
            sendActionBar(target, "§6§l⚡ Босс притянул вас к себе!");
        }

        return targets.size();
    }

    /**
     * 2. СПОСОБНОСТЬ: ОТТАЛКИВАНИЕ (в радиусе 15 блоков от босса).
     * Брутальный Пиглин бьёт топором о землю и отбрасывает всех игроков в радиусе 15 блоков.
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

        // Визуальное кольцо взрывной волны
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
            target.sendMessage(cfg.getMsgAbilityPush());
            sendActionBar(target, "§c§l💥 Ударная волна отбросила вас!");
        }

        return targets.size();
    }

    /**
     * 3. СПОСОБНОСТЬ: СОБРАТЬ ВСЕХ В ОДНО МЕСТО (в радиусе 15 блоков от босса).
     * Создаёт гравитационную воронку перед боссом и стягивает ВСЕХ игроков
     * в радиусе 15 блоков в одну общую точку!
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

        // Вычисляем единую точку стяжки прямо перед боссом
        Vector lookDir = bossLoc.getDirection().setY(0);
        if (lookDir.lengthSquared() < 0.01) {
            lookDir = new Vector(0, 0, 1);
        } else {
            lookDir.normalize();
        }

        Location gatherPoint = bossLoc.clone().add(lookDir.multiply(cfg.getGatherDistanceInFront()));
        gatherPoint.setY(bossLoc.getY());
        // Если точка оказалась внутри твёрдого блока — берём безопасную высоту рядом с боссом
        if (gatherPoint.getBlock().getType().isSolid()) {
            gatherPoint.add(0, 1.0, 0);
            if (gatherPoint.getBlock().getType().isSolid()) {
                gatherPoint = bossLoc.clone().add(0, 0.2, 0);
            }
        }

        // Эффект гравитационной воронки в точке стяжки
        world.playSound(gatherPoint, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 2.0f, 0.5f);
        world.playSound(gatherPoint, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.6f);
        world.spawnParticle(Particle.PORTAL, gatherPoint.clone().add(0, 1.0, 0), 90, 0.6, 0.8, 0.6, 0.35);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, gatherPoint.clone().add(0, 0.5, 0), 45, 0.4, 0.4, 0.4, 0.04);

        double damage = cfg.getGatherDamage();
        int slowTicks = cfg.getGatherSlownessTicks();

        for (Player target : targets) {
            Location oldLoc = target.getLocation();
            drawParticleLine(oldLoc.clone().add(0, 1.0, 0), gatherPoint.clone().add(0, 1.0, 0), Particle.PORTAL, 0.5);

            // Сохраняем направление камеры игрока (чтобы на телефоне/консоли Bedrock не сбивался прицел)
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
            target.sendMessage(cfg.getMsgAbilityGather());
            sendActionBar(target, "§5§l🌀 Все игроки стянуты в одну точку!");
        }

        // Направляем босса на точку скопления игроков
        if (boss instanceof Mob mob && !targets.isEmpty()) {
            mob.setTarget(targets.get(0));
        }

        return targets.size();
    }

    /**
     * 4. СПОСОБНОСТЬ: ПРИЗЫВ ПРИСПЕШНИКОВ.
     * Призывает свиту приспешников вокруг босса.
     */
    public int summonMinions(LivingEntity boss) {
        PluginConfig cfg = plugin.getPluginConfig();
        if (!cfg.isMinionsEnabled() || boss == null || boss.isDead()) {
            return 0;
        }

        cleanupDeadMinions();
        int currentCount = activeMinions.size();
        int maxAllowed = cfg.getMaxActiveMinions();
        if (currentCount >= maxAllowed) {
            return 0;
        }

        int toSpawn = Math.min(cfg.getMinionCountPerWave(), maxAllowed - currentCount);
        Location bossLoc = boss.getLocation();
        World world = bossLoc.getWorld();
        if (world == null) {
            return 0;
        }

        List<Player> nearbyPlayers = getTargetPlayersInRadius(bossLoc, cfg.getAbilityRadius() + 10.0);
        int spawned = 0;

        for (int i = 0; i < toSpawn; i++) {
            double angle = (2 * Math.PI * i) / toSpawn;
            double offsetX = Math.cos(angle) * 2.5;
            double offsetZ = Math.sin(angle) * 2.5;
            Location spawnLoc = bossLoc.clone().add(offsetX, 0.2, offsetZ);

            Entity entity = world.spawnEntity(spawnLoc, cfg.getMinionEntityType());
            if (entity instanceof LivingEntity minion) {
                configureMinion(minion, nearbyPlayers);
                activeMinions.add(minion.getUniqueId());
                world.spawnParticle(Particle.FLAME, spawnLoc.clone().add(0, 1.0, 0), 25, 0.3, 0.5, 0.3, 0.05);
                world.spawnParticle(Particle.LARGE_SMOKE, spawnLoc.clone().add(0, 1.0, 0), 15, 0.3, 0.5, 0.3, 0.02);
                spawned++;
            }
        }

        if (spawned > 0) {
            world.playSound(bossLoc, Sound.BLOCK_PORTAL_TRAVEL, 0.8f, 1.4f);
            world.playSound(bossLoc, Sound.ENTITY_PIGLIN_BRUTE_CONVERTED_TO_ZOMBIFIED, 1.8f, 0.7f);
            for (Player p : nearbyPlayers) {
                p.sendMessage(cfg.getMsgAbilityMinions());
            }
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
            Entity entity = org.bukkit.Bukkit.getEntity(uuid);
            if (entity == null || entity.isDead() || !entity.isValid()) {
                it.remove();
            }
        }
    }

    public void removeAllMinions() {
        for (UUID uuid : activeMinions) {
            Entity entity = org.bukkit.Bukkit.getEntity(uuid);
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

    /**
     * Находит всех подходящих игроков (Bedrock GeyserMC) в заданном радиусе (по умолчанию 15 блоков) от босса.
     */
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

    private void drawParticleLine(Location start, Location end, Particle particle, double step) {
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
        int steps = (int) Math.min(60, distance / step);
        for (int i = 0; i < steps; i++) {
            world.spawnParticle(particle, cur, 1, 0.02, 0.02, 0.02, 0.0);
            cur.add(dir);
        }
    }

    private void spawnShockwaveRing(Location center, double maxRadius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION, center, 4, 0.5, 0.2, 0.5, 0.02);
        for (double r = 2.0; r <= maxRadius; r += 3.0) {
            int points = (int) (r * 6);
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
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Универсальная установка масштаба (SCALE) моба для Purpur 1.21+ и Purpur 26.2 (2 метра для босса).
     */
    public static void applyEntityScale(LivingEntity entity, double scale) {
        setEntityAttribute(entity, "SCALE", "generic.scale", scale);
    }

    /**
     * Совместимая с Purpur 26.2 и 1.21.x установка атрибута сущности (учитывает переименование GENERIC_* -> *).
     */
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

    /**
     * Создаёт зачарованный Незеритовый топор для Босса.
     */
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
