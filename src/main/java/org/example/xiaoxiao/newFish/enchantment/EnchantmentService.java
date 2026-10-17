package org.example.xiaoxiao.newFish.enchantment;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.entity.FishHook;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.example.xiaoxiao.newFish.NewFish;
import org.example.xiaoxiao.newFish.item.ItemIdentity;
import org.example.xiaoxiao.newFish.util.Scheduler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EnchantmentService implements Listener {
    private final NewFish plugin;
    private final Map<UUID, FishHook> hooks = new HashMap<>();
    private final Map<UUID, Trail> trails = new java.util.concurrent.ConcurrentHashMap<>();
    private Scheduler.Handle taskHandle;

    public EnchantmentService(NewFish plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // 每个箭矢的光痕只会出现在自己所在的 region，因此按箭矢调度，而不是全局每 tick 扫一遍。
        taskHandle = Scheduler.runGlobalRepeating(plugin, this::dispatchTrails, 1L, 1L);
    }

    public void stop() {
        Scheduler.cancel(plugin, taskHandle);
        taskHandle = null;
        for (Trail trail : trails.values()) clearTrail(trail);
        trails.clear();
        hooks.clear();
    }

    /**
     * 把光痕的推进派发到每支箭矢自身的 region 线程上。
     *
     * <p>方块写入必须发生在拥有该区块的 region 线程，所以这里不做任何方块操作，
     * 只挑选待处理的箭矢并把任务交给它们的实体调度器。
     */
    private void dispatchTrails() {
        Iterator<Map.Entry<UUID, Trail>> iterator = trails.entrySet().iterator();
        while (iterator.hasNext()) {
            Trail trail = iterator.next().getValue();
            if (trail.scheduled) continue;
            Arrow arrow = trail.arrow;
            if (!arrow.isValid()) {
                iterator.remove();
                continue;
            }
            trail.scheduled = true;
            Scheduler.Handle handle = Scheduler.runEntityRepeating(
                    plugin, arrow,
                    () -> tickTrail(trail),
                    () -> {
                        trails.remove(arrow.getUniqueId());
                        clearTrail(trail);
                    },
                    1L, 1L);
            if (handle == Scheduler.Handle.NOOP) {
                // 箭矢已经失效，退回给全局分支下一轮清理。
                trail.scheduled = false;
            }
        }
    }

    /** 推进单条光痕，保证运行在该箭矢所属的 region 线程上。 */
    private void tickTrail(Trail trail) {
        Arrow arrow = trail.arrow;
        if (!arrow.isValid() || trail.hit || trail.age >= trail.lifetime
                || trail.blocks.size() >= trail.maxBlocks
                || arrow.getLocation().distanceSquared(trail.start) > (double) trail.maxDistance * trail.maxDistance) {
            trails.remove(arrow.getUniqueId());
            clearTrail(trail);
            return;
        }
        trail.age++;
        Vector velocity = arrow.getVelocity();
        if (velocity.lengthSquared() < 0.0001) return;
        Vector direction = velocity.clone().normalize().multiply(-1.0);
        Block block = arrow.getLocation().add(direction).getBlock();
        if (block.getType().isAir()) {
            block.setType(Material.WATER, false);
            trail.blocks.add(block);
        }
    }

    public void rememberHook(Player player, FishHook hook) {
        if (hook != null) hooks.put(player.getUniqueId(), hook);
    }

    public void forgetHook(Player player) {
        hooks.remove(player.getUniqueId());
    }

    public void onHookedEntity(Player player, Entity entity) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        int level = ItemIdentity.customEnchantment(rod, "hooked_strike");
        if (level <= 0 || entity == player || !entity.isValid()) return;
        if (entity instanceof org.bukkit.entity.Damageable damageable) {
            damageable.damage(Math.min(6, level * 2.0), player);
        }
        forgetHook(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof Arrow arrow) || !(arrow.getShooter() instanceof Player player)) return;
        int level = ItemIdentity.customEnchantment(player.getInventory().getItemInMainHand(), "water_trail");
        if (level <= 0 || !plugin.getConfig().getBoolean("enchantments.water_trail", true)) return;
        if (trails.size() >= plugin.getConfig().getInt("enchantments.water-trail.max-active-arrows", 64)) return;
        int maxDistance = plugin.getConfig().getInt("enchantments.water-trail.max-distance", 48);
        trails.put(arrow.getUniqueId(), new Trail(arrow, plugin.getConfig().getInt("enchantments.water-trail.lifetime-ticks", 20), plugin.getConfig().getInt("enchantments.water-trail.max-blocks-per-arrow", 24), maxDistance));
        player.getWorld().playSound(player.getLocation(), "entity.generic.splash", 1.0f, 2.0f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile instanceof Trident trident && ItemIdentity.customEnchantment(trident.getItem(), "thundering") > 0) {
            if (event.getHitBlock() != null) {
                event.getHitBlock().getWorld().strikeLightning(event.getHitBlock().getLocation().add(0.5, 0.0, 0.5));
            } else if (event.getHitEntity() != null) {
                event.getHitEntity().getWorld().strikeLightning(event.getHitEntity().getLocation());
            }
        }
        if (projectile instanceof Arrow arrow) {
            // 命中后立即收尾：清掉已经放下的水方块，避免光痕残留在地形上。
            Trail trail = trails.remove(arrow.getUniqueId());
            if (trail != null) {
                trail.hit = true;
                clearTrail(trail);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.isOnGround() || event.getFrom().getY() <= event.getTo().getY()) return;
        if (ItemIdentity.customEnchantment(player.getInventory().getItemInMainHand(), "ender_hook") <= 0) return;
        FishHook hook = hooks.get(player.getUniqueId());
        if (hook == null || !hook.isValid() || hook.getWorld() != player.getWorld()) return;
        if (hook.getLocation().distanceSquared(player.getLocation()) > 32 * 32) return;
        hooks.remove(player.getUniqueId());
        // 末影鱼钩会把玩家瞬移到浮标处，这在 Folia 上属于跨 region 传送，
        // 用 teleportAsync 让服务端自行完成线程切换，避免跨 region 直接写状态。
        player.teleportAsync(hook.getLocation()).thenAccept(success -> {
            if (!Boolean.TRUE.equals(success) || !player.isOnline()) return;
            dispatchOnPlayer(player, () -> teleportEffects(player));
        });
    }

    /** 末影鱼钩传送后的表现：音效、粒子与负面效果。 */
    private void teleportEffects(Player player) {
        player.getWorld().playSound(player.getLocation(), "entity.player.teleport", 1.0f, 1.0f);
        player.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, player.getLocation().add(0, 1, 0), 100, .5, 1, .5, 0);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 20, 0, true, false, true));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 60, 1, true, false, true));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 60, 1, true, false, true));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.HUNGER, 60, 1, true, false, true));
        player.damage(2.0, player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFishState(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.REEL_IN
                || event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            forgetHook(event.getPlayer());
        }
    }

    private void clearTrail(Trail trail) {
        for (Block block : trail.blocks) {
            if (block.getType() == Material.WATER) block.setType(Material.AIR, false);
        }
        trail.blocks.clear();
    }

    /** 把一段「操作玩家自身状态」的收尾动作交回该玩家所属的 region 线程。 */
    private void dispatchOnPlayer(Player player, Runnable action) {
        if (!Scheduler.isFolia()) {
            action.run();
            return;
        }
        Scheduler.runEntity(plugin, player, action, null);
    }

    private static final class Trail {
        private final Arrow arrow;
        private final int lifetime;
        private final int maxBlocks;
        private final int maxDistance;
        private final org.bukkit.Location start;
        private final Set<Block> blocks = new HashSet<>();
        private int age;
        private boolean hit;
        /** 是否已经交给该箭矢的实体调度器，避免重复提交。 */
        private volatile boolean scheduled;

        private Trail(Arrow arrow, int lifetime, int maxBlocks, int maxDistance) {
            this.arrow = arrow;
            this.lifetime = Math.max(1, lifetime);
            this.maxBlocks = Math.max(1, maxBlocks);
            this.maxDistance = Math.max(1, maxDistance);
            this.start = arrow.getLocation().clone();
        }
    }
}
