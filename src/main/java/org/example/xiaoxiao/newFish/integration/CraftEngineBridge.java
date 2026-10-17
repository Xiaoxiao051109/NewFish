package org.example.xiaoxiao.newFish.integration;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.example.xiaoxiao.newFish.NewFish;
import org.example.xiaoxiao.newFish.util.Scheduler;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CraftEngineBridge {
    private static final String API_CLASS = "net.momirealms.craftengine.bukkit.api.CraftEngineItems";
    private static final List<String> ASSETS = List.of(
            "newfish-pack/pack.yml",
            "newfish-pack/configuration/items.yml",
            "newfish-pack/LICENSE_exlinefishing",
            "newfish-pack/resourcepack/assets/newfish/textures/item/anchovy.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/anchovy.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/angelfish.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/angelfish.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/arapaima.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/arapaima.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/arowana.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/arowana.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/barbel_steed.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/barbel_steed.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/barred_knifejaw.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/barred_knifejaw.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/bass.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/bass.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/bitterling.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/bitterling.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/bluegill.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/bluegill.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/brook_trout.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/brook_trout.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/carp.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/carp.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/catfish.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/catfish.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/cherry_salmon.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/cherry_salmon.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/coelacanth.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/coelacanth.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/crawfish.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/crawfish.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/crucian_carp.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/crucian_carp.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/dab.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/dab.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/dace.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/dace.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/eel.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/eel.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/freshwater_goby.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/freshwater_goby.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/frog.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/frog.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/giant_catfish.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/giant_catfish.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/giant_snakehead.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/giant_snakehead.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/goldfish.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/goldfish.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/guppy.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/guppy.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/horse_mackerel.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/horse_mackerel.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/jellyfish.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/jellyfish.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/killifish.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/killifish.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/koi.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/koi.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/large_bass.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/large_bass.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/large_char.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/large_char.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/loach.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/loach.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/lobster.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/lobster.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/marlin.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/marlin.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/octopus.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/octopus.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/olive_flounder.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/olive_flounder.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/pale_chub.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/pale_chub.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/piranha.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/piranha.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/pond_smelt.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/pond_smelt.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/popeyed_goldfish.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/popeyed_goldfish.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/rainbow_trout.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/rainbow_trout.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/red_snapper.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/red_snapper.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/sea_bass.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/sea_bass.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/seahorse.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/seahorse.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/small_bass.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/small_bass.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/squid.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/squid.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/stringfish.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/stringfish.json",
            "newfish-pack/resourcepack/assets/newfish/textures/item/sweetfish.png",
            "newfish-pack/resourcepack/assets/newfish/models/item/sweetfish.json"
    );

    private final NewFish plugin;
    private final Map<String, String> configuredIds;
    private boolean available;
    private int lastLoaded = -1;

    public CraftEngineBridge(NewFish plugin) {
        this.plugin = plugin;
        this.configuredIds = Map.ofEntries(
                Map.entry("anchovy", plugin.getConfig().getString("craftengine.items.anchovy", "newfish:anchovy")),
                Map.entry("angelfish", plugin.getConfig().getString("craftengine.items.angelfish", "newfish:angelfish")),
                Map.entry("arapaima", plugin.getConfig().getString("craftengine.items.arapaima", "newfish:arapaima")),
                Map.entry("arowana", plugin.getConfig().getString("craftengine.items.arowana", "newfish:arowana")),
                Map.entry("barbel_steed", plugin.getConfig().getString("craftengine.items.barbel_steed", "newfish:barbel_steed")),
                Map.entry("barred_knifejaw", plugin.getConfig().getString("craftengine.items.barred_knifejaw", "newfish:barred_knifejaw")),
                Map.entry("bass", plugin.getConfig().getString("craftengine.items.bass", "newfish:bass")),
                Map.entry("bitterling", plugin.getConfig().getString("craftengine.items.bitterling", "newfish:bitterling")),
                Map.entry("bluegill", plugin.getConfig().getString("craftengine.items.bluegill", "newfish:bluegill")),
                Map.entry("brook_trout", plugin.getConfig().getString("craftengine.items.brook_trout", "newfish:brook_trout")),
                Map.entry("carp", plugin.getConfig().getString("craftengine.items.carp", "newfish:carp")),
                Map.entry("catfish", plugin.getConfig().getString("craftengine.items.catfish", "newfish:catfish")),
                Map.entry("cherry_salmon", plugin.getConfig().getString("craftengine.items.cherry_salmon", "newfish:cherry_salmon")),
                Map.entry("coelacanth", plugin.getConfig().getString("craftengine.items.coelacanth", "newfish:coelacanth")),
                Map.entry("crawfish", plugin.getConfig().getString("craftengine.items.crawfish", "newfish:crawfish")),
                Map.entry("crucian_carp", plugin.getConfig().getString("craftengine.items.crucian_carp", "newfish:crucian_carp")),
                Map.entry("dab", plugin.getConfig().getString("craftengine.items.dab", "newfish:dab")),
                Map.entry("dace", plugin.getConfig().getString("craftengine.items.dace", "newfish:dace")),
                Map.entry("eel", plugin.getConfig().getString("craftengine.items.eel", "newfish:eel")),
                Map.entry("freshwater_goby", plugin.getConfig().getString("craftengine.items.freshwater_goby", "newfish:freshwater_goby")),
                Map.entry("frog", plugin.getConfig().getString("craftengine.items.frog", "newfish:frog")),
                Map.entry("giant_catfish", plugin.getConfig().getString("craftengine.items.giant_catfish", "newfish:giant_catfish")),
                Map.entry("giant_snakehead", plugin.getConfig().getString("craftengine.items.giant_snakehead", "newfish:giant_snakehead")),
                Map.entry("goldfish", plugin.getConfig().getString("craftengine.items.goldfish", "newfish:goldfish")),
                Map.entry("guppy", plugin.getConfig().getString("craftengine.items.guppy", "newfish:guppy")),
                Map.entry("horse_mackerel", plugin.getConfig().getString("craftengine.items.horse_mackerel", "newfish:horse_mackerel")),
                Map.entry("jellyfish", plugin.getConfig().getString("craftengine.items.jellyfish", "newfish:jellyfish")),
                Map.entry("killifish", plugin.getConfig().getString("craftengine.items.killifish", "newfish:killifish")),
                Map.entry("koi", plugin.getConfig().getString("craftengine.items.koi", "newfish:koi")),
                Map.entry("large_bass", plugin.getConfig().getString("craftengine.items.large_bass", "newfish:large_bass")),
                Map.entry("large_char", plugin.getConfig().getString("craftengine.items.large_char", "newfish:large_char")),
                Map.entry("loach", plugin.getConfig().getString("craftengine.items.loach", "newfish:loach")),
                Map.entry("lobster", plugin.getConfig().getString("craftengine.items.lobster", "newfish:lobster")),
                Map.entry("marlin", plugin.getConfig().getString("craftengine.items.marlin", "newfish:marlin")),
                Map.entry("octopus", plugin.getConfig().getString("craftengine.items.octopus", "newfish:octopus")),
                Map.entry("olive_flounder", plugin.getConfig().getString("craftengine.items.olive_flounder", "newfish:olive_flounder")),
                Map.entry("pale_chub", plugin.getConfig().getString("craftengine.items.pale_chub", "newfish:pale_chub")),
                Map.entry("piranha", plugin.getConfig().getString("craftengine.items.piranha", "newfish:piranha")),
                Map.entry("pond_smelt", plugin.getConfig().getString("craftengine.items.pond_smelt", "newfish:pond_smelt")),
                Map.entry("popeyed_goldfish", plugin.getConfig().getString("craftengine.items.popeyed_goldfish", "newfish:popeyed_goldfish")),
                Map.entry("rainbow_trout", plugin.getConfig().getString("craftengine.items.rainbow_trout", "newfish:rainbow_trout")),
                Map.entry("red_snapper", plugin.getConfig().getString("craftengine.items.red_snapper", "newfish:red_snapper")),
                Map.entry("sea_bass", plugin.getConfig().getString("craftengine.items.sea_bass", "newfish:sea_bass")),
                Map.entry("seahorse", plugin.getConfig().getString("craftengine.items.seahorse", "newfish:seahorse")),
                Map.entry("small_bass", plugin.getConfig().getString("craftengine.items.small_bass", "newfish:small_bass")),
                Map.entry("squid", plugin.getConfig().getString("craftengine.items.squid", "newfish:squid")),
                Map.entry("stringfish", plugin.getConfig().getString("craftengine.items.stringfish", "newfish:stringfish")),
                Map.entry("sweetfish", plugin.getConfig().getString("craftengine.items.sweetfish", "newfish:sweetfish"))
        );
    }

    public void initialize() {
        available = Bukkit.getPluginManager().isPluginEnabled("CraftEngine");
        if (!plugin.getConfig().getBoolean("craftengine.enabled", true)) {
            available = false;
            return;
        }
        if (!available) {
            plugin.getLogger().warning("CraftEngine 未安装；NewFish 将使用原版占位物品，玩法仍可运行。");
            return;
        }
        installAssets(false);
        refresh();
    }

    public boolean isAvailable() {
        return available;
    }

    public void refresh() {
        if (!available) return;
        try {
            int loaded = 0;
            for (String id : configuredIds.values()) {
                if (byId(id) != null) loaded++;
            }
            if (loaded != lastLoaded) {
                plugin.getLogger().info("CraftEngine 已连接，发现 " + loaded + "/" + configuredIds.size() + " 个 NewFish 物品定义。");
                lastLoaded = loaded;
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("读取 CraftEngine 物品失败：" + throwable.getClass().getSimpleName() + " - " + throwable.getMessage());
        }
    }

    public Optional<ItemStack> build(String logicalId) {
        if (!available) return Optional.empty();
        String id = configuredIds.get(logicalId);
        if (id == null) return Optional.empty();
        try {
            Object definition = byId(id);
            if (definition == null) return Optional.empty();
            Method build = definition.getClass().getMethod("buildBukkitItem");
            Object value = build.invoke(definition);
            return value instanceof ItemStack item ? Optional.of(item) : Optional.empty();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("构建 CraftEngine 物品 " + id + " 失败：" + rootMessage(throwable));
            return Optional.empty();
        }
    }

    public boolean matches(ItemStack stack, String logicalId) {
        if (!available || stack == null || !configuredIds.containsKey(logicalId)) return false;
        try {
            Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
            if (craftEngine == null) return false;
            Class<?> api = Class.forName(API_CLASS, true, craftEngine.getClass().getClassLoader());
            Object key = api.getMethod("getCustomItemId", ItemStack.class).invoke(null, stack);
            return key != null && configuredIds.get(logicalId).equalsIgnoreCase(key.toString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void installAssets(boolean force) {
        if (!available) return;
        Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (craftEngine == null) return;
        Path resources = craftEngine.getDataFolder().toPath().resolve("resources").resolve("newfish");
        try {
            for (String resource : ASSETS) {
                String relative = resource.substring("newfish-pack/".length());
                Path target = resources.resolve(relative);
                if (!force && Files.exists(target)) continue;
                Files.createDirectories(target.getParent());
                try (InputStream input = plugin.getResource(resource)) {
                    if (input == null) {
                        plugin.getLogger().warning("插件资源缺失：" + resource);
                        continue;
                    }
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            plugin.getLogger().info("CraftEngine NewFish/Exline 鱼类资源已安装到 " + resources + "。执行 /ce reload all 使资源生效。");
        } catch (IOException exception) {
            plugin.getLogger().warning("写入 CraftEngine 资源包失败：" + exception.getMessage());
        }
    }

    public void reloadAssets() {
        installAssets(false);
        if (!available) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ce reload all");
        Scheduler.runGlobalDelayed(plugin, this::refresh, 20L);
    }

    public Optional<String> configuredId(String logicalId) {
        return Optional.ofNullable(configuredIds.get(logicalId));
    }

    private Object byId(String id) throws ReflectiveOperationException {
        Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (craftEngine == null || !craftEngine.isEnabled()) return null;
        Class<?> api = Class.forName(API_CLASS, true, craftEngine.getClass().getClassLoader());
        return api.getMethod("byId", String.class).invoke(null, id);
    }

    private String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
