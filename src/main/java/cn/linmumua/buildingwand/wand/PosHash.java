package cn.linmumua.buildingwand.wand;

import java.util.List;

/**
 * 位置集合的廉价哈希。
 *
 * <p>用途：预览每一轮都会算出「这一轮要显示哪些格子」，与上一轮比较。如果集合没变，
 * 就不该销毁重建实体。逐格拼完整字符串太贵，所以用 64 位滚动哈希。</p>
 *
 * <p>刻意放在不依赖 Bukkit 的包里：它必须能被单元测试直接验证。一旦判等出错
 * ——该重建时没重建——表现是「预览偶尔停在旧位置」，只在特定形状下偶发，
 * 靠肉眼和日志都几乎查不出来。</p>
 */
public final class PosHash {

    private PosHash() {
    }

    /** 对位置集合求滚动哈希。空集合返回一个固定值。 */
    public static long of(List<Pos> positions) {
        long hash = 1125899906842597L;
        for (Pos pos : positions) {
            hash = 31 * hash + pos.x();
            hash = 31 * hash + pos.y();
            hash = 31 * hash + pos.z();
        }
        return hash;
    }
}
