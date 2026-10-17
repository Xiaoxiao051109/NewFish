package org.example.xiaoxiao.newFish.fishing;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Turtle;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.FishHook;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.example.xiaoxiao.newFish.NewFish;
import org.example.xiaoxiao.newFish.data.PlayerDataStore;
import org.example.xiaoxiao.newFish.enchantment.EnchantmentKeys;
import org.example.xiaoxiao.newFish.item.ItemFactory;
import org.example.xiaoxiao.newFish.util.Scheduler;
import org.example.xiaoxiao.newFish.item.ItemIdentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class FishingService implements Listener {
    /** 寻宝地图的中文名称。 */
    private static final Map<String, String> MAP_NAMES = Map.of(
            "shipwreck", "沉船地图",
            "buried_treasure", "埋藏宝藏地图",
            "monument", "海底神殿地图",
            "ocean_ruin", "海底废墟地图",
            "ruined_portal", "废弃传送门地图"
    );

    private final NewFish plugin;
    private final ItemFactory items;
    private final FishWeight weights;

    public FishingService(NewFish plugin, ItemFactory items) {
        this.plugin = plugin;
        this.items = items;
        this.weights = new FishWeight(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        // 调试开关：fishing.debug 为 true 时打印事件关键字段。
        // Folia 上 PlayerFishEvent 的 getCaught() 语义与普通 Paper 可能不同，
        // 这里把实际拿到的类型打出来，便于定位「钓上来的东西没被插件接管」这类问题。
        Object caughtRaw = event.getCaught();
        debug("PlayerFishEvent state=" + event.getState()
                + " caughtType=" + (caughtRaw == null ? "null" : caughtRaw.getClass().getName())
                + " caught=" + caughtRaw
                + " world=" + event.getPlayer().getWorld().getName()
                + " thread=" + Thread.currentThread().getName());
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            plugin.getEnchantments().rememberHook(event.getPlayer(), event.getHook());
            int seaLure = ItemIdentity.customEnchantment(event.getPlayer().getInventory().getItemInMainHand(), "sea_lure");
            if (seaLure > 0 && plugin.getConfig().getBoolean("enchantments.sea_lure", true)) {
                int reduction = seaLure * 20;
                FishHook hook = event.getHook();
                int minWait = Math.max(1, hook.getMinWaitTime() - reduction);
                int maxWait = Math.max(minWait + 1, hook.getMaxWaitTime() - reduction);
                hook.setMinWaitTime(minWait);
                hook.setMaxWaitTime(maxWait);
            }
            return;
        }
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            handleCatch(event);
        } else if (event.getState() == PlayerFishEvent.State.REEL_IN
                || event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            plugin.getEnchantments().forgetHook(event.getPlayer());
        }

        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && event.getCaught() != null) {
            plugin.getEnchantments().onHookedEntity(event.getPlayer(), event.getCaught());
        }
    }

    private void handleCatch(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isWorldAllowed(player.getWorld())) {
            debug("跳过：世界 " + player.getWorld().getName() + " 不在 fishing.worlds 白名单内。");
            return;
        }
        if (!(event.getCaught() instanceof Item caught)) {
            Object raw = event.getCaught();
            plugin.getLogger().warning("NewFish 未能接管钓获物：PlayerFishEvent.getCaught() 不是 Item，实际为 "
                    + (raw == null ? "null" : raw.getClass().getName())
                    + "（state=" + event.getState() + "）。本次将保留原版掉落物。");
            return;
        }

        // ⚠️⚠️ 交接策略：**绝不取消事件**，只删掉原版那个掉落物，然后自己抛一份新的。
        //
        // 上一版在这里写了 event.setCancelled(true)，结果玩家反馈：
        // 「鱼钩没有从水里收起，我也没有拿到鱼，也没有掉落物从水里飞起」。
        //
        // 原因：CAUGHT_FISH 这个事件**同时承担两件事** ——
        //   (a) 原版在鱼钩处生成 Item 并抛向玩家；
        //   (b) 服务端收杆（retrieve）并复位钓鱼状态。
        // 底层是 `if (!event.isCancelled()) { retrieve(...); dropItem(...); }` ——
        // 取消之后 (a)(b) 一起没了，鱼钩就再也收不回来，玩家自然什么也拿不到。
        //
        // 正确做法：**放行事件**（保留原版收杆），只把原版生成的那个 Item 实体移除，
        // 再按同样的观感抛出我们自己的物品。这样：
        //   - 鱼钩正常收回；
        //   - 水里不会残留原版鱼（它一生成就被删）；
        //   - 玩家看到的是我们的自定义鱼飞过来。
        //
        // 位置要在移除实体之前取好 —— 移除后 getLocation() 就不再可靠。
        org.bukkit.Location dropLocation = caught.getLocation().clone();

        String category = rollCategory(player);
        Biome biome = player.getWorld().getBiome(dropLocation.getBlockX(),
                dropLocation.getBlockY(), dropLocation.getBlockZ());
        Map<?, ?> entry = rollEntry(category, biome);
        if (entry == null) {
            plugin.getLogger().warning("NewFish 未能接管钓获物：分类 " + category
                    + " 在生物群系 " + biome + " 未抽到任何条目（请检查 fishing.loot."
                    + category + " 的 weight 与 biomes 配置）。");
            return;
        }
        String type = string(entry, "type", "item");
        String id = string(entry, "id", "cod");
        debug("抽中条目 category=" + category + " type=" + type + " id=" + id);

        // 生成类条目（mob）直接在水里生成生物，不产生掉落物。
        if ("mob".equals(type)) {
            removeCaught(caught);
            Scheduler.runEntityChecked(plugin, caught,
                    () -> spawnMobAt(dropLocation, new org.bukkit.util.Vector(0, 0, 0), id), null);
            return;
        }

        ItemStack delivered = buildCatchStack(type, id, entry, dropLocation, player);
        if (delivered == null) {
            // buildCatchStack 内部已打警告（未知类型等），此处无需重复。
            return;
        }
        launchToPlayer(player, caught, dropLocation, delivered);
    }

    /**
     * 按原版观感把鱼抛给玩家：在钓起位置生成物品并赋予朝玩家的初速度，
     * 玩家靠近即自动拾取。
     *
     * <p>物品必须在「它自己所在 region」的线程上生成，因此这里统一派发；
     * 派发失败（实体已回收等）则回退为直接进背包，绝不丢东西。
     */
    private void launchToPlayer(Player player, Item reference, org.bukkit.Location dropLocation, ItemStack stack) {
        // ⚠️ 本方法必须保证 handleFishCatch 恰好被调用一次：
        // 成功路径在这里补一次，失败回退路径由 deliverToPlayer 内部承担。
        // 早先的写法在开头无条件调了一次，失败时 deliverToPlayer 又调一次，
        // 导致图鉴计数翻倍、消息与全服播报重复。
        boolean accepted = Scheduler.runEntityChecked(plugin, reference,
                () -> {
                    // 先把原版那条鱼删掉，再做我们自己的抛投 —— 否则会出现「水里一条原版鱼
                    // + 一条插件鱼」双份。事件保持放行，所以这一步必须在事件回调里手动做。
                    reference.remove();
                    Item spawned = dropLocation.getWorld().dropItem(dropLocation, stack);
                    // 速度方向：从落点指向玩家，并保留一点向上分量，做出原版那种"蹦跳着飞过去"的感觉。
                    spawned.setVelocity(velocityTowards(dropLocation, player.getLocation()));
                    spawned.setPickupDelay(0);
                },
                () -> deliverToPlayer(player, stack));
        if (accepted) {
            if ("fish".equals(ItemIdentity.type(stack))) {
                handleFishCatch(player, stack, ItemIdentity.variant(stack));
            }
        } else {
            deliverToPlayer(player, stack);
        }
    }

    /**
     * 删除原版钓起的掉落物，避免水里残留一条原版鱼。
     *
     * <p>事件不再被取消（取消会导致鱼钩收不回来），所以原版那条鱼照样会生成，
     * 必须由我们手动清理。能同 region 直接删就直接删；跨 region 时派发过去删，
     * 删不掉也无妨 —— 它自己会消失，且玩家拿到的仍是我们的自定义鱼。
     */
    private void removeCaught(Item caught) {
        if (caught.isValid() && isOwnedByCurrentRegion(caught)) {
            caught.remove();
            return;
        }
        Scheduler.runEntityChecked(plugin, caught, caught::remove, null);
    }

    /** 查询实体是否由当前线程的 region 拥有；非 Folia 服务端一律返回 true。 */
    private static boolean isOwnedByCurrentRegion(Entity entity) {
        try {
            return Bukkit.isOwnedByCurrentRegion(entity);
        } catch (Throwable throwable) {
            // 纯 Paper 没有 region 概念，视为「总是拥有」，保持原有单线程行为。
            return true;
        }
    }

    /** 计算从落点飞向玩家的速度向量，带一点向上弧度，贴近原版观感。 */
    private static org.bukkit.util.Vector velocityTowards(org.bukkit.Location from, org.bukkit.Location to) {
        org.bukkit.util.Vector direction = to.toVector().subtract(from.toVector());
        // 原版抛出的物品水平速度约 0.2~0.4，纵向略微上扬后受重力下落。
        org.bukkit.util.Vector horizontal = new org.bukkit.util.Vector(direction.getX(), 0, direction.getZ());
        if (horizontal.lengthSquared() > 1.0e-6) {
            horizontal.normalize().multiply(0.32);
        } else {
            horizontal = new org.bukkit.util.Vector(0, 0, 0);
        }
        return horizontal.setY(0.2);
    }

    /**
     * 把构造好的物品直接交给玩家（回退路径：实体已回收、派发失败等）。
     *
     * <p>必须在玩家所在 region 线程上调用（事件线程即满足）。
     */
    private void deliverToPlayer(Player player, ItemStack stack) {
        // 鱼获的重量/品级/图鉴要在**放入背包之前**写好，
        // 因为 addItem 之后传入的 ItemStack 对象即被消耗，改动不会反映到玩家手上的那份。
        if ("fish".equals(ItemIdentity.type(stack))) {
            handleFishCatch(player, stack, ItemIdentity.variant(stack));
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            // 背包满：把溢出的部分丢在玩家脚下，避免凭空消失。
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
    }

    /** 按类型构造要投递的物品；无法处理时返回 null（并已打日志）。 */
    private ItemStack buildCatchStack(String type, String id, Map<?, ?> entry,
                                      org.bukkit.Location hookLocation, Player player) {
        switch (type) {
            case "custom_fish" -> {
                ItemStack fish = items.fish(id);
                if (fish == null) {
                    plugin.getLogger().warning("NewFish 未能接管钓获物：构建自定义鱼 " + id + " 返回 null。");
                    return null;
                }
                // 若 CraftEngine 未就绪，items.fish 会回落到 Material.COD 的占位物品，
                // 表现为「钓上来的是鳕鱼」。这条日志是识别该情况的关键。
                if (fish.getType() == Material.COD && !plugin.getCraftEngine().isAvailable()) {
                    plugin.getLogger().warning("自定义鱼 " + id
                            + " 回落到原版鳕鱼占位物品，因为 CraftEngine 当前不可用。"
                            + "请确认 CraftEngine 已安装并执行 /newfish assets。");
                }
                return fish;
            }
            case "map" -> {
                return createMap(hookLocation.getWorld(), hookLocation, id);
            }
            case "enchanted" -> {
                return createEnchanted(id);
            }
            case "item" -> {
                return createVanilla(id, intValue(entry, "amount", 1));
            }
            default -> {
                // 未知类型不再静默回落成原版物品 —— 那会把配置写错（或用了已下线的类型，
                // 例如旧版的 turtle_armor / barrel）变成「玩家莫名其妙钓到一件原版物品」。
                // 这里显式告警并跳过本次钓获，让服主能在控制台直接看到问题。
                plugin.getLogger().warning("未知的战利品类型 \"" + type + "\"（条目 id=" + id
                        + "），已跳过。可用类型：custom_fish / item / mob / map / enchanted。"
                        + "若为升级后遗留的旧条目，删除该行或把 config-version 改为 5 后执行 /newfish reload。");
                return null;
            }
        }
    }

    /** 在指定位置生成钓鱼生物，并移除占位掉落物。 */
    private void spawnMobAt(org.bukkit.Location location, org.bukkit.util.Vector velocity, String id) {
        World world = location.getWorld();
        Entity entity;
        try {
            entity = switch (id.toLowerCase(Locale.ROOT)) {
                case "cod" -> world.spawnEntity(location, EntityType.COD);
                case "salmon" -> world.spawnEntity(location, EntityType.SALMON);
                case "tropical_fish" -> world.spawnEntity(location, EntityType.TROPICAL_FISH);
                case "pufferfish" -> world.spawnEntity(location, EntityType.PUFFERFISH);
                case "squid" -> world.spawnEntity(location, EntityType.SQUID);
                case "glow_squid" -> world.spawnEntity(location, EntityType.GLOW_SQUID);
                case "axolotl" -> world.spawnEntity(location, EntityType.AXOLOTL);
                case "dolphin" -> world.spawnEntity(location, EntityType.DOLPHIN);
                case "turtle" -> world.spawnEntity(location, EntityType.TURTLE);
                case "drowned", "trident", "drowned_guardian" -> world.spawnEntity(location, EntityType.DROWNED);
                case "guardian" -> world.spawnEntity(location, EntityType.GUARDIAN);
                default -> null;
            };
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("无法生成钓鱼生物 " + id + "：" + exception.getMessage());
            return;
        }
        if (entity == null) return;
        entity.setVelocity(velocity);
        entity.getPersistentDataContainer().set(plugin.getEntityMarker(), org.bukkit.persistence.PersistentDataType.STRING, "fishing_catch");
        if (entity instanceof Drowned drowned && ("trident".equals(id) || "drowned_guardian".equals(id))) {
            EntityEquipment equipment = drowned.getEquipment();
            if (equipment != null) {
                equipment.setItemInMainHand(new ItemStack(Material.TRIDENT), false);
                equipment.setHelmet(new ItemStack(Material.TURTLE_HELMET), false);
            }
        }
        if ("drowned_guardian".equals(id)) {
            Entity passenger = world.spawnEntity(location, EntityType.GUARDIAN);
            entity.addPassenger(passenger);
        }
    }

    /** 仅当 fishing.debug 打开时输出诊断信息。 */
    private void debug(String message) {
        if (plugin.getConfig().getBoolean("fishing.debug", false)) {
            plugin.getLogger().info("[debug] " + message);
        }
    }

    /**
     * 记录一次自定义鱼钓获：写入重量与品级、更新图鉴，并按品级给出反馈。
     *
     * <p>重量与品级只在「钓到的是自定义鱼」时才处理，原版物品不参与。
     */
    private void handleFishCatch(Player player, ItemStack stack, String fishId) {
        if (stack == null) return;
        if (!plugin.getConfig().getBoolean("fishing.weight.enabled", true)) {
            recordCodex(player, fishId, 0.0);
            return;
        }
        FishWeight.Roll roll = weights.roll(fishId);
        ItemIdentity.setFishWeight(stack, roll.kilograms());
        ItemIdentity.setFishGrade(stack, roll.grade());
        decorateFishLore(stack, fishId, roll);

        PlayerDataStore.CatchResult result =
                recordCodex(player, fishId, roll.kilograms());
        if (result == null) return;

        // 个人首次钓到该鱼种：提示一次，强化「收集」的正反馈。
        if (result.firstCatch() && plugin.getConfig().getBoolean("codex.announce-first-catch", true)) {
            int collected = plugin.getPlayerData().collectedCount(player.getUniqueId());
            int total = plugin.getPlayerData().totalSpecies();
            player.sendMessage(ChatColor.GREEN + "首次钓到 " + ChatColor.WHITE + fishTitle(fishId)
                    + ChatColor.GREEN + "！图鉴进度 " + collected + "/" + total);
        }
        if (result.personalBest()) {
            player.sendMessage(ChatColor.YELLOW + "个人新纪录：" + ChatColor.WHITE + fishTitle(fishId)
                    + ChatColor.YELLOW + " " + roll.kilograms() + " kg");
        }
        if (result.globalBest() && weights.broadcasts(roll.grade())) {
            String message = ChatColor.GOLD + "[钓鱼] " + ChatColor.WHITE + player.getName()
                    + ChatColor.GOLD + " 钓到了本服最大的 " + fishTitle(fishId)
                    + "（" + weights.gradeName(roll.grade()) + " " + roll.kilograms() + " kg）！";
            // 全服播报要遍历所有在线玩家，必须回到全局线程执行：
            // 这里正处在钓获者所在 region 的线程上，直接广播会跨 region 触碰其它玩家。
            Scheduler.runGlobal(plugin, () -> Bukkit.getServer().broadcastMessage(message));
        }
    }

    /** 把重量与品级写进鱼的 lore（保留 CraftEngine 或兜底逻辑已有的其它行）。 */
    private void decorateFishLore(ItemStack stack, String fishId, FishWeight.Roll roll) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        // 移除本插件上一次写入的重量/品级行，避免重复叠加。
        lore.removeIf(FishingService::isWeightLoreLine);
        ChatColor color = weights.gradeColor(roll.grade());
        lore.add(color + "【" + weights.gradeName(roll.grade()) + "】"
                + ChatColor.WHITE + roll.kilograms() + " kg");
        double record = plugin.getPlayerData().globalRecord(fishId);
        if (record > 0) {
            lore.add(ChatColor.DARK_GRAY + "全服最大 " + record + " kg");
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);
    }

    /** 判断某行 lore 是否为本插件写入的重量/品级行。 */
    private static boolean isWeightLoreLine(String line) {
        if (line == null) return false;
        String stripped = ChatColor.stripColor(line);
        if (stripped == null) return false;
        return stripped.endsWith(" kg") || stripped.startsWith("全服最大 ");
    }

    /** 更新图鉴与纪录，返回本次结果。 */
    private PlayerDataStore.CatchResult recordCodex(Player player, String fishId, double weight) {
        if (!plugin.getConfig().getBoolean("codex.enabled", true)) return null;
        return plugin.getPlayerData().record(player.getUniqueId(), player.getName(), fishId, weight);
    }

    /** 鱼的中文名。 */
    private String fishTitle(String fishId) {
        return ItemFactory.fishName(fishId);
    }

    private String rollCategory(Player player) {
        int fish = Math.max(0, plugin.getConfig().getInt("fishing.category-weights.fish", 85));
        int junk = Math.max(0, plugin.getConfig().getInt("fishing.category-weights.junk", 10));
        int treasure = Math.max(0, plugin.getConfig().getInt("fishing.category-weights.treasure", 5));
        ItemStack rod = player.getInventory().getItemInMainHand();
        int luck = rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA);
        int hunter = ItemIdentity.customEnchantment(rod, "treasure_hunter");
        treasure += luck * 2 + hunter * 2;
        junk = Math.max(1, junk - luck - hunter);
        int total = fish + junk + treasure;
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        if (roll < fish) return "fish";
        if (roll < fish + junk) return "junk";
        return "treasure";
    }

    private Map<?, ?> rollEntry(String category, Biome biome) {
        List<Map<?, ?>> entries = new ArrayList<>(plugin.getConfig().getMapList("fishing.loot." + category));
        entries.removeIf(entry -> !biomeAllowed(entry, biome));
        int total = entries.stream().mapToInt(entry -> Math.max(0, intValue(entry, "weight", 0))).sum();
        if (total <= 0) return null;
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (Map<?, ?> entry : entries) {
            roll -= Math.max(0, intValue(entry, "weight", 0));
            if (roll < 0) return entry;
        }
        return entries.getLast();
    }

    private boolean biomeAllowed(Map<?, ?> entry, Biome biome) {
        Object value = entry.get("biomes");
        if (!(value instanceof List<?> biomes) || biomes.isEmpty()) return true;
        for (Object candidate : biomes) {
            if (candidate != null && candidate.toString().equalsIgnoreCase(biome.toString())) return true;
        }
        return false;
    }

    private ItemStack createVanilla(String id, int amount) {
        Material material = Material.matchMaterial(id.toUpperCase(Locale.ROOT));
        if (material == null) material = Material.COD;
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        if ("ink_sac".equals(id)) stack.setAmount(Math.max(1, amount));
        return stack;
    }

    private ItemStack createEnchanted(String id) {
        Material material = switch (id) {
            case "fishing_rod", "random_fishing_rod" -> Material.FISHING_ROD;
            case "bow", "random_bow" -> Material.BOW;
            default -> Material.BOOK;
        };
        ItemStack stack = new ItemStack(material);
        if (material == Material.BOOK) {
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) stack.getItemMeta();
            if (meta != null) {
                meta.addStoredEnchant(Enchantment.LURE, 1 + ThreadLocalRandom.current().nextInt(3), true);
                meta.addStoredEnchant(Enchantment.LUCK_OF_THE_SEA, 1 + ThreadLocalRandom.current().nextInt(3), true);
                // 附魔书也能开出 NewFish 附魔，这是它们唯一的自然获取途径。
                applyNaturalEnchantment(meta, "book");
                stack.setItemMeta(meta);
            }
        } else {
            stack.addUnsafeEnchantment(Enchantment.LURE, 1 + ThreadLocalRandom.current().nextInt(3));
            stack.addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, 1 + ThreadLocalRandom.current().nextInt(3));
            applyNaturalEnchantment(stack, material == Material.BOW ? "bow" : "rod");
        }
        return stack;
    }

    /**
     * 按掉落配置随机给物品附上一个 NewFish 附魔。
     *
     * <p>这是 NewFish 附魔的自然获取途径：玩家钓上「附魔鱼竿 / 附魔弓 / 附魔书」时，
     * 有概率自带一个本插件的附魔，而不再只能靠管理员命令。
     *
     * @param target 目标类别：rod（鱼竿）/ bow（弓）/ book（附魔书）
     */
    private void applyNaturalEnchantment(ItemStack stack, String target) {
        if (!plugin.getConfig().getBoolean("enchantments.natural-loot.enabled", true)) return;
        double chance = plugin.getConfig().getDouble("enchantments.natural-loot.chance", 0.35);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        List<String> candidates = plugin.getConfig()
                .getStringList("enchantments.natural-loot.pool." + target);
        if (candidates.isEmpty()) return;
        String chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        ItemIdentity.setCustomEnchantment(stack, chosen, rollNaturalLevel(chosen));
    }

    /** 附魔书版本：把 NewFish 附魔写进「存储的附魔」而不是物品本身的附魔。 */
    private void applyNaturalEnchantment(EnchantmentStorageMeta meta, String target) {
        if (!plugin.getConfig().getBoolean("enchantments.natural-loot.enabled", true)) return;
        double chance = plugin.getConfig().getDouble("enchantments.natural-loot.chance", 0.35);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        List<String> candidates = plugin.getConfig()
                .getStringList("enchantments.natural-loot.pool." + target);
        if (candidates.isEmpty()) return;
        String chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Enchantment enchantment = EnchantmentKeys.get(chosen);
        if (enchantment == null) return;
        meta.addStoredEnchant(enchantment, rollNaturalLevel(chosen), true);
    }

    /**
     * 掷出该附魔自然出现时的等级。
     *
     * <p>等级区间来自 {@code enchantments.natural-loot.levels.<附魔ID>}，
     * 缺失时回落到 {@code enchantments.natural-loot.levels.default}。
     * 区间两端都是闭区间；配置非法（空、非递进）时一律给 1 级，保证不会抛异常。
     */
    private int rollNaturalLevel(String enchantmentId) {
        String base = "enchantments.natural-loot.levels.";
        List<Integer> range = plugin.getConfig().getIntegerList(base + enchantmentId);
        if (range.isEmpty()) range = plugin.getConfig().getIntegerList(base + "default");
        if (range.isEmpty()) return 1;
        int low = Math.max(1, range.get(0));
        int high = range.size() > 1 ? Math.max(low, range.get(1)) : low;
        return low == high ? low : ThreadLocalRandom.current().nextInt(low, high + 1);
    }

    private ItemStack createMap(World world, org.bukkit.Location location, String id) {
        ItemStack stack = new ItemStack(Material.MAP);
        MapMeta meta = (MapMeta) stack.getItemMeta();
        if (meta != null) {
            org.bukkit.Location center = findStructure(world, location, id);
            if (center == null) center = location;
            MapView view = Bukkit.createMap(world);
            view.setCenterX(center.getBlockX());
            view.setCenterZ(center.getBlockZ());
            view.setScale(MapView.Scale.CLOSE);
            view.setTrackingPosition(true);
            view.setUnlimitedTracking(false);
            meta.setMapView(view);
            meta.setDisplayName(ChatColor.AQUA + MAP_NAMES.getOrDefault(id, title(id) + " Map"));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private org.bukkit.Location findStructure(World world, org.bukkit.Location origin, String id) {
        String field = switch (id) {
            case "buried_treasure" -> "BURIED_TREASURE";
            case "monument" -> "OCEAN_MONUMENT";
            case "ocean_ruin" -> "OCEAN_RUIN";
            case "ruined_portal" -> "RUINED_PORTAL";
            default -> "SHIPWRECK";
        };
        try {
            Class<?> structureType = Class.forName("org.bukkit.generator.structure.StructureType");
            Object type = structureType.getField(field).get(null);
            java.lang.reflect.Method locate = world.getClass().getMethod("locateNearestStructure", org.bukkit.Location.class, structureType, int.class, boolean.class);
            Object result = locate.invoke(world, origin, type, plugin.getConfig().getInt("fishing.map-search-radius", 128), false);
            if (result instanceof org.bukkit.Location found) return found;
            if (result != null) {
                Object found = result.getClass().getMethod("getLocation").invoke(result);
                if (found instanceof org.bukkit.Location location) return location;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }

    private String title(String value) {
        String[] words = value.replace('_', ' ').split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : value.toString();
    }

    private static int intValue(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
