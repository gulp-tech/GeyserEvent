package ru.geyserevent.boss;

/**
 * Типы способностей Босса Адского Разлома (Брутального Пиглина).
 */
public enum BossAbilityType {

    PULL("Притягивание", "Притягивает всех игроков в радиусе 15 блоков к боссу"),
    PUSH("Отталкивание", "Отбрасывает всех игроков в радиусе 15 блоков мощной взрывной волной"),
    GATHER("Стяжка в одну точку", "Собирает всех игроков в радиусе 15 блоков в одну точку перед боссом"),
    SUMMON_MINIONS("Призыв приспешников", "Призывает свиту приспешников из Адского Разлома");

    private final String displayName;
    private final String description;

    BossAbilityType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
