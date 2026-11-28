package ru.geyserevent.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ванильный лут 1.21.0+: броня з3/з4, оружие, инструменты, зелья, еда.
 * Предметы, которых нет на конкретной сборке, просто пропускаются.
 */
public final class VanillaEventLoot {

    public static final int MAX_SLOTS = 90;
    public static final int PAGE_SIZE = 36;

    public record Entry(ItemStack item, double chance) {
    }

    private VanillaEventLoot() {
    }

    public static String signature(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "";
        }
        try {
            if (stack.getItemMeta() instanceof PotionMeta meta && meta.getBasePotionType() != null) {
                return stack.getType().name() + ":" + meta.getBasePotionType().name();
            }
        } catch (Throwable ignored) {
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

    public static boolean isStarterJunk(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.startsWith("LEATHER_")
                || name.startsWith("CHAINMAIL_")
                || name.equals("GOLDEN_HELMET")
                || name.equals("GOLDEN_CHESTPLATE")
                || name.equals("GOLDEN_LEGGINGS")
                || name.equals("GOLDEN_BOOTS")
                || name.equals("STONE_SWORD")
                || name.equals("WOODEN_SWORD")
                || name.equals("WOODEN_AXE")
                || name.equals("WOODEN_PICKAXE")
                || name.equals("GOLDEN_SWORD")
                || name.equals("TURTLE_HELMET")
                || name.equals("COOKIE")
                || name.equals("SWEET_BERRIES")
                || name.equals("GLOW_BERRIES")
                || name.equals("PUMPKIN_PIE")
                || name.equals("MUSHROOM_STEW")
                || name.equals("RABBIT_STEW")
                || name.equals("SUSPICIOUS_STEW")
                || name.equals("COBBLESTONE")
                || name.equals("STONE");
    }

    public static boolean isPluginSeedKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return key.startsWith("v") || key.startsWith("g_");
    }

    public static List<Entry> common() {
        List<Entry> list = new ArrayList<>();
        item(list, "GOLDEN_APPLE", 4, 38);
        item(list, "ENDER_PEARL", 12, 34);
        item(list, "EXPERIENCE_BOTTLE", 24, 40);
        item(list, "DIAMOND", 4, 32);
        item(list, "IRON_INGOT", 16, 36);
        item(list, "GOLD_INGOT", 8, 30);
        item(list, "ARROW", 32, 26);
        item(list, "TOTEM_OF_UNDYING", 1, 8);
        item(list, "COOKED_BEEF", 16, 24);
        item(list, "GOLDEN_CARROT", 8, 20);
        item(list, "HONEY_BOTTLE", 4, 16);
        gear(list, "IRON_HELMET", 18, "protection", 3, "unbreaking", 3);
        gear(list, "IRON_CHESTPLATE", 16, "protection", 3, "unbreaking", 3);
        gear(list, "IRON_LEGGINGS", 16, "protection", 3, "unbreaking", 3);
        gear(list, "IRON_BOOTS", 18, "protection", 3, "unbreaking", 3, "feather_falling", 3);
        gear(list, "DIAMOND_HELMET", 14, "protection", 3, "unbreaking", 3);
        gear(list, "DIAMOND_CHESTPLATE", 10, "protection", 3, "unbreaking", 3);
        gear(list, "DIAMOND_LEGGINGS", 10, "protection", 3, "unbreaking", 3);
        gear(list, "DIAMOND_BOOTS", 14, "protection", 3, "unbreaking", 3, "feather_falling", 3);
        gear(list, "IRON_SWORD", 16, "sharpness", 3, "unbreaking", 3);
        gear(list, "DIAMOND_SWORD", 12, "sharpness", 3, "unbreaking", 3);
        gear(list, "IRON_AXE", 12, "sharpness", 3, "unbreaking", 3);
        gear(list, "BOW", 14, "power", 3, "unbreaking", 3);
        gear(list, "CROSSBOW", 8, "piercing", 2, "unbreaking", 3);
        gear(list, "SHIELD", 12, "unbreaking", 3);
        gear(list, "IRON_PICKAXE", 12, "efficiency", 4, "unbreaking", 3);
        gear(list, "DIAMOND_PICKAXE", 8, "efficiency", 4, "unbreaking", 3);
        add(list, potion("SPLASH_POTION", "HEALING", 2), 22);
        add(list, potion("POTION", "SWIFTNESS", 1), 16);
        add(list, potion("POTION", "STRENGTH", 1), 12);
        add(list, potion("POTION", "FIRE_RESISTANCE", 1), 12);
        add(list, potion("TIPPED_ARROW", "HEALING", 8), 12);
        return list;
    }

