package ru.geyserevent.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Таблица лидеров по нанесённому урону Боссу Адского Разлома.
 * Используется для распределения наград:
 * 1 место — 1500 осколков
 * 2 место — 100 осколков
 * 3 место — 500 осколков
 * (настраивается в config.yml → rewards.places)
 */
public class DamageLeaderboard {

    public record DamageEntry(UUID uuid, String playerName, double damage) {
    }

    private final Map<UUID, Double> damageMap = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameMap = new ConcurrentHashMap<>();

    public void addDamage(UUID uuid, String playerName, double amount) {
        if (uuid == null || amount <= 0) {
            return;
        }
        if (playerName != null && !playerName.isBlank()) {
            nameMap.put(uuid, playerName);
        }
        damageMap.merge(uuid, amount, Double::sum);
    }

    public double getDamage(UUID uuid) {
        if (uuid == null) {
            return 0.0;
        }
        return damageMap.getOrDefault(uuid, 0.0);
    }

    /**
     * Возвращает отсортированный по убыванию урона список всех участников.
     */
    public List<DamageEntry> getSortedEntries() {
        List<DamageEntry> entries = new ArrayList<>(damageMap.size());
        for (Map.Entry<UUID, Double> e : damageMap.entrySet()) {
            UUID uuid = e.getKey();
            String name = nameMap.getOrDefault(uuid, "Игрок");
            entries.add(new DamageEntry(uuid, name, e.getValue()));
        }
        entries.sort(Comparator.comparingDouble(DamageEntry::damage).reversed());
        return entries;
    }

    /**
     * Возвращает топ-N игроков по урону.
     */
    public List<DamageEntry> getTop(int limit) {
        List<DamageEntry> sorted = getSortedEntries();
        if (sorted.size() <= limit) {
            return sorted;
        }
        return new ArrayList<>(sorted.subList(0, limit));
    }

    /**
     * Возвращает текущее место игрока (начиная с 1), или 0 если игрок ещё не наносил урон.
     */
    public int getPlayerRank(UUID uuid) {
        if (uuid == null || !damageMap.containsKey(uuid)) {
            return 0;
        }
        List<DamageEntry> sorted = getSortedEntries();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).uuid().equals(uuid)) {
                return i + 1;
            }
        }
        return 0;
    }

    public double getTotalDamage() {
        double sum = 0.0;
        for (double d : damageMap.values()) {
            sum += d;
        }
        return sum;
    }

    public boolean isEmpty() {
        return damageMap.isEmpty();
    }

    public void clear() {
        damageMap.clear();
        nameMap.clear();
    }
}
