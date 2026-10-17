package org.example.xiaoxiao.newFish;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.example.xiaoxiao.newFish.data.PlayerDataStore;
import org.example.xiaoxiao.newFish.enchantment.EnchantmentKeys;
import org.example.xiaoxiao.newFish.enchantment.EnchantmentService;
import org.example.xiaoxiao.newFish.fishing.FishingService;
import org.example.xiaoxiao.newFish.integration.CraftEngineBridge;
import org.example.xiaoxiao.newFish.item.ItemFactory;
import org.example.xiaoxiao.newFish.item.ItemIdentity;
import org.example.xiaoxiao.newFish.util.Scheduler;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NewFish 主类。
 *
 * <p><b>命令为什么用 BasicCommand 而不是 plugin.yml 的 commands 段：</b>
 * 本插件同时提供 {@code paper-plugin.yml}，因此被 Paper 当作「Paper 插件」加载。
 * Paper 插件**完全不读取 YAML 声明的命令**，{@code JavaPlugin#getCommand} 在启用阶段
 * 会直接抛：
 * <pre>
 * UnsupportedOperationException: You are trying to call JavaPlugin#getCommand
 * on a Paper plugin during startup ... Paper plugins do not support YAML-based
 * command declarations! You can use JavaPlugin#registerCommand ...
 * </pre>
 * 所以这里实现 {@link BasicCommand} 并在 {@code onEnable} 里用
 * {@code registerCommand(...)} 注册。/newfish 的子命令逻辑与 Tab 补全逻辑
 * 与原先 YAML 版本完全一致，只是换了注入入口。
 */
public final class NewFish extends JavaPlugin implements BasicCommand {
    /** /newfish 的一级子命令。 */
    private static final List<String> ROOT_COMMANDS = List.of("assets", "reload", "give", "enchant", "codex", "help");
    /** NewFish 自定义附魔（已在引导期注册为原版附魔）。 */
    private static final List<String> ENCHANTMENTS = EnchantmentKeys.ALL;
    /** 建议的物品数量。 */
    private static final List<String> AMOUNTS = List.of("1", "2", "4", "8", "16", "32", "64");
    /** 建议的附魔等级。 */
    private static final List<String> LEVELS = List.of("1", "2", "3", "4", "5");

    private NamespacedKey entityMarker;
    private CraftEngineBridge craftEngine;
    private ItemFactory itemFactory;
    private EnchantmentService enchantments;
    private PlayerDataStore playerData;
    private Scheduler.Handle craftEngineRefreshTask;
    /** 定期把钓鱼档案刷回磁盘的任务。 */
    private Scheduler.Handle autosaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        entityMarker = new NamespacedKey(this, "entity_marker");
        ItemIdentity.init(this);

        playerData = new PlayerDataStore(this);
        playerData.load();

        craftEngine = new CraftEngineBridge(this);
        craftEngine.initialize();
        itemFactory = new ItemFactory(craftEngine);
        enchantments = new EnchantmentService(this);

        Bukkit.getPluginManager().registerEvents(new FishingService(this, itemFactory), this);
        Bukkit.getPluginManager().registerEvents(enchantments, this);
        Bukkit.getPluginManager().registerEvents(new GameplayListener(this), this);
        enchantments.start();
        // CraftEngine 的 newfish 资源包由它自己在稍后加载，本插件启用时往往还不存在，
        // 因此首次「发现 0/52」属于正常的时序现象。把首次刷新压到 5 tick，
        // 之后每 10 秒复查一次，一旦 CraftEngine 就绪就会立刻切到 52/52。
        craftEngineRefreshTask = Scheduler.runGlobalRepeating(this, craftEngine::refresh, 5L, 200L);
        // 档案按周期落盘，避免每次钓获都写磁盘；关服时还会再存一次。
        autosaveTask = Scheduler.runGlobalRepeating(this, playerData::save, 1200L, 6000L);

        // Paper 插件不读 plugin.yml 的 commands 段，getCommand() 会直接抛
        // UnsupportedOperationException。必须走 Brigadier 的 registerCommand。
        // 这里用最简的 BasicCommand 重载（无别名、无 description），
        // 具体子命令与补全逻辑仍在 execute/suggest 里。
        registerCommand("newfish", "NewFish 管理命令", List.of(), this);

        getLogger().info("NewFish 已启用（服务端 " + Bukkit.getBukkitVersion()
                + (Scheduler.isFolia() ? "；检测到 Folia，已启用 region 化调度" : "")
                + "）。");
        // 附魔注册发生在引导期，失败时不会抛异常，只表现为 getByKey 一直返回 null，
        // 进而让 /newfish enchant 提示"成功"但物品毫无变化。这里主动自检并写进日志。
        List<String> missingEnchantments = EnchantmentKeys.missing();
        if (missingEnchantments.isEmpty()) {
            getLogger().info("附魔注册自检通过：7 个 NewFish 附魔均已就绪。");
        } else {
            getLogger().warning("附魔注册自检失败，以下附魔未能注册：" + String.join("、", missingEnchantments)
                    + "。请确认服务端为 Paper 1.21+ 且 bootstrap 未被跳过。");
        }
        if (Bukkit.getPluginManager().isPluginEnabled("CraftEngine")) {
            getLogger().info("首次安装 CraftEngine 资产后执行 /newfish assets，随后该命令会运行 /ce reload all。");
        }

        // PlaceholderAPI 在 paper-plugin.yml 里声明为 required: false ——
        // 因为 Paper 的现代加载路径会在引导期（连 bootstrapper 都还没加载时）
        // 就校验依赖表，而 PAPI 是传统 Bukkit 插件，那一刻还没进 provider 存储，
        // 写 required: true 必然抛 UnknownDependencyException 拒绝加载。
        //
        // 但用户要求 PAPI 是「硬依赖」，这个语义必须由代码自己保住：
        // 这里检测不到就直接禁用插件，而不是让占位符静默失效、留下一个残缺的插件。
        // 放在最后执行，是为了让上面的附魔/资源诊断日志能完整打出，便于排查。
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().severe("未检测到 PlaceholderAPI。NewFish 需要它提供占位符支持，插件将自行禁用。"
                    + "请下载 PlaceholderAPI 后重启服务端。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        // 注册 PAPI 扩展。
        //
        // ⚠️ 这里必须 try-catch(Throwable)：NewFishExpansion extends PlaceholderExpansion，
        // 而 **extends 是类加载期就要解析父类的**。Paper 插件默认 classloading isolation，
        // 若 paper-plugin.yml 里 PlaceholderAPI 的 join-classpath 不是 true，
        // 加载 NewFishExpansion 会抛：
        //   NoClassDefFoundError: me/clip/placeholderapi/expansion/PlaceholderExpansion
        //     Caused by: ClassNotFoundException
        // 那是一个 Error 而不是 Exception，会直接掀掉整个 onEnable、让插件被禁用。
        // 占位符只是附加能力，不该拖垮钓鱼/图鉴/附魔等主体功能，
        // 因此这里兜住并打明确日志，让服主能自己判断要不要修 classpath 配置。
        try {
            new org.example.xiaoxiao.newFish.integration.NewFishExpansion(this).register();
        } catch (Throwable throwable) {
            getLogger().severe("PlaceholderAPI 扩展注册失败，占位符将不可用（其余功能不受影响）："
                    + throwable);
            getLogger().severe("若错误为 NoClassDefFoundError/ClassNotFoundException，请确认"
                    + " paper-plugin.yml 中 PlaceholderAPI 的 join-classpath 为 true；"
                    + "若依赖未生效，可改用 java -jar 完整重启服务端（/reload 不会重建插件类加载器）。");
        }
    }

    @Override
    public void onDisable() {
        Scheduler.cancel(this, craftEngineRefreshTask);
        craftEngineRefreshTask = null;
        Scheduler.cancel(this, autosaveTask);
        autosaveTask = null;
        if (enchantments != null) enchantments.stop();
        // 关服前把档案落盘，避免丢失最后一段时间的钓鱼记录。
        if (playerData != null) playerData.save();
    }

    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 1);
        if (version >= 5) return;

        if (version < 2) {
            migrateCustomFishToFishPool();
            version = 2;
        }
        if (version < 4) {
            migrateFeatureConfig();
            version = 4;
        }
        if (version < 5) {
            migrateRemoveDroppedContent();
            version = 5;
        }
        getConfig().set("config-version", version);
        saveConfig();
    }

    /**
     * v4 -> v5：清理已下线的内容（沉船木桶与海龟盔甲）。
     *
     * <p>本版本不再提供这两类战利品与海龟盔甲套装，配置里遗留的条目必须一并删除 ——
     * 否则它们会落到「未知战利品类型」分支被跳过，玩家每次钓到都在控制台刷一条警告。
     *
     * <p>只做删除，不动其它条目与服主自行调整过的数值。
     */
    private void migrateRemoveDroppedContent() {
        List<Map<?, ?>> treasure = new ArrayList<>(getConfig().getMapList("fishing.loot.treasure"));
        List<Map<?, ?>> kept = new ArrayList<>();
        int removedLoot = 0;
        for (Map<?, ?> entry : treasure) {
            String type = String.valueOf(entry.get("type"));
            if ("turtle_armor".equals(type) || "barrel".equals(type)) {
                removedLoot++;
                continue;
            }
            kept.add(entry);
        }
        if (removedLoot > 0) {
            getConfig().set("fishing.loot.treasure", kept);
        }

        int removedKeys = 0;
        // 海龟盔甲套装整段配置
        if (getConfig().isSet("turtle-armor")) {
            getConfig().set("turtle-armor", null);
            removedKeys++;
        }
        // CraftEngine 物品映射里的 4 件盔甲
        for (String part : List.of("turtle_helmet", "turtle_chestplate", "turtle_leggings", "turtle_boots")) {
            if (getConfig().isSet("craftengine.items." + part)) {
                getConfig().set("craftengine.items." + part, null);
                removedKeys++;
            }
        }
        if (removedLoot > 0 || removedKeys > 0) {
            getLogger().info("已清理下线内容：移除 " + removedLoot + " 个宝藏条目与 "
                    + removedKeys + " 项配置键（沉船木桶 / 海龟盔甲）。");
        }
    }

    /**
     * v3 -> v4：补齐重量/品级、图鉴、附魔自然产出三段新配置，
     * 并清理已失效的旧配置项。
     *
     * <p>已有键一律不动，只补缺失项，避免覆盖服主自行调整过的数值。
     */
    private void migrateFeatureConfig() {
        // 只有 v3 之前生成的老配置才缺这些段落；若服主已手写同名键则保留其值。
        if (!getConfig().isSet("fishing.weight")) {
            getConfig().set("fishing.weight.enabled", true);
            getConfig().set("fishing.weight.default-base-kilograms", 1.0);
            getConfig().set("fishing.weight.variance", 0.35);
            getConfig().set("fishing.weight.legendary-factor", 1.6);
            getConfig().set("fishing.weight.broadcast-legendary", true);
        }
        if (!getConfig().isSet("codex")) {
            getConfig().set("codex.enabled", true);
            getConfig().set("codex.unknown-name", "???");
            getConfig().set("codex.top-limit", 5);
        }
        if (!getConfig().isSet("enchantments.natural-loot")) {
            getConfig().set("enchantments.natural-loot.enabled", true);
            getConfig().set("enchantments.natural-loot.pool.rod", ENCHANTMENTS.subList(0, 4));
            getConfig().set("enchantments.natural-loot.pool.bow", List.of(EnchantmentKeys.WATER_TRAIL));
            getConfig().set("enchantments.natural-loot.pool.book", ENCHANTMENTS);
        }
        // 附魔已改为真原版注册，这两个键不再有任何代码读取。
        getConfig().set("enchantments.natural-loot-legacy", null);
        getConfig().set("enchantments.water-trail.worldguard-check", null);
        // 龙虾此前有物品与贴图却没进掉落池，导致永远钓不到。
        List<Map<?, ?>> fish = new ArrayList<>(getConfig().getMapList("fishing.loot.fish"));
        boolean hasLobster = fish.stream().anyMatch(entry -> "lobster".equals(String.valueOf(entry.get("id"))));
        if (!hasLobster) {
            fish.add(Map.of("type", "custom_fish", "id", "lobster", "weight", 20));
            getConfig().set("fishing.loot.fish", fish);
        }
    }

    /** v1 -> v2：把误放在 treasure 池的自定义鱼条目移回 fish 池。 */
    private void migrateCustomFishToFishPool() {
        List<Map<?, ?>> fish = new ArrayList<>(getConfig().getMapList("fishing.loot.fish"));
        List<Map<?, ?>> treasure = new ArrayList<>(getConfig().getMapList("fishing.loot.treasure"));
        List<String> existingFish = new ArrayList<>();
        for (Map<?, ?> entry : fish) {
            if ("custom_fish".equals(String.valueOf(entry.get("type")))) {
                existingFish.add(String.valueOf(entry.get("id")));
            }
        }
        List<Map<?, ?>> remainingTreasure = new ArrayList<>();
        int moved = 0;
        for (Map<?, ?> entry : treasure) {
            if ("custom_fish".equals(String.valueOf(entry.get("type")))) {
                String id = String.valueOf(entry.get("id"));
                if (!existingFish.contains(id)) {
                    fish.add(entry);
                    existingFish.add(id);
                    moved++;
                }
            } else {
                remainingTreasure.add(entry);
            }
        }
        if (moved > 0 || treasure.size() != remainingTreasure.size()) {
            getConfig().set("fishing.loot.fish", fish);
            getConfig().set("fishing.loot.treasure", remainingTreasure);
        }
        if (moved > 0) {
            getLogger().info("已迁移 " + moved + " 个自定义鱼条目到 fish 池，避免被当作普通生物处理。");
        }
    }

    public CraftEngineBridge getCraftEngine() {
        return craftEngine;
    }

    public EnchantmentService getEnchantments() {
        return enchantments;
    }

    public ItemFactory getItemFactory() {
        return itemFactory;
    }

    public PlayerDataStore getPlayerData() {
        return playerData;
    }

    public NamespacedKey getEntityMarker() {
        return entityMarker;
    }

    public boolean isWorldAllowed(World world) {
        List<String> worlds = getConfig().getStringList("fishing.worlds");
        return getConfig().getBoolean("fishing.enabled", true)
                && (worlds.isEmpty() || worlds.contains(world.getName()));
    }

    /**
     * /newfish 的执行入口。
     *
     * <p>注意 {@code args} **不包含命令名本身**（Brigadier 语义），
     * 因此 {@code args[0]} 就是子命令 —— 与原 YAML 版本的行为一致。
     */
    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission("newfish.admin")) {
            sender.sendMessage("§c你没有权限执行此命令。");
            return;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§bNewFish §7命令：/newfish assets | reload | give | enchant | codex");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "assets" -> {
                if (!Bukkit.getPluginManager().isPluginEnabled("CraftEngine")) {
                    sender.sendMessage("§c未检测到 CraftEngine。");
                    return;
                }
                craftEngine.reloadAssets();
                sender.sendMessage("§a已安装 NewFish CraftEngine 资源并请求重载；请查看服务器日志和资源包状态。");
                return;
            }
            case "reload" -> {
                reloadConfig();
                sender.sendMessage("§aNewFish 配置已重载。");
                return;
            }
            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage("§e用法：/newfish give <玩家> <鱼类> [数量]");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                ItemStack stack = itemFactory.custom(args[2]);
                if (target == null || stack == null) {
                    sender.sendMessage("§c玩家不在线，或未知 NewFish 物品。");
                    return;
                }
                if (args.length >= 4) {
                    try {
                        stack.setAmount(Math.max(1, Integer.parseInt(args[3])));
                    } catch (NumberFormatException exception) {
                        sender.sendMessage("§c数量必须是整数。");
                        return;
                    }
                }
                target.getInventory().addItem(stack);
                sender.sendMessage("§a已给予 " + target.getName() + " NewFish 物品。");
                return;
            }
            case "enchant" -> {
                if (!(sender instanceof Player player) || args.length < 2) {
                    sender.sendMessage("§e用法：/newfish enchant <sea_lure|treasure_hunter|hooked_strike|ender_hook|water_trail|dolphins_grace|thundering> [等级]");
                    return;
                }
                String enchantment = args[1].toLowerCase(Locale.ROOT);
                if (!ENCHANTMENTS.contains(enchantment)) {
                    sender.sendMessage("§c未知的 NewFish 附魔，请使用：海之诱饵、宝藏猎人、鱼钩猛击、末影鱼钩、水痕、海豚恩惠或雷霆对应的英文 ID。");
                    return;
                }
                int level = 1;
                if (args.length >= 3) {
                    try {
                        level = Math.max(1, Integer.parseInt(args[2]));
                    } catch (NumberFormatException exception) {
                        sender.sendMessage("§c等级必须是整数。");
                        return;
                    }
                }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType().isAir()) {
                    sender.sendMessage("§c请先手持要附魔的物品。");
                    return;
                }
                if (!supportsEnchantment(held, enchantment)) {
                    sender.sendMessage("§c" + ItemIdentity.enchantmentName(enchantment) + " 不能附加到当前物品。请使用对应的鱼竿、弓、护腿或三叉戟。");
                    return;
                }
                if (!ItemIdentity.setCustomEnchantment(held, enchantment, level)) {
                    // 走到这里说明附魔对象取不到，多半是引导期注册失败。
                    // 必须显式报错：原实现无论成败都提示"已添加"，导致故障完全静默。
                    sender.sendMessage("§c附魔写入失败：服务端未能找到 newfish:" + enchantment
                            + "。请检查启动日志中的附魔注册结果。");
                    return;
                }
                applyVanillaEquivalent(held, enchantment, level);
                player.getInventory().setItemInMainHand(held);
                sender.sendMessage("§a已添加并启用 NewFish 附魔：" + ItemIdentity.enchantmentName(enchantment) + " " + level + "。");
                return;
            }
            case "codex" -> {
                Player target = sender instanceof Player self ? self : null;
                if (args.length >= 2) {
                    target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage("§c玩家不在线。");
                        return;
                    }
                }
                if (target == null) {
                    sender.sendMessage("§e用法：/newfish codex [玩家]");
                    return;
                }
                showCodex(sender, target);
                return;
            }
            default -> {
                sender.sendMessage("§e用法：/newfish assets | reload | codex");
                return;
            }
        }
    }

    /** 展示某个玩家的钓鱼图鉴。 */
    private void showCodex(CommandSender viewer, Player target) {
        var store = getPlayerData();
        List<String> all = ItemFactory.fishIds();
        int collected = store.collectedCount(target.getUniqueId());
        viewer.sendMessage("§b===== " + target.getName() + " 的钓鱼图鉴 =====");
        viewer.sendMessage("§7收集进度：§f" + collected + "§7/§f" + all.size()
                + "§7，累计钓获 §f" + store.totalCaught(target.getUniqueId()) + " §7条");
        StringBuilder line = new StringBuilder();
        int shown = 0;
        for (String fishId : all) {
            boolean has = store.hasCollected(target.getUniqueId(), fishId);
            double best = store.personalBest(target.getUniqueId(), fishId);
            String entry;
            if (!has) {
                // 未钓到的鱼只显示问号，保留“还没收集到”的悬念。
                entry = "§8？？？";
            } else if (best > 0) {
                entry = "§a" + ItemFactory.fishName(fishId) + "§7(" + best + "kg)";
            } else {
                entry = "§a" + ItemFactory.fishName(fishId);
            }
            if (line.length() + entry.length() > 90) {
                viewer.sendMessage(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append("§7, ");
            line.append(entry);
            shown++;
        }
        if (!line.isEmpty()) viewer.sendMessage(line.toString());
        viewer.sendMessage("§7可用占位符：§f%newfish_codex%§7、§f%newfish_total_caught%");
    }

    private boolean supportsEnchantment(ItemStack stack, String enchantment) {
        String material = stack.getType().name();
        return switch (enchantment) {
            case "sea_lure", "treasure_hunter", "hooked_strike", "ender_hook" -> stack.getType() == org.bukkit.Material.FISHING_ROD;
            case "water_trail" -> stack.getType() == org.bukkit.Material.BOW;
            case "dolphins_grace" -> material.endsWith("_LEGGINGS");
            case "thundering" -> stack.getType() == org.bukkit.Material.TRIDENT;
            default -> false;
        };
    }

    private void applyVanillaEquivalent(ItemStack stack, String enchantment, int level) {
        // NewFish 附魔已是真正的原版附魔，其效果由插件自己实现，
        // 因此不再额外叠加同名原版附魔（旧实现会同时写入 LURE / LUCK_OF_THE_SEA）。
    }

    /**
     * /newfish 的 Tab 补全。{@code args} 同样**不含命令名**，语义与原版一致。
     */
    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission("newfish.admin")) {
            return Collections.emptyList();
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1];
        List<String> options = new ArrayList<>();
        if (args.length <= 1) {
            options.addAll(ROOT_COMMANDS);
        } else {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give" -> {
                    if (args.length == 2) {
                        for (Player online : Bukkit.getOnlinePlayers()) options.add(online.getName());
                    } else if (args.length == 3) {
                        options.addAll(ItemFactory.fishIds());
                    } else if (args.length == 4) {
                        options.addAll(AMOUNTS);
                    }
                }
                case "enchant" -> {
                    if (args.length == 2) {
                        options.addAll(ENCHANTMENTS);
                    } else if (args.length == 3) {
                        options.addAll(LEVELS);
                    }
                }
                case "codex" -> {
                    // /newfish codex [玩家]
                    if (args.length == 2) {
                        for (Player online : Bukkit.getOnlinePlayers()) options.add(online.getName());
                    }
                }
                default -> {
                    // assets / reload / help 没有后续参数
                }
            }
        }
        List<String> result = new ArrayList<>();
        StringUtil.copyPartialMatches(prefix, options, result);
        Collections.sort(result);
        return result;
    }
}
