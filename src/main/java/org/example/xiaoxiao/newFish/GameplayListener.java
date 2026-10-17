package org.example.xiaoxiao.newFish;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

public final class GameplayListener implements Listener {
    private final NewFish plugin;

    public GameplayListener(NewFish plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        plugin.getItemFactory().applyFallbackFishEffect(event.getItem(), event.getPlayer());
    }
}
