package ru.geyserevent.boss;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.config.PluginConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Менеджер выпадения лута и опыта с приспешников Босса:
 * - Адские предметы, опыт, алмазы, золото и редко — всё что связано с незеритом.
 * - Без лишних сообщений в чат при выпадении редкого лута.
 * - Чем дольше игроки не могут убить Босса, тем сильнее ухудшается лут и опыт с приспешников.
 */
public class MinionLootManager {

    private final GeyserEventPlugin plugin;

    public MinionLootManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Вычисляет множитель качества лута приспешников (от 1.0 до 0.15)
     * в зависимости от того, сколько времени уже прошло с начала ивента.
     */
    public double getLootQualityMultiplier() {
        int elapsed = plugin.getRiftManager().getSecondsElapsed();
        int maxSeconds = Math.max(60, plugin.getPluginConfig().getDurationMinutes() * 60);
        double progress = (double) elapsed / (double) maxSeconds;

        if (progress <= 0.25) {
            return 1.0;   // Первые 25% времени (0-5 мин): 100% лучший лут
        } else if (progress <= 0.50) {
            return 0.65;  // 25-50% времени (5-10 мин): 65% качества
        } else if (progress <= 0.75) {
            return 0.35;  // 50-75% времени (10-15 мин): 35% качества (без незерита)
        } else {
            return 0.15;  // Последние 25% времени (15-20 мин): 15% (минимальный лут)
        }
    }

    public void handleMinionDeath(EntityDeathEvent event) {
        event.getDrops().clear();
        PluginConfig cfg = plugin.getPluginConfig();

        if (!cfg.isMinionLootEnabled()) {
            event.setDroppedExp(25);
            return;
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double quality = getLootQualityMultiplier();

        int rawExp = rnd.nextInt(cfg.getMinionMinExp(), cfg.getMinionMaxExp() + 1);
        int finalExp = Math.max(5, (int) Math.round(rawExp * quality));
        event.setDroppedExp(finalExp);

        LivingEntity minion = event.getEntity();
        Location loc = minion.getLocation();
        World world = loc.getWorld();

        List<ItemStack> generatedDrops = new ArrayList<>();
        List<PluginConfig.LootItemConfig> table = cfg.getMinionLootItems();

        for (PluginConfig.LootItemConfig entry : table) {
            // Если прошло больше половины времени ивента — незерит и алмазные блоки перестают падать
            if (quality <= 0.35 && (entry.rare()
                    || entry.material() == Material.DIAMOND_BLOCK
                    || entry.material() == Material.GOLD_BLOCK)) {
                continue;
            }
            // В последней четверти времени падают только базовые адские ресурсы и немного золота
            if (quality <= 0.15 && (entry.material() == Material.DIAMOND
                    || entry.material() == Material.GOLDEN_APPLE
                    || entry.material() == Material.GHAST_TEAR)) {
                continue;
            }

            double effectiveChance = entry.rare()
                    ? entry.chancePercent() * (quality * quality)
                    : entry.chancePercent() * quality;

            double roll = rnd.nextDouble(0.0, 100.0);
            if (roll <= effectiveChance) {
                int baseAmount = (entry.minAmount() >= entry.maxAmount())
                        ? entry.minAmount()
                        : rnd.nextInt(entry.minAmount(), entry.maxAmount() + 1);
                int scaledAmount = Math.max(1, (int) Math.round(baseAmount * quality));
                generatedDrops.add(new ItemStack(entry.material(), scaledAmount));

                if (entry.rare() && world != null) {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1.0, 0), 25, 0.3, 0.4, 0.3, 0.05);
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 1.2f);
                }
            }
        }

        if (generatedDrops.isEmpty()) {
            if (quality >= 0.5) {
                generatedDrops.add(new ItemStack(Material.GOLD_INGOT, rnd.nextInt(2, 5)));
                generatedDrops.add(new ItemStack(Material.BLAZE_ROD, rnd.nextInt(1, 3)));
            } else {
                generatedDrops.add(new ItemStack(Material.QUARTZ, rnd.nextInt(1, 3)));
                generatedDrops.add(new ItemStack(Material.GOLD_NUGGET, rnd.nextInt(2, 5)));
            }
        }

        event.getDrops().addAll(generatedDrops);

        if (world != null) {
            world.spawnParticle(Particle.LAVA, loc.clone().add(0, 0.8, 0), 8, 0.3, 0.3, 0.3, 0.04);
        }
    }
}
