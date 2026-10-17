package org.example.xiaoxiao.newFish.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

/**
 * 统一调度入口，同时兼容 Paper 与 Folia。
 *
 * <p>Folia 把世界切分成多个互相独立的 region，各自在自己的线程上 tick，
 * 因此 {@code BukkitScheduler} 的「全局同步任务」在 Folia 上不再成立：
 * 任何触碰世界、方块或实体的代码都必须落在拥有该对象的 region 线程上。
 * 本类把两种调度模型收敛成同一组方法，调用方不需要关心运行在哪种服务端。
 *
 * <p>Folia 的调度器接口随 {@code paper-api} 一起提供，无需反射即可直接调用；
 * 是否 Folia 由 {@link #isFolia()} 在运行时判断，并据此选择实现。
 */
public final class Scheduler {
    /**
     * 是否使用 Folia 的 region 化调度。
     *
     * <p><b>不要用 {@code Bukkit.isFolia()} 判断</b>：这个方法在 paper-api 26.3 里
     * 已经不存在了（javap 验证过），反射探测只会抛 {@code NoSuchMethodException}
     * 并被误判成「非 Folia」，随后走到传统调度器上 —— 而 Folia 系服务端的
     * {@code CraftScheduler} 是直接抛 {@code UnsupportedOperationException} 的，
     * 插件会在启用阶段崩溃。这正是 26.3 上的实际故障。
     *
     * <p>改为直接探测 Folia 调度器 API 是否存在：{@code getGlobalRegionScheduler()}
     * 只有 Folia 系服务端才提供，且它是本类所有全局任务的依赖，
     * 拿不到就说明必须回退传统调度器。
     */
    private static final boolean FOLIA = detectFolia();

    private Scheduler() {
    }

    private static boolean detectFolia() {
        try {
            // 方法存在只说明是 Folia 系；个别分支会保留方法但让调用抛异常，
            // 因此这里真调一次，能拿到实例才算数。
            return Bukkit.getGlobalRegionScheduler() != null;
        } catch (Throwable ignored) {
            // NoSuchMethodError（纯 Paper）/ UnsupportedOperationException（禁用调度）都走这里。
            return false;
        }
    }

    /** 当前服务端是否为 Folia（或其分支）。 */
    public static boolean isFolia() {
        return FOLIA;
    }

