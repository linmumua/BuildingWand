package cn.linmumua.buildingwand.wand;

/**
 * 算法层看到的世界视图。Bukkit 适配器负责实现，单元测试用内存实现。
 * 这一层刻意不依赖 Bukkit，以便把 BFS 形状用测试锁死。
 */
public interface WorldView {

    String AIR = "minecraft:air";

    /** 该位置的方块 id，空气返回 {@link #AIR}。 */
    String blockAt(Pos pos);

    /** 是否可以在此放置方块（空气，或在开启替换时属于可替换方块）。 */
    boolean isPlaceable(Pos pos);

    /** 是否可穿透（判断「面前是否被挡住」）。 */
    boolean isPermeable(Pos pos);

    /** 是否可被破坏（排除基岩类、容器类方块）。 */
    boolean isBreakable(Pos pos);
}
