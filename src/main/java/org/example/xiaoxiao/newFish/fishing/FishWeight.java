package org.example.xiaoxiao.newFish.fishing;

import org.bukkit.ChatColor;
import org.example.xiaoxiao.newFish.NewFish;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 鱼获重量与品级计算。
 *
 * <p>同一鱼种每次钓获的重量随机，并据此落进普通 / 稀有 / 传说三档。
 * 这样「钓到某种鱼」和「钓到一条好鱼」就变成两件事，重复钓鱼才有意义。
 *
 * <p>重量区间按鱼种基础值缩放：不同鱼种体型差异很大（鳀鱼和蓝枪鱼不该同一个量级），
 * 因此每条鱼有一个「基础重量」配置，实际重量在其上按倍率浮动。
 */
public final class FishWeight {
    /** 品级 ID。 */
    public static final String COMMON = "common";
    public static final String RARE = "rare";
    public static final String LEGENDARY = "legendary";

    private final NewFish plugin;

    public FishWeight(NewFish plugin) {
        this.plugin = plugin;
    }

    /** 一次重量与品级的计算结果。 */
    public record Roll(double kilograms, String grade) {
    }

    /**
     * 为指定鱼种掷一次重量。
     *
     * @param fishId 鱼的逻辑 ID
     */
    public Roll roll(String fishId) {
        double base = plugin.getConfig().getDouble("fishing.weight.base-kilograms." + fishId, defaultBase());
        double variance = plugin.getConfig().getDouble("fishing.weight.variance", 0.35);
        double minFactor = plugin.getConfig().getDouble("fishing.weight.legendary-factor", 1.6);

        // 在 [1-variance, 1+variance] 区间内浮动，再乘基础重量。
        double factor = 1.0 + ThreadLocalRandom.current().nextDouble(-variance, variance);
        double kilograms = round(base * factor, 2);

        // 超过「基础 × legendary-factor」即为传说档，其余按中位线分普通/稀有。
        String grade;
        if (kilograms >= base * minFactor) {
            grade = LEGENDARY;
        } else if (kilograms >= base * (1.0 + variance / 2.0)) {
            grade = RARE;
        } else {
            grade = COMMON;
        }
        return new Roll(kilograms, grade);
    }

    /** 品级对应的中文名。 */
    public String gradeName(String grade) {
        return switch (grade) {
            case LEGENDARY -> plugin.getConfig().getString("fishing.weight.grades.legendary.name", "传说");
            case RARE -> plugin.getConfig().getString("fishing.weight.grades.rare.name", "稀有");
            default -> plugin.getConfig().getString("fishing.weight.grades.common.name", "普通");
        };
    }

    /** 品级对应的显示颜色。 */
    public ChatColor gradeColor(String grade) {
        String raw = switch (grade) {
            case LEGENDARY -> plugin.getConfig().getString("fishing.weight.grades.legendary.color", "GOLD");
            case RARE -> plugin.getConfig().getString("fishing.weight.grades.rare.color", "AQUA");
            default -> plugin.getConfig().getString("fishing.weight.grades.common.color", "GRAY");
        };
        try {
            return ChatColor.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ChatColor.GRAY;
        }
    }

    /** 该品级是否触发全服播报。 */
    public boolean broadcasts(String grade) {
        return LEGENDARY.equals(grade)
                && plugin.getConfig().getBoolean("fishing.weight.broadcast-legendary", true);
    }

    /** 默认基础重量（未单独配置的鱼种）。 */
    private double defaultBase() {
        return plugin.getConfig().getDouble("fishing.weight.default-base-kilograms", 1.0);
    }

    private double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }
}
