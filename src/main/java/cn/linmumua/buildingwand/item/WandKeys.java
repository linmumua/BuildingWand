package cn.linmumua.buildingwand.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * 之杖在物品 PersistentDataContainer 里使用的键。
 */
public final class WandKeys {

    private final NamespacedKey tier;
    private final NamespacedKey cores;
    private final NamespacedKey core;
    private final NamespacedKey lock;
    private final NamespacedKey direction;
    private final NamespacedKey match;
    private final NamespacedKey replace;
    private final NamespacedKey random;
    private final NamespacedKey placed;

    public WandKeys(Plugin plugin) {
        this.tier = new NamespacedKey(plugin, "tier");
        this.cores = new NamespacedKey(plugin, "cores");
        this.core = new NamespacedKey(plugin, "core");
        this.lock = new NamespacedKey(plugin, "lock");
        this.direction = new NamespacedKey(plugin, "direction");
        this.match = new NamespacedKey(plugin, "match");
        this.replace = new NamespacedKey(plugin, "replace");
        this.random = new NamespacedKey(plugin, "random");
        this.placed = new NamespacedKey(plugin, "placed");
    }

    public NamespacedKey tier() {
        return tier;
    }

    public NamespacedKey cores() {
        return cores;
    }

    public NamespacedKey core() {
        return core;
    }

    public NamespacedKey lock() {
        return lock;
    }

    public NamespacedKey direction() {
        return direction;
    }

    public NamespacedKey match() {
        return match;
    }

    public NamespacedKey replace() {
        return replace;
    }

    public NamespacedKey random() {
        return random;
    }

    /** 累计放置方块数（统计用）。 */
    public NamespacedKey placed() {
        return placed;
    }
}
