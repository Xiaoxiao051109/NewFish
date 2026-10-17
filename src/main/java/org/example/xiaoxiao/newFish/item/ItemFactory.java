package org.example.xiaoxiao.newFish.item;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.example.xiaoxiao.newFish.integration.CraftEngineBridge;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemFactory {
    /** CraftEngine 不可用时使用的兜底物品中文名称。 */
    private static final Map<String, String> FISH_NAMES = Map.ofEntries(
            Map.entry("anchovy", "鳀鱼"),
            Map.entry("angelfish", "神仙鱼"),
            Map.entry("arapaima", "巨骨舌鱼"),
            Map.entry("arowana", "龙鱼"),
            Map.entry("barbel_steed", "花䱻"),
            Map.entry("barred_knifejaw", "条石鲷"),
            Map.entry("bass", "鲈鱼"),
            Map.entry("bitterling", "鳑鲏"),
            Map.entry("bluegill", "蓝鳃太阳鱼"),
            Map.entry("brook_trout", "溪红点鲑"),
            Map.entry("carp", "鲤鱼"),
            Map.entry("catfish", "鲶鱼"),
            Map.entry("cherry_salmon", "樱鳟"),
            Map.entry("coelacanth", "腔棘鱼"),
            Map.entry("crawfish", "小龙虾"),
            Map.entry("crucian_carp", "鲫鱼"),
            Map.entry("dab", "鲽鱼"),
            Map.entry("dace", "鲦鱼"),
            Map.entry("eel", "鳗鱼"),
            Map.entry("freshwater_goby", "淡水虾虎鱼"),
            Map.entry("frog", "青蛙"),
            Map.entry("giant_catfish", "巨型鲶鱼"),
            Map.entry("giant_snakehead", "巨型黑鱼"),
            Map.entry("goldfish", "金鱼"),
            Map.entry("guppy", "孔雀鱼"),
            Map.entry("horse_mackerel", "竹荚鱼"),
            Map.entry("jellyfish", "水母"),
            Map.entry("killifish", "鳉鱼"),
            Map.entry("koi", "锦鲤"),
            Map.entry("large_bass", "大鲈鱼"),
            Map.entry("large_char", "日本红点鲑"),
            Map.entry("loach", "泥鳅"),
            Map.entry("lobster", "龙虾"),
            Map.entry("marlin", "蓝枪鱼"),
            Map.entry("octopus", "章鱼"),
            Map.entry("olive_flounder", "牙鲆"),
            Map.entry("pale_chub", "白鲦"),
            Map.entry("piranha", "食人鱼"),
            Map.entry("pond_smelt", "池沼公鱼"),
            Map.entry("popeyed_goldfish", "龙睛金鱼"),
            Map.entry("rainbow_trout", "虹鳟"),
            Map.entry("red_snapper", "红笛鲷"),
            Map.entry("sea_bass", "海鲈鱼"),
            Map.entry("seahorse", "海马"),
            Map.entry("small_bass", "小鲈鱼"),
            Map.entry("squid", "鱿鱼"),
            Map.entry("stringfish", "远东哲罗鱼"),
            Map.entry("sweetfish", "香鱼")
    );

    private final CraftEngineBridge craftEngine;

    public ItemFactory(CraftEngineBridge craftEngine) {
        this.craftEngine = craftEngine;
    }

    /** 全部自定义鱼的逻辑 ID，供命令补全使用。 */
    public static List<String> fishIds() {
        return List.copyOf(FISH_NAMES.keySet());
    }

    /** 鱼的中文名，用于消息展示；未知 ID 回退为标题化处理。 */
    public static String fishName(String id) {
        if (id == null) return "?";
        String normalized = id.toLowerCase(Locale.ROOT);
        String name = FISH_NAMES.get(normalized);
        if (name != null) return name;
        return normalized.replace('_', ' ');
    }

    public ItemStack custom(String id) {
        if (id == null) return null;
        String normalized = id.toLowerCase(Locale.ROOT);
        if (FISH_NAMES.containsKey(normalized)) return fish(normalized);
        if ("pirahna".equals(normalized)) return fish("piranha");
        return null;
    }

    public ItemStack fish(String id) {
        ItemStack stack = craftEngine.build(id).orElseGet(() -> fallbackFish(id));
        ItemIdentity.mark(stack, "fish", id);
        return stack;
    }

    public ItemStack mobToken(String mob) {
        ItemStack stack = new ItemStack(Material.KELP);
        ItemIdentity.markMobToken(stack, mob);
        return stack;
    }

    public void applyFallbackFishEffect(ItemStack stack, org.bukkit.entity.Player player) {
        // ExlineFishing fish use CraftEngine's food/consumable components. The PDC marker
        // remains available for future per-fish effects without identifying by display name.
    }

    private ItemStack fallbackFish(String id) {
        ItemStack stack = new ItemStack(Material.COD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + FISH_NAMES.getOrDefault(id, title(id)));
            meta.setLore(List.of(ChatColor.GRAY + "钓鱼扩展"));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String title(String id) {
        String[] words = id.replace('_', ' ').split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