    public static List<Entry> rare() {
        List<Entry> list = new ArrayList<>();
        item(list, "GOLDEN_APPLE", 4, 36);
        item(list, "GOLDEN_CARROT", 16, 22);
        item(list, "HONEY_BOTTLE", 4, 14);
        item(list, "ENDER_PEARL", 12, 30);
        item(list, "DIAMOND", 6, 32);
        item(list, "EXPERIENCE_BOTTLE", 24, 34);
        item(list, "TNT", 8, 16);
        item(list, "TOTEM_OF_UNDYING", 1, 12);
        item(list, "WIND_CHARGE", 8, 10);
        gear(list, "IRON_HELMET", 14, "protection", 4, "unbreaking", 3);
        gear(list, "IRON_CHESTPLATE", 12, "protection", 4, "unbreaking", 3);
        gear(list, "IRON_LEGGINGS", 12, "protection", 4, "unbreaking", 3);
        gear(list, "IRON_BOOTS", 14, "protection", 4, "unbreaking", 3, "feather_falling", 4);
        gear(list, "DIAMOND_HELMET", 16, "protection", 4, "unbreaking", 3);
        gear(list, "DIAMOND_CHESTPLATE", 12, "protection", 3, "unbreaking", 3);
        gear(list, "DIAMOND_LEGGINGS", 14, "protection", 4, "unbreaking", 3);
        gear(list, "DIAMOND_BOOTS", 16, "protection", 4, "unbreaking", 3, "feather_falling", 4);
        gear(list, "DIAMOND_SWORD", 16, "sharpness", 4, "unbreaking", 3);
        gear(list, "IRON_SWORD", 12, "sharpness", 4, "unbreaking", 3, "knockback", 1);
        gear(list, "DIAMOND_AXE", 12, "sharpness", 3, "unbreaking", 3);
        gear(list, "BOW", 12, "power", 3, "punch", 1, "unbreaking", 3);
        gear(list, "CROSSBOW", 10, "piercing", 3, "unbreaking", 3);
        gear(list, "DIAMOND_PICKAXE", 12, "efficiency", 4, "unbreaking", 3);
        gear(list, "DIAMOND_HOE", 6, "efficiency", 4, "unbreaking", 3);
        gear(list, "SHIELD", 10, "unbreaking", 3);
        add(list, potion("SPLASH_POTION", "STRONG_HEALING", 2), 20);
        add(list, potion("POTION", "STRENGTH", 1), 14);
        add(list, potion("POTION", "LONG_FIRE_RESISTANCE", 1), 12);
        add(list, potion("POTION", "INVISIBILITY", 1), 8);
        add(list, potion("POTION", "LEAPING", 1), 10);
        add(list, potion("SPLASH_POTION", "SWIFTNESS", 1), 12);
        add(list, potion("TIPPED_ARROW", "STRONG_HEALING", 8), 12);
        add(list, potion("TIPPED_ARROW", "SLOWNESS", 8), 8);
        return list;
    }

