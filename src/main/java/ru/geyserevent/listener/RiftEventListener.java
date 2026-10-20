package ru.geyserevent.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.projectiles.ProjectileSource;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.event.InfernalRiftManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Слушатель событий ивента «Адский Разлом»:
 * - Блокирует телепортацию игроков и мобов через портал схематики
 * - Проверяет участие только с Bedrock (GeyserMC)
 * - Обрабатывает 3500 HP и 15 урона Босса
 * - Защищает схематику от грифа и взрывов
 */
public class RiftEventListener implements Listener {

    private final GeyserEventPlugin plugin;
    private final Map<UUID, Long> portalMessageCooldown = new HashMap<>();

    public RiftEventListener(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Запрещает порталу Адского Разлома телепортировать игроков в Незер.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!plugin.getPluginConfig().isDisablePortalTeleport()) {
            return;
        }
        if (plugin.getSchematicManager().isRiftPortalOrZone(event.getFrom())) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            long now = System.currentTimeMillis();
            long last = portalMessageCooldown.getOrDefault(player.getUniqueId(), 0L);
            if (now - last > 3000L) {
                portalMessageCooldown.put(player.getUniqueId(), now);
                player.sendMessage(plugin.getPluginConfig().getMsgPortalNoTeleport());
            }
        }
    }

    /**
     * Запрещает порталу Адского Разлома телепортировать босса, приспешников и любые сущности.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        Entity entity = event.getEntity();
        if (plugin.getRiftManager().isBoss(entity)
                || plugin.getAbilityController().isMinion(entity)
                || plugin.getSchematicManager().isRiftPortalOrZone(event.getFrom())) {
            event.setCancelled(true);
        }
    }

    /**
     * Запрещает зомбификацию Брутального Пиглина и его приспешников в мире world2.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        Entity entity = event.getEntity();
        if (plugin.getRiftManager().isBoss(entity) || plugin.getAbilityController().isMinion(entity)) {
            event.setCancelled(true);
        }
    }

    /**
     * Запрещает боссу и приспешникам атаковать друг друга.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        Entity source = event.getEntity();
        LivingEntity target = event.getTarget();
        if (target == null) {
            return;
        }

        boolean sourceIsRiftMob = plugin.getRiftManager().isBoss(source) || plugin.getAbilityController().isMinion(source);
        boolean targetIsRiftMob = plugin.getRiftManager().isBoss(target) || plugin.getAbilityController().isMinion(target);

        if (sourceIsRiftMob && targetIsRiftMob) {
            event.setCancelled(true);
            return;
        }

        // Если цель — игрок не с Bedrock (и включён строгий режим), босс не агрится на него
        if (sourceIsRiftMob && target instanceof Player player && !plugin.getBedrockService().canParticipate(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Обработка боя с Боссом и его приспешниками.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        InfernalRiftManager manager = plugin.getRiftManager();
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        boolean victimIsBoss = manager.isBoss(victim);
        boolean victimIsMinion = plugin.getAbilityController().isMinion(victim);
        boolean damagerIsBoss = manager.isBoss(damager);
        boolean damagerIsMinion = plugin.getAbilityController().isMinion(damager);

        // 1. Защита от урона между боссом и его приспешниками
        if ((victimIsBoss || victimIsMinion) && (damagerIsBoss || damagerIsMinion)) {
            event.setCancelled(true);
            return;
        }

        // 2. Когда Босс бьёт игрока Незеритовым топором — гарантируем урон 15
        if (damagerIsBoss) {
            event.setDamage(plugin.getPluginConfig().getBossDamage());
            return;
        }

        // 3. Когда приспешник бьёт игрока — устанавливаем урон приспешника
        if (damagerIsMinion) {
            event.setDamage(plugin.getPluginConfig().getMinionDamage());
            return;
        }

        // 4. Когда урон наносится Боссу или приспешнику
        if (victimIsBoss || victimIsMinion) {
            Player attacker = resolveAttackerPlayer(damager);
            if (attacker != null) {
                // Проверка: только для игроков с Bedrock Edition (GeyserMC)
                if (!plugin.getBedrockService().canParticipate(attacker)) {
                    event.setCancelled(true);
                    attacker.sendMessage(plugin.getPluginConfig().getMsgOnlyBedrock());
                    return;
                }
            }

            if (victimIsBoss && victim instanceof LivingEntity boss) {
                double finalDmg = Math.max(0.5, event.getFinalDamage());
                event.setDamage(0.0); // Управляем пулом 3500 HP вручную
                manager.handleBossDamaged(boss, attacker, finalDmg);
            } else if (victimIsMinion && victim instanceof LivingEntity minion) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getAbilityController().updateMinionName(minion)
                );
            }
        }
    }

    /**
     * Защита Босса от удушья в стенах, падения и прочего не-боевого урона.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossEnvironmentalDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        Entity entity = event.getEntity();
        if (!plugin.getRiftManager().isBoss(entity)) {
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.SUFFOCATION
                || cause == EntityDamageEvent.DamageCause.FALL
                || cause == EntityDamageEvent.DamageCause.DROWNING
                || cause == EntityDamageEvent.DamageCause.CRAMMING
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            event.setCancelled(true);
        }
    }

    /**
     * Очистка стандартного дропа босса и приспешников (награды выдаются в осколках PlayerPoints).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (plugin.getRiftManager().isBoss(entity)) {
            event.getDrops().clear();
            event.setDroppedExp(500);
            if (!plugin.getRiftManager().isDefeated()) {
                plugin.getRiftManager().handleBossDefeated();
            }
        } else if (plugin.getAbilityController().isMinion(entity)) {
            event.getDrops().clear();
            event.setDroppedExp(25);
        }
    }

    /**
     * Защита схематики Адского Разлома от разрушения блоками/игроками.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getRiftManager().isActive()) {
            return;
        }
        if (plugin.getSchematicManager().isProtectedBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getPluginConfig().getMsgRegionProtected());
        }
    }

    /**
     * Защита схематики Адского Разлома от застраивания блоками.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getRiftManager().isActive()) {
            return;
        }
        if (plugin.getSchematicManager().isProtectedBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getPluginConfig().getMsgRegionProtected());
        }
    }

    /**
     * Защита блоков схематики от взрывов кристаллов и динамита.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.getRiftManager().isActive()) {
            return;
        }
        event.blockList().removeIf(Block::isLiquid);
        event.blockList().removeIf(b -> plugin.getSchematicManager().isProtectedBlock(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!plugin.getRiftManager().isActive()) {
            return;
        }
        event.blockList().removeIf(b -> plugin.getSchematicManager().isProtectedBlock(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalPhysics(BlockFromToEvent event) {
        if (!plugin.getRiftManager().isActive()) {
            return;
        }
        if (plugin.getSchematicManager().isProtectedBlock(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    private Player resolveAttackerPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
