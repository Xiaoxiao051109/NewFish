package org.example.xiaoxiao.newFish.enchantment;

import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.List;

/**
 * 7 个 NewFish 附魔的注册规格。
 *
 * <p>抽成一份共享定义是为了让两套注册表 API（{@code compose()} 与 {@code freeze()}）
 * 使用同一组数值与物品范围 —— 否则两条路径很容易在后续维护中悄悄漂移。
 *
 * <p>这两套 API 的分界（用 javap 逐个版本核对过）：
 * <ul>
 *   <li>1.21 ~ 1.21.8：有 {@code freeze()}</li>
 *   <li>1.21.6 ~ 26.x：有 {@code compose()}</li>
 *   <li>1.21.9 起：{@code freeze()} 被移除，只剩 {@code compose()}</li>
 * </ul>
 */
public final class EnchantmentSpecs {

    /** 附魔的物品范围类别，对应原版的可附魔标签。 */
    public enum Tag {
        FISHING, BOW, ARMOR, TRIDENT
    }

    /**
     * 单个附魔的注册规格。
     *
     * @param id        newfish 命名空间下的附魔 ID
     * @param displayName 游戏内显示名
     * @param maxLevel  最高等级
     * @param tag       物品范围类别
     * @param slot      生效槽位
     */
    public record Spec(String id, String displayName, int maxLevel, Tag tag, EquipmentSlotGroup slot) {
    }

    /**
     * 附魔台出现权重：统一压到原版允许的最低值 1。
     *
     * <p>这些附魔的设计获取途径是「钓鱼产出」，附魔台/村民保留能力但不作为正常来源。
     */
    public static final int WEIGHT = 1;

    /**
     * 附魔台成本区间下界。
     *
     * <p>原版附魔台先算出 1~30 级的 {@code modifiedLevel}（30 级满书架时上限约 50），
     * 再只从 {@code minimumCost <= modifiedLevel} 的候选里抽。下界设为 60 后，
     * 任何等级、任何书架配置的附魔台都抽不到，而铁砧合并与 {@code /enchant} 仍可用。
     */
    public static final int COST_BASE = 60;

    /** 每级递增的成本。 */
    public static final int COST_PER_LEVEL = 20;

    /** 全部附魔，顺序固定。 */
    public static final List<Spec> ALL = List.of(
            // 鱼竿系：作用于鱼竿（原版可附魔钓鱼标签）。
            new Spec(EnchantmentKeys.SEA_LURE, "海之诱饵", 3, Tag.FISHING, EquipmentSlotGroup.MAINHAND),
            new Spec(EnchantmentKeys.TREASURE_HUNTER, "宝藏猎人", 3, Tag.FISHING, EquipmentSlotGroup.MAINHAND),
            new Spec(EnchantmentKeys.HOOKED_STRIKE, "鱼钩猛击", 3, Tag.FISHING, EquipmentSlotGroup.MAINHAND),
            new Spec(EnchantmentKeys.ENDER_HOOK, "末影鱼钩", 1, Tag.FISHING, EquipmentSlotGroup.MAINHAND),
            // 水痕：作用于弓。
            new Spec(EnchantmentKeys.WATER_TRAIL, "水痕", 3, Tag.BOW, EquipmentSlotGroup.MAINHAND),
            // 海豚恩惠：作用于护腿（沿用原有实现里的护腿判定）。
            new Spec(EnchantmentKeys.DOLPHINS_GRACE, "海豚恩惠", 1, Tag.ARMOR, EquipmentSlotGroup.LEGS),
            // 雷霆：作用于三叉戟。
            new Spec(EnchantmentKeys.THUNDERING, "雷霆", 1, Tag.TRIDENT, EquipmentSlotGroup.MAINHAND)
    );

    private EnchantmentSpecs() {
    }
}
