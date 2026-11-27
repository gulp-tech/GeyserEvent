package ru.geyserevent.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ванильный лут как на популярных анархиях: алмаз з3/з4, острота, гепы, тотемы.
 */
public final class VanillaEventLoot {

    public record Entry(ItemStack item, double chance) {
    }

    private VanillaEventLoot() {
    }

    public static List<Entry> threeTier(String tier) {
        return switch (tier == null ? "common" : tier.toLowerCase()) {
            case "epic" -> epic();
            case "legendary" -> legendary();
            default -> common();
        };
    }

    public static List<Entry> fourTier(String tier) {
        return switch (tier == null ? "common" : tier.toLowerCase()) {
            case "rare" -> rare();
            case "epic" -> epic();
            case "legendary" -> legendary();
            default -> common();
        };
    }

    public static List<Entry> common() {
        List<Entry> list = new ArrayList<>();
        add(list, stack(Material.GOLDEN_APPLE, 2), 40);
        add(list, stack(Material.ENDER_PEARL, 8), 40);
        add(list, stack(Material.EXPERIENCE_BOTTLE, 16), 45);
        add(list, stack(Material.DIAMOND, 3), 35);
        add(list, stack(Material.GOLD_INGOT, 8), 40);
        add(list, stack(Material.IRON_INGOT, 16), 45);
        add(list, stack(Material.OBSIDIAN, 16), 25);
        add(list, stack(Material.ARROW, 32), 30);
        add(list, gear(Material.DIAMOND_BOOTS, Map.of("protection", 3, "unbreaking", 2, "feather_falling", 3)), 14);
        add(list, gear(Material.DIAMOND_SWORD, Map.of("sharpness", 3, "unbreaking", 2)), 14);
        add(list, stack(Material.COOKED_BEEF, 16), 35);
        return list;
    }

    public static List<Entry> rare() {
        List<Entry> list = new ArrayList<>();
        add(list, stack(Material.GOLDEN_APPLE, 4), 40);
        add(list, stack(Material.ENDER_PEARL, 12), 35);
        add(list, stack(Material.DIAMOND, 6), 40);
        add(list, stack(Material.EXPERIENCE_BOTTLE, 24), 40);
        add(list, stack(Material.TNT, 8), 22);
        add(list, gear(Material.DIAMOND_HELMET, Map.of("protection", 3, "unbreaking", 3)), 16);
        add(list, gear(Material.DIAMOND_LEGGINGS, Map.of("protection", 3, "unbreaking", 3)), 14);
        add(list, gear(Material.DIAMOND_BOOTS, Map.of("protection", 3, "unbreaking", 3, "feather_falling", 4)), 16);
        add(list, gear(Material.DIAMOND_SWORD, Map.of("sharpness", 4, "unbreaking", 3)), 16);
        add(list, stack(Material.TOTEM_OF_UNDYING, 1), 12);
        return list;
    }

    public static List<Entry> epic() {
        List<Entry> list = new ArrayList<>();
        add(list, stack(Material.GOLDEN_APPLE, 6), 38);
        add(list, stack(Material.TOTEM_OF_UNDYING, 1), 20);
        add(list, stack(Material.ENDER_PEARL, 16), 32);
        add(list, stack(Material.DIAMOND, 8), 35);
        add(list, stack(Material.EXPERIENCE_BOTTLE, 32), 35);
        add(list, stack(Material.TNT, 16), 20);
        add(list, gear(Material.DIAMOND_HELMET, Map.of("protection", 4, "unbreaking", 3)), 16);
        add(list, gear(Material.DIAMOND_CHESTPLATE, Map.of("protection", 3, "unbreaking", 3)), 14);
        add(list, gear(Material.DIAMOND_LEGGINGS, Map.of("protection", 4, "unbreaking", 3)), 14);
        add(list, gear(Material.DIAMOND_BOOTS, Map.of("protection", 4, "unbreaking", 3, "feather_falling", 4)), 16);
        add(list, gear(Material.DIAMOND_SWORD, Map.of("sharpness", 4, "unbreaking", 3, "fire_aspect", 2)), 16);
        add(list, gear(Material.DIAMOND_PICKAXE, Map.of("efficiency", 5, "unbreaking", 3, "fortune", 3)), 12);
        add(list, stack(Material.END_CRYSTAL, 2), 12);
        return list;
    }

    public static List<Entry> legendary() {
        List<Entry> list = new ArrayList<>();
        add(list, stack(Material.ENCHANTED_GOLDEN_APPLE, 1), 14);
        add(list, stack(Material.TOTEM_OF_UNDYING, 1), 30);
        add(list, stack(Material.NETHERITE_INGOT, 1), 16);
        add(list, stack(Material.DIAMOND_BLOCK, 2), 25);
        add(list, stack(Material.ELYTRA, 1), 8);
        add(list, stack(Material.END_CRYSTAL, 4), 16);
        add(list, gear(Material.DIAMOND_HELMET, Map.of("protection", 4, "unbreaking", 3, "mending", 1)), 18);
        add(list, gear(Material.DIAMOND_CHESTPLATE, Map.of("protection", 4, "unbreaking", 3, "mending", 1)), 16);
        add(list, gear(Material.DIAMOND_LEGGINGS, Map.of("protection", 4, "unbreaking", 3, "mending", 1)), 16);
        add(list, gear(Material.DIAMOND_BOOTS, Map.of("protection", 4, "unbreaking", 3, "feather_falling", 4, "mending", 1)), 18);
        add(list, gear(Material.DIAMOND_SWORD, Map.of("sharpness", 5, "unbreaking", 3, "fire_aspect", 2, "looting", 3)), 16);
        add(list, gear(Material.BOW, Map.of("power", 5, "unbreaking", 3, "infinity", 1)), 12);
        add(list, stack(Material.GOLDEN_APPLE, 8), 30);
        return list;
    }

    private static void add(List<Entry> list, ItemStack item, double chance) {
        list.add(new Entry(item, chance));
    }

    private static ItemStack stack(Material material, int amount) {
        return new ItemStack(material, Math.max(1, amount));
    }

    private static ItemStack gear(Material material, Map<String, Integer> enchants) {
        return LootStacks.build(material, 1, null, List.of(), enchants);
    }
}
