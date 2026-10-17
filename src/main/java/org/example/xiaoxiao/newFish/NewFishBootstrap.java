package org.example.xiaoxiao.newFish;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.event.RegistryEventProvider;
import net.kyori.adventure.text.Component;

import java.lang.reflect.InvocationTargetException;

/**
 * 引导期注册器入口：把 NewFish 的附魔注册成真正的原版附魔。
 *
 * <p>自定义附魔必须在**引导期**（世界与注册表尚在构建时）注册，不能等到
 * {@code onEnable()}；因此本类实现 {@link PluginBootstrap}。
 *
 * <h2>为什么要分流</h2>
 * Paper 的注册表事件 API 在 1.21.x 内部换过一次形态（逐版本 javap 核对）：
 * <pre>
 *   1.21   ~ 1.21.5   : RegistryEventProvider#freeze()  + RegistryFreezeEvent
 *   1.21.6 ~ 1.21.8   : 两者并存
 *   1.21.9 ~ 26.x     : 只剩 compose() + RegistryComposeEvent
 * </pre>
 * 因此本插件编译在 **1.21.6**（两套 API 都在的版本），运行期再按服务端能力二选一。
 *
 * <h2>为什么用类名反射加载实现类</h2>
 * {@code compose()} 路径引用的 {@code RegistryComposeEvent} 在 1.21.0~1.21.5 不存在；
 * {@code freeze()} 路径引用的 {@code RegistryFreezeEvent} 在 1.21.9+ 不存在。
 * 只要用直接调用写在这一个方法里，某些 JVM 的校验阶段就可能提前解析到不存在的类。
 * 因此这里**只按类名反射加载**：走哪条路径就只加载哪个类，另一条永远不会被触碰。
 *
 * <p>注册完成后这些附魔就是普通原版附魔：附魔台、砂轮、铁砧、tooltip 全部由原版处理。
 */
public final class NewFishBootstrap implements PluginBootstrap {

    private static final String COMPOSE_REGISTRAR =
            "org.example.xiaoxiao.newFish.enchantment.ComposeEnchantmentRegistrar";
    private static final String FREEZE_REGISTRAR =
            "org.example.xiaoxiao.newFish.enchantment.FreezeEnchantmentRegistrar";

    @Override
    public void bootstrap(BootstrapContext context) {
        // 引导期日志：这一行是判断「bootstrap 到底有没有被执行」的唯一可靠依据。
        // 若 paper-plugin.yml 缺失或未被解析，插件会被当成传统 Bukkit 插件加载，
        // PluginBootstrap 被整个跳过，日志里就看不到这一行。
        context.getLogger().info("[NewFish] 引导期已启动，准备注册附魔。");

        if (providerSupports("compose") && install(COMPOSE_REGISTRAR, context)) {
            return;
        }
        if (providerSupports("freeze") && install(FREEZE_REGISTRAR, context)) {
            return;
        }
        context.getLogger().warn(Component.text("[NewFish] 当前服务端没有可用的注册表事件 API，"
                + "7 个附魔无法注册为原版附魔；钓鱼、战利品、图鉴与品级玩法不受影响。"
                + "完整功能需要 Paper 1.21 及以上（1.21.0 ~ 1.21.5 走 freeze 路径）。"));
    }

    /** 探测 {@code RegistryEventProvider} 是否提供指定方法（服务端未加载该类时返回 false）。 */
    private static boolean providerSupports(String methodName) {
        try {
            RegistryEventProvider.class.getMethod(methodName);
            return true;
        } catch (NoSuchMethodException | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    /**
     * 反射加载并执行一个注册器实现。
     *
     * @return 是否已接管（true 表示不需要再尝试其它路径）
     */
    private static boolean install(String className, BootstrapContext context) {
        try {
            Class<?> registrar = Class.forName(className, true, NewFishBootstrap.class.getClassLoader());
            registrar.getMethod("install", BootstrapContext.class).invoke(null, context);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException unavailable) {
            // 该类在当前服务端不存在（或形态不符），交给下一条路径。
            return false;
        } catch (InvocationTargetException failure) {
            // 注册器自身抛错：已经尝试过，记日志后不再回退，避免重复注册。
            context.getLogger().error("[NewFish] 附魔注册器执行失败：" + className, failure.getCause());
            return true;
        }
    }
}
