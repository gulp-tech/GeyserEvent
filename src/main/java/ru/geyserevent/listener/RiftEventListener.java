package ru.geyserevent.listener;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.event.EventType;
import ru.geyserevent.event.InfernalRiftManager;
import ru.geyserevent.excavation.BrushTier;
import ru.geyserevent.util.ColorUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Слушатель событий ивентов «Адский Разлом», «Пляжные Раскопки» и «Котёл Алхимика».
 */
public class RiftEventListener implements Listener {

    private final GeyserEventPlugin plugin;
    private final Map<UUID, Long> portalMessageCooldown = new HashMap<>();
    private final Map<UUID, Long> brushStrokeCooldown = new HashMap<>();
    private final Map<UUID, Long> villagerClickCooldown = new HashMap<>();
    private final Map<String, Integer> blockExcavationStep = new HashMap<>();

    public RiftEventListener(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getBrushLootManager().removeAllEventBrushes(player);
        player.removePotionEffect(PotionEffectType.GLOWING);
        if (plugin.getRiftManager().isActive()) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    plugin.getRiftManager().updateEventBossBar(), 10L
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (plugin.getAncientMineManager() != null) {
            plugin.getAncientMineManager().onChunkEntitiesLoaded(event.getChunk(), event.getEntities());
        }
        if (plugin.getRiftManager() != null) {
            plugin.getRiftManager().purgeStaleEventEntities(event.getEntities());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.getChunk().isEntitiesLoaded()) {
            var entities = java.util.Arrays.asList(event.getChunk().getEntities());
            if (plugin.getAncientMineManager() != null) {
                plugin.getAncientMineManager().onChunkEntitiesLoaded(event.getChunk(), entities);
            }
            if (plugin.getRiftManager() != null) {
                plugin.getRiftManager().purgeStaleEventEntities(entities);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (plugin.getMinerBackpackManager() != null) {
            plugin.getMinerBackpackManager().onPlayerMovedOrTeleported(event.getPlayer());
        }
        plugin.getRiftManager().enforceBrushZoneForPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (plugin.getMinerBackpackManager() != null) {
            plugin.getMinerBackpackManager().onPlayerMovedOrTeleported(event.getPlayer());
        }
        if (plugin.getAncientMineManager() != null) {
            plugin.getAncientMineManager().spawnOrVerifyBlacksmith();
        }
        plugin.getRiftManager().handlePlayerChangedWorld(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getMinerBackpackManager() != null) {
                plugin.getMinerBackpackManager().onPlayerMovedOrTeleported(player);
            }
            if (plugin.getAncientMineManager() != null) {
                plugin.getAncientMineManager().spawnOrVerifyBlacksmith();
            }
            plugin.getRiftManager().enforceBrushZoneForPlayer(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getBrushLootManager().removeAllEventBrushes(player);
        if (plugin.getMinerBackpackManager() != null) {
            plugin.getMinerBackpackManager().removeAllMineOresAndGeodes(player);
            plugin.getMinerBackpackManager().removeVisualBackpack(player);
        }
        player.removePotionEffect(PotionEffectType.GLOWING);
        plugin.getTopRightHudManager().removeHudFromPlayer(player);
        brushStrokeCooldown.remove(player.getUniqueId());
        villagerClickCooldown.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        plugin.getBrushLootManager().removeAllEventBrushes(player);
        if (plugin.getMinerBackpackManager() != null) {
            plugin.getMinerBackpackManager().removeAllMineOresAndGeodes(player);
            plugin.getMinerBackpackManager().removeVisualBackpack(player);
        }
        player.removePotionEffect(PotionEffectType.GLOWING);
        plugin.getTopRightHudManager().removeHudFromPlayer(player);
        brushStrokeCooldown.remove(player.getUniqueId());
        villagerClickCooldown.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.getMinerBackpackManager() != null) {
            plugin.getMinerBackpackManager().removeVisualBackpack(player);
        }
        // Кисточки и реагенты временных ивентов удаляются при смерти,
        // НО руды и жеоды из «Шахты Древних» ВЫПАДАЮТ НА ПОЛ!
        event.getDrops().removeIf(item -> plugin.getBrushLootManager().isTemporaryEventItem(item));
        if (plugin.getMinerBackpackManager() != null) {
            plugin.getMinerBackpackManager().dropAllMineItemsOnDeath(player, event.getDrops());
        }
    }

    /**
     * Подбор предметов с пола:
     * 1) Руды и жеоды из «Шахты Древних» проверяются по вместимости Шахтёрского Рюкзака (15..160 ед.).
     * 2) Кисточки и реагенты временных ивентов можно поднимать максимум до 3 штук.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        var backpackMgr = plugin.getMinerBackpackManager();

        if (backpackMgr != null && backpackMgr.isMineOreOrGeode(stack)) {
            if (!(event.getEntity() instanceof Player player)) {
                event.setCancelled(true);
                return;
            }
            if (!backpackMgr.canPickupMineItem(player, stack)) {
                event.setCancelled(true);
                return;
            }
            backpackMgr.notifyMineItemPickedUp(player, stack.getAmount());
            return;
        }

        if (plugin.getBrushLootManager().isTemporaryEventItem(stack)) {
            if (!(event.getEntity() instanceof Player player)) {
                event.setCancelled(true);
                return;
            }
            int currentBrushes = plugin.getBrushLootManager().countPlayerBrushes(player);
            if (currentBrushes >= 3) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // 1. Обработка кликов в GUI-меню настройки лута (/rift menu) и в меню «Кузница и Рюкзак»
        if (plugin.getBrushLootGuiManager().handleInventoryClick(event)) {
            return;
        }
        if (plugin.getMinerBackpackManager() != null
                && plugin.getMinerBackpackManager().handleForgeMenuClick(event)) {
            return;
        }

        // 2. Запрет перекладывания ивентовых предметов и непереплавленной руды в сундуки / эндер-сундук / шалкеры
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = (event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player p)
                ? p.getInventory().getItem(event.getHotbarButton())
                : null;
        boolean restricted = plugin.getBrushLootManager().getBrushTier(current) != null
                || plugin.getBrushLootManager().getBrushTier(cursor) != null
                || plugin.getBrushLootManager().getBrushTier(hotbar) != null
                || (plugin.getMinerBackpackManager() != null && (
                        plugin.getMinerBackpackManager().isMineOreOrGeode(current)
                        || plugin.getMinerBackpackManager().isMineOreOrGeode(cursor)
                        || plugin.getMinerBackpackManager().isMineOreOrGeode(hotbar)
                ));
        if (restricted) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (plugin.getBrushLootGuiManager().handleInventoryDrag(event)) {
            return;
        }
        if (plugin.getMinerBackpackManager() != null
                && plugin.getMinerBackpackManager().handleForgeMenuDrag(event)) {
            return;
        }
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            return;
        }
        ItemStack oldCursor = event.getOldCursor();
        if (plugin.getBrushLootManager().getBrushTier(oldCursor) != null
                || (plugin.getMinerBackpackManager() != null && plugin.getMinerBackpackManager().isMineOreOrGeode(oldCursor))) {
            event.setCancelled(true);
        }
    }

    /**
     * Запрет команд /ah sell и /ah sellinv, когда кисточка, реагент, жеода или руда в инвентаре.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        boolean holdingEventItem = plugin.getBrushLootManager().getBrushTier(mainHand) != null
                || plugin.getBrushLootManager().getBrushTier(offHand) != null
                || plugin.getBrushLootManager().hasAnyEventBrush(player)
                || (plugin.getMinerBackpackManager() != null
                && plugin.getMinerBackpackManager().countCarriedMineItems(player) > 0);

        if (!holdingEventItem) {
            return;
        }

        String raw = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (raw.startsWith("/")) {
            raw = raw.substring(1).trim();
        }
        String[] parts = raw.split("\\s+");
        if (parts.length == 0) {
            return;
        }
        String baseCmd = parts[0];
        int colonIdx = baseCmd.indexOf(':');
        if (colonIdx >= 0) {
            baseCmd = baseCmd.substring(colonIdx + 1);
        }

        boolean isAhCommand = baseCmd.equals("ah") || baseCmd.equals("auc") || baseCmd.equals("auction")
                || baseCmd.equals("auctions") || baseCmd.equals("ca") || baseCmd.equals("crazyauctions");
        if (isAhCommand && parts.length >= 2) {
            String sub = parts[1];
            if (sub.startsWith("sell") || sub.equals("s") || sub.equals("bid")) {
                event.setCancelled(true);
                player.sendMessage(ColorUtil.color("&e┠ &cНельзя использовать /ah sell с предметом ивента в инвентаре!"));
            }
        } else if (baseCmd.equals("ahsell") || baseCmd.equals("sellinv")) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.color("&e┠ &cНельзя использовать эту команду с предметом ивента в инвентаре!"));
        }
    }

    /**
     * Клик по 2-метровому Жителю-Археологу, Магистру Алхимику или Древнему Кузнецу (ПКМ / ЛКМ).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (!event.getRightClicked().isVisible() || event.getRightClicked().isMarker()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.MERCHANT
                && plugin.getAncientMineManager() != null
                && plugin.getAncientMineManager().isPlayerInsideMineZone(player.getLocation())) {
            event.setCancelled(true);
            triggerBlacksmithClickDebounced(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (plugin.getAncientMineManager() != null && plugin.getAncientMineManager().isBlacksmithNpc(clicked)) {
            event.setCancelled(true);
            triggerBlacksmithClickDebounced(event.getPlayer());
            return;
        }
        if (plugin.getRiftManager().isExcavationVillager(clicked)) {
            event.setCancelled(true);
            triggerVillagerClickDebounced(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (plugin.getAncientMineManager() != null && plugin.getAncientMineManager().isBlacksmithNpc(clicked)) {
            event.setCancelled(true);
            triggerBlacksmithClickDebounced(event.getPlayer());
            return;
        }
        if (plugin.getRiftManager().isExcavationVillager(clicked)) {
            event.setCancelled(true);
            triggerVillagerClickDebounced(event.getPlayer());
        }
    }

    private void triggerBlacksmithClickDebounced(Player player) {
        long now = System.currentTimeMillis();
        long last = villagerClickCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 250L) {
            return;
        }
        villagerClickCooldown.put(player.getUniqueId(), now);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.getAncientMineManager() != null) {
                plugin.getAncientMineManager().handleBlacksmithInteract(player);
            }
        });
    }

    private void triggerVillagerClickDebounced(Player player) {
        long now = System.currentTimeMillis();
        long last = villagerClickCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 250L) {
            return;
        }
        villagerClickCooldown.put(player.getUniqueId(), now);
        plugin.getRiftManager().handleVillagerInteraction(player);
    }

    /**
     * Обработка кликов по блокам в ивентах «Шахта Древних», «Пляжные Раскопки» и «Котёл Алхимика».
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractBlock(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        // Постоянный ивент «Шахта Древних» (0 ~ 0): клик по наковальне/кузнице в центре раскалывает жеоду
        if (plugin.getAncientMineManager() != null && plugin.getAncientMineManager().isInsideMineTerritory(block.getLocation())) {
            Material m = block.getType();
            if (m == Material.ANVIL || m == Material.CHIPPED_ANVIL || m == Material.DAMAGED_ANVIL
                    || m == Material.SMITHING_TABLE || m == Material.BLAST_FURNACE) {
                event.setCancelled(true);
                triggerBlacksmithClickDebounced(event.getPlayer());
                return;
            }
        }

        InfernalRiftManager manager = plugin.getRiftManager();
        if (!manager.isActive()) {
            return;
        }

        if (manager.getActiveEventType() == EventType.BEACH_EXCAVATION) {
            if (plugin.getBeachSchematicManager().isExcavatableBeachSand(block)) {
                if (tryExcavateSandStroke(event.getPlayer(), block)) {
                    event.setCancelled(true);
                }
            }
        } else if (manager.getActiveEventType() == EventType.ALCHEMIST_CAULDRON) {
            // 1. Клик по одному из 4 центральных Котлов вокруг Алхимика — варим реагент!
            if (plugin.getAlchemistSchematicManager().isCentralCauldronBlock(block)) {
                event.setCancelled(true);
                triggerVillagerClickDebounced(event.getPlayer());
                return;
            }
            // 2. Клик по выросшему на пьедестале Реагенту — собираем его!
            Block nodeBlock = plugin.getAlchemistSchematicManager().resolveReagentNodeBlock(block);
            if (nodeBlock != null) {
                event.setCancelled(true);
                tryHarvestAlchemistReagent(event.getPlayer(), nodeBlock);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDamageEventAction(BlockDamageEvent event) {
        InfernalRiftManager manager = plugin.getRiftManager();
        if (!manager.isActive()) {
            return;
        }
        Block block = event.getBlock();
        if (manager.getActiveEventType() == EventType.BEACH_EXCAVATION) {
            if (plugin.getBeachSchematicManager().isExcavatableBeachSand(block)) {
                if (tryExcavateSandStroke(event.getPlayer(), block)) {
                    event.setCancelled(true);
                }
            }
        } else if (manager.getActiveEventType() == EventType.ALCHEMIST_CAULDRON) {
            if (plugin.getAlchemistSchematicManager().isCentralCauldronBlock(block)) {
                event.setCancelled(true);
                triggerVillagerClickDebounced(event.getPlayer());
                return;
            }
            Block nodeBlock = plugin.getAlchemistSchematicManager().resolveReagentNodeBlock(block);
            if (nodeBlock != null) {
                event.setCancelled(true);
                tryHarvestAlchemistReagent(event.getPlayer(), nodeBlock);
            }
        }
    }

    /**
     * Сбор выросшего нестабильного Реагента с пьедестала в ивенте «Котёл Алхимика».
     */
    @SuppressWarnings("deprecation")
    private void tryHarvestAlchemistReagent(Player player, Block nodeBlock) {
        if (!plugin.getBedrockService().canParticipate(player)) {
            player.sendMessage(plugin.getPluginConfig().getMsgOnlyBedrock());
            return;
        }

        long now = System.currentTimeMillis();
        long last = brushStrokeCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 300L) {
            return;
        }
        brushStrokeCooldown.put(player.getUniqueId(), now);

        if (plugin.getBrushLootManager().countPlayerBrushes(player) >= 3) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            String msg = ColorUtil.color("&e┠ &cВ инвентаре максимум 3 реагента! Сдайте их Алхимику у котла.");
            player.sendMessage(msg);
            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
            } catch (Throwable ignored) {
            }
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage(ColorUtil.color("&e┠ &cОсвободите место в инвентаре для сбора реагента!"));
            return;
        }

