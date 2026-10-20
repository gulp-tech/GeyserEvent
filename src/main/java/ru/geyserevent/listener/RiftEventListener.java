package ru.geyserevent.listener;

import org.bukkit.Bukkit;
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
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.projectiles.ProjectileSource;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.event.InfernalRiftManager;
import ru.geyserevent.util.ColorUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Слушатель событий ивента «Адский Разлом».
 */
public class RiftEventListener implements Listener {

    private final GeyserEventPlugin plugin;
    private final Map<UUID, Long> portalMessageCooldown = new HashMap<>();

    public RiftEventListener(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.getRiftManager().isActive()) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    plugin.getTopRightHudManager().updateTopRightHud(), 10L
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        plugin.getRiftManager().handlePlayerChangedWorld(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getTopRightHudManager().removeHudFromPlayer(event.getPlayer());
    }

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        Entity entity = event.getEntity();
        if (plugin.getRiftManager().isBoss(entity)
                || plugin.getAbilityController().isMinion(entity)
                || plugin.getSchematicManager().isRiftPortalOrZone(event.getFrom())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        Entity entity = event.getEntity();
        if (plugin.getRiftManager().isBoss(entity) || plugin.getAbilityController().isMinion(entity)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        Entity entered = event.getEntered();
        if (plugin.getRiftManager().isBoss(entered) || plugin.getAbilityController().isMinion(entered)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        Entity caught = event.getCaught();
        if (caught != null && (plugin.getRiftManager().isBoss(caught) || plugin.getAbilityController().isMinion(caught))) {
            event.setCancelled(true);
        }
    }

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

        if (sourceIsRiftMob && target instanceof Player player && !plugin.getBedrockService().canParticipate(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        InfernalRiftManager manager = plugin.getRiftManager();
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        boolean victimIsBoss = manager.isBoss(victim);
        boolean victimIsMinion = plugin.getAbilityController().isMinion(victim);
        boolean damagerIsBoss = manager.isBoss(damager);
        boolean damagerIsMinion = plugin.getAbilityController().isMinion(damager);

        if ((victimIsBoss || victimIsMinion) && (damagerIsBoss || damagerIsMinion)) {
            event.setCancelled(true);
            return;
        }

        if (damagerIsBoss) {
            event.setDamage(plugin.getPluginConfig().getBossDamage());
            return;
        }

        if (damagerIsMinion) {
            event.setDamage(plugin.getPluginConfig().getMinionDamage());
            return;
        }

        if (victimIsBoss || victimIsMinion) {
            Player attacker = resolveAttackerPlayer(damager);
            if (attacker != null) {
                if (!plugin.getBedrockService().canParticipate(attacker)) {
                    event.setCancelled(true);
                    attacker.sendMessage(plugin.getPluginConfig().getMsgOnlyBedrock());
                    return;
                }

                if (victimIsBoss && damager instanceof Projectile) {
                    double maxRange = plugin.getPluginConfig().getMaxRangedAttackDistance();
                    if (attacker.getWorld().equals(victim.getWorld())
                            && attacker.getLocation().distanceSquared(victim.getLocation()) > maxRange * maxRange) {
                        event.setCancelled(true);
                        attacker.sendMessage(ColorUtil.color("&e┠ &cПодойдите ближе к Боссу для нанесения урона."));
                        return;
                    }
                }
            }

            if (victimIsBoss && victim instanceof LivingEntity boss) {
                double finalDmg = Math.max(0.5, event.getFinalDamage());
                event.setDamage(0.0);
                manager.handleBossDamaged(boss, attacker, finalDmg);
            } else if (victimIsMinion && victim instanceof LivingEntity minion) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getAbilityController().updateMinionName(minion)
                );
            }
        }
    }

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

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (plugin.getRiftManager().isBoss(entity)) {
            event.getDrops().clear();
            event.setDroppedExp(600);
            if (!plugin.getRiftManager().isDefeated()) {
                plugin.getRiftManager().handleBossDefeated();
            }
        } else if (plugin.getAbilityController().isMinion(entity)) {
            plugin.getMinionLootManager().handleMinionDeath(event);
        }
    }

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
