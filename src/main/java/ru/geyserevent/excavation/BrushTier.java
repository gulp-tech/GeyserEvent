package ru.geyserevent.excavation;

import java.util.Locale;

/**
 * Редкость кисточки, которую выдаёт 2-метровый Житель-Археолог на пляже раз в 30 секунд.
 */
public enum BrushTier {

    COMMON("common", "Обычная", "&aОбычная кисточка", 3),
    EPIC("epic", "Эпическая", "&dЭпическая кисточка", 4),
    LEGENDARY("legendary", "Легендарная", "&6Легендарная кисточка", 5);

    private final String configKey;
    private final String displayName;
    private final String itemTitle;
    private final int defaultUses;

    BrushTier(String configKey, String displayName, String itemTitle, int defaultUses) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.itemTitle = itemTitle;
        this.defaultUses = defaultUses;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getItemTitle() {
        return itemTitle;
    }

    public int getDefaultUses() {
        return defaultUses;
    }

    public static BrushTier fromString(String input) {
        if (input == null) {
            return null;
        }
        return switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "common", "обычная", "обычный", "1" -> COMMON;
            case "epic", "эпическая", "эпический", "эпик", "2" -> EPIC;
            case "legendary", "легендарная", "легендарный", "лега", "3" -> LEGENDARY;
            default -> null;
        };
    }
}
