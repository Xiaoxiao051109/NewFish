package org.example.xiaoxiao.newFish.enchantment;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryComposeEvent;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemType;

import java.util.List;

/**
 * 新版注册表事件（{@code compose()}）实现，适用于 **1.21.6 及以上**。
 *
 * <p>⚠️ 本类引用的 {@link RegistryComposeEvent} 在 1.21.0 ~ 1.21.5 并不存在，
 * 因此**不能被那些服务端加载**。调用方必须先探测
 * {@code RegistryEventProvider#compose} 存在，再走这里 ——
 * 这样旧服务端永远不会触发本类的类加载。
 */
public final class ComposeEnchantmentRegistrar {

    private ComposeEnchantmentRegistrar() {
    }

    /** 在当前引导上下文里挂上 compose 事件处理器。 */
    public static void install(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
                RegistryEvents.ENCHANTMENT.compose().newHandler(event -> {
                    try {
                        registerAll(event);
                        context.getLogger().info("[NewFish] 已通过 compose() 注册 "
                                + EnchantmentSpecs.ALL.size() + " 个附魔（服务端 ≥ 1.21.6）。");
                    } catch (Throwable throwable) {
                        // 引导期异常不会让插件崩溃，只会静默丢掉注册结果，必须显式打出来。
                        context.getLogger().error("[NewFish] 附魔注册失败（compose 路径）", throwable);
                    }
                }));
    }

    private static void registerAll(RegistryComposeEvent<Enchantment, EnchantmentRegistryEntry.Builder> event) {
        for (EnchantmentSpecs.Spec spec : EnchantmentSpecs.ALL) {
            RegistryKeySet<ItemType> supported = tag(event, spec.tag());
            // 附魔之间不互斥；空集合表示“可与任意附魔共存”。
            RegistryKeySet<Enchantment> exclusive = RegistrySet.keySet(RegistryKey.ENCHANTMENT, List.of());
            event.registry().register(
                    TypedKey.create(RegistryKey.ENCHANTMENT, EnchantmentKeys.key(spec.id())),
                    builder -> builder
                            .description(Component.text(spec.displayName()))
                            .supportedItems(supported)
                            .weight(EnchantmentSpecs.WEIGHT)
                            .maxLevel(spec.maxLevel())
                            .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(
                                    EnchantmentSpecs.COST_BASE, EnchantmentSpecs.COST_PER_LEVEL))
                            .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(
                                    EnchantmentSpecs.COST_BASE + spec.maxLevel() * EnchantmentSpecs.COST_PER_LEVEL,
                                    EnchantmentSpecs.COST_PER_LEVEL))
                            .anvilCost(1)
                            .activeSlots(spec.slot())
                            .exclusiveWith(exclusive));
        }
    }

    /**
     * 取得（必要时创建）原版物品标签，作为附魔的物品范围。
     *
     * <p>{@code Tag} 本身实现 {@code RegistryKeySet}，可直接传给 {@code supportedItems}。
     * 用标签引用比展开成物品清单更稳：标签内容随版本变动，引用会在运行期按当前版本解析。
     */
    private static RegistryKeySet<ItemType> tag(
            RegistryComposeEvent<Enchantment, EnchantmentRegistryEntry.Builder> event,
            EnchantmentSpecs.Tag which) {
        return switch (which) {
            case FISHING -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_FISHING);
            case BOW -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_BOW);
            case ARMOR -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_ARMOR);
            case TRIDENT -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_TRIDENT);
        };
    }
}
