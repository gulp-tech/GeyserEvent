package ru.geyserevent.event;

import java.util.Locale;

/**
 * Тип активного ивента:
 * 1) INFERNAL_RIFT — «Адский Разлом (Вторжение Босса)»
 * 2) BEACH_EXCAVATION — «Пляжные Раскопки (2-метровый Житель с кисточками)»
 * 3) ALCHEMIST_CAULDRON — «Котёл Алхимика (Мистическое болото 31x31, сбор реагентов и варка в котле)»
 * 4) ALIEN_INVASION — «Вторжение Пришельцев (посадочная площадь 25x25, босс-пришелец и 4 сундука)»
 * 5) GRAVITY_HOARD — «Сердце Ветра» (гравитация: металл тянет к ядру, без брони — полёт к шалкерам)
 */
public enum EventType {

    INFERNAL_RIFT("rift", "Адский Разлом"),
    BEACH_EXCAVATION("excavation", "Пляжные Раскопки"),
    ALCHEMIST_CAULDRON("cauldron", "Котёл Алхимика"),
    ALIEN_INVASION("alien", "Вторжение Пришельцев"),
    GRAVITY_HOARD("wind", "Сердце Ветра"),
    SKIBIDI("skibidi", "Шкибиди Туалет"),
    RONALDO("ronaldo", "Роналду SIUU"),
    FISHING("fishing", "Рыбалка");

    private final String id;
    private final String displayName;

    EventType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static EventType fromString(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "rift", "boss", "адский", "разлом", "1" -> INFERNAL_RIFT;
            case "excavation", "beach", "пляж", "раскопки", "2" -> BEACH_EXCAVATION;
            case "cauldron", "alchemist", "alchemy", "котел", "котёл", "алхимик", "болото", "3" -> ALCHEMIST_CAULDRON;
            case "alien", "invasion", "пришелец", "пришельцы", "ufo", "4" -> ALIEN_INVASION;
            case "wind", "gravity", "hoard", "breeze", "ветер", "сердце", "клад", "5" -> GRAVITY_HOARD;
            case "skibidi", "toilet", "туалет", "шкибиди", "6" -> SKIBIDI;
            case "ronaldo", "cr7", "siuu", "siu", "роналду", "рональдо", "7" -> RONALDO;
            case "fishing", "fish", "рыбалка", "удочка", "rybalka", "8" -> FISHING;
            default -> null;
        };
    }
}