        BrushTier tier = plugin.getAlchemistSchematicManager().harvestReagentAt(nodeBlock);
        if (tier == null) {
            return;
        }

        ItemStack reagent = plugin.getBrushLootManager().createReagentItem(tier);
        player.getInventory().addItem(reagent);

        Location effectLoc = nodeBlock.getLocation().add(0.5, 0.5, 0.5);
        nodeBlock.getWorld().playSound(effectLoc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.3f, 1.2f);
        nodeBlock.getWorld().playSound(effectLoc, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.3f);
        nodeBlock.getWorld().spawnParticle(Particle.WITCH, effectLoc, 8, 0.25, 0.3, 0.25, 0.03);
        nodeBlock.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, effectLoc, 5, 0.25, 0.25, 0.25, 0.03);

        String ab = ColorUtil.color(
                "&aСобран: " + plugin.getBrushLootManager().getReagentDisplayName(tier)
                        + " &8(&fСдайте Алхимику у котла!&8)"
        );
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ab));
        } catch (Throwable ignored) {
        }

        LivingEntity alch = plugin.getRiftManager().getExcavationVillagerEntity();
        if (alch != null) {
            plugin.getRiftManager().updateAlchemistNameAndBar(alch);
        }
        plugin.getTopRightHudManager().updateTopRightHud();
    }

    @SuppressWarnings("deprecation")
    private boolean tryExcavateSandStroke(Player player, Block block) {
        ItemStack handItem = player.getInventory().getItemInMainHand();
        BrushTier tier = plugin.getBrushLootManager().getBrushTier(handItem);
        if (tier == null) {
            handItem = player.getInventory().getItemInOffHand();
            tier = plugin.getBrushLootManager().getBrushTier(handItem);
        }
        if (tier == null) {
            return false;
        }

        if (!plugin.getBedrockService().canParticipate(player)) {
            player.sendMessage(plugin.getPluginConfig().getMsgOnlyBedrock());
            return true;
        }

        long now = System.currentTimeMillis();
        long lastStroke = brushStrokeCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastStroke < 600L) {
            return true;
        }
        brushStrokeCooldown.put(player.getUniqueId(), now);

        String blockKey = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        int step = blockExcavationStep.getOrDefault(blockKey, 0) + 1;
        Location effectLoc = block.getLocation().add(0.5, 1.05, 0.5);

        if (step < 3) {
            blockExcavationStep.put(blockKey, step);
            block.getWorld().playSound(effectLoc, Sound.BLOCK_SAND_HIT, 1.2f, 0.9f + (step * 0.15f));
            block.getWorld().spawnParticle(
                    Particle.BLOCK,
                    effectLoc,
                    8,
                    0.2, 0.12, 0.2,
                    0.04,
                    Material.SAND.createBlockData()
            );

            String progressBars = (step == 1) ? "&6■&7□□" : "&6■■&7□";
            String ab = ColorUtil.color("&eРаскопка песка: " + progressBars + " &8(&f" + step + "/3&8)");
            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ab));
            } catch (Throwable ignored) {
            }
        } else {
            blockExcavationStep.remove(blockKey);

            // Выдаём лут из таблицы данной кисточки БЕЗ сообщения в чат «Вы раскопали...»
            ItemStack reward = plugin.getBrushLootManager().rollLootForBrush(tier);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
            if (!leftovers.isEmpty()) {
                for (ItemStack drop : leftovers.values()) {
                    block.getWorld().dropItemNaturally(effectLoc, drop);
                }
            }

            plugin.getBrushLootManager().consumeBrushUse(player, handItem);
            plugin.getBeachSchematicManager().markBlockExcavatedAndScheduleRespawn(block);

            block.getWorld().playSound(effectLoc, Sound.BLOCK_SAND_BREAK, 1.2f, 1.1f);
            block.getWorld().playSound(effectLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
            block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, effectLoc, 8, 0.25, 0.25, 0.25, 0.04);
            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
            } catch (Throwable ignored) {
            }
        }
        return true;
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
                || plugin.getRiftManager().isExcavationVillager(entity)
                || plugin.getAbilityController().isMinion(entity)
                || plugin.getSchematicManager().isRiftPortalOrZone(event.getFrom())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        Entity entity = event.getEntity();
        if (plugin.getRiftManager().isBoss(entity)
                || plugin.getRiftManager().isExcavationVillager(entity)
                || plugin.getAbilityController().isMinion(entity)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        Entity entered = event.getEntered();
        if (plugin.getRiftManager().isBoss(entered)
                || plugin.getRiftManager().isExcavationVillager(entered)
                || plugin.getAbilityController().isMinion(entered)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        Entity caught = event.getCaught();
        if (caught != null && (plugin.getRiftManager().isBoss(caught)
                || plugin.getRiftManager().isExcavationVillager(caught)
                || plugin.getAbilityController().isMinion(caught))) {
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

        if (plugin.getRiftManager().isExcavationVillager(target)) {
            event.setCancelled(true);
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        InfernalRiftManager manager = plugin.getRiftManager();
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        // Если игрок нажал ЛКМ по 2-метровому Жителю, Алхимику или Древнему Кузнецу — обрабатываем взаимодействие!
        if (plugin.getAncientMineManager() != null && plugin.getAncientMineManager().isBlacksmithNpc(victim)) {
            event.setCancelled(true);
            if (damager instanceof Player player) {
                triggerBlacksmithClickDebounced(player);
            }
            return;
        }
        if (manager.isExcavationVillager(victim)) {
            event.setCancelled(true);
            if (damager instanceof Player player) {
                triggerVillagerClickDebounced(player);
            }
            return;
        }

        if (event.isCancelled()) {
            return;
        }

        boolean victimIsBoss = manager.isBoss(victim);
        boolean victimIsMinion = plugin.getAbilityController().isMinion(victim);
        boolean damagerIsBoss = manager.isBoss(damager);
        boolean damagerIsMinion = plugin.getAbilityController().isMinion(damager);

        // Если ивент Разлома уже закончился или Босс уже повержен, любые оставшиеся Боссы/Приспешники немедленно удаляются!
        boolean riftActive = manager.isActive()
                && !manager.isDefeated()
                && manager.getActiveEventType() == EventType.INFERNAL_RIFT;

        if (!riftActive) {
            if (victimIsBoss || victimIsMinion) {
                event.setCancelled(true);
                victim.remove();
                return;
            }
            if (damagerIsBoss || damagerIsMinion) {
                event.setCancelled(true);
                damager.remove();
                return;
            }
        }

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
                double finalDmg = Math.max(1.0, Math.max(event.getDamage(), event.getFinalDamage()));
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
        Entity entity = event.getEntity();
        if (plugin.getRiftManager().isExcavationVillager(entity)
                || (plugin.getAncientMineManager() != null && plugin.getAncientMineManager().isBlacksmithNpc(entity))) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
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
        Block block = event.getBlock();

        // 1. Постоянный ивент «Шахта Древних» (0 ~ 0): добыча руд и Древних жеод в жиле
        if (plugin.getAncientMineManager() != null
                && plugin.getAncientMineManager().handleMineBlockBreak(event.getPlayer(), block)) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.getRiftManager().isActive()) {
            return;
        }
        if (plugin.getRiftManager().getActiveEventType() == EventType.ALCHEMIST_CAULDRON) {
            Block nodeBlock = plugin.getAlchemistSchematicManager().resolveReagentNodeBlock(block);
            if (nodeBlock != null) {
                event.setCancelled(true);
                tryHarvestAlchemistReagent(event.getPlayer(), nodeBlock);
                return;
            }
        }
        if (plugin.getSchematicManager().isProtectedBlock(block.getLocation())
                || plugin.getBeachSchematicManager().isProtectedArenaBlock(block)
                || plugin.getAlchemistSchematicManager().isProtectedArenaBlock(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getPluginConfig().getMsgRegionProtected());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (plugin.getAncientMineManager() != null && plugin.getAncientMineManager().isInsideMineTerritory(block.getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getPluginConfig().getMsgRegionProtected());
            return;
        }
        if (!plugin.getRiftManager().isActive()) {
            return;
        }
        if (plugin.getSchematicManager().isProtectedBlock(block.getLocation())
                || plugin.getBeachSchematicManager().isProtectedArenaBlock(block)
                || plugin.getAlchemistSchematicManager().isProtectedArenaBlock(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getPluginConfig().getMsgRegionProtected());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> plugin.getAncientMineManager() != null
                && plugin.getAncientMineManager().isInsideMineTerritory(b.getLocation()));
        if (!plugin.getRiftManager().isActive()) {
            return;
        }
        event.blockList().removeIf(Block::isLiquid);
        event.blockList().removeIf(b -> plugin.getSchematicManager().isProtectedBlock(b.getLocation())
                || plugin.getBeachSchematicManager().isProtectedArenaBlock(b)
                || plugin.getAlchemistSchematicManager().isProtectedArenaBlock(b));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> plugin.getAncientMineManager() != null
                && plugin.getAncientMineManager().isInsideMineTerritory(b.getLocation()));
        if (!plugin.getRiftManager().isActive()) {
            return;
        }
        event.blockList().removeIf(b -> plugin.getSchematicManager().isProtectedBlock(b.getLocation())
                || plugin.getBeachSchematicManager().isProtectedArenaBlock(b)
                || plugin.getAlchemistSchematicManager().isProtectedArenaBlock(b));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalPhysics(BlockFromToEvent event) {
        if (!plugin.getRiftManager().isActive()) {
            return;
        }
        Block to = event.getToBlock();
        if (plugin.getSchematicManager().isProtectedBlock(to.getLocation())
                || plugin.getBeachSchematicManager().isProtectedArenaBlock(to)
                || plugin.getAlchemistSchematicManager().isProtectedArenaBlock(to)) {
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
