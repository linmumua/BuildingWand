package cn.linmumua.buildingwand.compat;

import cn.linmumua.buildingwand.config.Config;
import cn.linmumua.buildingwand.wand.Pos;
import cn.linmumua.buildingwand.wand.WorldView;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;

/**
 * 把 Bukkit 世界包装成算法层认识的 {@link WorldView}。
 *
 * <p>刻意不在这里做实体碰撞检测：BFS 会对每个候选格调用 {@link #isPlaceable(Pos)}，
 * 每次去扫附近实体的话 1024 格就是灾难。实体碰撞留到真正落块时再判。</p>
 */
public final class BukkitWorldView implements WorldView {

    private final World world;
    private final Player player;
    private final Config config;
    private final boolean replace;

    public BukkitWorldView(World world, Player player, Config config, boolean replace) {
        this.world = world;
        this.player = player;
        this.config = config;
        this.replace = replace;
    }

    @Override
    public String blockAt(Pos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Material type = block.getType();
        if (type.isAir()) {
            return AIR;
        }
        return type.getKey().toString();
    }

    @Override
    public boolean isPlaceable(Pos pos) {
        if (!withinRange(pos)) {
            return false;
        }
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (block.getType().isAir()) {
            return true;
        }
        // 只有开启替换时才允许顶掉水、雪、草丛这类可替换方块
        return replace && block.isReplaceable() && !tileEntityAllowed(block);
    }

    @Override
    public boolean isPermeable(Pos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        return block.isEmpty() || block.isPassable();
    }

    @Override
    public boolean isBreakable(Pos pos) {
        if (!withinRange(pos)) {
            return false;
        }
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Material type = block.getType();
        if (type.isAir()) {
            return false;
        }
        // 硬度 -1 即基岩类不可破坏方块
        if (type.getHardness() < 0) {
            return false;
        }
        return tileEntityAllowed(block);
    }

    /** 容器类方块按黑白名单判定是否允许被之杖操作。 */
    private boolean tileEntityAllowed(Block block) {
        if (!(block.getState() instanceof TileState)) {
            return true;
        }
        String fullId = block.getType().getKey().toString();
        String namespace = block.getType().getKey().getNamespace();
        boolean listed = config.tileEntityList().contains(fullId)
                || config.tileEntityList().contains(namespace);
        return config.tileEntityWhitelist() == listed;
    }

    /**
     * 操作半径限制。与原模组一致，只看水平距离（max(|dx|, |dz|)），
     * 避免向下挖时有额外的垂直限制。
     */
    private boolean withinRange(Pos pos) {
        int max = config.maxRange();
        if (max <= 0) {
            return true;
        }
        int dx = Math.abs(pos.x() - player.getLocation().getBlockX());
        int dz = Math.abs(pos.z() - player.getLocation().getBlockZ());
        return Math.max(dx, dz) <= max;
    }
}