    /**
     * 在「全局」线程上按固定周期重复执行。
     *
     * <p>只允许做不依赖具体坐标的事情：读取配置、遍历在线玩家列表、
     * 把真正的活派发给玩家自己的实体调度器。
     *
     * @return 任务句柄，可用于取消；调用方需自行持有
     */
    public static Handle runGlobalRepeating(Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        if (FOLIA) {
            ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                    plugin, ignored -> task.run(), Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
            return new Handle(scheduled);
        }
        int legacy = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin, task, Math.max(0L, initialDelayTicks), Math.max(1L, periodTicks));
        return new Handle(legacy);
    }

    /** 在「全局」线程上延迟执行一次。 */
    public static Handle runGlobalDelayed(Plugin plugin, Runnable task, long delayTicks) {
        if (FOLIA) {
            ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().runDelayed(
                    plugin, ignored -> task.run(), Math.max(1L, delayTicks));
            return new Handle(scheduled);
        }
        BukkitTask legacy = Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(0L, delayTicks));
        return new Handle(legacy);
    }

    /** 在「全局」线程上尽快执行一次。 */
    public static Handle runGlobal(Plugin plugin, Runnable task) {
        if (FOLIA) {
            ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> task.run());
            return new Handle(scheduled);
        }
        BukkitTask legacy = Bukkit.getScheduler().runTask(plugin, task);
        return new Handle(legacy);
    }

    /**
     * 在实体所属的 region 线程上按固定周期重复执行。
     *
     * <p>这是 Folia 上操作「随玩家/实体移动」状态的正确方式：
     * 任务会跟着实体所在的 region 走，实体卸载或死亡时自动停摆。
     *
     * @param retired     实体已失效（卸载/死亡）时执行的回调，可为 null
     */
    public static Handle runEntityRepeating(Plugin plugin, Entity entity, Runnable task,
                                            Runnable retired, long initialDelayTicks, long periodTicks) {
        if (FOLIA) {
            ScheduledTask scheduled = entity.getScheduler().runAtFixedRate(
                    plugin, ignored -> task.run(), retired,
                    Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
            // 实体已失效等情况下 Folia 会返回 null，此时没有任务可管。
            return scheduled == null ? Handle.NOOP : new Handle(scheduled);
        }
        int legacy = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin, task, Math.max(0L, initialDelayTicks), Math.max(1L, periodTicks));
        return new Handle(legacy);
    }

    /**
     * 在实体所属的 region 线程上尽快执行一次。
     *
     * @param retired 实体已失效时执行的回调，可为 null
     */
    public static Handle runEntity(Plugin plugin, Entity entity, Runnable task, Runnable retired) {
        if (FOLIA) {
            boolean accepted = entity.getScheduler().execute(plugin, task, retired, 1L);
            return accepted ? Handle.FIRE_AND_FORGET : Handle.NOOP;
        }
        BukkitTask legacy = Bukkit.getScheduler().runTask(plugin, task);
        return new Handle(legacy);
    }

    /**
     * 同 {@link #runEntity}，但把「任务是否被接受」作为返回值暴露给调用方。
     *
     * <p>用于「投递失败必须换一种方式补偿」的场景：Folia 上实体已被回收时
     * {@code execute} 会返回 false（任务根本不会执行），调用方需要据此走兜底逻辑。
     * 非 Folia 服务端一律返回 true。
     */
    public static boolean runEntityChecked(Plugin plugin, Entity entity, Runnable task, Runnable retired) {
        if (FOLIA) {
            return entity.getScheduler().execute(plugin, task, retired, 1L);
        }
        Bukkit.getScheduler().runTask(plugin, task);
        return true;
    }

    /**
     * 在异步线程上按固定周期重复执行。
     *
     * <p>只允许纯计算与 I/O，严禁触碰世界、方块、实体或玩家背包。
     */
    public static Handle runAsyncRepeating(Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        if (FOLIA) {
            ScheduledTask scheduled = Bukkit.getAsyncScheduler().runAtFixedRate(
                    plugin, ignored -> task.run(),
                    ticksToMillis(initialDelayTicks), ticksToMillis(Math.max(1L, periodTicks)), TimeUnit.MILLISECONDS);
            return new Handle(scheduled);
        }
        BukkitTask legacy = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, task, Math.max(0L, initialDelayTicks), Math.max(1L, periodTicks));
        return new Handle(legacy);
    }

    /** 取消一个由本类创建的任务。 */
    public static void cancel(Plugin plugin, Handle handle) {
        if (handle == null || handle == Handle.NOOP) return;
        if (handle.scheduled != null) {
            handle.scheduled.cancel();
            return;
        }
        if (handle.legacyTask != null) {
            handle.legacyTask.cancel();
            return;
        }
        if (handle.legacyId != -1) Bukkit.getScheduler().cancelTask(handle.legacyId);
    }

    /** 取消该插件在本类下登记的全部任务。 */
    public static void cancelAll(Plugin plugin) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
            return;
        }
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    /** 把 tick 换算成毫秒（1 tick = 50ms），用于 Folia 的异步调度器。 */
    private static long ticksToMillis(long ticks) {
        return Math.max(1L, ticks) * 50L;
    }

    /** 调度任务句柄，屏蔽两种服务端在取消方式上的差异。 */
    public static final class Handle {
        /** 已提交但无需跟踪的任务（例如 Folia 上一次性的实体任务）。 */
        public static final Handle FIRE_AND_FORGET = new Handle((ScheduledTask) null);
        /** 任务未能提交（实体已失效等）。 */
        public static final Handle NOOP = new Handle((BukkitTask) null);

        private final ScheduledTask scheduled;
        private final BukkitTask legacyTask;
        private final int legacyId;

        private Handle(ScheduledTask scheduled) {
            this.scheduled = scheduled;
            this.legacyTask = null;
            this.legacyId = -1;
        }

        private Handle(BukkitTask legacyTask) {
            this.scheduled = null;
            this.legacyTask = legacyTask;
            this.legacyId = -1;
        }

        private Handle(int legacyId) {
            this.scheduled = null;
            this.legacyTask = null;
            this.legacyId = legacyId;
        }
    }
}
