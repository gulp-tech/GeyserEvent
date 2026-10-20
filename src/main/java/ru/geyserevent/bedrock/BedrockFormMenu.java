package ru.geyserevent.bedrock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import ru.geyserevent.GeyserEventPlugin;
import ru.geyserevent.boss.BossAbilityType;
import ru.geyserevent.config.PluginConfig;
import ru.geyserevent.event.DamageLeaderboard;
import ru.geyserevent.event.InfernalRiftManager;
import ru.geyserevent.util.ColorUtil;

import java.util.List;

/**
 * Нативные окна Bedrock Edition (Cumulus SimpleForm / CustomForm)
 * для игроков GeyserMC.
 */
public class BedrockFormMenu {

    private final GeyserEventPlugin plugin;

    public BedrockFormMenu(GeyserEventPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Открывает главное меню ивента «Адский Разлом» (SimpleForm на Bedrock).
     */
    public void openMainForm(Player player) {
        if (!plugin.getBedrockService().canParticipate(player)) {
            player.sendMessage(plugin.getPluginConfig().getMsgOnlyBedrock());
            return;
        }

        InfernalRiftManager manager = plugin.getRiftManager();
        PluginConfig cfg = plugin.getPluginConfig();

        StringBuilder content = new StringBuilder();
        content.append("§4§l🔥 ВТОРЖЕНИЕ БОССА: АДСКИЙ РАЗЛОМ 🔥\n\n");

        if (manager.isActive() && manager.getRiftCenterLocation() != null) {
            Location loc = manager.getRiftCenterLocation();
            int curHp = (int) Math.ceil(manager.getBossCurrentHealth());
            int maxHp = (int) Math.ceil(manager.getBossMaxHealth());
            double myDamage = manager.getLeaderboard().getDamage(player.getUniqueId());
            int myRank = manager.getLeaderboard().getPlayerRank(player.getUniqueId());

            content.append("§7Статус: §a§lАКТИВЕН СЕЙЧАС!\n");
            content.append("§7Мир: §e").append(loc.getWorld().getName()).append("\n");
            content.append("§7Координаты: §6X: ").append(loc.getBlockX())
                    .append(" Y: ").append(loc.getBlockY())
                    .append(" Z: ").append(loc.getBlockZ()).append("\n");
            content.append("§7Босс: §cБрутальный Пиглин (2м)\n");
            content.append("§7Здоровье босса: §c❤ ").append(curHp).append(" / ").append(maxHp).append("\n");
            content.append("§7До закрытия разлома: §e").append(ColorUtil.formatTime(manager.getSecondsRemainingInEvent())).append("\n\n");
            content.append("§7Ваш урон: §f").append((int) myDamage).append(" ❤");
            if (myRank > 0) {
                content.append(" §8(§6#").append(myRank).append(" место§8)");
            }
            content.append("\n");
        } else {
            content.append("§7Статус: §eОжидание вторжения\n");
            content.append("§7Мир вторжения: §6").append(cfg.getWorldName()).append("\n");
            content.append("§7До следующего разлома: §a")
                    .append(ColorUtil.formatTime(manager.getSecondsUntilNextStart())).append("\n\n");
            content.append("§7Награды за топ урона:\n");
            content.append("§6🥇 1 место: §d").append(cfg.getRewardFirstPlace()).append(" ").append(cfg.getCurrencyName()).append("\n");
            content.append("§f🥈 2 место: §d").append(cfg.getRewardSecondPlace()).append(" ").append(cfg.getCurrencyName()).append("\n");
            content.append("§c🥉 3 место: §d").append(cfg.getRewardThirdPlace()).append(" ").append(cfg.getCurrencyName()).append("\n");
        }

        boolean compassOn = manager.isCompassEnabled(player);
        boolean isAdmin = player.hasPermission("geyserevent.admin");

        try {
            SimpleForm.Builder builder = SimpleForm.builder()
                    .title("§4§lАдский Разлом §8(§6Bedrock§8)")
                    .content(content.toString())
                    .button(
                            compassOn ? "§a🧭 Навигатор: ВКЛЮЧЁН\n§8Нажмите, чтобы выключить" : "§e🧭 Навигатор до Разлома\n§8Показать стрелку в ActionBar",
                            FormImage.Type.PATH,
                            "textures/items/compass_item.png"
                    )
                    .button(
                            "§6🏆 Топ урона и Награды\n§83000 / 2000 / 1000 осколков",
                            FormImage.Type.PATH,
                            "textures/items/nether_star.png"
                    )
                    .button(
                            "§c🪓 О Боссе и Способностях\n§8Брутальный Пиглин 2м (3500 HP)",
                            FormImage.Type.PATH,
                            "textures/items/netherite_axe.png"
                    );

            if (isAdmin) {
                builder.button(
                        "§4⚙ Управление Ивентом (Админ)\n§8Старт, стоп, способности, конфиг",
                        FormImage.Type.PATH,
                        "textures/blocks/respawn_anchor_side4.png"
                );
            }

            builder.validResultHandler(response -> {
                int id = response.clickedButtonId();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    switch (id) {
                        case 0 -> {
                            boolean nowOn = manager.toggleCompass(player);
                            player.sendMessage(cfg.getPrefix() + ColorUtil.color(
                                    nowOn ? "&aНавигатор до Адского Разлома включён!" : "&eНавигатор до Адского Разлома выключен."
                            ));
                        }
                        case 1 -> openLeaderboardForm(player);
                        case 2 -> openBossInfoForm(player);
                        case 3 -> {
                            if (isAdmin) {
                                openAdminForm(player);
                            }
                        }
                    }
                });
            });

            if (plugin.getBedrockService().sendCumulusForm(player, builder.build())) {
                return;
            }
        } catch (Throwable ignored) {
        }