    public static List<Entry> epic() {
        List<Entry> list = new ArrayList<>();
        item(list, "GOLDEN_APPLE", 6, 34);
        item(list, "ENCHANTED_GOLDEN_APPLE", 1, 6);
        item(list, "TOTEM_OF_UNDYING", 1, 20);
        item(list, "HONEY_BOTTLE", 4, 12);
        item(list, "GOLDEN_CARROT", 16, 18);
        item(list, "ENDER_PEARL", 16, 26);
        item(list, "DIAMOND", 8, 30);
        item(list, "EXPERIENCE_BOTTLE", 32, 30);
        item(list, "TNT", 16, 14);
        item(list, "END_CRYSTAL", 2, 12);
        item(list, "WIND_CHARGE", 16, 10);
        item(list, "BREEZE_ROD", 4, 8);
        item(list, "OBSIDIAN", 16, 16);
        gear(list, "DIAMOND_HELMET", 16, "protection", 4, "unbreaking", 3);
        gear(list, "DIAMOND_CHESTPLATE", 14, "protection", 4, "unbreaking", 3);
        gear(list, "DIAMOND_LEGGINGS", 14, "protection", 4, "unbreaking", 3);
        gear(list, "DIAMOND_BOOTS", 16, "protection", 4, "unbreaking", 3, "feather_falling", 4);
        gear(list, "IRON_CHESTPLATE", 10, "protection", 4, "unbreaking", 3);
        gear(list, "NETHERITE_BOOTS", 8, "protection", 3, "unbreaking", 3, "feather_falling", 4);
        gear(list, "DIAMOND_SWORD", 16, "sharpness", 4, "unbreaking", 3, "fire_aspect", 2);
        gear(list, "DIAMOND_AXE", 12, "sharpness", 4, "unbreaking", 3);
        gear(list, "NETHERITE_SWORD", 6, "sharpness", 4, "unbreaking", 3);
        gear(list, "BOW", 12, "power", 4, "unbreaking", 3, "flame", 1);
        gear(list, "CROSSBOW", 8, "multishot", 1, "quick_charge", 3, "unbreaking", 3);
        gear(list, "TRIDENT", 8, "loyalty", 3, "unbreaking", 3);
        gear(list, "DIAMOND_PICKAXE", 12, "efficiency", 5, "unbreaking", 3, "fortune", 3);
        gear(list, "DIAMOND_SHOVEL", 8, "efficiency", 5, "unbreaking", 3);
        gear(list, "DIAMOND_HOE", 6, "efficiency", 5, "unbreaking", 3);
        gear(list, "SHIELD", 10, "unbreaking", 3);
        gear(list, "FISHING_ROD", 8, "luck_of_the_sea", 3, "lure", 3, "unbreaking", 3);
        add(list, potion("SPLASH_POTION", "STRONG_STRENGTH", 1), 14);
        add(list, potion("POTION", "STRONG_SWIFTNESS", 1), 14);
        add(list, potion("POTION", "LONG_FIRE_RESISTANCE", 1), 14);
        add(list, potion("POTION", "STRONG_REGENERATION", 1), 10);
        add(list, potion("SPLASH_POTION", "STRONG_HEALING", 2), 16);
        add(list, potion("POTION", "LONG_INVISIBILITY", 1), 8);
        add(list, potion("POTION", "LONG_SLOW_FALLING", 1), 8);
        add(list, potion("TIPPED_ARROW", "STRONG_HEALING", 16), 12);
        add(list, potion("TIPPED_ARROW", "STRONG_HARMING", 8), 8);
        return list;
    }

