package org.example.xiaoxiao.newFish.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.example.xiaoxiao.newFish.NewFish;
import org.example.xiaoxiao.newFish.item.ItemFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家钓鱼档案：图鉴收集进度与个人最佳纪录。
 *
 * <p>数据量很小（每人最多几十条），因此用一个 YAML 文件整体保存，
 * 避免给插件引入数据库依赖。写入采用「先写临时文件再原子替换」，
 * 防止服务器崩溃时把档案写坏。
 *
 * <p>所有公开方法都做了线程安全处理：文件访问加锁，
 * 内存缓存用并发容器，因此可以在 Folia 的多个 region 线程上安全调用。
 */
public final class PlayerDataStore {
    private final NewFish plugin;
    private final File file;
    private final Object ioLock = new Object();

    /** 玩家 UUID -> 档案。 */
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    /** 全服各鱼种的最大重量纪录：鱼 ID -> [重量, 玩家名]。 */
    private final Map<String, Record> globalRecords = new ConcurrentHashMap<>();

    public PlayerDataStore(NewFish plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    /** 从磁盘载入档案。 */
    public void load() {
        profiles.clear();
        globalRecords.clear();
        if (!file.exists()) return;
        synchronized (ioLock) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String rawId : yaml.getKeys(false)) {
                if ("global-records".equals(rawId)) continue;
                UUID uuid;
                try {
                    uuid = UUID.fromString(rawId);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                PlayerProfile profile = new PlayerProfile();
                profile.caught.addAll(yaml.getStringList(rawId + ".caught"));
                profile.totalCaught = yaml.getInt(rawId + ".total", 0);
                profile.personalBest.putAll(readBest(yaml, rawId + ".best"));
                profiles.put(uuid, profile);
            }
            var section = yaml.getConfigurationSection("global-records");
            if (section != null) {
                for (String fishId : section.getKeys(false)) {
                    double weight = section.getDouble(fishId + ".weight", 0.0);
                    String holder = section.getString(fishId + ".holder", "?");
                    if (weight > 0) globalRecords.put(fishId, new Record(weight, holder));
                }
            }
        }
        plugin.getLogger().info("已载入 " + profiles.size() + " 份玩家钓鱼档案。");
    }

    private Map<String, Double> readBest(YamlConfiguration yaml, String path) {
        Map<String, Double> best = new LinkedHashMap<>();
        var section = yaml.getConfigurationSection(path);
        if (section == null) return best;
        for (String fishId : section.getKeys(false)) {
            double weight = section.getDouble(fishId, 0.0);
            if (weight > 0) best.put(fishId, weight);
        }
        return best;
    }