        // Текстовый резервный вывод (если команду вызвал админ с Java)
        sendChatFallbackSummary(player, content.toString());
    }

    /**
     * Открывает нативное окно Bedrock с таблицей лидеров по урону и наградами PlayerPoints.
     */
    public void openLeaderboardForm(Player player) {
        InfernalRiftManager manager = plugin.getRiftManager();
        PluginConfig cfg = plugin.getPluginConfig();
        String currency = cfg.getCurrencyName();

        StringBuilder sb = new StringBuilder();
        sb.append("§6§lНАГРАДЫ ПО УРОНУ (PlayerPoints):\n");
        sb.append("§6🥇 1 место — §d§l").append(cfg.getRewardFirstPlace()).append(" ").append(currency).append("\n");
        sb.append("§f🥈 2 место — §d§l").append(cfg.getRewardSecondPlace()).append(" ").append(currency).append("\n");
        sb.append("§c🥉 3 место — §d§l").append(cfg.getRewardThirdPlace()).append(" ").append(currency).append("\n\n");

        int myBalance = plugin.getPlayerPointsHook().lookShards(player.getUniqueId());
        if (myBalance > 0) {
            sb.append("§7Ваш баланс: §d").append(myBalance).append(" ").append(currency).append("\n\n");
        }

        sb.append("§e§lТЕКУЩИЙ ТОП УРОНА ПО БОССУ:\n");
        List<DamageLeaderboard.DamageEntry> top = manager.getLeaderboard().getTop(10);
        if (top.isEmpty()) {
            sb.append("§7Пока никто не нанёс урона боссу.\n");
        } else {
            for (int i = 0; i < top.size(); i++) {
                DamageLeaderboard.DamageEntry entry = top.get(i);
                int place = i + 1;
                int reward = cfg.getRewardForPlace(place);
                String rewardSuffix = reward > 0 ? " §8(§d+" + reward + " " + currency + "§8)" : "";
                sb.append("§6#").append(place).append(" §f").append(entry.playerName())
                        .append(" §8— §c").append((int) entry.damage()).append(" ❤")
                        .append(rewardSuffix).append("\n");
            }
        }

        try {
            SimpleForm form = SimpleForm.builder()
                    .title("§6§lТоп Урона и Награды")
                    .content(sb.toString())
                    .button("§7⬅ Назад в главное меню")
                    .validResultHandler(res -> Bukkit.getScheduler().runTask(plugin, () -> openMainForm(player)))
                    .build();

            if (plugin.getBedrockService().sendCumulusForm(player, form)) {
                return;
            }
        } catch (Throwable ignored) {
        }

        sendChatFallbackSummary(player, sb.toString());
    }

    /**
     * Открывает нативное окно Bedrock с описанием Босса (Брутальный Пиглин 2м) и его способностей.
     */
    public void openBossInfoForm(Player player) {
        PluginConfig cfg = plugin.getPluginConfig();

        String info = "§4§lБОСС: БРУТАЛЬНЫЙ ВЛАДЫКА РАЗЛОМА\n\n"
                + "§7• §fТип: §cБольшой Брутальный Пиглин (" + cfg.getBossHeightMeters() + " метра)\n"
                + "§7• §fЗдоровье: §c" + (int) cfg.getBossHealth() + " ❤\n"
                + "§7• §fУрон от удара: §c" + (int) cfg.getBossDamage() + " ед.\n"
                + "§7• §fОружие: §4Незеритовый топор\n"
                + "§7• §fМир появления: §e" + cfg.getWorldName() + "\n"
                + "§7• §fОсобенность портала: §aПортал схематики безопасен и НЕ телепортирует игроков в Ад!\n\n"
                + "§6§lСПОСОБНОСТИ БОССА (в радиусе " + (int) cfg.getAbilityRadius() + " блоков):\n"
                + "§61. ⚡ Притягивание:\n"
                + "§7Притягивает всех игроков в радиусе " + (int) cfg.getAbilityRadius() + " блоков к себе огненными цепями.\n\n"
                + "§c2. 💥 Отталкивание:\n"
                + "§7Ударяет Незеритовым топором о землю и отбрасывает всех игроков в радиусе " + (int) cfg.getAbilityRadius() + " блоков.\n\n"
                + "§d3. 🌀 Стяжка всех в одно место:\n"
                + "§7Создаёт гравитационный разлом и собирает ВСЕХ игроков в радиусе " + (int) cfg.getAbilityRadius() + " блоков в одну точку перед собой!\n\n"
                + "§44. 🔥 Призыв приспешников:\n"
                + "§7Каждые " + cfg.getMinionSummonIntervalSeconds() + " сек. призывает вооружённых приспешников из портала.";

        try {
            SimpleForm form = SimpleForm.builder()
                    .title("§c§lО Боссе и Способностях")
                    .content(info)
                    .button("§7⬅ Назад в главное меню")
                    .validResultHandler(res -> Bukkit.getScheduler().runTask(plugin, () -> openMainForm(player)))
                    .build();

            if (plugin.getBedrockService().sendCumulusForm(player, form)) {
                return;
            }
        } catch (Throwable ignored) {
        }

        sendChatFallbackSummary(player, info);
    }

    /**
     * Открывает панель управления администратора в нативной форме Bedrock.
     */
    public void openAdminForm(Player player) {
        if (!player.hasPermission("geyserevent.admin")) {
            return;
        }
        InfernalRiftManager manager = plugin.getRiftManager();
        PluginConfig cfg = plugin.getPluginConfig();

        try {
            SimpleForm form = SimpleForm.builder()
                    .title("§4§lАдмин-панель Адского Разлома")
                    .content("§7Мир по конфигу: §e" + cfg.getWorldName()
                            + "\n§7Статус ивента: " + (manager.isActive() ? "§aАктивен" : "§cНе активен")
                            + "\n§7Живых приспешников: §e" + plugin.getAbilityController().getActiveMinionCount())
                    .button("§a▶ Запустить в мире " + cfg.getWorldName() + "\n§8Случайные координаты")
                    .button("§e📍 Запустить прямо здесь\n§8На вашей текущей позиции")
                    .button("§c⏹ Остановить ивент\n§8Удалить босса и откатить схематику")
                    .button("§6⚡ Способность: Притянуть (15 бл.)")
                    .button("§c💥 Способность: Оттолкнуть (15 бл.)")
                    .button("§d🌀 Способность: Собрать в 1 точку")
                    .button("§4🔥 Призвать волну приспешников")
                    .button("§b⚙ Настройки (CustomForm)\n§8Мир, HP, урон, радиус, осколки")
                    .validResultHandler(res -> {
                        int id = res.clickedButtonId();
                        Bukkit.getScheduler().runTask(plugin, () -> handleAdminFormClick(player, id));
                    })
                    .build();

            if (plugin.getBedrockService().sendCumulusForm(player, form)) {
                return;
            }
        } catch (Throwable ignored) {
        }

        player.sendMessage(cfg.getPrefix() + ColorUtil.color("&eИспользуйте команды: &6/rift start, /rift start-here, /rift stop, /rift ability <pull|push|gather|minions>"));
    }

    private void handleAdminFormClick(Player player, int buttonId) {
        InfernalRiftManager manager = plugin.getRiftManager();
        PluginConfig cfg = plugin.getPluginConfig();
        LivingEntity boss = manager.getBossEntity();

        switch (buttonId) {
            case 0 -> {
                if (manager.startEventRandomInConfiguredWorld()) {
                    player.sendMessage(cfg.getPrefix() + ColorUtil.color("&aИвент успешно запущен в мире &e" + cfg.getWorldName() + "&a!"));
                } else {
                    player.sendMessage(cfg.getPrefix() + ColorUtil.color("&cНе удалось запустить ивент в мире &e" + cfg.getWorldName() + "&c. Проверьте наличие мира."));
                }
            }
            case 1 -> {
                if (manager.startEventAt(player.getLocation())) {
                    player.sendMessage(cfg.getPrefix() + ColorUtil.color("&aИвент «Адский Разлом» запущен на вашей позиции!"));
                }
            }
            case 2 -> {
                manager.stopEvent(false);
                player.sendMessage(cfg.getPrefix() + ColorUtil.color("&eИвент остановлен, схематика откачена."));
            }
            case 3 -> triggerAdminAbility(player, boss, BossAbilityType.PULL);
            case 4 -> triggerAdminAbility(player, boss, BossAbilityType.PUSH);
            case 5 -> triggerAdminAbility(player, boss, BossAbilityType.GATHER);
            case 6 -> triggerAdminAbility(player, boss, BossAbilityType.SUMMON_MINIONS);
            case 7 -> openAdminSettingsCustomForm(player);
        }
    }

    private void triggerAdminAbility(Player admin, LivingEntity boss, BossAbilityType type) {
        PluginConfig cfg = plugin.getPluginConfig();
        if (boss == null || boss.isDead()) {
            admin.sendMessage(cfg.getPrefix() + ColorUtil.color("&cБосс сейчас не активен!"));
            return;
        }
        int affected = plugin.getAbilityController().executeAbility(boss, type);
        admin.sendMessage(cfg.getPrefix() + ColorUtil.color("&aАктивирована способность &e" + type.getDisplayName()
                + " &7(затронуто целей: &f" + affected + "&7)."));
    }

    /**
     * Открывает нативную CustomForm для настройки параметров ивента прямо из Bedrock-клиента.
     */
    public void openAdminSettingsCustomForm(Player player) {
        PluginConfig cfg = plugin.getPluginConfig();

        try {
            CustomForm form = CustomForm.builder()
                    .title("§b§lНастройки Адского Разлома")
                    .input("Мир появления ивента:", "world2", cfg.getWorldName())
                    .input("Здоровье Босса (HP):", "3500", String.valueOf((int) cfg.getBossHealth()))
                    .input("Урон Босса (Незеритовый топор):", "15", String.valueOf((int) cfg.getBossDamage()))
                    .input("Радиус способностей (блоков):", "15", String.valueOf((int) cfg.getAbilityRadius()))
                    .input("Награда за 1 место (осколков):", "3000", String.valueOf(cfg.getRewardFirstPlace()))
                    .input("Награда за 2 место (осколков):", "2000", String.valueOf(cfg.getRewardSecondPlace()))
                    .input("Награда за 3 место (осколков):", "1000", String.valueOf(cfg.getRewardThirdPlace()))
                    .validResultHandler(res -> Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            String w = res.asInput(0);
                            double hp = Double.parseDouble(res.asInput(1));
                            double dmg = Double.parseDouble(res.asInput(2));
                            double rad = Double.parseDouble(res.asInput(3));
                            int r1 = Integer.parseInt(res.asInput(4));
                            int r2 = Integer.parseInt(res.asInput(5));
                            int r3 = Integer.parseInt(res.asInput(6));

                            cfg.updateFromAdminForm(
                                    (w == null || w.isBlank()) ? "world2" : w.trim(),
                                    Math.max(10.0, hp),
                                    Math.max(1.0, dmg),
                                    Math.max(3.0, rad),
                                    Math.max(0, r1),
                                    Math.max(0, r2),
                                    Math.max(0, r3)
                            );
                            player.sendMessage(cfg.getPrefix() + ColorUtil.color("&aНастройки ивента успешно сохранены в config.yml!"));
                        } catch (Exception ex) {
                            player.sendMessage(cfg.getPrefix() + ColorUtil.color("&cОшибка формата числа в полях настройки!"));
                        }
                    }))
                    .build();

            plugin.getBedrockService().sendCumulusForm(player, form);
        } catch (Throwable ignored) {
        }
    }

    private void sendChatFallbackSummary(Player player, String content) {
        player.sendMessage(ColorUtil.color("&8&m----------------------------------------------------"));
        for (String line : content.split("\n")) {
            player.sendMessage(line);
        }
        player.sendMessage(ColorUtil.color("&8&m----------------------------------------------------"));
    }
}
