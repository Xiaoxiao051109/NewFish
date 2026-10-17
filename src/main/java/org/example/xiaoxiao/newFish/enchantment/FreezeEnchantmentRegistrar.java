package org.example.xiaoxiao.newFish.enchantment;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryFreezeEvent;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.event.WritableRegistry;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemType;

import java.util.List;

/**
 * 旧版注册表事件（{@code freeze()}）实现，适用于 **1.21.0 ~ 1.21.8**。
 *
 * <p>为什么需要这一条路径：{@code compose()} 直到 1.21.6 才出现，
 * 而 1.21.0 ~ 1.21.5（含最常见的 1.21.1 / 1.21.4）只有 {@code freeze()}。
 * {@code freeze()} 在 1.21.9 被移除，因此本类引用的
 * {@link RegistryFreezeEvent} 在 1.21.9+ **不存在** —— 调用方只在探测到
 * {@code compose} 缺失时才会加载本类，新服务端永远不会触发它。
 *
 * <p>两套 API 的形状几乎一致：都有 {@code getOrCreateTag(...)}，注册都是
 * {@code registry().register(TypedKey, Consumer<Builder>)}，构建器方法名也相同。
 */
@SuppressWarnings("removal") // 1.21.9 起 freeze() 被标记待删除，但 1.21.0~1.21.5 只有它可用
public final class FreezeEnchantmentRegistrar {

    private FreezeEnchantmentRegistrar() {
    }

    /** 在当前引导上下文里挂上 freeze 事件处理器。 */
    public static void install(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
                RegistryEvents.ENCHANTMENT.freeze().newHandler(event -> {
                    try {
                        registerAll(event);
                        context.getLogger().info("[NewFish] 已通过 freeze() 注册 "
                                + EnchantmentSpecs.ALL.size() + " 个附魔（服务端 1.21.x 旧注册表 API）。");
                    } catch (Throwable throwable) {
                        context.getLogger().error("[NewFish] 附魔注册失败（freeze 路径）", throwable);
                    }
                }));
    }

    private static void registerAll(RegistryFreezeEvent<Enchantment, EnchantmentRegistryEntry.Builder> event) {
        WritableRegistry<Enchantment, EnchantmentRegistryEntry.Builder> registry = event.registry();
        for (EnchantmentSpecs.Spec spec : EnchantmentSpecs.ALL) {
            RegistryKeySet<ItemType> supported = tag(event, spec.tag());
            RegistryKeySet<Enchantment> exclusive = RegistrySet.keySet(RegistryKey.ENCHANTMENT, List.of());
            registry.register(
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

    private static RegistryKeySet<ItemType> tag(
            RegistryFreezeEvent<Enchantment, EnchantmentRegistryEntry.Builder> event,
            EnchantmentSpecs.Tag which) {
        return switch (which) {
            case FISHING -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_FISHING);
            case BOW -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_BOW);
            case ARMOR -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_ARMOR);
            case TRIDENT -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_TRIDENT);
        };
    }
}