    /** 把档案写回磁盘。 */
    public void save() {
        synchronized (ioLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, PlayerProfile> entry : profiles.entrySet()) {
                String base = entry.getKey().toString();
                PlayerProfile profile = entry.getValue();
                yaml.set(base + ".caught", new ArrayList<>(profile.caught));
                yaml.set(base + ".total", profile.totalCaught);
                for (Map.Entry<String, Double> best : profile.personalBest.entrySet()) {
                    yaml.set(base + ".best." + best.getKey(), best.getValue());
                }
            }
            for (Map.Entry<String, Record> entry : globalRecords.entrySet()) {
                yaml.set("global-records." + entry.getKey() + ".weight", entry.getValue().weight);
                yaml.set("global-records." + entry.getKey() + ".holder", entry.getValue().holder);
            }
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                File temp = new File(file.getParentFile(), file.getName() + ".tmp");
                yaml.save(temp);
                java.nio.file.Files.move(temp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                plugin.getLogger().warning("保存玩家钓鱼档案失败：" + exception.getMessage());
            }
        }
    }

    /**
     * 记录一次钓获，返回这次钓获是否是该鱼种的**个人首次**。
     *
     * @param fishId 鱼的逻辑 ID
     * @param weight 本次重量（千克），≤0 表示不参与纪录统计
     */
    public CatchResult record(UUID playerId, String playerName, String fishId, double weight) {
        PlayerProfile profile = profiles.computeIfAbsent(playerId, ignored -> new PlayerProfile());
        boolean firstCatch = profile.caught.add(fishId);
        profile.totalCaught++;

        boolean personalBest = false;
        boolean globalBest = false;
        if (weight > 0) {
            Double previous = profile.personalBest.get(fishId);
            if (previous == null || weight > previous) {
                profile.personalBest.put(fishId, weight);
                personalBest = previous != null;
            }
            Record record = globalRecords.get(fishId);
            if (record == null || weight > record.weight) {
                globalBest = record != null;
                globalRecords.put(fishId, new Record(weight, playerName));
            }
        }
        return new CatchResult(firstCatch, personalBest, globalBest);
    }

    /** 该玩家已收集的鱼种数量。 */
    public int collectedCount(UUID playerId) {
        PlayerProfile profile = profiles.get(playerId);
        return profile == null ? 0 : profile.caught.size();
    }

    /** 该玩家累计钓获次数。 */
    public int totalCaught(UUID playerId) {
        PlayerProfile profile = profiles.get(playerId);
        return profile == null ? 0 : profile.totalCaught;
    }

    /** 该玩家是否已收集某个鱼种。 */
    public boolean hasCollected(UUID playerId, String fishId) {
        PlayerProfile profile = profiles.get(playerId);
        return profile != null && profile.caught.contains(fishId);
    }

    /** 该玩家某个鱼种的个人最佳重量，没有纪录返回 0。 */
    public double personalBest(UUID playerId, String fishId) {
        PlayerProfile profile = profiles.get(playerId);
        if (profile == null) return 0.0;
        return profile.personalBest.getOrDefault(fishId, 0.0);
    }

    /** 该玩家全部鱼种的个人最佳重量（只读副本）。 */
    public Map<String, Double> personalBests(UUID playerId) {
        PlayerProfile profile = profiles.get(playerId);
        return profile == null ? Map.of() : Map.copyOf(profile.personalBest);
    }

    /** 全服某个鱼种的最大重量纪录，没有则返回 0。 */
    public double globalRecord(String fishId) {
        Record record = globalRecords.get(fishId);
        return record == null ? 0.0 : record.weight;
    }

    /** 全服某个鱼种的最大重量纪录保持者，没有则返回 null。 */
    public String globalRecordHolder(String fishId) {
        Record record = globalRecords.get(fishId);
        return record == null ? null : record.holder;
    }

    /** 全部鱼的图鉴总条目数（用于计算收集百分比）。 */
    public int totalSpecies() {
        return ItemFactory.fishIds().size();
    }

    /** 按重量排序的全服纪录榜，最多返回 limit 条。 */
    public List<Map.Entry<String, Record>> topRecords(int limit) {
        List<Map.Entry<String, Record>> entries = new ArrayList<>(globalRecords.entrySet());
        entries.sort(Comparator.comparingDouble((Map.Entry<String, Record> entry) -> entry.getValue().weight).reversed());
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    /** 单个玩家的档案。 */
    private static final class PlayerProfile {
        private final Set<String> caught = new LinkedHashSet<>();
        private final Map<String, Double> personalBest = new LinkedHashMap<>();
        private int totalCaught;
    }

    /** 全服纪录条目。 */
    public record Record(double weight, String holder) {
    }

    /** 一次钓获的结果标记。 */
    public record CatchResult(boolean firstCatch, boolean personalBest, boolean globalBest) {
    }

    /** 供命令补全使用：把输入前缀规范化为鱼 ID。 */
    public static String normalizeFishId(String raw) {
        return raw == null ? null : raw.toLowerCase(Locale.ROOT);
    }

    /** 已收集鱼种按 ID 排序，便于图鉴展示。 */
    public List<String> collectedSorted(UUID playerId) {
        PlayerProfile profile = profiles.get(playerId);
        if (profile == null) return List.of();
        List<String> list = new ArrayList<>(profile.caught);
        list.sort(Comparator.naturalOrder());
        return list;
    }
}