    public static List<Entry> legendary() {
        List<Entry> list = new ArrayList<>();
        item(list, "ENCHANTED_GOLDEN_APPLE", 1, 14);
        item(list, "TOTEM_OF_UNDYING", 1, 26);
        item(list, "NETHERITE_INGOT", 1, 14);
        item(list, "DIAMOND_BLOCK", 2, 20);
        item(list, "ELYTRA", 1, 8);
        item(list, "END_CRYSTAL", 4, 14);
        item(list, "GOLDEN_APPLE", 8, 26);
        item(list, "HONEY_BOTTLE", 4, 10);
        item(list, "GOLDEN_CARROT", 32, 16);
        item(list, "HEAVY_CORE", 1, 4);
        item(list, "TRIAL_KEY", 1, 6);
        item(list, "OMINOUS_BOTTLE", 1, 6);
        gear(list, "DIAMOND_HELMET", 16, "protection", 4, "unbreaking", 3, "mending", 1);
        gear(list, "DIAMOND_CHESTPLATE", 16, "protection", 4, "unbreaking", 3, "mending", 1);
        gear(list, "DIAMOND_LEGGINGS", 16, "protection", 4, "unbreaking", 3, "mending", 1);
        gear(list, "DIAMOND_BOOTS", 16, "protection", 4, "unbreaking", 3, "feather_falling", 4, "mending", 1);
        gear(list, "NETHERITE_HELMET", 10, "protection", 4, "unbreaking", 3);
        gear(list, "NETHERITE_CHESTPLATE", 8, "protection", 4, "unbreaking", 3);
        gear(list, "NETHERITE_LEGGINGS", 8, "protection", 4, "unbreaking", 3);
        gear(list, "NETHERITE_BOOTS", 10, "protection", 4, "unbreaking", 3, "feather_falling", 4);
        gear(list, "NETHERITE_SWORD", 10, "sharpness", 5, "unbreaking", 3, "fire_aspect", 2);
        gear(list, "DIAMOND_SWORD", 14, "sharpness", 5, "unbreaking", 3, "looting", 3);
        gear(list, "NETHERITE_AXE", 8, "sharpness", 5, "unbreaking", 3);
        gear(list, "BOW", 12, "power", 5, "unbreaking", 3, "infinity", 1);
        gear(list, "CROSSBOW", 8, "piercing", 4, "quick_charge", 3, "unbreaking", 3);
        gear(list, "TRIDENT", 8, "loyalty", 3, "channeling", 1, "unbreaking", 3);
        gear(list, "MACE", 6, "density", 3, "unbreaking", 3);
        gear(list, "NETHERITE_PICKAXE", 8, "efficiency", 5, "unbreaking", 3, "fortune", 3);
        gear(list, "NETHERITE_SHOVEL", 6, "efficiency", 5, "unbreaking", 3);
        gear(list, "SHIELD", 10, "unbreaking", 3, "mending", 1);
        add(list, potion("SPLASH_POTION", "STRONG_STRENGTH", 2), 16);
        add(list, potion("SPLASH_POTION", "STRONG_HEALING", 3), 14);
        add(list, potion("POTION", "TURTLE_MASTER", 1), 8);
        add(list, potion("POTION", "LONG_SLOW_FALLING", 1), 10);
        add(list, potion("POTION", "STRONG_REGENERATION", 1), 10);
        add(list, potion("LINGERING_POTION", "STRONG_HEALING", 1), 8);
        add(list, potion("POTION", "LONG_FIRE_RESISTANCE", 1), 12);
        add(list, potion("TIPPED_ARROW", "STRONG_HEALING", 16), 12);
        add(list, potion("TIPPED_ARROW", "STRENGTH", 8), 8);
        return list;
    }

    private static void item(List<Entry> list, String material, int amount, double chance) {
        add(list, stack(material, amount), chance);
    }

    private static void gear(List<Entry> list, String material, double chance, Object... enchPairs) {
        ItemStack stack = stack(material, 1);
        if (stack == null) {
            return;
        }
        Map<String, Integer> enchants = new LinkedHashMap<>();
        for (int i = 0; i + 1 < enchPairs.length; i += 2) {
            if (enchPairs[i] instanceof String key && enchPairs[i + 1] instanceof Integer level) {
                enchants.put(key, level);
            }
        }
        LootStacks.apply(stack, null, List.of(), enchants);
        add(list, stack, chance);
    }

    private static void add(List<Entry> list, ItemStack item, double chance) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        list.add(new Entry(item, chance));
    }

    private static ItemStack stack(String material, int amount) {
        Material mat = Material.matchMaterial(material);
        if (mat == null || mat.isAir()) {
            return null;
        }
        try {
            return new ItemStack(mat, Math.max(1, amount));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ItemStack potion(String bottle, String type, int amount) {
        ItemStack stack = stack(bottle, amount);
        if (stack == null) {
            return null;
        }
        try {
            PotionType potionType = PotionType.valueOf(type);
            if (stack.getItemMeta() instanceof PotionMeta meta) {
                meta.setBasePotionType(potionType);
                stack.setItemMeta(meta);
            }
            return stack;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ItemStack stew(String effect, int seconds) {
        ItemStack stack = stack("SUSPICIOUS_STEW", 1);
        if (stack == null) {
            return stack("MUSHROOM_STEW", 1);
        }
        try {
            if (stack.getItemMeta() instanceof SuspiciousStewMeta meta) {
                PotionEffectType type = null;
                try {
                    type = Registry.EFFECT.get(NamespacedKey.minecraft(effect.toLowerCase()));
                } catch (Throwable ignored) {
                }
                if (type == null) {
                    type = PotionEffectType.getByName(effect);
                }
                if (type != null) {
                    meta.addCustomEffect(new PotionEffect(type, Math.max(20, seconds * 20), 0), true);
                    stack.setItemMeta(meta);
                }
            }
        } catch (Throwable ignored) {
        }
        return stack;
    }
}
