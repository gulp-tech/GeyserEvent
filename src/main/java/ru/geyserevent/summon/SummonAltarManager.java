package ru.geyserevent.summon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.event.EventType;
import ru.geyserevent.event.InfernalRiftManager;
import ru.geyserevent.hook.BedrockService;
import ru.geyserevent.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Призывные ивенты: алтарь призыва «Адского Разлома» из специальных блоков
 * с кастомными текстурами ресурспака.
 *
 * <p>Как работает:
 * <ol>
 *   <li>Админ ставит алтарь: {@code /rift summonaltar} — появляется центральный блок
 *       и 4 пилона по сторонам света (каждый блок со своей текстурой из пака);</li>
 *   <li>игрок нажимает ПКМ по алтарю — начинается отсчёт ({@code summon.wait-seconds},
 *       по умолчанию 2 минуты), в чат и в панель угла идёт таймер;</li>
 *   <li>когда отсчёт кончился — на этом месте стартует обычный «Адский Разлом»
 *       ({@link InfernalRiftManager#startEventAt(Location, EventType, Integer)});</li>
 *   <li>если игроков рядом нет или босса не бьют — призыв/ивент отменяется,
 *       а алтарь <b>не выпадает</b> предметом (исчезает);</li>
 *   <li>если босса не убили за {@code summon.boss-timeout-seconds} (по умолчанию
 *       5 минут) — он исчезает и в чат уходит сообщение из конфига.</li>
 * </ol>
 *
 * Призыв работает только в мире из {@code summon.world} (PvP-мир) — в остальных
 * мирах алтарь отвечает, что тут призывать нельзя.
 */
public final class SummonAltarManager {

    /** Маркер панели в углу на время призыва (ресурспак рисует «ПРИЗЫВ БОССА»). */
    public static final String HUD_MARKER_SUMMON = "#GE_S#";

    /** Показана ли сейчас панель призыва (чтобы убрать её после окончания). */
    private boolean summonHudVisible = false;

    /** Ключ предмета-призывалки в PDC (если алтарь выдать игроку командой). */
    private final NamespacedKey altarItemKey;

    private final GeyserEventPlugin plugin;
    private final File dataFile;

    /** Активные (зарегистрированные) алтари: координаты → запись. */
    private final List<Altar> altars = new ArrayList<>();

    /** Идущий прямо сейчас призыв (null — нет). */
    private Ritual ritual;

    private BukkitRunnable tickTask;

    /** Состояние одного алтаря. */
    public static final class Altar {
        public final String world;
        public final int x;
        public final int y;
        public final int z;

        Altar(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x + 0.5, y, z + 0.5);
        }

        String key() {
            return world + ":" + x + "," + y + "," + z;
        }
    }

    /** Идущий призыв. */
    private static final class Ritual {
        Altar altar;
        int secondsLeft;
        /** Сколько секунд подряд нет игроков в радиусе. */
        int noPlayersSeconds;
        /** Сколько секунд подряд боссу не наносили урон. */
        int noDamageSeconds;
        /** Призыв уже превратился в босса. */
        boolean bossSpawned;
        long lastDamageMillis;
    }

    public SummonAltarManager(GeyserEventPlugin plugin) {
        this.plugin = plugin;
        this.altarItemKey = new NamespacedKey(plugin, "summon_altar_item");
        this.dataFile = new File(plugin.getDataFolder(), "summon_altars.yml");
        load();
        startTickLoop();
    }

    // ------------------------------------------------------------------ хранение

    private void load() {
        altars.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        List<?> raw = yml.getList("altars");
        if (raw == null) {
            return;
        }
        for (Object o : raw) {
            if (!(o instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            Object w = map.get("world");
            Object x = map.get("x");
            Object y = map.get("y");
            Object z = map.get("z");
            if (w == null || x == null || y == null || z == null) {
                continue;
            }
            try {
                altars.add(new Altar(String.valueOf(w), Integer.parseInt(String.valueOf(x)),
                        Integer.parseInt(String.valueOf(y)), Integer.parseInt(String.valueOf(z))));
            } catch (NumberFormatException ignored) {
            }
        }
        if (!altars.isEmpty()) {
            plugin.getLogger().info("Загружено алтарей призыва: " + altars.size());
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        List<java.util.Map<String, Object>> out = new ArrayList<>();
        for (Altar a : altars) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("world", a.world);
            m.put("x", a.x);
            m.put("y", a.y);
            m.put("z", a.z);
            out.add(m);
        }
        yml.set("altars", out);
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить summon_altars.yml: " + ex.getMessage());
        }
    }

    /** Алтари призыва (только для чтения). */
    public List<Altar> getAltars() {
        return new ArrayList<>(altars);
    }

    // ------------------------------------------------------------- постройка

    /** Смещения 4 пилонов относительно алтаря (стороны света, одна и та же высота). */
    private static final int[][] PYLON_OFFSETS = {{4, 0}, {-4, 0}, {0, 4}, {0, -4}};

    /**
     * Ставит алтарь призыва в указанной точке: центральный блок + 4 пилона.
     *
     * @return true, если алтарь построен и зарегистрирован
     */
    public boolean createAltar(Location center) {
        if (center == null || center.getWorld() == null) {
            return false;
        }
        PluginConfig cfg = plugin.getPluginConfig();
        Block base = center.getBlock();
        boolean alreadyAltar = base.getType() == cfg.getSummonAltarBlock();
        if (!alreadyAltar && !base.getType().isAir() && !base.isLiquid()) {
            // если в точке уже что-то стоит — ставим на блок выше
            // (но если это только что поставленный предмет-призывалка — оставляем как есть)
            base = base.getRelative(0, 1, 0);
        }
        base.setType(cfg.getSummonAltarBlock(), false);

        Material[] pylons = {cfg.getSummonPylonBlock1(), cfg.getSummonPylonBlock2(),
                cfg.getSummonPylonBlock3(), cfg.getSummonPylonBlock4()};
        for (int i = 0; i < PYLON_OFFSETS.length; i++) {
            int dx = PYLON_OFFSETS[i][0];
            int dz = PYLON_OFFSETS[i][1];
            Location pylonLoc = base.getLocation().clone().add(dx, 0, dz);
            Block pylon = pylonLoc.getBlock();
            if (pylon.getType().isAir() || pylon.isLiquid()) {
                // под пилоном должна быть твёрдая опора
                Block below = pylon.getRelative(0, -1, 0);
                if (below.getType().isAir()) {
                    below.setType(Material.POLISHED_DEEPSLATE, false);
                }
                pylon.setType(pylons[i], false);
            }
        }

        Altar altar = new Altar(base.getWorld().getName(), base.getX(), base.getY(), base.getZ());
        for (Altar existing : altars) {
            if (existing.key().equals(altar.key())) {
                return true;
            }
        }
        altars.add(altar);
        save();
        World w = base.getWorld();
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, base.getLocation().add(0.5, 1.0, 0.5), 60, 0.6, 0.8, 0.6, 0.02);
        w.playSound(base.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 2.0f, 0.7f);
        return true;
    }

    /** Убирает алтарь (блоки исчезают без дропа) и снимает его с регистрации. */
    public boolean removeAltar(Location anyBlockOfAltar) {
        Altar altar = findAltar(anyBlockOfAltar);
        if (altar == null) {
            return false;
        }
        removeAltar(altar);
        return true;
    }

    private void removeAltar(Altar altar) {
        Location loc = altar.toLocation();
        if (loc != null && loc.getWorld() != null) {
            loc.getBlock().setType(Material.AIR, false);
            for (int[] off : PYLON_OFFSETS) {
                loc.clone().add(off[0], 0, off[1]).getBlock().setType(Material.AIR, false);
            }
        }
        altars.remove(altar);
        save();
    }

    /** Предмет-призывалка: выдать игроку, чтобы он мог ставить алтари сам. */
    public ItemStack createAltarItem(int amount) {
        PluginConfig cfg = plugin.getPluginConfig();
        ItemStack item = new ItemStack(cfg.getSummonAltarBlock(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color("&6&lАлтарь Призыва &7(ПКМ — поставить)"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&7Поставьте блок — рядом сами"));
            lore.add(ColorUtil.color("&7появятся 4 пилона призыва."));
            lore.add(ColorUtil.color("&7ПКМ по алтарю — призыв босса."));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(altarItemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isAltarItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(altarItemKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------- поиск

    /** Алтарь, если точка — его центральный блок. */
    public Altar findAltar(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        for (Altar a : altars) {
            if (a.world.equals(loc.getWorld().getName())
                    && a.x == loc.getBlockX() && a.y == loc.getBlockY() && a.z == loc.getBlockZ()) {
                return a;
            }
        }
        return null;
    }

    /** Алтарь, если точка — его пилон. */
    public Altar findAltarByPylon(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        for (Altar a : altars) {
            Location base = a.toLocation();
            if (base == null) {
                continue;
            }
            for (int[] off : PYLON_OFFSETS) {
                if (base.clone().add(off[0], 0, off[1]).getBlock().getLocation().equals(loc.getBlock().getLocation())) {
                    return a;
                }
            }
        }
        return null;
    }

    public boolean isSummonBlock(Block block) {
        return block != null && (findAltar(block.getLocation()) != null
                || findAltarByPylon(block.getLocation()) != null);
    }

    // ------------------------------------------------------------- призыв

    /**
     * ПКМ по алтарю: проверяет мир, занятость и запускает отсчёт.
     *
     * @return сообщение для игрока (уже с цветами) или null, если призыв пошёл
     */
    public String tryStartRitual(Player player, Block block) {
        PluginConfig cfg = plugin.getPluginConfig();
        if (!cfg.isSummonEnabled()) {
            return ColorUtil.color("&cПризывные ивенты выключены в конфиге (summon.enabled: false).");
        }
        Altar altar = findAltar(block.getLocation());
        if (altar == null) {
            return null;
        }
        if (!altar.world.equalsIgnoreCase(cfg.getSummonWorldName())) {
            return ColorUtil.color("&cПризыв работает только в мире &e" + cfg.getSummonWorldName() + "&c!");
        }
        if (ritual != null) {
            return ColorUtil.color("&cПризыв уже идёт! Дождитесь появления босса.");
        }
        InfernalRiftManager manager = plugin.getRiftManager();
        if (manager != null && manager.isActive() && !manager.isDefeated()) {
            return ColorUtil.color("&cСейчас уже идёт ивент — дождитесь его окончания.");
        }
        Location loc = altar.toLocation();
        if (loc == null) {
            return ColorUtil.color("&cМир алтаря не загружен — призыв невозможен.");
        }

        ritual = new Ritual();
        ritual.altar = altar;
        ritual.secondsLeft = cfg.getSummonWaitSeconds();
        ritual.noPlayersSeconds = 0;
        ritual.noDamageSeconds = 0;
        ritual.bossSpawned = false;
        ritual.lastDamageMillis = System.currentTimeMillis();

        Bukkit.broadcastMessage(ColorUtil.color(
                "&e\u253c &6&lПРИЗЫВ БОССА! &f" + player.getName()
                        + " \u0430\u043a\u0442\u0438\u0432\u0438\u0440\u043e\u0432\u0430\u043b \u0430\u043b\u0442\u0430\u0440\u044c \u0432 "
                        + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                        + " \u2014 \u0431\u043e\u0441\u0441 \u043f\u043e\u044f\u0432\u0438\u0442\u0441\u044f \u0447\u0435\u0440\u0435\u0437 "
                        + formatTime(cfg.getSummonWaitSeconds()) + "!"));
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.8f);
        loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0.5, 1.2, 0.5),
                120, 0.8, 1.0, 0.8, 0.03);
        return null;
    }

    /** Ломать блоки алтаря во время призыва нельзя — призыв сорвётся, блок не выпадет. */
    public String handleBreak(Player player, Block block) {
        if (ritual == null) {
            // без призыва алтарь тоже не ломается: снести его можно только командой
            return ColorUtil.color("&c\u0410\u043b\u0442\u0430\u0440\u044c \u043f\u0440\u0438\u0437\u044b\u0432\u0430 \u043d\u0435\u043b\u044c\u0437\u044f \u0441\u043b\u043e\u043c\u0430\u0442\u044c \u2014 \u0442\u043e\u043b\u044c\u043a\u043e \u043a\u043e\u043c\u0430\u043d\u0434\u043e\u0439 &e/rift summonremove&c.");
        }
        Altar altar = findAltar(block.getLocation());
        if (altar == null) {
            altar = findAltarByPylon(block.getLocation());
        }
        if (altar == null) {
            return null;
        }
        cancelRitual("&c\u041f\u0440\u0438\u0437\u044b\u0432 \u0441\u043e\u0440\u0432\u0430\u043d \u2014 \u0430\u043b\u0442\u0430\u0440\u044c \u0440\u0430\u0437\u0440\u0443\u0448\u0435\u043d!");
        return ColorUtil.color("&c\u0410\u043b\u0442\u0430\u0440\u044c \u043f\u0440\u0438\u0437\u044b\u0432\u0430 \u0438\u0441\u0447\u0435\u0437 \u2014 \u043e\u043d \u043d\u0435 \u0432\u044b\u043f\u0430\u0434\u0430\u0435\u0442 \u043f\u0440\u0435\u0434\u043c\u0435\u0442\u043e\u043c.");
    }

    /** Урон по призванному боссу — сбрасывает таймер «босса не бьют». */
    public void noteBossDamage() {
        if (ritual != null) {
            ritual.lastDamageMillis = System.currentTimeMillis();
            ritual.noDamageSeconds = 0;
        }
    }

    public boolean isRitualActive() {
        return ritual != null;
    }

    public boolean isSummonedBossActive() {
        return ritual != null && ritual.bossSpawned;
    }

    /** Идёт ли призыв (отсчёт) прямо сейчас. */
    public int getRitualSecondsLeft() {
        return ritual == null ? 0 : Math.max(0, ritual.secondsLeft);
    }

    private void cancelRitual(String message) {
        if (ritual == null) {
            return;
        }
        Ritual r = ritual;
        ritual = null;
        stopSummonHud();
        InfernalRiftManager manager = plugin.getRiftManager();
        if (r.bossSpawned && manager != null && manager.isActive()) {
            manager.stopEvent(false);
        }
        // алтарь не выпадает: просто исчезает
        if (!plugin.getPluginConfig().isSummonDropAltarOnFail()) {
            removeAltar(r.altar);
        } else {
            Location loc = r.altar.toLocation();
            if (loc != null && loc.getWorld() != null) {
                loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1.0, 0.5),
                        createAltarItem(1));
            }
            removeAltar(r.altar);
        }
        Bukkit.broadcastMessage(ColorUtil.color("&e\u253c " + message));
    }

    private void finishRitual() {
        if (ritual == null) {
            return;
        }
        Ritual r = ritual;
        Location loc = r.altar.toLocation();
        if (loc == null) {
            ritual = null;
            removeAltar(r.altar);
            return;
        }
        PluginConfig cfg = plugin.getPluginConfig();
        Bukkit.broadcastMessage(ColorUtil.color(
                "&e\u253c &6&l\u0410\u0414\u0421\u041a\u0418\u0419 \u0420\u0410\u0417\u041b\u041e\u041c \u041e\u0422\u041a\u0420\u042b\u0422! &f\u0411\u043e\u0441\u0441 \u043f\u0440\u0438\u0437\u0432\u0430\u043d \u0432 "
                        + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 1.2f);
        loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0.5, 1.0, 0.5),
                200, 1.2, 1.5, 1.2, 0.05);

        InfernalRiftManager manager = plugin.getRiftManager();
        boolean started = manager != null
                && manager.startEventAt(loc, EventType.INFERNAL_RIFT, cfg.getSummonBossTimeoutSeconds(), true);
        if (started) {
            // алтарь съеден цитаделью — снимаем с регистрации, чтобы не было «призрака»
            altars.remove(r.altar);
            save();
            ritual.bossSpawned = true;
            ritual.secondsLeft = 0;
            ritual.noDamageSeconds = 0;
            ritual.noPlayersSeconds = 0;
            ritual.lastDamageMillis = System.currentTimeMillis();
            // отсчёт кончился — дальше панелью владеет обычная панель ивента
            stopSummonHud();
        } else {
            ritual = null;
            stopSummonHud();
            removeAltar(r.altar);
            Bukkit.broadcastMessage(ColorUtil.color(
                    "&e\u253c &c\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043f\u043e\u0441\u0442\u0440\u043e\u0438\u0442\u044c \u0430\u0440\u0435\u043d\u0443 \u0431\u043e\u0441\u0441\u0430 \u2012 \u043f\u0440\u0438\u0437\u044b\u0432 \u043e\u0442\u043c\u0435\u043d\u0451\u043d."));
        }
    }

    // ------------------------------------------------------------- цикл

    private void startTickLoop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void tick() {
        PluginConfig cfg = plugin.getPluginConfig();
        if (ritual == null || !cfg.isSummonEnabled()) {
            // призыва больше нет — страхуем панель на экране
            stopSummonHud();
            return;
        }
        Location loc = ritual.altar.toLocation();
        if (loc == null || loc.getWorld() == null) {
            cancelRitual("&c\u041c\u0438\u0440 \u0430\u043b\u0442\u0430\u0440\u044f \u043d\u0435 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d \u2014 \u043f\u0440\u0438\u0437\u044b\u0432 \u043e\u0442\u043c\u0435\u043d\u0451\u043d.");
            return;
        }

        int playersNear = countPlayersNear(loc, cfg.getSummonPlayerRadius());
        if (playersNear == 0) {
            ritual.noPlayersSeconds++;
        } else {
            ritual.noPlayersSeconds = 0;
        }

        if (!ritual.bossSpawned) {
            // ---- отсчёт до появления босса ----
            ritual.secondsLeft--;
            spawnRitualParticles(loc, ritual.secondsLeft);

            int left = ritual.secondsLeft;
            if (left > 0 && (left == cfg.getSummonWaitSeconds() / 2 || left == 30 || left == 10
                    || (left <= 5 && left > 0))) {
                Bukkit.broadcastMessage(ColorUtil.color(
                        "&e\u253c &6\u041f\u0440\u0438\u0437\u044b\u0432 \u0431\u043e\u0441\u0441\u0430 \u0447\u0435\u0440\u0435\u0437 &f" + formatTime(left)
                                + " &6\u2014 " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
            }
            if (left <= 0) {
                finishRitual();
                return;
            }
        } else {
            // ---- босс уже призван: следим, чтобы ивент не завис ----
            InfernalRiftManager manager = plugin.getRiftManager();
            if (manager == null || !manager.isActive() || manager.isDefeated()) {
                // босса убили или ивент закончился сам — призыв отработал
                ritual = null;
                stopSummonHud();
                return;
            }
            LivingEntity boss = manager.getBossEntity();
            if (boss == null || !boss.isValid()) {
                ritual = null;
                stopSummonHud();
                return;
            }
        }

        if (ritual == null) {
            return;
        }

        // ---- общие причины отмены ----
        if (ritual.noPlayersSeconds >= cfg.getSummonIdleNoPlayersSeconds()) {
            cancelRitual("&c\u0420\u044f\u0434\u043e\u043c \u0441 \u0430\u043b\u0442\u0430\u0440\u0451\u043c \u043d\u0438\u043a\u043e\u0433\u043e \u043d\u0435\u0442 \u2014 \u043f\u0440\u0438\u0437\u044b\u0432 \u0440\u0430\u0441\u0441\u044b\u043f\u0430\u0435\u0442\u0441\u044f.");
            return;
        }
        long sinceDamageSeconds = (System.currentTimeMillis() - ritual.lastDamageMillis) / 1000L;
        if (ritual.bossSpawned && sinceDamageSeconds >= cfg.getSummonIdleNoDamageSeconds()) {
            cancelRitual("&c\u0411\u043e\u0441\u0441\u0430 \u043d\u0435 \u0430\u0442\u0430\u043a\u043e\u0432\u0430\u043b\u0438 \u0441\u043b\u0438\u0448\u043a\u043e\u043c \u0434\u043e\u043b\u0433\u043e \u2014 \u043e\u043d \u0432\u0435\u0440\u043d\u0443\u043b\u0441\u044f \u0432 \u0420\u0430\u0437\u043b\u043e\u043c.");
            return;
        }

        // ---- панель в правом верхнем углу (Bedrock), только пока идёт отсчёт ----
        // после появления босса панелью владеет обычная панель ивента,
        // иначе две панели будут мигать каждую секунду
        if (!ritual.bossSpawned) {
            sendSummonHud(loc, ritual.secondsLeft);
        }
    }

    /**
     * Убирает панель призыва с экрана Bedrock-игроков.
     *
     * <p>Ресурспак рисует панель, пока в action bar есть маркер #GE_S#.
     * Если просто перестать слать payload и не отправить пустую строку,
     * панель останется висеть на экране бесконечно.</p>
     */
    private void stopSummonHud() {
        if (!summonHudVisible) {
            return;
        }
        summonHudVisible = false;
        if (plugin.getTopRightHudManager() != null) {
            plugin.getTopRightHudManager().clearCornerHud();
        }
    }

    private void sendSummonHud(Location loc, int secondsLeft) {
        BedrockService bedrock = plugin.getBedrockService();
        if (bedrock == null) {
            return;
        }
        String status = ritual.bossSpawned ? "\u0411\u043e\u0441\u0441 \u043f\u0440\u0438\u0437\u0432\u0430\u043d!" : "\u0411\u043e\u0441\u0441 \u043f\u043e\u044f\u0432\u0438\u0442\u0441\u044f \u0447\u0435\u0440\u0435\u0437";
        String payload = HUD_MARKER_SUMMON + "|" + formatTime(secondsLeft) + "~|" + status + "~|"
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != null && player.isOnline() && bedrock.isBedrockPlayer(player)) {
                player.sendActionBar(payload);
            }
        }
        // панель на экране — её нужно будет убрать, когда отсчёт кончится
        summonHudVisible = true;
        plugin.getTopRightHudManager().noteCornerHudActive();
    }

    private int countPlayersNear(Location loc, double radius) {
        int count = 0;
        Collection<? extends Player> players = loc.getWorld().getPlayers();
        double rSq = radius * radius;
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(loc) <= rSq) {
                count++;
            }
        }
        return count;
    }

    private void spawnRitualParticles(Location loc, int secondsLeft) {
        World w = loc.getWorld();
        if (w == null) {
            return;
        }
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0.5, 1.1, 0.5), 25, 0.5, 0.6, 0.5, 0.01);
        w.spawnParticle(Particle.SMOKE, loc.clone().add(0.5, 0.2, 0.5), 12, 1.2, 0.1, 1.2, 0.01);
        if (secondsLeft % 10 == 0) {
            w.playSound(loc, Sound.BLOCK_BEACON_AMBIENT, 1.4f, 1.4f);
        }
    }

    private static String formatTime(int totalSeconds) {
        int s = Math.max(0, totalSeconds);
        return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }

    /** Вызывается при выключении плагина: гасим цикл. */
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    /** Текст для /rift status: сколько алтарей и что сейчас происходит. */
    public String describe() {
        PluginConfig cfg = plugin.getPluginConfig();
        StringBuilder sb = new StringBuilder();
        sb.append("\u0430\u043b\u0442\u0430\u0440\u0435\u0439: ").append(altars.size())
                .append(", \u043c\u0438\u0440 \u043f\u0440\u0438\u0437\u044b\u0432\u0430: ").append(cfg.getSummonWorldName());
        if (ritual != null) {
            if (ritual.bossSpawned) {
                sb.append(", \u0441\u0435\u0439\u0447\u0430\u0441: \u0431\u043e\u0441\u0441 \u043f\u0440\u0438\u0437\u0432\u0430\u043d");
            } else {
                sb.append(", \u0441\u0435\u0439\u0447\u0430\u0441: \u043e\u0442\u0441\u0447\u0451\u0442 ").append(formatTime(ritual.secondsLeft));
            }
        } else {
            sb.append(", \u0441\u0435\u0439\u0447\u0430\u0441: \u043f\u0440\u043e\u0441\u0442\u043e\u0439");
        }
        return sb.toString();
    }

    /** Для /rift status: список координат алтарей. */
    public List<String> describeAltars() {
        List<String> out = new ArrayList<>();
        for (Altar a : altars) {
            out.add(a.world + " " + a.x + " " + a.y + " " + a.z);
        }
        return out;
    }
}
