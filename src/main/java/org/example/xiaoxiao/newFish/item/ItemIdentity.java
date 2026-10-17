package org.example.xiaoxiao.newFish.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.example.xiaoxiao.newFish.NewFish;
import org.example.xiaoxiao.newFish.enchantment.EnchantmentKeys;

public final class ItemIdentity {
    private static NamespacedKey itemType;
    private static NamespacedKey variant;
    private static NamespacedKey mobToken;
    private static NamespacedKey temporary;
    private static NamespacedKey fishWeight;
    private static NamespacedKey fishGrade;

    private static NewFish plugin;

    private ItemIdentity() {
    }

    public static void init(NewFish plugin) {
        ItemIdentity.plugin = plugin;
        itemType = new NamespacedKey(plugin, "item_type");
        variant = new NamespacedKey(plugin, "variant");
        mobToken = new NamespacedKey(plugin, "mob_token");
        temporary = new NamespacedKey(plugin, "temporary");
        fishWeight = new NamespacedKey(plugin, "fish_weight");
        fishGrade = new NamespacedKey(plugin, "fish_grade");
    }

    public static ItemStack mark(ItemStack stack, String type, String value) {
        if (stack == null || stack.getType().isAir()) return stack;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.getPersistentDataContainer().set(itemType, PersistentDataType.STRING, type);
        if (value != null) {
            meta.getPersistentDataContainer().set(variant, PersistentDataType.STRING, value);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack markMobToken(ItemStack stack, String mob) {
        mark(stack, "mob_token", mob);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(mobToken, PersistentDataType.STRING, mob);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static String type(ItemStack stack) {
        return read(stack, itemType);
    }

    public static String variant(ItemStack stack) {
        return read(stack, variant);
    }

    public static String mobToken(ItemStack stack) {
        return read(stack, mobToken);
    }

    public static boolean is(ItemStack stack, String type, String value) {
        return type.equals(type(stack)) && (value == null || value.equals(variant(stack)));
    }

    /**
     * 给物品附上 NewFish 自定义附魔。
     *
     * <p>这些附魔已在引导期注册为**真正的原版附魔**，因此直接走原版 API 写入，
     * 显示、等级数字、发光与砂轮/铁砧识别全部交给原版，插件不再手写 lore。
     *
     * @return 是否写入成功；附魔未注册时返回 false，调用方应据此提示失败
     */
    public static boolean setCustomEnchantment(ItemStack stack, String enchantment, int level) {
        return EnchantmentKeys.apply(stack, enchantment, level);
    }

    /** 附魔的中文显示名（转发到集中定义，避免两处维护）。 */
    public static String enchantmentName(String enchantment) {
        return EnchantmentKeys.displayName(enchantment);
    }

    /** 读取物品上指定 NewFish 附魔的等级，没有则返回 0。 */
    public static int customEnchantment(ItemStack stack, String enchantment) {
        return EnchantmentKeys.levelOf(stack, enchantment);
    }

    /** 写入鱼获重量（千克）。 */
    public static void setFishWeight(ItemStack stack, double kilograms) {
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(fishWeight, PersistentDataType.DOUBLE, kilograms);
        stack.setItemMeta(meta);
    }

    /** 读取鱼获重量（千克），未记录时返回 0。 */
    public static double fishWeight(ItemStack stack) {
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        if (meta == null) return 0.0;
        Double value = meta.getPersistentDataContainer().get(fishWeight, PersistentDataType.DOUBLE);
        return value == null ? 0.0 : value;
    }

    /** 写入鱼获品级 ID（common / rare / legendary）。 */
    public static void setFishGrade(ItemStack stack, String grade) {
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        if (meta == null || grade == null) return;
        meta.getPersistentDataContainer().set(fishGrade, PersistentDataType.STRING, grade);
        stack.setItemMeta(meta);
    }

    /** 读取鱼获品级 ID，未记录时返回 null。 */
    public static String fishGrade(ItemStack stack) {
        return read(stack, fishGrade);
    }

    public static boolean isTemporary(ItemStack stack) {
        return "true".equals(read(stack, temporary));
    }

    public static void markTemporary(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(temporary, PersistentDataType.STRING, "true");
            stack.setItemMeta(meta);
        }
    }

    private static String read(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType().isAir()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
