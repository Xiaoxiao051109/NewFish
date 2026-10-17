package org.example.xiaoxiao.newFish.enchantment;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NewFish 自定义附魔的集中定义。
 *
 * <p>这些附魔通过 Paper 的注册表事件在引导期注册成**真正的原版附魔**，
 * 因此读写一律走原版 {@link Enchantment} API，不再使用持久化数据标记伪装。
 * 副作用是附魔台、砂轮、铁砧以及原版 tooltip 都能正确识别它们。
 *
 * <p>这里的 {@link #KEY} 是附魔在 {@code newfish} 命名空间下的稳定 ID，
 * 既用于引导期注册，也用于游戏内读写。改动这些字符串会让旧物品上的附魔失效。
 */
public final class EnchantmentKeys {
    /** 附魔 ID（不含命名空间）。 */
    public static final String SEA_LURE = "sea_lure";
    public static final String TREASURE_HUNTER = "treasure_hunter";
    public static final String HOOKED_STRIKE = "hooked_strike";
    public static final String ENDER_HOOK = "ender_hook";
    public static final String WATER_TRAIL = "water_trail";
    public static final String DOLPHINS_GRACE = "dolphins_grace";
    public static final String THUNDERING = "thundering";

    /** 全部附魔 ID，顺序固定，用于命令补全与展示。 */
    public static final List<String> ALL = List.of(
            SEA_LURE, TREASURE_HUNTER, HOOKED_STRIKE, ENDER_HOOK,
            WATER_TRAIL, DOLPHINS_GRACE, THUNDERING);

    /** 附魔中文名，用于语言文件缺失时的兜底展示与命令反馈。 */
    private static final Map<String, String> NAMES = new LinkedHashMap<>(Map.of(
            SEA_LURE, "海之诱饵",
            TREASURE_HUNTER, "宝藏猎人",
            HOOKED_STRIKE, "鱼钩猛击",
            ENDER_HOOK, "末影鱼钩",
            WATER_TRAIL, "水痕",
            DOLPHINS_GRACE, "海豚恩惠",
            THUNDERING, "雷霆"
    ));

    private EnchantmentKeys() {
    }

    /** 附魔在 newfish 命名空间下的完整 Key。 */
    public static NamespacedKey key(String id) {
        return new NamespacedKey("newfish", id.toLowerCase(Locale.ROOT));
    }

    /** 附魔的中文显示名。 */
    public static String displayName(String id) {
        return NAMES.getOrDefault(id.toLowerCase(Locale.ROOT), id);
    }

    /** 全部附魔 ID 的中文名映射（只读）。 */
    public static Map<String, String> names() {
        return Map.copyOf(NAMES);
    }

    /**
     * 取得已注册的原版附魔对象。
     *
     * <p>引导期注册尚未完成或注册失败时返回 null，调用方需要自行兜底。
     */
    public static Enchantment get(String id) {
        return Enchantment.getByKey(key(id));
    }

    /**
     * 校验全部附魔是否都在注册表里能取到，用于启动自检。
     *
     * <p>返回「取不到的附魔 ID」列表；返回空列表表示 7 个附魔全部注册成功。
     * 附魔注册发生在引导期，一旦失败通常不会抛异常，只表现为
     * {@link #get(String)} 一直返回 null —— 于是命令提示"成功"但物品毫无变化。
     * 因此必须主动自检并把结果写进日志，否则这个故障完全静默。
     */
    public static List<String> missing() {
        List<String> absent = new java.util.ArrayList<>();
        for (String id : ALL) {
            if (get(id) == null) absent.add(id);
        }
        return absent;
    }

    /** 判断给定物品是否带有指定 NewFish 附魔。 */
    public static boolean has(ItemStack stack, String id) {
        return levelOf(stack, id) > 0;
    }

    /** 读取物品上指定 NewFish 附魔的等级，没有则返回 0。 */
    public static int levelOf(ItemStack stack, String id) {
        if (stack == null || stack.getType().isAir()) return 0;
        Enchantment enchantment = get(id);
        if (enchantment == null) return 0;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0;
        return meta.getEnchantLevel(enchantment);
    }

    /**
     * 把指定 NewFish 附魔按等级写到物品上。
     *
     * @return 是否真的写入成功。附魔未注册、物品非法或 meta 不可用时返回 false，
     *         调用方应据此提示失败，而不是无条件报告成功
     */
    public static boolean apply(ItemStack stack, String id, int level) {
        if (stack == null || stack.getType().isAir()) return false;
        Enchantment enchantment = get(id);
        if (enchantment == null) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        meta.addEnchant(enchantment, Math.max(1, level), true);
        stack.setItemMeta(meta);
        return true;
    }
}
