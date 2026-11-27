package ru.geyserevent.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ванильный лут как на популярных анархиях: броня з3/з4, оружие, инструменты, зелья.
 */
public final class VanillaEventLoot {

    public static final int MAX_SLOTS = 36;

    public record Entry(ItemStack item, double chance) {
    }

    private VanillaEventLoot() {
    }

    public static String signature(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "";
        }
        if (stack.getItemMeta() instanceof PotionMeta meta && meta.getBasePotionType() != null) {
            return stack.getType().name() + ":" + meta.getBasePotionType().name();
        }
        return stack.getType().name();
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
        add(list, stack(Material.GOLDEN_APPLE, 2), 38);
        add(list, stack(Material.ENDER_PEARL, 8), 36);
        add(list, stack(Material.EXPERIENCE_BOTTLE, 16), 42);
        add(list, stack(Material.IRON_INGOT, 16), 40);
        add(list, stack(Material.GOLD_INGOT, 8), 36);
        add(list, stack(Material.ARROW, 32), 28);
        add(list, stack(Material.COOKED_BEEF, 16), 30);
        add(list, gear(Material.IRON_HELMET, Map.of("protection", 3, "unbreaking", 2)), 16);
        add(list, gear(Material.IRON_CHESTPLATE, Map.of("protection", 3, "unbreaking", 2)), 14);
        add(list, gear(Material.IRON_LEGGINGS, Map.of("protection", 3, "unbreaking", 2)), 14);
        add(list, gear(Material.IRON_BOOTS, Map.of("protection", 3, "unbreaking", 2, "feather_falling", 2)), 16);
        add(list, gear(Material.GOLDEN_HELMET, Map.of("protection", 2, "unbreaking", 2)), 10);
        add(list, gear(Material.CHAINMAIL_CHESTPLATE, Map.of("protection", 3, "unbreaking", 2)), 12);
        add(list, gear(Material.LEATHER_BOOTS, Map.of("protection", 2, "unbreaking", 2, "feather_falling", 2)), 10);
        add(list, gear(Material.IRON_SWORD, Map.of("sharpness", 2, "unbreaking", 2)), 16);
        add(list, gear(Material.STONE_SWORD, Map.of("sharpness", 3, "unbreaking", 2)), 12);
        add(list, gear(Material.BOW, Map.of("power", 2, "unbreaking", 2)), 12);
        add(list, gear(Material.IRON_AXE, Map.of("sharpness", 2, "unbreaking", 2)), 12);
        add(list, gear(Material.IRON_PICKAXE, Map.of("efficiency", 3, "unbreaking", 2)), 14);
        add(list, gear(Material.SHIELD, Map.of("unbreaking", 2)), 10);
        add(list, potion(Material.SPLASH_POTION, PotionType.HEALING, 2), 22);
        add(list, potion(Material.POTION, PotionType.SWIFTNESS, 1), 16);
        add(list, potion(Material.POTION, PotionType.FIRE_RESISTANCE, 1), 12);
        return list;
    }

    public static List<Entry> rare() {
        List<Entry> list = new ArrayList<>();
        add(list, stack(Material.GOLDEN_APPLE, 4), 38);
        add(list, stack(Material.ENDER_PEARL, 12), 32);
        add(list, stack(Material.DIAMOND, 6), 34);
        add(list, stack(Material.TNT, 8), 18);
        add(list, stack(Material.TOTEM_OF_UNDYING, 1), 12);
        add(list, gear(Material.IRON_HELMET, Map.of("protection", 4, "unbreaking", 3)), 14);
        add(list, gear(Material.IRON_CHESTPLATE, Map.of("protection", 4, "unbreaking", 3)), 12);
        add(list, gear(Material.IRON_LEGGINGS, Map.of("protection", 4, "unbreaking", 3)), 12);
        add(list, gear(Material.IRON_BOOTS, Map.of("protection", 4, "unbreaking", 3, "feather_falling", 4)), 14);
        add(list, gear(Material.DIAMOND_HELMET, Map.of("protection", 3, "unbreaking", 3)), 16);
        add(list, gear(Material.DIAMOND_LEGGINGS, Map.of("protection", 3, "unbreaking", 3)), 14);
        add(list, gear(Material.DIAMOND_BOOTS, Map.of("protection", 3, "unbreaking", 3, "feather_falling", 4)), 16);
        add(list, gear(Material.GOLDEN_CHESTPLATE, Map.of("protection", 3, "unbreaking", 3)), 10);
        add(list, gear(Material.CHAINMAIL_LEGGINGS, Map.of("protection", 3, "unbreaking", 3)), 10);
        add(list, gear(Material.DIAMOND_SWORD, Map.of("sharpness", 4, "unbreaking", 3)), 16);
        add(list, gear(Material.DIAMOND_AXE, Map.of("sharpness", 3, "unbreaking", 3)), 12);
        add(list, gear(Material.BOW, Map.of("power", 3, "punch", 1, "unbreaking", 3)), 12);
        add(list, gear(Material.CROSSBOW, Map.of("piercing", 3, "unbreaking", 3)), 10);
        add(list, gear(Material.DIAMOND_PICKAXE, Map.of("efficiency", 4, "unbreaking", 3)), 12);
        add(list, potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 2), 20);
        add(list, potion(Material.POTION, PotionType.STRENGTH, 1), 14);
        add(list, potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE, 1), 12);
        return list;
    }

    public static List<Entry> epic() {
        List<Entry> list = new ArrayList<>();
        add(list, stack(Material.GOLDEN_APPLE, 6), 36);
        add(list, stack(Material.TOTEM_OF_UNDYING, 1), 20);
        add(list, stack(Material.ENDER_PEARL, 16), 28);
        add(list, stack(Material.DIAMOND, 8), 32);
        add(list, stack(Material.TNT, 16), 16);
        add(list, stack(Material.END_CRYSTAL, 2), 12);
        add(list, gear(Material.DIAMOND_HELMET, Map.of("protection", 4, "unbreaking", 3)), 16);
        add(list, gear(Material.DIAMOND_CHESTPLATE, Map.of("protection", 4, "unbreaking", 3)), 14);
        add(list, gear(Material.DIAMOND_LEGGINGS, Map.of("protection", 4, "unbreaking", 3)), 14);
        add(list, gear(Material.DIAMOND_BOOTS, Map.of("protection", 4, "unbreaking", 3, "feather_falling", 4)), 16);
        add(list, gear(Material.IRON_CHESTPLATE, Map.of("protection", 4, "unbreaking", 3)), 10);
        add(list, gear(Material.NETHERITE_BOOTS, Map.of("protection", 3, "unbreaking", 3, "feather_falling", 4)), 8);
        add(list, gear(Material.DIAMOND_SWORD, Map.of("sharpness", 4, "unbreaking", 3, "fire_aspect", 2)), 16);
        add(list, gear(Material.DIAMOND_AXE, Map.of("sharpness", 4, "unbreaking", 3)), 12);
        add(list, gear(Material.BOW, Map.of("power", 4, "unbreaking", 3, "flame", 1)), 12);
        add(list, gear(Material.TRIDENT, Map.of("loyalty", 3, "unbreaking", 3)), 8);
        add(list, gear(Material.DIAMOND_PICKAXE, Map.of("efficiency", 5, "unbreaking", 3, "fortune", 3)), 12);
        add(list, gear(Material.DIAMOND_SHOVEL, Map.of("efficiency", 5, "unbreaking", 3)), 8);
        add(list, gear(Material.SHIELD, Map.of("unbreaking", 3)), 10);
        add(list, potion(Material.SPLASH_POTION, PotionType.STRONG_STRENGTH, 1), 14);
        add(list, potion(Material.POTION, PotionType.STRONG_SWIFTNESS, 1), 14);
        add(list, potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE, 1), 14);
        add(list, potion(Material.POTION, PotionType.STRONG_REGENERATION, 1), 10);
        return list;
    }

    public static List<Entry> legendary() {
        List<Entry> list = new ArrayList<>();
        add(list, stack(Material.ENCHANTED_GOLDEN_APPLE, 1), 14);
        add(list, stack(Material.TOTEM_OF_UNDYING, 1), 28);
        add(list, stack(Material.NETHERITE_INGOT, 1), 14);
        add(list, stack(Material.DIAMOND_BLOCK, 2), 22);
        add(list, stack(Material.ELYTRA, 1), 8);
        add(list, stack(Material.END_CRYSTAL, 4), 14);
        add(list, stack(Material.GOLDEN_APPLE, 8), 28);
        add(list, gear(Material.DIAMOND_HELMET, Map.of("protection", 4, "unbreaking", 3, "mending", 1)), 16);
        add(list, gear(Material.DIAMOND_CHESTPLATE, Map.of("protection", 4, "unbreaking", 3, "mending", 1)), 16);
        add(list, gear(Material.DIAMOND_LEGGINGS, Map.of("protection", 4, "unbreaking", 3, "mending", 1)), 16);
        add(list, gear(Material.DIAMOND_BOOTS, Map.of("protection", 4, "unbreaking", 3, "feather_falling", 4, "mending", 1)), 16);
        add(list, gear(Material.NETHERITE_HELMET, Map.of("protection", 4, "unbreaking", 3)), 10);
        add(list, gear(Material.NETHERITE_CHESTPLATE, Map.of("protection", 4, "unbreaking", 3)), 8);
        add(list, gear(Material.NETHERITE_LEGGINGS, Map.of("protection", 4, "unbreaking", 3)), 8);
        add(list, gear(Material.NETHERITE_BOOTS, Map.of("protection", 4, "unbreaking", 3, "feather_falling", 4)), 10);
        add(list, gear(Material.NETHERITE_SWORD, Map.of("sharpness", 5, "unbreaking", 3, "fire_aspect", 2)), 10);
        add(list, gear(Material.DIAMOND_SWORD, Map.of("sharpness", 5, "unbreaking", 3, "looting", 3)), 14);
        add(list, gear(Material.BOW, Map.of("power", 5, "unbreaking", 3, "infinity", 1)), 12);
        add(list, gear(Material.MACE, Map.of("density", 3, "unbreaking", 3)), 6);
        add(list, gear(Material.NETHERITE_PICKAXE, Map.of("efficiency", 5, "unbreaking", 3, "fortune", 3)), 8);
        add(list, potion(Material.SPLASH_POTION, PotionType.STRONG_STRENGTH, 2), 16);
        add(list, potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 3), 14);
        add(list, potion(Material.POTION, PotionType.TURTLE_MASTER, 1), 8);
        add(list, potion(Material.POTION, PotionType.LONG_SLOW_FALLING, 1), 10);
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

    private static ItemStack potion(Material bottle, PotionType type, int amount) {
        ItemStack stack = new ItemStack(bottle, Math.max(1, amount));
        if (stack.getItemMeta() instanceof PotionMeta meta) {
            meta.setBasePotionType(type);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
