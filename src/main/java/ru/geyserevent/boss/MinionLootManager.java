package ru.geyserevent.boss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Менеджер выпадения лута и опыта с приспешников Босса:
 * - Повышенный опыт (EXP)
 * - Адские предметы (стержни ифрита, сгустки магмы, кварц, слеза гаста, светокамень, нарост, плачущий обсидиан)
 * - Золото и алмазы (слитки, блоки, золотые яблоки, алмазы)
 * - Редко: всё, что связано с незеритом (древние обломки, лом, кузнечный шаблон, слиток, незеритовые вещи)
 */
public class MinionLootManager {

    private final GeyserEventPlugin plugin;

    public MinionLootManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Генерирует и добавляет лут при смерти приспешника Разлома.
     */
    public void handleMinionDeath(EntityDeathEvent event) {
        event.getDrops().clear();
        PluginConfig cfg = plugin.getPluginConfig();

        if (!cfg.isMinionLootEnabled()) {
            event.setDroppedExp(35);
            return;
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int exp = rnd.nextInt(cfg.getMinionMinExp(), cfg.getMinionMaxExp() + 1);
        event.setDroppedExp(exp);

        LivingEntity minion = event.getEntity();
        Player killer = minion.getKiller();
        Location loc = minion.getLocation();
        World world = loc.getWorld();

        List<ItemStack> generatedDrops = new ArrayList<>();
        List<PluginConfig.LootItemConfig> table = cfg.getMinionLootItems();

        for (PluginConfig.LootItemConfig entry : table) {
            double roll = rnd.nextDouble(0.0, 100.0);
            if (roll <= entry.chancePercent()) {
                int amount = (entry.minAmount() >= entry.maxAmount())
                        ? entry.minAmount()
                        : rnd.nextInt(entry.minAmount(), entry.maxAmount() + 1);
                ItemStack stack = new ItemStack(entry.material(), amount);
                generatedDrops.add(stack);

                if (entry.rare()) {
                    onRareNetheriteDrop(killer, loc, entry.material(), amount);
                }
            }
        }

        // Гарантируем минимум 2 предмета (адский ресурс + золото/алмаз), если рандом ничего не выбрал
        if (generatedDrops.isEmpty()) {
            generatedDrops.add(new ItemStack(Material.GOLD_INGOT, rnd.nextInt(3, 7)));
            generatedDrops.add(new ItemStack(Material.BLAZE_ROD, rnd.nextInt(1, 3)));
        }

        event.getDrops().addAll(generatedDrops);

        if (world != null) {
            world.spawnParticle(Particle.LAVA, loc.clone().add(0, 0.8, 0), 12, 0.3, 0.3, 0.3, 0.05);
            world.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f, 0.9f);
        }
    }

    private void onRareNetheriteDrop(Player killer, Location loc, Material material, int amount) {
        World world = loc.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1.0, 0), 35, 0.4, 0.5, 0.4, 0.06);
            world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.2f);
        }

        if (plugin.getPluginConfig().isAnnounceRareDrops()) {
            String killerName = (killer != null) ? killer.getName() : "Неизвестный воин";
            String itemName = translateNetheriteName(material);
            String msg = plugin.getPluginConfig().getMsgRareMinionDrop()
                    .replace("%player%", killerName)
                    .replace("%item%", itemName)
                    .replace("%amount%", String.valueOf(amount));
            Bukkit.broadcastMessage(ColorUtil.color(msg));
        }
    }

    private String translateNetheriteName(Material material) {
        return switch (material) {
            case ANCIENT_DEBRIS -> "Древние обломки";
            case NETHERITE_SCRAP -> "Незеритовый лом";
            case NETHERITE_UPGRADE_SMITHING_TEMPLATE -> "Кузнечный шаблон (Незеритовое улучшение)";
            case NETHERITE_INGOT -> "Незеритовый слиток";
            case NETHERITE_SWORD -> "Незеритовый меч";
            case NETHERITE_PICKAXE -> "Незеритовая кирка";
            case NETHERITE_AXE -> "Незеритовый топор";
            case NETHERITE_HELMET -> "Незеритовый шлем";
            case NETHERITE_CHESTPLATE -> "Незеритовый нагрудник";
            case NETHERITE_LEGGINGS -> "Незеритовые поножи";
            case NETHERITE_BOOTS -> "Незеритовые ботинки";
            default -> material.name();
        };
    }
}
