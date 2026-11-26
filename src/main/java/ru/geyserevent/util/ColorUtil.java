package ru.geyserevent.util;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилита для форматирования текста (включая HEX &#RRGGBB) и расчёта направления GPS-трекера.
 */
public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtil() {
    }

    /**
     * Переводит цветовые коды & и HEX (&#RRGGBB) в формат Minecraft.
     */
    @SuppressWarnings("deprecation")
    public static String color(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Вычисляет стрелку направления относительно взгляда игрока (для GPS-трекера /event gps).
     */
    public static String getDirectionArrow(Player player, Location target) {
        if (player == null || target == null || !player.getWorld().equals(target.getWorld())) {
            return "•";
        }
        Location playerLoc = player.getLocation();
        Vector toTarget = target.toVector().subtract(playerLoc.toVector()).setY(0);
        if (toTarget.lengthSquared() < 0.01) {
            return "✔";
        }
        toTarget.normalize();

        Vector lookDir = playerLoc.getDirection().setY(0);
        if (lookDir.lengthSquared() < 0.01) {
            return "⬆";
        }
        lookDir.normalize();

        double angle = Math.toDegrees(Math.atan2(
                lookDir.getX() * toTarget.getZ() - lookDir.getZ() * toTarget.getX(),
                lookDir.getX() * toTarget.getX() + lookDir.getZ() * toTarget.getZ()
        ));
        if (angle < 0) {
            angle += 360.0;
        }

        if (angle >= 337.5 || angle < 22.5) return "⬆";
        if (angle < 67.5) return "↗";
        if (angle < 112.5) return "➡";
        if (angle < 157.5) return "↘";
        if (angle < 202.5) return "⬇";
        if (angle < 247.5) return "↙";
        if (angle < 292.5) return "⬅";
        return "↖";
    }

    /**
     * Форматирует секунды в ММ:СС.
     */
    public static String formatTime(long totalSeconds) {
        if (totalSeconds < 0) {
            totalSeconds = 0;
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static String formatDuration(long totalSeconds) {
        return formatTime(totalSeconds);
    }

    /**
     * Форматирует секунды в читаемый вид для /event delay (например: "42 мин. 15 сек.").
     */
    public static String formatDurationVerbose(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "несколько секунд";
        }
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append(" ч. ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append(" мин. ");
        }
        sb.append(seconds).append(" сек.");
        return sb.toString().trim();
    }
}
