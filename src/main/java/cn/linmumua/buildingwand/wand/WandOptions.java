package cn.linmumua.buildingwand.wand;

import cn.linmumua.buildingwand.config.Config;
import cn.linmumua.buildingwand.item.WandKeys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 一把之杖的选项状态。逐物品持久化在 PersistentDataContainer 上，
 * 因此之杖掉落、交易、放箱子后选项都跟着走。
 */
public final class WandOptions {

    private LockMode lock;
    private DirectionMode direction;
    private MatchMode match;
    private boolean replace;
    private boolean random;

    private WandOptions(LockMode lock, DirectionMode direction, MatchMode match,
                        boolean replace, boolean random) {
        this.lock = lock;
        this.direction = direction;
        this.match = match;
        this.replace = replace;
        this.random = random;
    }

    /** 按配置默认值创建（新建之杖时使用）。 */
    public static WandOptions defaults(Config config) {
        Config.DefaultOptions d = config.defaultOptions();
        return new WandOptions(d.lock(), d.direction(), d.match(), d.replace(), d.random());
    }

    /** 从物品读取；缺失或损坏的字段回退到默认值。 */
    public static WandOptions read(ItemStack stack, WandKeys keys, Config config) {
        Config.DefaultOptions d = config.defaultOptions();
        if (stack == null || !stack.hasItemMeta()) {
            return defaults(config);
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        LockMode lock = parseLock(pdc.get(keys.lock(), PersistentDataType.STRING), d.lock());
        DirectionMode direction = parseDirection(pdc.get(keys.direction(), PersistentDataType.STRING), d.direction());
        MatchMode match = parseMatch(pdc.get(keys.match(), PersistentDataType.STRING), d.match());
        boolean replace = pdc.has(keys.replace(), PersistentDataType.BYTE)
                ? pdc.get(keys.replace(), PersistentDataType.BYTE) == 1 : d.replace();
        boolean random = pdc.has(keys.random(), PersistentDataType.BYTE)
                ? pdc.get(keys.random(), PersistentDataType.BYTE) == 1 : d.random();

        return new WandOptions(lock, direction, match, replace, random);
    }

    public void save(ItemStack stack, WandKeys keys) {
        if (stack == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.lock(), PersistentDataType.STRING, lock.name());
        pdc.set(keys.direction(), PersistentDataType.STRING, direction.name());
        pdc.set(keys.match(), PersistentDataType.STRING, match.name());
        pdc.set(keys.replace(), PersistentDataType.BYTE, (byte) (replace ? 1 : 0));
        pdc.set(keys.random(), PersistentDataType.BYTE, (byte) (random ? 1 : 0));
        stack.setItemMeta(meta);
    }

    private static LockMode parseLock(String raw, LockMode def) {
        if (raw == null) {
            return def;
        }
        try {
            return LockMode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private static DirectionMode parseDirection(String raw, DirectionMode def) {
        if (raw == null) {
            return def;
        }
        try {
            return DirectionMode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private static MatchMode parseMatch(String raw, MatchMode def) {
        if (raw == null) {
            return def;
        }
        try {
            return MatchMode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    public LockMode lock() {
        return lock;
    }

    public void setLock(LockMode lock) {
        this.lock = lock;
    }

    public DirectionMode direction() {
        return direction;
    }

    public void setDirection(DirectionMode direction) {
        this.direction = direction;
    }

    public MatchMode match() {
        return match;
    }

    public void setMatch(MatchMode match) {
        this.match = match;
    }

    public boolean replace() {
        return replace;
    }

    public void setReplace(boolean replace) {
        this.replace = replace;
    }

    public boolean random() {
        return random;
    }

    public void setRandom(boolean random) {
        this.random = random;
    }

}
