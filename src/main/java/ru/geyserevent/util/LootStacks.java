package ru.geyserevent.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.geyserevent.config.PluginConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Сборка лута из конфига: имя, лор, чары (protection: 4 и т.д.). */
public final class LootStacks {

    private LootStacks() {
    }

    public static ItemStack fromAlien(PluginConfig.AlienLootItem item, int amount) {
        return build(item.material(), amount, item.displayName(), item.lore(), item.enchants());
    }

    public static ItemStack build(Material material, int amount, String name, List<String> lore, Map<String, Integer> enchants) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        apply(stack, name, lore, enchants);
        return stack;
    }

    public static void apply(ItemStack stack, String name, List<String> lore, Map<String, Integer> enchants) {
        if (stack == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) {
                meta.setDisplayName(name);
            }
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        applyEnchants(stack, enchants);
    }

    public static void applyEnchants(ItemStack stack, Map<String, Integer> enchants) {
        if (stack == null || enchants == null || enchants.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            Enchantment enchantment = matchEnchantment(entry.getKey());
            if (enchantment == null) {
                continue;
            }
            int level = Math.max(1, entry.getValue() == null ? 1 : entry.getValue());
            stack.addUnsafeEnchantment(enchantment, level);
        }
    }

    public static Map<String, Integer> readEnchants(Object raw) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (raw instanceof ConfigurationSection section) {
            raw = section.getValues(false);
        }
        if (!(raw instanceof Map<?, ?> map)) {
            return out;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            int level;
            try {
                level = Integer.parseInt(String.valueOf(entry.getValue()));
            } catch (NumberFormatException ex) {
                continue;
            }
            out.put(String.valueOf(entry.getKey()), level);
        }
        return out;
    }

    public static Enchantment matchEnchantment(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        int colon = key.indexOf(':');
        if (colon >= 0) {
            key = key.substring(colon + 1);
        }
        key = switch (key) {
            case "prot", "protection_environmental", "защита" -> "protection";
            case "sharp", "острота" -> "sharpness";
            case "eff", "efficiency", "эффективность" -> "efficiency";
            case "unb", "unbreaking", "прочность" -> "unbreaking";
            case "fort", "удача" -> "fortune";
            case "fall", "featherfalling" -> "feather_falling";
            default -> key;
        };
        Enchantment byKey = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        if (byKey != null) {
            return byKey;
        }
        for (Enchantment candidate : Registry.ENCHANTMENT) {
            if (candidate.getKey().getKey().equalsIgnoreCase(key)
                    || candidate.getKey().getKey().replace("_", "").equalsIgnoreCase(key.replace("_", ""))) {
                return candidate;
            }
        }
        return null;
    }
}
