package org.example.xiaoxiao.newFish.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.example.xiaoxiao.newFish.NewFish;
import org.example.xiaoxiao.newFish.data.PlayerDataStore;
import org.example.xiaoxiao.newFish.item.ItemFactory;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NewFish 的 PlaceholderAPI 扩展。
 *
 * <p>该扩展由 {@link NewFish} 在启用时手动注册（PlaceholderAPI 在 plugin.yml 里
 * 是 depend，因此注册时它一定已经就绪）。所有占位符都以 {@code newfish_} 开头，
 * 例如 {@code %newfish_collected%}、{@code %newfish_record_bass%}。
 *
 * <p>取值全部来自内存中的 {@link PlayerDataStore} 缓存，不做磁盘访问，
 * 因此可以在计分板、Tab 列表等高频刷新场景安全使用。
 */
public final class NewFishExpansion extends PlaceholderExpansion {
    private final NewFish plugin;

    public NewFishExpansion(NewFish plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "newfish";
    }

    @Override
    public @NotNull String getAuthor() {
        return "NewFish";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** 跟随插件卸载，避免数据库/服务器重载后残留。 */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        PlayerDataStore data = plugin.getPlayerData();
        if (data == null) return "";

        String key = params.toLowerCase(Locale.ROOT);

        // ---- 与玩家无关的全局占位符 ----
        switch (key) {
            case "species" -> {
                return String.valueOf(data.totalSpecies());
            }
            case "top" -> {
                return formatTop(data);
            }
            default -> {
                // 继续往下走，处理与玩家相关的占位符
            }
        }

        if (player == null) return "";
        java.util.UUID id = player.getUniqueId();
        int species = data.totalSpecies();
        int collected = data.collectedCount(id);

        switch (key) {
            case "collected" -> {
                return String.valueOf(collected);
            }
            case "collected_percent", "collected-percent" -> {
                if (species <= 0) return "0";
                return formatDecimal(collected * 100.0 / species, 1);
            }
            case "caught" -> {
                return String.valueOf(data.totalCaught(id));
            }
            case "missing" -> {
                return String.valueOf(Math.max(0, species - collected));
            }
            case "complete" -> {
                return collected >= species && species > 0 ? "是" : "否";
            }
            default -> {
                // 前缀类占位符继续判定
            }
        }

        // ---- %newfish_best_<鱼ID>%：该玩家的个人最佳重量 ----
        if (key.startsWith("best_") || key.startsWith("best-")) {
            String fishId = PlayerDataStore.normalizeFishId(key.substring(5));
            double weight = data.personalBest(id, fishId);
            return weight > 0 ? formatWeight(weight) : "0";
        }

        // ---- %newfish_full_<鱼ID>%：个人最佳，附带鱼的中文名（无纪录时显示 ???） ----
        if (key.startsWith("full_") || key.startsWith("full-")) {
            String fishId = PlayerDataStore.normalizeFishId(key.substring(5));
            double weight = data.personalBest(id, fishId);
            if (weight <= 0) return "???";
            return ItemFactory.fishName(fishId) + " " + formatWeight(weight) + "kg";
        }

        // ---- %newfish_has_<鱼ID>%：是否已收录进图鉴 ----
        if (key.startsWith("has_") || key.startsWith("has-")) {
            String fishId = PlayerDataStore.normalizeFishId(key.substring(4));
            return data.hasCollected(id, fishId) ? "是" : "否";
        }

        // ---- %newfish_record_<鱼ID>%：全服最大重量 ----
        if (key.startsWith("record_") || key.startsWith("record-")) {
            String fishId = PlayerDataStore.normalizeFishId(key.substring(7));
            double weight = data.globalRecord(fishId);
            return weight > 0 ? formatWeight(weight) : "0";
        }

        // ---- %newfish_holder_<鱼ID>%：全服纪录保持者 ----
        if (key.startsWith("holder_") || key.startsWith("holder-")) {
            String fishId = PlayerDataStore.normalizeFishId(key.substring(7));
            String holder = data.globalRecordHolder(fishId);
            return holder == null ? "-" : holder;
        }

        return null;
    }

    /** 把全服纪录榜格式化成一行文本，元素之间用逗号分隔。 */
    private String formatTop(PlayerDataStore data) {
        int limit = plugin.getConfig().getInt("codex.top-limit", 5);
        if (limit <= 0) return "";
        List<Map.Entry<String, PlayerDataStore.Record>> top = data.topRecords(limit);
        if (top.isEmpty()) return "-";
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < top.size(); index++) {
            Map.Entry<String, PlayerDataStore.Record> entry = top.get(index);
            if (index > 0) builder.append(", ");
            builder.append(ItemFactory.fishName(entry.getKey()))
                    .append(' ')
                    .append(formatWeight(entry.getValue().weight()))
                    .append("kg");
        }
        return builder.toString();
    }

    private static String formatWeight(double kilograms) {
        return formatDecimal(kilograms, 2);
    }

    /** 按小数位数格式化，同时去掉多余的尾随零，避免出现 "3.50" 这类观感。 */
    private static String formatDecimal(double value, int scale) {
        java.math.BigDecimal decimal = java.math.BigDecimal.valueOf(value)
                .setScale(scale, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (decimal.scale() < 0) decimal = decimal.setScale(0, java.math.RoundingMode.HALF_UP);
        return decimal.toPlainString();
    }
}
